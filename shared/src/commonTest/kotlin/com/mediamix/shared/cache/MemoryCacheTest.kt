package com.mediamix.shared.cache

import com.mediamix.shared.models.MemoryPressureLevel
import kotlin.test.*

class MemoryCacheTest {

    private lateinit var memoryCache: MemoryCache

    @BeforeTest
    fun setup() {
        memoryCache = MemoryCache(
            maxL1Entries = 5,
            maxL2Entries = 10,
            memoryReader = { 0L },
            maxRssBytes = 512L * 1024 * 1024,
        )
    }

    // ===========================================================
    // L1 Frame Buffer Tests
    // ===========================================================

    @Test
    fun l1_putAndGetFrameBuffer() {
        val frameBuffer = mapOf("frame1" to "data1" as Any)
        memoryCache.putFrameBuffer("video1", frameBuffer, "720p")

        val result = memoryCache.getFrameBuffer("video1", "720p")
        assertNotNull(result)
        assertEquals("data1", result["frame1"])
    }

    @Test
    fun l1_getNonExistent_returnsNull() {
        val result = memoryCache.getFrameBuffer("nonexistent", "720p")
        assertNull(result)
    }

    @Test
    fun l1_differentQuality_returnsNull() {
        memoryCache.putFrameBuffer("video1", mapOf("f" to "d" as Any), "720p")
        val result = memoryCache.getFrameBuffer("video1", "1080p")
        assertNull(result)
    }

    @Test
    fun l1_lruEviction_whenExceedsMaxEntries() {
        // maxL1Entries = 5, add 6 entries
        for (i in 1..6) {
            memoryCache.putFrameBuffer("video$i", mapOf("f" to "d$i" as Any), "720p")
        }

        // First entry should be evicted (LRU)
        val result = memoryCache.getFrameBuffer("video1", "720p")
        assertNull(result)

        // Latest entry should exist
        val latest = memoryCache.getFrameBuffer("video6", "720p")
        assertNotNull(latest)
    }

    @Test
    fun l1_statsReflectSize() {
        memoryCache.putFrameBuffer("v1", mapOf("f" to "d" as Any), "720p")
        memoryCache.putFrameBuffer("v2", mapOf("f" to "d" as Any), "720p")

        val (l1Size, _) = memoryCache.getStats()
        assertEquals(2, l1Size)
    }

    // ===========================================================
    // L2 Segment Cache Tests
    // ===========================================================

    @Test
    fun l2_putAndGetSegment() {
        val data = byteArrayOf(1, 2, 3, 4)
        memoryCache.putSegment("video1", "seg1", data, "720p")

        val result = memoryCache.getSegment("video1", "seg1", "720p")
        assertNotNull(result)
        assertTrue(result.hit)
        assertEquals(data.toList(), result.data)
    }

    @Test
    fun l2_getNonExistent_returnsNull() {
        val result = memoryCache.getSegment("nonexistent", "seg1", "720p")
        assertNull(result)
    }

    @Test
    fun l2_lruEviction_whenExceedsMaxEntries() {
        // maxL2Entries = 10, add 11
        for (i in 1..11) {
            memoryCache.putSegment("v1", "seg$i", byteArrayOf(i.toByte()), "720p")
        }

        // First segment should be evicted
        val result = memoryCache.getSegment("v1", "seg1", "720p")
        assertNull(result)

        // Latest should exist
        val latest = memoryCache.getSegment("v1", "seg11", "720p")
        assertNotNull(latest)
    }

    // ===========================================================
    // Memory Pressure Tests
    // ===========================================================

    @Test
    fun memoryPressure_criticalRejectsL1Write() {
        // Create cache with memory reader that reports critical pressure
        val criticalCache = MemoryCache(
            maxL1Entries = 5,
            maxL2Entries = 10,
            memoryReader = { 480L * 1024 * 1024 }, // >90% of 512MB
            maxRssBytes = 512L * 1024 * 1024,
        )

        // Trigger pressure check
        criticalCache.checkMemoryPressure()
        assertEquals(MemoryPressureLevel.CRITICAL, criticalCache.memoryPressure)

        // L1 write should be rejected
        criticalCache.putFrameBuffer("v1", mapOf("f" to "d" as Any), "720p")
        val (l1Size, _) = criticalCache.getStats()
        assertEquals(0, l1Size)
    }

    @Test
    fun memoryPressure_warningReducesCapacity() {
        val warningCache = MemoryCache(
            maxL1Entries = 10,
            maxL2Entries = 20,
            memoryReader = { 370L * 1024 * 1024 }, // ~72% of 512MB
            maxRssBytes = 512L * 1024 * 1024,
        )

        warningCache.checkMemoryPressure()
        assertEquals(MemoryPressureLevel.WARNING, warningCache.memoryPressure)

        // L1 max should be halved
        assertEquals(5, warningCache.getL1MaxEntries())
    }

    @Test
    fun clearAll_resetsEverything() {
        memoryCache.putFrameBuffer("v1", mapOf("f" to "d" as Any), "720p")
        memoryCache.putSegment("v1", "seg1", byteArrayOf(1), "720p")

        memoryCache.clearAll()

        val (l1, l2) = memoryCache.getStats()
        assertEquals(0, l1)
        assertEquals(0, l2)
    }
}