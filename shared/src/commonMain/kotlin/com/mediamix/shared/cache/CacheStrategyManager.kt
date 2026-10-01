package com.mediamix.shared.cache

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.CachePriority
import com.mediamix.shared.models.CacheStrategySuggestion
import com.mediamix.shared.models.ViewingHabitSnapshot
import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * 智能缓存策略管理器
 *
 * 基于用户观看习惯（观看时段、偏好类型、重播频率）动态调整缓存策略。
 * 独立于 VideoCacheService，不修改其内部逻辑，仅提供策略建议。
 *
 * 数据来源：通过 [recordViewing] 记录每次观看行为，
 * 使用 multiplatform-settings 轻量级持久化。
 */
class CacheStrategyManager(
    private val settings: Settings,
) {
    private val logger = Logger.withTag("CacheStrategyManager")

    // ----------------------------------------------------------
    // Settings 键名
    // ----------------------------------------------------------
    private val keyHourHistogram = "cache_strategy_hour_histogram"
    private val keyCategoryCounts = "cache_strategy_category_counts"
    private val keyReplayCounts = "cache_strategy_replay_counts"
    private val keyLastUpdated = "cache_strategy_last_updated"

    // ----------------------------------------------------------
    // 阈值配置
    // ----------------------------------------------------------

    /** 高频时段阈值：该小时观看次数 >= 总次数 * 此比例 视为高频 */
    private val peakHourRatioThreshold = 0.06

    /** 高重播频率阈值：同一视频观看次数 >= 此值视为高重播 */
    private val highReplayThreshold = 3

    /** 偏好类型阈值：该类型观看次数 >= 总次数 * 此比例 视为偏好 */
    private val preferenceRatioThreshold = 0.15

    /** 最大追踪的视频数量（防止 Settings 过大） */
    private val maxTrackedVideos = 200

    /** 最大追踪的类型数量 */
    private val maxTrackedCategories = 30

    // ----------------------------------------------------------
    // 内存中的习惯数据
    // ----------------------------------------------------------

    /** 小时级别观看直方图（0~23 → 观看次数） */
    private val hourHistogram = mutableMapOf<Int, Int>()

    /** 类型观看次数（category → count） */
    private val categoryCounts = mutableMapOf<String, Int>()

    /** 视频重播次数（videoId → count） */
    private val replayCounts = mutableMapOf<String, Int>()

    /** 总观看次数（用于计算比例） */
    private var totalViewCount = 0

    /** 是否已初始化 */
    private var initialized = false

    /** 是否已初始化 */
    val isInitialized: Boolean get() = initialized

    // ===========================================================
    // 初始化
    // ===========================================================

    /**
     * 初始化，从 Settings 加载历史数据
     */
    fun initialize() {
        if (initialized) return

        try {
            loadFromSettings()
            initialized = true
            logger.i(
                "缓存策略管理器初始化完成，" +
                    "追踪视频: ${replayCounts.size}, " +
                    "类型: ${categoryCounts.size}, " +
                    "总观看: $totalViewCount",
            )
        } catch (e: Exception) {
            logger.e(e) { "缓存策略管理器初始化失败" }
            initialized = true // 允许降级运行
        }
    }

    // ===========================================================
    // 观看习惯追踪
    // ===========================================================

    /**
     * 记录一次观看行为
     *
     * @param videoId 视频唯一标识
     * @param category 视频类型/分类（如 "电影"、"电视剧"、"综艺" 等）
     * @param hour 观看时段（小时级别，0~23），默认使用当前时间
     */
    fun recordViewing(
        videoId: String,
        category: String? = null,
        hour: Int? = null,
    ) {
        val h = hour ?: currentHour()

        // 更新小时直方图
        hourHistogram[h] = (hourHistogram[h] ?: 0) + 1

        // 更新类型计数
        if (!category.isNullOrEmpty()) {
            categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
            trimCategoryCounts()
        }

        // 更新重播计数
        replayCounts[videoId] = (replayCounts[videoId] ?: 0) + 1
        trimReplayCounts()

        totalViewCount++

        // 持久化
        saveToSettings()

        logger.d("记录观看: videoId=$videoId, category=$category, hour=$h, 总观看=$totalViewCount")
    }

    /**
     * 获取当前观看习惯快照
     */
    fun getSnapshot(currentHour: Int? = null): ViewingHabitSnapshot {
        val h = currentHour ?: currentHour()
        val currentHourFreq = getCurrentHourFrequency(h)
        val isPeak = isPeakHour(h)
        val preferred = getPreferredCategories()
        val highReplay = getHighReplayVideoIds()
        val predicted = predictCategories(h)

        return ViewingHabitSnapshot(
            isPeakHour = isPeak,
            currentHourFrequency = currentHourFreq,
            preferredCategories = preferred,
            highReplayVideoIds = highReplay.toList(),
            predictedCategories = predicted,
        )
    }

    // ===========================================================
    // 动态策略建议
    // ===========================================================

    /**
     * 获取指定视频的缓存策略建议
     *
     * 综合考虑：当前时段、视频类型、重播频率
     */
    fun getSuggestion(
        videoId: String,
        category: String? = null,
        currentHour: Int? = null,
    ): CacheStrategySuggestion {
        if (!initialized || totalViewCount == 0) {
            return CacheStrategySuggestion.defaultSuggestion
        }

        val h = currentHour ?: currentHour()
        val isPeak = isPeakHour(h)

        // TTL 倍数
        var ttlMultiplier = 1.0
        if (isPeak) {
            ttlMultiplier *= 1.5 // 高频时段延长 TTL
        } else {
            ttlMultiplier *= 0.7 // 低频时段缩短 TTL
        }

        // 容量倍数
        var capacityMultiplier = 1.0
        if (isPeak) {
            capacityMultiplier *= 1.3 // 高频时段扩大缓存
        } else {
            capacityMultiplier *= 0.8 // 低频时段缩小缓存
        }

        // 优先级
        var priority = CachePriority.NORMAL

        // 高重播频率 → 高优先级 + 延长 TTL
        val replayCount = replayCounts[videoId] ?: 0
        if (replayCount >= highReplayThreshold) {
            priority = CachePriority.HIGH
            ttlMultiplier *= 2.0
        }

        // 用户偏好类型 → 高优先级
        if (category != null && isPreferredCategory(category)) {
            if (priority != CachePriority.HIGH) {
                priority = CachePriority.HIGH
            }
            ttlMultiplier *= 1.3
            capacityMultiplier *= 1.2
        }

        return CacheStrategySuggestion(
            ttlMultiplier = ttlMultiplier.coerceIn(0.3, 5.0),
            capacityMultiplier = capacityMultiplier.coerceIn(0.3, 3.0),
            priority = priority,
        )
    }

    /**
     * 获取当前时段的容量倍数
     */
    fun getCapacityMultiplier(currentHour: Int? = null): Double {
        if (!initialized || totalViewCount == 0) return 1.0
        val h = currentHour ?: currentHour()
        return if (isPeakHour(h)) 1.3 else 0.8
    }

    /**
     * 获取指定视频的动态 TTL（秒）
     *
     * @param baseTtl 基础 TTL（秒）
     */
    fun getDynamicTtl(
        videoId: String,
        category: String? = null,
        baseTtl: Int = 604800,
        currentHour: Int? = null,
    ): Int {
        val suggestion = getSuggestion(videoId, category = category, currentHour = currentHour)
        return (baseTtl * suggestion.ttlMultiplier).roundToInt()
    }

    /**
     * 获取指定视频的缓存优先级
     */
    fun getPriority(
        videoId: String,
        category: String? = null,
        currentHour: Int? = null,
    ): CachePriority = getSuggestion(videoId, category = category, currentHour = currentHour).priority

    // ===========================================================
    // 预测性预热
    // ===========================================================

    /**
     * 预测并预热缓存策略
     *
     * 根据当前时段和用户历史，预测接下来可能观看的内容类型，
     * 返回应提升缓存优先级的类型列表。
     * 此方法是轻量的，不做实际网络请求，仅返回策略建议。
     *
     * @return 预测的优先类型列表（按优先级降序）
     */
    fun predictAndPreheat(currentHour: Int? = null): List<String> {
        if (!initialized || totalViewCount == 0) {
            logger.d("predictAndPreheat: 无历史数据，返回空")
            return emptyList()
        }

        val h = currentHour ?: currentHour()
        val predicted = predictCategories(h)

        logger.i("predictAndPreheat: 当前时段=$h, 预测类型=$predicted")

        return predicted
    }

    /**
     * 获取预测类型对应的缓存策略建议
     *
     * 对于预测类型中的视频，返回提升后的策略建议
     */
    fun getPreheatSuggestion(
        category: String,
        currentHour: Int? = null,
    ): CacheStrategySuggestion {
        if (!isPreferredCategory(category) && !predictAndPreheat(currentHour).contains(category)) {
            return CacheStrategySuggestion.defaultSuggestion
        }

        return CacheStrategySuggestion(
            ttlMultiplier = 1.5,
            capacityMultiplier = 1.2,
            priority = CachePriority.HIGH,
        )
    }

    // ===========================================================
    // 内部方法 — 时段分析
    // ===========================================================

    /** 判断指定小时是否为高频观看时段 */
    internal fun isPeakHour(hour: Int): Boolean {
        if (totalViewCount == 0) return false
        val count = hourHistogram[hour] ?: 0
        val ratio = count.toDouble() / totalViewCount
        return ratio >= peakHourRatioThreshold
    }

    /** 获取指定小时的观看频率 (0.0~1.0) */
    internal fun getCurrentHourFrequency(hour: Int): Double {
        if (totalViewCount == 0) return 0.0
        val count = hourHistogram[hour] ?: 0
        return (count.toDouble() / totalViewCount).coerceIn(0.0, 1.0)
    }

    /**
     * 基于当前时段预测可能观看的类型
     *
     * 逻辑：找到该时段历史上最常观看的类型
     */
    internal fun predictCategories(hour: Int): List<String> {
        val hourCount = hourHistogram[hour] ?: 0
        if (hourCount == 0 || totalViewCount == 0) {
            // 无时段数据时，使用全局偏好
            return getPreferredCategories()
        }

        val preferred = getPreferredCategories()
        if (preferred.isEmpty()) return emptyList()

        // 如果当前时段活跃度高于平均，返回更多类型
        val avgHourFreq = totalViewCount / 24.0
        val currentFreq = hourHistogram[hour] ?: 0

        return when {
            currentFreq > avgHourFreq * 1.5 -> preferred.take(3) // 高频时段：返回前3个偏好类型
            currentFreq > avgHourFreq -> preferred.take(2) // 中频时段：返回前2个
            else -> preferred.take(1) // 低频时段：返回前1个
        }
    }

    // ===========================================================
    // 内部方法 — 类型偏好分析
    // ===========================================================

    /** 获取偏好类型列表（按频率降序） */
    internal fun getPreferredCategories(): List<String> {
        if (totalViewCount == 0 || categoryCounts.isEmpty()) return emptyList()

        return categoryCounts.entries
            .sortedByDescending { it.value }
            .filter { it.value.toDouble() / totalViewCount >= preferenceRatioThreshold }
            .map { it.key }
    }

    /** 判断指定类型是否为用户偏好类型 */
    internal fun isPreferredCategory(category: String): Boolean {
        if (totalViewCount == 0) return false
        val count = categoryCounts[category] ?: 0
        return count.toDouble() / totalViewCount >= preferenceRatioThreshold
    }

    /** 限制类型追踪数量 */
    private fun trimCategoryCounts() {
        if (categoryCounts.size <= maxTrackedCategories) return
        val sorted = categoryCounts.entries.sortedByDescending { it.value }
        categoryCounts.clear()
        sorted.take(maxTrackedCategories).forEach { (key, value) ->
            categoryCounts[key] = value
        }
    }

    // ===========================================================
    // 内部方法 — 重播频率分析
    // ===========================================================

    /** 获取高重播频率的 videoId 集合 */
    internal fun getHighReplayVideoIds(): Set<String> =
        replayCounts.entries
            .filter { it.value >= highReplayThreshold }
            .map { it.key }
            .toSet()

    /** 限制视频追踪数量（保留重播次数最高的） */
    private fun trimReplayCounts() {
        if (replayCounts.size <= maxTrackedVideos) return
        val sorted = replayCounts.entries.sortedByDescending { it.value }
        replayCounts.clear()
        sorted.take(maxTrackedVideos).forEach { (key, value) ->
            replayCounts[key] = value
        }
    }

    // ===========================================================
    // 持久化 — multiplatform-settings
    // ===========================================================

    /** 从 Settings 加载数据 */
    internal fun loadFromSettings() {
        try {
            // 加载小时直方图
            val hourJson = settings.getStringOrNull(keyHourHistogram)
            if (!hourJson.isNullOrEmpty()) {
                val decoded = Json.parseToJsonElement(hourJson).jsonObject
                hourHistogram.clear()
                decoded.forEach { (key, value) ->
                    hourHistogram[key.toInt()] = value.jsonPrimitive.content.toInt()
                }
            }

            // 加载类型计数
            val catJson = settings.getStringOrNull(keyCategoryCounts)
            if (!catJson.isNullOrEmpty()) {
                val decoded = Json.parseToJsonElement(catJson).jsonObject
                categoryCounts.clear()
                decoded.forEach { (key, value) ->
                    categoryCounts[key] = value.jsonPrimitive.content.toInt()
                }
            }

            // 加载重播计数
            val replayJson = settings.getStringOrNull(keyReplayCounts)
            if (!replayJson.isNullOrEmpty()) {
                val decoded = Json.parseToJsonElement(replayJson).jsonObject
                replayCounts.clear()
                decoded.forEach { (key, value) ->
                    replayCounts[key] = value.jsonPrimitive.content.toInt()
                }
            }

            // 计算总观看次数
            totalViewCount = 0
            for (count in hourHistogram.values) {
                totalViewCount += count
            }
        } catch (e: Exception) {
            logger.w(e) { "从 Settings 加载习惯数据失败" }
        }
    }

    /** 保存数据到 Settings */
    internal fun saveToSettings() {
        try {
            // 保存小时直方图
            val hourObj =
                buildJsonObject {
                    hourHistogram.forEach { (k, v) -> put(k.toString(), v) }
                }
            settings.putString(keyHourHistogram, hourObj.toString())

            // 保存类型计数
            val catObj =
                buildJsonObject {
                    categoryCounts.forEach { (k, v) -> put(k, v) }
                }
            settings.putString(keyCategoryCounts, catObj.toString())

            // 保存重播计数
            val replayObj =
                buildJsonObject {
                    replayCounts.forEach { (k, v) -> put(k, v) }
                }
            settings.putString(keyReplayCounts, replayObj.toString())

            // 记录最后更新时间
            settings.putString(keyLastUpdated, Clock.System.now().toString())
        } catch (e: Exception) {
            logger.w(e) { "保存习惯数据到 Settings 失败" }
        }
    }

    // ===========================================================
    // 时间辅助
    // ===========================================================

    /** 获取当前小时 (0~23) */
    private fun currentHour(): Int =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .hour

    // ===========================================================
    // 测试辅助
    // ===========================================================

    /** 重置所有数据（仅用于测试） */
    fun resetForTesting() {
        hourHistogram.clear()
        categoryCounts.clear()
        replayCounts.clear()
        totalViewCount = 0
        initialized = false
    }

    /** 获取总观看次数（仅用于测试/调试） */
    val totalViewCountForTest: Int get() = totalViewCount

    /** 获取小时直方图副本（仅用于测试/调试） */
    val hourHistogramForTest: Map<Int, Int> get() = hourHistogram.toMap()

    /** 获取类型计数字副本（仅用于测试/调试） */
    val categoryCountsForTest: Map<String, Int> get() = categoryCounts.toMap()

    /** 获取重播计数字副本（仅用于测试/调试） */
    val replayCountsForTest: Map<String, Int> get() = replayCounts.toMap()
}
