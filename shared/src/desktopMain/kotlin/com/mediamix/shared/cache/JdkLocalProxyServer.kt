package com.mediamix.shared.cache

import co.touchlab.kermit.Logger
import com.mediamix.shared.network.HttpClientFactory
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Desktop 平台的本地代理实现，基于 JDK 自带的 `com.sun.net.httpserver`。
 *
 * 完整支持：L3 整文件缓存直出、L4 分片拼装、CDN 转发 + 边下边存（512KB 分片）、
 * HTTP Range（含 206 / 416）。单例由 Koin 的 `single {}` 保证，本类不做静态持有。
 */
class JdkLocalProxyServer(
    private val cacheService: VideoCacheService,
) : LocalProxyServer {

    private val logger = Logger.withTag("LocalProxyServer")
    private var server: HttpServer? = null
    private var port = 0
    private var streamingClient: HttpClient? = null

    override val currentPort: Int get() = port
    override val isRunning: Boolean get() = server != null

    // ----------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------

    override fun start() {
        if (server != null) return
        try {
            val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            port = srv.address.port
            srv.createContext("/vod") { exchange -> handleRequest(exchange) }
            srv.executor = null // use default thread pool
            srv.start()
            server = srv
            streamingClient = HttpClientFactory.createStreamingClient()
            logger.i { "Local proxy started: http://127.0.0.1:$port" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to start local proxy" }
            server = null
            port = 0
        }
    }

    override fun stop() {
        try {
            streamingClient?.close()
            streamingClient = null
        } catch (_: Exception) {}
        server?.stop(0)
        server = null
        port = 0
        logger.i { "Local proxy stopped" }
    }

    override fun proxyUrl(cdnUrl: String, videoId: String, quality: String): String {
        val encoded = URLEncoder.encode(cdnUrl, "UTF-8")
        return "http://127.0.0.1:$port/vod/$videoId?url=$encoded&quality=$quality"
    }

    // ----------------------------------------------------------
    // Request handling
    // ----------------------------------------------------------

    private fun handleRequest(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path
            val segments = path.split("/").filter { it.isNotEmpty() }

            if (segments.size < 2 || segments[0] != "vod") {
                sendError(exchange, 404, "Not Found")
                return
            }

            val videoId = segments[1]
            val query = parseQueryString(exchange.requestURI.query ?: "")
            val cdnUrl = query["url"] ?: ""
            val quality = query["quality"] ?: "720p"

            if (cdnUrl.isEmpty()) {
                sendError(exchange, 400, "Missing url parameter")
                return
            }

            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            logger.d { "Proxy request: $videoId, Range: ${rangeHeader ?: "none"}" }

            // 1. Check L3 complete file cache
            val cachePath = cacheService.getCachePath(videoId, quality)
            if (cachePath != null) {
                val file = File(cachePath)
                if (file.exists()) {
                    serveFile(exchange, file, rangeHeader)
                    return
                }
            }

            // 2. Try L4 segment assembly
            val segmentPaths = getSegmentsForVideo(videoId, quality)
            if (segmentPaths.isNotEmpty()) {
                serveFromSegments(exchange, segmentPaths, rangeHeader)
                return
            }

            // 3. No cache — proxy from CDN and cache on-the-fly
            proxyAndCache(exchange, cdnUrl, videoId, quality)
        } catch (e: Exception) {
            logger.e(e) { "Proxy request handling failed" }
            try {
                sendError(exchange, 500, "Internal Error")
            } catch (_: Exception) {}
        }
    }

    // ----------------------------------------------------------
    // Segment probing
    // ----------------------------------------------------------

    /**
     * Probe L4 segments by incrementing index 0..199,
     * checking both "preload_{i}" and "seg_{i}" keys.
     */
    private fun getSegmentsForVideo(videoId: String, quality: String): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until 200) {
            val preloadResult = cacheService.getSegment(videoId, "preload_$i", quality)
            if (preloadResult.hit && preloadResult.path != null) {
                result.add(preloadResult.path!!)
            } else {
                val segResult = cacheService.getSegment(videoId, "seg_$i", quality)
                if (segResult.hit && segResult.path != null) {
                    result.add(segResult.path!!)
                }
            }
        }
        return result
    }

    // ----------------------------------------------------------
    // Serve from complete file cache
    // ----------------------------------------------------------

    private fun serveFile(exchange: HttpExchange, file: File, rangeHeader: String?) {
        val fileSize = file.length()

        if (rangeHeader != null) {
            val range = parseRange(rangeHeader, fileSize)
            if (range == null) {
                exchange.responseHeaders.set("Content-Range", "bytes */$fileSize")
                exchange.sendResponseHeaders(416, -1)
                exchange.close()
                return
            }
            val (start, end) = range
            val length = end - start + 1

            exchange.responseHeaders.set("Content-Type", "video/mp4")
            exchange.responseHeaders.set("Content-Length", length.toString())
            exchange.responseHeaders.set("Content-Range", "bytes $start-$end/$fileSize")
            exchange.responseHeaders.set("Accept-Ranges", "bytes")
            exchange.sendResponseHeaders(206, length)

            val raf = RandomAccessFile(file, "r")
            try {
                raf.seek(start)
                var remaining = length
                val buf = ByteArray(8192)
                val os = exchange.responseBody
                while (remaining > 0) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    val read = raf.read(buf, 0, toRead)
                    if (read <= 0) break
                    os.write(buf, 0, read)
                    remaining -= read
                }
            } finally {
                raf.close()
            }
        } else {
            exchange.responseHeaders.set("Content-Type", "video/mp4")
            exchange.responseHeaders.set("Content-Length", fileSize.toString())
            exchange.responseHeaders.set("Accept-Ranges", "bytes")
            exchange.sendResponseHeaders(200, fileSize)

            val fis = FileInputStream(file)
            try {
                val buf = ByteArray(8192)
                val os = exchange.responseBody
                var read: Int
                while (fis.read(buf).also { read = it } != -1) {
                    os.write(buf, 0, read)
                }
            } finally {
                fis.close()
            }
        }
        exchange.close()
    }

    // ----------------------------------------------------------
    // Serve from segments
    // ----------------------------------------------------------

    private fun serveFromSegments(
        exchange: HttpExchange,
        segmentPaths: List<String>,
        rangeHeader: String?,
    ) {
        // Calculate total size and collect existing files
        var totalSize = 0L
        val files = mutableListOf<File>()
        for (path in segmentPaths) {
            val f = File(path)
            if (f.exists()) {
                totalSize += f.length()
                files.add(f)
            }
        }

        if (files.isEmpty()) {
            sendError(exchange, 404, "Segments missing")
            return
        }

        exchange.responseHeaders.set("Content-Type", "video/mp4")
        exchange.responseHeaders.set("Accept-Ranges", "bytes")

        if (rangeHeader != null) {
            val range = parseRange(rangeHeader, totalSize)
            if (range == null) {
                exchange.sendResponseHeaders(416, -1)
                exchange.close()
                return
            }
            val (start, end) = range
            val length = end - start + 1
            exchange.responseHeaders.set("Content-Length", length.toString())
            exchange.responseHeaders.set("Content-Range", "bytes $start-$end/$totalSize")
            exchange.sendResponseHeaders(206, length)
            streamSegmentRange(exchange, files, start, end)
        } else {
            exchange.responseHeaders.set("Content-Length", totalSize.toString())
            exchange.sendResponseHeaders(200, totalSize)
            val os = exchange.responseBody
            for (file in files) {
                val fis = FileInputStream(file)
                try {
                    val buf = ByteArray(8192)
                    var read: Int
                    while (fis.read(buf).also { read = it } != -1) {
                        os.write(buf, 0, read)
                    }
                } finally {
                    fis.close()
                }
            }
        }
        exchange.close()
    }

    /**
     * Stream bytes from segment files for the given byte range.
     */
    private fun streamSegmentRange(
        exchange: HttpExchange,
        files: List<File>,
        rangeStart: Long,
        rangeEnd: Long,
    ) {
        var offset = 0L
        var remaining = rangeEnd - rangeStart + 1
        val os = exchange.responseBody

        for (file in files) {
            val fileSize = file.length()
            val fileStart = offset
            val fileEnd = offset + fileSize - 1

            if (fileEnd >= rangeStart && fileStart <= rangeEnd) {
                val segStart = maxOf(0L, minOf(rangeStart - fileStart, fileSize - 1))
                val segEnd = maxOf(0L, minOf(rangeEnd - fileStart, fileSize - 1))

                val raf = RandomAccessFile(file, "r")
                try {
                    raf.seek(segStart)
                    var segRemaining = segEnd - segStart + 1
                    val buf = ByteArray(8192)
                    while (segRemaining > 0 && remaining > 0) {
                        val toRead = minOf(buf.size.toLong(), minOf(segRemaining, remaining)).toInt()
                        val read = raf.read(buf, 0, toRead)
                        if (read <= 0) break
                        os.write(buf, 0, read)
                        segRemaining -= read
                        remaining -= read
                    }
                } finally {
                    raf.close()
                }
            }

            offset += fileSize
            if (remaining <= 0) break
        }
        exchange.close()
    }

    // ----------------------------------------------------------
    // Proxy CDN and cache on-the-fly
    // ----------------------------------------------------------

    private fun proxyAndCache(
        exchange: HttpExchange,
        cdnUrl: String,
        videoId: String,
        quality: String,
    ) {
        val rangeHeader = exchange.requestHeaders.getFirst("Range")
        val client = streamingClient ?: run {
            sendError(exchange, 500, "Streaming client not available")
            return
        }

        val downloadStartMs = System.currentTimeMillis()
        var totalDownloadBytes = 0L

        try {
            runBlocking {
                val response = client.get(cdnUrl) {
                    if (rangeHeader != null) {
                        headers.append("Range", rangeHeader)
                    }
                }

                val statusCode = response.status.value
                val contentLength = response.headers["Content-Length"]
                val contentType = response.headers["Content-Type"]

                if (contentType != null) {
                    exchange.responseHeaders.set("Content-Type", contentType)
                }
                if (contentLength != null) {
                    exchange.responseHeaders.set("Content-Length", contentLength)
                }
                exchange.responseHeaders.set("Accept-Ranges", "bytes")

                val contentLen = contentLength?.toLongOrNull() ?: -1L
                exchange.sendResponseHeaders(statusCode, contentLen)

                val os = exchange.responseBody
                val channel = response.bodyAsChannel()
                val buf = ByteArray(8192)

                var segIndex = 0
                var segBytes = 0
                var segBuffer = SegBuffer()
                val segMaxBytes = 512 * 1024 // 512KB per segment

                while (true) {
                    val read = channel.readAvailable(buf, 0, buf.size)
                    if (read <= 0) break

                    os.write(buf, 0, read)
                    totalDownloadBytes += read

                    // Accumulate into segment buffer
                    segBuffer.write(buf, 0, read)
                    segBytes += read

                    // Flush every 512KB as a segment
                    if (segBytes >= segMaxBytes) {
                        val data = segBuffer.toByteArray()
                        cacheService.putSegment(videoId, "seg_$segIndex", data, quality)
                        segIndex++
                        segBuffer = SegBuffer()
                        segBytes = 0
                    }
                }

                // Write remaining data as final segment
                if (segBuffer.size() > 0) {
                    val data = segBuffer.toByteArray()
                    cacheService.putSegment(videoId, "seg_$segIndex", data, quality)
                }

                val elapsed = System.currentTimeMillis() - downloadStartMs
                logger.d {
                    "Proxy cache done: $videoId, ${segIndex + 1} segments, " +
                        "${totalDownloadBytes / 1024}KB, ${elapsed}ms"
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Proxy CDN request failed" }
            try {
                sendError(exchange, 502, "Bad Gateway")
            } catch (_: Exception) {}
            return
        }
        exchange.close()
    }

    // ----------------------------------------------------------
    // Utilities
    // ----------------------------------------------------------

    private fun parseQueryString(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (query.isEmpty()) return result
        for (part in query.split("&")) {
            val eqIdx = part.indexOf('=')
            if (eqIdx > 0) {
                val key = part.substring(0, eqIdx)
                val value = part.substring(eqIdx + 1)
                result[key] = URLDecoder.decode(value, "UTF-8")
            }
        }
        return result
    }

    private fun sendError(exchange: HttpExchange, status: Int, message: String) {
        val bytes = message.toByteArray()
        exchange.responseHeaders.set("Content-Type", "text/plain")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
        exchange.close()
    }
}

actual fun createLocalProxyServer(cacheService: VideoCacheService): LocalProxyServer =
    JdkLocalProxyServer(cacheService)

/**
 * Simple byte buffer for accumulating segment data during proxy streaming.
 */
private class SegBuffer {
    private var buf = ByteArray(1024)
    private var count = 0

    fun write(data: ByteArray, offset: Int, length: Int) {
        ensureCapacity(count + length)
        System.arraycopy(data, offset, buf, count, length)
        count += length
    }

    fun toByteArray(): ByteArray = buf.copyOf(count)
    fun size(): Int = count

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > buf.size) {
            var newSize = buf.size * 2
            while (newSize < minCapacity) newSize *= 2
            buf = buf.copyOf(newSize)
        }
    }
}
