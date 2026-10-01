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
        // TVBox 源（csp_* Java 蜘蛛）在本架构下解析不了。直接给出结论，
        // 不去 GET 那个「图片伪装成配置」的地址 —— 那只会测出「可用」的假象。
        if (site.isTvBox) {
            return SourceStatus(
                key = site.key,
                isAvailable = false,
                latencyMs = -1,
                error = "TVBox 源需要 TVBox 内核（jar + JS 引擎），当前架构无法解析",
            )
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
