package com.mediamix.shared.services

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.SourceRef
import com.mediamix.shared.models.SourceUnavailableException
import com.mediamix.shared.models.SpiderCategory
import com.mediamix.shared.models.SpiderListResult
import com.mediamix.shared.models.TvBoxConfig
import com.mediamix.shared.models.VideoCategory
import com.mediamix.shared.models.VideoDetail
import com.mediamix.shared.models.VideoParser
import com.mediamix.shared.spider.SpiderAdapter
import com.mediamix.shared.spider.SpiderHealthStore
import com.mediamix.shared.spider.SpiderService
import com.mediamix.shared.spider.VideoApiService
import kotlinx.coroutines.withTimeout

// ============================================================
// 目录（首页分类）结构
// ============================================================

/**
 * 首页目录。
 *
 * CMS 源是**一层**分类（接口直接给 class 列表）；TVBox 源是**两层**
 * —— 先选配置里的站点，再选该站点内部的 class。
 * 用 sealed 表达后，UI 只需一次 `when`，而不是在 ViewModel 里堆 `if (site.isTvBox)`。
 */
sealed interface HomeCatalog {
    /** CMS：一层分类。 */
    data class Flat(
        val categories: List<VideoCategory>,
    ) : HomeCatalog

    /** TVBox：站点列表；每站的站内分类按需再取（见 [SourceContentGateway.loadSiteClasses]）。 */
    data class Tree(
        val sites: List<SiteNode>,
    ) : HomeCatalog
}

/** TVBox 配置里的一个站点。 */
data class SiteNode(
    val key: String,
    val name: String,
)

/** 解析后可直接交给播放器的地址。 */
data class ResolvedPlay(
    val url: String,
    /** 防盗链等必须携带的请求头；TVBox 蜘蛛会在此给出真实值。 */
    val headers: Map<String, String> = emptyMap(),
    /** 是否经过了解析器（用于诊断展示）。 */
    val parsed: Boolean = false,
)

/**
 * 数据源内容网关 —— **唯一**分流「CMS 协议」与「TVBox 蜘蛛」的地方。
 *
 * 背景：`if (site.isTvBox)` 此前散落在首页 / 详情 / 源管理三处 ViewModel 里，
 * 详情页甚至在 TVBox 分支里直接抛异常，导致饭太硬即便 jar 桥正常也走不到播放。
 * 把这些分支上收到网关后：
 * - ViewModel 只面对与协议无关的语义（目录 / 列表 / 详情 / 解析播放地址）；
 * - TVBox 逻辑得以在 Desktop 上被测试（Desktop 跑不了 dex，但 CMS 分支与
 *   纯映射函数都能跑）。
 */
class SourceContentGateway(
    private val videoApiService: VideoApiService,
    private val spiderService: SpiderService,
    /**
     * 站点健康度。可由 UI 层注入 Settings 支持的实现（跨启动生效），
     * 不注入时退化为进程内记录 —— 功能可用，只是重启后清零。
     */
    val healthStore: SpiderHealthStore = SpiderHealthStore(),
) {
    private val logger = Logger.withTag("SourceContentGateway")

    /** TVBox 配置缓存（按配置地址），避免每次点分类都重走网络。 */
    private var cachedConfigUrl: String? = null
    private var cachedConfig: TvBoxConfig? = null

    /** 站内分类缓存：`站点key -> class 列表`。 */
    private val siteClassesCache = mutableMapOf<String, List<SpiderCategory>>()

    /**
     * 探测成功后的接口地址写回回调：`(源key, 可用地址)`。
     *
     * 由 UI 层注入（写回 [com.mediamix.ui.source.SourceRepository]）—— shared 层
     * 不反向依赖 UI 的仓库，但在同一进程里共享同一份源配置。
     */
    var onEndpointResolved: ((String, String) -> Unit)? = null

    /** 切源/换配置时调用，清掉一切与具体 TVBox 配置绑定的缓存。 */
    fun invalidateTvBoxCache() {
        cachedConfigUrl = null
        cachedConfig = null
        siteClassesCache.clear()
    }

    // ==================== 目录 ====================

    /**
     * 取首页目录。
     *
     * TVBox 分支**只做一次配置拉取 + 建蜘蛛**，不去逐个站点拉 homeContent
     * —— 43 个站点各一次网络请求会让首屏等十几秒到一分钟。站内分类等用户
     * 真正点进某个站点时再取（[loadSiteClasses]）。
     */
    suspend fun loadCatalog(site: CmsApiSite): HomeCatalog {
        if (!site.isTvBox) {
            return HomeCatalog.Flat(videoApiService.fetchCategories(site.apiUrl))
        }

        val config = loadConfig(site)
        val spiders = spiderService.initFromConfig(config, configKey = site.key)
        val spiderKeys = spiders.map { it.key }.toSet()
        val all =
            config.sites
                .filter { it.key in spiderKeys }
                .map { SiteNode(key = it.key, name = it.name) }

        // 连续失败多次的站点默认不再出现在首屏 —— 否则每次打开都要在
        // 它们身上白等一轮。健康数据可在诊断页查看，也可手动重试清除。
        val (unhealthy, healthy) = all.partition { healthStore.isUnhealthy(it.key) }
        if (unhealthy.isNotEmpty()) {
            logger.i { "跳过 ${unhealthy.size} 个连续失败的站点: ${unhealthy.take(5).map { it.name }}" }
        }

        if (healthy.isEmpty()) {
            throw SourceUnavailableException(
                if (all.isEmpty()) {
                    "「${site.name}」的 ${config.sites.size} 个站点都没能建立解析器，" +
                        "可能是蜘蛛内核不可用或蜘蛛包下载失败。可在「诊断」页查看蜘蛛桥状态。"
                } else {
                    "「${site.name}」的 ${all.size} 个站点近期连续失败，已暂时隐藏。可在诊断页清除失败记录后重试。"
                },
            )
        }
        logger.i { "TVBox 目录就绪: ${healthy.size}/${all.size} 个站点" }
        return HomeCatalog.Tree(healthy)
    }

    /**
     * 取某个 TVBox 站点内部的 class 列表。
     *
     * 站内 class 是 `homeContent` 返回的 `class` 数组（如「电影 / 电视剧 / 综艺」）。
     * 结果按站点缓存 —— 用户来回切换站点时不至于反复打网络。
     */
    suspend fun loadSiteClasses(
        site: CmsApiSite,
        siteKey: String,
    ): List<SpiderCategory> {
        siteClassesCache[siteKey]?.let { return it }

        val spider = requireSpider(site, siteKey)
        val classes =
            try {
                val result = spider.homeContent(page = 1)
                healthStore.recordSuccess(siteKey)
                result.categories
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Error) {
                // dex 蜘蛛桥加载失败抛的是 Error（NoClassDefFoundError 等），
                // 只 catch Exception 会让 Error 逃逸成闪退 —— 降级为空分类
                healthStore.recordFailure(siteKey)
                emptyList()
            } catch (e: Exception) {
                healthStore.recordFailure(siteKey)
                throw e
            }
        siteClassesCache[siteKey] = classes
        logger.i { "站点 $siteKey 站内分类 ${classes.size} 个" }
        return classes
    }

    // ==================== 列表 ====================

    /**
     * 取列表。
     *
     * - CMS：走 `ac=detail&pg=` 接口（自带 5 分钟缓存与 DNS 预解析）。
     * - TVBox：[tid] 为 null 时取本站首页推荐；否则取该分类的分页列表。
     *
     * @param siteKey TVBox 站点 key；CMS 源可传空串
     * @param tid 站内分类 id（String，可能形如 `dianying`）；null 表示该站点首页
     */
    suspend fun loadList(
        site: CmsApiSite,
        siteKey: String,
        tid: String?,
        page: Int,
    ): SpiderListResult {
        if (!site.isTvBox) {
            val typeId = tid?.toIntOrNull()
            val response = videoApiService.fetchVideoList(apiUrl = site.apiUrl, page = page, typeId = typeId)
            return SpiderListResult(
                list = response.list,
                page = response.page,
                pageCount = response.pageCount,
                total = response.total,
            )
        }

        val spider = requireSpider(site, siteKey)
        return if (tid.isNullOrBlank()) {
            // 站点没有细分分类时，首页推荐就是它的内容入口
            SpiderListResult(list = spider.homeContent(page = page).recommend, page = page)
        } else {
            spider.categoryContent(tid = tid, page = page)
        }
    }

    // ==================== 详情 ====================

    /**
     * 取影片详情。
     *
     * [sourceKey] 是 [SourceRef] 形式：TVBox 影片形如 `fantaiying::douDou`，
     * 据此找回对应的蜘蛛；CMS 源则是裸 key。
     */
    suspend fun loadDetail(
        site: CmsApiSite,
        vodId: String,
        sourceKey: String,
    ): VideoDetail {
        if (!site.isTvBox) {
            return videoApiService.fetchVideoDetail(apiUrl = site.apiUrl, vodId = vodId, sourceKey = sourceKey)
        }

        val spider = requireSpider(site, SourceRef.siteKey(sourceKey))
        val detail =
            spider.detailContent(vodId).detail
                ?: throw SourceUnavailableException("「${site.name}」没有返回这部影片的详情。")
        if (!detail.hasPlayableSource) {
            throw SourceUnavailableException("「${site.name}」这部影片没有可用的播放地址。")
        }
        return detail
    }

    // ==================== 播放地址解析 ====================

    /**
     * 把「详情页给出的剧集标识」解析成可直接播放的地址与请求头。
     *
     * CMS 源的剧集 url 本身就是播放地址，原样返回；
     * **TVBox 源不是** —— 它的 `vod_play_url` 里存的是待解析的 id，
     * 必须经 `playerContent(flag, id)` 才能拿到真实地址和防盗链头。
     * 少了这一步，TVBox 影片点播放必然 403 或直接失败。
     *
     * @param flag TVBox 的线路名（即 [com.mediamix.shared.models.PlaySource.name]）
     */
    suspend fun resolvePlay(
        site: CmsApiSite,
        flag: String,
        episodeId: String,
        sourceKey: String,
    ): ResolvedPlay {
        if (!site.isTvBox) {
            return ResolvedPlay(url = episodeId)
        }

        val spider = requireSpider(site, SourceRef.siteKey(sourceKey))
        val result = spider.playerContent(flag = flag, id = episodeId)
        if (result.url.isBlank()) {
            throw SourceUnavailableException("「${site.name}」没能解析出播放地址，换一条线路试试。")
        }

        val headers = result.headerMap()
        val url = applyParserIfNeeded(result.url, result.jx, result.needsParse)
        return ResolvedPlay(url = url, headers = headers, parsed = url != result.url)
    }

    /**
     * `parse=1` 表示地址需要经解析接口二次处理。
     *
     * 优先用蜘蛛给出的 [jx]（对应 [VideoParser.key]）匹配本地解析器；
     * 匹配不上就原样返回 —— 少数站点会同时给出可用直链，硬套解析器反而更糟。
     */
    private fun applyParserIfNeeded(
        url: String,
        jx: String?,
        needsParse: Boolean,
    ): String {
        if (!needsParse) return url
        val parser =
            VideoParser.defaultParsers.firstOrNull { it.key == jx }
                ?: VideoParser.defaultParsers.firstOrNull()
                ?: return url
        return parser.buildUrl(url)
    }

    // ==================== 内部 ====================

    private suspend fun requireSpider(
        site: CmsApiSite,
        siteKey: String,
    ): SpiderAdapter {
        if (siteKey.isBlank()) {
            throw SourceUnavailableException("「${site.name}」的影片缺少站点信息，请回首页重新进入该数据源。")
        }
        spiderService.getSpider(siteKey)?.let { return it }

        // 首页没进过 / 进程被回收时会走到这里：按配置补建一次
        val config = loadConfig(site)
        spiderService.initFromConfig(config, configKey = site.key)
        return spiderService.getSpider(siteKey)
            ?: throw SourceUnavailableException(
                "「${site.name}」的站点「$siteKey」解析器不可用，可能是蜘蛛内核未就绪。",
            )
    }

    /**
     * 取 TVBox 配置，按 [CmsApiSite.allApiUrls] 顺序试线路。
     *
     * 采集站的域名经常整体失效（饭太硬实测只剩 `.net`），因此这里做**多线路兜底**：
     * 第一条成功即写回缓存，并把可用地址回报给上层持久化，避免下次启动又去撞死域名。
     */
    private suspend fun loadConfig(site: CmsApiSite): TvBoxConfig {
        // 已经成功过的地址优先（同一进程内换线路后不必再重试死域名）
        cachedConfig?.takeIf { cachedConfigUrl != null && site.allApiUrls.contains(cachedConfigUrl) }?.let { return it }

        for (url in site.allApiUrls) {
            val config = tryFetchConfig(site, url) ?: continue
            rememberWorkingEndpoint(site, url)
            cachedConfigUrl = url
            cachedConfig = config
            return config
        }
        throw SourceUnavailableException(
            "「${site.name}」的 ${site.allApiUrls.size} 条接口线路都不可用：" +
                "${lastEndpointError?.message ?: "未知错误"}",
        )
    }

    /** 单条线路取配置；失败返回 null 并记下原因（供最终错误信息使用）。 */
    private suspend fun tryFetchConfig(
        site: CmsApiSite,
        url: String,
    ): TvBoxConfig? =
        try {
            withTimeout(ENDPOINT_TIMEOUT_MS) { spiderService.fetchTvBoxConfig(url) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 含 Error：release 混淆下 dex 桥失败抛 NoClassDefFoundError
            logger.w { "接口地址不可用: $url（${e.message ?: e.javaClass.simpleName}）" }
            lastEndpointError = e
            null
        }

    /** 记住生效的线路地址（必要时失效旧线路建出的蜘蛛）。 */
    private fun rememberWorkingEndpoint(
        site: CmsApiSite,
        url: String,
    ) {
        if (cachedConfigUrl == url) return
        siteClassesCache.clear()
        if (site.apiUrl == url) return
        // 换到备用线路后必须失效**已建蜘蛛**：蜘蛛是按 configKey 缓存的，
        // 旧线路的实例仍指向旧域名。
        spiderService.disposeSpidersFor(site.key)
        onEndpointResolved?.invoke(site.key, url)
        logger.i { "TVBox 换用备用线路: $url" }
    }

    /** 最近一次线路失败的原因（用于拼装最终的用户可读错误）。 */
    private var lastEndpointError: Throwable? = null

    private companion object {
        /** 单条线路的探测超时：8s 内没响应即认为该域名不可用，换下一条。 */
        const val ENDPOINT_TIMEOUT_MS = 15_000L
    }
}
