package com.mediamix.shared.cache

import com.mediamix.shared.player.QualityLevel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 画质优先级与缓存清理的回归测试。
 *
 * 这里锁定的是一个「静默失效」的坑：`getAnyQualityCachePath` 里的画质数组
 * 曾是 **GBK 乱码**（`瓒呮竻/楂樻竻/…` = 超清/高清/… 被按 GBK 解码 UTF-8 字节的产物），
 * 与写入端（[QualityLevel.label]）永远对不上 —— 该分支从未命中过，
 * 而且不会报错，只会「回退到任意画质」。所以必须有测试盯着。
 */
class DiskCacheQualityTest {
    private val dir = "build/tmp/disk-cache-quality-test"

    @Test
    fun qualityLabelsAreTheOnesWrittenIntoTheIndex() {
        // 与 DiskCache.getAnyQualityCachePath 内部用的是同一份枚举，
        // 因此「写入端标签」与「查找端标签」不可能再对不上。
        assertEquals(listOf("流畅", "标清", "高清", "超清"), QualityLevel.entries.map { it.label })
    }

    @Test
    fun lookupUsesExactlyTheLabelsUsedWhenPutting() =
        runTest {
            val cache = DiskCache("build/tmp/disk-cache-labels")
            cache.initialize()
            cache.clearAll()
            cache.putVideo(videoId = "v1", filePath = tempFile("a.mp4"), quality = QualityLevel.HIGH.label)

            // 首选画质不匹配 → 必须能通过画质优先级找到已缓存的那一档。
            // 这正是当初用 GBK 乱码常量时永久失效的分支。
            val found = cache.getAnyQualityCachePath("v1", preferredQuality = "720p")

            assertEquals(QualityLevel.HIGH.label, found?.second)
        }

    @Test
    fun anyQualityCachePathFindsTheLabelUsedWhenPutting() =
        runTest {
            val cache = DiskCache(dir)
            cache.initialize()
            cache.clearAll()
            cache.putVideo(
                videoId = "v1",
                filePath = tempFile("a.mp4"),
                quality = QualityLevel.ULTRA.label,
            )

            // 首选画质不匹配时，应当通过「画质优先级」找到已缓存的那一档
            val found = cache.getAnyQualityCachePath("v1", preferredQuality = "720p")

            assertEquals(QualityLevel.ULTRA.label, found?.second)
            assertTrue(java.io.File(found!!.first).exists())
        }

    @Test
    fun clearAllRemovesEverything() =
        runTest {
            val cache = DiskCache(dir)
            cache.initialize()
            cache.putVideo(videoId = "v2", filePath = tempFile("b.mp4"), quality = QualityLevel.HIGH.label)
            assertTrue(cache.getDiskTotalSize() > 0L)

            cache.clearAll()

            assertEquals(0L, cache.getDiskTotalSize())
            assertTrue(cache.getAllEntries().isEmpty())
        }

    private fun tempFile(name: String): String {
        val file = java.io.File(dir, name)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(2048) { 1 })
        return file.absolutePath
    }
}
