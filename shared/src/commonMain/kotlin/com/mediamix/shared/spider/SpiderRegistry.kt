package com.mediamix.shared.spider

import com.mediamix.shared.models.TvBoxSite
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.*

/**
 * 蜘蛛工厂函数类型
 *
 * 接收 [TvBoxSite] 站点配置，返回 [SpiderAdapter] 实例。
 */
typealias SpiderFactory = (TvBoxSite) -> SpiderAdapter

/**
 * 蜘蛛注册表
 *
 * 负责根据 [TvBoxSite] 创建、缓存和管理 [SpiderAdapter] 实例。
 * 使用单例模式，支持内置类型自动映射和自定义工厂注册。
 *
 * 迁移自：lib/features/video/services/spider/spider_registry.dart
 */
class SpiderRegistry private constructor() {

    private val logger = Logger.withTag("SpiderRegistry")
    private val factories = mutableMapOf<String, SpiderFactory>()
    private val instances = mutableMapOf<String, SpiderAdapter>()

    /** 全局共享的 JavaBridgeManager 引用（由 SpiderService 初始化时注入） */
    var javaBridgeManager: JavaBridgeManager? = null

    /**
     * 注册自定义蜘蛛工厂
     *
     * [key] 为站点 key，当 [createFromSite] 遇到该 key 的站点时会优先使用此工厂。
     */
    fun register(key: String, factory: SpiderFactory) {
        factories[key] = factory
    }

    /**
     * 根据站点配置创建或返回缓存的蜘蛛实例
     *
     * 查找顺序：缓存 -> 已注册工厂 -> 内置类型映射。
     * 创建成功后会调用 [SpiderAdapter.init] 并传入 [parseExt] 解析后的配置。
     */
    suspend fun createFromSite(site: TvBoxSite): SpiderAdapter? {
        instances[site.key]?.let { return it }

        val spider = buildSpider(site) ?: return null
        spider.init(parseExt(site.ext))
        instances[site.key] = spider
        return spider
    }

    /** 批量根据站点配置创建蜘蛛实例 */
    suspend fun createFromSites(sites: List<TvBoxSite>): List<SpiderAdapter> {
        return sites.mapNotNull { createFromSite(it) }
    }

    /** 获取指定 key 的蜘蛛实例 */
    fun get(key: String): SpiderAdapter? = instances[key]

    /** 获取所有已缓存的蜘蛛实例 */
    val all: List<SpiderAdapter> get() = instances.values.toList()

    /** 移除并释放指定 key 的蜘蛛实例 */
    fun remove(key: String) {
        instances.remove(key)?.dispose()
    }

    /** 释放所有蜘蛛实例并清空缓存 */
    fun disposeAll() {
        instances.values.forEach { it.dispose() }
        instances.clear()
    }

    /**
     * 构建蜘蛛实例（不缓存、不初始化）
     *
     * 查找顺序：自定义工厂 -> Java 蜘蛛 -> 内置类型映射
     */
    internal fun buildSpider(site: TvBoxSite): SpiderAdapter? {
        // 1. 检查自定义工厂
        factories[site.key]?.let { return it(site) }

        // 2. Java 蜘蛛桥接：csp_* 格式
        if (site.isJavaSpider && javaBridgeManager != null) {
            return JavaBridgeSpider(site = site)
        }

        // 3. 内置类型映射
        return when (site.type) {
            0 -> CmsSpider(site = site)
            1 -> JsonSpider()
            3 -> if (!site.isJavaSpider) XpathSpider(site = site) else null
            else -> null
        }
    }

    /**
     * 解析站点的 ext 字段为初始化配置映射
     *
     * - 以 `http://` 或 `https://` 开头的值返回 `{"extUrl": ext}`
     * - 可解析为 JSON 对象的字符串返回该对象的 Map 形式
     * - 其他值返回 `{"ext": ext}`
     * - [ext] 为 null 时返回空映射
     */
    internal fun parseExt(ext: String?): Map<String, Any> {
        if (ext == null) return emptyMap()

        if (ext.startsWith("http://") || ext.startsWith("https://")) {
            return mapOf("extUrl" to ext)
        }

        return try {
            val element = Json.parseToJsonElement(ext)
            if (element is JsonObject) {
                jsonObjectToMap(element)
            } else {
                mapOf("ext" to ext)
            }
        } catch (_: Exception) {
            mapOf("ext" to ext)
        }
    }

    companion object {
        /** 全局单例 */
        val instance = SpiderRegistry()

        /** 将 [JsonObject] 递归转换为 [Map]<String, Any> */
        internal fun jsonObjectToMap(obj: JsonObject): Map<String, Any> {
            return obj.entries.associate { (k, v) -> k to jsonElementToAny(v) }
        }

        /** 将 [JsonElement] 转换为对应的 Kotlin 值 */
        internal fun jsonElementToAny(element: JsonElement): Any {
            return when (element) {
                is JsonPrimitive -> {
                    if (element.isString) element.content
                    else element.content // 数字/布尔也作为字符串保留
                }
                is JsonObject -> jsonObjectToMap(element)
                is JsonArray -> element.map { jsonElementToAny(it) }
            }
        }
    }
}
