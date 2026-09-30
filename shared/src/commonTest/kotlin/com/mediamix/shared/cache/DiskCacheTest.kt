package com.mediamix.shared.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import java.io.File

class DiskCacheTest {

    private lateinit var diskCache: DiskCache
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() = runTest {
        tempDir = createTempDir("disk_cache_test")
        diskCache = DiskCache(cacheDir = tempDir.absolutePath)
        diskCache.initialize()
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    // ===========================================================
    // Index Tests
    // ===========================================================

    @Test
    fun index_saveAndLoad() = runTest {
        // Create a temp video file
        val videoFile = File(tempDir, "test_video.mp4")
        videoFile.writeBytes(ByteArray(100))

        diskCache.putVideo("video1", videoFile.absolutePath, "720p")

        // Verify index was saved
        val entries = diskCache.getAllEntries()
        assertEquals(1, entries.size)
        assertEquals("video1", entries[0].videoId)
    }

    // ===========================================================
    // L3 Video Cache Tests
    // ===========================================================

    @Test
    fun l3_putAndGetCachePath() = runTest {
        val videoFile = File(tempDir, "source.mp4")
        videoFile.writeBytes(ByteArray(1024))

        diskCache.putVideo("video1", videoFile.absolutePath, "720p")

        val cachePath = diskCache.getCachePath("video1", "720p")
        assertNotNull(cachePath)
        assertTrue(File(cachePath).exists())
    }

    @Test
    fun l3_getNonExistent_returnsNull() {
        val result = diskCache.getCachePath("nonexistent", "720p")
        assertNull(result)
    }

    @Test
    fun l3_hasCache_returnsTrueAfterPut() = runTest {
        val videoFile = File(tempDir, "source.mp4")
        videoFile.writeBytes(ByteArray(512))

        assertFalse(diskCache.hasCache("video1", "720p"))

        diskCache.putVideo("video1", videoFile.absolutePath, "720p")
        assertTrue(diskCache.hasCache("video1", "720p"))
    }

    @Test
    fun l3_removeEntry_deletesFile() = runTest {
        val videoFile = File(tempDir, "source.mp4")
        videoFile.writeBytes(ByteArray(256))

        diskCache.putVideo("video1", videoFile.absolutePath, "720p")
        val cachePath = diskCache.getCachePath("video1", "720p")
        assertNotNull(cachePath)

        val entries = diskCache.getAllEntries()
        assertEquals(1, entries.size)

        diskCache.removeEntry(entries[0].cacheId)
        assertFalse(File(cachePath).exists())
        assertEquals(0, diskCache.getAllEntries().size)
    }

    // ===========================================================
    // L4 Segment Cache Tests
    // ===========================================================

    @Test
    fun l4_putAndGetSegment() = runTest {
        val data = byteArrayOf(10, 20, 30, 40)
        diskCache.putSegment("video1", "seg1", data, "720p")

        val result = diskCache.getSegment("video1", "seg1", "720p")
        assertNotNull(result)
        assertTrue(result.hit)
        assertNotNull(result.path)
        assertTrue(File(result.path!!).exists())
    }

    @Test
    fun l4_getNonExistent_returnsNull() {
        val result = diskCache.getSegment("nonexistent", "seg1", "720p")
        assertNull(result)
    }

    @Test
    fun l4_multipleSegments() = runTest {
        diskCache.putSegment("video1", "seg1", byteArrayOf(1), "720p")
        diskCache.putSegment("video1", "seg2", byteArrayOf(2), "720p")
        diskCache.putSegment("video1", "seg3", byteArrayOf(3), "720p")

        assertNotNull(diskCache.getSegment("video1", "seg1", "720p"))
        assertNotNull(diskCache.getSegment("video1", "seg2", "720p"))
        assertNotNull(diskCache.getSegment("video1", "seg3", "720p"))

        val entries = diskCache.getAllEntries()
        assertEquals(1, entries.size) // same videoId+quality = same cacheId
        assertEquals(3, entries[0].segments.size)
    }

    // ===========================================================
    // Expiry Tests
    // ===========================================================

    @Test
    fun expiredEntry_returnsNull() = runTest {
        val videoFile = File(tempDir, "source.mp4")
        videoFile.writeBytes(ByteArray(100))

        // Put with TTL = 1 second
        diskCache.putVideo("video1", videoFile.absolutePath, "720p", ttl = 1)

        // Should exist immediately
        assertNotNull(diskCache.getCachePath("video1", "720p"))

        // Wait for expiry
        Thread.sleep(1100)

        // Should be expired
        assertNull(diskCache.getCachePath("video1", "720p"))
    }

    // ===========================================================
    // Clear All
    // ===========================================================

    @Test
    fun clearAll_removesEverything() = runTest {
        val videoFile = File(tempDir, "source.mp4")
        videoFile.writeBytes(ByteArray(100))
        diskCache.putVideo("video1", videoFile.absolutePath, "720p")
        diskCache.putSegment("video2", "seg1", byteArrayOf(1), "720p")

        diskCache.clearAll()

        assertEquals(0, diskCache.getAllEntries().size)
        assertEquals(0L, diskCache.getDiskTotalSize())
    }
}