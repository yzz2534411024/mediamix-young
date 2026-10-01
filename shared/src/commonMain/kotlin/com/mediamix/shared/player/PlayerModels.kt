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
/**
 * 播放画面比例模式（手机 / 桌面共用）。
 *
 * 两端的实现分工不同：
 * - **Android**：TextureView 不会自动按视频比例适配（默认拉伸填满 → 画面变形），
 *   由 Compose 层按 [AspectMode] + 视频真实宽高比约束 surface 尺寸；
 * - **Desktop**：mpv 原生 `keepaspect` 默认就按比例渲染，由引擎直接下发
 *   mpv 参数（panscan / video-aspect-override），上层不改尺寸。
 */
enum class AspectMode(val label: String) {
    /** 保持视频比例完整显示（可能留黑边）—— 默认 */
    ADAPTIVE("自适应"),

    /** 按视频比例放大到铺满屏幕，超出部分裁掉 */
    CROP("铺满裁剪"),

    /** 忽略视频比例直接铺满（画面会变形） */
    STRETCH("拉伸铺满"),

    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    RATIO_21_9("21:9"),

    /** 原始像素（不缩放）—— 与自适应接近，保留兼容旧配置 */
    ORIGINAL("原始比例"),
}

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

    /**
     * 视频尺寸就绪（首帧前后各触发一次）。
     *
     * UI 层用它按视频真实宽高比约束画面尺寸 —— TextureView 不会自动适配比例，
     * 不约束就是「拉伸填满/比例失真」。
     */
    fun onVideoAspectRatioChanged(aspectRatio: Float) {}
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
