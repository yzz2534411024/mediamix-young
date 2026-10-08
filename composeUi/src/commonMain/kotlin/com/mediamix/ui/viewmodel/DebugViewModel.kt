package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.cache.CacheManager
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.player.engines.MetricsEngine
import com.mediamix.shared.spider.SpiderService
import com.mediamix.shared.spider.TvBoxSelfTestRunner
import com.mediamix.ui.source.SourceRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 一条诊断项的展示模型 */
data class DebugEntry(
    val label: String,
    val value: String,
)

/**
 * 播放诊断面板。
 *
 * 把此前只存在于日志里的运行数据摆到界面上：播放指标（缓冲次数/时长、首帧延迟、错误）、
 * 缓存统计、以及各数据源的实测延迟。排查「为什么这个源慢」「为什么卡」时不用再翻 logcat。
 */
class DebugViewModel(
    private val metricsEngine: MetricsEngine,
    private val cacheManager: CacheManager,
    private val sourceRepository: SourceRepository,
    private val spiderService: SpiderService,
    private val httpClient: HttpClient,
) : ViewModel() {
    private val logger = Logger.withTag("DebugViewModel")

    private val _metrics = MutableStateFlow<List<DebugEntry>>(emptyList())
    val metrics: StateFlow<List<DebugEntry>> = _metrics.asStateFlow()

    private val _cacheStats = MutableStateFlow<List<DebugEntry>>(emptyList())
    val cacheStats: StateFlow<List<DebugEntry>> = _cacheStats.asStateFlow()

    private val _sources = MutableStateFlow<List<DebugEntry>>(emptyList())
    val sources: StateFlow<List<DebugEntry>> = _sources.asStateFlow()

    private val _bridge = MutableStateFlow<List<DebugEntry>>(emptyList())
    val bridge: StateFlow<List<DebugEntry>> = _bridge.asStateFlow()

    /** 一键探测的报告文本（可复制） */
    private val _probeReport = MutableStateFlow<List<String>>(emptyList())
    val probeReport: StateFlow<List<String>> = _probeReport.asStateFlow()

    private val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()

    /** 最近一次探测用的 TVBox 源（决定探测哪个配置） */
    val tvBoxSites: List<CmsApiSite> get() = sourceRepository.sites.value.filter { it.isTvBox }

    private var probeJob: Job? = null

    /**
     * 接口自检（**桌面端也可跑**）：线路连通 → 配置拉取+伪装解码 → 站点解析 →
     * 蜘蛛包下载+md5+zip 结构 → 平台蜘蛛桥状态。
     *
     * 与 [runProbe] 的区别：probe 走完整蜘蛛管线（含 dex 调用，桌面端跑不了）；
     * 自检只测「接口本身」，用于在桌面端快速判断配置/线路/蜘蛛包是否正常。
     */
    fun runSelfTest() {
        if (_isProbing.value) return
        val site =
            sourceRepository.sites.value.firstOrNull { it.isTvBox }
                ?: run {
                    _probeReport.value = listOf("没有可自检的 TVBox 源。先在数据源管理里启用「饭太硬」。")
                    return
                }
        probeJob?.cancel()
        probeJob =
            viewModelScope.launch {
                _isProbing.value = true
                _probeReport.value = listOf("接口自检中…（逐线路探测，最长约 40 秒）")
                _probeReport.value =
                    try {
                        val runner = TvBoxSelfTestRunner(spiderService, httpClient)
                        runner.run(site).map { item ->
                            "${if (item.ok) "✓" else "✗"} ${item.name}：${item.detail}"
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        listOf("自检异常: ${e.message ?: "未知错误"}")
                    }
                _isProbing.value = false
            }
    }

    /**
     * 一键探测：按真实链路跑 homeContent → categoryContent → detailContent → playerContent。
     *
     * 「TVBox 源为什么不可用」此前只能靠翻 logcat 逐条比对，且 `homeContent` 的
     * 返回内容一直没定论 —— 这是判断「壳/站点侧无数据」还是「映射层丢数据」的唯一依据。
     */
    fun runProbe() {
        if (_isProbing.value) return
        val site =
            sourceRepository.sites.value.firstOrNull { it.isTvBox }
                ?: run {
                    _probeReport.value = listOf("没有可探测的 TVBox 源。先在数据源管理里启用「饭太硬」。")
                    return
                }
        probeJob?.cancel()
        probeJob =
            viewModelScope.launch {
                _isProbing.value = true
                _probeReport.value = listOf("探测中…（首次可能需下载蜘蛛包与等待壳解密，最长约 60 秒）")
                _probeReport.value =
                    try {
                        // 传真实 configKey：蜘蛛按「配置源 + 站点」缓存，
                        // 猜一个 key 会另建一套实例，探测的就不是首页实际用的那些。
                        spiderService.probeTvBoxPipeline(site.apiUrl, configKey = site.key)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        listOf("探测异常: ${e.message ?: "未知错误"}")
                    }
                _isProbing.value = false
            }
    }

    fun clearProbeReport() {
        _probeReport.value = emptyList()
    }

    /**
     * 一键测速：逐个探测「数据源延迟」列表里的源，记录实测毫秒数。
     *
     * 此前这些数据只能去「设置 → 数据源管理」跑「检测全部」，而诊断页只显示
     * 「未测速」——用户看到列表却找不到触发入口（实测反馈「功能存在但没启用」）。
     * 与 [refreshSources] 同口径：只测非 TVBox 的 CMS 源（TVBox 源走接口自检）。
     */
    fun runSourceSpeedTest() {
        if (_isProbing.value) return
        probeJob?.cancel()
        probeJob =
            viewModelScope.launch {
                _isProbing.value = true
                val sites = sourceRepository.sites.value.filterNot { it.isTvBox }
                _probeReport.value = listOf("正在测速…（${sites.size} 个数据源，逐个探测）")
                val lines = mutableListOf<String>()
                try {
                    coroutineScope {
                        sites
                            .map { site ->
                                async {
                                    val start = Clock.System.now().toEpochMilliseconds()
                                    val ok =
                                        runCatching {
                                            httpClient
                                                .get(site.apiUrl) {
                                                    timeout { requestTimeoutMillis = 8_000 }
                                                }.bodyAsText()
                                        }.isSuccess
                                    val ms = Clock.System.now().toEpochMilliseconds() - start
                                    Triple(site, ok, ms)
                                }
                            }.awaitAll()
                            .forEach { (site, ok, ms) ->
                                if (ok) {
                                    sourceRepository.recordLatency(site.key, ms)
                                    lines += "✓ ${site.name}: ${ms}ms"
                                } else {
                                    lines += "✗ ${site.name}: 不可达"
                                }
                            }
                    }
                    refreshSources()
                    val reachable = lines.count { it.startsWith("✓") }
                    _probeReport.value =
                        listOf("测速完成：$reachable/${sites.size} 个源可达（列表已按快慢排序）") + lines
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _probeReport.value = listOf("测速异常: ${e.message ?: "未知错误"}")
                }
                _isProbing.value = false
            }
    }

    private var pollingJob: Job? = null

    /** 面板可见时开启轮询：指标是播放过程中动态变化的，静止快照看不出问题 */
    fun startPolling() {
        if (pollingJob != null) return
        pollingJob =
            viewModelScope.launch {
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
        refreshBridge()
    }

    private fun refreshMetrics() {
        _metrics.value =
            try {
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
            _cacheStats.value =
                try {
                    // 统一从 CacheManager 门面读，避免诊断页与设置页读数不一致
                    val snapshot = cacheManager.snapshot()
                    listOf(
                        DebugEntry("内存缓存(L1+L2)", formatBytes(snapshot.memory.l1Bytes + snapshot.memory.l2Bytes)),
                        DebugEntry("磁盘缓存", formatBytes(snapshot.stats.totalSize)),
                        DebugEntry("合计", formatBytes(snapshot.totalBytes)),
                        DebugEntry("缓存条目数", snapshot.stats.entryCount.toString()),
                        DebugEntry("命中率", "${(snapshot.stats.hitRate * 100).toInt()}%"),
                    )
                } catch (e: Exception) {
                    listOf(DebugEntry("错误", e.message ?: "读取缓存失败"))
                }
        }
    }

    private fun refreshSources() {
        val entries =
            sourceRepository.sites.value
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

    /**
     * TVBox 蜘蛛桥状态 —— 排查「饭太硬等 TVBox 源为什么不可用」的首要观测点。
     */
    private fun refreshBridge() {
        _bridge.value =
            try {
                listOf(
                    DebugEntry("蜘蛛桥", spiderService.spiderBridgeStatus),
                    DebugEntry(
                        "TVBox 源",
                        sourceRepository.sites.value
                            .count { it.isTvBox }
                            .toString() + " 个",
                    ),
                )
            } catch (e: Exception) {
                listOf(DebugEntry("错误", e.message ?: "读取蜘蛛桥状态失败"))
            }
    }

    private fun formatValue(
        key: String,
        value: Any?,
    ): String =
        when {
            value == null -> "-"
            key.endsWith("Ms") && value is Long -> formatMs(value)
            else -> value.toString()
        }

    private fun formatMs(ms: Long): String = if (ms < 1000) "${ms}ms" else "${ms / 1000}.${(ms % 1000) / 100}s"

    private fun formatBytes(bytes: Long): String =
        when {
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
