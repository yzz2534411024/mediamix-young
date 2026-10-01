package com.mediamix.shared.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class VideoModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // CmsApiSite tests

    @Test
    fun test_cmsApiSite_creation() {
        val site = CmsApiSite(key = "test", name = "Test", apiUrl = "http://test.com/api")
        assertEquals("test", site.key)
        assertEquals("Test", site.name)
        assertEquals("http://test.com/api", site.apiUrl)
        assertTrue(site.enabled)
        assertFalse(site.isBuiltIn)
        assertFalse(site.isTvBox)
    }

    @Test
    fun test_cmsApiSite_defaultSites_notEmpty() {
        assertTrue(CmsApiSite.defaultSites.isNotEmpty(), "Default sites should not be empty")
    }

    @Test
    fun test_cmsApiSite_defaultSites_allHaveRequiredFields() {
        CmsApiSite.defaultSites.forEach { site ->
            assertTrue(site.key.isNotEmpty(), "Site key should not be empty")
            assertTrue(site.name.isNotEmpty(), "Site name should not be empty")
            assertTrue(site.apiUrl.isNotEmpty(), "Site apiUrl should not be empty")
            assertTrue(site.isBuiltIn, "Default site should be built-in: ${site.key}")
        }
    }

    @Test
    fun test_cmsApiSite_serialization() {
        val site = CmsApiSite(key = "k", name = "n", apiUrl = "http://a.com")
        val encoded = json.encodeToString(site)
        val decoded = json.decodeFromString<CmsApiSite>(encoded)
        assertEquals(site, decoded)
    }

    // SourceType tests

    @Test
    fun test_sourceType_hasTwoValues() {
        assertEquals(2, SourceType.entries.size)
    }

    @Test
    fun test_sourceType_values() {
        assertNotNull(SourceType.CMS)
        assertNotNull(SourceType.SPIDER)
    }

    // VideoSource tests

    @Test
    fun test_videoSource_creation() {
        val source = VideoSource(key = "s1", name = "Source1", apiUrl = "http://api.com")
        assertEquals("s1", source.key)
        assertTrue(source.enabled)
        assertEquals(SourceType.CMS, source.sourceType)
        assertNull(source.spiderKey)
        assertNull(source.playerType)
    }

    @Test
    fun test_videoSource_fromCmsSite() {
        val site = CmsApiSite(key = "k1", name = "N1", apiUrl = "http://a.com", enabled = true, isBuiltIn = true)
        val source = VideoSource.fromCmsSite(site)
        assertEquals("k1", source.key)
        assertEquals("N1", source.name)
        assertEquals(SourceType.CMS, source.sourceType)
        assertTrue(source.isBuiltIn)
    }

    @Test
    fun test_videoSource_serialization() {
        val source = VideoSource(key = "k", name = "n", apiUrl = "http://a.com", sourceType = SourceType.SPIDER, spiderKey = "sp1")
        val encoded = json.encodeToString(source)
        val decoded = json.decodeFromString<VideoSource>(encoded)
        assertEquals(source, decoded)
    }

    // SourceStatus tests

    @Test
    fun test_sourceStatus_defaultValues() {
        val status = SourceStatus(key = "k", isAvailable = true)
        assertEquals(-1, status.latencyMs)
        assertNull(status.error)
    }

    @Test
    fun test_sourceStatus_withError() {
        val status = SourceStatus(key = "k", isAvailable = false, latencyMs = 500, error = "timeout")
        assertFalse(status.isAvailable)
        assertEquals(500, status.latencyMs)
        assertEquals("timeout", status.error)
    }

    // VideoItem tests

    @Test
    fun test_videoItem_creation_minimalFields() {
        val item = VideoItem(vodId = "1", vodName = "Test Video")
        assertEquals("1", item.vodId)
        assertEquals("Test Video", item.vodName)
        assertNull(item.vodPic)
        assertNull(item.vodRemarks)
        assertNull(item.vodYear)
        assertNull(item.vodArea)
        assertNull(item.typeName)
        assertNull(item.sourceKey)
    }

    @Test
    fun test_videoItem_copy() {
        val item = VideoItem(vodId = "1", vodName = "Video")
        val copied = item.copy(vodName = "New Name", vodYear = "2026")
        assertEquals("1", copied.vodId)
        assertEquals("New Name", copied.vodName)
        assertEquals("2026", copied.vodYear)
    }

    @Test
    fun test_videoItem_equality() {
        val a = VideoItem(vodId = "1", vodName = "V")
        val b = VideoItem(vodId = "1", vodName = "V")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun test_videoItem_fromJson() {
        val map = mapOf(
            "vod_id" to "42",
            "vod_name" to "MyVideo",
            "vod_pic" to "http://img.com/pic.jpg",
            "vod_year" to "2025",
            "vod_area" to "CN"
        )
        val item = VideoItem.fromJson(map, sourceKey = "src1")
        assertEquals("42", item.vodId)
        assertEquals("MyVideo", item.vodName)
        assertEquals("http://img.com/pic.jpg", item.vodPic)
        assertEquals("2025", item.vodYear)
        assertEquals("CN", item.vodArea)
        assertEquals("src1", item.sourceKey)
    }

    @Test
    fun test_videoItem_fromJson_emptyMap() {
        val item = VideoItem.fromJson(emptyMap())
        assertEquals("", item.vodId)
        assertEquals("未知", item.vodName)
        assertNull(item.vodPic)
    }

    @Test
    fun test_videoItem_fromJson_blankStrings() {
        val map = mapOf("vod_id" to "1", "vod_name" to "V", "vod_pic" to "", "vod_year" to "")
        val item = VideoItem.fromJson(map)
        assertNull(item.vodPic, "Empty string should become null")
        assertNull(item.vodYear, "Empty string should become null")
    }

    // VideoListResponse tests

    @Test
    fun test_videoListResponse_fromJson() {
        val map = mapOf(
            "page" to "2",
            "pagecount" to "10",
            "total" to "100",
            "list" to listOf(
                mapOf("vod_id" to "1", "vod_name" to "V1"),
                mapOf("vod_id" to "2", "vod_name" to "V2")
            )
        )
        val response = VideoListResponse.fromJson(map)
        assertEquals(2, response.page)
        assertEquals(10, response.pageCount)
        assertEquals(100, response.total)
        assertEquals(2, response.list.size)
    }

    @Test
    fun test_videoListResponse_fromJson_emptyList() {
        val response = VideoListResponse.fromJson(emptyMap())
        assertEquals(1, response.page)
        assertEquals(1, response.pageCount)
        assertEquals(0, response.total)
        assertTrue(response.list.isEmpty())
    }

    // VideoEpisode tests

    @Test
    fun test_videoEpisode_creation() {
        val ep = VideoEpisode(name = "EP1", url = "http://video.com/ep1.m3u8")
        assertEquals("EP1", ep.name)
        assertEquals("http://video.com/ep1.m3u8", ep.url)
    }

    @Test
    fun test_videoEpisode_serialization() {
        val ep = VideoEpisode(name = "E1", url = "http://u.com/1")
        val encoded = json.encodeToString(ep)
        val decoded = json.decodeFromString<VideoEpisode>(encoded)
        assertEquals(ep, decoded)
    }

    // PlaySource tests

    @Test
    fun test_playSource_creation() {
        val ps = PlaySource(name = "Source1", episodes = listOf(VideoEpisode("E1", "http://u")))
        assertEquals("Source1", ps.name)
        assertEquals(1, ps.episodes.size)
    }

    // VideoDetail tests

    @Test
    fun test_videoDetail_creation() {
        val detail = VideoDetail(vodId = "1", vodName = "Movie", sourceKey = "src")
        assertEquals("1", detail.vodId)
        assertEquals("src", detail.sourceKey)
        assertTrue(detail.playSources.isEmpty())
        assertNull(detail.vodActor)
    }

    @Test
    fun test_videoDetail_fromJson_parsesPlaySources() {
        val map = mapOf(
            "vod_id" to "10",
            "vod_name" to "TestMovie",
            "vod_play_from" to "SourceA\$\$\$SourceB",
            "vod_play_url" to "EP1\$http://a.com/1#EP2\$http://a.com/2\$\$\$EP3\$http://b.com/3"
        )
        val detail = VideoDetail.fromJson(map, sourceKey = "k")
        assertEquals("10", detail.vodId)
        assertEquals("TestMovie", detail.vodName)
        assertEquals(2, detail.playSources.size)
        assertEquals("SourceA", detail.playSources[0].name)
        assertEquals(2, detail.playSources[0].episodes.size)
        assertEquals("SourceB", detail.playSources[1].name)
        assertEquals(1, detail.playSources[1].episodes.size)
    }

    @Test
    fun test_videoDetail_fromJson_emptyPlayFrom() {
        val map = mapOf("vod_id" to "1", "vod_name" to "V", "vod_play_from" to "", "vod_play_url" to "")
        val detail = VideoDetail.fromJson(map)
        assertTrue(detail.playSources.isEmpty())
    }

    // VideoCategory tests

    @Test
    fun test_videoCategory_fromJson() {
        val map = mapOf("type_id" to "5", "type_pid" to "1", "type_name" to "Action")
        val cat = VideoCategory.fromJson(map)
        assertEquals(5, cat.typeId)
        assertEquals(1, cat.typePid)
        assertEquals("Action", cat.typeName)
    }

    @Test
    fun test_videoCategory_fromJson_invalidNumbers() {
        val map = mapOf("type_id" to "abc", "type_pid" to "xyz", "type_name" to "Drama")
        val cat = VideoCategory.fromJson(map)
        assertEquals(0, cat.typeId)
        assertEquals(0, cat.typePid)
        assertEquals("Drama", cat.typeName)
    }

    // VideoParser tests

    @Test
    fun test_videoParser_buildUrl() {
        val parser = VideoParser(key = "p", name = "P", urlTemplate = "https://jx.com/?url={url}")
        assertEquals("https://jx.com/?url=http://video.com/1.m3u8", parser.buildUrl("http://video.com/1.m3u8"))
    }

    @Test
    fun test_videoParser_defaultParsers_notEmpty() {
        assertTrue(VideoParser.defaultParsers.isNotEmpty())
    }

    @Test
    fun test_videoParser_defaultParsers_allHaveUrlTemplate() {
        VideoParser.defaultParsers.forEach { parser ->
            assertTrue(parser.urlTemplate.contains("{url}"), "Parser ${parser.key} should contain {url} placeholder")
        }
    }

    // SpiderEngineException tests

    @Test
    fun test_spiderEngineException_message() {
        val ex = SpiderEngineException("test error")
        assertEquals("test error", ex.message)
    }

    @Test
    fun test_spiderEngineException_isException() {
        val ex = SpiderEngineException("err")
        assertTrue(ex is Exception)
    }
    // ========================================================
    // JSON 引号污染回归测试
    // 见 VideoModels.kt 的 plainText()：用 JsonElement.toString() 取 CMS 字段
    // 会把字符串写成 `"abc"`，播放地址尾部多一个引号 → ExoPlayer Source error。
    // ========================================================

    @Test
    fun test_videoDetail_fromJson_stripsJsonQuotes() {
        // 注意：raw string 里写 ${'$'} 而不是 \\(dollar)，否则会生成非法 JSON 转义
        val parsed = Json.parseToJsonElement(
            """
            {
              "vod_id": 1287,
              "vod_name": "应援团少女",
              "vod_play_from": "liangzi${'$'}${'$'}${'$'}lzm3u8",
              "vod_play_url": "HD中字${'$'}https://a.example/share/1${'$'}${'$'}${'$'}HD中字${'$'}https://b.example/1.m3u8"
            }
            """.trimIndent()
        ).jsonObject

        val detail = VideoDetail.fromJson(parsed.toMap(), sourceKey = "lzzy")

        assertEquals("应援团少女", detail.vodName)
        assertFalse(detail.vodName.contains('"'), "vodName 不应带 JSON 引号")
        assertEquals(2, detail.playSources.size)
        assertEquals("liangzi", detail.playSources[0].name)
        assertEquals("lzm3u8", detail.playSources[1].name)

        val lastUrl = detail.playSources[1].episodes[0].url
        assertEquals("https://b.example/1.m3u8", lastUrl)
        assertFalse(lastUrl.endsWith("\""), "播放地址尾部不应有引号，否则播放器会报 Source error")

        assertEquals(
            "第1集",
            VideoDetail.parseEpisodes("第1集${'$'}https://a.example/1.m3u8")[0].name
        )
    }

    @Test
    fun test_videoDetail_fromJson_defaultSourceIsRichestLine() {
        val map = mapOf(
            "vod_play_from" to "A${'$'}${'$'}${'$'}B",
            "vod_play_url" to "1${'$'}https://a/1${'$'}${'$'}${'$'}1${'$'}https://b/1" +
                "#2${'$'}https://b/2#3${'$'}https://b/3",
        )
        val detail = VideoDetail.fromJson(map)
        assertEquals(1, detail.defaultSourceIndex)
        assertTrue(detail.hasPlayableSource)
    }

    @Test
    fun test_videoDetail_fallbackUrlsForEpisode() {
        val map = mapOf(
            "vod_play_from" to "A${'$'}${'$'}${'$'}B",
            "vod_play_url" to "1${'$'}https://a/1#2${'$'}https://a/2" +
                "${'$'}${'$'}${'$'}1${'$'}https://b/1#2${'$'}https://b/2",
        )
        val detail = VideoDetail.fromJson(map)
        val fallbacks = detail.fallbackUrlsFor(episodeIndex = 1, currentSourceIndex = 0)
        assertEquals(listOf("https://b/2"), fallbacks)
    }

    @Test
    fun test_parseEpisodes_keepsDollarInsideUrl() {
        // 地址里本身带 $ 时不能被 split("$") 截断
        val episodes = VideoDetail.parseEpisodes("第1集${'$'}https://cdn/1.m3u8?sign=a${'$'}b")
        assertEquals(1, episodes.size)
        assertEquals("https://cdn/1.m3u8?sign=a${'$'}b", episodes[0].url)
    }

    @Test
    fun test_parseEpisodes_skipsBlankAndWhitespace() {
        val episodes = VideoDetail.parseEpisodes("#第1集${'$'}https://a/1##第2集${'$'}  #")
        assertEquals(1, episodes.size)
        assertEquals("第1集", episodes[0].name)
    }

    @Test
    fun test_sanitizePlayUrl_removesQuotes() {
        assertEquals("https://a/1.m3u8", sanitizePlayUrl("\"https://a/1.m3u8\""))
        assertNull(sanitizePlayUrl("   "))
        assertNull(sanitizePlayUrl("https://a/1 .m3u8"))
    }
}
