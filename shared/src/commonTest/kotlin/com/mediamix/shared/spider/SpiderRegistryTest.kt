package com.mediamix.shared.spider

import com.mediamix.shared.models.TvBoxSite
import kotlin.test.*

class SpiderRegistryTest {
    private val registry = SpiderRegistry.instance

    @BeforeTest
    fun setUp() {
        registry.disposeAll()
        registry.javaBridgeManager = null
    }

    // ==================== 内置类型映射 ====================

    @Test
    fun testBuildSpider_type0_returnsCmsSpider() {
        val site = TvBoxSite(key = "cms1", name = "CMS站", type = 0, api = "http://api.example.com")
        val spider = registry.buildSpider(site)
        assertNotNull(spider)
        assertTrue(spider is CmsSpider)
        assertEquals("cms1", spider.key)
    }

    @Test
    fun testBuildSpider_type1_returnsJsonSpider() {
        val site = TvBoxSite(key = "json1", name = "JSON站", type = 1, api = "http://api.example.com")
        val spider = registry.buildSpider(site)
        assertNotNull(spider)
        assertTrue(spider is JsonSpider)
    }

    @Test
    fun testBuildSpider_type3_returnsXpathSpider() {
        // 新语义：XPath 站点必须带 ext 规则（无 ext 无法工作，会被剔除）
        val site =
            TvBoxSite(
                key = "xpath1",
                name = "XPath站",
                type = 3,
                api = "http://api.example.com",
                ext = "{\"homeUrl\":\"https://example.com\"}",
            )
        val spider = registry.buildSpider(site)
        assertNotNull(spider)
        assertTrue(spider is XpathSpider)
    }

    @Test
    fun testBuildSpider_type3_withoutExt_returnsNull() {
        // 无 ext 的 XPath 站点无法工作（曾把 api 当 URL 请求到 localhost:80），应剔除
        val site = TvBoxSite(key = "xpath2", name = "XPath空规则", type = 3, api = "http://api.example.com")
        assertNull(registry.buildSpider(site))
    }

    @Test
    fun testBuildSpider_unknownType_returnsNull() {
        val site = TvBoxSite(key = "unknown", name = "未知站", type = 99, api = "http://api.example.com")
        val spider = registry.buildSpider(site)
        assertNull(spider)
    }

    // ==================== 自定义工厂 ====================

    @Test
    fun testRegister_customFactory() {
        val site = TvBoxSite(key = "custom", name = "自定义站", type = 0, api = "http://api.example.com")
        var factoryCalled = false

        registry.register("custom") { s ->
            factoryCalled = true
            assertEquals("custom", s.key)
            CmsSpider(site = s)
        }

        val spider = registry.buildSpider(site)
        assertNotNull(spider)
        assertTrue(factoryCalled)
        assertEquals("custom", spider.key)
    }

    @Test
    fun testCustomFactory_takesPriorityOverBuiltin() {
        // type=0 normally creates CmsSpider, but custom factory should override
        val site = TvBoxSite(key = "override", name = "覆盖站", type = 0, api = "http://api.example.com")
        registry.register("override") { s -> XpathSpider(site = s) }

        val spider = registry.buildSpider(site)
        assertNotNull(spider)
        assertTrue(spider is XpathSpider)
    }

    // ==================== Java 蜘蛛 ====================

    @Test
    fun testBuildSpider_javaSpider_noManager_returnsNull() {
        // csp_* 格式但 javaBridgeManager 为 null
        val site = TvBoxSite(key = "csp_test", name = "Java站", type = 3, api = "csp_Test")
        assertNull(registry.javaBridgeManager)

        val spider = registry.buildSpider(site)
        // 无 manager 时 csp_* 蜘蛛无法创建，type=3 且 isJavaSpider → null
        assertNull(spider)
    }

    // ==================== 缓存实例复用 ====================

    @Test
    fun testCreateFromSite_cachesInstance() =
        runTestAsync {
            val site = TvBoxSite(key = "cache_test", name = "缓存站", type = 0, api = "http://api.example.com")

            val spider1 = registry.createFromSite(site)
            val spider2 = registry.createFromSite(site)

            assertNotNull(spider1)
            assertSame(spider1, spider2, "应返回同一缓存实例")
        }

    @Test
    fun testGet_returnsCachedInstance() =
        runTestAsync {
            val site = TvBoxSite(key = "get_test", name = "获取站", type = 0, api = "http://api.example.com")
            assertNull(registry.get("get_test"))

            val spider = registry.createFromSite(site)
            assertNotNull(spider)
            assertSame(spider, registry.get("get_test"))
        }

    @Test
    fun testAll_returnsAllCached() =
        runTestAsync {
            val site1 = TvBoxSite(key = "a1", name = "站1", type = 0, api = "http://a1.com")
            val site2 = TvBoxSite(key = "a2", name = "站2", type = 1, api = "http://a2.com")

            registry.createFromSite(site1)
            registry.createFromSite(site2)

            assertEquals(2, registry.all.size)
        }

    // ==================== 批量创建 ====================

    @Test
    fun testCreateFromSites() =
        runTestAsync {
            val sites =
                listOf(
                    TvBoxSite(key = "b1", name = "站1", type = 0, api = "http://b1.com"),
                    TvBoxSite(key = "b2", name = "站2", type = 1, api = "http://b2.com"),
                    TvBoxSite(
                        key = "b3",
                        name = "站3",
                        type = 3,
                        api = "http://b3.com",
                        ext = "{\"homeUrl\":\"https://example.com\"}",
                    ),
                )

            val spiders = registry.createFromSites(sites)
            assertEquals(3, spiders.size)
        }

    @Test
    fun testCreateFromSites_skipsNull() =
        runTestAsync {
            val sites =
                listOf(
                    TvBoxSite(key = "c1", name = "站1", type = 0, api = "http://c1.com"),
                    TvBoxSite(key = "c2", name = "站2", type = 99, api = "http://c2.com"), // unknown type
                )

            val spiders = registry.createFromSites(sites)
            assertEquals(1, spiders.size)
        }

    // ==================== ext 字段解析 ====================

    @Test
    fun testParseExt_null() {
        val result = registry.parseExt(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testParseExt_url() {
        val result = registry.parseExt("https://example.com/config.json")
        assertEquals(mapOf("extUrl" to "https://example.com/config.json"), result)
    }

    @Test
    fun testParseExt_httpUrl() {
        val result = registry.parseExt("http://example.com/config")
        assertEquals(mapOf("extUrl" to "http://example.com/config"), result)
    }

    @Test
    fun testParseExt_jsonObject() {
        val jsonStr = """{"homeUrl":"http://home.com","categoryUrl":"http://cat.com"}"""
        val result = registry.parseExt(jsonStr)
        assertEquals("http://home.com", result["homeUrl"])
        assertEquals("http://cat.com", result["categoryUrl"])
    }

    @Test
    fun testParseExt_plainString() {
        val result = registry.parseExt("some_plain_text")
        assertEquals(mapOf("ext" to "some_plain_text"), result)
    }

    @Test
    fun testParseExt_invalidJson() {
        val result = registry.parseExt("{not valid json")
        assertEquals(mapOf("ext" to "{not valid json"), result)
    }

    // ==================== 释放和移除 ====================

    @Test
    fun testRemove() =
        runTestAsync {
            val site = TvBoxSite(key = "rm_test", name = "移除站", type = 0, api = "http://rm.com")
            registry.createFromSite(site)
            assertNotNull(registry.get("rm_test"))

            registry.remove("rm_test")
            assertNull(registry.get("rm_test"))
        }

    @Test
    fun testDisposeAll() =
        runTestAsync {
            val sites =
                listOf(
                    TvBoxSite(key = "d1", name = "站1", type = 0, api = "http://d1.com"),
                    TvBoxSite(key = "d2", name = "站2", type = 1, api = "http://d2.com"),
                )
            registry.createFromSites(sites)
            assertEquals(2, registry.all.size)

            registry.disposeAll()
            assertEquals(0, registry.all.size)
        }
}

/**
 * 简单的协程测试辅助（commonTest 中不支持 runTest 时回退）
 */
private fun runTestAsync(block: suspend () -> Unit) {
    kotlinx.coroutines.runBlocking { block() }
}
