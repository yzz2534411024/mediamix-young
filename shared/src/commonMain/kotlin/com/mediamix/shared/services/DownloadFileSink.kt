package com.mediamix.shared.services

/**
 * 下载写入端 —— 把平台文件 IO 收在一处。
 *
 * **为什么需要它**：`DownloadViewModel` 原先放在 `commonMain` 却直接
 * `import java.io.File` 并调用 `targetFile.outputStream()` —— 这是「伪 common」，
 * 一旦新增非 JVM 目标就编译不过。把 IO 下沉到 expect/actual 后，
 * `commonMain` 只剩纯逻辑（HLS 解析、分片顺序、进度计算），可以在 Desktop 上直接测。
 */
interface DownloadFileSink {
    /** 确保目标文件可用，并可追加写入 */
    fun open()

    /** 追加一段数据 */
    fun append(
        data: ByteArray,
        length: Int,
    )

    /** 已写入的字节数（断点续传的判定依据） */
    fun bytesWritten(): Long

    /** 落盘并关闭 */
    fun close()

    /** 删除目标文件（含残留） */
    fun delete()

    /** 文件是否已存在且非空 */
    fun exists(): Boolean
}

/**
 * 创建写入端。[path] 为最终产物路径（如 `xxx.mp4`）。
 *
 * [resume] 为 true 时追加到已有文件末尾，否则先清空重写。
 */
expect fun createDownloadFileSink(
    path: String,
    resume: Boolean,
): DownloadFileSink
