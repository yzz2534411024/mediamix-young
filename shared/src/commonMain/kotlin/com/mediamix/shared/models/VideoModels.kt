package com.mediamix.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

// ============================================================
// 异常
// ============================================================

/** 蜘蛛引擎异常（如 Java Bridge 不可用） */
class SpiderEngineException(
    message: String,
) : Exception(message)

/** 数据源不可用（网络失败、格式不支持等），message 可直接展示给用户 */
class SourceUnavailableException(
    message: String,
) : Exception(message)

// ============================================================
// JSON 取值工具
// ============================================================

/**
 * 从 CMS 接口返回的字段中安全地取出字符串。
 *
 * **⚠️ 不要再用 `JsonElement.toString()` 取 CMS 字段值。**
 * `Json.parseToJsonElement` 产出的是 `JsonLiteral`，它的 `toString()` 会补上
 * JSON 引号：字段值 `abc` 会变成 `"abc"`。播放地址被这样处理后尾部会多一个
 * `"`，ExoPlayer 直接抛 Source error —— 这正是「详情页能打开、点播放就报错」
 * 的根因。这里统一改用 [JsonPrimitive.content]。
 */
fun Any?.plainText(): String? =
    when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> content.trim().ifEmpty { null }
        is JsonElement -> toString().trim().ifEmpty { null }
        else -> toString()?.trim()?.ifEmpty { null }
    }

/**
 * 清洗播放地址：去掉 JSON 残留引号、包裹空白和全角空格。
 *
 * 只做「去污染」，不做「白名单」——CMS 各家的地址形态差异很大（直链 m3u8、
 * 网盘分享页、需要二次解析的相对路径都有），用过于严格的正则去筛反而会把
 * 本来能播的地址丢掉。清洗后仍含空白或为空才判定为无效。
 */
internal fun sanitizePlayUrl(raw: String?): String? {
    if (raw == null) return null
    val cleaned =
        raw
            .trim()
            .trim('"', '\'', '\u201C', '\u201D', '\u2018', '\u2019', '\uFF02')
            .replace("\u3000", "")
            .trim()
    if (cleaned.isEmpty()) return null
    if (cleaned.any { it.isWhitespace() }) return null
    return cleaned
}

// ============================================================
// CMS API 站点
// ============================================================

/** CMS API 站点 */
@Serializable
data class CmsApiSite(
    val key: String,
    val name: String,
    val apiUrl: String,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val isTvBox: Boolean = false,
) {
    companion object {
        /**
         * 内置源列表。
         *
         * **2026-09-30 实测**。测试接口为 `{api}?ac=detail&pg=1`（即首页实际调用的接口），
         * 记录首包响应耗时并**按快慢排序** —— 首页默认落在第一个源上，顺序直接影响打开速度：
         *
         * | 源 | 首包耗时 |
         * |---|---|
         * | 魔都资源 | 2.1s |
         * | 极速资源 | 2.1s |
         * | 樱花资源 | 2.2s |
         * | 红牛资源 | 2.3s |
         * | 百度资源 | 2.5s |
         * | 最大资源 | 2.7s |
         * | 无尽资源 | 3.6s |
         * | 天涯资源 | 4.2s |
         * | 暴风资源 | 5.7s |
         * | 豆瓣资源 | 5.7s |
         * | 量子资源 | 9.4s（最慢，但资源库最大）|
         *
         * ⚠️ **不要用 `?ac=list` 判活**：该接口在部分采集站上不可用或极慢，会误判成
         * 「站点已下线」。天涯、魔都就曾被这样误杀 —— 换用 `?ac=detail&pg=1` 复测后
         * 两者都完全正常，魔都还是响应最快的源。
         *
         * 末尾两个 `http://` 源在本次测试中被网络层拦截（403），未能验证真伪，故排在最后。
         *
         * 饭太硬是 TVBox 配置源（该地址现在返回的其实是一张 JPEG），其配置里 47 个站点
         * **全部**是 `csp_*` Java 蜘蛛，需要 TVBox 的 jar + JS 引擎才能解析，本项目架构跑不了。
         * 保留在列表里是为了让用户能看到并理解；首页会给出明确提示并引导切换。
         */
        val defaultSites: List<CmsApiSite> =
            listOf(
                CmsApiSite(key = "mdzyapi", name = "魔都资源", apiUrl = "https://www.mdzyapi.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "jszyapi", name = "极速资源", apiUrl = "https://jszyapi.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "apiYhzy", name = "樱花资源", apiUrl = "https://m3u8.apiyhzy.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "hnzy", name = "红牛资源", apiUrl = "https://hongniuzy2.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "apibdzy", name = "百度资源", apiUrl = "https://api.apibdzy.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "zuidapi", name = "最大资源", apiUrl = "https://api.zuidapi.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "apiwujin", name = "无尽资源", apiUrl = "https://api.wujinapi.me/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "tyyszy", name = "天涯资源", apiUrl = "https://tyyszy.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "bfzy", name = "暴风资源", apiUrl = "https://bfzyapi.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "dbzy", name = "豆瓣资源", apiUrl = "https://dbzy.tv/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "lzzy", name = "量子资源", apiUrl = "https://cj.lziapi.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "ffzy", name = "非凡资源", apiUrl = "http://ffzy5.tv/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "dyttzyapi", name = "电影天堂", apiUrl = "http://caiji.dyttzyapi.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(
                    key = "fantaiying",
                    name = "饭太硬 (TVBox)",
                    apiUrl = "http://www.xn--sss604efuw.net/tv",
                    isBuiltIn = true,
                    isTvBox = true,
                ),
            )

        /**
         * 已从默认列表移除的失效源，仅用于兼容旧配置里的 key 反查名字。
         *
         * 移除依据（2026-09-30 实测）：
         * - 如意资源：TLS 握手失败（UNEXPECTED_EOF_WHILE_READING）
         * - 小猫咪资源：HTTP 404
         */
        val retiredSites: List<CmsApiSite> =
            listOf(
                CmsApiSite(key = "rycjapi", name = "如意资源", apiUrl = "https://cj.rycjapi.com/api.php/provide/vod/", isBuiltIn = true),
                CmsApiSite(key = "xiaomaomi", name = "小猫咪资源", apiUrl = "https://zy.xiaomaomi.cc/api.php/provide/vod/", isBuiltIn = true),
            )

        /** 按 key 查源（含已下线源，用于详情页反查 apiUrl） */
        fun findByKey(key: String): CmsApiSite? = defaultSites.find { it.key == key } ?: retiredSites.find { it.key == key }

        /** 第一个可用的非 TVBox 源，作为兜底/首选项 */
        val firstCmsSite: CmsApiSite get() = defaultSites.first { !it.isTvBox }
    }
}

// ============================================================
// 枚举
// ============================================================

/** 视频源类型 */
@Serializable
enum class SourceType {
    @SerialName("cms")
    CMS,

    @SerialName("spider")
    SPIDER,
}

// ============================================================
// 视频源
// ============================================================

/** 视频源（CMS 或 Spider） */
@Serializable
data class VideoSource(
    val key: String,
    val name: String,
    val apiUrl: String,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val sourceType: SourceType = SourceType.CMS,
    val spiderKey: String? = null,
    val playerType: String? = null,
) {
    companion object {
        fun fromCmsSite(site: CmsApiSite): VideoSource =
            VideoSource(
                key = site.key,
                name = site.name,
                apiUrl = site.apiUrl,
                enabled = site.enabled,
                isBuiltIn = site.isBuiltIn,
                sourceType = SourceType.CMS,
            )
    }
}

// ============================================================
// 源状态检测结果
// ============================================================

/** 源状态检测结果 */
@Serializable
data class SourceStatus(
    val key: String,
    val isAvailable: Boolean,
    val latencyMs: Int = -1,
    val error: String? = null,
)

// ============================================================
// 影片条目（列表/搜索结果）
// ============================================================

/** 影片条目（列表/搜索结果） */
@Serializable
data class VideoItem(
    val vodId: String,
    val vodName: String,
    val vodPic: String? = null,
    val vodRemarks: String? = null,
    val vodYear: String? = null,
    val vodArea: String? = null,
    val typeName: String? = null,
    val sourceKey: String? = null,
) {
    companion object {
        fun fromJson(
            json: Map<String, Any?>,
            sourceKey: String? = null,
        ): VideoItem =
            VideoItem(
                vodId = json["vod_id"].plainText() ?: "",
                vodName = json["vod_name"].plainText() ?: "未知",
                vodPic = json["vod_pic"].plainText(),
                vodRemarks = json["vod_remarks"].plainText(),
                vodYear = json["vod_year"].plainText(),
                vodArea = json["vod_area"].plainText(),
                typeName = json["type_name"].plainText(),
                sourceKey = sourceKey,
            )
    }
}

// ============================================================
// 影片列表响应
// ============================================================

/** 影片列表响应 */
@Serializable
data class VideoListResponse(
    val list: List<VideoItem>,
    val page: Int,
    val pageCount: Int,
    val total: Int,
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): VideoListResponse {
            val rawList = json["list"] as? List<*> ?: emptyList<Any>()
            val items =
                rawList.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { m ->
                        @Suppress("UNCHECKED_CAST")
                        VideoItem.fromJson(m as Map<String, Any?>)
                    }
                }
            return VideoListResponse(
                list = items,
                page = json["page"].plainText()?.toIntOrNull() ?: 1,
                pageCount = json["pagecount"].plainText()?.toIntOrNull() ?: 1,
                total = json["total"].plainText()?.toIntOrNull() ?: 0,
            )
        }
    }
}

// ============================================================
// 影片选集
// ============================================================

/** 影片选集 */
@Serializable
data class VideoEpisode(
    val name: String,
    val url: String,
)

// ============================================================
// 播放源（一个源包含多个剧集）
// ============================================================

/** 播放源（一个源包含多个剧集） */
@Serializable
data class PlaySource(
    val name: String,
    val episodes: List<VideoEpisode>,
)

// ============================================================
// 影片详情
// ============================================================

/** 影片详情 */
@Serializable
data class VideoDetail(
    val vodId: String,
    val vodName: String,
    val vodPic: String? = null,
    val vodContent: String? = null,
    val vodActor: String? = null,
    val vodDirector: String? = null,
    val vodYear: String? = null,
    val vodArea: String? = null,
    val vodRemarks: String? = null,
    val typeName: String? = null,
    val typeId: Int? = null,
    val sourceKey: String,
    val playSources: List<PlaySource> = emptyList(),
) {
    /** 是否有任何可播放的剧集 */
    val hasPlayableSource: Boolean get() = playSources.any { it.episodes.isNotEmpty() }

    /** 默认应该选中的播放源下标：优先第一个「剧集多」的源 */
    val defaultSourceIndex: Int
        get() = playSources.indices.maxByOrNull { playSources[it].episodes.size } ?: 0

    /**
     * 同一集在**其它播放源**里的候选地址。
     *
     * CMS 站点通常同时提供 `m3u8`/`lz`/`share` 等多条线路，其中部分线路是网盘
     * 分享页、需要二次解析，直连必然失败。播放失败时按顺序回退到这些候选地址，
     * 能显著提升「点进去就能播」的成功率。
     */
    fun fallbackUrlsFor(
        episodeIndex: Int,
        currentSourceIndex: Int,
    ): List<String> {
        if (playSources.size <= 1) return emptyList()
        val result = mutableListOf<String>()
        for (index in playSources.indices) {
            if (index == currentSourceIndex) continue
            val url = playSources[index].episodes.getOrNull(episodeIndex)?.url
            if (!url.isNullOrBlank() && url !in result) result.add(url)
        }
        return result
    }

    companion object {
        fun fromJson(
            json: Map<String, Any?>,
            sourceKey: String = "",
        ): VideoDetail {
            val sources = mutableListOf<PlaySource>()
            val vodPlayFrom = json["vod_play_from"].plainText() ?: ""
            val vodPlayUrl = json["vod_play_url"].plainText() ?: ""

            if (vodPlayFrom.isNotEmpty() && vodPlayUrl.isNotEmpty()) {
                val fromNames = vodPlayFrom.split("$$$")
                val fromUrls = vodPlayUrl.split("$$$")
                for (i in fromNames.indices) {
                    if (i >= fromUrls.size) break
                    val sourceName = fromNames[i].plainText() ?: "线路${i + 1}"
                    val episodes = parseEpisodes(fromUrls[i])
                    if (episodes.isNotEmpty()) {
                        sources.add(PlaySource(name = sourceName, episodes = episodes))
                    }
                }
            }

            return VideoDetail(
                vodId = json["vod_id"].plainText() ?: "",
                vodName = json["vod_name"].plainText() ?: "未知",
                vodPic = json["vod_pic"].plainText(),
                vodContent = json["vod_content"].plainText(),
                vodActor = json["vod_actor"].plainText(),
                vodDirector = json["vod_director"].plainText(),
                vodYear = json["vod_year"].plainText(),
                vodArea = json["vod_area"].plainText(),
                vodRemarks = json["vod_remarks"].plainText(),
                typeName = json["type_name"].plainText(),
                typeId = json["type_id"].plainText()?.toIntOrNull(),
                sourceKey = sourceKey,
                playSources = sources,
            )
        }

        /**
         * 解析形如 `第1集$http://a.m3u8#第2集$http://b.m3u8` 的剧集串。
         *
         * 注意用 `indexOf('$')` 而不是 `split("$")`：部分 CDN 地址本身带 `$`，
         * 直接 split 会把地址截断成半截。
         */
        internal fun parseEpisodes(raw: String?): List<VideoEpisode> {
            val text = raw?.trim().orEmpty().trim('"')
            if (text.isEmpty()) return emptyList()

            val episodes = mutableListOf<VideoEpisode>()
            for (line in text.split("#")) {
                if (line.isBlank()) continue
                val sep = line.indexOf('$')
                val nameRaw = if (sep >= 0) line.substring(0, sep) else line
                val urlRaw = if (sep >= 0) line.substring(sep + 1) else ""
                val url = sanitizePlayUrl(urlRaw) ?: continue
                val name = nameRaw.trim().trim('"').ifEmpty { "第${episodes.size + 1}集" }
                episodes.add(VideoEpisode(name = name, url = url))
            }
            return episodes
        }
    }
}

// ============================================================
// 影片分类
// ============================================================

/** 影片分类 */
@Serializable
data class VideoCategory(
    val typeId: Int,
    val typePid: Int,
    val typeName: String,
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): VideoCategory =
            VideoCategory(
                typeId = json["type_id"].plainText()?.toIntOrNull() ?: 0,
                typePid = json["type_pid"].plainText()?.toIntOrNull() ?: 0,
                typeName = json["type_name"].plainText() ?: "",
            )
    }
}

// ============================================================
// 视频解析接口
// ============================================================

/** 视频解析接口 */
@Serializable
data class VideoParser(
    val key: String,
    val name: String,
    val urlTemplate: String,
    val enabled: Boolean = true,
) {
    /** 构建解析后的完整 URL */
    fun buildUrl(videoUrl: String): String = urlTemplate.replace("{url}", videoUrl)

    companion object {
        val defaultParsers: List<VideoParser> =
            listOf(
                VideoParser(key = "yparse", name = "YParse", urlTemplate = "https://yparse.ik9.cc/index.php?url={url}"),
                VideoParser(key = "m3u8tv", name = "M3U8.TV", urlTemplate = "https://jx.m3u8.tv/jiexi/?url={url}"),
                VideoParser(key = "ik9", name = "IK9 自建", urlTemplate = "http://82.156.40.118:1234/jx/?url={url}"),
                VideoParser(key = "oftens", name = "Oftens", urlTemplate = "https://jx.oftens.top/player/?url={url}"),
                VideoParser(key = "jlk", name = "JLK解析", urlTemplate = "https://jlk.jianghu.vip/?url={url}"),
            )
    }
}
