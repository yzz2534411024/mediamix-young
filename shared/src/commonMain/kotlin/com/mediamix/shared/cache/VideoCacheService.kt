package com.mediamix.shared.cache

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

/**
 * Multi-level video cache service (orchestrator).
 *
 * Coordinates MemoryCache (L1/L2) + DiskCache (L3/L4) + CacheStrategyManager.
 *
 * Eviction order:
 * 1. Expired entries
 * 2. Low-priority preload entries
 * 3. LRU (least recently used)
 * 4. Large files
 *
 * Retention: favorites, 24h playback history, popular videos (hitCount >= 10)
 */
class VideoCacheService(
    private val memoryCache: MemoryCache,
    private val diskCache: DiskCache,
    private val cacheStrategyManager: CacheStrategyManager? = null,
) {
    private val logger = Logger.withTag("VideoCacheService")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ----------------------------------------------------------
    // State
    // ----------------------------------------------------------
    private var policy = CachePolicy.NORMAL
    private var hitCount = 0L
    private var missCount = 0L
    private var initialized = false

    // ----------------------------------------------------------
    // Constants
    // ----------------------------------------------------------
    private val emergencyThresholdBytes = 500L * 1024 * 1024 // 500MB
    private val evictionThresholdPercent = 80.0
    private val maxCacheBytes = 2L * 1024 * 1024 * 1024 // 2GB
    private val recentlyPlayedWindowMs = 24L * 60 * 60 * 1000 // 24 hours

    // TTL cleanup
    private val ttlCleanupIntervalMs = 30L * 1000 // 30 seconds
    private var ttlCleanupJob: Job? = null

    // ===========================================================
    // Initialization
    // ===========================================================

    suspend fun initialize() {
        if (initialized) return
        try {
            diskCache.initialize()
            startTtlCleanupTimer()
            initialized = true
            logger.i("VideoCacheService initialized")
        } catch (e: Exception) {
            logger.e(e) { "VideoCacheService initialization failed" }
            initialized = true // allow degraded operation
        }
    }

    // ===========================================================
    // Unified API - Query
    // ===========================================================

    fun hasCache(
        videoId: String,
        quality: String = "default",
    ): Boolean {
        // L3/L4 disk check
        return diskCache.hasCache(videoId, quality)
    }

    fun getCachePath(
        videoId: String,
        quality: String = "default",
    ): String? {
        val path = diskCache.getCachePath(videoId, quality)
        if (path != null) {
            hitCount++
            recordViewingIfNeeded(videoId)
            logger.d("Cache hit(L3): $videoId@$quality")
            return path
        }
        missCount++
        return null
    }

    fun getAnyQualityCachePath(
        videoId: String,
        preferredQuality: String? = null,
    ): Pair<String, String>? {
        val result = diskCache.getAnyQualityCachePath(videoId, preferredQuality)
        if (result != null) {
            hitCount++
            recordViewingIfNeeded(videoId)
        } else {
            missCount++
        }
        return result
    }

    // ===========================================================
    // Unified API - Write
    // ===========================================================

    suspend fun putVideo(
        videoId: String,
        filePath: String,
        quality: String = "default",
        priority: Int = 0,
        ttl: Int = 604800,
        category: String? = null,
    ) {
        // Use dynamic TTL if strategy manager available
        val effectiveTtl =
            if (cacheStrategyManager != null && cacheStrategyManager.isInitialized) {
                cacheStrategyManager.getDynamicTtl(videoId, category = category, baseTtl = ttl)
            } else {
                ttl
            }

        // Use dynamic priority if strategy manager available
        var effectivePriority = priority
        if (cacheStrategyManager != null && cacheStrategyManager.isInitialized) {
            val dynamicPriority = cacheStrategyManager.getPriority(videoId, category = category)
            val mappedPriority =
                when (dynamicPriority) {
                    CachePriority.HIGH -> 20
                    CachePriority.LOW -> -5
                    else -> 0
                }
            if (mappedPriority > effectivePriority) {
                effectivePriority = mappedPriority
            }
        }

        diskCache.putVideo(videoId, filePath, quality, effectivePriority, effectiveTtl, category)
        checkEvictionNeeded()
    }

    suspend fun putSegment(
        videoId: String,
        segmentKey: String,
        data: ByteArray,
        quality: String = "default",
    ) {
        // L2 memory cache (skip if critical pressure)
        memoryCache.putSegment(videoId, segmentKey, data, quality)

        // L4 disk cache
        diskCache.putSegment(videoId, segmentKey, data, quality)
    }

    fun getSegment(
        videoId: String,
        segmentKey: String,
        quality: String = "default",
    ): SegmentCacheResult {
        // L2 memory
        val memResult = memoryCache.getSegment(videoId, segmentKey, quality)
        if (memResult != null) {
            hitCount++
            recordViewingIfNeeded(videoId)
            logger.d("Segment hit(L2): $videoId/$segmentKey@$quality")
            return memResult
        }

        // L4 disk
        val diskResult = diskCache.getSegment(videoId, segmentKey, quality)
        if (diskResult != null) {
            hitCount++
            recordViewingIfNeeded(videoId)
            logger.d("Segment hit(L4): $videoId/$segmentKey@$quality")
            return diskResult
        }

        missCount++
        return SegmentCacheResult(hit = false)
    }

    // ===========================================================
    // L1 Frame Buffer
    // ===========================================================

    fun putFrameBuffer(
        videoId: String,
        frameBuffer: Map<String, Any>,
        quality: String = "default",
    ) {
        memoryCache.putFrameBuffer(videoId, frameBuffer, quality)
    }

    fun getFrameBuffer(
        videoId: String,
        quality: String = "default",
    ): Map<String, Any>? {
        val result = memoryCache.getFrameBuffer(videoId, quality)
        if (result != null) {
            hitCount++
            recordViewingIfNeeded(videoId)
        } else {
            missCount++
        }
        return result
    }

    fun getMemoryUsage(): MemoryUsageInfo = memoryCache.getMemoryUsage()

    fun trimMemory() = memoryCache.trimMemory()

    fun clearAllMemoryCaches() = memoryCache.clearAll()

    // ===========================================================
    // Eviction (4-stage)
    // ===========================================================

    suspend fun evict() {
        logger.i("Starting cache eviction, entries: ${diskCache.getAllEntries().size}")
        var evictedCount = 0

        // Stage 1: expired entries
        evictedCount += evictExpired()

        // Stage 2: low-priority preload
        evictedCount += evictLowPriorityPreload()

        if (getDiskUsagePercent() < evictionThresholdPercent) {
            logger.i("Eviction done (stage 1-2), evicted: $evictedCount")
            diskCache.saveIndex()
            return
        }

        // Stage 3: LRU
        evictedCount += evictLRU()

        if (getDiskUsagePercent() < evictionThresholdPercent) {
            logger.i("Eviction done (stage 1-3), evicted: $evictedCount")
            diskCache.saveIndex()
            return
        }

        // Stage 4: large files
        evictedCount += evictLargeFiles()

        diskCache.saveIndex()
        logger.i("Eviction done (all stages), evicted: $evictedCount")
    }

    private suspend fun evictExpired(): Int {
        val now = Clock.System.now().toEpochMilliseconds()
        val expiredKeys =
            diskCache
                .getAllEntries()
                .filter { it.isExpired(now) }
                .map { it.cacheId }

        for (key in expiredKeys) {
            diskCache.removeEntry(key)
        }
        if (expiredKeys.isNotEmpty()) {
            logger.d("Evicted expired entries: ${expiredKeys.size}")
        }
        return expiredKeys.size
    }

    private suspend fun evictLowPriorityPreload(): Int {
        val candidates =
            diskCache
                .getAllEntries()
                .filter { entry ->
                    val effectivePri = getEffectivePriorityForEntry(entry)
                    effectivePri <= 0 && !entry.isComplete && !shouldKeep(entry)
                }.sortedWith(compareBy({ getEffectivePriorityForEntry(it) }, { it.lastAccess }))

        var evicted = 0
        for (entry in candidates) {
            diskCache.removeEntry(entry.cacheId)
            evicted++
            if (getDiskUsagePercent() < evictionThresholdPercent) break
        }
        if (evicted > 0) logger.d("Evicted low-priority preload: $evicted")
        return evicted
    }

    private suspend fun evictLRU(): Int {
        val candidates =
            diskCache
                .getAllEntries()
                .filter { !shouldKeep(it) }
                .sortedWith(compareBy({ getEffectivePriorityForEntry(it) }, { it.lastAccess }))

        var evicted = 0
        for (entry in candidates) {
            diskCache.removeEntry(entry.cacheId)
            evicted++
            if (getDiskUsagePercent() < evictionThresholdPercent) break
        }
        if (evicted > 0) logger.d("LRU evicted: $evicted")
        return evicted
    }

    private suspend fun evictLargeFiles(): Int {
        val candidates =
            diskCache
                .getAllEntries()
                .filter { !shouldKeep(it) }
                .sortedWith(
                    compareByDescending<CacheEntry> { getEffectivePriorityForEntry(it) }
                        .thenByDescending { it.fileSize }
                        .reversed(),
                )

        var evicted = 0
        for (entry in candidates) {
            diskCache.removeEntry(entry.cacheId)
            evicted++
            if (getDiskUsagePercent() < evictionThresholdPercent) break
        }
        if (evicted > 0) logger.d("Large file evicted: $evicted")
        return evicted
    }

    private fun shouldKeep(entry: CacheEntry): Boolean {
        val effectivePriority = getEffectivePriorityForEntry(entry)
        if (effectivePriority >= 10) return true // user favorites

        val now = Clock.System.now().toEpochMilliseconds()
        if (now - entry.lastAccess < recentlyPlayedWindowMs) return true // 24h playback

        if (entry.hitCount >= 10) return true // popular videos

        return false
    }

    // ===========================================================
    // Policy
    // ===========================================================

    fun setPolicy(policy: CachePolicy) {
        this.policy = policy
        logger.i("Cache policy switched: $policy")
    }

    fun autoSelectPolicy(
        isWiFi: Boolean,
        availableDiskBytes: Long,
    ) {
        if (availableDiskBytes < emergencyThresholdBytes) {
            setPolicy(CachePolicy.EMERGENCY)
        } else if (!isWiFi) {
            setPolicy(CachePolicy.CONSERVATIVE)
        } else if (isWiFi && availableDiskBytes > emergencyThresholdBytes * 4) {
            setPolicy(CachePolicy.AGGRESSIVE)
        } else {
            setPolicy(CachePolicy.NORMAL)
        }
    }

    // ===========================================================
    // Stats
    // ===========================================================

    fun getStats(): CacheStats {
        val entries = diskCache.getAllEntries()
        val totalSize = entries.sumOf { it.fileSize }
        val total = hitCount + missCount
        val hitRate = if (total > 0) hitCount.toDouble() / total else 0.0
        val diskUsage = getDiskUsagePercent()

        return CacheStats(
            totalSize = totalSize,
            entryCount = entries.size,
            hitCount = hitCount,
            missCount = missCount,
            hitRate = hitRate,
            diskUsagePercent = diskUsage,
        )
    }

    // ===========================================================
    // Lifecycle
    // ===========================================================

    suspend fun clearAll() {
        ttlCleanupJob?.cancel()
        ttlCleanupJob = null

        memoryCache.clearAll()
        diskCache.clearAll()

        hitCount = 0L
        missCount = 0L
        logger.i("All caches cleared")
    }

    fun dispose() {
        ttlCleanupJob?.cancel()
        ttlCleanupJob = null
        scope.cancel()
        memoryCache.clearAll()
        logger.i("VideoCacheService disposed")
    }

    // ===========================================================
    // Internal
    // ===========================================================

    private fun getDiskUsagePercent(): Double {
        val maxSize = maxCacheBytes
        if (maxSize <= 0) return 0.0
        return (diskCache.getDiskTotalSize().toDouble() / maxSize) * 100.0
    }

    private fun checkEvictionNeeded() {
        scope.launch {
            val usage = getDiskUsagePercent()
            if (usage > evictionThresholdPercent) {
                logger.i("Cache usage ${"%.1f".format(usage)}%, triggering eviction")
                evict()
            }
        }
    }

    private fun getEffectivePriorityForEntry(entry: CacheEntry): Int {
        if (cacheStrategyManager != null && cacheStrategyManager.isInitialized) {
            try {
                val dynamicPriority = cacheStrategyManager.getPriority(entry.videoId)
                val mappedPriority =
                    when (dynamicPriority) {
                        CachePriority.HIGH -> 20
                        CachePriority.LOW -> -5
                        else -> 0
                    }
                return maxOf(mappedPriority, entry.priority)
            } catch (_: Exception) {
                // fallback
            }
        }
        return entry.priority
    }

    private fun recordViewingIfNeeded(videoId: String) {
        if (cacheStrategyManager != null && cacheStrategyManager.isInitialized) {
            try {
                cacheStrategyManager.recordViewing(videoId)
            } catch (e: Exception) {
                logger.w("Failed to record viewing: $e")
            }
        }
    }

    private fun startTtlCleanupTimer() {
        ttlCleanupJob?.cancel()
        ttlCleanupJob =
            scope.launch {
                while (isActive) {
                    delay(ttlCleanupIntervalMs)
                    memoryCache.ttlCleanup()
                }
            }
    }
}
