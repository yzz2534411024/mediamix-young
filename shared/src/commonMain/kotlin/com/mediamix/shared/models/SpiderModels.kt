package com.mediamix.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ============================================================
// 蜘蛛类型
// ============================================================

/** 蜘蛛类型 */
@Serializable
enum class SpiderType {
    @SerialName("cms")
    CMS,

    @SerialName("xpath")
    XPATH,

    @SerialName("json")
    JSON,

    @SerialName("site")
    SITE,

    @SerialName("javaBridge")
    JAVA_BRIDGE,
}

// ============================================================
// 首页推荐结果
// ============================================================

/** 首页推荐结果 */
@Serializable
data class SpiderHomeResult(
    val categories: List<SpiderCategory> = emptyList(),
    val recommend: List<VideoItem> = emptyList(),
    val classList: Map<String, List<VideoItem>>? = null,
)

// ============================================================
// 列表结果
// ============================================================

/** 列表结果 */
@Serializable
data class SpiderListResult(
    val list: List<VideoItem> = emptyList(),
    val page: Int = 1,
    val pageCount: Int = 1,
    val total: Int = 0,
)

// ============================================================
// 详情结果
// ============================================================

/** 详情结果 */
@Serializable
data class SpiderDetailResult(
    val detail: VideoDetail? = null,
)

// ============================================================
// 播放结果
// ============================================================

/** 播放结果 */
@Serializable
data class SpiderPlayResult(
    val url: String = "",
    /**
     * TVBox/CatVod 约定的 `header` 字段原文。
     *
     * ⚠️ 是**单数** `header`，值是 **JSON 字符串**（不是对象），需要二次解析。
     * 只读 `headers`（复数）会把这些防盗链头整段丢掉 —— 表现就是播放直接 403。
     */
    val header: String? = null,
    val headers: Map<String, String>? = null,
    val parse: String? = null,
    /** 解析器标识，对应 [VideoParser.key]（`parse=1` 时用来挑解析线路）。 */
    val jx: String? = null,
    val playUrl: String? = null,
    val format: String? = null,
) {
    /** "0"=直连, "1"=需二次解析 */
    val needsParse: Boolean get() = parse == "1"

    /**
     * 取可用的请求头。
     *
     * `headers`（对象，部分蜘蛛会直接给）优先；否则解析 `header`（JSON 字符串）。
     * 两种形态都兼容，解析失败返回空表而不是抛异常 —— 播放头不是关键路径，
     * 缺了顶多 403，不该因此崩掉调用链。
     */
    fun headerMap(): Map<String, String> {
        headers?.takeIf { it.isNotEmpty() }?.let { return it }
        val raw = header?.trim().orEmpty()
        if (raw.isEmpty()) return emptyMap()
        val obj =
            runCatching {
                Json.parseToJsonElement(raw) as? JsonObject
            }.getOrNull() ?: return emptyMap()
        return obj.entries
            .mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { key to it }
            }.toMap()
    }
}

// ============================================================
// 蜘蛛分类
// ============================================================

/** 蜘蛛分类 */
@Serializable
data class SpiderCategory(
    val typeId: String,
    val typeName: String,
    val filters: List<SpiderFilter>? = null,
)

// ============================================================
// 筛选条件
// ============================================================

/** 筛选条件 */
@Serializable
data class SpiderFilter(
    val key: String,
    val name: String,
    val values: List<SpiderFilterValue>,
)

// ============================================================
// 筛选值
// ============================================================

/** 筛选值 */
@Serializable
data class SpiderFilterValue(
    val value: String,
    val name: String,
)

// ============================================================
// TVBox 配置
// ============================================================

/** TVBox 配置 */
@Serializable
data class TvBoxConfig(
    /** 蜘蛛包地址（已去掉 `;md5;<hash>` 后缀的裸 URL） */
    val spiderUrl: String? = null,
    /**
     * 蜘蛛字段原文，形如 `url;md5;<hash>`（饭太硬实测为 3 段）。
     *
     * **为什么不复用 [spiderUrl]**：`parseSpiderUrl` 会按分号截断，只留 URL ——
     * 于是 `loadSpiderJar` 再也拿不到 md5，md5 校验与「按 md5 缓存」双双失效，
     * 每次冷启都要重下 1.1MB 的蜘蛛包。这里保留原文供加载器提取校验值。
     */
    val spiderSpec: String? = null,
    val sites: List<TvBoxSite> = emptyList(),
    val lives: List<TvBoxLive> = emptyList(),
    val flags: List<String> = emptyList(),
)

/** TVBox 站点 */
@Serializable
data class TvBoxSite(
    val key: String,
    val name: String,
    val type: Int = 0,
    val api: String,
    val ext: String? = null,
    val jar: String? = null,
    val playerType: Int? = null,
    val searchable: Boolean = true,
    val quickSearch: Boolean = false,
    val changeable: Boolean = false,
) {
    /**
     * 站点的解析方式分类。
     *
     * ⚠️ 不能用「api 以 csp_ 开头」一刀切判定为 jar 蜘蛛：
     * `csp_XPath` / `csp_XPathFilter` / `csp_XPathMac` 的解析规则就写在 [ext] 字段里
     * （XPath JSON 或其 URL），**不需要加载任何 jar**，本项目的 [XpathSpider] 可直接执行。
     * 真正需要 TVBox 内核（dex/jar + JavaBridge）的只是其余的 csp_* 蜘蛛。
     *
     * 同时尊重 TVBox 的 type 约定：`type=3` 即 XPath 蜘蛛（此时 api 是规则地址，
     * 经 ext 通道下发给 XpathSpider），不能因为 api 恰好以 http 开头就改判成 CMS。
     */
    val kind: SiteKind
        get() =
            when {
                api.startsWith("csp_XPath") -> SiteKind.XPATH
                api.startsWith("csp_") -> SiteKind.JAR
                type == 3 -> SiteKind.XPATH
                else -> SiteKind.CMS // type=0/1 或未标注 → CmsSpider / JsonSpider
            }

    /**
     * 是否为需要 TVBox 内核（jar/dex）的蜘蛛。
     *
     * 语义已修正：只对真 jar 蜘蛛返回 true。此前对 csp_ 前缀一刀切，
     * 导致 csp_XPath 类站点（无需内核）也被过滤/拒绝建蜘蛛 —— 这正是
     * 「饭太硬 47 个站点全部不可用」的原因之一。
     */
    val isJavaSpider: Boolean get() = kind == SiteKind.JAR
}

/** TVBox 站点的解析方式 */
enum class SiteKind {
    /** HTTP 直连的 CMS 接口，走 CmsSpider */
    CMS,

    /** XPath 规则蜘蛛（csp_XPath*），规则在 ext 字段，走 XpathSpider */
    XPATH,

    /** 需要 TVBox 内核（dex/jar）的 Java 蜘蛛，本项目无法执行 */
    JAR,
}

/** TVBox 直播源 */
@Serializable
data class TvBoxLive(
    val name: String,
    val type: String? = null,
    val url: String,
    val playerType: Int? = null,
)
