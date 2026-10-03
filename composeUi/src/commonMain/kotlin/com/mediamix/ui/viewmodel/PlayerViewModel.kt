package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.database.PlaybackProgressDao
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.player.AspectMode
import com.mediamix.shared.player.EpisodeSource
import com.mediamix.shared.player.PlayMode
import com.mediamix.shared.player.PlayerCoreManager
import com.mediamix.shared.player.PlayerState
import com.mediamix.shared.player.SubtitleService
import com.mediamix.shared.player.SubtitleTrack
import com.mediamix.shared.player.TrackInfo
import com.mediamix.shared.services.PlaybackResolver
import com.mediamix.shared.services.ResolvedPlay
import com.mediamix.ui.player.PlaybackSession
import com.mediamix.shared.database.WatchHistoryDao
import com.mediamix.ui.player.PlaybackSessionStore
import com.mediamix.ui.prefs.AppPreferences
import com.mediamix.ui.source.SourceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * 播放器 ViewModel
 *
 * 桥接 PlayerCoreManager 的回调为 StateFlow 更新。
 * 集成 PlaybackProgressDao 实现播放进度持久化。
 * 集成 SubtitleService 实现字幕文本实时展示。
 */
class PlayerViewModel(
    private val playerCoreManager: PlayerCoreManager,
    private val playbackProgressDao: PlaybackProgressDao,
    private val subtitleService: SubtitleService,
    private val appPreferences: AppPreferences,
    private val sourceRepository: SourceRepository,
    private val resolver: PlaybackResolver,
    private val sessionStore: PlaybackSessionStore,
    private val watchHistoryDao: WatchHistoryDao,
) : ViewModel() {
    private val logger = Logger.withTag("PlayerViewModel")

    // ===== Playback State =====

    private val _playerState = MutableStateFlow(PlayerState.IDLE)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPercentage = MutableStateFlow(0)
    val bufferedPercentage: StateFlow<Int> = _bufferedPercentage.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentQualityIndex = MutableStateFlow(0)
    val currentQualityIndex: StateFlow<Int> = _currentQualityIndex.asStateFlow()

    private val _qualityLabels = MutableStateFlow<List<String>>(emptyList())
    val qualityLabels: StateFlow<List<String>> = _qualityLabels.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isSeeking = MutableStateFlow(false)
    val isSeeking: StateFlow<Boolean> = _isSeeking.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _brightness = MutableStateFlow(0.5f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks.asStateFlow()

    private val _currentSubtitleTrack = MutableStateFlow(0)
    val currentSubtitleTrack: StateFlow<Int> = _currentSubtitleTrack.asStateFlow()

    private val _currentEpisodeIndex = MutableStateFlow(0)
    val currentEpisodeIndex: StateFlow<Int> = _currentEpisodeIndex.asStateFlow()

    private val _currentEpisodeName = MutableStateFlow("")
    val currentEpisodeName: StateFlow<String> = _currentEpisodeName.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _aspectMode = MutableStateFlow(AspectMode.ADAPTIVE)
    val aspectMode: StateFlow<AspectMode> = _aspectMode.asStateFlow()

    /**
     * 视频真实宽高比（w/h），未知为 0f。
     *
     * Android 用 TextureView 渲染，**不会自动适配视频比例** —— UI 层必须按这个值
     * 约束画面尺寸，否则默认拉伸填满（实测用户反馈「自适应比例太大」）。
     * 桌面端由 mpv 自行处理，该值恒为 0。
     */
    private val _videoAspectRatio = MutableStateFlow(0f)
    val videoAspectRatio: StateFlow<Float> = _videoAspectRatio.asStateFlow()

    /**
     * 首次播放前的原生运行库准备进度（桌面端 mpv 按需下载）。
     * null = 无需准备（已就绪 / Android）；0f..1f = 正在下载。
     */
    private val _runtimeProgress = MutableStateFlow<Float?>(null)
    val runtimeProgress: StateFlow<Float?> = _runtimeProgress.asStateFlow()

    /** 引擎是否自行处理画面比例（桌面 mpv = true → UI 层不再约束尺寸）。 */
    val engineHandlesAspect: Boolean get() = playerCoreManager.engineHandlesAspectInternally

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingText = MutableStateFlow("Loading...")
    val loadingText: StateFlow<String> = _loadingText.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _hasPrevEpisode = MutableStateFlow(false)
    val hasPrevEpisode: StateFlow<Boolean> = _hasPrevEpisode.asStateFlow()

    private val _hasNextEpisode = MutableStateFlow(false)
    val hasNextEpisode: StateFlow<Boolean> = _hasNextEpisode.asStateFlow()

    private val _showSubtitles = MutableStateFlow(true)
    val showSubtitles: StateFlow<Boolean> = _showSubtitles.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks.asStateFlow()

    private val _videoTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val videoTracks: StateFlow<List<TrackInfo>> = _videoTracks.asStateFlow()

    private val _speedOptions =
        MutableStateFlow(
            listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f),
        )
    val speedOptions: StateFlow<List<Float>> = _speedOptions.asStateFlow()

    // ===== Subtitle Text (实时字幕文本) =====

    private val _currentSubtitleText = MutableStateFlow<String?>(null)
    val currentSubtitleText: StateFlow<String?> = _currentSubtitleText.asStateFlow()

    /** 字幕同步偏移量（毫秒），正值延后、负值提前 */
    private val _subtitleOffsetMs = MutableStateFlow(0L)
    val subtitleOffsetMs: StateFlow<Long> = _subtitleOffsetMs.asStateFlow()

    /**
     * 快进 / 快退间隔（秒）。
     *
     * 播放页此前把它硬编码成 10，用户在设置里改多少都没用。
     * 现在由设置页写入 [AppPreferences]，打开播放页时推给播放器再回读。
     */
    private val _skipInterval = MutableStateFlow(AppPreferences.DEFAULT_SKIP_INTERVAL)
    val skipInterval: StateFlow<Int> = _skipInterval.asStateFlow()

    // ===== Internal =====

    private var currentVideoUrl: String = ""
    private var progressSaveJob: Job? = null
    private var subtitleUpdateJob: Job? = null
    private var disposed = false

    /** 本次打开时传入的剧集列表（用于选集面板） */
    private var localEpisodeNames: List<String> = emptyList()
    private var localEpisodeUrls: List<String> = emptyList()

    /** 剧集列表变化（切集/自动连播都会更新），供 UI 显示选集与上下集状态 */
    private val _episodeList = MutableStateFlow<List<String>>(emptyList())
    val episodeList: StateFlow<List<String>> = _episodeList.asStateFlow()

    // ===== Init =====

    fun initialize() {
        // Connect callbacks directly to StateFlow — no manual sync needed
        playerCoreManager.onPlayerStateChanged = { state -> _playerState.value = state }
        playerCoreManager.onBufferingChanged = { buffering -> _isBuffering.value = buffering }
        playerCoreManager.onSubtitlesLoaded = { tracks ->
            _subtitleTracks.value = tracks
            // Event-driven: only poll when tracks exist
            if (tracks.isNotEmpty()) startSubtitleUpdates() else stopSubtitleUpdates()
        }
        playerCoreManager.onError = { event -> _lastError.value = event.message }
        // 补发：注册回调之前发生的初始化失败（如桌面端缺 mpv 运行库）——
        // 不补发的话用户只会看到白屏，永远等不到错误提示
        playerCoreManager.consumePendingError()?.let { _lastError.value = it.message }
        // 视频比例：`onVideoSizeChanged` 后 UI 层按比例约束画面（Android）
        playerCoreManager.onVideoAspectRatioChanged = { ratio -> _videoAspectRatio.value = ratio }
        // 首次播放组件准备（桌面端按需下载 mpv）：给 UI 一个进度提示，
        // 否则用户看到的是长时间黑屏 —— 与之前的「白屏」观感一模一样。
        playerCoreManager.onNativeRuntimeProgress = { p -> _runtimeProgress.value = p }
        playerCoreManager.onNativeRuntimeReady = { _runtimeProgress.value = null }
        // 剧集变化事件（切集 / 自动连播）——取代了原来的 500ms 轮询
        playerCoreManager.onEpisodeChanged = { _, _ -> syncEpisodeState() }
        playerCoreManager.onTracksChanged = { audio, video ->
            _audioTracks.value = audio
            _videoTracks.value = video
        }
        // 进度链路：PlayerCoreManager 每 250ms 主动推送，进度条 / 时长 / 缓冲率 / 字幕同步都依赖它
        playerCoreManager.onProgress = { positionMs, durationMs, bufferedPercent ->
            _position.value = positionMs
            _duration.value = durationMs
            _bufferedPercentage.value = bufferedPercent
        }

        // One-time initial sync for static properties
        _playbackSpeed.value = playerCoreManager.getPlaybackSpeed()
        _currentQualityIndex.value = playerCoreManager.getCurrentQualityIndex()
        _qualityLabels.value = playerCoreManager.getQualityLabels()
        _volume.value = playerCoreManager.getVolume()
        _brightness.value = playerCoreManager.getBrightness()
        _playMode.value = playerCoreManager.getPlayMode()
        _aspectMode.value = playerCoreManager.getAspectMode()
        _speedOptions.value = playerCoreManager.getSpeedOptions()

        // ⚠️ 播放状态也要立刻同步一次。上面那些回调只在**状态变化时**被触发，
        // 若状态在回调注册之前就已经变过（页面回退后重进、或 ViewModel 被重建），
        // 界面会一直停在默认的 IDLE/暂停 —— 用户看到的正是「点了播放没反应」。
        _playerState.value = playerCoreManager.playerState
        _isBuffering.value = false
        _currentEpisodeIndex.value = playerCoreManager.getCurrentEpisodeIndex()
        _currentEpisodeName.value = playerCoreManager.getCurrentEpisodeName()
        _hasPrevEpisode.value = playerCoreManager.getHasPrevEpisode()
        _hasNextEpisode.value = playerCoreManager.getHasNextEpisode()
    }

    // ===== Actions =====

    fun openVideo(
        url: String,
        title: String,
        episodeIndex: Int? = null,
        episodeNames: List<String>? = null,
        episodeUrls: List<String>? = null,
        qualityLabels: List<String>? = null,
        qualityUrls: List<String>? = null,
        subtitleUrls: List<String>? = null,
        headers: Map<String, String>? = null,
        preferSoftwareDecoding: Boolean = false,
        fallbackUrls: List<String>? = null,
    ) {
        disposed = false
        currentVideoUrl = url
        localEpisodeNames = episodeNames ?: emptyList()
        localEpisodeUrls = episodeUrls ?: emptyList()

        // 恢复播放进度
        val savedProgress =
            try {
                playbackProgressDao.getByVideoUrl(url)
            } catch (e: Exception) {
                logger.w { "Failed to load playback progress: ${e.message}" }
                null
            }

        playerCoreManager.initialize(
            url = url,
            title = title,
            episodeIndex = episodeIndex,
            episodeNames = episodeNames,
            episodeUrls = episodeUrls,
            qualityLabels = qualityLabels,
            qualityUrls = qualityUrls,
            subtitleUrls = subtitleUrls,
            headers = headers,
            preferSoftwareDecoding = preferSoftwareDecoding,
            fallbackUrls = fallbackUrls,
        )

        // 如果有保存的进度，跳转到该位置
        if (savedProgress != null && savedProgress.position > 0 && savedProgress.position < savedProgress.duration - 5000) {
            playerCoreManager.seekTo(savedProgress.position)
            logger.d { "Restored playback position: ${savedProgress.position}ms" }
        }

        // Sync episode state after initialization
        _currentEpisodeIndex.value = playerCoreManager.getCurrentEpisodeIndex()
        _currentEpisodeName.value = playerCoreManager.getCurrentEpisodeName()
        _hasPrevEpisode.value = playerCoreManager.getHasPrevEpisode()
        _hasNextEpisode.value = playerCoreManager.getHasNextEpisode()
        _currentSubtitleTrack.value = playerCoreManager.getCurrentSubtitleTrack()
        _showSubtitles.value = playerCoreManager.getShowSubtitles()

        // 把偏好里的快进/快退间隔推给播放器 —— skipForward / skipBackward 用的是
        // PlayerCoreManager 内部字段，光改设置页的存储是没用的。
        // 注意这行要放在 playerCoreManager.initialize() 之后，
        // 否则会被它内部的 loadSkipInterval() 覆盖掉。
        playerCoreManager.setSkipInterval(appPreferences.skipIntervalSeconds)
        _skipInterval.value = playerCoreManager.getSkipInterval()

        startProgressSaving()
        // 事件驱动：剧集列表与初始状态各同步一次，之后由 onEpisodeChanged 推送
        _episodeList.value = localEpisodeNames
        syncEpisodeState()
        refreshTracks()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        saveCurrentProgress()
        stopProgressSaving()
        stopSubtitleUpdates()
        playerCoreManager.dispose()
    }

    /** 鼠标在视频区活动（桌面端）→ 唤出 mpv OSC 沉浸式控制条。 */
    fun notifyMouseActivity() {
        playerCoreManager.showOsc()
    }

    fun togglePlayPause() {
        playerCoreManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerCoreManager.seekTo(positionMs)
    }

    fun fastSeek(positionMs: Long) {
        playerCoreManager.fastSeek(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        playerCoreManager.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun setPlayMode(mode: PlayMode) {
        playerCoreManager.setPlayMode(mode)
        _playMode.value = mode
    }

    fun setAspectMode(mode: AspectMode) {
        playerCoreManager.setAspectMode(mode)
        _aspectMode.value = mode
    }

    fun switchQuality(index: Int) {
        playerCoreManager.switchQuality(index)
        _currentQualityIndex.value = index
    }

    /** 当前正在播放/已解析的媒体地址，供「下载本集」使用。 */
    fun currentPlayableUrl(): String = playerCoreManager.resolvedUrl ?: currentVideoUrl

    fun playPrevEpisode() {
        playerCoreManager.playPrevEpisode()
        syncEpisodeState()
    }

    fun playNextEpisode() {
        playerCoreManager.playNextEpisode()
        syncEpisodeState()
    }

    /** 直接跳到第 [index] 集（选集面板用） */
    fun playEpisodeAt(index: Int) {
        if (index !in localEpisodeUrls.indices) return
        playerCoreManager.playEpisodeAtIndex(index)
        syncEpisodeState()
    }

    fun setVolume(vol: Float) {
        playerCoreManager.setVolume(vol)
        _volume.value = vol
    }

    fun setBrightness(bright: Float) {
        playerCoreManager.setBrightness(bright)
        _brightness.value = bright
    }

    fun setSurface(surface: Any?) {
        playerCoreManager.setSurface(surface)
    }

    fun toggleSubtitles() {
        playerCoreManager.toggleSubtitles()
        _showSubtitles.value = !_showSubtitles.value
    }

    fun setSubtitleTrack(index: Int) {
        playerCoreManager.setSubtitleTrack(index)
        _currentSubtitleTrack.value = index
        _showSubtitles.value = true
    }

    /** 调节字幕同步偏移（毫秒），正值为延后，负值为提前 */
    fun adjustSubtitleOffset(deltaMs: Long) {
        val newOffset = (_subtitleOffsetMs.value + deltaMs).coerceIn(-5000L, 5000L)
        _subtitleOffsetMs.value = newOffset
        subtitleService.setSyncOffset(newOffset)
    }

    /** 重置字幕偏移为 0 */
    fun resetSubtitleOffset() {
        _subtitleOffsetMs.value = 0L
        subtitleService.setSyncOffset(0L)
    }

    fun lockScreen() {
        _isLocked.value = true
    }

    fun unlockScreen() {
        _isLocked.value = false
    }

    fun retryPlayback() {
        _lastError.value = null
        playerCoreManager.retryPlayback()
    }

    /**
     * 手动更新播放位置。
     *
     * 正常情况下由 [initialize] 中注册的 `onProgress` 回调自动驱动，
     * 此方法保留给外部（如测试或宿主）主动同步时使用。
     */
    fun updatePosition(positionMs: Long) {
        _position.value = positionMs
    }

    fun updateDuration(durationMs: Long) {
        _duration.value = durationMs
    }

    fun updateBuffered(percentage: Int) {
        _bufferedPercentage.value = percentage
    }

    // ===== 播放进度持久化 =====

    private fun startProgressSaving() {
        stopProgressSaving()
        progressSaveJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(5000) // 每 5 秒保存一次
                    saveCurrentProgress()
                    // HLS 变体是异步解析的：轮询时顺带同步画质列表（有变化才写入）
                    val labels = playerCoreManager.getQualityLabels()
                    if (labels != _qualityLabels.value) {
                        _qualityLabels.value = labels
                        _currentQualityIndex.value = playerCoreManager.getCurrentQualityIndex()
                    }
                }
            }
    }

    private fun stopProgressSaving() {
        progressSaveJob?.cancel()
        progressSaveJob = null
    }

    /**
     * 写观看历史。此前【全项目没有任何地方调用过 insertOrReplace】——
     * 播放进度表一直在写、观看历史表永远是空的（用户实测反馈）。
     * 在进度保存的同一个节拍里顺带刷新，播放中历史就在持续更新。
     */
    private fun recordWatchHistory() {
        val session = sessionStore.session.value ?: return
        if (session.vodId.isBlank()) return
        try {
            watchHistoryDao.insertOrReplace(
                vodId = session.vodId,
                vodName = session.vodName,
                vodPic = session.vodPic,
                sourceKey = session.sourceKey,
                episodeName = _currentEpisodeName.value.ifBlank { null },
                lastWatchTime = Clock.System.now().toEpochMilliseconds(),
            )
        } catch (e: Exception) {
            logger.w { "Failed to record watch history: ${e.message}" }
        }
    }

    private fun saveCurrentProgress() {
        if (currentVideoUrl.isEmpty()) return
        val pos = _position.value
        val dur = _duration.value
        if (dur <= 0) return
        recordWatchHistory()
        try {
            playbackProgressDao.insertOrReplace(
                videoUrl = currentVideoUrl,
                position = pos,
                duration = dur,
                lastPlayTime = Clock.System.now().toEpochMilliseconds(),
            )
        } catch (e: Exception) {
            logger.w { "Failed to save playback progress: ${e.message}" }
        }
    }

    // ===== 字幕实时更新 =====

    private fun startSubtitleUpdates() {
        stopSubtitleUpdates()
        subtitleUpdateJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(100) // 每 100ms 更新一次字幕（±50ms 容差内）
                    updateSubtitleText()
                }
            }
    }

    private fun stopSubtitleUpdates() {
        subtitleUpdateJob?.cancel()
        subtitleUpdateJob = null
    }

    private fun updateSubtitleText() {
        if (!_showSubtitles.value || _subtitleTracks.value.isEmpty()) {
            _currentSubtitleText.value = null
            return
        }

        val trackIndex = _currentSubtitleTrack.value
        val tracks = _subtitleTracks.value
        if (trackIndex < 0 || trackIndex >= tracks.size) {
            _currentSubtitleText.value = null
            return
        }

        val track = tracks[trackIndex]
        val entry = subtitleService.getSubtitleAt(track.entries, _position.value)
        _currentSubtitleText.value = entry?.text
    }

    // ===== 剧集状态同步 =====

    /**
     * 把 [PlayerCoreManager] 的剧集状态同步到 UI。
     *
     * ⚠️ 已改为**事件驱动**（`onEpisodeChanged`）：旧实现每 500ms 轮询一次
     * [PlayerCoreManager]，上/下一集与自动连播后标题、选集高亮都会迟滞半秒。
     */
    private fun syncEpisodeState() {
        val index = playerCoreManager.getCurrentEpisodeIndex()
        _currentEpisodeIndex.value = index
        _currentEpisodeName.value = playerCoreManager.getCurrentEpisodeName()
        _hasPrevEpisode.value = playerCoreManager.getHasPrevEpisode()
        _hasNextEpisode.value = playerCoreManager.getHasNextEpisode()
        // 进度按"当前这一集的地址"落库，否则切集后进度会写到第一集头上
        localEpisodeUrls.getOrNull(index)?.let { currentVideoUrl = it }
    }

    // ==================== 音视频轨 ====================

    private fun refreshTracks() {
        _audioTracks.value = playerCoreManager.getAudioTracks()
        _videoTracks.value = playerCoreManager.getVideoTracks()
    }

    fun selectAudioTrack(trackId: String) {
        if (playerCoreManager.setAudioTrack(trackId)) refreshTracks()
    }

    fun selectVideoTrack(trackId: String) {
        if (playerCoreManager.setVideoTrack(trackId)) refreshTracks()
    }

    // ==================== TVBox 按需解析 ====================

    /**
     * 解析并切换一集（TVBox 会话）。
     *
     * TVBox 的剧集标识不是地址，必须先经 `playerContent` 解析出真实地址与防盗链头。
     * 结果会回写 [PlaybackSessionStore]，切集/连播对 TVBox 源因此同样可用。
     *
     * 解析失败时写入 [lastError] 让界面提示，并由调用方决定是否阻断切集。
     */
    suspend fun resolveEpisode(
        session: PlaybackSession,
        index: Int,
    ): ResolvedPlay? {
        val rawId = session.episodes.getOrNull(index)?.url ?: return null
        if (rawId.isBlank()) return null
        val flag =
            session.playSources
                .getOrNull(session.sourceIndex)
                ?.name
                .orEmpty()

        // 已解析过就直接复用，避免重复打 playerContent
        session.resolved[rawId]?.let { return it }

        return try {
            val resolved =
                resolver.resolve(
                    sourceKey = session.sourceKey,
                    flag = flag,
                    episodeId = rawId,
                    siteResolver = { key -> sourceRepository.findByKey(key) ?: CmsApiSite.findByKey(key) },
                )
            sessionStore.recordResolved(rawId, resolved)
            resolved
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e { "Resolve episode $index failed: ${e.message}" }
            _lastError.value = e.message ?: "解析这一集的播放地址失败。"
            null
        }
    }

    /**
     * 把「按需解析剧集」装到 [PlayerCoreManager]。
     *
     * TVBox 会话的 `episodeUrls` 是待解析标识，切集/自动连播发生在管理器内部，
     * 因此必须由管理器在装载前回调这里换出真实地址；CMS 会话直通（标识即地址）。
     */
    fun installEpisodeResolver(session: PlaybackSession?) {
        if (session == null || !session.isResolvable || session.sourceKey.isBlank()) {
            playerCoreManager.episodeResolver = null
            return
        }
        playerCoreManager.episodeResolver = { index ->
            resolveEpisode(session, index)?.let { resolved ->
                if (resolved.headers.isEmpty()) {
                    EpisodeSource(url = resolved.url)
                } else {
                    EpisodeSource(url = resolved.url, headers = resolved.headers)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dispose() // Idempotent — safe to call multiple times
    }
}
