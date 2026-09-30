package com.mediamix.shared.player

import com.mediamix.shared.network.ThroughputPrediction
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * 自适应码率控制器 (ABR Controller)
 *
 * 基于加权多因子画质决策模型：
 * - 预测吞吐量 (60%)：安全余量 20%，10000kbps 满分
 * - 当前缓冲水位 (25%)：5s→0, 30s→1 线性
 * - 历史带宽稳定性 (15%)：0~1
 *
 * 切换防抖：
 * - 连续 [debounceCount] 次预测一致才执行切换
 * - 升级额外要求：缓冲 > 30s + 延迟确认 [upgradeDelayMs]
 * - 紧急降级 bypass 防抖：缓冲 < 5s 立即降到 LOW
 * - 切换冷却期：最小间隔 [minSwitchIntervalMs]
 */
class ABRController(
    private val settings: Settings = Settings(),
    private val upgradeDelayMs: Long = 5_000L,
    private val minSwitchIntervalMs: Long = 10_000L,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    companion object {
        // 决策权重
        private const val THROUGHPUT_WEIGHT = 0.60
        private const val BUFFER_WEIGHT = 0.25
        private const val STABILITY_WEIGHT = 0.15

        // 安全余量系数（预测吞吐量打 8 折）
        private const val SAFETY_MARGIN = 0.8

        // 满分吞吐量 (kbps)
        private const val FULL_THROUGHPUT_KBPS = 10_000.0

        // 缓冲评分区间 (秒)
        private const val BUFFER_SCORE_LOW_SEC = 5.0
        private const val BUFFER_SCORE_HIGH_SEC = 30.0

        // 紧急降级阈值 (毫秒)
        private const val EMERGENCY_DOWNGRADE_BUFFER_MS = 5_000L

        // 升级缓冲阈值 (毫秒)
        private const val UPGRADE_BUFFER_THRESHOLD_MS = 30_000L

        // 防抖次数
        private const val DEBOUNCE_COUNT = 2

        // 画质映射阈值
        private const val SCORE_LOW_THRESHOLD = 0.15
        private const val SCORE_MEDIUM_THRESHOLD = 0.35
        private const val SCORE_HIGH_THRESHOLD = 0.60

        private const val PREF_KEY_QUALITY = "preferred_quality"
    }

    // ---- 运行时状态 ----
    private var currentBandwidthKbps: Double = 0.0
    private var currentBufferMs: Long = 0
    private var lastSwitchTimeMs: Long = 0
    private var highBandwidthStartMs: Long = 0
    private var latestPrediction: ThroughputPrediction? = null

    // 防抖状态
    private var pendingQuality: QualityLevel? = null
    private var pendingCount: Int = 0

    // 当前画质（StateFlow 供 UI 观察）
    private val _currentQuality = MutableStateFlow(QualityLevel.MEDIUM)
    val currentQuality: StateFlow<QualityLevel> = _currentQuality.asStateFlow()

    // 画质切换回调
    var onQualityChanged: ((QualityLevel) -> Unit)? = null

    // ---- 公开接口 ----

    /** 更新吞吐量预测数据 */
    fun updateThroughputPrediction(prediction: ThroughputPrediction) {
        latestPrediction = prediction
        currentBandwidthKbps = prediction.predictedKbps
        evaluate()
    }

    /** 更新缓冲区状态（毫秒） */
    fun updateBuffer(bufferMs: Long) {
        currentBufferMs = bufferMs
        evaluate()
    }

    /** 兼容旧接口：直接更新带宽值 */
    fun updateBandwidth(kbps: Double) {
        currentBandwidthKbps = kbps
        evaluate()
    }

    /** 当前最新预测结果（可能为 null） */
    fun getLatestPrediction(): ThroughputPrediction? = latestPrediction

    // ---- 核心评估逻辑 ----

    internal fun evaluate() {
        val now = clock()

        // 冷却期保护
        if (lastSwitchTimeMs > 0 && now - lastSwitchTimeMs < minSwitchIntervalMs) return

        // 紧急降级：缓冲 < 5s 时立即降级，不受防抖约束
        if (currentBufferMs < EMERGENCY_DOWNGRADE_BUFFER_MS &&
            _currentQuality.value != QualityLevel.LOW
        ) {
            pendingQuality = null
            pendingCount = 0
            switchQuality(QualityLevel.LOW, "缓冲不足，紧急降级")
            return
        }

        val targetQuality = computeTargetQuality() ?: return

        if (targetQuality != _currentQuality.value) {
            applyDebounce(targetQuality, now)
        } else {
            // 目标与当前一致，重置防抖
            pendingQuality = null
            pendingCount = 0
            highBandwidthStartMs = 0
        }
    }

    /** 应用切换防抖逻辑 */
    internal fun applyDebounce(target: QualityLevel, now: Long) {
        if (target == pendingQuality) {
            pendingCount++
        } else {
            pendingQuality = target
            pendingCount = 1
        }

        if (pendingCount >= DEBOUNCE_COUNT) {
            if (target.ordinal > _currentQuality.value.ordinal) {
                // 升级：需要缓冲充足 + 延迟确认
                if (currentBufferMs > UPGRADE_BUFFER_THRESHOLD_MS) {
                    if (highBandwidthStartMs == 0L) {
                        highBandwidthStartMs = now
                    } else if (now - highBandwidthStartMs >= upgradeDelayMs) {
                        switchQuality(target, "预测吞吐量充足，升级画质")
                        pendingQuality = null
                        pendingCount = 0
                    }
                } else {
                    highBandwidthStartMs = 0
                }
            } else {
                // 降级：防抖通过直接执行
                switchQuality(target, "预测吞吐量不足，降级画质")
                pendingQuality = null
                pendingCount = 0
            }
        }
    }

    /**
     * 基于加权模型计算目标画质
     *
     * 决策因素：
     * - 预测吞吐量 (60%)：使用安全余量后的有效吞吐量
     * - 当前缓冲水位 (25%)：缓冲充足时倾向于更高画质
     * - 历史带宽稳定性 (15%)：稳定性高时更信任预测
     */
    internal fun computeTargetQuality(): QualityLevel? {
        val throughputScore = computeThroughputScore()
        val bufferScore = computeBufferScore()
        val stabilityScore = computeStabilityScore()

        val weightedScore = throughputScore * THROUGHPUT_WEIGHT +
            bufferScore * BUFFER_WEIGHT +
            stabilityScore * STABILITY_WEIGHT

        return scoreToQuality(weightedScore)
    }

    /** 吞吐量评分 (0.0 - 1.0)：10000kbps 满分，使用安全余量 */
    internal fun computeThroughputScore(): Double {
        val effectiveKbps = if (latestPrediction != null) {
            latestPrediction!!.predictedKbps * SAFETY_MARGIN
        } else {
            currentBandwidthKbps * SAFETY_MARGIN
        }
        if (effectiveKbps <= 0) return 0.0
        return (effectiveKbps / FULL_THROUGHPUT_KBPS).coerceIn(0.0, 1.0)
    }

    /** 缓冲水位评分 (0.0 - 1.0)：5s→0, 30s→1 线性 */
    internal fun computeBufferScore(): Double {
        val bufferSec = currentBufferMs / 1000.0
        if (bufferSec >= BUFFER_SCORE_HIGH_SEC) return 1.0
        if (bufferSec <= BUFFER_SCORE_LOW_SEC) return 0.0
        return (bufferSec - BUFFER_SCORE_LOW_SEC) / (BUFFER_SCORE_HIGH_SEC - BUFFER_SCORE_LOW_SEC)
    }

    /** 稳定性评分 (0.0 - 1.0)：基于预测的 stability 字段 */
    internal fun computeStabilityScore(): Double {
        return latestPrediction?.stability ?: 0.5
    }

    /** 将综合评分映射到画质等级 */
    internal fun scoreToQuality(score: Double): QualityLevel {
        return when {
            score < SCORE_LOW_THRESHOLD -> QualityLevel.LOW
            score < SCORE_MEDIUM_THRESHOLD -> QualityLevel.MEDIUM
            score < SCORE_HIGH_THRESHOLD -> QualityLevel.HIGH
            else -> QualityLevel.ULTRA
        }
    }

    private fun switchQuality(newQuality: QualityLevel, reason: String) {
        if (newQuality == _currentQuality.value) return
        _currentQuality.value = newQuality
        lastSwitchTimeMs = clock()
        highBandwidthStartMs = 0
        onQualityChanged?.invoke(newQuality)
    }

    // ---- 画质偏好持久化 ----

    fun saveQualityPreference(level: QualityLevel) {
        settings.putString(PREF_KEY_QUALITY, level.name)
    }

    fun loadQualityPreference(): QualityLevel {
        val name = settings.getStringOrNull(PREF_KEY_QUALITY) ?: return QualityLevel.MEDIUM
        return QualityLevel.entries.firstOrNull { it.name == name } ?: QualityLevel.MEDIUM
    }

    // ---- 网络质量描述 ----

    val networkQualityDescription: String
        get() = when {
            currentBandwidthKbps <= 0 -> "未知"
            currentBandwidthKbps < 800 -> "弱网"
            currentBandwidthKbps < 2500 -> "一般"
            currentBandwidthKbps < 5000 -> "良好"
            else -> "优秀"
        }
}