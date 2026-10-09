package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.SourceStatus
import com.mediamix.shared.spider.SpiderService
import com.mediamix.ui.source.SourceRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock

/**
 * 源管理 ViewModel
 *
 * 状态统一委托给 [SourceRepository]（Koin single）—— 首页也读同一份，
 * 避免"这里停用了、回首页还在"的割裂。
 */
class SourceManageViewModel(
    private val httpClient: HttpClient,
    private val spiderService: SpiderService,
    private val sourceRepository: SourceRepository,
) : ViewModel() {
    private val logger = Logger.withTag("SourceManageViewModel")

    val sites: StateFlow<List<CmsApiSite>> = sourceRepository.sites

    private val _sourceStatuses = MutableStateFlow<Map<String, SourceStatus>>(emptyMap())
    val sourceStatuses: StateFlow<Map<String, SourceStatus>> = _sourceStatuses.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    fun loadSources() = Unit

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun consumeImportMessage() {
        _importMessage.value = null
    }

    /**
     * 导入自定义数据源。
     *
     * **一条输入框两用**：地址若是 TVBox 配置（返回图片伪装的 JSON），
     * 就按 TVBox 源导入 —— 手动拉一次配置确认「站点数 > 0」再落库，
     * 而不是先加进去、回首页才发现是死的。CMS 接口地址则按普通源导入。
     */
    fun importSource(
        name: String,
        url: String,
    ) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val key = keyFor(trimmed)
                if (sourceRepository.findByKey(key) != null) {
                    _importMessage.value = "这个地址已经添加过了。"
                    return@launch
                }
                when (val validated = validate(trimmed)) {
                    is ImportResult.TvBox ->
                        sourceRepository
                            .add(
                                CmsApiSite(
                                    key = key,
                                    name = name.ifBlank { "TVBox 源" },
                                    apiUrl = trimmed,
                                    isBuiltIn = false,
                                    isTvBox = true,
                                ),
                            ).also {
                                _importMessage.value = "已添加 TVBox 源（${validated.siteCount} 个站点）"
                                checkSource(CmsApiSite(key = key, name = name.ifBlank { "TVBox 源" }, apiUrl = trimmed, isBuiltIn = false, isTvBox = true))
                            }

                    is ImportResult.Cms ->
                        sourceRepository
                            .add(
                                CmsApiSite(
                                    key = key,
                                    name = name.ifBlank { "自定义源" },
                                    apiUrl = trimmed,
                                    isBuiltIn = false,
                                ),
                            ).also {
                                _importMessage.value = "已添加 CMS 数据源"
                                checkSource(CmsApiSite(key = key, name = name.ifBlank { "自定义源" }, apiUrl = trimmed, isBuiltIn = false))
                            }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e { "Import source failed: ${e.message}" }
                _importMessage.value = "导入失败：${e.message ?: "地址不可用"}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    private sealed interface ImportResult {
        data class TvBox(
            val siteCount: Int,
        ) : ImportResult

        data object Cms : ImportResult
    }

    /**
     * 试探地址到底是 TVBox 配置还是 CMS 接口。
     *
     * 先按 TVBox 配置解（图片伪装 / Base64 / JSON 三种形态都能识别）；
     * 解析成功且站点数 > 0 就判定为 TVBox，否则按 CMS 接口 GET 一次判活。
     */
    private suspend fun validate(url: String): ImportResult =
        try {
            val config = withTimeout(20_000) { spiderService.fetchTvBoxConfig(url) }
            if (config.sites.isNotEmpty()) {
                ImportResult.TvBox(config.sites.size)
            } else {
                throw IllegalStateException("TVBox 配置里没有站点")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            val probeUrl = url + (if (url.contains("?")) "&" else "?") + "ac=detail&pg=1"
            withTimeout(10_000) { httpClient.get(probeUrl) }
            ImportResult.Cms
        }

    /** 由地址派生稳定的 key（源仓库按 key 去重）。 */
    private fun keyFor(url: String): String {
        val base = url.substringAfter("//").substringBefore('/')
        val hash = base.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
        return "custom_${base.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)}_${hash.toString(36)}"
    }

    fun addSource(config: CmsApiSite) {
        sourceRepository.add(config)
    }

    fun removeSource(key: String) {
        sourceRepository.remove(key)
        _sourceStatuses.value = _sourceStatuses.value.toMutableMap().apply { remove(key) }
    }

    fun toggleSourceEnabled(key: String) {
        sourceRepository.toggleEnabled(key)
    }

    fun checkSource(site: CmsApiSite) {
        viewModelScope.launch {
            val status = probe(site)
            _sourceStatuses.value = _sourceStatuses.value + (site.key to status)
            rememberLatency(status)
        }
    }

    /** 把可用源的实测延迟写回仓库，供下次启动挑选默认源时参考。 */
    private fun rememberLatency(status: SourceStatus) {
        if (status.isAvailable && status.latencyMs > 0) {
            sourceRepository.recordLatency(status.key, status.latencyMs.toLong())
        }
    }

    fun checkAllSources() {
        viewModelScope.launch {
            _isChecking.value = true
            try {
                coroutineScope {
                    currentSites()
                        .map { site ->
                            async { site.key to probe(site) }
                        }.forEach { deferred ->
                            val (key, status) = deferred.await()
                            _sourceStatuses.value = _sourceStatuses.value + (key to status)
                            rememberLatency(status)
                        }
                }
            } catch (e: Exception) {
                logger.e { "Check all sources failed: ${e.message}" }
            } finally {
                _isChecking.value = false
            }
        }
    }

    private fun currentSites(): List<CmsApiSite> = sourceRepository.sites.value

    /**
     * 探测单个源。
     *
     * TVBox 源走的是图片伪装配置，直接 GET 配置地址也算可用性；其余源按
     * CMS 列表接口探测。统一 8s 超时，避免一个死源拖住整轮检测。
     */
    private suspend fun probe(site: CmsApiSite): SourceStatus {
        if (site.isTvBox) {
            // TVBox 源真正要测的是「配置能不能拉到 + 蜘蛛能不能建起来」。
            // 此前一律直接判不可用，用户永远看不到真实原因；现在给出可区分的结论。
            // 首次探测可能包含 1.1MB 蜘蛛包下载与壳解密，所以超时放宽到 30s。
            val startTime = Clock.System.now().toEpochMilliseconds()
            return try {
                val config = withTimeout(30_000) { spiderService.fetchTvBoxConfig(site.apiUrl) }
                val spiders = spiderService.initFromConfig(config, configKey = site.key)
                val latencyMs = (Clock.System.now().toEpochMilliseconds() - startTime).toInt()
                if (spiders.isEmpty()) {
                    SourceStatus(
                        key = site.key,
                        isAvailable = false,
                        latencyMs = latencyMs,
                        error = "配置已取到（${config.sites.size} 个站点），但蜘蛛内核未就绪，暂时解析不了",
                    )
                } else {
                    SourceStatus(
                        key = site.key,
                        isAvailable = true,
                        latencyMs = latencyMs,
                        error = null,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                SourceStatus(
                    key = site.key,
                    isAvailable = false,
                    latencyMs = -1,
                    error = "TVBox 配置拉取失败：${e.message ?: "未知错误"}",
                )
            }
        }

        val startTime = Clock.System.now().toEpochMilliseconds()
        return try {
            // ⚠️ 必须测真实使用的列表接口（带 ac=detail）。
            // 裸地址返回的是分类列表（约 6KB），比真实请求快得多，测出来的延迟没有参考价值。
            val url =
                site.apiUrl +
                    (if (site.apiUrl.contains("?")) "&" else "?") + "ac=detail&pg=1"
            withTimeout(10_000) {
                httpClient.get(url)
            }
            val elapsed = (Clock.System.now().toEpochMilliseconds() - startTime).toInt()
            SourceStatus(key = site.key, isAvailable = true, latencyMs = elapsed)
        } catch (e: Exception) {
            SourceStatus(key = site.key, isAvailable = false, latencyMs = -1, error = e.message)
        }
    }
}
