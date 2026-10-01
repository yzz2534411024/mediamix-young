package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.database.FavoriteDao
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.SourceUnavailableException
import com.mediamix.shared.models.VideoDetail
import com.mediamix.shared.models.VideoItem
import com.mediamix.shared.services.PreloadService
import com.mediamix.shared.spider.SpiderService
import com.mediamix.ui.source.SourceRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 详情页 ViewModel。
 *
 * 负责：拉详情 → 解析播放线路 → 收藏状态 → 相关推荐。
 */
class VideoDetailViewModel(
    private val httpClient: HttpClient,
    private val spiderService: SpiderService,
    private val favoriteDao: FavoriteDao,
    private val sourceRepository: SourceRepository,
    private val preloadService: PreloadService,
) : ViewModel() {
    private val logger = Logger.withTag("VideoDetailViewModel")
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val _detail = MutableStateFlow<VideoDetail?>(null)
    val detail: StateFlow<VideoDetail?> = _detail.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _relatedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val relatedVideos: StateFlow<List<VideoItem>> = _relatedVideos.asStateFlow()

    private val _selectedSourceIndex = MutableStateFlow(0)
    val selectedSourceIndex: StateFlow<Int> = _selectedSourceIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadedVodId: String? = null

    fun loadDetail(
        vodId: String,
        sourceKey: String,
    ) {
        if (loadedVodId == vodId && _detail.value != null) return
        val site = sourceRepository.findByKey(sourceKey) ?: CmsApiSite.findByKey(sourceKey)
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                if (site == null) {
                    throw SourceUnavailableException("找不到数据源「$sourceKey」，请在设置 → 数据源管理里检查。")
                }
                if (site.isTvBox) {
                    // TVBox 源的详情要通过 csp 蜘蛛在 TVBox 内核里执行，本项目不具备该能力；
                    // 早点给出明确原因，比让用户对着空白详情页猜要好。
                    throw SourceUnavailableException(
                        "「${site.name}」需要 TVBox 蜘蛛内核才能取到播放地址，当前版本无法支持，" +
                            "请切换到其它 CMS 数据源。",
                    )
                }
                val loaded = fetchCmsDetail(site, vodId, sourceKey)
                _detail.value = loaded
                loadedVodId = vodId
                _selectedSourceIndex.value = loaded.defaultSourceIndex
                refreshFavoriteState(loaded.vodId)
                loadRelated(site, loaded.typeId, loaded.vodId)
                warmUpFirstEpisode(loaded)
            } catch (e: Exception) {
                logger.e { "Load detail failed: ${e.message}" }
                _error.value = e.message ?: "加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry(
        vodId: String,
        sourceKey: String,
    ) {
        loadedVodId = null
        loadDetail(vodId, sourceKey)
    }

    private suspend fun fetchCmsDetail(
        site: CmsApiSite,
        vodId: String,
        sourceKey: String,
    ): VideoDetail {
        val url = "${site.apiUrl}?ac=detail&ids=$vodId"
        val text = httpClient.get(url).bodyAsText()
        val jsonObj = json.parseToJsonElement(text).jsonObject
        val listArr = jsonObj["list"]?.jsonArray
        if (listArr.isNullOrEmpty()) {
            throw SourceUnavailableException("「${site.name}」没有返回这部影片的详情。")
        }
        val detail =
            VideoDetail.fromJson(
                listArr[0].jsonObject.toMap(),
                sourceKey = sourceKey,
            )
        if (!detail.hasPlayableSource) {
            throw SourceUnavailableException("「${site.name}」这部影片没有可用的播放地址。")
        }
        return detail
    }

    /**
     * 切换收藏状态并**持久化到数据库**。
     *
     * 与收藏页共用 [FavoriteDao] 这一份数据源；原实现只翻转内存布尔值，
     * 导致「详情页点了收藏、收藏页看不到、重启就丢」。
     */
    fun toggleFavorite() {
        val current = _detail.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (favoriteDao.isFavorite(current.vodId)) {
                    favoriteDao.deleteByVodId(current.vodId)
                    _isFavorite.value = false
                    logger.d { "Removed favorite: ${current.vodName}" }
                } else {
                    favoriteDao.insertOrReplace(
                        vodId = current.vodId,
                        vodName = current.vodName,
                        vodPic = current.vodPic,
                        sourceKey = current.sourceKey,
                        typeName = current.typeName,
                        lastEpisodeCount =
                            current.playSources
                                .firstOrNull()
                                ?.episodes
                                ?.size
                                ?.toLong() ?: 0L,
                        addTime = Clock.System.now().toEpochMilliseconds(),
                    )
                    _isFavorite.value = true
                    logger.d { "Added favorite: ${current.vodName}" }
                }
            } catch (e: Exception) {
                logger.e { "Toggle favorite failed: ${e.message}" }
            }
        }
    }

    /**
     * 预热第一集。
     *
     * 用户从详情页点「第1集」的概率最高，而这一步能提前完成 DNS/TCP/TLS 握手，
     * 让首帧更快出来。失败无副作用，因此不做任何提示。
     */
    private fun warmUpFirstEpisode(detail: VideoDetail) {
        val firstUrl =
            detail.playSources
                .firstOrNull { it.episodes.isNotEmpty() }
                ?.episodes
                ?.firstOrNull()
                ?.url
                ?.takeIf { it.isNotBlank() }
                ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                preloadService.warmUp(firstUrl)
            } catch (e: Exception) {
                logger.d { "Warm up first episode failed: ${e.message}" }
            }
        }
    }

    /** 从数据库读取当前影片的收藏状态（进入详情页时调用）。 */
    private fun refreshFavoriteState(vodId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _isFavorite.value = favoriteDao.isFavorite(vodId)
            } catch (e: Exception) {
                logger.e { "Load favorite state failed: ${e.message}" }
                _isFavorite.value = false
            }
        }
    }

    fun selectSource(index: Int) {
        val detail = _detail.value ?: return
        if (index in detail.playSources.indices) {
            _selectedSourceIndex.value = index
        }
    }

    fun loadRelated(
        site: CmsApiSite,
        typeId: Int?,
        excludeVodId: String,
    ) {
        if (typeId == null) return
        viewModelScope.launch {
            try {
                val url = "${site.apiUrl}?ac=detail&t=$typeId&pg=1"
                val text = httpClient.get(url).bodyAsText()
                val jsonObj = json.parseToJsonElement(text).jsonObject
                val listArr = jsonObj["list"]?.jsonArray ?: return@launch
                _relatedVideos.value =
                    listArr
                        .mapNotNull { element ->
                            val obj = element.jsonObject
                            val id = obj["vod_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            if (id == excludeVodId) return@mapNotNull null
                            VideoItem(
                                vodId = id,
                                vodName = obj["vod_name"]?.jsonPrimitive?.content ?: "",
                                vodPic = obj["vod_pic"]?.jsonPrimitive?.content,
                                vodRemarks = obj["vod_remarks"]?.jsonPrimitive?.content,
                                sourceKey = site.key,
                            )
                        }.take(20)
            } catch (e: Exception) {
                logger.e { "Load related failed: ${e.message}" }
                _relatedVideos.value = emptyList()
            }
        }
    }
}
