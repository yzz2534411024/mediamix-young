package com.mediamix.shared.spider

import com.mediamix.shared.models.SpiderPlayResult
import kotlin.test.*

/**
 * TVBox 桥接映射层的回归测试。
 *
 * 这里覆盖的都是**曾经写错、且只在真机上才暴露**的纯逻辑：
 * 蜘蛛实例选取循环、播放结果字段约定、spider 字段的 md5 保留。
 * 它们全部是可离线验证的字符串/集合处理，所以放在 commonTest（Desktop 也能跑）。
 */
class SpiderBridgeMappingTest {
    // ==================== pickSpiderInstance ====================

    /**
     * 回归：候选 key 必须「取到即停」。
     *
     * 旧实现是 `for (k in keyCandidates) { spiderObj = fetch(k) }` —— 内层无条件把两个候选
     * 都问一遍，用后一个候选的返回值覆盖前一个。只要第二个候选落空，
     * 明明已经拿到手的实例也会被冲成 null，最终误报「getSpider 未取到实例」。
     */
    @Test
    fun stopsAtFirstHitAndDoesNotCallLaterCandidates() {
        val asked = mutableListOf<String>()
        val instance = Any()

        val picked =
            pickSpiderInstance(listOf("csp_DouDouGuard", "DouDouGuard")) { key ->
                asked += key
                if (key == "csp_DouDouGuard") instance else null
            }

        assertSame(instance, picked, "首个候选命中就应当直接返回该实例")
        assertEquals(listOf("csp_DouDouGuard"), asked, "命中后不应再询问后续候选")
    }

    @Test
    fun fallsBackToNextCandidateWhenFirstMisses() {
        val asked = mutableListOf<String>()
        val instance = Any()

        val picked =
            pickSpiderInstance(listOf("csp_X", "X")) { key ->
                asked += key
                if (key == "X") instance else null
            }

        assertSame(instance, picked)
        assertEquals(listOf("csp_X", "X"), asked)
    }

    @Test
    fun returnsNullWhenAllCandidatesMiss() {
        assertNull(pickSpiderInstance(listOf("a", "b")) { null })
    }

    @Test
    fun emptyCandidatesYieldNull() {
        val picked =
            pickSpiderInstance<String>(emptyList()) { key ->
                fail("候选为空时不应发起任何询问，却问了 $key")
            }
        assertNull(picked)
    }

    // ==================== SpiderPlayResult.headerMap ====================

    /**
     * TVBox/CatVod 的约定是**单数** `header`，值是 JSON **字符串**。
     * 只读 `headers`（复数对象）会把防盗链头整段丢掉，表现就是播放 403。
     */
    @Test
    fun parsesTvBoxHeaderJsonString() {
        val result = SpiderPlayResult(url = "http://a/b.m3u8", header = """{"User-Agent":"okhttp/3.12.11","Referer":"http://a/"}""")

        val headers = result.headerMap()

        assertEquals("okhttp/3.12.11", headers["User-Agent"])
        assertEquals("http://a/", headers["Referer"])
    }

    @Test
    fun prefersHeadersObjectWhenPresent() {
        val result =
            SpiderPlayResult(
                url = "http://a/b.m3u8",
                header = """{"User-Agent":"from-string"}""",
                headers = mapOf("User-Agent" to "from-object"),
            )

        assertEquals("from-object", result.headerMap()["User-Agent"])
    }

    @Test
    fun toleratesBrokenHeaderInsteadOfThrowing() {
        val result = SpiderPlayResult(url = "http://a/b.m3u8", header = "{这不是合法 JSON")

        assertTrue(result.headerMap().isEmpty(), "坏 header 应当退化为空表，而不是抛异常打断播放链路")
    }

    @Test
    fun blankHeaderYieldsEmptyMap() {
        assertTrue(SpiderPlayResult(url = "u").headerMap().isEmpty())
        assertTrue(SpiderPlayResult(url = "u", header = "   ").headerMap().isEmpty())
    }

    @Test
    fun needsParseReflectsParseFlag() {
        assertTrue(SpiderPlayResult(parse = "1").needsParse)
        assertFalse(SpiderPlayResult(parse = "0").needsParse)
        assertFalse(SpiderPlayResult().needsParse)
    }

    // ==================== spider 字段的 md5 保留 ====================

    /**
     * 回归：`spiderUrl` 会按分号截断（这是它的既有契约，有测试锁定），
     * 所以 md5 必须靠 `spiderSpec` 原文保留 —— 丢了两者都会没：
     * 既无法校验完整性，也无法按 md5 命中缓存，每次冷启重下 1.1MB。
     */
    @Test
    fun parseSpiderSpecKeepsMd5Suffix() {
        val parser = TvBoxConfigParser()
        val raw = "https://cdn.example.com/p.jpg;md5;2cc088afa757ba8bafffcfbab4b73ccc"

        assertEquals(raw, parser.parseSpiderSpec(raw))
        // 同一个原文，两条通道各自给出预期结果
        assertEquals("https://cdn.example.com/p.jpg", parser.parseSpiderUrl(raw))
    }

    @Test
    fun parseSpiderSpecNullForBlankOrNonString() {
        val parser = TvBoxConfigParser()
        assertNull(parser.parseSpiderSpec(null))
        assertNull(parser.parseSpiderSpec("   "))
        assertNull(parser.parseSpiderSpec(42))
    }

    @Test
    fun fullConfigCarriesBothSpiderUrlAndSpec() {
        val config =
            TvBoxConfigParser().parse(
                mapOf("spider" to "https://a/p.jpg;md5;2cc088afa757ba8bafffcfbab4b73ccc"),
            )

        assertEquals("https://a/p.jpg", config.spiderUrl)
        assertEquals(
            "https://a/p.jpg;md5;2cc088afa757ba8bafffcfbab4b73ccc",
            config.spiderSpec,
            "加载器需要原文才能提取 md5 做校验与缓存键",
        )
    }
}
