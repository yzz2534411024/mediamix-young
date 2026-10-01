package com.mediamix.shared.spider

import com.mediamix.shared.models.PlaySource
import com.mediamix.shared.models.SpiderDetailResult
import com.mediamix.shared.models.SpiderHomeResult
import com.mediamix.shared.models.SpiderListResult
import com.mediamix.shared.models.SpiderPlayResult
import com.mediamix.shared.models.SpiderType
import com.mediamix.shared.models.TvBoxSite
import com.mediamix.shared.models.VideoDetail
import com.mediamix.shared.models.VideoEpisode
import com.mediamix.shared.models.VideoItem

/**
 * Java Bridge 蜘蛛适配器 —— 通过 [JavaBridgeManager.invokeMethod] 反射调用
 * TVBox 蜘蛛包（dex）里的 csp_* 类。
 *
 * 结果映射遵循 TVBox/CatVod 的 JSON 约定：
 * - home  → `{"class":[{"type_id","type_name"}], "list":[vod...]}`
 * - 列表  → `{"list":[vod...], "page","pagecount","total"}`
 * - 详情  → `{"list":[{...,"vod_play_from":"A#B","vod_play_url":"ep$url#ep$url$$$..."}]}`
 * - 播放  → `{"parse":"0","jx":"0","url":"...","headers":{...}}`
 */
class JavaBridgeSpider(
    private val site: TvBoxSite,
    private val bridgeManager: JavaBridgeManager = JavaBridgeManager.instance,
) : SpiderAdapter {
    override val key: String get() = site.key
    override val name: String get() = site.name
    override val type: SpiderType get() = SpiderType.JAVA_BRIDGE

    override val isSearchSupported: Boolean
        get() = site.searchable

    /** 站点级 ext（部分蜘蛛用它拿规则/配置地址），透传给每次 invoke。 */
    private var siteExt: String? = null

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
            args = buildMap {
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

        return SpiderPlayResult(
            url = map["url"]?.toString().orEmpty().ifEmpty { id },
            parse = map["parse"]?.toString(),
            playUrl = map["playUrl"]?.toString(),
            headers = headers,
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
        val item = VideoItem.fromJson(stringMap, sourceKey = site.key)
        if (item.vodName.isEmpty()) return null
        return item
    }

    private fun videoDetailOf(
        item: Map<*, *>,
        fallbackId: String,
    ): VideoDetail {
        val stringMap = item.entries.associate { it.key.toString() to it.value }
        // TVBox 约定：多播放源以 $$$ 分隔，集与集之间 #，集名与地址之间 $
        val playFrom = (stringMap["vod_play_from"] ?: "").toString().split("$\$")
        val playUrl = (stringMap["vod_play_url"] ?: "").toString().split("$\$")

        val sources =
            playFrom.zip(playUrl).mapNotNull { (from, urls) ->
                val episodes =
                    urls
                        .split('#')
                        .mapNotNull { ep ->
                            val sep = ep.indexOf('$')
                            if (sep <= 0) return@mapNotNull null
                            VideoEpisode(
                                name = ep.substring(0, sep).trim(),
                                url = ep.substring(sep + 1).trim(),
                            )
                        }
                if (episodes.isEmpty()) null else PlaySource(name = from.trim(), episodes = episodes)
            }

        return VideoDetail(
            vodId = (stringMap["vod_id"] ?: fallbackId).toString(),
            vodName = (stringMap["vod_name"] ?: "未知").toString(),
            vodPic = stringMap["vod_pic"]?.toString(),
            vodContent = stringMap["vod_content"]?.toString(),
            vodActor = stringMap["vod_actor"]?.toString(),
            vodDirector = stringMap["vod_director"]?.toString(),
            vodYear = stringMap["vod_year"]?.toString(),
            vodArea = stringMap["vod_area"]?.toString(),
            vodRemarks = stringMap["vod_remarks"]?.toString(),
            typeName = stringMap["type_name"]?.toString(),
            typeId = (stringMap["type_id"] as? Number)?.toInt(),
            sourceKey = site.key,
            playSources = sources,
        )
    }
}
