package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediamix.shared.cache.VideoCacheService
import com.mediamix.shared.player.engines.MetricsEngine
import com.mediamix.ui.source.SourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger

/** 一条诊断项的展示模型 */
data class DebugEntry(val label: String, val value: String)

/**
 * 播放诊断面板。
 *
 * 把此前只存在于日志里的运行数据摆到界面上：播放指标（缓冲次数/时长、首帧延迟、错误）、
 * 缓存统计、以及各数据源的实测延迟。排查「为什么这个源慢」「为什么卡」时不用再翻 logcat。
 */
class DebugViewModel(
    private val metricsEngine: MetricsEngine,
    private val videoCacheService: VideoCacheService,
    private val sourceRepository: SourceRepository,
) : ViewModel() {

    private val logger = Logger.withTag("DebugViewModel")

    private val _metrics = MutableStateFlow<List<DebugEntry>>(emptyList())
    val metrics: StateFlow<List<DebugEntry>> = _metrics.asStateFlow()

    private val _cacheStats = MutableStateFlow<List<DebugEntry>>(emptyList())
    val cacheStats: StateFlow<List<DebugEntry>> = _cacheStats.asStateFlow()

    private val _sources = MutableStateFlow<List<DebugEntry>>(emptyList())
    val sources: StateFlow<List<DebugEntry>> = _sources.asStateFlow()

    private var pollingJob: Job? = null

    /** 面板可见时开启轮询：指标是播放过程中动态变化的，静止快照看不出问题 */
    fun startPolling() {
        if (pollingJob != null) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refresh() {
        refreshMetrics()
        refreshCache()
        refreshSources()
    }

    private fun refreshMetrics() {
        _metrics.value = try {
            val raw = metricsEngine.getCurrentMetrics()
            if (raw.isNullOrEmpty()) {
                listOf(DebugEntry("状态", "当前没有活动的播放会话"))
            } else {
                raw.entries
                    .filter { it.value != null }
                    .map { DebugEntry(it.key, formatValue(it.key, it.value)) }
            }
        } catch (e: Exception) {
            logger.e { "Refresh metrics failed: ${e.message}" }
            listOf(DebugEntry("错误", e.message ?: "读取指标失败"))
        }
    }

    private fun refreshCache() {
        viewModelScope.launch(Dispatchers.Default) {
            _cacheStats.value = try {
                val stats = videoCacheService.getStats()
                val mem = videoCacheService.getMemoryUsage()
                listOf(
                    DebugEntry("内存缓存(L1+L2)", formatBytes(mem.l1Bytes + mem.l2Bytes)),
                    DebugEntry("磁盘缓存", formatBytes(stats.totalSize)),
                    DebugEntry("缓存条目数", stats.entryCount.toString()),
                    DebugEntry("命中率", "${(stats.hitRate * 100).toInt()}%"),
                )
            } catch (e: Exception) {
                listOf(DebugEntry("错误", e.message ?: "读取缓存失败"))
            }
        }
    }

    private fun refreshSources() {
        val entries = sourceRepository.sites.value
            .filterNot { it.isTvBox }
            .sortedBy { sourceRepository.latencyOf(it.key) ?: Long.MAX_VALUE }
            .map { site ->
                val latency = sourceRepository.latencyOf(site.key)
                DebugEntry(
                    site.name,
                    when {
                        latency != null -> "${latency}ms"
                        site.enabled -> "未测速"
                        else -> "已停用"
                    },
                )
            }
        _sources.value = entries
    }

    private fun formatValue(key: String, value: Any?): String = when {
        value == null -> "-"
        key.endsWith("Ms") && value is Long -> formatMs(value)
        else -> value.toString()
    }

    private fun formatMs(ms: Long): String =
        if (ms < 1000) "${ms}ms" else "${ms / 1000}.${(ms % 1000) / 100}s"

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${bytes / (1024 * 1024)}MB"
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 1000L
    }
}
