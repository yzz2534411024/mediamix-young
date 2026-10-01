package com.mediamix.shared.cache

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.CacheEntry
import com.mediamix.shared.models.SegmentCacheResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * L3/L4 disk cache layer with in-memory index.
 *
 * - L3: full video file cache
 * - L4: segment file cache (HLS/DASH)
 * - CacheIndex: JSON-persisted disk index
 */
class DiskCache(
    private val cacheDir: String,
) {
    private val logger = Logger.withTag("DiskCache")

    // ----------------------------------------------------------
    // Disk index (in-memory, persisted to JSON)
    // ----------------------------------------------------------
    private val diskIndex = mutableMapOf<String, CacheEntry>()
    private var cachedDiskTotalSize: Long = 0L

    private val indexFile: String get() = "$cacheDir${File.separator}cache_index.json"
    private val segmentDir: String get() = "$cacheDir${File.separator}segments"

    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // ===========================================================
    // Initialization
    // ===========================================================

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                File(cacheDir).mkdirs()
                File(segmentDir).mkdirs()
                loadIndex()
                logger.i("DiskCache initialized, index entries: ${diskIndex.size}")
            } catch (e: Exception) {
                logger.e(e) { "DiskCache initialization failed" }
            }
        }
    }

    // ===========================================================
    // L3 Video Cache
    // ===========================================================

    suspend fun putVideo(
        videoId: String,
        filePath: String,
        quality: String = "default",
        priority: Int = 0,
        ttl: Int = 604800,
        category: String? = null,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val sourceFile = File(filePath)
                if (!sourceFile.exists()) {
                    logger.w("Source file not found, cannot cache: $filePath")
                    return@withContext
                }

                val fileSize = sourceFile.length()
                val cacheId = hashKey("${videoId}_$quality")
                val ext = sourceFile.extension
                val destPath = "$cacheDir${File.separator}$cacheId.$ext"

                val destFile = File(destPath)
                if (destFile.exists()) {
                    destFile.delete()
                }

                sourceFile.copyTo(destFile)

                val now = Clock.System.now().toEpochMilliseconds()
                val entry =
                    CacheEntry(
                        cacheId = cacheId,
                        videoId = videoId,
                        quality = quality,
                        filePath = destPath,
                        fileSize = fileSize,
                        priority = priority,
                        ttl = ttl,
                        isComplete = true,
                        createdAt = now,
                        lastAccess = now,
                    )

                diskIndex[cacheId] = entry
                cachedDiskTotalSize += fileSize
                saveIndex()

                logger.i("Video cached(L3): $videoId@$quality, size: ${fileSize / 1024 / 1024}MB")
            } catch (e: Exception) {
                logger.e(e) { "Failed to cache video: $videoId" }
            }
        }
    }

    fun getCachePath(
        videoId: String,
        quality: String = "default",
    ): String? {
        val cacheId = hashKey("${videoId}_$quality")
        val entry = diskIndex[cacheId] ?: return null

        if (entry.isExpired(Clock.System.now().toEpochMilliseconds())) return null
        if (!entry.isComplete) return null
        if (!File(entry.filePath).exists()) return null

        // Update hit info
        updateHitCount(cacheId)
        return entry.filePath
    }

    fun getAnyQualityCachePath(
        videoId: String,
        preferredQuality: String? = null,
    ): Pair<String, String>? {
        val now = Clock.System.now().toEpochMilliseconds()

        // 1. Try preferred quality first
        if (preferredQuality != null) {
            val path = getCachePath(videoId, preferredQuality)
            if (path != null) return Pair(path, preferredQuality)
        }

        // 2. Quality priority order
        val qualityPriority = listOf("瓒呮竻", "楂樻竻", "鏍囨竻", "娴佺晠")
        for (q in qualityPriority) {
            if (q == preferredQuality) continue
            val path = getCachePath(videoId, q)
            if (path != null) return Pair(path, q)
        }

        // 3. Fallback: any quality in disk index
        for (entry in diskIndex.values) {
            if (entry.videoId == videoId && !entry.isExpired(now) && entry.isComplete) {
                if (File(entry.filePath).exists()) {
                    updateHitCount(entry.cacheId)
                    return Pair(entry.filePath, entry.quality)
                }
            }
        }

        return null
    }

    // ===========================================================
    // L4 Segment Cache
    // ===========================================================

    suspend fun putSegment(
        videoId: String,
        segmentKey: String,
        data: ByteArray,
        quality: String = "default",
    ) {
        withContext(Dispatchers.IO) {
            try {
                val cacheId = hashKey("${videoId}_$quality")
                val segFileName = "${cacheId}_$segmentKey.seg"
                val segPath = "$segmentDir${File.separator}$segFileName"

                File(segPath).writeBytes(data)

                val now = Clock.System.now().toEpochMilliseconds()
                val existing = diskIndex[cacheId]
                if (existing != null) {
                    val updatedSegments =
                        if (existing.segments.contains(segmentKey)) {
                            existing.segments
                        } else {
                            existing.segments + segmentKey
                        }
                    diskIndex[cacheId] =
                        existing.copy(
                            segments = updatedSegments,
                            fileSize = existing.fileSize + data.size,
                            lastAccess = now,
                        )
                } else {
                    diskIndex[cacheId] =
                        CacheEntry(
                            cacheId = cacheId,
                            videoId = videoId,
                            quality = quality,
                            filePath = segPath,
                            fileSize = data.size.toLong(),
                            segments = listOf(segmentKey),
                            isComplete = false,
                            createdAt = now,
                            lastAccess = now,
                        )
                }
                cachedDiskTotalSize += data.size

                saveIndex()
                logger.d("Segment cached: $videoId/$segmentKey@$quality, size: ${data.size}B")
            } catch (e: Exception) {
                logger.e(e) { "Failed to cache segment: $videoId/$segmentKey" }
            }
        }
    }

    fun getSegment(
        videoId: String,
        segmentKey: String,
        quality: String = "default",
    ): SegmentCacheResult? {
        val cacheId = hashKey("${videoId}_$quality")
        val diskEntry = diskIndex[cacheId] ?: return null

        if (!diskEntry.segments.contains(segmentKey)) return null

        val segPath = "$segmentDir${File.separator}${cacheId}_$segmentKey.seg"
        val segFile = File(segPath)
        if (!segFile.exists()) return null

        updateHitCount(cacheId)
        return SegmentCacheResult(hit = true, path = segPath)
    }

    // ===========================================================
    // Index management
    // ===========================================================

    fun hasCache(
        videoId: String,
        quality: String = "default",
    ): Boolean {
        val cacheId = hashKey("${videoId}_$quality")
        val entry = diskIndex[cacheId] ?: return false
        if (entry.isExpired(Clock.System.now().toEpochMilliseconds())) return false
        return File(entry.filePath).exists()
    }

    fun getAllEntries(): List<CacheEntry> = diskIndex.values.toList()

    fun getEntry(key: String): CacheEntry? = diskIndex[key]

    fun removeEntry(key: String) {
        val entry = diskIndex[key] ?: return
        try {
            if (entry.isComplete) {
                File(entry.filePath).deleteRecursively()
            } else {
                for (seg in entry.segments) {
                    File("$segmentDir${File.separator}${key}_$seg.seg").delete()
                }
            }
        } catch (e: Exception) {
            logger.w("Failed to delete cache file: ${entry.filePath}, error: $e")
        }
        cachedDiskTotalSize -= entry.fileSize
        diskIndex.remove(key)
    }

    suspend fun saveIndex() {
        withContext(Dispatchers.IO) {
            try {
                val tempFile = File("$cacheDir${File.separator}cache_index.json.tmp")
                val indexData = IndexData(entries = diskIndex.toMap())
                val jsonStr = json.encodeToString(indexData)

                tempFile.writeText(jsonStr)
                val target = File(indexFile)
                if (target.exists()) target.delete()
                tempFile.renameTo(target)
            } catch (e: Exception) {
                logger.e(e) { "Failed to save disk index" }
            }
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                File(cacheDir).deleteRecursively()
                File(cacheDir).mkdirs()
                File(segmentDir).mkdirs()
            } catch (e: Exception) {
                logger.e(e) { "Failed to clear disk cache" }
            }
            diskIndex.clear()
            cachedDiskTotalSize = 0L
            saveIndex()
        }
    }

    fun getDiskTotalSize(): Long = cachedDiskTotalSize

    fun updateHitInfo(key: String) {
        val entry = diskIndex[key] ?: return
        diskIndex[key] =
            entry.copy(
                hitCount = entry.hitCount + 1,
                lastAccess = Clock.System.now().toEpochMilliseconds(),
            )
    }

    // ===========================================================
    // Internal
    // ===========================================================

    private fun loadIndex() {
        try {
            val file = File(indexFile)
            if (!file.exists()) return

            val jsonStr = file.readText()
            val indexData = json.decodeFromString<IndexData>(jsonStr)

            diskIndex.clear()
            diskIndex.putAll(indexData.entries)
            recomputeDiskTotal()
            logger.d("Disk index loaded, entries: ${diskIndex.size}")
        } catch (e: Exception) {
            logger.w("Failed to load disk index: $e")
        }
    }

    private fun updateHitCount(key: String) {
        val entry = diskIndex[key] ?: return
        diskIndex[key] =
            entry.copy(
                hitCount = entry.hitCount + 1,
                lastAccess = Clock.System.now().toEpochMilliseconds(),
            )
    }

    private fun recomputeDiskTotal() {
        cachedDiskTotalSize = 0L
        for (entry in diskIndex.values) {
            cachedDiskTotalSize += entry.fileSize
        }
    }

    /**
     * Java-style hashCode matching Dart's hashKey implementation.
     * Computes integer hashCode same as Java String.hashCode, then converts to base-36.
     */
    private fun hashKey(input: String): String {
        var hash = 0
        for (i in input.indices) {
            hash = ((hash shl 5) - hash) + input[i].code
            hash = hash and 0x7FFFFFFF
        }
        return hash.toString(36)
    }

    // ===========================================================
    // Index serialization wrapper
    // ===========================================================

    @kotlinx.serialization.Serializable
    private data class IndexData(
        val entries: Map<String, CacheEntry> = emptyMap(),
    )
}
