package com.mediamix.shared.player

// 播放状态枚举
enum class PlayerState {
    IDLE,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

// 播放模式
enum class PlayMode { SEQUENTIAL, LOOP_SINGLE, LOOP_ALL }

// 画面比例模式
// ⚠️ 已删除 FILL / COVER：两端引擎都没有对应实现（ExoPlayer 侧用的是 TextureView
// 等比适配，mpv 侧没有 resize 模式开关），UI 也从没暴露过这两个选项 ——
// 留着只会让「调整画面比例无效」被误判为 bug。真要支持需要接
// ExoPlayer 的 RESIZE_MODE_FILL / RESIZE_MODE_ZOOM，属独立改动。
enum class AspectMode { ORIGINAL, RATIO_16_9, RATIO_4_3 }

// 画质等级
enum class QualityLevel(
    val label: String,
) {
    LOW("流畅"),
    MEDIUM("标清"),
    HIGH("高清"),
    ULTRA("超清"),
}

// 轨道信息
data class TrackInfo(
    val id: String,
    val label: String,
    val language: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val isSelected: Boolean = false,
)

/**
 * 一集「可播放」的形态：真实地址 + 必需请求头。
 *
 * TVBox 源的剧集标识（`playerContent` 入参）不是地址，必须解析后才是这个形态；
 * CMS 源两者相同。由 [PlayerCoreManager.episodeResolver] 回调产出。
 */
data class EpisodeSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

// 播放器事件监听接口
interface PlayerEngineListener {
    fun onStateChanged(state: PlayerState)

    fun onPositionChanged(positionMs: Long)

    fun onBufferChanged(bufferedPercent: Int)

    fun onError(
        error: String,
        code: Int? = null,
    )

    fun onFirstFrameRendered()

    fun onPlaybackEnded()
}

// 首帧事件
data class FirstFrameEvent(
    val firstFrameTimeMs: Long,
)

// 错误事件
data class ErrorEvent(
    val message: String,
    val hasNextEpisode: Boolean = false,
    val hasUntriedQuality: Boolean = false,
    val untriedQualityLabel: String? = null,
    val triedQualityCount: Int = 0,
)

// 画质建议事件
data class QualitySuggestionEvent(
    val networkQualityDescription: String,
    val qualityLabel: String,
)

// 进度恢复事件
data class ProgressResumeEvent(
    val position: Long,
)

// 画质自动切换事件
data class QualityAutoSwitchEvent(
    val label: String,
)
