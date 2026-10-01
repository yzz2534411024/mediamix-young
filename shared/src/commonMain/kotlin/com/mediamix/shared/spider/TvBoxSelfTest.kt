package com.mediamix.shared.spider

import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.TvBoxConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest

/**
 * TVBox 接口一键自检 —— 桌面端与 Android 共用。
 *
 * 桌面端**无法**执行 dex（JVM 平台边界），但链路的前半段（线路连通、配置拉取、
 * 伪装解码、站点解析、蜘蛛包下载与校验）全部可测 —— 用于把「接口本身是否正常」
 * 与「平台能力边界」分开判断，省去真机反复插拔。
 *
 * 每个环节一条 [TvBoxSelfTestItem]，ok=false 不中断后续环节（尽力多暴露信息）。
 */
data class TvBoxSelfTestItem(
    val name: String,
    val ok: Boolean,
    val detail: String,
)

class TvBoxSelfTestRunner(
    private val spiderService: SpiderService,
    private val httpClient: HttpClient,
) {
    suspend fun run(site: CmsApiSite?): List<TvBoxSelfTestItem> {
        if (site == null) {
            return listOf(TvBoxSelfTestItem("当前源", false, "未选中任何数据源"))
        }
        val items = mutableListOf<TvBoxSelfTestItem>()
        items += TvBoxSelfTestItem("数据源", true, "「${site.name}」isTvBox=${site.isTvBox}")

        if (!site.isTvBox) {
            items += TvBoxSelfTestItem("TVBox 自检", false, "当前源不是 TVBox 源，无需自检")
            return items
        }

        // ① 线路连通性 + 配置拉取 + 伪装解码（逐线路探测）
        var config: TvBoxConfig? = null
        var okUrl: String? = null
        var lastError: String? = null
        site.allApiUrls.forEachIndexed { index, url ->
            val label = "线路 ${index + 1}/${site.allApiUrls.size}"
            try {
                val c = withTimeout(12_000) { spiderService.fetchTvBoxConfig(url) }
                config = c
                okUrl = url
                items += TvBoxSelfTestItem(
                    "$label 配置拉取+解码",
                    true,
                    "${url.take(48)} → ${c.sites.size} 个站点，spider=${c.spiderSpec?.take(48) ?: "无"}",
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                items += TvBoxSelfTestItem("$label 配置拉取+解码", false, "$url → $lastError")
            }
        }

        val c = config
        if (c == null) {
            items += TvBoxSelfTestItem(
                "结论",
                false,
                "全部 ${site.allApiUrls.size} 条线路不可用（$lastError）。多线路已在网关自动轮换，" +
                    "持续失败多为域名整体失效或网络环境限制。",
            )
            return items
        }

        // ② 站点类型分布
        val jar = c.sites.count { it.isJavaSpider }
        val xpath = c.sites.count { !it.isJavaSpider && it.api.startsWith("csp_") }
        val direct = c.sites.size - jar - xpath
        items += TvBoxSelfTestItem(
            "站点解析",
            true,
            "共 ${c.sites.size} 个：JAR 蜘蛛 $jar 个 · XPath $xpath 个 · 直连 $direct 个",
        )

        // ③ 蜘蛛包下载 + md5 + zip 结构检查（桌面端可完整执行）
        val spiderSpec = c.spiderSpec
        if (spiderSpec.isNullOrBlank()) {
            items += TvBoxSelfTestItem("蜘蛛包", false, "配置未携带 spider 字段")
        } else {
            val expectedMd5 = Regex(""";md5;([0-9a-fA-F]{32})""").find(spiderSpec)?.groupValues?.get(1)
            val realUrl = spiderSpec.substringBefore(";")
            try {
                val bytes = withTimeout(90_000) { httpClient.get(realUrl).readRawBytes() }
                val actual = bytes.md5Hex()
                val md5Ok = expectedMd5 == null || actual.equals(expectedMd5, ignoreCase = true)
                val zipReport = inspectZip(bytes)
                items += TvBoxSelfTestItem(
                    "蜘蛛包下载",
                    md5Ok && zipReport.second,
                    "${bytes.size} bytes，md5=${actual.take(12)}${if (md5Ok) " ✓" else " ✗（期望 $expectedMd5）"}，${zipReport.first}",
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                items += TvBoxSelfTestItem("蜘蛛包下载", false, "${e.javaClass.simpleName}: ${e.message}")
            }
        }

        // ④ 蜘蛛桥状态（Android：DexClassLoader；Desktop：显示平台边界）
        items += TvBoxSelfTestItem("蜘蛛桥（平台）", true, spiderService.spiderBridgeStatus)

        // ⑤ 生效线路
        items += TvBoxSelfTestItem("生效线路", true, okUrl ?: "未知")

        return items
    }

    /** 检查 zip 结构：classes.dex 与 assets so 是否在位。返回 (描述, dex 是否在位)。 */
    private fun inspectZip(bytes: ByteArray): Pair<String, Boolean> {
        return try {
            val tmp = java.io.File.createTempFile("spider_test", ".zip")
            tmp.writeBytes(bytes)
            tmp.deleteOnExit()
            java.util.zip.ZipFile(tmp).use { zf ->
                val names = zf.entries().toList().map { it.name }
                val hasDex = names.any { it == "classes.dex" }
                val soCount = names.count { it.endsWith(".so") }
                val guard = names.count { it.contains("guard") }
                "zip：dex=${if (hasDex) "有" else "无"}，so=$soCount，guard=$guard，共 ${names.size} 项" to hasDex
            }
        } catch (e: Exception) {
            "zip 结构检查失败: ${e.message}" to false
        }
    }

    private fun ByteArray.md5Hex(): String =
        MessageDigest.getInstance("MD5").digest(this).joinToString("") { "%02x".format(it) }
}
