package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.cache.CacheKind
import com.mediamix.shared.cache.CacheManager
import com.mediamix.shared.core.PlatformPaths
import com.mediamix.shared.database.FavoriteDao
import com.mediamix.shared.database.WatchHistoryDao
import com.mediamix.shared.player.engines.MetricsEngine
import com.mediamix.ui.prefs.AppPreferences
import com.mediamix.ui.prefs.DecodeMode
import com.mediamix.ui.theme.ThemeConfig
import com.mediamix.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * 主题模式。
 *
 * 直接用 [ThemeMode]，避免历史上「两套一模一样的枚举」导致
 * 设置页改了 A、主题系统读的是 B，怎么点都不生效。
 */
typealias ThemeModeOption = ThemeMode

/**
 * 缓存统计信息
 */
data class CacheStatsInfo(
    val memoryCacheSize: Long = 0L,
    val diskCacheSize: Long = 0L,
    val totalSize: Long = 0L,
    val entryCount: Int = 0,
    val hitRate: Double = 0.0,
)

/** 一次性操作结果提示（设置页用 Snackbar 展示） */
data class SettingsMessage(
    val text: String,
    val id: Long,
)

/**
 * 设置 ViewModel
 *
 * 所有开关都走 [AppPreferences] 落盘，并在改动时同步到运行中的系统
 * （例如主题要写回 [ThemeConfig]，播放器才能立即换肤）。
 */
class SettingsViewModel(
    private val preferences: AppPreferences,
    private val cacheManager: CacheManager,
    private val watchHistoryDao: WatchHistoryDao,
    private val favoriteDao: FavoriteDao,
    private val metricsEngine: MetricsEngine,
) : ViewModel() {
    private val logger = Logger.withTag("SettingsViewModel")

    private val _themeMode = MutableStateFlow(preferences.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _decodeMode = MutableStateFlow(preferences.decodeMode)
    val decodeMode: StateFlow<DecodeMode> = _decodeMode.asStateFlow()

    private val _shareUsageData = MutableStateFlow(preferences.shareUsageData)
    val shareUsageData: StateFlow<Boolean> = _shareUsageData.asStateFlow()

    private val _skipInterval = MutableStateFlow(preferences.skipIntervalSeconds)
    val skipInterval: StateFlow<Int> = _skipInterval.asStateFlow()

    private val _cacheStats = MutableStateFlow(CacheStatsInfo())
    val cacheStats: StateFlow<CacheStatsInfo> = _cacheStats.asStateFlow()

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message.asStateFlow()

    private var messageSeq = 0L

    init {
        // 启动时把持久化的主题推给运行中的主题系统，否则冷启动永远是"跟随系统"
        ThemeConfig.themeMode.value = _themeMode.value
        // 「使用数据分享」开关必须真正作用于指标引擎，否则它只是个存了偏好的死开关
        metricsEngine.setEnabled(_shareUsageData.value)
    }

    // ==================== 主题 ====================

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        preferences.themeMode = mode
        // 立即生效：MediaMixTheme 收集的就是这个 StateFlow
        ThemeConfig.themeMode.value = mode
        logger.d { "Theme mode set: $mode" }
    }

    // ==================== 解码模式 ====================

    fun setDecodeMode(mode: DecodeMode) {
        _decodeMode.value = mode
        preferences.decodeMode = mode
        notify("解码方式已切换为「${mode.labelForMessage()}」，下次播放生效")
    }

    // ==================== 使用数据分享 ====================

    fun setShareUsageData(enabled: Boolean) {
        _shareUsageData.value = enabled
        preferences.shareUsageData = enabled
        // 真正生效：关闭后指标引擎不再累积数据
        metricsEngine.setEnabled(enabled)
        notify(if (enabled) "已开启播放指标记录" else "已关闭播放指标记录")
    }

    // ==================== 快进 / 快退间隔 ====================

    /**
     * 设置快进 / 快退间隔（秒）。
     *
     * 只写偏好，不直接碰 PlayerCoreManager —— 它是 single 的，而设置页打开时
     * 播放器可能压根没初始化。真正下发发生在打开播放页时（见 PlayerViewModel.openVideo）。
     */
    fun setSkipInterval(seconds: Int) {
        val valid =
            seconds.takeIf { it in AppPreferences.SKIP_INTERVAL_OPTIONS }
                ?: AppPreferences.DEFAULT_SKIP_INTERVAL
        _skipInterval.value = valid
        preferences.skipIntervalSeconds = valid
        notify("快进/快退间隔已设为 $valid 秒")
    }

    // ==================== 缓存 ====================

    /**
     * 清除缓存。
     *
     * 界面需要先弹确认框再调用这里；返回结果通过 [message] 通知，
     * 而不是像原来那样点完什么都不发生。
     */
    fun clearCache() {
        viewModelScope.launch {
            try {
                // 走 CacheManager 门面：视频缓存与接口元数据一起清，
                // 避免「清了缓存但占用没降」这种因清理范围不一致造成的困惑。
                cacheManager.clear(CacheKind.ALL)
                refreshCacheStats()
                notify("缓存已清除")
            } catch (e: Exception) {
                logger.e { "Clear cache failed: ${e.message}" }
                notify("清除缓存失败：${e.message}")
            }
        }
    }

    /**
     * 刷新缓存统计 — 统一从 [CacheManager] 取真实数据
     */
    fun refreshCacheStats() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val snapshot = cacheManager.snapshot()
                val memSize = snapshot.memory.l1Bytes + snapshot.memory.l2Bytes
                _cacheStats.value =
                    CacheStatsInfo(
                        memoryCacheSize = memSize,
                        diskCacheSize = snapshot.stats.totalSize,
                        totalSize = snapshot.totalBytes,
                        entryCount = snapshot.stats.entryCount,
                        hitRate = snapshot.stats.hitRate,
                    )
            } catch (e: Exception) {
                logger.e { "Refresh cache stats failed: ${e.message}" }
                _cacheStats.value = CacheStatsInfo()
            }
        }
    }

    // ==================== 导出 ====================

    /**
     * 导出收藏与历史为 JSON。
     *
     * 旧实现在 viewModelScope 里异步写文件，然后**直接返回空字符串** ——
     * 界面拿不到路径，用户以为功能坏了。现在写完后通过 [message] 把
     * 真实路径回传。
     */
    fun exportData() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val json = Json { prettyPrint = true }

                val favoritesJson =
                    buildJsonArray {
                        favoriteDao.getAll().forEach { fav ->
                            add(
                                buildJsonObject {
                                    put("vodId", fav.vodId)
                                    put("vodName", fav.vodName)
                                    put("vodPic", fav.vodPic ?: "")
                                    put("sourceKey", fav.sourceKey)
                                    put("typeName", fav.typeName ?: "")
                                    put("lastEpisodeCount", fav.lastEpisodeCount)
                                    put("addTime", fav.addTime)
                                },
                            )
                        }
                    }

                val historyJson =
                    buildJsonArray {
                        watchHistoryDao.getAll().forEach { hist ->
                            add(
                                buildJsonObject {
                                    put("vodId", hist.vodId)
                                    put("vodName", hist.vodName)
                                    put("vodPic", hist.vodPic ?: "")
                                    put("sourceKey", hist.sourceKey)
                                    put("episodeName", hist.episodeName ?: "")
                                    put("lastWatchTime", hist.lastWatchTime)
                                },
                            )
                        }
                    }

                val exportJson =
                    buildJsonObject {
                        put(
                            "exportTime",
                            kotlinx.datetime.Clock.System
                                .now()
                                .toEpochMilliseconds(),
                        )
                        put("appVersion", "0.2.0")
                        put(
                            "data",
                            buildJsonObject {
                                put("favorites", favoritesJson)
                                put("histories", historyJson)
                            },
                        )
                    }

                val result = json.encodeToString(exportJson)

                val exportDir = File(PlatformPaths.dataDir.ifBlank { PlatformPaths.cacheDir })
                if (!exportDir.exists()) exportDir.mkdirs()
                val exportFile = File(exportDir, "mediamix_export.json")
                exportFile.writeText(result)

                val favCount = favoriteDao.getAll().size
                val histCount = watchHistoryDao.getAll().size
                logger.d { "Data exported to: ${exportFile.absolutePath}" }
                notify("已导出 $favCount 条收藏、$histCount 条历史\n${exportFile.absolutePath}")
            } catch (e: Exception) {
                logger.e { "Export data failed: ${e.message}" }
                notify("导出失败：${e.message}")
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun notify(text: String) {
        messageSeq += 1
        _message.value = SettingsMessage(text = text, id = messageSeq)
    }
}

/** 提示文案里用的简短名称 */
private fun DecodeMode.labelForMessage(): String =
    when (this) {
        DecodeMode.AUTO -> "自动"
        DecodeMode.HARDWARE -> "硬件解码优先"
        DecodeMode.SOFTWARE -> "软件解码优先"
    }
