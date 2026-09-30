package com.mediamix.shared.player

/**
 * 统一播放器接口 — expect 声明
 * Android actual: Media3 ExoPlayer
 * Desktop actual: mpv (via JNA)
 */
expect class PlayerEngine() {
    fun initialize()
    fun setSource(url: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun getPosition(): Long
    fun getDuration(): Long
    fun isPlaying(): Boolean
    fun release()

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
}