package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.VideoCategory
import com.mediamix.shared.models.VideoItem
import com.mediamix.shared.models.VideoListResponse
import com.mediamix.shared.spider.SpiderService
import com.mediamix.shared.spider.VideoApiService
import com.mediamix.ui.source.SourceRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import co.touchlab.kermit.Logger

/**
 * 首页 ViewModel。
 *
 * 这里只在「CMS 接口协议」层面做编排：源 → 分类 → 影片列表 → 分页。
 *
 * ⚠️ TVBox 源（如饭太硬）走的是另一套东西：它的配置里全是 `csp_*` Java 蜘蛛，
 * 需要在 TVBox 内核（jar + JS 引擎）里执行，本项目跑不了。碰到这种情况
 * 不再默默给一个空列表，而是把原因写进 [notice] 让界面明确告诉用户，
 * 并提供一键切换到可用源。
 */
class VideoHomeViewModel(
    private val spiderService: SpiderService,
    private val httpClient: HttpClient,
    private val sourceRepository: SourceRepository,
    private val videoApiService: VideoApiService,
) : ViewModel() {

    private val logger = Logger.withTag("VideoHomeViewModel")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val sites: StateFlow<List<CmsApiSite>> = sourceRepository.sites

    private val _currentSite = MutableStateFlow<CmsApiSite?>(null)
    val currentSite: StateFlow<CmsApiSite?> = _currentSite.asStateFlow()

    private val _categories = MutableStateFlow<List<VideoCategory>>(emptyList())
    val categories: StateFlow<List<VideoCategory>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<VideoCategory?>(null)
    val selectedCategory: StateFlow<VideoCategory?> = _selectedCategory.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isTvBoxSource = MutableStateFlow(false)
    val isTvBoxSource: StateFlow<Boolean> = _isTvBoxSource.asStateFlow()

    /** 当前源在当前架构下无法解析（TVBox csp 蜘蛛）*/
    private val _isSourceUnsupported = MutableStateFlow(false)
    val isSourceUnsupported: StateFlow<Boolean> = _isSourceUnsupported.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 一次性提示（首页顶部横条）*/
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var currentPage = 1
    private var pageCount = 1
    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        sourceRepository.resolveDefaultSite()?.let(::selectSite)
    }

    // ==================== 源 ====================

    fun loadSites() {
        if (_currentSite.value == null) {
            sourceRepository.resolveDefaultSite()?.let(::selectSite)
        }
    }

    fun selectSite(site: CmsApiSite) {
        sourceRepository.currentSiteKey = site.key
        _currentSite.value = site
        _isTvBoxSource.value = site.isTvBox
        _selectedCategory.value = null
        _categories.value = emptyList()
        _videos.value = emptyList()
        _error.value = null
        _notice.value = null
        currentPage = 1
        pageCount = 1
        _hasMore.value = false

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadCategoriesInternal(site)
            loadVideosInternal(site)
        }
    }

    /**
     * 切到第一个「能用」的源。
     *
     * 用于 TVBox 源解析不了、或当前源整体挂掉时给用户一个一键出口，
     * 不用自己去源列表里一个个试。
     */
    fun switchToAvailableSource(): CmsApiSite? {
        val candidate = sourceRepository.enabledSites.firstOrNull {
            !it.isTvBox && it.key != _currentSite.value?.key
        } ?: sourceRepository.enabledSites.firstOrNull { !it.isTvBox }
        candidate?.let { selectSite(it) }
        return candidate
    }

    fun dismissNotice() {
        _notice.value = null
    }

    // ==================== 分类 ====================

    fun selectCategory(category: VideoCategory?) {
        val site = _currentSite.value ?: return
        _selectedCategory.value = category
        _videos.value = emptyList()
        currentPage = 1
        _hasMore.value = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadVideosInternal(site) }
    }

    private suspend fun loadCategoriesInternal(site: CmsApiSite) {
        try {
            if (site.isTvBox) {
                val config = spiderService.fetchTvBoxConfig(site.apiUrl)
                val playable = config.sites.filterNot { it.isJavaSpider }
                _isSourceUnsupported.value = playable.isEmpty()
                if (playable.isEmpty()) {
                    _notice.value = "「${site.name}」的 ${config.sites.size} 个分类全部依赖 TVBox 蜘蛛内核，" +
                        "当前版本无法解析。已保留其它可用的 CMS 源。"
                    _categories.value = emptyList()
                    return
                }
                _categories.value = playable.mapIndexed { index, tvboxSite ->
                    VideoCategory(typeId = index + 1, typePid = 0, typeName = tvboxSite.name)
                }
            } else {
                _isSourceUnsupported.value = false
                val text = httpClient.get(site.apiUrl).bodyAsText()
                val classArr = json.parseToJsonElement(text).jsonObject["class"]?.jsonArray ?: return
                _categories.value = classArr.mapNotNull { elem ->
                    val obj = elem.jsonObject
                    val id = obj["type_id"]?.jsonPrimitive?.int ?: return@mapNotNull null
                    VideoCategory(
                        typeId = id,
                        typePid = obj["type_pid"]?.jsonPrimitive?.int ?: 0,
                        typeName = obj["type_name"]?.jsonPrimitive?.content ?: "",
                    )
                }.filter { it.typePid == 0 || it.typePid < 0 }
            }
        } catch (e: Exception) {
            logger.e { "Load categories failed: ${e.message}" }
            _categories.value = emptyList()
        }
    }

    // ==================== 影片列表 ====================

    fun loadVideos() {
        val site = _currentSite.value ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadVideosInternal(site) }
    }

    private suspend fun loadVideosInternal(site: CmsApiSite) {
        _isLoading.value = true
        _error.value = null
        try {
            if (site.isTvBox) {
                loadSpiderVideos(site)
            } else {
                loadCmsVideos(site)
            }
        } catch (e: Exception) {
            logger.e { "Load videos failed: ${e.message}" }
            _error.value = friendlyError(site, e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun loadCmsVideos(site: CmsApiSite) {
        val typeId = _selectedCategory.value?.typeId
        val page1 = fetchCmsPage(site.apiUrl, 1, typeId)
        val first = mergeVideoItems(page1.list)
        _videos.value = first
        currentPage = 1
        pageCount = page1.pageCount
        _hasMore.value = page1.pageCount > 1 && first.isNotEmpty()

        // 首页默认多抓一页，避免一屏还没填满就到底了；分类页保持一页，滚动更快
        if (typeId == null && _hasMore.value && first.isNotEmpty()) {
            try {
                val page2 = fetchCmsPage(site.apiUrl, 2, typeId)
                _videos.value = mergeVideoItems(first + page2.list)
                currentPage = 2
                _hasMore.value = page2.pageCount > 2
            } catch (e: Exception) {
                logger.w { "Second page preload failed: ${e.message}" }
            }
        }

        if (_videos.value.isEmpty()) {
            _notice.value = "「${site.name}」当前分类没有返回影片，换一个分类或数据源试试。"
        }
    }

    private suspend fun loadSpiderVideos(site: CmsApiSite) {
        val config = spiderService.fetchTvBoxConfig(site.apiUrl)
        val category = _selectedCategory.value

        if (category != null) {
            val tvboxSite = config.sites.filterNot { it.isJavaSpider }
                .getOrNull(category.typeId - 1)
            if (tvboxSite == null) {
                _isSourceUnsupported.value = true
                _notice.value = "该分类来自 TVBox 蜘蛛内核，当前版本无法解析。"
                _videos.value = emptyList()
                return
            }
            val spider = spiderService.getSpider(tvboxSite.key)
                ?: spiderService.initFromConfig(config).find { it.key == tvboxSite.key }
            if (spider == null) {
                _videos.value = emptyList()
                _notice.value = "分类「${tvboxSite.name}」的解析器不可用。"
                return
            }
            _videos.value = spider.homeContent(page = 1).recommend
            _hasMore.value = false
            return
        }

        val spiders = spiderService.initFromConfig(config)
            .filter { spider -> config.sites.any { it.key == spider.key && !it.isJavaSpider } }
        if (spiders.isEmpty()) {
            _isSourceUnsupported.value = true
            _notice.value = "「${site.name}」的 ${config.sites.size} 个分类全部依赖 TVBox 蜘蛛内核，" +
                "当前版本无法解析。已保留其它可用的 CMS 源。"
            _videos.value = emptyList()
            return
        }

        val allItems = mutableListOf<VideoItem>()
        val seenIds = mutableSetOf<String>()
        for (spider in spiders) {
            try {
                spider.homeContent(page = 1).recommend.forEach { item ->
                    if (seenIds.add(item.vodId)) allItems.add(item)
                }
            } catch (e: Exception) {
                logger.w { "Spider ${spider.key} home load failed: ${e.message}" }
            }
        }
        _videos.value = allItems
        _hasMore.value = false
    }

    fun loadMore() {
        if (_isLoading.value || !_hasMore.value) return
        val site = _currentSite.value ?: return
        if (site.isTvBox) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val nextPage = currentPage + 1
                val typeId = _selectedCategory.value?.typeId
                val response = fetchCmsPage(site.apiUrl, nextPage, typeId)
                _videos.value = mergeVideoItems(_videos.value + response.list)
                currentPage = response.page
                pageCount = response.pageCount
                _hasMore.value = response.page < response.pageCount
            } catch (e: Exception) {
                logger.e { "Load more failed: ${e.message}" }
                _hasMore.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        val site = _currentSite.value ?: return
        _notice.value = null
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadCategoriesInternal(site)
            loadVideosInternal(site)
        }
    }

    /** 只刷新列表，不动分类（分类栏展开状态下用）*/
    fun refreshList() {
        val site = _currentSite.value ?: return
        _notice.value = null
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadVideosInternal(site) }
    }

    // ==================== 内部 ====================

    private fun friendlyError(site: CmsApiSite, e: Exception): String = when {
        e is kotlinx.serialization.SerializationException ->
            "「${site.name}」返回的数据不是标准 CMS 格式，可能接口已变更。"
        e.message?.contains("timeout", ignoreCase = true) == true ||
            e.message?.contains("timed out", ignoreCase = true) == true ->
            "连接「${site.name}」超时，请检查网络后重试。"
        e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
            "「${site.name}」域名解析失败，该源可能已下线。"
        else -> "「${site.name}」加载失败：${e.message ?: "未知错误"}"
    }

    /**
     * 拉取一页 CMS 列表。
     *
     * 统一委托给 [VideoApiService]：它自带 `ac=detail`、5 分钟列表缓存与 DNS 预解析，
     * 首页不必再自己拼 URL、自己解析 JSON（这套逻辑原先在 UI 层重复实现了一份）。
     */
    private suspend fun fetchCmsPage(apiUrl: String, page: Int, typeId: Int?): VideoListResponse {
        return videoApiService.fetchVideoList(apiUrl = apiUrl, page = page, typeId = typeId)
    }

    private fun mergeVideoItems(items: List<VideoItem>): List<VideoItem> {
        val seen = mutableSetOf<String>()
        return items.filter { it.vodId.isNotEmpty() && seen.add(it.vodId) }
    }
}
