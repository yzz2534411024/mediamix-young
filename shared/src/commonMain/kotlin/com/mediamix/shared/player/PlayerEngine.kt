package com.mediamix.shared.player

/**
 * 统一播放器接口 — expect 声明
 * Android actual: Media3 ExoPlayer
 * Desktop actual: mpv (via JNA)
 */
expect class PlayerEngine() {
    fun initialize()

    fun setSource(
        url: String,
        headers: Map<String, String>? = null,
    )

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setPlaybackSpeed(speed: Float)

    fun setVolume(volume: Float)

    fun getPosition(): Long

    fun getDuration(): Long

    fun isPlaying(): Boolean

    fun release()

    /**
     * 设置解码偏好。
     *
     * @param preferSoftware true = 优先软解（兼容老旧/异常编码），false = 默认硬解 + 失败回退。
     *   Android 侧通过 MediaCodecSelector 过滤软件解码器实现；Desktop 侧忽略。
     */
    fun setDecodeMode(preferSoftware: Boolean)

    // === 新增 API ===

    // 播放状态
    fun getPlayerState(): PlayerState

    // 回调监听
    fun setListener(listener: PlayerEngineListener?)

    // 缓冲
    fun getBufferedPercentage(): Int

    // 视频轨道选择（可选操作，不支持时返回 false）
    fun setVideoTrack(trackId: String): Boolean

    fun getVideoTracks(): List<TrackInfo>

    // 音频轨道选择
    fun setAudioTrack(trackId: String): Boolean

    fun getAudioTracks(): List<TrackInfo>

    // 字幕轨道选择
    fun setSubtitleTrack(trackId: String): Boolean

    fun getSubtitleTracks(): List<TrackInfo>

    // Surface 管理（视频渲染）
    fun setSurface(surface: Any?)

    // 停止
    fun stop()

    // === 画面比例 ===

    /**
     * 设置画面比例模式。
     *
     * Android（ExoPlayer）不做处理 —— TextureView 的尺寸由 UI 层按
     * [getVideoAspectRatio] + 模式约束；Desktop（mpv）在这里直接下发
     * keepaspect / panscan / video-aspect-override 参数。
     */
    fun setAspectMode(mode: AspectMode)

    /** 引擎是否自行处理画面比例（Desktop mpv = true 时 UI 层不再约束 surface 尺寸）。 */
    val handlesAspectInternally: Boolean

    /** 当前视频宽高比（w/h）；未知返回 0f（UI 层回退 16:9）。 */
    fun getVideoAspectRatio(): Float
}
