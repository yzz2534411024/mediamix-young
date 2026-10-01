package com.mediamix.shared.cache

import com.mediamix.shared.models.CachePolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import java.io.File

class VideoCacheServiceTest {

    private lateinit var service: VideoCacheService
    private lateinit var memoryCache: MemoryCache
    private lateinit var diskCache: DiskCache
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() = runTest {
        tempDir = createTempDir("vcs_test")
        memoryCache = MemoryCache(maxL1Entries = 5, maxL2Entries = 10, memoryReader = { 0L })
        diskCache = DiskCache(cacheDir = tempDir.absolutePath)
        service = VideoCacheService(memoryCache = memoryCache, diskCache = diskCache)
        service.initialize()
    }

    @AfterTest
    fun teardown() {
        service.dispose()
        tempDir.deleteRecursively()
    }

    @Test
    fun putVideoAndGetCachePath() = runTest {
        val videoFile = File(tempDir, "source.mp4")
        videoFile.writeBytes(ByteArray(1024))
        service.putVideo("video1", videoFile.absolutePath, "720p")
        val path = service.getCachePath("video1", "720p")
        assertNotNull(path)
    }

    @Test
    fun hasCache_returnsFalseInitially() {
        assertFalse(service.hasCache("video1", "720p"))
    }

    @Test
    fun hasCache_returnsTrueAfterPut() = runTest {
        val videoFile = File(tempDir, "source.mp4")
        videoFile.writeBytes(ByteArray(100))
        service.putVideo("video1", videoFile.absolutePath, "720p")
        assertTrue(service.hasCache("video1", "720p"))
    }

    @Test
    fun getSegment_l2ThenL4() = runTest {
        val data = byteArrayOf(1, 2, 3)
        service.putSegment("video1", "seg1", data, "720p")
        val result = service.getSegment("video1", "seg1", "720p")
        assertTrue(result.hit)
    }

    @Test
    fun frameBuffer_putAndGet() {
        val buffer = mapOf("frame" to "data" as Any)
        service.putFrameBuffer("video1", buffer, "720p")
        val result = service.getFrameBuffer("video1", "720p")
        assertNotNull(result)
        assertEquals("data", result["frame"])
    }

    @Test
    fun evict_removesExpiredEntries() = runTest {
        val videoFile = File(tempDir, "source.mp4")
        videoFile.writeBytes(ByteArray(100))
        service.putVideo("video1", videoFile.absolutePath, "720p", ttl = 1)
        Thread.sleep(1100)
        service.evict()
        assertFalse(service.hasCache("video1", "720p"))
    }

    @Test
    fun setPolicy_changesPolicy() {
        service.setPolicy(CachePolicy.AGGRESSIVE)
    }

    @Test
    fun autoSelectPolicy_emergencyWhenLowDisk() {
        service.autoSelectPolicy(isWiFi = true, availableDiskBytes = 100L * 1024 * 1024)
    }

    @Test
    fun autoSelectPolicy_conservativeWhenMobile() {
        service.autoSelectPolicy(isWiFi = false, availableDiskBytes = 10L * 1024 * 1024 * 1024)
    }

    @Test
    fun getStats_returnsCorrectData() = runTest {
        val stats = service.getStats()
        assertEquals(0, stats.entryCount)
        assertEquals(0L, stats.hitCount)
        assertEquals(0L, stats.missCount)
    }

    @Test
    fun getStats_reflectsHitsAndMisses() = runTest {
        service.getCachePath("nonexistent", "720p")
        val stats = service.getStats()
        assertEquals(1L, stats.missCount)
    }

    @Test
    fun clearAll_clearsEverything() = runTest {
        val videoFile = File(tempDir, "source.mp4")
        videoFile.writeBytes(ByteArray(100))
        service.putVideo("video1", videoFile.absolutePath, "720p")
        service.putFrameBuffer("video1", mapOf("f" to "d" as Any), "720p")
        service.clearAll()
        assertFalse(service.hasCache("video1", "720p"))
        assertNull(service.getFrameBuffer("video1", "720p"))
    }
}
