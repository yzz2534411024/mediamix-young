package com.mediamix.ui.viewmodel

import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.VideoCategory
import com.mediamix.shared.models.VideoItem
import com.mediamix.shared.models.VideoListResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoHomeViewModelTest {
    @Test
    fun cmsApiSite_defaultValues() {
        val site = CmsApiSite(key = "test", name = "Test", apiUrl = "http://test.com")
        assertTrue(site.enabled)
        assertFalse(site.isBuiltIn)
        assertFalse(site.isTvBox)
    }

    @Test
    fun cmsApiSite_tvBoxSite() {
        val site = CmsApiSite(key = "tvb", name = "TVBox", apiUrl = "http://tvb.com", isTvBox = true)
        assertTrue(site.isTvBox)
        assertFalse(site.isBuiltIn)
    }

    @Test
    fun cmsApiSite_defaultSitesNotEmpty() {
        assertTrue(CmsApiSite.defaultSites.isNotEmpty())
    }

    @Test
    fun cmsApiSite_defaultSitesHaveEnabledSites() {
        val enabled = CmsApiSite.defaultSites.filter { it.enabled }
        assertTrue(enabled.isNotEmpty())
    }

    @Test
    fun cmsApiSite_defaultSitesFirstIsCmsSource() {
        // 首页默认落在第一个源上；TVBox 源（饭太硬）需要 TVBox 内核才能解析，
        // 不能当默认值，否则用户一打开就是空列表。
        val firstEnabled = CmsApiSite.defaultSites.filter { it.enabled }.first()
        assertFalse(firstEnabled.isTvBox, "第一个源必须是普通 CMS 源，实际是 ${firstEnabled.key}")
    }

    @Test
    fun cmsApiSite_defaultSitesHasTvBoxEntry() {
        assertTrue(CmsApiSite.defaultSites.any { it.isTvBox }, "饭太硬应保留在源列表里")
    }

    @Test
    fun cmsApiSite_findByKeyFallsBackToRetired() {
        assertEquals("天涯资源", CmsApiSite.findByKey("tyyszy")?.name)
    }

    @Test
    fun videoCategory_creation() {
        val cat = VideoCategory(typeId = 1, typePid = 0, typeName = "Movies")
        assertEquals(1, cat.typeId)
        assertEquals(0, cat.typePid)
        assertEquals("Movies", cat.typeName)
    }

    @Test
    fun videoItem_creation() {
        val item =
            VideoItem(
                vodId = "123",
                vodName = "Test Movie",
                vodPic = "https://example.com/pic.jpg",
                vodRemarks = "HD",
                vodYear = "2024",
                vodArea = "US",
            )
        assertEquals("123", item.vodId)
        assertEquals("Test Movie", item.vodName)
        assertEquals("https://example.com/pic.jpg", item.vodPic)
        assertEquals("HD", item.vodRemarks)
        assertEquals("2024", item.vodYear)
        assertEquals("US", item.vodArea)
    }

    @Test
    fun videoListResponse_creation() {
        val items =
            listOf(
                VideoItem(vodId = "1", vodName = "A"),
                VideoItem(vodId = "2", vodName = "B"),
            )
        val response = VideoListResponse(list = items, page = 1, pageCount = 5, total = 50)
        assertEquals(2, response.list.size)
        assertEquals(1, response.page)
        assertEquals(5, response.pageCount)
        assertEquals(50, response.total)
    }
}
