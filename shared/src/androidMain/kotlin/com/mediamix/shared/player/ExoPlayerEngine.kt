package com.mediamix.shared.player

import android.content.Context
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import co.touchlab.kermit.Logger

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

    // 首帧渲染标记，每次 setSource 时重置
    private var firstFrameReported = false

    /**
     * ExoPlayer 播放状态监听器
     * 负责将 Media3 的回调映射到 PlayerEngineListener 和 PlayerState
     */
    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            val newState = when (state) {
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

        override fun onPlayerError(error: PlaybackException) {
            currentState = PlayerState.ERROR
            listener?.onError(
                error.message ?: "Unknown playback error",
                error.errorCode
            )
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
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
        val context = appContext
            ?: throw IllegalStateException("ExoPlayerEngine.init(context) 必须先调用")

        // 释放旧实例，避免资源泄漏
        exoPlayer?.let {
            it.removeListener(playerListener)
            it.release()
        }

        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(playerListener)
        }
        currentState = PlayerState.IDLE
        firstFrameReported = false
    }

    /**
     * 设置播放源
     * 构建 MediaItem 并加载到 ExoPlayer，同时重置首帧标记
     */
    actual fun setSource(url: String) {
        firstFrameReported = false
        try {
            if (url.isBlank()) {
                logger.w("Empty URL, skipping setSource")
                return
            }
            val mediaItem = MediaItem.fromUri(url)
            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()
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

    actual fun getPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    actual fun getDuration(): Long {
        val duration = exoPlayer?.duration ?: 0L
        // ExoPlayer 在未知时长时返回 C.TIME_UNSET
        return if (duration == C.TIME_UNSET) 0L else duration
    }

    actual fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }

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

    actual fun getPlayerState(): PlayerState {
        return currentState
    }

    actual fun setListener(listener: PlayerEngineListener?) {
        this.listener = listener
    }

    actual fun getBufferedPercentage(): Int {
        return exoPlayer?.bufferedPercentage ?: 0
    }

    // ==================== 轨道管理 ====================

    /**
     * 切换视频轨道
     * @param trackId 轨道组索引字符串
     * @return 是否切换成功
     */
    actual fun setVideoTrack(trackId: String): Boolean {
        return selectTrackByGroupIndex(trackId, C.TRACK_TYPE_VIDEO)
    }

    /**
     * 获取所有视频轨道列表
     */
    actual fun getVideoTracks(): List<TrackInfo> {
        return extractTracks(C.TRACK_TYPE_VIDEO)
    }

    /**
     * 切换音频轨道
     * @param trackId 轨道组索引字符串
     * @return 是否切换成功
     */
    actual fun setAudioTrack(trackId: String): Boolean {
        return selectTrackByGroupIndex(trackId, C.TRACK_TYPE_AUDIO)
    }

    /**
     * 获取所有音频轨道列表
     */
    actual fun getAudioTracks(): List<TrackInfo> {
        return extractTracks(C.TRACK_TYPE_AUDIO)
    }

    /**
     * 切换字幕轨道
     * @param trackId 轨道组索引字符串
     * @return 是否切换成功
     */
    actual fun setSubtitleTrack(trackId: String): Boolean {
        return selectTrackByGroupIndex(trackId, C.TRACK_TYPE_TEXT)
    }

    /**
     * 获取所有字幕轨道列表
     */
    actual fun getSubtitleTracks(): List<TrackInfo> {
        return extractTracks(C.TRACK_TYPE_TEXT)
    }

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

    // ==================== 内部辅助方法 ====================

    /**
     * 根据轨道组索引切换轨道
     * 使用 TrackSelectionOverride + TrackSelectionParameters 覆盖指定类型的轨道选择
     *
     * @param trackId 轨道组在 currentTracks.groups 中的索引
     * @param trackType C.TRACK_TYPE_VIDEO / AUDIO / TEXT
     * @return 是否切换成功
     */
    private fun selectTrackByGroupIndex(trackId: String, trackType: Int): Boolean {
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
            player.trackSelectionParameters = player.trackSelectionParameters
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
                    isSelected = group.isSelected
                )
            )
        }
        return result
    }

    /**
     * 构建轨道显示标签
     */
    private fun buildTrackLabel(name: String?, language: String?): String {
        return when {
            name != null && language != null -> "$name ($language)"
            name != null -> name
            language != null -> language
            else -> "未知轨道"
        }
    }

    companion object {
        private var appContext: Context? = null

        /**
         * 初始化 ExoPlayerEngine，注入 Application Context
         * 应在 Application.onCreate() 或 Koin 模块初始化时调用
         */
        fun init(context: Context) {
            appContext = context.applicationContext
        }
    }
}