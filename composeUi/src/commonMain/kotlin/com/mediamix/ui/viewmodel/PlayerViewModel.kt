package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediamix.shared.database.PlaybackProgressDao
import com.mediamix.shared.player.AspectMode
import com.mediamix.shared.player.PlayerCoreManager
import com.mediamix.shared.player.PlayerState
import com.mediamix.shared.player.PlayMode
import com.mediamix.shared.player.SubtitleService
import com.mediamix.shared.player.SubtitleTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import co.touchlab.kermit.Logger

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

    private val _aspectMode = MutableStateFlow(AspectMode.ORIGINAL)
    val aspectMode: StateFlow<AspectMode> = _aspectMode.asStateFlow()

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

    private val _speedOptions = MutableStateFlow(
        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f)
    )
    val speedOptions: StateFlow<List<Float>> = _speedOptions.asStateFlow()

    // ===== Subtitle Text (实时字幕文本) =====

    private val _currentSubtitleText = MutableStateFlow<String?>(null)
    val currentSubtitleText: StateFlow<String?> = _currentSubtitleText.asStateFlow()

    /** 字幕同步偏移量（毫秒），正值延后、负值提前 */
    private val _subtitleOffsetMs = MutableStateFlow(0L)
    val subtitleOffsetMs: StateFlow<Long> = _subtitleOffsetMs.asStateFlow()

    // ===== Internal =====

    private var currentVideoUrl: String = ""
    private var progressSaveJob: Job? = null
    private var subtitleUpdateJob: Job? = null
    private var disposed = false

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
    ) {
        currentVideoUrl = url

        // 恢复播放进度
        val savedProgress = try {
            playbackProgressDao.getByVideoUrl(url)
        } catch (e: Exception) {
            logger.w { "Failed to load playback progress: ${e.message}" }
            null
        }

        playerCoreManager.initialize(
            url = url, title = title,
            episodeIndex = episodeIndex,
            episodeNames = episodeNames,
            episodeUrls = episodeUrls,
            qualityLabels = qualityLabels,
            qualityUrls = qualityUrls,
            subtitleUrls = subtitleUrls,
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
        startProgressSaving()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        saveCurrentProgress()
        stopProgressSaving()
        stopSubtitleUpdates()
        playerCoreManager.dispose()
    }

    fun togglePlayPause() { playerCoreManager.togglePlayPause() }
    fun seekTo(positionMs: Long) { playerCoreManager.seekTo(positionMs) }
    fun fastSeek(positionMs: Long) { playerCoreManager.fastSeek(positionMs) }

    fun setPlaybackSpeed(speed: Float) {
        playerCoreManager.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun setPlayMode(mode: PlayMode) { playerCoreManager.setPlayMode(mode); _playMode.value = mode }
    fun setAspectMode(mode: AspectMode) { playerCoreManager.setAspectMode(mode); _aspectMode.value = mode }
    fun switchQuality(index: Int) { playerCoreManager.switchQuality(index); _currentQualityIndex.value = index }
    fun playPrevEpisode() {
        playerCoreManager.playPrevEpisode()
        _currentEpisodeIndex.value = playerCoreManager.getCurrentEpisodeIndex()
        _hasPrevEpisode.value = playerCoreManager.getHasPrevEpisode()
        _hasNextEpisode.value = playerCoreManager.getHasNextEpisode()
    }
    fun playNextEpisode() {
        playerCoreManager.playNextEpisode()
        _currentEpisodeIndex.value = playerCoreManager.getCurrentEpisodeIndex()
        _hasPrevEpisode.value = playerCoreManager.getHasPrevEpisode()
        _hasNextEpisode.value = playerCoreManager.getHasNextEpisode()
    }

    fun setVolume(vol: Float) { playerCoreManager.setVolume(vol); _volume.value = vol }
    fun setBrightness(bright: Float) { playerCoreManager.setBrightness(bright); _brightness.value = bright }
    fun setSurface(surface: Any?) { playerCoreManager.setSurface(surface) }

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

    fun lockScreen() { _isLocked.value = true }
    fun unlockScreen() { _isLocked.value = false }

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
        progressSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(5000) // 每 5 秒保存一次
                saveCurrentProgress()
            }
        }
    }

    private fun stopProgressSaving() {
        progressSaveJob?.cancel()
        progressSaveJob = null
    }

    private fun saveCurrentProgress() {
        if (currentVideoUrl.isEmpty()) return
        val pos = _position.value
        val dur = _duration.value
        if (dur <= 0) return
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
        subtitleUpdateJob = viewModelScope.launch {
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

    override fun onCleared() {
        super.onCleared()
        dispose() // Idempotent — safe to call multiple times
    }
}
