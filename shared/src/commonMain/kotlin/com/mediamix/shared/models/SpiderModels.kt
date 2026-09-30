package com.mediamix.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// 蜘蛛类型
// ============================================================

/** 蜘蛛类型 */
@Serializable
enum class SpiderType {
    @SerialName("cms") CMS,
    @SerialName("xpath") XPATH,
    @SerialName("json") JSON,
    @SerialName("site") SITE,
    @SerialName("javaBridge") JAVA_BRIDGE,
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
    val headers: Map<String, String>? = null,
    val parse: String? = null,
    val playUrl: String? = null,
) {
    /** "0"=直连, "1"=需二次解析 */
    val needsParse: Boolean get() = parse == "1"
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
    val spiderUrl: String? = null,
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
    /** 是否为 Java 蜘蛛（csp_* 格式） */
    val isJavaSpider: Boolean get() = api.startsWith("csp_")
}

/** TVBox 直播源 */
@Serializable
data class TvBoxLive(
    val name: String,
    val type: String? = null,
    val url: String,
    val playerType: Int? = null,
)
