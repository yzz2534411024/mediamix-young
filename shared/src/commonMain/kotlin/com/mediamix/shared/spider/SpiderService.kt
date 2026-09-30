package com.mediamix.shared.spider

import com.mediamix.shared.models.*
import com.mediamix.shared.network.HttpClientFactory
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.*

/**
 * 蜘蛛服务 — 统一入口，封装 Registry + TVBox 配置获取
 *
 * 迁移自：lib/features/video/services/spider/spider_service.dart
 */
class SpiderService(
    private val registry: SpiderRegistry = SpiderRegistry.instance,
    private val httpClient: HttpClient = HttpClientFactory.createHttpClient(
        connectTimeoutSeconds = 5,
        requestTimeoutSeconds = 15,
    ),
) {
    private val logger = Logger.withTag("SpiderService")
    private val json = Json {
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
    suspend fun initFromConfig(config: TvBoxConfig): List<SpiderAdapter> {
        // 检查是否有 Java 蜘蛛需要桥接
        val hasJavaSpiders = config.sites.any { it.isJavaSpider }
        if (hasJavaSpiders) {
            ensureJavaBridge(config)
        }
        return registry.createFromSites(config.sites)
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
            val jarPath = config.spiderUrl
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

    /** 通过蜘蛛获取首页内容 */
    suspend fun fetchHome(spider: SpiderAdapter, page: Int = 1): SpiderHomeResult {
        return spider.homeContent(page)
    }

    /** 通过蜘蛛获取分类内容 */
    suspend fun fetchCategory(
        spider: SpiderAdapter,
        tid: String,
        page: Int = 1,
    ): SpiderListResult {
        return spider.categoryContent(tid, page)
    }

    /** 通过蜘蛛获取详情 */
    suspend fun fetchDetail(spider: SpiderAdapter, id: String): SpiderDetailResult {
        return spider.detailContent(id)
    }

    /** 通过蜘蛛搜索 */
    suspend fun fetchSearch(
        spider: SpiderAdapter,
        keyword: String,
        page: Int = 1,
    ): SpiderListResult {
        return spider.searchContent(keyword, page)
    }

    /** 通过蜘蛛解析播放地址 */
    suspend fun fetchPlay(spider: SpiderAdapter, flag: String, id: String): SpiderPlayResult {
        return spider.playerContent(flag, id)
    }

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
        } catch (_: Exception) {}
        return null
    }
}