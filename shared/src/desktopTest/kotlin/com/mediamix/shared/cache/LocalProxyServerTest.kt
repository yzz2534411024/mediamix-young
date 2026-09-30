package com.mediamix.shared.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * JdkLocalProxyServer（Desktop 实现）的集成测试。
 *
 * 原先位于 commonTest，但实现依赖 `com.sun.net.httpserver` 与 `java.net.HttpURLConnection`，
 * 只能在 JVM/Desktop 上运行，故迁移到 desktopTest。
 */
class LocalProxyServerTest {

    private lateinit var cacheService: VideoCacheService
    private lateinit var memoryCache: MemoryCache
    private lateinit var diskCache: DiskCache
    private lateinit var tempDir: File
    private lateinit var proxyServer: LocalProxyServer

    @BeforeTest
    fun setup() = runTest {
        tempDir = createTempDir("proxy_test")
        memoryCache = MemoryCache(maxL1Entries = 5, maxL2Entries = 10, memoryReader = { 0L })
        diskCache = DiskCache(cacheDir = tempDir.absolutePath)
        cacheService = VideoCacheService(memoryCache = memoryCache, diskCache = diskCache)
        cacheService.initialize()
        proxyServer = createLocalProxyServer(cacheService)
    }

    @AfterTest
    fun teardown() {
        proxyServer.stop()
        cacheService.dispose()
        tempDir.deleteRecursively()
    }

    // ----------------------------------------------------------
    // Range parsing tests
    // ----------------------------------------------------------

    @Test
    fun parseRange_validRange() {
        val result = proxyServer.parseRange("bytes=0-499", 1000)
        assertNotNull(result)
        assertEquals(0L, result.first)
        assertEquals(499L, result.second)
    }

    @Test
    fun parseRange_openEndedRange() {
        val result = proxyServer.parseRange("bytes=500-", 1000)
        assertNotNull(result)
        assertEquals(500L, result.first)
        assertEquals(999L, result.second)
    }

    @Test
    fun parseRange_fullRange() {
        val result = proxyServer.parseRange("bytes=0-", 1000)
        assertNotNull(result)
        assertEquals(0L, result.first)
        assertEquals(999L, result.second)
    }

    @Test
    fun parseRange_invalidFormat() {
        assertNull(proxyServer.parseRange("invalid", 1000))
    }

    @Test
    fun parseRange_startBeyondFileSize() {
        assertNull(proxyServer.parseRange("bytes=2000-3000", 1000))
    }

    @Test
    fun parseRange_startGreaterThanEnd() {
        assertNull(proxyServer.parseRange("bytes=500-100", 1000))
    }

    @Test
    fun parseRange_zeroFileSize() {
        assertNull(proxyServer.parseRange("bytes=0-100", 0))
    }

    @Test
    fun parseRange_endClampedToFileSize() {
        val result = proxyServer.parseRange("bytes=0-9999", 1000)
        assertNotNull(result)
        assertEquals(0L, result.first)
        assertEquals(999L, result.second)
    }

    // ----------------------------------------------------------
    // Proxy URL building
    // ----------------------------------------------------------

    @Test
    fun proxyUrl_containsVideoIdAndParams() {
        proxyServer.start()
        val url = proxyServer.proxyUrl("https://cdn.example.com/video.mp4", "vid123", "1080p")
        assertTrue(url.contains("/vod/vid123"))
        assertTrue(url.contains("quality=1080p"))
        assertTrue(url.startsWith("http://127.0.0.1:"))
    }

    @Test
    fun proxyUrl_defaultQuality() {
        proxyServer.start()
        val url = proxyServer.proxyUrl("https://cdn.example.com/video.mp4", "vid123")
        assertTrue(url.contains("quality=720p"))
    }

    // ----------------------------------------------------------
    // Server lifecycle
    // ----------------------------------------------------------

    @Test
    fun start_andStop() {
        assertFalse(proxyServer.isRunning)
        assertEquals(0, proxyServer.currentPort)

        proxyServer.start()
        assertTrue(proxyServer.isRunning)
        assertTrue(proxyServer.currentPort > 0)

        proxyServer.stop()
        assertFalse(proxyServer.isRunning)
        assertEquals(0, proxyServer.currentPort)
    }

    @Test
    fun start_idempotent() {
        proxyServer.start()
        val port1 = proxyServer.currentPort
        proxyServer.start() // should not restart
        assertEquals(port1, proxyServer.currentPort)
    }

    // ----------------------------------------------------------
    // HTTP endpoint tests (real HTTP requests to proxy)
    // ----------------------------------------------------------

    @Test
    fun request_missingUrlParam_returns400() {
        proxyServer.start()
        val url = URL("http://127.0.0.1:${proxyServer.currentPort}/vod/vid123")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(400, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun request_invalidPath_returns404() {
        proxyServer.start()
        val url = URL("http://127.0.0.1:${proxyServer.currentPort}/invalid")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(404, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun request_cacheHit_servesFile() {
        proxyServer.start()

        // Create a fake video file and put it in cache
        val videoFile = File(tempDir, "test_video.mp4")
        val videoData = ByteArray(2048) { (it % 256).toByte() }
        videoFile.writeBytes(videoData)
        runTest {
            cacheService.putVideo("vid_cache", videoFile.absolutePath, "720p")
        }

        // Request from proxy
        val url = URL(
            "http://127.0.0.1:${proxyServer.currentPort}/vod/vid_cache?url=http%3A%2F%2Ffake&quality=720p"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        assertEquals("video/mp4", conn.contentType)

        val responseBody = conn.inputStream.readBytes()
        assertEquals(2048, responseBody.size)
        assertContentEquals(videoData, responseBody)
        conn.disconnect()
    }

    @Test
    fun request_cacheHit_withRange_returns206() {
        proxyServer.start()

        val videoFile = File(tempDir, "test_video_range.mp4")
        val videoData = ByteArray(4096) { (it % 256).toByte() }
        videoFile.writeBytes(videoData)
        runTest {
            cacheService.putVideo("vid_range", videoFile.absolutePath, "720p")
        }

        val url = URL(
            "http://127.0.0.1:${proxyServer.currentPort}/vod/vid_range?url=http%3A%2F%2Ffake&quality=720p"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Range", "bytes=0-1023")
        assertEquals(206, conn.responseCode)

        val contentRange = conn.getHeaderField("Content-Range")
        assertNotNull(contentRange)
        assertTrue(contentRange.startsWith("bytes 0-1023/4096"))

        val responseBody = conn.inputStream.readBytes()
        assertEquals(1024, responseBody.size)
        conn.disconnect()
    }

    @Test
    fun request_cacheHit_invalidRange_returns416() {
        proxyServer.start()

        val videoFile = File(tempDir, "test_video_416.mp4")
        videoFile.writeBytes(ByteArray(100))
        runTest {
            cacheService.putVideo("vid_416", videoFile.absolutePath, "720p")
        }

        val url = URL(
            "http://127.0.0.1:${proxyServer.currentPort}/vod/vid_416?url=http%3A%2F%2Ffake&quality=720p"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Range", "bytes=500-600")
        assertEquals(416, conn.responseCode)
        conn.disconnect()
    }
}
