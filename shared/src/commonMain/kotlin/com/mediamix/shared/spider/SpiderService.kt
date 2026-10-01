package com.mediamix.shared.spider

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.*
import com.mediamix.shared.models.PlaySource
import com.mediamix.shared.models.VideoDetail
import com.mediamix.shared.network.HttpClientFactory
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*

/**
 * 蜘蛛服务 — 统一入口，封装 Registry + TVBox 配置获取
 *
 * 迁移自：lib/features/video/services/spider/spider_service.dart
 */
class SpiderService(
    private val registry: SpiderRegistry = SpiderRegistry.instance,
    private val httpClient: HttpClient =
        HttpClientFactory.createHttpClient(
            connectTimeoutSeconds = 5,
            requestTimeoutSeconds = 15,
        ),
) {
    private val logger = Logger.withTag("SpiderService")
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * 获取 TVBox 配置
     *
     * 支持以下响应格式：
     * 1. 标准 JSON（Content-Type: application/json）
     * 2. JPEG 图片伪装（饭太硬格式：FF D8...FF D9 [标识]**[Base64 JSON]）
     * 3. 纯 Base64 编码的 JSON
     */
    suspend fun fetchTvBoxConfig(configUrl: String): TvBoxConfig {
        try {
            // 以 bytes 方式请求，支持图片伪装格式
            val response = httpClient.get(configUrl)
            val bytes = response.readBytes()

            if (bytes.isEmpty()) {
                throw Exception("TVBox 配置响应为空")
            }

            logger.d {
                "TVBox配置响应: ${bytes.size} bytes, " +
                    "Content-Type: ${response.headers["content-type"]}"
            }

            // 尝试多种解码方式
            var jsonObj: JsonObject? = null

            // 方式1：JPEG/BMP 图片伪装格式
            if (TvBoxImageDecoder.isJpegDisguise(bytes) || TvBoxImageDecoder.isBmpDisguise(bytes)) {
                logger.d { "检测到图片伪装格式，尝试解码..." }
                jsonObj = TvBoxImageDecoder.decode(bytes)
            }

            // 方式2：直接作为文本解析
            if (jsonObj == null) {
                jsonObj = extractJsonFromBytes(bytes)
            }

            // 方式3：尝试通用图片解码（兜底）
            if (jsonObj == null) {
                jsonObj = TvBoxImageDecoder.decode(bytes)
            }

            if (jsonObj == null) {
                throw Exception("无法解析 TVBox 配置：未知格式")
            }

            val config = TvBoxConfigParser().parseFromJsonObject(jsonObj)
            logger.d {
                "TVBox配置解析完成: ${config.sites.size}个站点, spider=${config.spiderUrl}"
            }
            return config
        } catch (e: Exception) {
            logger.e { "获取TVBox配置失败: ${e.message}" }
            throw e
        }
    }

    /**
     * 从 TVBox 配置创建所有蜘蛛
     *
     * 如果配置中包含 Java 蜘蛛（csp_*），会尝试启动 Java Bridge。
     */
    suspend fun initFromConfig(
        config: TvBoxConfig,
        configKey: String = "",
    ): List<SpiderAdapter> {
        // 检查是否有 Java 蜘蛛需要桥接
        val hasJavaSpiders = config.sites.any { it.isJavaSpider }
        if (hasJavaSpiders) {
            ensureJavaBridge(config)
        }
        return registry.createFromSites(config.sites, configKey)
    }

    /**
     * 确保 Java Bridge 已启动并注入到 Registry
     */
    private suspend fun ensureJavaBridge(config: TvBoxConfig) {
        // 如果已经注入过且已初始化，跳过
        if (registry.javaBridgeManager != null && registry.javaBridgeManager!!.isInitialized) {
            return
        }

        val bridgeManager = JavaBridgeManager.instance

        // 如果 Bridge 未初始化，尝试加载 JAR
        if (!bridgeManager.isInitialized) {
            // 必须传**原文**（`url;md5;<hash>`）而不是 parseSpiderUrl 截断后的裸 URL：
            // 没有 md5 时 loadSpiderJar 既无法校验完整性，也无法按 md5 命中本地缓存，
            // 每次冷启都会重下 1.1MB 的蜘蛛包。
            val jarPath = config.spiderSpec ?: config.spiderUrl
            if (jarPath == null) {
                logger.w { "Java Bridge JAR 路径为空，csp_* 蜘蛛将不可用" }
                return
            }
            val loaded = bridgeManager.loadSpiderJar(jarPath)
            if (!loaded) {
                logger.w { "Java Bridge 加载失败，csp_* 蜘蛛将不可用" }
                return
            }
        }

        // 注入到 Registry
        registry.javaBridgeManager = bridgeManager
        logger.d { "Java Bridge 已注入 SpiderRegistry" }
    }

    // ==================== 蜘蛛操作 API ====================

    /** 获取蜘蛛实例 */
    fun getSpider(key: String): SpiderAdapter? = registry.get(key)

    /** 获取所有蜘蛛 */
    val allSpiders: List<SpiderAdapter> get() = registry.all

    /**
     * 释放某个 TVBox 配置源建出的全部蜘蛛实例。
     *
     * 换用备用接口线路后必须调用：蜘蛛实例内部持有站点 ext / 已初始化的 OkHttp 客户端，
     * 继续复用它们仍会打到旧域名。逐个释放而不是 `disposeAll()` ——
     * 后者会把其它源的实例一起清掉。
     */
    fun disposeSpidersFor(configKey: String) {
        allSpiders
            .filterIsInstance<JavaBridgeSpider>()
            .filter { it.configKeyForSource == configKey }
            .forEach { registry.remove(it.key) }
    }

    /**
     * 蜘蛛包（dex/jar）加载状态 —— 诊断页用。
     *
     * 排查「TVBox 源为什么不可用」时的关键观测点：
     * - 包未加载 → csp_* jar 蜘蛛全部建不出来
     * - 已加载但站点仍空 → 类名解析/壳解密/反射调用失败，看 logcat tag=JavaBridgeManager
     */
    val spiderBridgeStatus: String
        get() {
            val jm = registry.javaBridgeManager
            val jarLoaded = jm?.isInitialized == true
            return "蜘蛛包=${if (jarLoaded) "已加载" else "未加载"} · 已建蜘蛛 ${allSpiders.size} 个"
        }

    /** 通过蜘蛛获取首页内容 */
    suspend fun fetchHome(
        spider: SpiderAdapter,
        page: Int = 1,
    ): SpiderHomeResult = spider.homeContent(page)

    /** 通过蜘蛛获取分类内容 */
    suspend fun fetchCategory(
        spider: SpiderAdapter,
        tid: String,
        page: Int = 1,
    ): SpiderListResult = spider.categoryContent(tid, page)

    /** 通过蜘蛛获取详情 */
    suspend fun fetchDetail(
        spider: SpiderAdapter,
        id: String,
    ): SpiderDetailResult = spider.detailContent(id)

    /** 通过蜘蛛搜索 */
    suspend fun fetchSearch(
        spider: SpiderAdapter,
        keyword: String,
        page: Int = 1,
    ): SpiderListResult = spider.searchContent(keyword, page)

    /** 通过蜘蛛解析播放地址 */
    suspend fun fetchPlay(
        spider: SpiderAdapter,
        flag: String,
        id: String,
    ): SpiderPlayResult = spider.playerContent(flag, id)

    /**
     * 一键探测 —— 按真实链路跑一遍 TVBox 的四步，把每步结果写成可复制文本。
     *
     * **为什么要它**：`homeContent` 到底返回了什么至今没有定论，而这是判断
     * 「故障在壳/站点侧 还是 在映射层」的唯一依据。以前只能翻 logcat 逐条比对；
     * 现在在诊断页点一下就能拿到结构化结论。
     *
     * 链路：`homeContent → categoryContent → detailContent → playerContent`。
     * 前一步没数据就停下来，并把原因写清楚 —— 后面的步骤依赖前面的产出。
     */
    suspend fun probeTvBoxPipeline(configUrl: String): List<String> {
        val lines = mutableListOf<String>()

        fun add(line: String) = lines.add(line)

        add("== TVBox 一键探测 ==")
        add("配置: $configUrl")

        val config =
            try {
                fetchTvBoxConfig(configUrl)
            } catch (e: Exception) {
                add("配置拉取: 失败 —— ${e.message ?: "未知错误"}")
                return lines
            }
        add("配置拉取: 成功（${config.sites.size} 个站点, spider=${config.spiderSpec ?: config.spiderUrl ?: "无"}）")

        val spiders =
            try {
                initFromConfig(config, configKey = sourceKeyOf(configUrl))
            } catch (e: Exception) {
                add("建蜘蛛: 异常 —— ${e.message ?: "未知错误"}")
                return lines
            }
        add("建蜘蛛: ${spiders.size}/${config.sites.size} 成功")
        if (spiders.isEmpty()) {
            add("结论: 蜘蛛内核未就绪（jar 未加载 / 壳未解密 / 类名不匹配），链路无法继续")
            return lines
        }

        // 逐个站点探测，直到有一个站点能走通 —— 饭太硬有 40+ 站点，
        // 全跑一遍太慢，也不必要：只要有一个能出数据，就说明映射层是通的。
        for (spider in spiders.take(PROBE_MAX_SITES)) {
            val verdict = probeSingleSite(spider, ::add)
            if (verdict != null) {
                add("结论: $verdict")
                return lines
            }
        }

        add("结论: 前 $PROBE_MAX_SITES 个站点都没能走通（多为站点自身无数据或壳解密未完成）")
        return lines
    }

    /**
     * 探测单个站点的四步。
     *
     * 返回 null 表示「本站没走通、但可以换下一个站点继续」；
     * 返回非 null 是最终结论（已经能定性，无需再试别的站点）。
     */
    private suspend fun probeSingleSite(
        spider: SpiderAdapter,
        add: (String) -> Unit,
    ): String? {
        add("---- 站点 ${spider.key}（${spider.name}）----")

        val home =
            try {
                spider.homeContent(page = 1)
            } catch (e: Exception) {
                add("  homeContent: 异常 —— ${e.message ?: "未知错误"}")
                return null
            }
        add("  homeContent: class=${home.categories.size} list=${home.recommend.size}")

        probeCategory(spider, home.categories.firstOrNull()?.typeId, add)

        val first = home.recommend.firstOrNull()
        if (first == null) {
            add("  无影片条目，无法继续探测 detail/player")
            return null
        }
        add("  首条影片: ${first.vodId} / ${first.vodName}")

        val detail = fetchDetailForProbe(spider, first.vodId, add) ?: return null
        add(
            "  detailContent: ${detail.vodName} 线路=${detail.playSources.size} " +
                "集数=${detail.playSources.sumOf { it.episodes.size }}",
        )

        val line = detail.playSources.firstOrNull { it.episodes.isNotEmpty() } ?: return null
        return probePlayer(spider, line, add)
    }

    private suspend fun probeCategory(
        spider: SpiderAdapter,
        tid: String?,
        add: (String) -> Unit,
    ) {
        if (tid == null) {
            add("  无站内分类，跳过 categoryContent")
            return
        }
        val cat =
            try {
                spider.categoryContent(tid = tid, page = 1)
            } catch (e: Exception) {
                add("  categoryContent($tid): 异常 —— ${e.message ?: "未知错误"}")
                return
            }
        add("  categoryContent($tid): list=${cat.list.size} page=${cat.page}/${cat.pageCount}")
    }

    private suspend fun fetchDetailForProbe(
        spider: SpiderAdapter,
        vodId: String,
        add: (String) -> Unit,
    ): VideoDetail? =
        try {
            spider.detailContent(vodId).detail.also {
                if (it == null) add("  detailContent: 空 —— 站点没返回详情")
            }
        } catch (e: Exception) {
            add("  detailContent: 异常 —— ${e.message ?: "未知错误"}")
            null
        }

    private suspend fun probePlayer(
        spider: SpiderAdapter,
        line: PlaySource,
        add: (String) -> Unit,
    ): String? {
        val episode = line.episodes.first()
        val play =
            try {
                spider.playerContent(flag = line.name, id = episode.url)
            } catch (e: Exception) {
                add("  playerContent: 异常 —— ${e.message ?: "未知错误"}")
                return null
            }
        add(
            "  playerContent(${line.name}): url=${play.url.take(PROBE_URL_CHARS).ifEmpty { "空" }} " +
                "header=${play.headerMap().keys.joinToString(",").ifEmpty { "无" }} parse=${play.parse ?: "-"}",
        )
        return if (play.url.isNotBlank()) {
            "链路通畅 —— homeContent 有 list、detail 有剧集、playerContent 给出了播放地址"
        } else {
            "playerContent 未给出地址（站点侧限制或该线路需额外参数）"
        }
    }

    /**
     * 探测用的配置源 key。
     *
     * 这里只需要一个稳定标识（用于构造 `配置源::站点` 复合 sourceKey），
     * 直接取配置 URL 的 host 部分，避免为了探测再去反查源仓库。
     */
    private fun sourceKeyOf(configUrl: String): String = configUrl.substringAfter("//").substringBefore('/').ifEmpty { "tvbox" }

    // ==================== 生命周期 ====================

    /** 释放所有蜘蛛并关闭 Java Bridge */
    fun disposeAll() {
        registry.disposeAll()
        JavaBridgeManager.instance.release()
    }

    /** 关闭内部 HttpClient，释放连接池资源 */
    fun close() {
        httpClient.close()
    }

    // ==================== 内部工具 ====================

    /**
     * 从 bytes 提取 JSON（尝试 UTF-8 解码后直接解析）
     */
    internal fun extractJsonFromBytes(bytes: ByteArray): JsonObject? {
        try {
            val text = bytes.decodeToString().trim()
            if (text.startsWith("{") || text.startsWith("[")) {
                val element = json.parseToJsonElement(text)
                if (element is JsonObject) return element
            }
        } catch (_: Exception) {
        }
        return null
    }

    private companion object {
        /** 一键探测最多尝试的站点数：够判定「映射层是否通」即可，全跑太慢。 */
        const val PROBE_MAX_SITES = 5

        /** 探测报告里播放地址的截断长度 */
        const val PROBE_URL_CHARS = 80
    }
}
