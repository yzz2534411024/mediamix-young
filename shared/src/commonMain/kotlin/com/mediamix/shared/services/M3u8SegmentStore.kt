package com.mediamix.shared.services

import java.io.File

/**
 * HLS 分片暂存 —— 断点续传的落点。
 *
 * 每个分片单独一个文件（`<index>.seg`），**完成一个记一个**：
 * 下载中断后重启，已存在的分片直接复用，只补缺失的那些。
 * 分片写文件而不是留在内存，是因为一集 1080p 的分片总量可能上百 MB。
 *
 * ⚠️ 合并成功后必须 [clear]，否则临时目录会随下载次数无限增长。
 */
internal object M3u8SegmentStore {
    fun save(
        dir: String,
        index: Int,
        bytes: ByteArray,
    ) {
        val file = File(dir).apply { mkdirs() }
        File(file, fileName(index)).writeBytes(bytes)
    }

    fun load(
        dir: String,
        index: Int,
    ): ByteArray? {
        val file = File(dir, fileName(index))
        if (!file.exists() || file.length() == 0L) return null
        return file.readBytes()
    }

    /** 已下载完成的分片下标集合。 */
    fun loadCompleted(dir: String): Set<Int> {
        val files = File(dir).listFiles() ?: return emptySet()
        return files
            .mapNotNull { it.name.removeSuffix(SUFFIX).toIntOrNull() }
            .toSet()
    }

    fun clear(dir: String) {
        File(dir).deleteRecursively()
    }

    private fun fileName(index: Int): String = "$index$SUFFIX"

    private const val SUFFIX = ".seg"
}
