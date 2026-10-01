package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.database.FavoriteDao
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.SourceRef
import com.mediamix.shared.models.SourceUnavailableException
import com.mediamix.shared.models.VideoDetail
import com.mediamix.shared.models.VideoItem
import com.mediamix.shared.services.PlaybackResolver
import com.mediamix.shared.services.PreloadService
import com.mediamix.shared.services.ResolvedPlay
import com.mediamix.shared.services.SourceContentGateway
import com.mediamix.ui.source.SourceRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    private val favoriteDao: FavoriteDao,
    private val sourceRepository: SourceRepository,
    private val preloadService: PreloadService,
    private val gateway: SourceContentGateway,
    private val resolver: PlaybackResolver,
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

    /** 正在进行的播放地址解析任务 —— 连点同一集时复用，避免重复请求。 */
    private var inFlightResolve: kotlinx.coroutines.Deferred<ResolvedPlay?>? = null

    fun loadDetail(
        vodId: String,
        sourceKey: String,
    ) {
        if (loadedVodId == vodId && _detail.value != null) return
        // TVBox 影片的 sourceKey 是 `配置源::站点` 复合形式，反查时必须先取配置源那一段；
        // 直接拿整串去查会查不到（那正是「点进详情报找不到数据源」的原因）。
        val configKey = SourceRef.configKey(sourceKey)
        val site = sourceRepository.findByKey(configKey) ?: CmsApiSite.findByKey(configKey)
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                if (site == null) {
                    throw SourceUnavailableException("找不到数据源「$configKey」，请在设置 → 数据源管理里检查。")
                }
                val loaded = gateway.loadDetail(site = site, vodId = vodId, sourceKey = sourceKey)
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

    /**
     * 解析某一集的真实播放地址与请求头。
     *
     * CMS 源直通（剧集 url 本身就是地址）；**TVBox 源必须走这一步** ——
     * 它的 `vod_play_url` 里存的是待解析的标识，只有 `playerContent` 才能换成
     * 真实地址和防盗链头。少这一步，点播放必然失败。
     *
     * 失败时写入 [error] 并返回 null，由界面提示用户。
     */
    suspend fun resolveEpisode(
        sourceIndex: Int,
        episodeIndex: Int,
        sourceKey: String,
    ): ResolvedPlay? {
        val d = _detail.value ?: return null
        val playSource = d.playSources.getOrNull(sourceIndex) ?: return null
        val episode = playSource.episodes.getOrNull(episodeIndex) ?: return null

        // 连点去重：同一集正在解析时直接复用同一个任务，避免重复打 playerContent
        inFlightResolve?.let { return it.await() }
        val job = viewModelScope.async { doResolve(playSource.name, episode.url, sourceKey) }
        inFlightResolve = job
        return try {
            job.await()
        } finally {
            inFlightResolve = null
        }
    }

    private suspend fun doResolve(
        flag: String,
        episodeId: String,
        sourceKey: String,
    ): ResolvedPlay? =
        try {
            resolver.resolve(
                sourceKey = sourceKey,
                flag = flag,
                episodeId = episodeId,
                siteResolver = { key -> sourceRepository.findByKey(key) },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e { "Resolve play failed: ${e.message}" }
            _error.value = e.message ?: "解析播放地址失败，换一条线路试试。"
            null
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
        // TVBox 的剧集 url 是**待解析的标识**（要经 playerContent 才能变成地址），
        // 拿它去预热只会打出一串必然失败的请求。
        if (SourceRef.isTvBox(detail.sourceKey)) return
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
        // TVBox 源没有「相关推荐」这种接口，typeId 也拿不到（站内分类是 String），直接跳过
        if (site.isTvBox || typeId == null) return
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
