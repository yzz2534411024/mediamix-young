package com.mediamix.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SourceRef] 与接口多线路的回归测试。
 *
 * 复合源标识解决的是「TVBox 影片点进详情报找不到数据源」：
 * 影片的 `sourceKey` 此前直接是 TVBox 站点 key（`douDou`），
 * 而详情页拿它去 `CmsApiSite.findByKey()` 反查 —— 两边对不上。
 */
class SourceRefTest {
    @Test
    fun composeAndSplitRoundTrip() {
        val ref = SourceRef.compose("fantaiying", "cDouDou")

        assertEquals("fantaiying::cDouDou", ref)
        assertEquals("fantaiying", SourceRef.configKey(ref))
        assertEquals("cDouDou", SourceRef.siteKey(ref))
        assertTrue(SourceRef.isTvBox(ref))
    }

    @Test
    fun plainKeyIsNotTvBox() {
        assertFalse(SourceRef.isTvBox("mdzyapi"))
        // 非 TVBox 时 configKey 原样返回、siteKey 为空 —— 调用方据此走 CMS 分支
        assertEquals("mdzyapi", SourceRef.configKey("mdzyapi"))
        assertEquals("", SourceRef.siteKey("mdzyapi"))
    }

    @Test
    fun siteKeyWithDotsAndDashesSurvives() {
        // 站点 key 常含 `.` / `_` / `-`，分隔符选 `::` 正是为了不与它们冲突
        val ref = SourceRef.compose("fantaiying", "csp_XPath-Mac.v2")

        assertEquals("fantaiying", SourceRef.configKey(ref))
        assertEquals("csp_XPath-Mac.v2", SourceRef.siteKey(ref))
    }

    @Test
    fun emptySiteKeyYieldsEmpty() {
        assertEquals("", SourceRef.siteKey("fantaiying::"))
    }
}

/**
 * 采集站接口多线路（[CmsApiSite.apiUrlCandidates]）。
 *
 * 饭太硬的官方域名只有 `.net` 可达，`.com` / `.top` 已失效 ——
 * 只写死一个地址时，域名一轮换整个源就彻底不可用。
 */
class CmsApiSiteCandidatesTest {
    @Test
    fun allApiUrlsPutsPrimaryFirstAndDeduplicates() {
        val site =
            CmsApiSite(
                key = "k",
                name = "n",
                apiUrl = "http://a/tv",
                apiUrlCandidates = listOf("http://b/tv", "http://a/tv", "http://c/tv"),
            )

        assertEquals(listOf("http://a/tv", "http://b/tv", "http://c/tv"), site.allApiUrls)
    }

    @Test
    fun allApiUrlsIsJustPrimaryWhenNoCandidates() {
        assertEquals(listOf("http://a/tv"), CmsApiSite(key = "k", name = "n", apiUrl = "http://a/tv").allApiUrls)
    }

    @Test
    fun fanTaiYingShipsThreeLines() {
        val site = CmsApiSite.defaultSites.first { it.key == "fantaiying" }

        assertTrue(site.isTvBox)
        // 主 + 两条备用；写死单条时域名轮换即整源失效
        assertEquals(3, site.allApiUrls.size)
        assertTrue(site.allApiUrls.all { it.endsWith("/tv") })
    }

    @Test
    fun candidatesSurviveSerialization() {
        val site =
            CmsApiSite(
                key = "k",
                name = "n",
                apiUrl = "http://a/tv",
                apiUrlCandidates = listOf("http://b/tv"),
            )

        val json =
            kotlinx.serialization.json.Json
                .encodeToString(CmsApiSite.serializer(), site)
        val restored =
            kotlinx.serialization.json.Json
                .decodeFromString(CmsApiSite.serializer(), json)

        assertEquals(site, restored)
    }
}
