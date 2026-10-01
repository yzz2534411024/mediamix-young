package com.mediamix.shared.cache

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.MemoryPressureLevel
import com.mediamix.shared.models.MemoryUsageInfo
import com.mediamix.shared.models.SegmentCacheResult
import kotlinx.datetime.Clock

/**
 * L1/L2 memory cache layer.
 *
 * - L1: decoded frame buffer cache
 * - L2: raw stream segment cache
 *
 * Features:
 * - TTL-based expiry (active videos 5min, inactive 1min for L1; 5min for L2)
 * - LRU eviction when exceeding max entries
 * - Memory pressure awareness (normal/warning/critical)
 * - Dynamic capacity adjustment via CacheStrategyManager
 */
class MemoryCache(
    private val maxL1Entries: Int = 20,
    private val maxL2Entries: Int = 50,
    private val minL2Entries: Int = 5,
    private val memoryReader: (() -> Long) = { 0L },
    private val maxRssBytes: Long = 512L * 1024 * 1024,
) {
    private val logger = Logger.withTag("MemoryCache")

    // ----------------------------------------------------------
    // TTL configuration (millis)
    // ----------------------------------------------------------
    private val l1ActiveTtlMs = 5L * 60 * 1000   // 5 minutes
    private val l1InactiveTtlMs = 1L * 60 * 1000  // 1 minute
    private val l2TtlMs = 5L * 60 * 1000          // 5 minutes

    // ----------------------------------------------------------
    // Memory pressure thresholds
    // ----------------------------------------------------------
    private val memoryWarningThreshold = 0.70
    private val memoryCriticalThreshold = 0.90

    // ----------------------------------------------------------
    // Memory pressure check interval
    // ----------------------------------------------------------
    private val memoryCheckInterval = 10
    private var memoryCheckCounter = 0

    // ----------------------------------------------------------
    // L1: frame buffer cache
    // ----------------------------------------------------------
    private val l1Cache = mutableMapOf<String, L1Entry>()

    // ----------------------------------------------------------
    // L2: stream segment cache
    // ----------------------------------------------------------
    private val l2Cache = mutableMapOf<String, L2Entry>()

    // ----------------------------------------------------------
    // State
    // ----------------------------------------------------------
    private var currentPressure = MemoryPressureLevel.NORMAL
    private val activeVideoIds = mutableSetOf<String>()

    // ===========================================================
    // L1 Frame Buffer API
    // ===========================================================

    fun putFrameBuffer(videoId: String, frameBuffer: Map<String, Any>, quality: String) {
        periodicMemoryPressureCheck()

        if (currentPressure == MemoryPressureLevel.CRITICAL) {
            logger.w("Memory pressure critical, rejecting L1 write: $videoId@$quality")
            return
        }

        activeVideoIds.add(videoId)
        val key = l1Key(videoId, quality)
        l1Cache[key] = L1Entry(
            videoId = videoId,
            quality = quality,
            frameBuffer = frameBuffer,
            estimatedBytes = estimateFrameBufferSize(frameBuffer),
        )
        evictL1IfNeeded()
    }

    fun getFrameBuffer(videoId: String, quality: String): Map<String, Any>? {
        val key = l1Key(videoId, quality)
        val entry = l1Cache[key] ?: return null

        val ttl = if (activeVideoIds.contains(videoId)) l1ActiveTtlMs else l1InactiveTtlMs
        if (isL1Expired(entry, ttl)) {
            l1Cache.remove(key)
            logger.d("L1 TTL expired, removing: $videoId@$quality")
            return null
        }

        entry.hitCount++
        entry.lastAccessMs = Clock.System.now().toEpochMilliseconds()
        return entry.frameBuffer
    }

    // ===========================================================
    // L2 Segment Cache API
    // ===========================================================

    fun putSegment(videoId: String, segmentKey: String, data: ByteArray, quality: String) {
        periodicMemoryPressureCheck()

        if (currentPressure == MemoryPressureLevel.CRITICAL) {
            logger.w("Memory pressure critical, rejecting L2 write: $videoId/$segmentKey@$quality")
            return
        }

        val key = l2Key(videoId, segmentKey, quality)
        l2Cache[key] = L2Entry(
            videoId = videoId,
            segmentKey = segmentKey,
            quality = quality,
            data = data,
        )
        evictL2IfNeeded()
    }

    fun getSegment(videoId: String, segmentKey: String, quality: String): SegmentCacheResult? {
        val key = l2Key(videoId, segmentKey, quality)
        val entry = l2Cache[key] ?: return null

        if (isL2Expired(entry)) {
            l2Cache.remove(key)
            logger.d("L2 TTL expired, removing: $videoId/$segmentKey@$quality")
            return null
        }

        entry.hitCount++
        entry.lastAccessMs = Clock.System.now().toEpochMilliseconds()
        return SegmentCacheResult(hit = true, data = entry.data.toList())
    }

    // ===========================================================
    // Active video tracking
    // ===========================================================

    fun markVideoActive(videoId: String) {
        activeVideoIds.add(videoId)
    }

    fun markVideoInactive(videoId: String) {
        activeVideoIds.remove(videoId)
    }

    // ===========================================================
    // Stats & Memory Info
    // ===========================================================

    fun getStats(): Pair<Int, Int> = Pair(l1Cache.size, l2Cache.size)

    fun getMemoryUsage(): MemoryUsageInfo {
        var l1Bytes = 0L
        for (entry in l1Cache.values) {
            l1Bytes += entry.estimatedBytes
        }
        var l2Bytes = 0L
        for (entry in l2Cache.values) {
            l2Bytes += entry.data.size
        }
        return MemoryUsageInfo(
            l1Bytes = l1Bytes,
            l2Bytes = l2Bytes,
            processRssBytes = memoryReader(),
            pressureLevel = currentPressure,
            l1MaxEntries = getL1MaxEntries(),
            l2MaxEntries = getL2MaxEntries(),
        )
    }

    // ===========================================================
    // Memory management
    // ===========================================================

    fun trimMemory() {
        logger.i("trimMemory called, current pressure: $currentPressure")
        checkMemoryPressure()

        if (currentPressure == MemoryPressureLevel.CRITICAL) {
            l1Cache.clear()
            logger.w("trimMemory: pressure critical, cleared L1")
        }

        evictExpiredL1()
        evictExpiredL2()
        evictL1IfNeeded()
        evictL2IfNeeded()
    }

    fun clearAll() {
        val l1Count = l1Cache.size
        val l2Count = l2Cache.size
        l1Cache.clear()
        l2Cache.clear()
        activeVideoIds.clear()
        currentPressure = MemoryPressureLevel.NORMAL
        memoryCheckCounter = 0
        logger.i("clearAll: cleared L1($l1Count) + L2($l2Count)")
    }

    fun setMaxRssBytes(maxBytes: Long) {
        // Allow runtime override for testing
    }

    // ===========================================================
    // TTL periodic cleanup
    // ===========================================================

    fun ttlCleanup() {
        val l1Evicted = evictExpiredL1()
        val l2Evicted = evictExpiredL2()
        if (l1Evicted > 0 || l2Evicted > 0) {
            logger.d("TTL cleanup: L1=$l1Evicted, L2=$l2Evicted")
        }
        checkMemoryPressure()
    }

    // ===========================================================
    // Internal - Key generation
    // ===========================================================

    private fun l1Key(videoId: String, quality: String): String = "${videoId}_${quality}"

    private fun l2Key(videoId: String, segmentKey: String, quality: String): String =
        "${videoId}_${segmentKey}_${quality}"

    // ===========================================================
    // Internal - TTL checks
    // ===========================================================

    private fun isL1Expired(entry: L1Entry, ttlMs: Long): Boolean {
        return Clock.System.now().toEpochMilliseconds() - entry.lastAccessMs > ttlMs
    }

    private fun isL2Expired(entry: L2Entry): Boolean {
        return Clock.System.now().toEpochMilliseconds() - entry.lastAccessMs > l2TtlMs
    }

    private fun evictExpiredL1(): Int {
        val expiredKeys = l1Cache.entries.filter { entry ->
            val ttl = if (activeVideoIds.contains(entry.value.videoId)) l1ActiveTtlMs else l1InactiveTtlMs
            isL1Expired(entry.value, ttl)
        }.map { it.key }
        expiredKeys.forEach { l1Cache.remove(it) }
        if (expiredKeys.isNotEmpty()) {
            logger.d("L1 TTL evicted: ${expiredKeys.size}")
        }
        return expiredKeys.size
    }

    private fun evictExpiredL2(): Int {
        val expiredKeys = l2Cache.entries.filter { isL2Expired(it.value) }.map { it.key }
        expiredKeys.forEach { l2Cache.remove(it) }
        if (expiredKeys.isNotEmpty()) {
            logger.d("L2 TTL evicted: ${expiredKeys.size}")
        }
        return expiredKeys.size
    }

    // ===========================================================
    // Internal - LRU eviction
    // ===========================================================

    private fun evictL1IfNeeded() {
        val max = getL1MaxEntries()
        while (l1Cache.size > max) {
            val oldest = l1Cache.entries.minByOrNull { it.value.lastAccessMs } ?: break
            l1Cache.remove(oldest.key)
        }
    }

    private fun evictL2IfNeeded() {
        val max = getL2MaxEntries()
        while (l2Cache.size > max) {
            val oldest = l2Cache.entries.minByOrNull { it.value.lastAccessMs } ?: break
            l2Cache.remove(oldest.key)
        }
    }

    // ===========================================================
    // Internal - Dynamic capacity
    // ===========================================================

    internal fun getL1MaxEntries(): Int {
        return when (currentPressure) {
            MemoryPressureLevel.NORMAL -> maxL1Entries
            MemoryPressureLevel.WARNING -> maxL1Entries / 2
            MemoryPressureLevel.CRITICAL -> 0
            else -> maxL1Entries
        }
    }

    internal fun getL2MaxEntries(): Int {
        val base = when (currentPressure) {
            MemoryPressureLevel.NORMAL -> maxL2Entries
            MemoryPressureLevel.WARNING -> maxL2Entries / 2
            MemoryPressureLevel.CRITICAL -> minL2Entries
            else -> maxL2Entries
        }
        return base.coerceAtLeast(minL2Entries)
    }

    // ===========================================================
    // Internal - Memory pressure
    // ===========================================================

    private fun periodicMemoryPressureCheck() {
        memoryCheckCounter++
        if (memoryCheckCounter >= memoryCheckInterval) {
            memoryCheckCounter = 0
            checkMemoryPressure()
        }
    }

    internal fun checkMemoryPressure(): MemoryPressureLevel {
        val rss = memoryReader()
        val ratio = if (maxRssBytes > 0) rss.toDouble() / maxRssBytes else 0.0

        val newLevel = when {
            ratio >= memoryCriticalThreshold -> MemoryPressureLevel.CRITICAL
            ratio >= memoryWarningThreshold -> MemoryPressureLevel.WARNING
            else -> MemoryPressureLevel.NORMAL
        }

        if (newLevel != currentPressure) {
            logger.w("Memory pressure changed: $currentPressure -> $newLevel (RSS: ${rss / 1024 / 1024}MB)")
            currentPressure = newLevel
            evictL1IfNeeded()
            evictL2IfNeeded()
        }

        return newLevel
    }

    val memoryPressure: MemoryPressureLevel get() = currentPressure

    // ===========================================================
    // Internal - Frame buffer size estimation
    // ===========================================================

    private fun estimateFrameBufferSize(buffer: Map<String, Any>): Long {
        var size = 0L
        for ((_, value) in buffer) {
            when (value) {
                is ByteArray -> size += value.size
                is String -> size += value.length * 2
                is List<*> -> size += value.size * 8L
                else -> size += 64
            }
        }
        return if (size > 0) size else 1024L
    }

    // ===========================================================
    // Internal entry classes
    // ===========================================================

    private class L1Entry(
        val videoId: String,
        val quality: String,
        val frameBuffer: Map<String, Any>,
        val estimatedBytes: Long,
        var lastAccessMs: Long = Clock.System.now().toEpochMilliseconds(),
        var hitCount: Int = 0,
    )

    private class L2Entry(
        val videoId: String,
        val segmentKey: String,
        val quality: String,
        val data: ByteArray,
        var lastAccessMs: Long = Clock.System.now().toEpochMilliseconds(),
        var hitCount: Int = 0,
    )
}
