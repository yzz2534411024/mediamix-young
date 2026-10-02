package com.mediamix.shared.player

import android.content.Context
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import co.touchlab.kermit.Logger
import java.io.File

/**
 * Android actual 实现 PlayerEngine
 *
 * 使用 Media3 ExoPlayer 实现统一播放器接口。
 * 需要通过 companion object 的 init() 方法注入 Context。
 */
@OptIn(UnstableApi::class)
actual class PlayerEngine actual constructor() {
    private val logger = Logger.withTag("ExoPlayerEngine")
    private var exoPlayer: ExoPlayer? = null
    private var currentState: PlayerState = PlayerState.IDLE
    private var listener: PlayerEngineListener? = null

    /** 软解优先开关，重建 ExoPlayer 时生效 */
    private var preferSoftwareDecoding = false

    /** 数据源工厂，持有它才能在切集时动态更新请求头 */
    private var dataSourceFactory: DefaultHttpDataSource.Factory? = null

    // 首帧渲染标记，每次 setSource 时重置
    private var firstFrameReported = false

    /**
     * ExoPlayer 播放状态监听器
     * 负责将 Media3 的回调映射到 PlayerEngineListener 和 PlayerState
     */
    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val newState =
                    when (state) {
                        Player.STATE_IDLE -> PlayerState.IDLE
                        Player.STATE_BUFFERING -> PlayerState.BUFFERING
                        Player.STATE_READY -> PlayerState.READY
                        Player.STATE_ENDED -> PlayerState.ENDED
                        else -> PlayerState.IDLE
                    }
                currentState = newState
                listener?.onStateChanged(newState)

                // READY 状态且首帧尚未上报时，通知首帧渲染完成
                if (state == Player.STATE_READY && !firstFrameReported) {
                    firstFrameReported = true
                    listener?.onFirstFrameRendered()
                }

                // 播放结束回调
                if (state == Player.STATE_ENDED) {
                    listener?.onPlaybackEnded()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val newState = if (isPlaying) PlayerState.PLAYING else PlayerState.PAUSED
                currentState = newState
                listener?.onStateChanged(newState)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // 宽高比要把像素宽高比（SAR）算进去，否则 anamorphic 片源比例会偏
                val ratio =
                    if (videoSize.height > 0 && videoSize.width > 0) {
                        videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    } else {
                        0f
                    }
                if (ratio > 0f && kotlin.math.abs(ratio - videoAspectRatio) > 0.001f) {
                    videoAspectRatio = ratio
                    listener?.onVideoAspectRatioChanged(ratio)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentState = PlayerState.ERROR
                listener?.onError(
                    error.message ?: "Unknown playback error",
                    error.errorCode,
                )
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                listener?.onPositionChanged(newPosition.positionMs)
            }

            override fun onTracksChanged(tracks: Tracks) {
                // 轨道变化时可在未来扩展通知逻辑
            }
        }

    /**
     * 初始化 ExoPlayer 实例
     * 必须先调用 companion object 的 init(context) 注入 Context
     */
    actual fun initialize() {
        val context =
            appContext
                ?: throw IllegalStateException("ExoPlayerEngine.init(context) 必须先调用")

        // 释放旧实例，避免资源泄漏
        exoPlayer?.let {
            it.removeListener(playerListener)
            it.release()
        }

        exoPlayer =
            ExoPlayer
                .Builder(context, buildRenderersFactory(context))
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(
                        // 边播边缓存：CacheDataSource 包住 HTTP 源 —— 已播分片落盘，
                        // 复播/续播命中本地不再走网络；m3u8 分片同样按 URL 落盘。
                        // upstream 就是 [newDataSourceFactory] 的实例，切集时更新的
                        // UA/Referer 请求头对它依然生效。
                        cacheDataSourceFactory(context),
                    ),
                ).setLoadControl(buildLoadControl())
                // ★ 音频属性必须显式配置：不配时 ExoPlayer 不申请音频焦点、USAGE 为 UNKNOWN，
                // 实测在 Android 上表现为「有画面没声音」（用户反馈）。
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
                .build()
                .apply { addListener(playerListener) }
        currentState = PlayerState.IDLE
        firstFrameReported = false
    }

    /**
     * 带「边播边缓存」能力的数据源工厂。
     *
     * 结构：`CacheDataSource( SimpleCache, DefaultHttpDataSource )` ——
     * 读请求先查本地缓存，未命中的分片走 HTTP 上游并把响应同时写入缓存。
     *
     * 设计要点：
     * - `SimpleCache` **必须全局单例**：同目录建多实例会触发数据库锁冲突。
     *   引擎会被反复 `initialize()` 重建，缓存实例放 companion 持有。
     * - `LeastRecentlyUsedCacheEvictor(512MB)`：超限自动按 LRU 淘汰，无需手动清理；
     *   与 `VideoCacheService` 的下载缓存（用户主动保存的视频）目录隔离，互不影响。
     * - `FLAG_IGNORE_CACHE_ON_ERROR`：缓存层出错时自动降级直连，绝不因缓存问题阻断播放。
     * - UA/Referer 防盗链头挂在 upstream（[DefaultHttpDataSource.Factory]）上，
     *   `setSource` 切集时更新默认头依然生效。
     */
    @OptIn(UnstableApi::class)
    private fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstream = newDataSourceFactory()
        return CacheDataSource
            .Factory()
            .setCache(getPlayerCache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun getPlayerCache(context: Context): SimpleCache =
        playerCache
            ?: synchronized(this) {
                playerCache
                    ?: SimpleCache(
                        File(context.cacheDir, "exo_stream_cache"),
                        LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
                        StandaloneDatabaseProvider(context),
                    ).also { playerCache = it }
            }

    /**
     * 缓冲策略。
     *
     * ExoPlayer 默认值（起播 2.5s / 最小 50s / 最大 50s）对采集站的 m3u8 源并不合适：
     * 起播偏慢，而缓冲上限又没给弱网留够抗抖动空间。这里调成「起播更快 + 抗抖动更强」：
     * - `bufferForPlaybackMs = 1.5s`：点开更快出画面
     * - `minBufferMs = 15s / maxBufferMs = 90s`：网络抖动时不容易卡断，又不会一次吃掉太多内存
     * - `bufferForPlaybackAfterRebufferMs = 4s`：避免弱网下「缓冲→播放→再缓冲」反复抖动
     */
    private fun buildLoadControl(): DefaultLoadControl =
        DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(
                // minBufferMs =
                15_000,
                // maxBufferMs =
                90_000,
                // bufferForPlaybackMs =
                1_500,
                // bufferForPlaybackAfterRebufferMs =
                4_000,
            ).build()

    /**
     * 新建数据源工厂。
     *
     * 必须带默认 UA：大量 CMS/CDN（尤其带防盗链的）对 ExoPlayer 默认的
     * `ExoPlayerLib/x.y.z` 直接返回 403，表现为「Source error」。
     */
    private fun newDataSourceFactory(): DefaultHttpDataSource.Factory {
        val factory =
            DefaultHttpDataSource
                .Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setDefaultRequestProperties(DEFAULT_HEADERS)
        dataSourceFactory = factory
        return factory
    }

    /**
     * 构建渲染器工厂。
     *
     * - 一律开启 [DefaultRenderersFactory.setEnableDecoderFallback]：硬解在部分
     *   中低端机型上会直接失败，开启后 ExoPlayer 会自动退到软件解码器。
     * - 软解优先时用 [MediaCodecSelector] 过滤出软件解码器；若该编码没有软件
     *   实现则回退到全量列表，避免「过滤后无解码器 → 黑屏」。
     */
    private fun buildRenderersFactory(context: Context): DefaultRenderersFactory {
        val factory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        if (preferSoftwareDecoding) {
            factory.setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunneling ->
                val all =
                    MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunneling)
                all.filter { it.softwareOnly }.ifEmpty { all }
            }
        }
        return factory
    }

    actual fun setDecodeMode(preferSoftware: Boolean) {
        preferSoftwareDecoding = preferSoftware
        logger.i("Decode mode: ${if (preferSoftware) "software preferred" else "hardware preferred"}")
    }

    /**
     * 设置播放源
     * 构建 MediaItem 并加载到 ExoPlayer，同时重置首帧标记
     */
    actual fun setSource(
        url: String,
        headers: Map<String, String>?,
    ) {
        firstFrameReported = false
        try {
            if (url.isBlank()) {
                logger.w("Empty URL, skipping setSource")
                return
            }
            // 合并默认头与调用方传入的头（后者优先），每次切集都要重设，
            // 否则上一集的 Referer 会串到下一集，导致 CDN 鉴权失败。
            val merged =
                buildMap {
                    putAll(DEFAULT_HEADERS)
                    headers?.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) put(k, v) }
                }
            (dataSourceFactory ?: newDataSourceFactory()).setDefaultRequestProperties(merged)

            val mediaItem = MediaItem.fromUri(url)
            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
            currentState = PlayerState.BUFFERING
            listener?.onStateChanged(PlayerState.BUFFERING)
        } catch (e: Exception) {
            logger.e("setSource failed: $e")
            currentState = PlayerState.ERROR
            listener?.onError("Failed to load video: ${e.message}", null)
        }
    }

    actual fun play() {
        exoPlayer?.play()
    }

    actual fun pause() {
        exoPlayer?.pause()
    }

    actual fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    actual fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackParameters(PlaybackParameters(speed))
    }

    /**
     * 设置音量（0.0 ~ 1.0）
     */
    actual fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    actual fun getPosition(): Long = exoPlayer?.currentPosition ?: 0L

    actual fun getDuration(): Long {
        val duration = exoPlayer?.duration ?: 0L
        // ExoPlayer 在未知时长时返回 C.TIME_UNSET
        return if (duration == C.TIME_UNSET) 0L else duration
    }

    actual fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false

    /**
     * 释放播放器资源
     * 释放后需重新调用 initialize() 才能使用
     */
    actual fun release() {
        exoPlayer?.apply {
            removeListener(playerListener)
            release()
        }
        exoPlayer = null
        currentState = PlayerState.IDLE
    }

    actual fun getPlayerState(): PlayerState = currentState

    actual fun setListener(listener: PlayerEngineListener?) {
        this.listener = listener
    }

    actual fun getBufferedPercentage(): Int = exoPlayer?.bufferedPercentage ?: 0

    // ==================== 轨道管理 ====================

    /**
     * 切换视频轨道
     * @param trackId 轨道组索引字符串
     * @return 是否切换成功
     */
    actual fun setVideoTrack(trackId: String): Boolean = selectTrackByGroupIndex(trackId, C.TRACK_TYPE_VIDEO)

    /**
     * 获取所有视频轨道列表
     */
    actual fun getVideoTracks(): List<TrackInfo> = extractTracks(C.TRACK_TYPE_VIDEO)

    /**
     * 切换音频轨道
     * @param trackId 轨道组索引字符串
     * @return 是否切换成功
     */
    actual fun setAudioTrack(trackId: String): Boolean = selectTrackByGroupIndex(trackId, C.TRACK_TYPE_AUDIO)

    /**
     * 获取所有音频轨道列表
     */
    actual fun getAudioTracks(): List<TrackInfo> = extractTracks(C.TRACK_TYPE_AUDIO)

    /**
     * 切换字幕轨道
     * @param trackId 轨道组索引字符串
     * @return 是否切换成功
     */
    actual fun setSubtitleTrack(trackId: String): Boolean = selectTrackByGroupIndex(trackId, C.TRACK_TYPE_TEXT)

    /**
     * 获取所有字幕轨道列表
     */
    actual fun getSubtitleTracks(): List<TrackInfo> = extractTracks(C.TRACK_TYPE_TEXT)

    // ==================== Surface 管理 ====================

    /**
     * 设置视频渲染目标。
     *
     * @param surface `TextureView`（来自 VideoSurface.android.kt）或 `Surface`，传 null 清除
     */
    actual fun setSurface(surface: Any?) {
        when (surface) {
            // 主路径：VideoSurface 在 Android 侧提供 TextureView，
            // 以便与 Compose 的字幕/控制层正确叠加。
            is TextureView -> exoPlayer?.setVideoTextureView(surface)
            is Surface -> exoPlayer?.setVideoSurface(surface)
            null -> exoPlayer?.clearVideoSurface()
            else -> logger.w("Unknown surface type: ${surface::class}, ignoring")
        }
    }

    actual fun stop() {
        exoPlayer?.stop()
        currentState = PlayerState.IDLE
        listener?.onStateChanged(PlayerState.IDLE)
    }

    // === 画面比例 ===

    /** 当前视频宽高比（w * pixelRatio / h）；未知为 0f。 */
    @Volatile
    private var videoAspectRatio: Float = 0f

    /**
     * Android 端不在这里处理画面比例：TextureView 的尺寸由 UI 层（PlayerScreen）
     * 按 [getVideoAspectRatio] + [AspectMode] 约束 —— TextureView 自己不会适配比例。
     */
    actual fun setAspectMode(mode: AspectMode) {
        // no-op：尺寸约束在 Compose 层完成
    }

    /** Android 端画面比例由 UI 层处理（非引擎内部）。 */
    actual val handlesAspectInternally: Boolean get() = false

    actual fun getVideoAspectRatio(): Float = videoAspectRatio

    // ==================== 内部辅助方法 ====================

    /**
     * 根据轨道组索引切换轨道
     * 使用 TrackSelectionOverride + TrackSelectionParameters 覆盖指定类型的轨道选择
     *
     * @param trackId 轨道组在 currentTracks.groups 中的索引
     * @param trackType C.TRACK_TYPE_VIDEO / AUDIO / TEXT
     * @return 是否切换成功
     */
    private fun selectTrackByGroupIndex(
        trackId: String,
        trackType: Int,
    ): Boolean {
        val player = exoPlayer ?: return false
        val groupIndex = trackId.toIntOrNull() ?: return false
        val groups = player.currentTracks.groups
        if (groupIndex < 0 || groupIndex >= groups.size) return false

        val targetGroup = groups[groupIndex]
        if (targetGroup.type != trackType) return false

        return try {
            // 通过 TrackSelectionParameters 添加轨道覆盖
            val trackGroup = targetGroup.mediaTrackGroup
            val override = TrackSelectionOverride(trackGroup, listOf(0))
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .addOverride(override)
                    .build()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 根据轨道类型提取轨道信息列表
     * 通过 mediaTrackGroup.getFormat() 访问 Format 信息
     *
     * @param trackType C.TRACK_TYPE_VIDEO / AUDIO / TEXT
     */
    private fun extractTracks(trackType: Int): List<TrackInfo> {
        val player = exoPlayer ?: return emptyList()
        val tracks = player.currentTracks
        val result = mutableListOf<TrackInfo>()

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != trackType) continue
            if (group.length == 0) continue

            // 通过 mediaTrackGroup 获取 Format
            val trackGroup = group.mediaTrackGroup
            val format = trackGroup.getFormat(0)

            val id = groupIndex.toString()
            val label = buildTrackLabel(format.label, format.language)

            result.add(
                TrackInfo(
                    id = id,
                    label = label,
                    language = format.language,
                    mimeType = format.sampleMimeType,
                    bitrate = if (format.bitrate != Format.NO_VALUE) format.bitrate else null,
                    isSelected = group.isSelected,
                ),
            )
        }
        return result
    }

    /**
     * 构建轨道显示标签
     */
    private fun buildTrackLabel(
        name: String?,
        language: String?,
    ): String =
        when {
            name != null && language != null -> "$name ($language)"
            name != null -> name
            language != null -> language
            else -> "未知轨道"
        }

    companion object {
        private var appContext: Context? = null

        /** 全局唯一的播放缓存实例（SimpleCache 同目录多实例会锁冲突，必须单例）。 */
        @Volatile
        private var playerCache: SimpleCache? = null

        /**
         * 默认请求头。
         *
         * 媒体聚合站的 CDN 普遍做 UA 校验与防盗链，ExoPlayer 自带的
         * `ExoPlayerLib/1.5.0` 会被直接拒绝（403 → Source error）。
         * 这里伪装成移动端浏览器 UA，并放开 Accept 通配。
         */
        private val DEFAULT_HEADERS: Map<String, String> =
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                "Accept" to "*/*",
                "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
            )

        /**
         * 初始化 ExoPlayerEngine，注入 Application Context
         * 应在 Application.onCreate() 或 Koin 模块初始化时调用
         */
        fun init(context: Context) {
            appContext = context.applicationContext
        }
    }
}
