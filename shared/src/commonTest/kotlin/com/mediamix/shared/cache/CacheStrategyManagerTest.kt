package com.mediamix.shared.cache

import com.mediamix.shared.models.CachePriority
import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheStrategyManagerTest {
    private lateinit var settings: MapSettings
    private lateinit var manager: CacheStrategyManager

    @BeforeTest
    fun setUp() {
        settings = MapSettings()
        manager = CacheStrategyManager(settings)
        manager.initialize()
    }

    // ===========================================================
    // 观看记录追踪
    // ===========================================================

    @Test
    fun recordViewing_updatesHourHistogram() {
        manager.recordViewing("v1", hour = 10)
        manager.recordViewing("v2", hour = 10)
        manager.recordViewing("v3", hour = 14)

        assertEquals(2, manager.hourHistogramForTest[10])
        assertEquals(1, manager.hourHistogramForTest[14])
        assertEquals(3, manager.totalViewCountForTest)
    }

    @Test
    fun recordViewing_updatesCategoryCounts() {
        manager.recordViewing("v1", category = "电影", hour = 10)
        manager.recordViewing("v2", category = "电影", hour = 11)
        manager.recordViewing("v3", category = "电视剧", hour = 12)

        assertEquals(2, manager.categoryCountsForTest["电影"])
        assertEquals(1, manager.categoryCountsForTest["电视剧"])
    }

    @Test
    fun recordViewing_updatesReplayCounts() {
        manager.recordViewing("v1", hour = 10)
        manager.recordViewing("v1", hour = 11)
        manager.recordViewing("v1", hour = 12)
        manager.recordViewing("v2", hour = 10)

        assertEquals(3, manager.replayCountsForTest["v1"])
        assertEquals(1, manager.replayCountsForTest["v2"])
    }

    @Test
    fun recordViewing_nullCategory_doesNotUpdateCategoryCounts() {
        manager.recordViewing("v1", hour = 10)
        assertTrue(manager.categoryCountsForTest.isEmpty())
    }

    @Test
    fun recordViewing_emptyCategory_doesNotUpdateCategoryCounts() {
        manager.recordViewing("v1", category = "", hour = 10)
        assertTrue(manager.categoryCountsForTest.isEmpty())
    }

    // ===========================================================
    // 高频时段检测
    // ===========================================================

    @Test
    fun isPeakHour_noData_returnsFalse() {
        assertFalse(manager.isPeakHour(10))
    }

    @Test
    fun isPeakHour_highRatio_returnsTrue() {
        // 需要 ratio >= 0.06，即 count/total >= 0.06
        // 在 hour=20 放 6 次，总共 10 次 → ratio = 0.6
        repeat(5) { manager.recordViewing("v$it", hour = 10) }
        repeat(5) { manager.recordViewing("v${it + 5}", hour = 20) }

        assertTrue(manager.isPeakHour(10))
        assertTrue(manager.isPeakHour(20))
    }

    @Test
    fun isPeakHour_lowRatio_returnsFalse() {
        // 在 hour=10 放 1 次，总共 100 次 → ratio = 0.01 < 0.06
        manager.recordViewing("v0", hour = 10)
        repeat(99) { manager.recordViewing("v${it + 1}", hour = 12) }

        assertFalse(manager.isPeakHour(10))
    }

    // ===========================================================
    // 偏好类型识别
    // ===========================================================

    @Test
    fun getPreferredCategories_noData_returnsEmpty() {
        assertTrue(manager.getPreferredCategories().isEmpty())
    }

    @Test
    fun getPreferredCategories_returnsHighFrequencyCategories() {
        // 需要 count/total >= 0.15
        // 电影 20 次, 电视剧 5 次, 综艺 1 次 → total = 26
        // 电影: 20/26 = 0.77 ✓, 电视剧: 5/26 = 0.19 ✓, 综艺: 1/26 = 0.038 ✗
        repeat(20) { manager.recordViewing("v$it", category = "电影", hour = 10) }
        repeat(5) { manager.recordViewing("v${it + 20}", category = "电视剧", hour = 11) }
        manager.recordViewing("v25", category = "综艺", hour = 12)

        val preferred = manager.getPreferredCategories()
        assertEquals(2, preferred.size)
        assertEquals("电影", preferred[0]) // 按频率降序
        assertEquals("电视剧", preferred[1])
    }

    @Test
    fun isPreferredCategory_aboveThreshold_returnsTrue() {
        repeat(20) { manager.recordViewing("v$it", category = "电影", hour = 10) }
        repeat(5) { manager.recordViewing("v${it + 20}", category = "电视剧", hour = 11) }

        assertTrue(manager.isPreferredCategory("电影"))
        assertTrue(manager.isPreferredCategory("电视剧"))
    }

    @Test
    fun isPreferredCategory_belowThreshold_returnsFalse() {
        repeat(20) { manager.recordViewing("v$it", category = "电影", hour = 10) }
        manager.recordViewing("v20", category = "综艺", hour = 12)

        // 综艺: 1/21 = 0.047 < 0.15
        assertFalse(manager.isPreferredCategory("综艺"))
    }

    // ===========================================================
    // 高重播视频识别
    // ===========================================================

    @Test
    fun getHighReplayVideoIds_noData_returnsEmpty() {
        assertTrue(manager.getHighReplayVideoIds().isEmpty())
    }

    @Test
    fun getHighReplayVideoIds_returnsVideosAboveThreshold() {
        // 阈值 = 3
        repeat(3) { manager.recordViewing("v1", hour = 10) }
        repeat(5) { manager.recordViewing("v2", hour = 11) }
        repeat(2) { manager.recordViewing("v3", hour = 12) } // 不够

        val highReplay = manager.getHighReplayVideoIds()
        assertEquals(2, highReplay.size)
        assertTrue(highReplay.contains("v1"))
        assertTrue(highReplay.contains("v2"))
        assertFalse(highReplay.contains("v3"))
    }

    // ===========================================================
    // 动态 TTL 计算
    // ===========================================================

    @Test
    fun getDynamicTtl_noData_returnsBaseTtl() {
        val ttl = manager.getDynamicTtl("v1", baseTtl = 604800)
        assertEquals(604800, ttl)
    }

    @Test
    fun getDynamicTtl_peakHour_highReplay_extendedTtl() {
        // 建立数据：hour=20 是高频时段，v1 是高重播
        repeat(5) { manager.recordViewing("other$it", hour = 10) }
        repeat(5) { manager.recordViewing("v1", hour = 20) }

        val ttl = manager.getDynamicTtl("v1", currentHour = 20)
        // peak: 1.5, high replay: 2.0 → 1.5 * 2.0 = 3.0
        // 604800 * 3.0 = 1814400
        assertEquals(1814400, ttl)
    }

    @Test
    fun getDynamicTtl_offPeak_lowReplay_reducedTtl() {
        // 建立数据：hour=10 不是高频时段 (1/21 = 0.048 < 0.06)
        repeat(20) { manager.recordViewing("other$it", hour = 20) }
        manager.recordViewing("v1", hour = 10) // 只看了 1 次

        val ttl = manager.getDynamicTtl("v1", currentHour = 10)
        // off-peak: 0.7 → 604800 * 0.7 = 423360
        assertEquals(423360, ttl)
    }

    @Test
    fun getDynamicTtl_preferredCategory_extendedTtl() {
        // 建立偏好类型
        repeat(20) { manager.recordViewing("v$it", category = "电影", hour = 10) }

        val ttl = manager.getDynamicTtl("v_new", category = "电影", currentHour = 10)
        // peak: 1.5, preferred: 1.3 → 1.5 * 1.3 = 1.95
        // 604800 * 1.95 = 1179360
        assertEquals(1179360, ttl)
    }

    // ===========================================================
    // 容量倍数计算
    // ===========================================================

    @Test
    fun getCapacityMultiplier_noData_returns1() {
        assertEquals(1.0, manager.getCapacityMultiplier())
    }

    @Test
    fun getCapacityMultiplier_peakHour_returns1_3() {
        repeat(5) { manager.recordViewing("v$it", hour = 20) }
        assertEquals(1.3, manager.getCapacityMultiplier(currentHour = 20))
    }

    @Test
    fun getCapacityMultiplier_offPeak_returns0_8() {
        repeat(5) { manager.recordViewing("v$it", hour = 20) }
        assertEquals(0.8, manager.getCapacityMultiplier(currentHour = 3))
    }

    // ===========================================================
    // 策略建议
    // ===========================================================

    @Test
    fun getSuggestion_noData_returnsDefault() {
        val suggestion = manager.getSuggestion("v1")
        assertEquals(1.0, suggestion.ttlMultiplier)
        assertEquals(1.0, suggestion.capacityMultiplier)
        assertEquals(CachePriority.NORMAL, suggestion.priority)
    }

    @Test
    fun getSuggestion_highReplay_returnsHighPriority() {
        repeat(5) { manager.recordViewing("other$it", hour = 10) }
        repeat(3) { manager.recordViewing("v1", hour = 10) }

        val suggestion = manager.getSuggestion("v1", currentHour = 10)
        assertEquals(CachePriority.HIGH, suggestion.priority)
    }

    @Test
    fun getSuggestion_preferredCategory_returnsHighPriority() {
        repeat(20) { manager.recordViewing("v$it", category = "电影", hour = 10) }

        val suggestion = manager.getSuggestion("v_new", category = "电影", currentHour = 10)
        assertEquals(CachePriority.HIGH, suggestion.priority)
    }

    @Test
    fun getSuggestion_ttlMultiplier_clampedToRange() {
        // 构造极端场景：peak + high replay + preferred
        repeat(20) { manager.recordViewing("v$it", category = "电影", hour = 20) }
        repeat(3) { manager.recordViewing("v1", category = "电影", hour = 20) }

        val suggestion = manager.getSuggestion("v1", category = "电影", currentHour = 20)
        // ttl: 1.5 * 2.0 * 1.3 = 3.9, within [0.3, 5.0]
        assertTrue(suggestion.ttlMultiplier in 0.3..5.0)
        assertTrue(suggestion.capacityMultiplier in 0.3..3.0)
    }

    // ===========================================================
    // 预测预热
    // ===========================================================

    @Test
    fun predictAndPreheat_noData_returnsEmpty() {
        assertTrue(manager.predictAndPreheat(currentHour = 10).isEmpty())
    }

    @Test
    fun predictAndPreheat_withData_returnsPredictedCategories() {
        // 建立偏好
        repeat(20) { manager.recordViewing("v$it", category = "电影", hour = 20) }
        repeat(5) { manager.recordViewing("v${it + 20}", category = "电视剧", hour = 20) }

        val predicted = manager.predictAndPreheat(currentHour = 20)
        assertTrue(predicted.isNotEmpty())
        assertTrue(predicted.contains("电影"))
    }

    @Test
    fun getPreheatSuggestion_preferredCategory_returnsElevated() {
        repeat(20) { manager.recordViewing("v$it", category = "电影", hour = 10) }

        val suggestion = manager.getPreheatSuggestion("电影", currentHour = 10)
        assertEquals(CachePriority.HIGH, suggestion.priority)
        assertEquals(1.5, suggestion.ttlMultiplier)
        assertEquals(1.2, suggestion.capacityMultiplier)
    }

    @Test
    fun getPreheatSuggestion_unknownCategory_returnsDefault() {
        repeat(20) { manager.recordViewing("v$it", category = "电影", hour = 10) }

        val suggestion = manager.getPreheatSuggestion("纪录片", currentHour = 10)
        assertEquals(CachePriority.NORMAL, suggestion.priority)
        assertEquals(1.0, suggestion.ttlMultiplier)
    }

    // ===========================================================
    // 持久化加载/保存
    // ===========================================================

    @Test
    fun saveAndLoad_persistsData() {
        // 记录一些数据
        repeat(5) { manager.recordViewing("v1", category = "电影", hour = 20) }
        repeat(3) { manager.recordViewing("v2", category = "电视剧", hour = 10) }

        // 创建新的 manager，使用相同的 settings
        val newManager = CacheStrategyManager(settings)
        newManager.initialize()

        assertEquals(8, newManager.totalViewCountForTest)
        assertEquals(5, newManager.hourHistogramForTest[20])
        assertEquals(3, newManager.hourHistogramForTest[10])
        assertEquals(5, newManager.categoryCountsForTest["电影"])
        assertEquals(3, newManager.categoryCountsForTest["电视剧"])
        assertEquals(5, newManager.replayCountsForTest["v1"])
        assertEquals(3, newManager.replayCountsForTest["v2"])
    }

    @Test
    fun loadFromSettings_emptySettings_noData() {
        val newManager = CacheStrategyManager(MapSettings())
        newManager.initialize()

        assertEquals(0, newManager.totalViewCountForTest)
        assertTrue(newManager.hourHistogramForTest.isEmpty())
        assertTrue(newManager.categoryCountsForTest.isEmpty())
        assertTrue(newManager.replayCountsForTest.isEmpty())
    }

    // ===========================================================
    // 快照
    // ===========================================================

    @Test
    fun getSnapshot_returnsCorrectData() {
        repeat(20) { manager.recordViewing("v$it", category = "电影", hour = 20) }
        repeat(3) { manager.recordViewing("v1", hour = 20) }

        val snapshot = manager.getSnapshot(currentHour = 20)
        assertTrue(snapshot.isPeakHour)
        assertTrue(snapshot.currentHourFrequency > 0)
        assertTrue(snapshot.preferredCategories.contains("电影"))
        assertTrue(snapshot.highReplayVideoIds.contains("v1"))
    }

    @Test
    fun getSnapshot_noData_returnsDefault() {
        val snapshot = manager.getSnapshot(currentHour = 10)
        assertFalse(snapshot.isPeakHour)
        assertEquals(0.0, snapshot.currentHourFrequency)
        assertTrue(snapshot.preferredCategories.isEmpty())
        assertTrue(snapshot.highReplayVideoIds.isEmpty())
    }

    // ===========================================================
    // 修剪
    // ===========================================================

    @Test
    fun trimCategoryCounts_limitsToMaxTracked() {
        // 先添加高频类型，确保其 count 足够高不会被 trim 掉
        repeat(100) { i ->
            manager.recordViewing("hot$i", category = "热门类型", hour = 10)
        }
        // 再添加超过 30 个类型
        repeat(40) { i ->
            manager.recordViewing("v$i", category = "类型$i", hour = 10)
        }

        assertTrue(manager.categoryCountsForTest.size <= 30)
        // 热门类型应该被保留
        assertEquals(100, manager.categoryCountsForTest["热门类型"])
    }

    @Test
    fun trimReplayCounts_limitsToMaxTracked() {
        // 添加超过 200 个视频
        repeat(250) { i ->
            manager.recordViewing("video_$i", hour = 10)
        }

        assertTrue(manager.replayCountsForTest.size <= 200)
    }

    // ===========================================================
    // 初始化
    // ===========================================================

    @Test
    fun initialize_setsIsInitialized() {
        val newManager = CacheStrategyManager(MapSettings())
        assertFalse(newManager.isInitialized)
        newManager.initialize()
        assertTrue(newManager.isInitialized)
    }

    @Test
    fun initialize_doubleInit_noEffect() {
        manager.recordViewing("v1", hour = 10)
        manager.initialize() // 第二次初始化应该被忽略
        assertTrue(manager.isInitialized)
        assertEquals(1, manager.totalViewCountForTest)
    }

    // ===========================================================
    // 重置
    // ===========================================================

    @Test
    fun resetForTesting_clearsAllData() {
        manager.recordViewing("v1", category = "电影", hour = 10)
        manager.resetForTesting()

        assertEquals(0, manager.totalViewCountForTest)
        assertTrue(manager.hourHistogramForTest.isEmpty())
        assertTrue(manager.categoryCountsForTest.isEmpty())
        assertTrue(manager.replayCountsForTest.isEmpty())
        assertFalse(manager.isInitialized)
    }
}
