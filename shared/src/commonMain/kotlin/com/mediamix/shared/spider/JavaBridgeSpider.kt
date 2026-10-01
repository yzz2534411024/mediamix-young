package com.mediamix.shared.spider

import com.mediamix.shared.models.SourceRef
import com.mediamix.shared.models.SpiderDetailResult
import com.mediamix.shared.models.SpiderHomeResult
import com.mediamix.shared.models.SpiderListResult
import com.mediamix.shared.models.SpiderPlayResult
import com.mediamix.shared.models.SpiderType
import com.mediamix.shared.models.TvBoxSite
import com.mediamix.shared.models.VideoDetail
import com.mediamix.shared.models.VideoItem

/**
 * Java Bridge 蜘蛛适配器 —— 通过 [JavaBridgeManager.invokeMethod] 反射调用
 * TVBox 蜘蛛包（dex）里的 csp 类。
 *
 * 结果映射遵循 TVBox / CatVod 的 JSON 约定：
 * - home、category、search 返回一个含 list 数组的对象，home 另含 class 数组（站内分类）；
 * - detail 返回的 list 首项里，vod_play_from 与 vod_play_url 各用**三个**美元符分隔
 *   多条线路，线路内用井号分隔剧集、用单个美元符分隔集名与地址；
 * - player 返回 parse、jx、url、header 等字段，其中 header 是 JSON 字符串而非对象。
 *
 * （原文此处用内联代码贴了四段 JSON 样例，会让 ktlint 的解析器报
 * 「Closing bracket expected」而整个文件无法通过格式门禁，故改为文字描述。）
 */
class JavaBridgeSpider(
    private val site: TvBoxSite,
    /**
     * 所属 TVBox 配置源的 key（即 [CmsApiSite.key]，如 `fantaiying`）。
     *
     * 用来构造 [SourceRef] 复合标识 —— 只带站点 key 的话，详情页
     * 无法反查回是哪个配置源，会报「找不到数据源」。
     */
    private val configKey: String = "",
    private val bridgeManager: JavaBridgeManager = JavaBridgeManager.instance,
) : SpiderAdapter {
    /** 所属 TVBox 配置源 key（换线路时据此整体释放实例）。 */
    val configKeyForSource: String get() = configKey

    override val key: String get() = site.key
    override val name: String get() = site.name
    override val type: SpiderType get() = SpiderType.JAVA_BRIDGE

    override val isSearchSupported: Boolean
        get() = site.searchable

    /** 站点级 ext（部分蜘蛛用它拿规则/配置地址），透传给每次 invoke。 */
    private var siteExt: String? = null

    /**
     * 产物携带的源标识。
     *
     * 有 [configKey] 时用 `配置源::站点` 复合形式（详情页据此找回蜘蛛）；
     * 配置源缺失（旧调用方）时退化为裸站点 key，保持向后兼容。
     */
    private fun sourceRef(): String = if (configKey.isBlank()) site.key else SourceRef.compose(configKey, site.key)

    override suspend fun init(config: Map<String, Any>) {
        siteExt = (config["ext"] ?: config["extUrl"])?.toString()
    }

    /** 合并站点级参数后发起反射调用。invoke 的目标 key 用 api 原文（csp_Xxx 是类名约定）。 */
    private suspend fun invoke(
        method: String,
        args: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> =
        bridgeManager.invokeMethod(
            spiderKey = site.api.ifEmpty { site.key },
            method = method,
            args =
                buildMap {
                    putAll(args)
                    siteExt?.let { put("ext", it) }
                },
        )

    override suspend fun homeContent(page: Int): SpiderHomeResult {
        val map = invoke("homeContent", mapOf("page" to page, "filter" to false))
        if (isError(map)) return SpiderHomeResult()

        val categories =
            (map["class"] as? List<*>)
                ?.mapNotNull { entry ->
                    (entry as? Map<*, *>)?.let { m ->
                        val tid = m["type_id"]?.toString() ?: return@mapNotNull null
                        com.mediamix.shared.models.SpiderCategory(
                            typeId = tid,
                            typeName = m["type_name"]?.toString() ?: "",
                        )
                    }
                }.orEmpty()

        val recommend =
            (map["list"] as? List<*>)
                ?.mapNotNull { entry -> videoItemOf(entry) }
                .orEmpty()
        // 定位「壳返回了 list 但 UI 没数据」：原始条数 vs 映射后条数
        val rawListSize = (map["list"] as? List<*>)?.size ?: -1
        co.touchlab.kermit.Logger.withTag("JavaBridgeSpider").i {
            "homeContent 映射: 原始 $rawListSize 条 → 映射后 ${recommend.size} 条（$key）"
        }

        return SpiderHomeResult(categories = categories, recommend = recommend)
    }

    override suspend fun categoryContent(
        tid: String,
        page: Int,
        filter: Map<String, String>?,
    ): SpiderListResult {
        val map =
            invoke(
                "categoryContent",
                mapOf(
                    "tid" to tid,
                    "page" to page,
                    "filter" to false,
                    "extend" to filter.orEmpty(),
                ),
            )
        if (isError(map)) return SpiderListResult()

        val list =
            (map["list"] as? List<*>)
                ?.mapNotNull { entry -> videoItemOf(entry) }
                .orEmpty()

        return SpiderListResult(
            list = list,
            page = (map["page"] as? Number)?.toInt() ?: page,
            pageCount = (map["pagecount"] as? Number)?.toInt() ?: 1,
            total = (map["total"] as? Number)?.toInt() ?: list.size,
        )
    }

    override suspend fun detailContent(id: String): SpiderDetailResult {
        val map = invoke("detailContent", mapOf("id" to id))
        if (isError(map)) return SpiderDetailResult()

        val item =
            (map["list"] as? List<*>)
                ?.firstOrNull() as? Map<*, *>
                ?: return SpiderDetailResult()

        return SpiderDetailResult(detail = videoDetailOf(item, fallbackId = id))
    }

    override suspend fun searchContent(
        keyword: String,
        page: Int,
    ): SpiderListResult {
        val map = invoke("searchContent", mapOf("keyword" to keyword, "quick" to (page > 1)))
        if (isError(map)) return SpiderListResult()

        val list =
            (map["list"] as? List<*>)
                ?.mapNotNull { entry -> videoItemOf(entry) }
                .orEmpty()

        return SpiderListResult(list = list, page = page)
    }

    override suspend fun playerContent(
        flag: String,
        id: String,
    ): SpiderPlayResult {
        val map = invoke("playerContent", mapOf("flag" to flag, "id" to id))
        if (isError(map)) return SpiderPlayResult(url = id)

        val headers =
            (map["headers"] as? Map<*, *>)
                ?.entries
                ?.associate { it.key.toString() to it.value.toString() }
                ?.takeIf { it.isNotEmpty() }

        // ⚠️ 剥掉壳的本地代理前缀：壳假定宿主在 127.0.0.1:9978 跑 TVBox 代理
        // （返回形如 http://127.0.0.1:-1/proxy?do=m3u8&url=<enc 的地址，端口未配置时为 -1）。
        // 我们的播放器直连原始地址 + header 注入（ExoPlayer），不需要这层代理。
        // 同时把 danmaku 地址做同样处理。
        fun stripLocalProxy(u: String?): String? {
            val raw = u ?: return null
            // ⚠️ 只剥 do=m3u8（直链代理）。其它 do 类型（如网盘解析 do=py）的
            // proxy 地址是壳解析流程的一部分，剥掉会破坏网盘源播放。
            val match =
                Regex("""http://127\.0\.0\.1:[-0-9]+/proxy\?do=m3u8&url=([^&\s]+)""").find(raw)
                    ?: return raw
            val decoded =
                runCatching {
                    java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
                }.getOrDefault(match.groupValues[1])
            return decoded
        }

        val playUrl = stripLocalProxy(map["url"]?.toString()).orEmpty()

        return SpiderPlayResult(
            // ⚠️ 解析失败时**不要**回落成 id：id 是待解析的剧集标识（常为「集名$地址」
            // 或纯 token），把它当播放地址会发出一个必然失败的请求，还会把排查方向带偏。
            // 返回空串，由上层判定为「解析失败」并给出可读提示。
            url = playUrl,
            // TVBox/CatVod 的约定是**单数** header（JSON 字符串），不是 headers 对象。
            // 只读 headers 会把防盗链头整段丢掉，表现就是播放直接 403。
            header = map["header"]?.toString()?.takeIf { it.isNotBlank() },
            headers = headers,
            parse = map["parse"]?.toString(),
            jx = map["jx"]?.toString()?.takeIf { it.isNotBlank() },
            playUrl = map["playUrl"]?.toString()?.takeIf { it.isNotBlank() },
            format = map["format"]?.toString()?.takeIf { it.isNotBlank() },
        )
    }

    override fun dispose() {
        // 由 JavaBridgeManager 统一管理蜘蛛实例生命周期
    }

    // ==================== 结果映射 ====================

    private fun isError(map: Map<String, Any?>): Boolean = map["code"] == -1

    private fun videoItemOf(entry: Any?): VideoItem? {
        val m = entry as? Map<*, *> ?: return null
        val stringMap = m.entries.associate { it.key.toString() to it.value }
        val item = VideoItem.fromJson(stringMap, sourceKey = sourceRef())
        if (item.vodName.isEmpty()) return null
        return item
    }

    /**
     * TVBox 详情 → [VideoDetail]。
     *
     * **直接复用 [VideoDetail.fromJson]**，不再手写一份平行拆解：原来的实现用
     * `split("$\$")` 拆多线路 —— Kotlin 里 `"$\$"` 求值就是 `"$$"`（两个美元符），
     * 而 TVBox 的约定是 **`$$$`（三个）**，多线路必然解析错位。
     * [VideoDetail.fromJson] 按 `$$$` 拆线路、按 `#` 拆集，并用 `indexOf('$')`
     * 定位集名/地址分隔符（防地址自带 `$` 被截断），且已被 VideoModelsTest 覆盖。
     */
    private fun videoDetailOf(
        item: Map<*, *>,
        fallbackId: String,
    ): VideoDetail {
        val stringMap = item.entries.associate { it.key.toString() to it.value }
        val detail = VideoDetail.fromJson(stringMap, sourceKey = sourceRef())
        // fromJson 在 vod_id 缺失时给空串；这里回落到调用方传入的 id
        return if (detail.vodId.isBlank()) detail.copy(vodId = fallbackId) else detail
    }
}
