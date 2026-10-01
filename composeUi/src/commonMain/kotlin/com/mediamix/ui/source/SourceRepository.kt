package com.mediamix.ui.source

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.CmsApiSite
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 数据源仓库 —— 首页与「数据源管理」页共享的**唯一**数据源。
 *
 * 原来的实现里首页和源管理页各自持有一份 `CmsApiSite.defaultSites` 的副本：
 * 在管理页里停用某个源，回首页它还在；在首页切了源，重启又回到第一个。
 * 这里把状态收拢到一处并落盘（multiplatform-settings），两个页面都从这里读写。
 */
class SourceRepository(
    private val settings: Settings,
) {
    private val logger = Logger.withTag("SourceRepository")
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val _sites = MutableStateFlow(loadSites())
    val sites: StateFlow<List<CmsApiSite>> = _sites.asStateFlow()

    /** 当前选中的源 key；未保存过时为 null，由调用方决定默认值 */
    var currentSiteKey: String?
        get() = settings.getStringOrNull(KEY_CURRENT_SITE)?.takeIf { it.isNotBlank() }
        set(value) = settings.putString(KEY_CURRENT_SITE, value.orEmpty())

    /** 启用中的源。饭太硬这类 TVBox 源即使启用，也需要上层单独判断能否解析。 */
    val enabledSites: List<CmsApiSite> get() = _sites.value.filter { it.enabled }

    /**
     * 首次进入时的默认源：优先用户上次选的源，其次是第一个可用的 CMS 源。
     * 不会返回 TVBox 源 —— 那类源在当前架构下解析不了，当默认值只会让用户看到空列表。
     */
    fun resolveDefaultSite(): CmsApiSite? {
        val enabled = enabledSites
        if (enabled.isEmpty()) return null

        // 1) 用户上次显式选过的源最优先
        currentSiteKey?.let { key -> enabled.find { it.key == key }?.let { return it } }

        // 2) 有实测延迟数据时，挑最快的可用 CMS 源。
        //    网络环境会变，静态顺序只能覆盖「大多数情况」，实测数据能自适应。
        val latencies = readLatencies()
        enabled
            .filter { !it.isTvBox && latencies.containsKey(it.key) }
            .minByOrNull { latencies.getValue(it.key) }
            ?.let { return it }

        // 3) 兜底：第一个非 TVBox 源（defaultSites 本身已按实测速度排序）
        return enabled.firstOrNull { !it.isTvBox } ?: enabled.first()
    }

    /** 记录一次实测延迟（毫秒），由「数据源管理」页测速后调用。 */
    fun recordLatency(
        key: String,
        latencyMs: Long,
    ) {
        if (latencyMs <= 0) return
        val updated = readLatencies().toMutableMap()
        updated[key] = latencyMs
        settings.putString(
            KEY_SOURCE_LATENCY,
            updated.entries.joinToString("|") { "${'$'}{it.key}:${'$'}{it.value}" },
        )
    }

    fun latencyOf(key: String): Long? = readLatencies()[key]

    fun clearLatencies() = settings.remove(KEY_SOURCE_LATENCY)

    /** 解析 `key:ms|key:ms` 形式的延迟记录，坏数据直接跳过。 */
    private fun readLatencies(): Map<String, Long> =
        settings
            .getStringOrNull(KEY_SOURCE_LATENCY)
            .orEmpty()
            .split('|')
            .mapNotNull { part ->
                val i = part.indexOf(':')
                if (i <= 0) return@mapNotNull null
                val ms = part.substring(i + 1).toLongOrNull() ?: return@mapNotNull null
                part.substring(0, i) to ms
            }.toMap()

    fun findByKey(key: String): CmsApiSite? = _sites.value.find { it.key == key } ?: CmsApiSite.findByKey(key)

    fun setEnabled(
        key: String,
        enabled: Boolean,
    ) {
        val site = _sites.value.find { it.key == key } ?: return
        if (site.enabled == enabled) return
        _sites.value = _sites.value.map { if (it.key == key) it.copy(enabled = enabled) else it }
        persistDisabledKeys()
    }

    fun toggleEnabled(key: String) {
        val site = _sites.value.find { it.key == key } ?: return
        setEnabled(key, !site.enabled)
    }

    fun add(site: CmsApiSite) {
        if (_sites.value.any { it.key == site.key }) return
        _sites.value = _sites.value + site
        persistCustomSites()
    }

    fun remove(key: String) {
        val site = _sites.value.find { it.key == key } ?: return
        if (site.isBuiltIn) return
        _sites.value = _sites.value.filter { it.key != key }
        persistCustomSites()
    }

    // ==================== 持久化 ====================

    private fun loadSites(): List<CmsApiSite> {
        val disabled =
            settings
                .getStringOrNull(KEY_DISABLED_SITES)
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

        val custom =
            try {
                settings
                    .getStringOrNull(KEY_CUSTOM_SITES)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { json.decodeFromString<List<CmsApiSite>>(it) }
                    ?: emptyList()
            } catch (e: Exception) {
                logger.w { "Failed to restore custom sites: ${e.message}" }
                emptyList()
            }

        val builtIn =
            CmsApiSite.defaultSites.map { site ->
                if (site.key in disabled) site.copy(enabled = false) else site
            }
        return builtIn + custom.filter { c -> builtIn.none { it.key == c.key } }
    }

    private fun persistDisabledKeys() {
        val disabled = _sites.value.filter { !it.enabled && it.isBuiltIn }.joinToString(",") { it.key }
        settings.putString(KEY_DISABLED_SITES, disabled)
    }

    private fun persistCustomSites() {
        val custom = _sites.value.filter { !it.isBuiltIn }
        settings.putString(KEY_CUSTOM_SITES, json.encodeToString(custom))
    }

    private companion object {
        const val KEY_DISABLED_SITES = "source_disabled_keys"
        const val KEY_CUSTOM_SITES = "source_custom_sites"
        const val KEY_CURRENT_SITE = "source_current_key"
        const val KEY_SOURCE_LATENCY = "source_latency"
    }
}
