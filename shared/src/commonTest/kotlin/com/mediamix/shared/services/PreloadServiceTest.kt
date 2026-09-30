package com.mediamix.shared.services

import com.mediamix.shared.services.NetworkCondition
import com.mediamix.shared.services.PreloadDepthCalculator
import com.mediamix.shared.services.PreloadPriority
import com.mediamix.shared.services.PreloadStrategy
import com.mediamix.shared.services.PreloadTaskStatus
import com.mediamix.shared.services.PreloadStatusInfo
import kotlin.test.*

class PreloadDepthCalculatorTest {

    private val calc = PreloadDepthCalculator

    // ---- OFFLINE / POOR edge cases ----

    @Test
    fun offline_returnsZero() {
        assertEquals(0, calc.calculateDepth(NetworkCondition.OFFLINE))
    }

    @Test
    fun poor_returnsMinDepth() {
        assertEquals(PreloadDepthCalculator.minDepth, calc.calculateDepth(NetworkCondition.POOR))
    }

    // ---- Base depth by network ----

    @Test
    fun wifi_baseDepth3() {
        assertEquals(3, calc.calculateDepth(NetworkCondition.WIFI))
    }

    @Test
    fun lte_baseDepth2() {
        assertEquals(2, calc.calculateDepth(NetworkCondition.LTE))
    }

    @Test
    fun threeG_baseDepth1() {
        assertEquals(1, calc.calculateDepth(NetworkCondition.THREE_G))
    }

    // ---- Dwell time adjustments ----

    @Test
    fun wifi_longDwell_clampedAtMax() {
        // WiFi base=3, long dwell +1 → 4, clamped to 3
        assertEquals(3, calc.calculateDepth(NetworkCondition.WIFI, avgDwellTimeSec = 700.0))
    }

    @Test
    fun lte_longDwell_plusOne() {
        // LTE base=2, long dwell +1 → 3
        assertEquals(3, calc.calculateDepth(NetworkCondition.LTE, avgDwellTimeSec = 600.0))
    }

    @Test
    fun lte_shortDwell_minusOne() {
        // LTE base=2, short dwell -1 → 1
        assertEquals(1, calc.calculateDepth(NetworkCondition.LTE, avgDwellTimeSec = 60.0))
    }

    @Test
    fun threeG_shortDwell_clampedAtMin() {
        // 3G base=1, short dwell -1 → 0, clamped to 1
        assertEquals(1, calc.calculateDepth(NetworkCondition.THREE_G, avgDwellTimeSec = 30.0))
    }

    // ---- Bounce rate adjustments ----

    @Test
    fun wifi_highBounce_minusOne() {
        // WiFi base=3, high bounce -1 → 2
        assertEquals(2, calc.calculateDepth(NetworkCondition.WIFI, bounceRate = 0.7))
    }

    @Test
    fun wifi_lowBounce_noAdjustment() {
        // WiFi base=3, low bounce → no change → 3
        assertEquals(3, calc.calculateDepth(NetworkCondition.WIFI, bounceRate = 0.2))
    }

    // ---- Disk space adjustments ----

    @Test
    fun wifi_lowDisk_minusOne() {
        // WiFi base=3, low disk -1 → 2
        assertEquals(2, calc.calculateDepth(NetworkCondition.WIFI, availableDiskMB = 200L))
    }

    @Test
    fun wifi_sufficientDisk_noAdjustment() {
        assertEquals(3, calc.calculateDepth(NetworkCondition.WIFI, availableDiskMB = 1000L))
    }

    // ---- Combined adjustments ----

    @Test
    fun lte_allNegative_clampedToMin() {
        // LTE base=2, short dwell -1, high bounce -1, low disk -1 → -1, clamped to 1
        assertEquals(
            1,
            calc.calculateDepth(
                NetworkCondition.LTE,
                avgDwellTimeSec = 60.0,
                bounceRate = 0.8,
                availableDiskMB = 100L,
            )
        )
    }

    @Test
    fun wifi_longDwell_noNegative_maxDepth() {
        // WiFi base=3, long dwell +1 → 4 clamped to 3
        assertEquals(
            3,
            calc.calculateDepth(
                NetworkCondition.WIFI,
                avgDwellTimeSec = 900.0,
                bounceRate = 0.1,
                availableDiskMB = 2000L,
            )
        )
    }

    @Test
    fun nullAdjustments_justBaseDepth() {
        assertEquals(2, calc.calculateDepth(NetworkCondition.LTE))
    }
}

class PreloadStrategyTest {

    @Test
    fun wifi_returnsFullVideoStrategy() {
        val s = PreloadStrategy.getStrategy(NetworkCondition.WIFI)
        assertTrue(s.cacheFullVideo)
        assertEquals(3, s.preloadCount)
        assertEquals(0L, s.firstSegmentBytes)
        assertEquals(0.30, s.bandwidthRatio)
    }

    @Test
    fun lte_returnsMobileStrategy() {
        val s = PreloadStrategy.getStrategy(NetworkCondition.LTE)
        assertFalse(s.cacheFullVideo)
        assertEquals(1, s.preloadCount)
        assertEquals(512L * 1024, s.firstSegmentBytes)
        assertEquals(0.20, s.bandwidthRatio)
    }

    @Test
    fun threeG_returnsMobileStrategy() {
        val s = PreloadStrategy.getStrategy(NetworkCondition.THREE_G)
        assertFalse(s.cacheFullVideo)
        assertEquals(1, s.preloadCount)
    }

    @Test
    fun poor_returnsMinimalStrategy() {
        val s = PreloadStrategy.getStrategy(NetworkCondition.POOR)
        assertFalse(s.cacheFullVideo)
        assertEquals(0, s.preloadCount)
        assertEquals(128L * 1024, s.firstSegmentBytes)
        assertEquals(0.10, s.bandwidthRatio)
    }

    @Test
    fun offline_returnsZeroStrategy() {
        val s = PreloadStrategy.getStrategy(NetworkCondition.OFFLINE)
        assertFalse(s.cacheFullVideo)
        assertEquals(0, s.preloadCount)
        assertEquals(0L, s.firstSegmentBytes)
        assertEquals(0.0, s.bandwidthRatio)
    }
}

class PreloadPriorityTest {

    @Test
    fun priorityOrdering() {
        assertTrue(PreloadPriority.CURRENT_PLAYBACK.value < PreloadPriority.NEXT_EPISODE.value)
        assertTrue(PreloadPriority.NEXT_EPISODE.value < PreloadPriority.ADJACENT_ITEM.value)
        assertTrue(PreloadPriority.ADJACENT_ITEM.value < PreloadPriority.PLAYLIST_ITEM.value)
        assertTrue(PreloadPriority.PLAYLIST_ITEM.value < PreloadPriority.HISTORY_REPLAY.value)
    }

    @Test
    fun sortingByPriority() {
        val priorities = listOf(
            PreloadPriority.HISTORY_REPLAY,
            PreloadPriority.CURRENT_PLAYBACK,
            PreloadPriority.PLAYLIST_ITEM,
            PreloadPriority.NEXT_EPISODE,
            PreloadPriority.ADJACENT_ITEM,
        )
        val sorted = priorities.sortedBy { it.value }
        assertEquals(
            listOf(
                PreloadPriority.CURRENT_PLAYBACK,
                PreloadPriority.NEXT_EPISODE,
                PreloadPriority.ADJACENT_ITEM,
                PreloadPriority.PLAYLIST_ITEM,
                PreloadPriority.HISTORY_REPLAY,
            ),
            sorted,
        )
    }
}

class PreloadStatusInfoTest {

    @Test
    fun totalCount_sumsAll() {
        val info = PreloadStatusInfo(
            pendingCount = 2,
            downloadingCount = 1,
            completedCount = 3,
            cancelledCount = 0,
            failedCount = 1,
            currentDepth = 2,
        )
        assertEquals(7, info.totalCount)
    }

    @Test
    fun hasActiveTasks_trueWhenDownloading() {
        val info = PreloadStatusInfo(1, 1, 0, 0, 0, 2)
        assertTrue(info.hasActiveTasks)
    }

    @Test
    fun hasActiveTasks_falseWhenNoDownloading() {
        val info = PreloadStatusInfo(2, 0, 1, 0, 0, 2)
        assertFalse(info.hasActiveTasks)
    }
}
