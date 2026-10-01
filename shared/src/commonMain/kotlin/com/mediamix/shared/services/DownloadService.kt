package com.mediamix.shared.services

import co.touchlab.kermit.Logger
import com.mediamix.shared.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 下载进度回调：[downloadedBytes] 为已写字节，[totalBytes] 为总字节（未知时 -1） */
typealias DownloadProgressCallback = (downloadedBytes: Long, totalBytes: Long) -> Unit

/**
 * 下载引擎。
 *
 * 修掉的是这条链路上最致命的三个问题：
 * 1. **m3u8 被当普通文件下载** —— 产物是一份文本清单，播放器打不开。
 *    现在解析 media playlist、并发拉分片、按序合并为一个可播文件。
 * 2. **不带请求头** —— 带防盗链的 CDN 直接 403。现在把播放时用的
 *    `header`（TVBox 蜘蛛给出）一并带上，且**贯穿到分片请求**。
 * 3. **无断点续传** —— 中断即从零开始。现在按「已下载的分片索引」续传，
 *    分片落在临时目录，完成一个记一个。
 *
 * 统一走 [HttpClientFactory]，与播放/接口共用 DNS 与连接池配置。
 */
class DownloadService(
    private val httpClient: HttpClient = HttpClientFactory.createHttpClient(requestTimeoutSeconds = 120),
    private val concurrency: Int = M3u8Parser.DEFAULT_CONCURRENCY,
) {
    private val logger = Logger.withTag("DownloadService")

    /**
     * 下载一个地址到 [targetPath]。
     *
     * HLS（m3u8）自动识别：能解析出分片列表就走分片合并，否则按普通文件流式写入。
     * 返回实际写入的字节数；抛异常表示失败（由调用方落库为 FAILED）。
     *
     * @param resume true 时从已有产物大小/已下分片继续
     * @param workspaceDir 分片临时目录（断点续传依赖它保留已下分片）
     */
    suspend fun download(
        url: String,
        targetPath: String,
        headers: Map<String, String> = emptyMap(),
        resume: Boolean = false,
        workspaceDir: String,
        onProgress: DownloadProgressCallback = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): Long {
        if (isHlsUrl(url)) {
            val result = downloadHls(url, targetPath, headers, resume, workspaceDir, onProgress, isCancelled)
            if (result != null) return result
            // m3u8 判定失败（响应不是真的清单）→ 退回普通文件下载，
            // 少数站点用 .m3u8 后缀返回直链 mp4。
            logger.w { "m3u8 解析失败，回退为普通文件下载: $url" }
        }
        return downloadPlain(url, targetPath, headers, resume, onProgress)
    }

    // ========================================================================
    // 普通文件
    // ========================================================================

    private suspend fun downloadPlain(
        url: String,
        targetPath: String,
        headers: Map<String, String>,
        resume: Boolean,
        onProgress: DownloadProgressCallback,
    ): Long =
        withContext(Dispatchers.IO) {
            val sink = createDownloadFileSink(targetPath, resume)
            sink.open()
            try {
                val startOffset = if (resume) sink.bytesWritten() else 0L
                val response =
                    httpClient.get(url) {
                        headers.forEach { (k, v) -> header(k, v) }
                        if (startOffset > 0L) header("Range", "bytes=$startOffset-")
                    }
                // 非 2xx 时 body 是错误页，写进产物只会得到一个坏文件
                check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
                val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                val total = if (contentLength > 0) contentLength + startOffset else -1L

                val channel = response.bodyAsChannel()
                val buffer = ByteArray(BUFFER_SIZE)
                var written = startOffset
                var sinceReport = 0L
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    sink.append(buffer, read)
                    written += read
                    sinceReport += read
                    // 每 256KB 汇报一次，避免进度回调把主线程刷爆
                    if (sinceReport >= PROGRESS_REPORT_BYTES) {
                        sinceReport = 0
                        onProgress(written, total)
                    }
                }
                sink.close()
                onProgress(written, total)
                written
            } catch (e: CancellationException) {
                sink.close()
                throw e
            } catch (e: Exception) {
                sink.close()
                throw e
            }
        }

    // ========================================================================
    // HLS
    // ========================================================================

    /**
     * 下载 HLS 流。
     *
     * 返回 null 表示「拿到的不是可解析的清单」，由调用方回退普通下载。
     * 抛 [DownloadFailedException] 表示确实识别出是 HLS，但无法合并（加密流、分片缺失等）——
     * 这种情况**必须报错**：产出一个无法播放的文件比明确失败更糟。
     */
    private suspend fun downloadHls(
        url: String,
        targetPath: String,
        headers: Map<String, String>,
        resume: Boolean,
        workspaceDir: String,
        onProgress: DownloadProgressCallback,
        isCancelled: () -> Boolean,
    ): Long? {
        val media = loadMediaPlaylist(url, headers) ?: return null
        if (media.segments.isEmpty()) return null
        if (media.encrypted) {
            throw DownloadFailedException("该 HLS 流已加密（EXT-X-KEY），暂不支持离线下载")
        }

        val segmentDir = "$workspaceDir/segments"
        val doneSegments = if (resume) M3u8SegmentStore.loadCompleted(segmentDir) else emptySet()
        if (!resume) M3u8SegmentStore.clear(segmentDir)

        val totalSegments = media.segments.size
        if (doneSegments.size > totalSegments) {
            throw DownloadFailedException("已有分片数超过总分片数，缓存目录可能被污染")
        }
        if (doneSegments.isNotEmpty()) {
            logger.i { "HLS 续传：已有 ${doneSegments.size}/$totalSegments 个分片" }
        }
        onProgress(doneSegments.size.toLong(), totalSegments.toLong())

        val sink = createDownloadFileSink(targetPath, resume = false)
        try {
            fetchSegments(media.segments, doneSegments, segmentDir, headers, semaphoreFor(totalSegments), onProgress, isCancelled)
            val total = mergeSegments(sink, segmentDir, totalSegments)
            onProgress(totalSegments.toLong(), totalSegments.toLong())
            M3u8SegmentStore.clear(segmentDir)
            logger.i { "HLS 下载完成：$totalSegments 个分片，${total / 1024}KB" }
            return total
        } catch (e: Throwable) {
            // 取消时保留分片目录，下次 resume 继续；失败同样保留（已下的分片仍有价值）
            sink.close()
            throw e
        }
    }

    /**
     * 取到「真正的分片清单」。
     *
     * 主列表（多码率）需要再下一层；拿到的东西不像清单时返回 null，
     * 让调用方回退普通文件下载（部分站点用 .m3u8 后缀返回直链 mp4）。
     */
    private suspend fun loadMediaPlaylist(
        url: String,
        headers: Map<String, String>,
    ): M3u8Playlist.Media? {
        val root = fetchText(url, headers) ?: return null
        return when (val playlist = M3u8Parser.parse(root, url)) {
            is M3u8Playlist.Media -> playlist

            is M3u8Playlist.Master -> {
                val best = M3u8Parser.pickBestVariant(playlist.variants) ?: return null
                logger.i { "HLS 主列表：选 ${best.resolution ?: "?"}@${best.bandwidth} 的分支" }
                val text = fetchText(best.url, headers) ?: return null
                M3u8Parser.parse(text, best.url) as? M3u8Playlist.Media
            }

            M3u8Playlist.Unsupported -> null
        }
    }

    /** 并发拉取缺失的分片；每完成一个即落盘并上报进度，便于中断后续传。 */
    private suspend fun fetchSegments(
        segments: List<String>,
        doneSegments: Set<Int>,
        segmentDir: String,
        headers: Map<String, String>,
        semaphore: Semaphore,
        onProgress: DownloadProgressCallback,
        isCancelled: () -> Boolean,
    ) {
        val totalSegments = segments.size
        val completed = java.util.Collections.synchronizedSet(mutableSetOf<Int>())
        segments.forEachIndexed { index, segmentUrl ->
            if (isCancelled()) throw CancellationException("下载已取消")
            if (index in doneSegments) return@forEachIndexed
            // 限流：分片小而多，适度并发能显著提速；写完一个立刻记账，便于续传。
            semaphore.withPermit {
                val bytes =
                    fetchBytes(segmentUrl, headers)
                        ?: throw DownloadFailedException("分片 $index 下载失败")
                M3u8SegmentStore.save(segmentDir, index, bytes)
                completed.add(index)
                onProgress((doneSegments.size + completed.size).toLong(), totalSegments.toLong())
            }
        }
    }

    /** 按序把分片合并成单文件 —— 顺序不能靠并发完成顺序，必须是清单顺序。 */
    private suspend fun mergeSegments(
        sink: DownloadFileSink,
        segmentDir: String,
        totalSegments: Int,
    ): Long {
        var total = 0L
        sink.open()
        for (index in 0 until totalSegments) {
            val bytes =
                M3u8SegmentStore.load(segmentDir, index)
                    ?: throw DownloadFailedException("分片 $index 缺失，请重试续传")
            sink.append(bytes, bytes.size)
            total += bytes.size
        }
        sink.close()
        return total
    }

    private fun semaphoreFor(totalSegments: Int): Semaphore = Semaphore(minOf(concurrency, totalSegments.coerceAtLeast(1)))

    private suspend fun fetchText(
        url: String,
        headers: Map<String, String>,
    ): String? =
        try {
            httpClient.get(url) { headers.forEach { (k, v) -> header(k, v) } }.bodyAsChannel().let { channel ->
                readPlaylistText(channel)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "拉取 m3u8 失败: ${e.message}" }
            null
        }

    /** 读清单文本；超过上限视为「不是清单」，返回 null 让调用方回退。 */
    private suspend fun readPlaylistText(channel: ByteReadChannel): String? {
        val buffer = StringBuilder()
        val bytes = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = channel.readAvailable(bytes)
            if (read <= 0) return buffer.toString()
            buffer.append(bytes.decodeToString(0, read))
            if (buffer.length > MAX_PLAYLIST_CHARS) return null
        }
    }

    private suspend fun fetchBytes(
        url: String,
        headers: Map<String, String>,
    ): ByteArray? =
        try {
            val response = httpClient.get(url) { headers.forEach { (k, v) -> header(k, v) } }
            if (response.status.isSuccess()) response.readRawBytes() else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "分片下载失败: ${e.message}" }
            null
        }

    /** 是否看起来像 HLS。 */
    fun isHlsUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains(".ts?")
    }

    fun close() {
        httpClient.close()
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_REPORT_BYTES = 256 * 1024L
        const val MAX_PLAYLIST_CHARS = 4 * 1024 * 1024
    }
}

/**
 * 下载过程中的确定性失败（加密流、分片缺失等）。
 *
 * 与「拿到的不是 m3u8 清单」区分开：后者返回 null，让调用方回退普通文件下载；
 * 前者必须直接失败 —— 产出一个无法播放的文件，比明确告知用户失败更糟。
 */
class DownloadFailedException(
    message: String,
) : Exception(message)
