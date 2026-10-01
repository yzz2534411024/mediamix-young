package com.mediamix.shared.services

import java.io.File
import java.io.RandomAccessFile

/**
 * Android 侧的下载写入端。
 *
 * 与 Desktop 实现逻辑一致（都用 `RandomAccessFile` 支持追加），
 * 但刻意分开声明：Android 的存储路径来自 `PlatformPaths.downloadDir`
 * （应用专属外部目录，受 scoped storage 约束），
 * 后续若要接入 MediaStore 或 SAF 只需改这一处。
 */
private class AndroidDownloadFileSink(
    private val path: String,
    private val resume: Boolean,
) : DownloadFileSink {
    private var raf: RandomAccessFile? = null

    override fun open() {
        val file = File(path)
        file.parentFile?.mkdirs()
        if (!resume && file.exists()) file.delete()
        val raf = RandomAccessFile(file, "rw")
        if (resume) raf.seek(raf.length())
        this.raf = raf
    }

    override fun append(
        data: ByteArray,
        length: Int,
    ) {
        raf?.write(data, 0, length)
    }

    override fun bytesWritten(): Long = raf?.length() ?: 0L

    override fun close() {
        raf?.close()
        raf = null
    }

    override fun delete() {
        close()
        File(path).delete()
    }

    override fun exists(): Boolean = File(path).let { it.exists() && it.length() > 0L }
}

actual fun createDownloadFileSink(
    path: String,
    resume: Boolean,
): DownloadFileSink = AndroidDownloadFileSink(path, resume)
