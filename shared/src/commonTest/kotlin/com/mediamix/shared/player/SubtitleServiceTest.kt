package com.mediamix.shared.player

import kotlin.test.*

class SubtitleServiceTest {

    private val service = SubtitleService(httpClient = null)

    // ==================== SRT 解析测试 ====================

    @Test
    fun parseSrt_standardFormat() {
        val srt = """
1
00:00:01,000 --> 00:00:04,000
Hello World

2
00:00:05,500 --> 00:00:08,000
Second subtitle
""".trimIndent()

        val entries = service.parseSrt(srt)
        assertEquals(2, entries.size)
        assertEquals(1000L, entries[0].startMs)
        assertEquals(4000L, entries[0].endMs)
        assertEquals("Hello World", entries[0].text)
        assertEquals(5500L, entries[1].startMs)
        assertEquals(8000L, entries[1].endMs)
        assertEquals("Second subtitle", entries[1].text)
    }

    @Test
    fun parseSrt_dotMillisecondSeparator() {
        val srt = """
1
00:00:01.500 --> 00:00:04.000
Dot separator test
""".trimIndent()

        val entries = service.parseSrt(srt)
        assertEquals(1, entries.size)
        assertEquals(1500L, entries[0].startMs)
        assertEquals(4000L, entries[0].endMs)
    }

    @Test
    fun parseSrt_multiLineText() {
        val srt = """
1
00:00:01,000 --> 00:00:04,000
Line one
Line two
Line three
""".trimIndent()

        val entries = service.parseSrt(srt)
        assertEquals(1, entries.size)
        assertEquals("Line one\nLine two\nLine three", entries[0].text)
    }

    @Test
    fun parseSrt_emptyContent() {
        val entries = service.parseSrt("")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun parseSrt_invalidFormat() {
        val srt = """
This is just random text
without any proper SRT format
""".trimIndent()

        val entries = service.parseSrt(srt)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun parseSrt_sortedByStartTime() {
        val srt = """
1
00:00:10,000 --> 00:00:12,000
Later subtitle

2
00:00:01,000 --> 00:00:04,000
Earlier subtitle
""".trimIndent()

        val entries = service.parseSrt(srt)
        assertEquals(2, entries.size)
        assertEquals(1000L, entries[0].startMs)
        assertEquals(10000L, entries[1].startMs)
    }

    @Test
    fun parseSrt_hoursMinutesSeconds() {
        val srt = """
1
01:30:45,123 --> 01:30:50,456
Time test
""".trimIndent()

        val entries = service.parseSrt(srt)
        assertEquals(1, entries.size)
        assertEquals(1 * 3600000 + 30 * 60000 + 45 * 1000 + 123, entries[0].startMs)
        assertEquals(1 * 3600000 + 30 * 60000 + 50 * 1000 + 456, entries[0].endMs)
    }

    @Test
    fun parseSrt_skipEmptyText() {
        val srt = """
1
00:00:01,000 --> 00:00:04,000


2
00:00:05,000 --> 00:00:08,000
Valid text
""".trimIndent()

        val entries = service.parseSrt(srt)
        assertEquals(1, entries.size)
        assertEquals("Valid text", entries[0].text)
    }

    // ==================== 时间戳解析测试 ====================

    @Test
    fun parseTimestamp_commaFormat() {
        assertEquals(3661500L, service.parseTimestamp("01:01:01,500"))
    }

    @Test
    fun parseTimestamp_dotFormat() {
        assertEquals(3661500L, service.parseTimestamp("01:01:01.500"))
    }

    @Test
    fun parseTimestamp_invalid() {
        assertNull(service.parseTimestamp("invalid"))
        assertNull(service.parseTimestamp(""))
        assertNull(service.parseTimestamp("00:00"))
    }

    // ==================== 二分查找测试 ====================

    private val testEntries = listOf(
        SubtitleEntry(0L, 2000L, "First"),
        SubtitleEntry(3000L, 5000L, "Second"),
        SubtitleEntry(6000L, 8000L, "Third"),
        SubtitleEntry(10000L, 12000L, "Fourth"),
    )

    @Test
    fun getSubtitleAt_normalPosition() {
        val result = service.getSubtitleAt(testEntries, 1500L)
        assertNotNull(result)
        assertEquals("First", result.text)
    }

    @Test
    fun getSubtitleAt_secondEntry() {
        val result = service.getSubtitleAt(testEntries, 4000L)
        assertNotNull(result)
        assertEquals("Second", result.text)
    }

    @Test
    fun getSubtitleAt_notInAnyRange() {
        // Position 2500 is between entry 1 (ends 2000) and entry 2 (starts 3000)
        val result = service.getSubtitleAt(testEntries, 2500L)
        assertNull(result)
    }

    @Test
    fun getSubtitleAt_emptyList() {
        val result = service.getSubtitleAt(emptyList(), 1000L)
        assertNull(result)
    }

    @Test
    fun getSubtitleAt_withOffsetSec() {
        // Position 1500 with +2s offset => adjusted to 3500, should hit "Second"
        val result = service.getSubtitleAt(testEntries, 1500L, offsetSec = 2.0)
        assertNotNull(result)
        assertEquals("Second", result.text)
    }

    @Test
    fun getSubtitleAt_withSyncOffset() {
        // Position 1500 with syncOffset +3000 => adjusted to 4500, should hit "Second"
        val result = service.getSubtitleAt(testEntries, 1500L, syncOffset = 3000L)
        assertNotNull(result)
        assertEquals("Second", result.text)
    }

    @Test
    fun getSubtitleAt_boundaryStart() {
        val result = service.getSubtitleAt(testEntries, 3000L)
        assertNotNull(result)
        assertEquals("Second", result.text)
    }

    @Test
    fun getSubtitleAt_boundaryEnd() {
        val result = service.getSubtitleAt(testEntries, 5000L)
        assertNotNull(result)
        assertEquals("Second", result.text)
    }

    // ==================== PTS 同步测试 ====================

    @Test
    fun autoSyncOffset_normalCalculation() {
        val entries = listOf(
            SubtitleEntry(1000L, 2000L, "A"),
            SubtitleEntry(3000L, 4000L, "B"),
            SubtitleEntry(5000L, 6000L, "C"),
        )
        // Audio timestamps: all 100ms behind subtitle start times
        // offsets: [1000-900=100, 3000-2900=100, 5000-4900=100]
        val audioTimestamps = listOf(900L, 2900L, 4900L)
        val offset = service.autoSyncOffset(entries, audioTimestamps)
        assertEquals(100L, offset)
        assertEquals(100L, service.getSyncOffset())
    }

    @Test
    fun autoSyncOffset_emptyEntries() {
        service.setSyncOffset(42L)
        val offset = service.autoSyncOffset(emptyList(), listOf(1000L))
        assertEquals(42L, offset) // returns current offset unchanged
    }

    @Test
    fun autoSyncOffset_emptyAudioTimestamps() {
        service.setSyncOffset(42L)
        val offset = service.autoSyncOffset(
            listOf(SubtitleEntry(1000L, 2000L, "A")),
            emptyList()
        )
        assertEquals(42L, offset)
    }

    @Test
    fun autoSyncOffset_medianResistantToOutliers() {
        val entries = listOf(
            SubtitleEntry(1000L, 2000L, "A"),
            SubtitleEntry(3000L, 4000L, "B"),
            SubtitleEntry(5000L, 6000L, "C"),
        )
        // offsets: [0, 0, 5000] — median should be 0 (the middle value after sorting)
        val audioTimestamps = listOf(1000L, 3000L, 0L)
        val offset = service.autoSyncOffset(entries, audioTimestamps)
        // offsets computed: 1000-1000=0, 3000-3000=0, nearest(0)->1000 so 1000-0=1000
        // sorted: [0, 0, 1000], median index=1 => 0
        assertEquals(0L, offset)
    }

    // ==================== LRU 缓存测试 ====================

    @Test
    fun lruCache_hitAfterPut() {
        val cache = SubtitleLruCache(maxEntries = 3)
        val entries = listOf(SubtitleEntry(0, 1000, "test"))
        cache.put("key1", entries)
        val result = cache.get("key1")
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("test", result[0].text)
    }

    @Test
    fun lruCache_evictLeastRecentlyUsed() {
        val cache = SubtitleLruCache(maxEntries = 2)
        cache.put("key1", listOf(SubtitleEntry(0, 1000, "a")))
        cache.put("key2", listOf(SubtitleEntry(0, 1000, "b")))
        // Cache is full. Adding key3 should evict key1 (least recently used)
        cache.put("key3", listOf(SubtitleEntry(0, 1000, "c")))
        assertNull(cache.get("key1"))
        assertNotNull(cache.get("key2"))
        assertNotNull(cache.get("key3"))
        assertEquals(2, cache.size)
    }

    @Test
    fun lruCache_accessRefreshesOrder() {
        val cache = SubtitleLruCache(maxEntries = 2)
        cache.put("key1", listOf(SubtitleEntry(0, 1000, "a")))
        cache.put("key2", listOf(SubtitleEntry(0, 1000, "b")))
        // Access key1, making it recently used; key2 becomes least recently used
        cache.get("key1")
        // Adding key3 should evict key2
        cache.put("key3", listOf(SubtitleEntry(0, 1000, "c")))
        assertNotNull(cache.get("key1"))
        assertNull(cache.get("key2"))
        assertNotNull(cache.get("key3"))
    }

    @Test
    fun lruCache_clear() {
        val cache = SubtitleLruCache(maxEntries = 5)
        cache.put("key1", listOf(SubtitleEntry(0, 1000, "a")))
        cache.put("key2", listOf(SubtitleEntry(0, 1000, "b")))
        assertEquals(2, cache.size)
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get("key1"))
    }

    @Test
    fun lruCache_missReturnsNull() {
        val cache = SubtitleLruCache()
        assertNull(cache.get("nonexistent"))
    }

    // ==================== 同步偏移控制测试 ====================

    @Test
    fun syncOffset_setAndGet() {
        service.setSyncOffset(500L)
        assertEquals(500L, service.getSyncOffset())
        service.setSyncOffset(0L)
        assertEquals(0L, service.getSyncOffset())
    }

    @Test
    fun clearCache_resetsSize() {
        // Use loadFromUrl indirectly by testing cache through parseSrt + manual cache
        service.clearCache()
        assertEquals(0, service.getCacheSize())
    }
}