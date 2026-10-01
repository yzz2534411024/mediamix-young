package com.mediamix.shared.spider

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.SiteKind
import com.mediamix.shared.models.TvBoxSite
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
    fun register(
        key: String,
        factory: SpiderFactory,
    ) {
        factories[key] = factory
    }

    /**
     * 根据站点配置创建或返回缓存的蜘蛛实例。
     *
     * 查找顺序：缓存 -> 已注册工厂 -> 内置类型映射。
     * 创建成功后会调用 [SpiderAdapter.init] 并传入 [parseExt] 解析后的配置。
     *
     * ⚠️ **缓存键必须是「配置源 + 站点」复合键**，不能只用站点 key。
     * 实例里固定持有构造时的 [configKey]（用于产出 `配置源::站点` 复合标识），
     * 只按站点 key 缓存会让**构造参数被静默忽略**：
     * 只要有一条路径先用空 configKey 建过实例（例如「数据源管理 → 检查」），
     * 之后首页/详情拿到的就是它 —— 产出的 sourceKey 是裸 key，
     * 详情页随即反查失败，表现又是「点进详情报找不到数据源」。
     */
    suspend fun createFromSite(
        site: TvBoxSite,
        configKey: String = "",
    ): SpiderAdapter? {
        val cacheKey = instanceKey(configKey, site.key)
        instances[cacheKey]?.let { return it }

        // 同一站点若已按**裸 key** 缓存过（旧路径或调用方未传 configKey），
        // 且现在拿到了真正的配置源 key —— 换成带源标识的实例，否则标识会一直是错的。
        if (configKey.isNotBlank()) {
            instances.remove(site.key)?.dispose()
        }

        val spider = buildSpider(site, configKey) ?: return null
        spider.init(parseExt(site.ext))
        instances[cacheKey] = spider
        return spider
    }

    /**
     * 批量根据站点配置创建蜘蛛实例。
     *
     * [configKey] 是所属 TVBox 配置源的 key，会透传给 jar 蜘蛛用于构造 [SourceRef]。
     */
    suspend fun createFromSites(
        sites: List<TvBoxSite>,
        configKey: String = "",
    ): List<SpiderAdapter> = sites.mapNotNull { createFromSite(it, configKey) }

    /**
     * 获取指定 key 的蜘蛛实例。
     *
     * 同时接受 [configKey] 复合键与**裸站点 key**（调用方常常只有后者），
     * 避免「明明建出来了却取不到」。
     */
    fun get(key: String): SpiderAdapter? =
        instances[key]
            ?: instances.entries.firstOrNull { (instanceKey, _) -> instanceKey.substringAfterLast(SEPARATOR) == key }?.value

    /** 获取所有已缓存的蜘蛛实例 */
    val all: List<SpiderAdapter> get() = instances.values.toList()

    /** 移除并释放指定 key 的蜘蛛实例（同样接受复合键与裸站点 key）。 */
    fun remove(key: String) {
        val entry =
            instances.entries.firstOrNull { (instanceKey, _) ->
                instanceKey == key ||
                    instanceKey.substringAfterLast(SEPARATOR) == key
            }
        if (entry != null) {
            instances.remove(entry.key)?.dispose()
        }
    }

    /** 释放所有蜘蛛实例并清空缓存 */
    fun disposeAll() {
        instances.values.forEach { it.dispose() }
        instances.clear()
    }

    /**
     * 构建蜘蛛实例（不缓存、不初始化）
     *
     * 查找顺序：自定义工厂 -> 按站点类型分发。
     *
     * ⚠️ 分发依据是 [SiteKind] 而不是 type 数字：TVBox 配置里 XPath 蜘蛛
     * 通常写作 `type:3, api:"csp_XPath"` —— 若按 csp_ 前缀判定为 jar 蜘蛛，
     * 会在没有 JavaBridge 时直接返回 null，把能跑的站点全杀掉。
     */
    internal fun buildSpider(
        site: TvBoxSite,
        configKey: String = "",
    ): SpiderAdapter? {
        // 1. 检查自定义工厂
        factories[site.key]?.let { return it(site) }

        // 2. 按解析方式分发
        return when (site.kind) {
            SiteKind.JAR ->
                // 真 jar 蜘蛛：需要 TVBox 内核（JavaBridge）；内核未就绪则建不出来
                if (javaBridgeManager != null) JavaBridgeSpider(site = site, configKey = configKey) else null

            SiteKind.XPATH -> {
                // XPath 蜘蛛的解析规则全在 ext 里；ext 为空就没法工作
                // （实测饭太硬的 csp_XPathGuard 防诈提示站无 ext，曾把 api 当 URL
                // 请求到 localhost:80），直接剔除。
                if (site.ext.isNullOrBlank()) {
                    logger.w { "XPath 站点缺 ext 规则，跳过: ${site.key} (${site.name})" }
                    null
                } else {
                    XpathSpider(site = site)
                }
            }

            SiteKind.CMS ->
                when (site.type) {
                    0 -> CmsSpider(site = site)
                    1 -> JsonSpider()
                    else -> null
                }
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

        /** 实例缓存键分隔符（与 [com.mediamix.shared.models.SourceRef] 保持一致）。 */
        private const val SEPARATOR = "::"

        /**
         * 实例缓存键：有配置源时用 `配置源::站点`，否则退回裸站点 key。
         *
         * 用复合键是为了让「同一站点被不同配置源引用」不至于互相串味，
         * 更关键的是**不能忽略构造参数**（见 [createFromSite] 的说明）。
         */
        private fun instanceKey(
            configKey: String,
            siteKey: String,
        ): String = if (configKey.isBlank()) siteKey else "$configKey$SEPARATOR$siteKey"

        /** 将 [JsonObject] 递归转换为 [Map]<String, Any> */
        internal fun jsonObjectToMap(obj: JsonObject): Map<String, Any> = obj.entries.associate { (k, v) -> k to jsonElementToAny(v) }

        /** 将 [JsonElement] 转换为对应的 Kotlin 值 */
        internal fun jsonElementToAny(element: JsonElement): Any =
            when (element) {
                is JsonPrimitive -> {
                    if (element.isString) {
                        element.content
                    } else {
                        element.content // 数字/布尔也作为字符串保留
                    }
                }
                is JsonObject -> jsonObjectToMap(element)
                is JsonArray -> element.map { jsonElementToAny(it) }
            }
    }
}
