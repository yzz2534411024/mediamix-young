package com.mediamix.shared.player

import co.touchlab.kermit.Logger
import com.mediamix.shared.core.PowerManager
import com.mediamix.shared.core.PowerMode
import com.mediamix.shared.network.ThroughputPrediction
import com.mediamix.shared.player.engines.*
import com.russhwolf.settings.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock

// ============================================================================
// Constants
// ============================================================================

private const val FIRST_FRAME_TIMEOUT_MS = 10_000L
private const val PRELOAD_TRIGGER_POSITION = 0.8
private const val AV_SYNC_CHECK_INTERVAL_MS = 2_000L

/**
 * AV 同步基线的最大有效年龄：超过就不再作为漂移依据，直接重新取基线。
 *
 * 位置上报间隔是 250ms，2s 一次检查时基线应当是「刚刚」的；一旦超过 5s，
 * 说明中间发生过暂停/卡顿/位置未上报 —— 此时 elapsed 已经不能代表真实播放进度，
 * 继续用它推算会把「暂停时长」算成漂移并触发无意义的 seek。
 */
private const val MAX_AV_SYNC_BASELINE_AGE_MS = 5_000L
private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L

/** 播放进度上报间隔（驱动进度条 / 时长 / 字幕同步），需远高于持久化频率 */
private const val PROGRESS_REPORT_INTERVAL_MS = 250L
private const val BACKGROUND_AV_SYNC_INTERVAL_MS = 10_000L
private const val BACKGROUND_PROGRESS_SAVE_INTERVAL_MS = 30_000L
private const val REOPEN_DELAY_MS = 300L
private const val AV_SYNC_SPEED_RECOVERY_MS = 600L
private const val SEEK_OVERLAY_DURATION_MS = 800L
private const val SPEED_INDICATOR_DURATION_MS = 3_000L

// ============================================================================
// Video parser interface (for URL wrapping)
// ============================================================================

/**
 * Video parser interface — wraps original URL for different source parsers.
 */
interface VideoParser {
    val name: String

    fun buildUrl(originalUrl: String): String
}

// ============================================================================
// Progress save callback
// ============================================================================

/**
 * Callback interface for persisting playback progress.
 * Replaces Drift AppDatabase dependency.
 */
interface ProgressSaveCallback {
    fun saveProgress(
        videoUrl: String,
        positionMs: Long,
        durationMs: Long,
        lastPlayTimeMs: Long,
    )

    suspend fun getProgress(videoUrl: String): PlaybackProgress?
}

data class PlaybackProgress(
    val videoUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayTimeMs: Long,
)

// ============================================================================
// PlayerCoreManager — core orchestrator
// ============================================================================

/**
 * Player core manager — orchestrates all playback sub-modules.
 *
 * Migrated from player_core_manager.dart (~955 lines).
 *
 * Core responsibilities:
 * 1. Player lifecycle (init / dispose)
 * 2. Video opening via CacheEngine (cache -> proxy -> direct)
 * 3. Episode switching (prev / next / jump)
 * 4. Quality switching (multi-resolution URL management)
 * 5. Play mode (sequential / loop single / loop all)
 * 6. Playback speed (0.25x - 3.0x, 12 steps)
 * 7. Subtitle management (multi-track loading, track switching)
 * 8. AV sync monitoring (every 2s, tiered correction)
 * 9. Progress saving (every 10s via callback)
 * 10. Error handling (delegated to PlaybackErrorHandler, 9 recovery actions)
 * 11. ABR coordination (throughput prediction -> ABRController)
 * 12. Buffer management (buffer state -> BufferManager)
 */
class PlayerCoreManager(
    private val playerEngine: PlayerEngine,
    private val cacheEngine: CacheEngine,
    private val errorHandler: PlaybackErrorHandler,
    private val metricsEngine: MetricsEngine,
    private val abrController: ABRController,
    private val bufferManager: BufferManager,
    private val subtitleService: SubtitleService,
    private val powerManager: PowerManager,
    private val settings: Settings,
) : PlayerEngineListener {
    private val logger = Logger.withTag("PlayerCoreManager")
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lifecycleMutex = Mutex()

    @Volatile
    private var isDisposed = false

    var onFirstFrame: ((FirstFrameEvent) -> Unit)? = null
    var onError: ((ErrorEvent) -> Unit)? = null

    /**
     * 视频宽高比变化回调（w/h）。
     *
     * UI 层据此约束画面尺寸 —— TextureView 不会自动适配视频比例，
     * 不约束就是默认拉伸填满（画面比例失真，实测用户反馈「自适应太大」）。
     */
    var onVideoAspectRatioChanged: ((Float) -> Unit)? = null

    /** 引擎是否自行处理画面比例（桌面 mpv = true，UI 层不再约束尺寸）。 */
    val engineHandlesAspectInternally: Boolean get() = playerEngine.handlesAspectInternally

    /** 首次播放前准备原生运行库的进度（0..1）—— UI 用来提示「正在准备播放组件」。 */
    var onNativeRuntimeProgress: ((Float) -> Unit)? = null

    /** 运行库已就绪（UI 收起提示）。 */
    var onNativeRuntimeReady: (() -> Unit)? = null

    /**
     * 最近一次未被消费的错误。
     *
     * 播放页的 onError 回调是在 viewModel.initialize() 时才注册的 —— 若初始化/装载
     * 在注册前就失败（实测桌面端缺 mpv 运行库会这样），错误会**永久丢失**，
     * 用户只看到白屏（无错误面板、无提示）。缓存一份供上层注册后补发。
     */
    @Volatile
    private var pendingError: ErrorEvent? = null

    private fun emitError(event: ErrorEvent) {
        pendingError = event
        onError?.invoke(event)
    }

    /** 取出并清空未消费的错误（供 PlayerViewModel 注册回调后补发到 UI）。 */
    fun consumePendingError(): ErrorEvent? = pendingError.also { pendingError = null }
    var onQualitySuggestion: ((QualitySuggestionEvent) -> Unit)? = null
    var onProgressResume: ((ProgressResumeEvent) -> Unit)? = null
    var onQualityAutoSwitch: ((QualityAutoSwitchEvent) -> Unit)? = null
    var onSubtitlesLoaded: ((List<SubtitleTrack>) -> Unit)? = null
    var onBufferingChanged: ((Boolean) -> Unit)? = null
    var onPlayerStateChanged: ((PlayerState) -> Unit)? = null
    var onNotifyPreloadBuffering: ((Boolean) -> Unit)? = null

    /**
     * 播放进度上报：positionMs / durationMs / bufferedPercent(0..100)。
     *
     * ⚠️ PlayerEngine 不提供周期性的 position 回调（ExoPlayer 仅在 seek 时触发
     * `onPositionDiscontinuity`），而 UI 的进度条、时长、字幕同步、进度记忆
     * 全部依赖它，因此由 [startProgressReportTimer] 以 250ms 间隔主动推送。
     */
    var onProgress: ((positionMs: Long, durationMs: Long, bufferedPercent: Int) -> Unit)? = null

    /**
     * 剧集变化（切集 / 自动连播）。
     *
     * 此前没有这个回调，UI 只能靠 500ms 轮询 [getCurrentEpisodeIndex] 猜测 ——
     * 上/下一集与自动连播后标题、选集高亮会迟滞半秒。改为事件驱动后即时同步。
     */
    var onEpisodeChanged: ((index: Int, name: String) -> Unit)? = null

    /** 音轨 / 视频轨列表变化（引擎上报轨道后触发）。 */
    var onTracksChanged: ((audio: List<TrackInfo>, video: List<TrackInfo>) -> Unit)? = null

    /**
     * 按需解析「本集真实播放地址」。
     *
     * TVBox 源的 `episodeUrls` 存的是**待解析标识**（`playerContent` 的入参），
     * 不是地址。切集/连播/失败回退前都要先过这里换成可直接播放的 URL 与请求头；
     * CMS 源由调用方直通返回（标识即地址），因此不会多花时间。
     *
     * 返回 null 表示解析失败 —— 此时不装载，避免拿着无效标识去请求。
     */
    var episodeResolver: (suspend (index: Int) -> EpisodeSource?)? = null

    // External callbacks
    var progressSaveCallback: ProgressSaveCallback? = null
    var throughputProvider: (() -> ThroughputPrediction)? = null
    var bandwidthProvider: (() -> Double)? = null

    // ========================================================================
    // Internal state
    // ========================================================================

    private var isInitialized = false
    private var isInitializing = false
    private var hasTriedDirectUrl = false

    // Video parser
    private var activeParser: VideoParser? = null
    private var pendingParser: VideoParser? = null

    // Playback state
    private var playbackSpeed = 1.0f
    private val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f)
    private var skipInterval = 10
    private val skipIntervals = listOf(5, 10, 30, 60)
    private var playMode = PlayMode.SEQUENTIAL
    private var aspectMode = AspectMode.ADAPTIVE
    private var currentEpisodeIndex = 0
    private var currentEpisodeName = ""
    private var currentQualityIndex = 0
    private var qualityLabels: List<String> = emptyList()
    private var qualityUrls: List<String> = emptyList()
    private var volume = 1.0f
    private var brightness = 0.5f

    /** 省电模式下禁用「预加载下一集」（由 [applyPowerMode] 维护）。 */
    private var preloadDisabled = false

    // Init params
    private var title = ""
    private var url = ""
    private var episodeNames: List<String>? = null
    private var episodeUrls: List<String>? = null
    private var subtitleUrls: List<String>? = null

    /** 播放请求头（UA / Referer 等），切集时同样会带上 */
    private var requestHeaders: Map<String, String>? = null

    /**
     * 按集请求头：`剧集下标 -> headers`。
     *
     * TVBox 蜘蛛对不同的集/线路可能给出不同的防盗链头，`:requestHeaders`
     * 只放「初始那一集」的头；切集后若继续用它们，很容易 403。
     * 由播放页在解析出结果时通过 [setEpisodeHeaders] 登记。
     */
    private val episodeHeaders = mutableMapOf<Int, Map<String, String>>()

    /** 同一集在其它播放线路里的候选地址，当前线路失败时按序回退 */
    private var fallbackUrls: List<String>? = null

    /** 已经用掉的候选地址下标 */
    private var fallbackIndex = 0

    /** 软解优先（来自设置页的解码模式） */
    private var preferSoftwareDecoding = false

    // Subtitles
    private var subtitleTracks: List<SubtitleTrack> = emptyList()
    private var showSubtitles = true
    private var currentSubtitleTrack = 0

    // Hardware decoding / AV sync
    private var hardwareDecodingEnabled = true
    private var avSyncCheckJob: Job? = null
    private var lastVideoPositionMs: Long = 0L
    private var lastPositionUpdateTimeMs: Long = 0L
    private var avSyncCorrectionCount = 0
    private var lastAVSyncCorrectionMs: Long? = null

    // Periodic jobs
    private var progressSaveJob: Job? = null
    private var progressReportJob: Job? = null
    private var firstFrameTimeoutJob: Job? = null
    private var seekOverlayJob: Job? = null
    private var speedIndicatorJob: Job? = null

    // Seek
    private var isSeeking = false
    private var lastIsLowBuffer: Boolean? = null

    // Preload
    private var hasTriggeredNextEpisodePreload = false
    private var preloadDepth = 1

    // Background / PiP
    private var isInBackground = false
    private var isInPipMode = false
    private var pipSavedQualityIndex: Int? = null
    private var powerMode = PowerMode.BALANCED

    // Loading state
    private var isLoading = false
    private var loadingText = "Loading..."
    private var bufferPercent = 0.0
    private var networkSpeedText = ""

    // Error / cache
    private var lastError: String? = null
    private var fallbackQuality: String? = null

    /** 当前媒体真正装载的地址（"下载本集"与诊断展示用）。 */
    val resolvedUrl: String
        get() = resolvedMediaUrl

    private var resolvedMediaUrl = ""

    // Surface lifecycle — pending surface for race condition handling
    private var pendingSurface: Any? = null

    // ========================================================================
    // Public getters
    // ========================================================================

    val playerState: PlayerState get() = playerEngine.getPlayerState()

    fun getPlaybackSpeed(): Float = playbackSpeed

    fun getSpeedOptions(): List<Float> = speedOptions

    fun getSkipInterval(): Int = skipInterval

    fun getSkipIntervals(): List<Int> = skipIntervals

    fun getPlayMode(): PlayMode = playMode

    fun getAspectMode(): AspectMode = aspectMode

    fun getCurrentEpisodeIndex(): Int = currentEpisodeIndex

    fun getCurrentEpisodeName(): String = currentEpisodeName

    fun getCurrentQualityIndex(): Int = currentQualityIndex

    fun getQualityLabels(): List<String> = qualityLabels

    fun getQualityUrls(): List<String> = qualityUrls

    fun hasQualityOptions(): Boolean = qualityUrls.size > 1

    fun getVolume(): Float = volume

    fun getBrightness(): Float = brightness

    fun getSubtitleTracks(): List<SubtitleTrack> = subtitleTracks

    fun getShowSubtitles(): Boolean = showSubtitles

    fun getCurrentSubtitleTrack(): Int = currentSubtitleTrack

    fun getSubtitleService(): SubtitleService = subtitleService

    fun getHardwareDecodingEnabled(): Boolean = hardwareDecodingEnabled

    fun getBufferManager(): BufferManager = bufferManager

    fun getAbrController(): ABRController = abrController

    fun getIsSeeking(): Boolean = isSeeking

    fun getIsBuffering(): Boolean = if (isInitialized) metricsEngine.isBuffering else false

    fun getIsLoading(): Boolean = isLoading

    fun getLoadingText(): String = loadingText

    fun getBufferPercent(): Double = bufferPercent

    fun getNetworkSpeedText(): String = networkSpeedText

    fun getIsWaitingForNetwork(): Boolean = errorHandler.isWaitingForNetwork

    fun getLastError(): String? = lastError

    fun getIsUsingCache(): Boolean = if (isInitialized) cacheEngine.isUsingCache else false

    fun getFallbackQuality(): String? = fallbackQuality

    fun getLastVideoPositionMs(): Long = lastVideoPositionMs

    fun getIsInBackground(): Boolean = isInBackground

    fun getIsInPipMode(): Boolean = isInPipMode

    fun getPowerMode(): PowerMode = powerMode

    fun getHasPrevEpisode(): Boolean = episodeUrls != null && currentEpisodeIndex > 0

    fun getHasNextEpisode(): Boolean = episodeUrls != null && currentEpisodeIndex < (episodeUrls?.size ?: 0) - 1

    // ========================================================================
    // 音视频轨（引擎侧已实现，此前中间层没有透传方法 → UI 无入口）
    // ========================================================================

    /** 取当前媒体可选的音轨；引擎不可用时返回空列表而不是抛异常。 */
    fun getAudioTracks(): List<TrackInfo> =
        if (isDisposed) {
            emptyList()
        } else {
            runCatching { playerEngine.getAudioTracks() }.getOrDefault(emptyList())
        }

    /** 取当前媒体可选的视频轨（画质档位）。 */
    fun getVideoTracks(): List<TrackInfo> =
        if (isDisposed) {
            emptyList()
        } else {
            runCatching { playerEngine.getVideoTracks() }.getOrDefault(emptyList())
        }

    /** 切换音轨；成功返回 true。 */
    fun setAudioTrack(trackId: String): Boolean {
        if (isDisposed) return false
        val ok = runCatching { playerEngine.setAudioTrack(trackId) }.getOrDefault(false)
        if (ok) notifyTracksChanged()
        return ok
    }

    /** 切换视频轨；成功返回 true。 */
    fun setVideoTrack(trackId: String): Boolean {
        if (isDisposed) return false
        val ok = runCatching { playerEngine.setVideoTrack(trackId) }.getOrDefault(false)
        if (ok) notifyTracksChanged()
        return ok
    }

    /** 登记某一集的请求头（切集 / 重新装载时按当前集取用）。 */
    fun setEpisodeHeaders(
        index: Int,
        headers: Map<String, String>,
    ) {
        if (headers.isEmpty()) {
            episodeHeaders.remove(index)
        } else {
            episodeHeaders[index] = headers
        }
    }

    /** 当前这一集应该用的请求头：优先按集登记的，其次初始集。 */
    private fun headersForCurrentEpisode(): Map<String, String>? =
        episodeHeaders[currentEpisodeIndex]?.takeIf { it.isNotEmpty() } ?: requestHeaders

    private fun notifyEpisodeChanged() {
        onEpisodeChanged?.invoke(currentEpisodeIndex, currentEpisodeName)
    }

    private fun notifyTracksChanged() {
        onTracksChanged?.invoke(getAudioTracks(), getVideoTracks())
    }

    fun getIsInitialized(): Boolean = isInitialized

    fun getActiveParser(): VideoParser? = activeParser

    // ========================================================================
    // Initialization
    // ========================================================================

    /**
     * Switch video parser (null = direct connection).
     */
    fun setParser(parser: VideoParser?) {
        if (!isInitialized) {
            pendingParser = parser
            return
        }
        activeParser = parser
        pendingParser = null
        hasTriedDirectUrl = false
        playerEngine.stop()
        scope.launch {
            delay(REOPEN_DELAY_MS)
            if (isDisposed) return@launch
            openVideoWithCacheCheck(url, "${title}_$currentEpisodeIndex")
        }
    }

    /**
     * Initialize the player with video parameters.
     */
    fun initialize(
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
        isInitializing = true
        this.url = url
        this.title = title
        this.episodeNames = episodeNames
        this.episodeUrls = episodeUrls
        this.subtitleUrls = subtitleUrls
        this.requestHeaders = headers
        this.preferSoftwareDecoding = preferSoftwareDecoding
        this.fallbackUrls = fallbackUrls?.filter { it.isNotBlank() && it != url }
        this.fallbackIndex = 0
        hasTriedDirectUrl = false
        isDisposed = false

        // 初始化/重新初始化播放器引擎
        try {
            // ⚠️ 解码模式必须在 initialize() **之前**设置：Android 侧的渲染器工厂
            // 是在 initialize() 里构建的，之后再设不会生效。
            playerEngine.setDecodeMode(preferSoftwareDecoding)
            playerEngine.initialize()
        } catch (t: Throwable) {
            // ⚠️ 必须捕获 Throwable 而不是 Exception。
            // Desktop 缺 mpv 运行库时抛的是 UnsatisfiedLinkError，Android 曾经
            // 引用 com.sun.net.httpserver 时抛的是 NoClassDefFoundError —— 两者都继承
            // Error 而非 Exception，用 catch (Exception) 会漏掉并直接把进程打挂。
            logger.e("PlayerEngine.initialize() failed: ${t.message}")
            // 把失败暴露到 UI，避免用户只看到黑屏
            emitError(
                ErrorEvent(
                    message = "播放器初始化失败：${t.message}",
                    hasNextEpisode = false,
                ),
            )
            isInitializing = false
            return
        }

        // 重新应用 pending Surface（引擎重建后需重新绑定）
        pendingSurface?.let { surface ->
            try {
                playerEngine.setSurface(surface)
            } catch (e: Exception) {
                logger.e("Re-apply pending surface failed: $e")
            }
        }

        // 重建协程作用域（dispose 后可能已取消）
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // Configure hardware decoding (non-blocking)
        scope.launch(Dispatchers.Default) {
            configureHardwareDecoding()
        }

        // Set up callbacks
        // Note: BufferManager.onBufferStateChanged is set at construction time
        abrController.onQualityChanged = { level -> onAbrQualityChanged(level) }

        // Set playback parameters
        currentEpisodeIndex = episodeIndex ?: 0
        currentEpisodeName = title
        this.qualityLabels = qualityLabels ?: emptyList()
        this.qualityUrls = qualityUrls ?: emptyList()
        currentQualityIndex = 0
        (errorHandler as? PlaybackErrorHandlerImpl)?.qualityCount = this.qualityUrls.size

        // Set PlayerEngine listener
        playerEngine.setListener(this)

        // Start metrics session
        val videoId = "${title}_${episodeIndex ?: 0}"
        metricsEngine.startSession(videoId)
        metricsEngine.recordEvent(MetricsEvent.PLAY_START)

        // Open video and start periodic tasks
        scope.launch {
            // ★ 首次播放前准备原生运行库（桌面端 mpv 按需下载，约 45 MB）。
            // 必须在装载之前：MpvLib 加载 dll 是同步调用，库里没有就直接抛。
            // 后台预热通常已经下好，这里兜底重试并负责失败提示。
            try {
                ensureNativeRuntime { p -> onNativeRuntimeProgress?.invoke(p) }
                onNativeRuntimeReady?.invoke()
            } catch (t: Throwable) {
                logger.e { "原生运行库准备失败: ${t.message}" }
                emitError(
                    ErrorEvent(
                        "播放组件准备失败：${t.message ?: "未知错误"}\n" +
                            "可手动下载 libmpv-2.dll 放到：${nativeRuntimeHint()}",
                    ),
                )
                isInitializing = false
                return@launch
            }

            openVideoWithCacheCheck(url, videoId)

            // Set playback speed
            playerEngine.setPlaybackSpeed(playbackSpeed)

            // Start periodic tasks
            startProgressSaveTimer()
            startAvSyncCheckTimer()
            startProgressReportTimer()

            // First frame timeout fallback
            firstFrameTimeoutJob =
                scope.launch {
                    delay(FIRST_FRAME_TIMEOUT_MS)
                    if (isDisposed) return@launch
                    if (!metricsEngine.hasRecordedFirstFrame && url.isNotEmpty() && !hasTriedDirectUrl) {
                        hasTriedDirectUrl = true
                        logger.w("First frame timeout (10s), retrying with direct CDN url")
                        // ⚠️ 这里刻意不调 stop()：stop() 会把播放器打回 IDLE，
                        // 画面与进度一起中断，而且之后 `play()` 在 IDLE 下不会重新装载媒体，
                        // 用户看到的就是「卡住，点播放也没反应」。setMediaItem 本身就能替换媒体源。
                        delay(REOPEN_DELAY_MS)
                        if (isDisposed) return@launch
                        playerEngine.setSource(url, requestHeaders)
                    }
                }

            // Load preferences
            loadSkipInterval()
            detectPowerMode()

            isInitialized = true
            isInitializing = false

            // Apply pending parser if set before initialization
            pendingParser?.let {
                activeParser = it
                pendingParser = null
            }

            onPlayerStateChanged?.invoke(playerEngine.getPlayerState())
        }
    }

    // ========================================================================
    // PlayerEngineListener implementation
    // ========================================================================

    override fun onStateChanged(state: PlayerState) {
        if (isDisposed) return
        // 让 UI 的「缓冲中」指示跟随真实缓冲状态。
        // 原先 onBufferingChanged 只在网络质量探测那一处被调用过，从没和播放器的
        // BUFFERING 状态关联 —— 结果是视频卡在缓冲时界面显示的是「暂停」，
        // 对用户来说就跟「点了播放没反应」一模一样。
        onBufferingChanged?.invoke(state == PlayerState.BUFFERING)
        onPlayerStateChanged?.invoke(state)
    }

    override fun onPositionChanged(positionMs: Long) {
        if (isDisposed) return
        lastVideoPositionMs = positionMs
        lastPositionUpdateTimeMs = Clock.System.now().toEpochMilliseconds()

        if (!metricsEngine.hasRecordedFirstFrame && positionMs > 0L) {
            metricsEngine.markFirstFrameRecorded()
            metricsEngine.recordEvent(MetricsEvent.FIRST_FRAME)
            notifyFirstFrame()
            loadSubtitles()
        }
        checkPreloadTrigger(positionMs)
    }

    override fun onBufferChanged(bufferedPercent: Int) {
        if (isDisposed) return
        val bufferMs = bufferedPercent.toLong() * 1000
        bufferManager.updateBuffer(bufferMs)
        // Handle buffer state change (only trigger on state transitions)
        val isLowBuffer = bufferManager.getIsLowBuffer()
        if (lastIsLowBuffer != isLowBuffer) {
            lastIsLowBuffer = isLowBuffer
            onBufferStateChanged(isLowBuffer)
        }
        abrController.updateBuffer(bufferMs)

        try {
            throughputProvider?.let { abrController.updateThroughputPrediction(it()) }
        } catch (_: Exception) {
        }

        if (isLoading && bufferedPercent > 0) isLoading = false
        bufferPercent = bufferManager.bufferPercent
        notifyIfNotInitializing()
    }

    override fun onError(
        error: String,
        code: Int?,
    ) {
        if (isDisposed) return
        logger.e("Playback error: $error")
        metricsEngine.recordEvent(MetricsEvent.ERROR, errorMessage = error)
        handlePlaybackError(error)
    }

    override fun onFirstFrameRendered() {
        if (isDisposed) return
        if (!metricsEngine.hasRecordedFirstFrame) {
            metricsEngine.markFirstFrameRecorded()
            metricsEngine.recordEvent(MetricsEvent.FIRST_FRAME)
            notifyFirstFrame()
            loadSubtitles()
        }
    }

    override fun onVideoAspectRatioChanged(aspectRatio: Float) {
        if (isDisposed) return
        onVideoAspectRatioChanged?.invoke(aspectRatio)
    }

    override fun onPlaybackEnded() {
        if (isDisposed) return
        metricsEngine.recordEvent(MetricsEvent.PLAY_COMPLETE)
        onPlaybackCompleted()
    }

    // ========================================================================
    // Playback controls
    // ========================================================================

    fun play() {
        if (isDisposed) return
        playerEngine.play()
    }

    fun pause() {
        if (isDisposed) return
        playerEngine.pause()
    }

    /**
     * 播放 / 暂停。
     *
     * ⚠️ 不能只判断 `isPlaying()` 就调 `play()`：引擎处于 IDLE / ENDED / ERROR 时
     * （例如首帧超时兜底重新加载过、或上一集已播完），`play()` 只会把
     * `playWhenReady` 置真，**并不会重新装载媒体** —— 用户看到的就是
     * 「点了播放毫无反应、画面一直不动」。这种情况必须先重新 setSource。
     */
    fun togglePlayPause() {
        if (isDisposed) {
            // 管理器已被释放（页面回退后重进、或 ViewModel 被重建）时，
            // 所有控制方法都会在第一行直接 return —— 表现就是「点了播放毫无反应」。
            // 这里兜底重建一次。
            logger.w("togglePlayPause on a disposed manager, re-initializing")
            reinitializeCurrentSource()
            return
        }
        if (playerEngine.isPlaying()) {
            playerEngine.pause()
            return
        }
        when (playerEngine.getPlayerState()) {
            PlayerState.IDLE, PlayerState.ENDED, PlayerState.ERROR -> {
                logger.w("togglePlayPause: engine in ${playerEngine.getPlayerState()}, reloading source")
                reloadCurrentSource()
            }
            else -> playerEngine.play()
        }
    }

    /** 引擎已被释放时的兜底：拿现有参数重走一遍 initialize。 */
    private fun reinitializeCurrentSource() {
        val target = episodeUrls?.getOrNull(currentEpisodeIndex)?.takeIf { it.isNotBlank() } ?: url
        if (target.isBlank()) return
        initialize(
            url = target,
            title = title,
            episodeIndex = currentEpisodeIndex,
            episodeNames = episodeNames,
            episodeUrls = episodeUrls,
            subtitleUrls = subtitleUrls,
            headers = headersForCurrentEpisode(),
            preferSoftwareDecoding = preferSoftwareDecoding,
            fallbackUrls = fallbackUrls,
        )
    }

    /**
     * 用「当前正在看的这一集」重新装载媒体。
     *
     * 地址优先取剧集列表里当前下标那一项 —— 切集后 [url] 字段仍是上次
     * initialize 传入的地址，直接用它会跳回第一集。
     */
    private fun reloadCurrentSource() {
        val target =
            episodeUrls?.getOrNull(currentEpisodeIndex)?.takeIf { it.isNotBlank() }
                ?: url
        if (target.isBlank()) return
        // 引擎重建后 Surface 可能已失效，补绑一次再装载
        pendingSurface?.let { playerEngine.setSurface(it) }
        playerEngine.setSource(target, headersForCurrentEpisode())
    }

    fun seekTo(positionMs: Long) {
        if (isDisposed) return
        playerEngine.seekTo(positionMs)
    }

    fun fastSeek(positionMs: Long) {
        if (isDisposed || isSeeking) return
        isSeeking = true
        metricsEngine.recordEvent(MetricsEvent.SEEK)
        playerEngine.seekTo(positionMs)
        seekOverlayJob?.cancel()
        seekOverlayJob =
            scope.launch {
                delay(SEEK_OVERLAY_DURATION_MS)
                if (isDisposed) return@launch
                isSeeking = false
            }
    }

    fun setPlaybackSpeed(speed: Float) {
        if (isDisposed) return
        playbackSpeed = speed
        playerEngine.setPlaybackSpeed(speed)
        speedIndicatorJob?.cancel()
        speedIndicatorJob =
            scope.launch {
                delay(SPEED_INDICATOR_DURATION_MS)
                if (isDisposed) return@launch
            }
    }

    fun setSkipInterval(interval: Int) {
        skipInterval = interval
        saveSkipInterval()
    }

    fun setPlayMode(mode: PlayMode) {
        playMode = mode
    }

    fun setAspectMode(mode: AspectMode) {
        aspectMode = mode
        // 之前只改字段、从未下发引擎 —— 比例切换实际是空操作（实测）。
        // 桌面端由 mpv 原生参数处理；Android 端引擎 no-op，尺寸约束在 Compose 层。
        playerEngine.setAspectMode(mode)
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        // ⚠️ 不要乘 100：ExoPlayer 的 volume 是 0f..1f（mpv 也一样，引擎内部各自处理量纲）。
        // 之前 `volume * 100f` 会被 clamp 成 1f → 永远最大声，用户怎么调都没变化。
        if (!isDisposed) playerEngine.setVolume(volume)
    }

    fun setBrightness(b: Float) {
        brightness = b.coerceIn(0f, 1f)
    }

    /**
     * Set the video rendering surface.
     * @param surface platform-specific surface object (android.view.Surface on Android, Long HWND on Desktop)
     */
    fun setSurface(surface: Any?) {
        pendingSurface = surface
        if (isDisposed) return
        playerEngine.setSurface(surface)
    }

    fun skipForward() {
        if (isDisposed) return
        val pos = playerEngine.getPosition()
        seekTo(pos + skipInterval * 1000L)
    }

    fun skipBackward() {
        if (isDisposed) return
        val pos = playerEngine.getPosition()
        seekTo((pos - skipInterval * 1000L).coerceAtLeast(0L))
    }

    // ========================================================================
    // Episode switching
    // ========================================================================

    fun playPrevEpisode() {
        if (getHasPrevEpisode()) playEpisodeAtIndex(currentEpisodeIndex - 1)
    }

    fun playNextEpisode() {
        if (getHasNextEpisode()) playEpisodeAtIndex(currentEpisodeIndex + 1)
    }

    fun playEpisodeAtIndex(index: Int) {
        if (isDisposed) return
        val urls = episodeUrls ?: return
        val names = episodeNames ?: return
        if (index < 0 || index >= urls.size) return
        if (index >= names.size) return

        // 切集即通知 UI：等 500ms 轮询会让标题和选集高亮迟滞半拍
        currentEpisodeIndex = index
        currentEpisodeName = names[index]
        fallbackIndex = 0
        hasTriedDirectUrl = false
        hasTriggeredNextEpisodePreload = false
        isLoading = true
        loadingText = "Loading..."
        errorHandler.resetRetryCount()
        errorHandler.clearTriedQualityIndices()
        metricsEngine.startSession("${title}_$index")
        metricsEngine.recordEvent(MetricsEvent.PLAY_START)
        notifyEpisodeChanged()

        scope.launch {
            // TVBox：先把标识换成真实地址与请求头，再装载（见 [episodeResolver]）
            val source =
                if (episodeResolver != null) {
                    try {
                        episodeResolver?.invoke(index)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.e("Episode $index resolve failed: ${e.message}")
                        null
                    }
                } else {
                    null
                }
            if (isDisposed) return@launch
            if (source == null && episodeResolver != null) {
                // 解析失败：不要拿待解析的标识去播放（必然失败，还会把排查方向带偏）
                isLoading = false
                emitError(
                    ErrorEvent(message = "这一集的播放地址解析失败，换一集或换条线路试试。", hasNextEpisode = getHasNextEpisode()),
                )
                return@launch
            }
            val playableUrl = source?.url ?: urls[index]
            // url 始终指向「当前这一集的可播放地址」，重试/换线/首帧兜底都依赖它
            url = playableUrl
            source?.headers?.let { episodeHeaders[index] = it }
            openVideoWithCacheCheck(playableUrl, "${title}_$index", source?.headers ?: headersForCurrentEpisode())
            savePlaybackProgress()
        }
    }

    // ========================================================================
    // Quality switching
    // ========================================================================

    fun switchQuality(index: Int) {
        if (isDisposed || index < 0 || index >= qualityUrls.size) return
        val savedPos = playerEngine.getPosition()
        currentQualityIndex = index
        isLoading = true
        loadingText = "Switching quality..."
        scope.launch {
            openVideoWithCacheCheck(qualityUrls[index], "${title}_$index")
            delay(REOPEN_DELAY_MS)
            if (isDisposed) return@launch
            if (savedPos > 0L) playerEngine.seekTo(savedPos)
        }
    }

    // ========================================================================
    // Subtitle controls
    // ========================================================================

    fun toggleSubtitles() {
        showSubtitles = !showSubtitles
    }

    fun setSubtitleTrack(index: Int) {
        currentSubtitleTrack = index
        showSubtitles = true
    }

    fun hideSubtitles() {
        showSubtitles = false
    }

    // ========================================================================
    // Play mode
    // ========================================================================

    fun cyclePlayMode() {
        playMode =
            when (playMode) {
                PlayMode.SEQUENTIAL -> PlayMode.LOOP_ALL
                PlayMode.LOOP_ALL -> PlayMode.LOOP_SINGLE
                PlayMode.LOOP_SINGLE -> PlayMode.SEQUENTIAL
            }
    }

    // ========================================================================
    // Power mode
    // ========================================================================

    fun setPowerMode(mode: PowerMode) {
        powerMode = mode
        applyPowerMode()
    }

    // ========================================================================
    // Retry / Recovery
    // ========================================================================

    fun retryPlayback() {
        if (isDisposed) return
        errorHandler.stopNetworkRecoveryMonitoring()
        errorHandler.resetRetryCount()
        errorHandler.clearTriedQualityIndices()
        lastError = null
        scope.launch {
            openVideoWithCacheCheck(url, "${title}_$currentEpisodeIndex")
        }
    }

    fun resumeToPosition(positionMs: Long) {
        if (!isDisposed) playerEngine.seekTo(positionMs)
    }

    suspend fun checkPlaybackProgress(): Long? {
        val callback = progressSaveCallback ?: return null
        val currentUrl = episodeUrls?.getOrNull(currentEpisodeIndex) ?: url
        val progress = callback.getProgress(currentUrl) ?: return null
        return if (progress.positionMs > 5000) progress.positionMs else null
    }

    // ========================================================================
    // Preload
    // ========================================================================

    fun preloadAdjacentEpisodes() {
        if (isDisposed) return
        val urls = episodeUrls ?: return
        val depth = preloadDepth.coerceIn(1, 3)
        val indices =
            ((-depth)..depth)
                .map { currentEpisodeIndex + it }
                .filter { it >= 0 && it < urls.size }
                .filter { it != currentEpisodeIndex }
        cacheEngine.preloadAdjacentEpisodes(indices, title, urls, powerMode)
    }

    fun setPreloadDepth(depth: Int) {
        preloadDepth = depth.coerceIn(1, 3)
    }

    // ========================================================================
    // App lifecycle
    // ========================================================================

    fun onAppLifecycleStateChanged(background: Boolean) {
        if (isDisposed) return
        if (background) onAppBackgrounded() else onAppForegrounded()
    }

    // ========================================================================
    // Delegate methods
    // ========================================================================

    fun findNextUntriedQuality(): Int = errorHandler.findNextUntriedQuality()

    fun clearTriedQualityIndices() = errorHandler.clearTriedQualityIndices()

    fun resetRetryCount() = errorHandler.resetRetryCount()

    // ========================================================================
    // Dispose
    // ========================================================================

    fun dispose() {
        isDisposed = true
        isInitialized = false

        // 1. Disconnect listener first to prevent callbacks during teardown
        playerEngine.setListener(null)

        // 2. Cancel scope to stop all coroutines
        scope.cancel()

        pendingSurface = null

        metricsEngine.endSession()
        savePlaybackProgress()

        // Cancel all jobs
        progressSaveJob?.cancel()
        progressReportJob?.cancel()
        firstFrameTimeoutJob?.cancel()
        seekOverlayJob?.cancel()
        speedIndicatorJob?.cancel()
        avSyncCheckJob?.cancel()

        // Cancel preloads
        cacheEngine.cancelPreloads()

        // Dispose engines
        cacheEngine.dispose()
        errorHandler.dispose()
        metricsEngine.dispose()

        // 3. Release player engine last (after all callbacks/coroutines stopped)
        playerEngine.release()
    }

    // ========================================================================
    // Internal: Video opening with cache
    // ========================================================================

    private suspend fun openVideoWithCacheCheck(
        videoUrl: String,
        videoId: String,
        headers: Map<String, String>? = requestHeaders,
    ) {
        if (isDisposed) return
        val effectiveUrl = applyParser(videoUrl)
        try {
            val result =
                cacheEngine.resolveVideoUrlWithFallback(
                    effectiveUrl,
                    videoId,
                    preferredQuality = currentQualityLabel,
                )
            if (isDisposed) return
            fallbackQuality = result.fallbackQuality
            if (result.fallbackQuality != null) {
                logger.i("Quality fallback hit: requested $currentQualityLabel, using ${result.fallbackQuality}")
                onQualityAutoSwitch?.invoke(QualityAutoSwitchEvent("${result.fallbackQuality}(cache)"))
            }
            resolvedMediaUrl = result.url
            val sourceLabel = if (cacheEngine.isUsingCache) "local cache" else "network"
            val parserLabel = activeParser?.let { ", parser: ${it.name}" }.orEmpty()
            logger.i("Video URL resolved: $sourceLabel$parserLabel")
            playerEngine.setSource(resolvedMediaUrl, headers)
        } catch (e: Exception) {
            logger.w("Video URL resolution failed, using original URL: $e")
            resolvedMediaUrl = effectiveUrl
            playerEngine.setSource(effectiveUrl, headers)
        }
    }

    private val currentQualityLabel: String
        get() =
            if (qualityLabels.isEmpty() || currentQualityIndex >= qualityLabels.size) {
                "720p"
            } else {
                qualityLabels[currentQualityIndex]
            }

    private fun applyParser(originalUrl: String): String {
        val parser = activeParser ?: return originalUrl
        return parser.buildUrl(originalUrl)
    }

    /** 取下一条尚未尝试过的备用线路地址；用尽返回 null */
    private fun nextFallbackUrl(): String? {
        val candidates = fallbackUrls ?: return null
        while (fallbackIndex < candidates.size) {
            val candidate = candidates[fallbackIndex]
            fallbackIndex++
            if (candidate.isNotBlank() && candidate != url) return candidate
        }
        return null
    }

    // ========================================================================
    // Internal: Error handling — delegated to PlaybackErrorHandler
    // ========================================================================

    private fun handlePlaybackError(error: String) {
        if (isDisposed) return
        lastError = error
        val result =
            errorHandler.handleError(
                error = error,
                hardwareDecodingEnabled = hardwareDecodingEnabled,
                hasQualityOptions = hasQualityOptions(),
                currentQualityIndex = currentQualityIndex,
                lastPlaybackPositionMs = playerEngine.getPosition(),
            )

        when (result.action) {
            ErrorAction.DOWNGRADE_TO_SOFTWARE_DECODE -> {
                logger.w("Hardware decode failure, downgrading to software decode")
                onHardwareDecodeFailure(error)
            }
            ErrorAction.WAIT_FOR_NETWORK_RECOVERY -> {
                logger.w("Network-related playback interruption, waiting for recovery")
                errorHandler.startNetworkRecoveryMonitoring { attemptReconnect() }
            }
            ErrorAction.RETRY_SAME_URL -> {
                scope.launch {
                    openVideoWithCacheCheck(url, "${title}_$currentEpisodeIndex")
                }
            }
            ErrorAction.SWITCH_TO_NEXT_QUALITY -> {
                val idx = result.nextQualityIndex ?: return
                logger.i(
                    "Quality downgrade: ${qualityLabels.getOrElse(currentQualityIndex) { "?" }} -> ${qualityLabels.getOrElse(idx) { "?" }}",
                )
                onQualityAutoSwitch?.invoke(QualityAutoSwitchEvent(qualityLabels.getOrElse(idx) { "" }))
                switchQualityInternal(idx)
            }
            ErrorAction.SHOW_ERROR_DIALOG -> {
                if (!hasTriedDirectUrl && url.isNotEmpty() && url != resolvedMediaUrl) {
                    hasTriedDirectUrl = true
                    logger.w("Proxy chain failed, trying direct CDN URL")
                    playerEngine.stop()
                    scope.launch {
                        delay(REOPEN_DELAY_MS)
                        if (isDisposed) return@launch
                        playerEngine.setSource(url, headersForCurrentEpisode())
                    }
                    return
                }
                // 直连也失败时，换同一集的其它线路再试一次 —— CMS 站点通常同时
                // 给出 m3u8 / 网盘分享页等多条线路，其中分享页必然直连失败。
                val nextFallback = nextFallbackUrl()
                if (nextFallback != null) {
                    logger.w("Current line failed, switching to alternative line: $nextFallback")
                    url = nextFallback
                    hasTriedDirectUrl = false
                    playerEngine.stop()
                    scope.launch {
                        delay(REOPEN_DELAY_MS)
                        if (isDisposed) return@launch
                        openVideoWithCacheCheck(nextFallback, "${title}_$currentEpisodeIndex")
                    }
                    return
                }
                val nq = errorHandler.findNextUntriedQuality()
                emitError(
                    ErrorEvent(
                        message = error,
                        hasNextEpisode = getHasNextEpisode(),
                        hasUntriedQuality = hasQualityOptions() && nq >= 0,
                        untriedQualityLabel = if (nq >= 0) qualityLabels.getOrElse(nq) { null } else null,
                        triedQualityCount = errorHandler.retryCount,
                    ),
                )
            }
            ErrorAction.RECOVER_FROM_STUCK -> {
                logger.w("Player stuck detected, attempting seek recovery")
                val pos = playerEngine.getPosition()
                playerEngine.seekTo(if (pos > 0L) pos else 0L)
            }
            ErrorAction.RECOVER_FROM_BLACK_SCREEN -> {
                logger.w("Black screen detected, reinitializing player")
                val blackPos = playerEngine.getPosition()
                playerEngine.release()
                playerEngine.initialize()
                playerEngine.setListener(this)
                // Critical fix: re-bind Surface after engine reinitialization
                pendingSurface?.let { playerEngine.setSurface(it) }
                scope.launch {
                    openVideoWithCacheCheck(url, "${title}_$currentEpisodeIndex")
                    delay(REOPEN_DELAY_MS)
                    if (isDisposed) return@launch
                    if (blackPos > 0L) playerEngine.seekTo(blackPos)
                }
            }
            ErrorAction.RECOVER_FROM_SILENCE -> {
                logger.w("Silence detected, resetting audio pipeline")
                playerEngine.setAudioTrack("none")
                scope.launch {
                    delay(200)
                    if (!isDisposed) playerEngine.setAudioTrack("auto")
                }
            }
            ErrorAction.SWITCH_SOURCE -> {
                logger.w("Current source abnormal, switching to next source")
                if (hasQualityOptions()) {
                    val nextIdx = result.nextQualityIndex ?: (currentQualityIndex + 1)
                    if (nextIdx < qualityLabels.size) {
                        onQualityAutoSwitch?.invoke(QualityAutoSwitchEvent(qualityLabels[nextIdx]))
                        switchQualityInternal(nextIdx)
                    } else {
                        emitError(ErrorEvent(message = error, hasNextEpisode = getHasNextEpisode()))
                    }
                } else {
                    emitError(ErrorEvent(message = error, hasNextEpisode = getHasNextEpisode()))
                }
            }
        }
    }

    private fun attemptReconnect() {
        if (isDisposed) return
        val pos = playerEngine.getPosition()
        logger.i("Auto reconnect - breakpoint: ${pos / 1000}s")
        scope.launch {
            openVideoWithCacheCheck(url, "${title}_$currentEpisodeIndex")
            delay(500)
            if (isDisposed) return@launch
            if (playerEngine.isPlaying() || playerEngine.getPosition() > 0L) {
                playerEngine.seekTo(pos)
                logger.i("Breakpoint restored")
            }
        }
    }

    // ========================================================================
    // Internal: Hardware decode failure
    // ========================================================================

    private fun onHardwareDecodeFailure(error: String) {
        if (isDisposed || !hardwareDecodingEnabled) return
        logger.w("Hardware decode failure, downgrading to software: $error")
        hardwareDecodingEnabled = false
        playerEngine.stop()
        scope.launch {
            delay(REOPEN_DELAY_MS)
            if (isDisposed) return@launch
            openVideoWithCacheCheck(url, "${title}_$currentEpisodeIndex")
        }
    }

    // ========================================================================
    // Internal: Hardware decoding configuration
    // ========================================================================

    private fun configureHardwareDecoding() {
        hardwareDecodingEnabled = true
        logger.d("Hardware decoding configured: $hardwareDecodingEnabled")
    }

    // ========================================================================
    // Internal: Buffer / ABR callbacks
    // ========================================================================

    private fun onBufferStateChanged(isLow: Boolean) {
        if (isDisposed) return
        if (isLow) {
            isLoading = true
            loadingText = "Buffering..."
            metricsEngine.setBuffering(true)
            metricsEngine.recordEvent(MetricsEvent.BUFFER_START)
            cacheEngine.notifyPreloadBuffering(true)
        } else {
            metricsEngine.setBuffering(false)
            metricsEngine.recordEvent(MetricsEvent.BUFFER_END)
            cacheEngine.notifyPreloadBuffering(false)
        }
        onBufferingChanged?.invoke(isLow)
    }

    private fun onAbrQualityChanged(level: QualityLevel) {
        if (isDisposed) return
        logger.i("ABR quality suggestion: ${level.label}")
        abrController.saveQualityPreference(level)
        onQualitySuggestion?.invoke(
            QualitySuggestionEvent(
                networkQualityDescription = abrController.networkQualityDescription,
                qualityLabel = level.label,
            ),
        )
    }

    // ========================================================================
    // Internal: AV sync monitoring
    // ========================================================================

    private fun startAvSyncCheckTimer() {
        avSyncCheckJob?.cancel()
        avSyncCheckJob =
            scope.launch {
                while (isActive && !isDisposed) {
                    delay(if (isInBackground) BACKGROUND_AV_SYNC_INTERVAL_MS else AV_SYNC_CHECK_INTERVAL_MS)
                    checkAVSync()
                }
            }
    }

    internal fun checkAVSync() {
        if (isDisposed || !playerEngine.isPlaying()) return
        val now = Clock.System.now().toEpochMilliseconds()
        // 基线未建立（还没收到任何位置）→ 不比较，否则 elapsed = now - 0 = 墙钟时间
        if (lastPositionUpdateTimeMs <= 0L) return
        val elapsed = now - lastPositionUpdateTimeMs
        if (elapsed <= 120) return
        // 基线过期（暂停/卡顿/位置长时间没更新）→ 重新取基线，而不是把它当成漂移
        if (elapsed > MAX_AV_SYNC_BASELINE_AGE_MS) {
            lastPositionUpdateTimeMs = now
            lastVideoPositionMs = playerEngine.getPosition()
            return
        }

        val expectedMs = lastVideoPositionMs + elapsed
        val actualMs = playerEngine.getPosition()
        val driftMs = expectedMs - actualMs
        val absDriftMs = kotlin.math.abs(driftMs)
        if (absDriftMs < 50) return

        val frames = absDriftMs / 33

        when {
            absDriftMs > 2000 -> {
                logger.w("Video severely drifted (${absDriftMs}ms / ~$frames frames), seek correction")
                metricsEngine.recordEvent(MetricsEvent.ERROR, avSyncOffsetMs = absDriftMs.toInt())
                if (driftMs < 0) playerEngine.seekTo(expectedMs) else playerEngine.seekTo(actualMs)
                avSyncCorrectionCount++
                lastAVSyncCorrectionMs = now
            }
            absDriftMs > 500 -> {
                logger.w("AV sync offset too large (${absDriftMs}ms), seek correction")
                playerEngine.seekTo(expectedMs)
                avSyncCorrectionCount++
                lastAVSyncCorrectionMs = now
            }
            frames > 5 -> {
                logger.w("Frame accumulation: $frames frames (${absDriftMs}ms), seek to expected")
                playerEngine.seekTo(expectedMs)
                avSyncCorrectionCount++
                lastAVSyncCorrectionMs = now
            }
            else -> {
                logger.d("AV sync micro-adjust: ${driftMs}ms (~$frames frames)")
                val rate = if (driftMs < 0) playbackSpeed * 1.05f else playbackSpeed * 0.95f
                playerEngine.setPlaybackSpeed(rate)
                scope.launch {
                    delay(AV_SYNC_SPEED_RECOVERY_MS)
                    if (isDisposed) return@launch
                    if (playerEngine.isPlaying()) playerEngine.setPlaybackSpeed(playbackSpeed)
                }
            }
        }
    }

    // ========================================================================
    // Internal: Progress save timer
    // ========================================================================

    /**
     * 周期性把播放进度推给 UI 层。
     *
     * 250ms 的间隔既能让进度条平滑、字幕同步落在 ±50ms 容差内，
     * 又不会给播放器带来可感知负担（ExoPlayer 的 currentPosition 是本地读取）。
     *
     * 这里顺带驱动下一集预加载判断：原先它挂在 `onPositionChanged` 上，
     * 而那个回调只在 seek 时触发，等于从未生效。
     */
    private fun startProgressReportTimer() {
        progressReportJob?.cancel()
        progressReportJob =
            scope.launch {
                while (isActive && !isDisposed) {
                    try {
                        val position = playerEngine.getPosition()
                        val duration = playerEngine.getDuration()
                        val buffered = playerEngine.getBufferedPercentage()
                        if (position > 0L) {
                            lastVideoPositionMs = position
                            // ⚠️ 基线**时间戳**必须一起更新：checkAVSync 用
                            // 「上次位置 + 经过时间」推算预期位置，只更新位置不更新时间
                            // 会让 elapsed 越滚越大 → 误判「严重失步」→ 频繁 seek 校正
                            // → 播放卡顿跳帧（实测日志出现 1.79e12ms 的漂移值，
                            // 那就是墙钟时间被当成漂移量）。
                            lastPositionUpdateTimeMs = Clock.System.now().toEpochMilliseconds()
                        }
                        onProgress?.invoke(position, duration, buffered)
                        checkPreloadTrigger(position)
                    } catch (e: Exception) {
                        logger.w("Progress report failed: $e")
                    }
                    delay(PROGRESS_REPORT_INTERVAL_MS)
                }
            }
    }

    private fun startProgressSaveTimer() {
        progressSaveJob?.cancel()
        progressSaveJob =
            scope.launch {
                while (isActive && !isDisposed) {
                    delay(if (isInBackground) BACKGROUND_PROGRESS_SAVE_INTERVAL_MS else PROGRESS_SAVE_INTERVAL_MS)
                    savePlaybackProgress()
                }
            }
    }

    private fun savePlaybackProgress() {
        if (isDisposed) return
        val callback = progressSaveCallback ?: return
        val videoUrl = episodeUrls?.getOrNull(currentEpisodeIndex) ?: url
        try {
            callback.saveProgress(
                videoUrl = videoUrl,
                positionMs = playerEngine.getPosition(),
                durationMs = playerEngine.getDuration(),
                lastPlayTimeMs = Clock.System.now().toEpochMilliseconds(),
            )
        } catch (e: Exception) {
            logger.w("Progress save failed: $e")
        }
    }

    // ========================================================================
    // Internal: First frame notification / preload trigger
    // ========================================================================

    private fun notifyFirstFrame() {
        val metrics = metricsEngine.getCurrentMetrics() ?: return
        val ms = (metrics["firstFrameTimeMs"] as? Long) ?: 0L
        if (ms > 0) onFirstFrame?.invoke(FirstFrameEvent(ms))
    }

    private fun checkPreloadTrigger(positionMs: Long) {
        // 省电模式下不预加载下一集（见 applyPowerMode）—— 否则「省电」只是个空承诺。
        if (isDisposed || preloadDisabled || hasTriggeredNextEpisodePreload || !getHasNextEpisode()) return
        val duration = playerEngine.getDuration()
        if (duration <= 0) return
        if (positionMs.toFloat() / duration.toFloat() >= PRELOAD_TRIGGER_POSITION) {
            hasTriggeredNextEpisodePreload = true
            val next = currentEpisodeIndex + 1
            val nextUrl = episodeUrls?.getOrNull(next) ?: return
            cacheEngine.preloadNextEpisode("${title}_$next", nextUrl)
        }
    }

    // ========================================================================
    // Internal: Subtitle loading
    // ========================================================================

    private fun loadSubtitles() {
        val urls = subtitleUrls ?: return
        if (urls.isEmpty()) return
        scope.launch {
            try {
                val trackInfos =
                    urls.mapIndexed { index, subtitleUrl ->
                        TrackInfo(
                            id = subtitleUrl,
                            label = "Subtitle ${index + 1}",
                            language = "zh-CN",
                        )
                    }
                val tracks = subtitleService.loadMultiTrackFromUrl(trackInfos)
                if (isDisposed) return@launch
                subtitleTracks = tracks
                onSubtitlesLoaded?.invoke(tracks)
            } catch (e: Exception) {
                logger.w("Subtitle loading failed: $e")
            }
        }
    }

    // ========================================================================
    // Internal: Playback completed
    // ========================================================================

    private fun onPlaybackCompleted() {
        if (isDisposed) return
        when (playMode) {
            PlayMode.LOOP_SINGLE -> {
                playerEngine.seekTo(0L)
                playerEngine.play()
            }
            PlayMode.LOOP_ALL -> {
                if (getHasNextEpisode()) {
                    playNextEpisode()
                } else if (!episodeUrls.isNullOrEmpty()) {
                    playEpisodeAtIndex(0)
                } else {
                    playerEngine.seekTo(0L)
                    playerEngine.play()
                }
            }
            PlayMode.SEQUENTIAL -> {
                if (getHasNextEpisode()) playNextEpisode()
            }
        }
    }

    // ========================================================================
    // Internal: Quality switching (internal)
    // ========================================================================

    private fun switchQualityInternal(index: Int) {
        if (isDisposed || index < 0 || index >= qualityUrls.size) return
        val savedPos = playerEngine.getPosition()
        currentQualityIndex = index
        isLoading = true
        loadingText = "Switching quality..."
        scope.launch {
            openVideoWithCacheCheck(qualityUrls[index], "${title}_$index")
            delay(REOPEN_DELAY_MS)
            if (isDisposed) return@launch
            if (savedPos > 0L) playerEngine.seekTo(savedPos)
        }
    }

    // ========================================================================
    // Internal: App lifecycle
    // ========================================================================

    private fun onAppBackgrounded() {
        if (isDisposed) return
        isInBackground = true
        logger.d("App backgrounded, keeping audio playback")
        startAvSyncCheckTimer()
        startProgressSaveTimer()
        if (!isInPipMode) savePlaybackProgress()
    }

    private fun onAppForegrounded() {
        if (isDisposed || !isInBackground) return
        isInBackground = false
        logger.d("App foregrounded, restoring video playback")
        startAvSyncCheckTimer()
        startProgressSaveTimer()
        if (isInPipMode && pipSavedQualityIndex != null) {
            isInPipMode = false
            currentQualityIndex = pipSavedQualityIndex!!
            pipSavedQualityIndex = null
            logger.i("PiP exit: restoring original resolution")
        }
    }

    // ========================================================================
    // Internal: Power mode
    // ========================================================================

    private fun detectPowerMode() {
        try {
            powerMode = powerManager.getPowerMode()
            logger.i("Power mode: $powerMode, battery: ${powerManager.getBatteryLevel()}%, charging: ${powerManager.isCharging()}")
            applyPowerMode()
        } catch (e: Exception) {
            powerMode = PowerMode.BALANCED
            logger.d("Power mode detection failed, using default: $e")
        }
    }

    private fun applyPowerMode() {
        // 省电模式的实际动作是「关掉预加载」—— 之前这里只打了一行日志，
        // 于是省电模式在观感上完全不存在。现在把开关真正下发给 preload 触发点。
        preloadDisabled = powerMode == PowerMode.POWER_SAVING
        logger.i("Power mode applied: $powerMode, preload=${if (preloadDisabled) "off" else "on"}")
    }

    // ========================================================================
    // Internal: Preferences
    // ========================================================================

    private fun saveSkipInterval() {
        settings.putInt("skip_interval", skipInterval)
    }

    private fun loadSkipInterval() {
        try {
            val saved = settings.getIntOrNull("skip_interval")
            if (saved != null && saved in skipIntervals) {
                skipInterval = saved
            }
        } catch (e: Exception) {
            logger.w("Skip interval load failed: $e")
        }
    }

    // ========================================================================
    // Internal: Notification control
    // ========================================================================

    private fun notifyIfNotInitializing() {
        if (!isInitializing) {
            onPlayerStateChanged?.invoke(playerEngine.getPlayerState())
        }
    }

    // ========================================================================
    // Utility functions
    // ========================================================================

    companion object {
        fun formatNetworkSpeed(kbps: Double): String =
            when {
                kbps <= 0 -> ""
                kbps < 1000 -> "${"%.0f".format(kbps)} kb/s"
                else -> "${"%.1f".format(kbps / 1000)} MB/s"
            }

        fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }

        fun getPowerModeName(mode: PowerMode): String =
            when (mode) {
                PowerMode.HIGH_PERFORMANCE -> "High Performance"
                PowerMode.BALANCED -> "Balanced"
                PowerMode.POWER_SAVING -> "Power Saving"
            }
    }
}
