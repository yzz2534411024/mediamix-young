package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.SpiderCategory
import com.mediamix.shared.models.VideoCategory
import com.mediamix.shared.models.VideoItem
import com.mediamix.shared.services.HomeCatalog
import com.mediamix.shared.services.SiteNode
import com.mediamix.shared.services.SourceContentGateway
import com.mediamix.ui.source.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页 ViewModel。
 *
 * 只做「源 → 目录 → 影片列表 → 分页」的编排，**具体协议由
 * [SourceContentGateway] 分流**。此前这里散落着三处 `if (site.isTvBox)`，
 * 而且 TVBox 分支用「站点下标 + 1」伪装成 CMS 的 `typeId`，从没真正进入
 * 站内 class 层级 —— 用户点任何一个分类都只会看到该站点的首页推荐。
 * 现在 TVBox 走**两层**目录：
 *
 * 1. 一级 = 配置里的站点（[siteNodes]，由网关建立蜘蛛后给出）；
 * 2. 二级 = 站点的站内 class（[siteClasses]，点进站点时才拉，带缓存）。
 */
class VideoHomeViewModel(
    private val sourceRepository: SourceRepository,
    private val gateway: SourceContentGateway,
) : ViewModel() {
    private val logger = Logger.withTag("VideoHomeViewModel")

    val sites: StateFlow<List<CmsApiSite>> = sourceRepository.sites

    private val _currentSite = MutableStateFlow<CmsApiSite?>(null)
    val currentSite: StateFlow<CmsApiSite?> = _currentSite.asStateFlow()

    // ==================== 目录 ====================

    /** CMS 源的一级分类 */
    private val _categories = MutableStateFlow<List<VideoCategory>>(emptyList())
    val categories: StateFlow<List<VideoCategory>> = _categories.asStateFlow()

    /** CMS 源当前选中的分类 */
    private val _selectedCategory = MutableStateFlow<VideoCategory?>(null)
    val selectedCategory: StateFlow<VideoCategory?> = _selectedCategory.asStateFlow()

    /** TVBox 源的一级目录：配置里的站点 */
    private val _siteNodes = MutableStateFlow<List<SiteNode>>(emptyList())
    val siteNodes: StateFlow<List<SiteNode>> = _siteNodes.asStateFlow()

    /** TVBox 源当前选中的站点 */
    private val _selectedSiteNode = MutableStateFlow<SiteNode?>(null)
    val selectedSiteNode: StateFlow<SiteNode?> = _selectedSiteNode.asStateFlow()

    /** TVBox 源当前站点的站内分类 */
    private val _siteClasses = MutableStateFlow<List<SpiderCategory>>(emptyList())
    val siteClasses: StateFlow<List<SpiderCategory>> = _siteClasses.asStateFlow()

    /** 当前选中的站内分类；null 表示「该站点首页推荐」 */
    private val _selectedClass = MutableStateFlow<SpiderCategory?>(null)
    val selectedClass: StateFlow<SpiderCategory?> = _selectedClass.asStateFlow()

    private val _isTvBoxSource = MutableStateFlow(false)
    val isTvBoxSource: StateFlow<Boolean> = _isTvBoxSource.asStateFlow()

    /** 当前源在当前架构下无法解析 */
    private val _isSourceUnsupported = MutableStateFlow(false)
    val isSourceUnsupported: StateFlow<Boolean> = _isSourceUnsupported.asStateFlow()

    // ==================== 列表 ====================

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

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
        resetCatalogState()
        _error.value = null
        _notice.value = null

        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                loadCatalog(site)
                loadVideosInternal(site)
            }
    }

    private fun resetCatalogState() {
        _selectedCategory.value = null
        _categories.value = emptyList()
        _siteNodes.value = emptyList()
        _selectedSiteNode.value = null
        _siteClasses.value = emptyList()
        _selectedClass.value = null
        _videos.value = emptyList()
        currentPage = 1
        pageCount = 1
        _hasMore.value = false
    }

    /**
     * 切到第一个「能用」的源。
     *
     * 用于当前源整体挂掉时给用户一个一键出口，不用自己去源列表里一个个试。
     */
    fun switchToAvailableSource(): CmsApiSite? {
        val candidate =
            sourceRepository.enabledSites.firstOrNull {
                !it.isTvBox && it.key != _currentSite.value?.key
            } ?: sourceRepository.enabledSites.firstOrNull { !it.isTvBox }
        candidate?.let { selectSite(it) }
        return candidate
    }

    fun dismissNotice() {
        _notice.value = null
    }

    // ==================== 目录加载 ====================

    private suspend fun loadCatalog(site: CmsApiSite) {
        _isSourceUnsupported.value = false
        try {
            when (val catalog = gateway.loadCatalog(site)) {
                is HomeCatalog.Flat -> {
                    _categories.value = catalog.categories
                }

                is HomeCatalog.Tree -> {
                    _siteNodes.value = catalog.sites
                    // 站点选好之前先不下拉任何列表 —— 两层目录的第二层要靠用户点选，
                    // 每站一次 homeContent 的并发聚合只会在首屏白等几十秒。
                    if (catalog.sites.isEmpty()) {
                        _isSourceUnsupported.value = true
                        _notice.value = "「${site.name}」配置里没有可用站点。"
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e { "Load catalog failed: ${e.message}" }
            _categories.value = emptyList()
            _siteNodes.value = emptyList()
            if (site.isTvBox) {
                _isSourceUnsupported.value = true
                _notice.value = e.message ?: "「${site.name}」目录加载失败。"
            }
        }
    }

    // ==================== TVBox 两层目录 ====================

    /** 点击一级目录里的站点：拉该站点的站内 class，并展示首页推荐 */
    fun selectSiteNode(node: SiteNode) {
        val site = _currentSite.value ?: return
        if (!site.isTvBox) return
        _selectedSiteNode.value = node
        _selectedClass.value = null
        _siteClasses.value = emptyList()
        _notice.value = null
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                try {
                    _siteClasses.value = gateway.loadSiteClasses(site, node.key)
                    if (_siteClasses.value.isEmpty()) {
                        _notice.value = "「${node.name}」没有上报站内分类，只能看它的首页推荐。"
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w { "Load site classes failed: ${e.message}" }
                    _notice.value = "「${node.name}」的站内分类读取失败：${e.message ?: "未知错误"}"
                }
            }
        loadVideos()
    }

    /** 点击二级目录里的站内分类 */
    fun selectClass(category: SpiderCategory?) {
        _selectedClass.value = category
        loadVideos()
    }

    // ==================== CMS 分类 ====================

    fun selectCategory(category: VideoCategory?) {
        _selectedCategory.value = category
        loadVideos()
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
            if (!site.isTvBox) {
                loadCmsVideos(site)
                return
            }
            // TVBox 必须先选中站点 —— 43 个站点并发聚合会让首屏等几十秒，
            // 而且用户根本看不出「为什么慢」。选中哪个站点就只拉哪个。
            val node = _selectedSiteNode.value
            if (node == null) {
                _videos.value = emptyList()
                _hasMore.value = false
                return
            }
            loadSpiderVideos(site, node, page = 1, append = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e { "Load videos failed: ${e.message}" }
            _error.value = friendlyError(site, e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun loadCmsVideos(site: CmsApiSite) {
        val typeId = _selectedCategory.value?.typeId
        val page1 = gateway.loadList(site = site, siteKey = "", tid = typeId?.toString(), page = 1)
        val first = mergeVideoItems(page1.list)
        _videos.value = first
        currentPage = 1
        pageCount = page1.pageCount
        _hasMore.value = page1.pageCount > 1 && first.isNotEmpty()

        // 首页默认多抓一页，避免一屏还没填满就到底了；分类页保持一页，滚动更快
        if (typeId == null && _hasMore.value && first.isNotEmpty()) {
            try {
                val page2 = gateway.loadList(site = site, siteKey = "", tid = null, page = 2)
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

    /**
     * TVBox 列表。
     *
     * [tid] 为 null 时是站点首页推荐（`homeContent`），否则是该站内分类
     * （`categoryContent(tid, page)`）—— 这正是 A2 修复点：旧实现点分类仍然调
     * `homeContent`，站内 class 层级从来没被用过。
     */
    private suspend fun loadSpiderVideos(
        site: CmsApiSite,
        node: SiteNode,
        page: Int,
        append: Boolean,
    ) {
        val tid = _selectedClass.value?.typeId
        val result = gateway.loadList(site = site, siteKey = node.key, tid = tid, page = page)
        val incoming = result.list.filter { it.vodId.isNotEmpty() }
        _videos.value = if (append) mergeVideoItems(_videos.value + incoming) else mergeVideoItems(incoming)
        currentPage = result.page.coerceAtLeast(page)
        pageCount = result.pageCount.coerceAtLeast(1)
        _hasMore.value = currentPage < pageCount && incoming.isNotEmpty()

        if (_videos.value.isEmpty()) {
            _notice.value =
                if (tid == null) {
                    "「${node.name}」没有返回影片，换一个站点试试。"
                } else {
                    "「${node.name}」的这个分类没有返回影片，换一个分类试试。"
                }
        }
    }

    fun loadMore() {
        if (_isLoading.value || !_hasMore.value) return
        val site = _currentSite.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val nextPage = currentPage + 1
                if (site.isTvBox) {
                    val node = _selectedSiteNode.value ?: return@launch
                    loadSpiderVideos(site, node, page = nextPage, append = true)
                } else {
                    val response =
                        gateway.loadList(
                            site = site,
                            siteKey = "",
                            tid = _selectedCategory.value?.typeId?.toString(),
                            page = nextPage,
                        )
                    _videos.value = mergeVideoItems(_videos.value + response.list)
                    currentPage = response.page
                    pageCount = response.pageCount
                    _hasMore.value = response.page < response.pageCount
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
        gateway.invalidateTvBoxCache()
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                loadCatalog(site)
                loadVideosInternal(site)
            }
    }

    /** 只刷新列表，不动分类（分类栏展开状态下用）*/
    fun refreshList() {
        val site = _currentSite.value ?: return
        _notice.value = null
        loadVideos()
    }

    // ==================== 内部 ====================

    private fun friendlyError(
        site: CmsApiSite,
        e: Exception,
    ): String =
        when {
            e is kotlinx.serialization.SerializationException ->
                "「${site.name}」返回的数据不是标准 CMS 格式，可能接口已变更。"
            e.message?.contains("timeout", ignoreCase = true) == true ||
                e.message?.contains("timed out", ignoreCase = true) == true ->
                "连接「${site.name}」超时，请检查网络后重试。"
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                "「${site.name}」域名解析失败，该源可能已下线。"
            else -> "「${site.name}」加载失败：${e.message ?: "未知错误"}"
        }

    private fun mergeVideoItems(items: List<VideoItem>): List<VideoItem> {
        val seen = mutableSetOf<String>()
        return items.filter { it.vodId.isNotEmpty() && seen.add(it.vodId) }
    }
}
