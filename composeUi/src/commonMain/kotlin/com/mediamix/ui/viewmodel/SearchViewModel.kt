package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.VideoItem
import com.mediamix.shared.spider.SpiderService
import com.mediamix.ui.util.encodeUrlComponent
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 搜索 ViewModel
 * 替代: searchQueryProvider, debouncedSearchQueryProvider, searchResultProvider
 * 支持 500ms 防抖搜索和多源结果合并。
 */
class SearchViewModel(
    private val httpClient: HttpClient,
    private val spiderService: SpiderService,
) : ViewModel() {
    private val logger = Logger.withTag("SearchViewModel")
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<VideoItem>>(emptyList())
    val results: StateFlow<List<VideoItem>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var debounceJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        debounceJob?.cancel()
        debounceJob =
            viewModelScope.launch {
                delay(500)
                if (newQuery.isNotBlank()) {
                    search(newQuery)
                } else {
                    clearResults()
                }
            }
    }

    fun search(queryText: String = _query.value) {
        if (queryText.isBlank()) {
            clearResults()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val allItems = searchAllSources(queryText)
                val seen = mutableSetOf<String>()
                _results.value = allItems.filter { seen.add(it.vodName) }
            } catch (e: Exception) {
                logger.e { "Search failed: ${e.message}" }
                _results.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearResults() {
        _results.value = emptyList()
        _isSearching.value = false
    }

    private suspend fun searchAllSources(queryText: String): List<VideoItem> {
        // 仅搜索 CMS API 站点，TvBox 站点不支持 CMS 搜索协议
        val sites = CmsApiSite.defaultSites.filter { it.enabled && !it.isTvBox }
        if (sites.isEmpty()) {
            logger.w { "No CMS API sites enabled for search" }
            return emptyList()
        }

        // URL 编码搜索关键词（支持中文，兼容 KMP commonMain）
        val encodedQuery = queryText.encodeUrlComponent()
        logger.d { "Searching ${sites.size} CMS sites for: $queryText" }

        val allResults = mutableListOf<VideoItem>()
        val jobs =
            sites.map { site ->
                viewModelScope.launch {
                    try {
                        // 正确拼接 URL（处理已有查询参数的情况）
                        val separator = if (site.apiUrl.contains("?")) "&" else "?"
                        val url = "${site.apiUrl}${separator}ac=detail&wd=$encodedQuery"
                        val response = httpClient.get(url)
                        val text = response.bodyAsText()
                        val jsonObj = json.parseToJsonElement(text).jsonObject
                        val listArr = jsonObj["list"]?.jsonArray ?: emptyList()
                        val items =
                            listArr.map { elem ->
                                val obj = elem.jsonObject
                                VideoItem(
                                    vodId = obj["vod_id"]?.jsonPrimitive?.content ?: "",
                                    vodName = obj["vod_name"]?.jsonPrimitive?.content ?: "",
                                    vodPic = obj["vod_pic"]?.jsonPrimitive?.content,
                                    vodRemarks = obj["vod_remarks"]?.jsonPrimitive?.content,
                                    sourceKey = site.key,
                                )
                            }
                        logger.d { "Site ${site.name}: found ${items.size} results" }
                        synchronized(allResults) { allResults.addAll(items) }
                    } catch (e: Exception) {
                        logger.w { "Site ${site.name} search failed: ${e.message}" }
                    }
                }
            }
        jobs.forEach { it.join() }
        logger.d { "Total search results: ${allResults.size}" }
        return allResults
    }
}
