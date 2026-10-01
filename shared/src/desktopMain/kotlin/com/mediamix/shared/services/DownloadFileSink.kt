package com.mediamix.shared.services

import java.io.File
import java.io.RandomAccessFile

/**
 * JVM（Android / Desktop）侧的下载写入端。
 *
 * 用 `RandomAccessFile` 而不是 `FileOutputStream`：断点续传需要「定位到已有长度再追加」，
 * 而 `FileOutputStream(append = true)` 在并发/中断场景下的可观测性更差。
 */
private class JvmDownloadFileSink(
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
): DownloadFileSink = JvmDownloadFileSink(path, resume)
