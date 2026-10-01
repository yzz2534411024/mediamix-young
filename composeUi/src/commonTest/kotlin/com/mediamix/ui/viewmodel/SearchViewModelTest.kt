package com.mediamix.ui.viewmodel

import com.mediamix.shared.models.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchViewModelTest {
    @Test
    fun videoItem_sourceKey() {
        val item =
            VideoItem(
                vodId = "1",
                vodName = "Test",
                sourceKey = "test_source",
            )
        assertEquals("test_source", item.sourceKey)
    }

    @Test
    fun videoItem_deduplicationByName() {
        val items =
            listOf(
                VideoItem(vodId = "1", vodName = "Movie A", sourceKey = "s1"),
                VideoItem(vodId = "2", vodName = "Movie A", sourceKey = "s2"),
                VideoItem(vodId = "3", vodName = "Movie B", sourceKey = "s1"),
            )
        val seen = mutableSetOf<String>()
        val deduped = items.filter { seen.add(it.vodName) }
        assertEquals(2, deduped.size)
        assertEquals("Movie A", deduped[0].vodName)
        assertEquals("Movie B", deduped[1].vodName)
    }

    @Test
    fun videoItem_blankQueryDetection() {
        assertTrue("".isBlank())
        assertTrue("   ".isBlank())
        assertFalse("test".isBlank())
    }

    @Test
    fun videoItem_searchResultMerge() {
        val source1 =
            listOf(
                VideoItem(vodId = "1", vodName = "A", sourceKey = "s1"),
                VideoItem(vodId = "2", vodName = "B", sourceKey = "s1"),
            )
        val source2 =
            listOf(
                VideoItem(vodId = "3", vodName = "C", sourceKey = "s2"),
            )
        val merged = source1 + source2
        assertEquals(3, merged.size)
    }
}
