package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediamix.shared.cache.VideoCacheService
import com.mediamix.shared.core.PlatformPaths
import com.mediamix.shared.database.FavoriteDao
import com.mediamix.shared.database.WatchHistoryDao
import com.russhwolf.settings.Settings
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
import co.touchlab.kermit.Logger
import java.io.File

/**
 * 主题模式枚举
 */
enum class ThemeModeOption {
    SYSTEM, LIGHT, DARK
}

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

/**
 * 设置 ViewModel
 * 接入 VideoCacheService、WatchHistoryDao、FavoriteDao 实现真实功能
 */
class SettingsViewModel(
    private val settings: Settings,
    private val videoCacheService: VideoCacheService,
    private val watchHistoryDao: WatchHistoryDao,
    private val favoriteDao: FavoriteDao,
) : ViewModel() {

    private val logger = Logger.withTag("SettingsViewModel")
    private val themeKey = "theme_mode"

    private val _themeMode = MutableStateFlow(ThemeModeOption.SYSTEM)
    val themeMode: StateFlow<ThemeModeOption> = _themeMode.asStateFlow()

    private val _cacheStats = MutableStateFlow(CacheStatsInfo())
    val cacheStats: StateFlow<CacheStatsInfo> = _cacheStats.asStateFlow()

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    init {
        val savedIndex = settings.getInt(themeKey, 0)
        _themeMode.value = ThemeModeOption.entries.getOrElse(savedIndex) { ThemeModeOption.SYSTEM }
    }

    fun setThemeMode(mode: ThemeModeOption) {
        _themeMode.value = mode
        settings.putInt(themeKey, mode.ordinal)
        logger.d { "Theme mode set: $mode" }
    }

    /**
     * 导出数据为 JSON — 包含收藏和历史记录
     * 返回 JSON 字符串，调用方可选择保存到文件或分享
     */
    fun exportData(): String {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val json = Json { prettyPrint = true }

                val favoritesJson = buildJsonArray {
                    favoriteDao.getAll().forEach { fav ->
                        add(buildJsonObject {
                            put("vodId", fav.vodId)
                            put("vodName", fav.vodName)
                            put("vodPic", fav.vodPic ?: "")
                            put("sourceKey", fav.sourceKey)
                            put("typeName", fav.typeName ?: "")
                            put("lastEpisodeCount", fav.lastEpisodeCount)
                            put("addTime", fav.addTime)
                        })
                    }
                }

                val historyJson = buildJsonArray {
                    watchHistoryDao.getAll().forEach { hist ->
                        add(buildJsonObject {
                            put("vodId", hist.vodId)
                            put("vodName", hist.vodName)
                            put("vodPic", hist.vodPic ?: "")
                            put("sourceKey", hist.sourceKey)
                            put("episodeName", hist.episodeName ?: "")
                            put("lastWatchTime", hist.lastWatchTime)
                        })
                    }
                }

                val exportJson = buildJsonObject {
                    put("exportTime", kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
                    put("appVersion", "0.2.0")
                    put("data", buildJsonObject {
                        put("favorites", favoritesJson)
                        put("histories", historyJson)
                    })
                }

                val result = json.encodeToString(exportJson)

                // 写入本地文件
                val exportDir = File(PlatformPaths.dataDir)
                if (!exportDir.exists()) exportDir.mkdirs()
                val exportFile = File(exportDir, "mediamix_export.json")
                exportFile.writeText(result)

                _exportResult.value = exportFile.absolutePath
                logger.d { "Data exported to: ${exportFile.absolutePath}" }
            } catch (e: Exception) {
                logger.e { "Export data failed: ${e.message}" }
                _exportResult.value = null
            }
        }
        return ""
    }

    /**
     * 清除缓存 — 调用 VideoCacheService.clearAll() 清理内存+磁盘缓存
     */
    fun clearCache() {
        viewModelScope.launch {
            try {
                videoCacheService.clearAll()
                refreshCacheStats()
                logger.d { "Cache cleared" }
            } catch (e: Exception) {
                logger.e { "Clear cache failed: ${e.message}" }
            }
        }
    }

    /**
     * 刷新缓存统计 — 从 VideoCacheService 获取真实数据
     */
    fun refreshCacheStats() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val stats = videoCacheService.getStats()
                val memUsage = videoCacheService.getMemoryUsage()
                val memSize = memUsage.l1Bytes + memUsage.l2Bytes
                _cacheStats.value = CacheStatsInfo(
                    memoryCacheSize = memSize,
                    diskCacheSize = stats.totalSize,
                    totalSize = memSize + stats.totalSize,
                    entryCount = stats.entryCount,
                    hitRate = stats.hitRate,
                )
            } catch (e: Exception) {
                logger.e { "Refresh cache stats failed: ${e.message}" }
                _cacheStats.value = CacheStatsInfo()
            }
        }
    }
}
