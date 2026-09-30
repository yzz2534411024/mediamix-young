package com.mediamix.shared.spider

import com.mediamix.shared.models.TvBoxConfig
import kotlin.test.*

class TvBoxConfigParserTest {

    private val parser = TvBoxConfigParser()

    @Test
    fun testParseFullConfig() {
        val json = mapOf(
            "spider" to "https://example.com/spider.jar;abc123md5",
            "sites" to listOf(
                mapOf(
                    "key" to "site1",
                    "name" to "测试站点",
                    "type" to 3,
                    "api" to "csp_Baidu",
                    "ext" to "ext_data",
                    "jar" to "https://example.com/site.jar",
                    "playerType" to 1,
                    "searchable" to 1,
                    "quickSearch" to 1,
                    "changeable" to 0,
                ),
                mapOf(
                    "key" to "site2",
                    "name" to "站点2",
                    "type" to 1,
                    "api" to "https://api.example.com",
                    "searchable" to 0,
                    "quickSearch" to 0,
                    "changeable" to 1,
                ),
            ),
            "lives" to listOf(
                mapOf(
                    "name" to "直播1",
                    "type" to "0",
                    "url" to "https://live.example.com/channel.m3u8",
                    "playerType" to 2,
                ),
            ),
            "flags" to listOf("qq", "iqiyi", "youku"),
        )

        val config = parser.parse(json)

        // spider URL 应去掉 ;md5 部分
        assertEquals("https://example.com/spider.jar", config.spiderUrl)

        // sites 解析
        assertEquals(2, config.sites.size)

        val site1 = config.sites[0]
        assertEquals("site1", site1.key)
        assertEquals("测试站点", site1.name)
        assertEquals(3, site1.type)
        assertEquals("csp_Baidu", site1.api)
        assertEquals("ext_data", site1.ext)
        assertEquals("https://example.com/site.jar", site1.jar)
        assertEquals(1, site1.playerType)
        assertTrue(site1.searchable)
        assertTrue(site1.quickSearch)
        assertFalse(site1.changeable)
        assertTrue(site1.isJavaSpider)

        val site2 = config.sites[1]
        assertEquals("site2", site2.key)
        assertFalse(site2.searchable)
        assertFalse(site2.quickSearch)
        assertTrue(site2.changeable)
        assertFalse(site2.isJavaSpider)

        // lives 解析
        assertEquals(1, config.lives.size)
        assertEquals("直播1", config.lives[0].name)
        assertEquals("0", config.lives[0].type)
        assertEquals("https://live.example.com/channel.m3u8", config.lives[0].url)
        assertEquals(2, config.lives[0].playerType)

        // flags 解析
        assertEquals(listOf("qq", "iqiyi", "youku"), config.flags)
    }

    @Test
    fun testSpiderUrlWithSemicolon() {
        assertEquals("https://example.com/jar.jar", parser.parseSpiderUrl("https://example.com/jar.jar;md5hash"))
    }

    @Test
    fun testSpiderUrlWithoutSemicolon() {
        assertEquals("https://example.com/jar.jar", parser.parseSpiderUrl("https://example.com/jar.jar"))
    }

    @Test
    fun testSpiderUrlNull() {
        assertNull(parser.parseSpiderUrl(null))
        assertNull(parser.parseSpiderUrl(""))
        assertNull(parser.parseSpiderUrl("   "))
    }

    @Test
    fun testSearchableDefaultTrue() {
        // searchable 默认为 1（true）
        val sites = parser.parseSites(
            listOf(
                mapOf("key" to "k1", "name" to "n1", "api" to "api1"),
            )
        )
        assertEquals(1, sites.size)
        assertTrue(sites[0].searchable)
        assertFalse(sites[0].quickSearch)
        assertFalse(sites[0].changeable)
    }

    @Test
    fun testBooleanFieldsFromInt() {
        val sites = parser.parseSites(
            listOf(
                mapOf(
                    "key" to "k1",
                    "name" to "n1",
                    "api" to "api1",
                    "searchable" to 0,
                    "quickSearch" to 1,
                    "changeable" to 1,
                ),
            )
        )
        assertFalse(sites[0].searchable)
        assertTrue(sites[0].quickSearch)
        assertTrue(sites[0].changeable)
    }

    @Test
    fun testEmptyAndNullFields() {
        val config = parser.parse(emptyMap<String, Any>())
        assertNull(config.spiderUrl)
        assertTrue(config.sites.isEmpty())
        assertTrue(config.lives.isEmpty())
        assertTrue(config.flags.isEmpty())
    }

    @Test
    fun testSitesMissingRequiredFields() {
        // 缺少 key/name/api 的站点应被跳过
        val sites = parser.parseSites(
            listOf(
                mapOf("key" to "k1"), // 缺少 name, api
                mapOf("name" to "n2", "api" to "api2"), // 缺少 key
                mapOf("key" to "k3", "name" to "n3", "api" to "api3"), // 完整
            )
        )
        assertEquals(1, sites.size)
        assertEquals("k3", sites[0].key)
    }

    @Test
    fun testStringValueHandlesJsonElements() {
        // stringValue 应能处理 JsonPrimitive
        val jsonStr = """{"key": "value", "num": 42, "empty": ""}"""
        val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr)
            as kotlinx.serialization.json.JsonObject

        assertEquals("value", parser.stringValue(jsonObj["key"]))
        assertEquals("42", parser.stringValue(jsonObj["num"]))
        assertNull(parser.stringValue(jsonObj["empty"]))
    }

    @Test
    fun testIntValueHandlesStrings() {
        assertEquals(42, parser.intValue("42"))
        assertEquals(1, parser.intValue("true"))
        assertEquals(0, parser.intValue("false"))
        assertNull(parser.intValue(""))
        assertNull(parser.intValue("abc"))
    }

    @Test
    fun testFlagsFilterNonStrings() {
        val flags = parser.parseFlags(listOf("qq", 123, "iqiyi", null, "youku"))
        assertEquals(listOf("qq", "iqiyi", "youku"), flags)
    }
}
