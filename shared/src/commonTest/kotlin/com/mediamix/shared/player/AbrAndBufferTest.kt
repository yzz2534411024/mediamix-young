package com.mediamix.shared.player

import com.mediamix.shared.network.ThroughputPrediction
import com.mediamix.shared.services.NetworkCondition
import com.russhwolf.settings.MapSettings
import kotlin.test.*

class BufferManagerTest {
    @Test
    fun initialState_isLowBuffer() {
        val manager = BufferManager()
        assertTrue(manager.getIsLowBuffer())
        assertEquals(0L, manager.getCurrentBufferMs())
    }

    @Test
    fun updateBufferAboveLow_notLowBuffer() {
        var callbackValue: Boolean? = null
        val manager = BufferManager { callbackValue = it }
        manager.updateBuffer(5000)
        assertFalse(manager.getIsLowBuffer())
        assertEquals(false, callbackValue)
    }

    @Test
    fun updateBufferBelowLow_isLowBuffer() {
        val manager = BufferManager()
        manager.updateBuffer(5000)
        manager.updateBuffer(1000)
        assertTrue(manager.getIsLowBuffer())
    }

    @Test
    fun bufferStateCallbackOnlyFiresOnTransition() {
        var callbackCount = 0
        val manager = BufferManager { callbackCount++ }
        manager.updateBuffer(1000)
        assertEquals(0, callbackCount)
        manager.updateBuffer(5000)
        assertEquals(1, callbackCount)
        manager.updateBuffer(10000)
        assertEquals(1, callbackCount)
    }

    @Test
    fun bufferPercent_zero() {
        val manager = BufferManager()
        assertEquals(0.0, manager.bufferPercent)
    }

    @Test
    fun bufferPercent_half() {
        val manager = BufferManager()
        manager.updateBuffer(30000)
        assertEquals(0.5, manager.bufferPercent, 0.001)
    }

    @Test
    fun bufferPercent_clampedToOne() {
        val manager = BufferManager()
        manager.updateBuffer(100000)
        assertEquals(1.0, manager.bufferPercent)
    }

    @Test
    fun waterLinesForWifi() {
        val manager = BufferManager()
        manager.updateNetworkCondition(NetworkCondition.WIFI)
        assertEquals(BufferWaterLines.WIFI, manager.getWaterLines())
    }

    @Test
    fun waterLinesForLte() {
        val manager = BufferManager()
        manager.updateNetworkCondition(NetworkCondition.LTE)
        assertEquals(BufferWaterLines.MOBILE_4G, manager.getWaterLines())
    }

    @Test
    fun waterLinesForWeak() {
        val manager = BufferManager()
        for (condition in listOf(NetworkCondition.THREE_G, NetworkCondition.POOR, NetworkCondition.OFFLINE)) {
            manager.updateNetworkCondition(condition)
            assertEquals(BufferWaterLines.WEAK, manager.getWaterLines())
        }
    }

    @Test
    fun waterLinesChange_rechecksBufferState() {
        val manager = BufferManager()
        manager.updateBuffer(4000)
        assertFalse(manager.getIsLowBuffer())
        manager.updateNetworkCondition(NetworkCondition.LTE)
        assertTrue(manager.getIsLowBuffer())
    }
}

class ABRControllerTest {
    private fun prediction(
        kbps: Double,
        stability: Double = 0.5,
    ) = ThroughputPrediction(kbps, 0.8, 0.0, kbps, stability)

    // ---- scoreToQuality boundary tests ----

    @Test
    fun scoreToQuality_belowLowThreshold() {
        val controller = ABRController(settings = MapSettings())
        assertEquals(QualityLevel.LOW, controller.scoreToQuality(0.0))
        assertEquals(QualityLevel.LOW, controller.scoreToQuality(0.14))
    }

    @Test
    fun scoreToQuality_atLowBoundary() {
        val controller = ABRController(settings = MapSettings())
        assertEquals(QualityLevel.MEDIUM, controller.scoreToQuality(0.15))
    }

    @Test
    fun scoreToQuality_atMediumBoundary() {
        val controller = ABRController(settings = MapSettings())
        assertEquals(QualityLevel.HIGH, controller.scoreToQuality(0.35))
    }

    @Test
    fun scoreToQuality_atHighBoundary() {
        val controller = ABRController(settings = MapSettings())
        assertEquals(QualityLevel.ULTRA, controller.scoreToQuality(0.60))
    }

    // ---- Throughput score ----

    @Test
    fun throughputScore_usesPredictionWithSafetyMargin() {
        val controller = ABRController(settings = MapSettings())
        controller.updateThroughputPrediction(prediction(10000.0))
        assertEquals(0.8, controller.computeThroughputScore(), 0.001)
    }

    @Test
    fun throughputScore_fallsBackToBandwidth() {
        val controller = ABRController(settings = MapSettings())
        controller.updateBandwidth(5000.0)
        assertEquals(0.4, controller.computeThroughputScore(), 0.001)
    }

    // ---- Buffer score ----

    @Test
    fun bufferScore_boundaries() {
        val controller = ABRController(settings = MapSettings())
        controller.updateBuffer(3000)
        assertEquals(0.0, controller.computeBufferScore(), 0.001)
        controller.updateBuffer(35000)
        assertEquals(1.0, controller.computeBufferScore(), 0.001)
        controller.updateBuffer(17500)
        assertEquals(0.5, controller.computeBufferScore(), 0.001)
    }

    // ---- Stability score ----

    @Test
    fun stabilityScore_fromPredictionOrDefault() {
        val controller = ABRController(settings = MapSettings())
        assertEquals(0.5, controller.computeStabilityScore(), 0.001)
        controller.updateThroughputPrediction(prediction(5000.0, stability = 0.9))
        assertEquals(0.9, controller.computeStabilityScore(), 0.001)
    }

    // ---- Weighted score model ----

    @Test
    fun weightedScore_allHigh() {
        val controller = ABRController(settings = MapSettings())
        controller.updateThroughputPrediction(prediction(10000.0, stability = 1.0))
        controller.updateBuffer(40000)
        assertEquals(QualityLevel.ULTRA, controller.computeTargetQuality())
    }

    @Test
    fun weightedScore_allLow() {
        val controller = ABRController(settings = MapSettings())
        controller.updateThroughputPrediction(prediction(500.0, stability = 0.1))
        controller.updateBuffer(2000)
        assertEquals(QualityLevel.LOW, controller.computeTargetQuality())
    }

    // ---- Debounce mechanism ----
    // NOTE: updateBuffer/updateBandwidth/updateThroughputPrediction all call evaluate() internally.
    // Upgrade requires: 2 eval to pass debounce (pendingCount≥2), then highBandwidthStartMs is set on
    // the eval where debounce passes. A SUBSEQUENT eval checks the delay. So 4+ eval calls total.
    // IMPORTANT: clock must not return 0 (highBandwidthStartMs sentinel), use non-zero base time.

    @Test
    fun debounce_firstPredictionNoSwitch() {
        var currentTime = 1000L
        var switchedQuality: QualityLevel? = null
        val controller =
            ABRController(
                settings = MapSettings(),
                upgradeDelayMs = 5000,
                clock = { currentTime },
            )
        controller.onQualityChanged = { switchedQuality = it }
        controller.updateBuffer(40000)
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        assertNull(switchedQuality)
        assertEquals(QualityLevel.MEDIUM, controller.currentQuality.value)
    }

    @Test
    fun debounce_eventuallySwitchesAfterDelay() {
        var currentTime = 1000L
        var switchedQuality: QualityLevel? = null
        val controller =
            ABRController(
                settings = MapSettings(),
                upgradeDelayMs = 5000,
                clock = { currentTime },
            )
        controller.onQualityChanged = { switchedQuality = it }
        // eval #1 (updateBuffer): target=MEDIUM=current → reset
        controller.updateBuffer(40000)
        // eval #2: pendingCount=1
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        // eval #3: pendingCount=2 ≥ 2, highBandwidthStartMs=0 → set to now=1000
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        assertNull(switchedQuality) // just set highBandwidthStartMs

        // eval #4: pendingCount=3, highBandwidthStartMs=1000, now=7000 → 6000>=5000 → switch!
        currentTime = 7000L
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        assertEquals(QualityLevel.ULTRA, switchedQuality)
    }

    @Test
    fun debounce_targetChangeResetsCounter() {
        var switchedQuality: QualityLevel? = null
        val controller =
            ABRController(
                settings = MapSettings(),
                upgradeDelayMs = 0,
                clock = { 1000L },
            )
        controller.onQualityChanged = { switchedQuality = it }
        controller.updateBuffer(40000)
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        // Change prediction → new target, resets counter
        controller.updateThroughputPrediction(prediction(3000.0, stability = 0.5))
        assertNull(switchedQuality)
    }

    // ---- Emergency downgrade ----

    @Test
    fun emergencyDowngrade_bufferBelow5s_immediateSwitch() {
        var currentTime = 1000L
        var switchedQuality: QualityLevel? = null
        val controller =
            ABRController(
                settings = MapSettings(),
                upgradeDelayMs = 5000,
                clock = { currentTime },
            )
        controller.onQualityChanged = { switchedQuality = it }
        controller.updateBuffer(40000)
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        currentTime = 7000L
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        assertEquals(QualityLevel.ULTRA, switchedQuality)

        // Buffer drops below 5s → emergency downgrade
        currentTime = 20000L
        controller.updateBuffer(3000)
        assertEquals(QualityLevel.LOW, controller.currentQuality.value)
    }

    @Test
    fun emergencyDowngrade_alreadyLow_noSwitch() {
        var currentTime = 1000L
        var switchCount = 0
        val controller =
            ABRController(
                settings = MapSettings(),
                clock = { currentTime },
            )
        controller.onQualityChanged = { switchCount++ }
        // Trigger emergency downgrade MEDIUM → LOW
        controller.updateBuffer(2000)
        assertEquals(QualityLevel.LOW, controller.currentQuality.value)
        assertEquals(1, switchCount)

        // Already LOW, still low buffer → no additional switch
        currentTime = 12000L
        controller.updateBuffer(1000)
        assertEquals(1, switchCount)
    }

    // ---- Cooldown period ----

    @Test
    fun cooldown_preventsSwitchWithin10s() {
        var currentTime = 1000L
        var switchedQuality: QualityLevel? = null
        val controller =
            ABRController(
                settings = MapSettings(),
                clock = { currentTime },
            )
        controller.onQualityChanged = { switchedQuality = it }

        // Emergency downgrade at t=1000
        controller.updateBuffer(3000)
        assertEquals(QualityLevel.LOW, controller.currentQuality.value)
        assertNotNull(switchedQuality)

        // At t=6000 (5s later, within 10s cooldown)
        currentTime = 6000L
        controller.updateBuffer(40000)
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        assertEquals(QualityLevel.LOW, controller.currentQuality.value)
    }

    @Test
    fun cooldown_allowsSwitchAfter10s() {
        var currentTime = 1000L
        val controller =
            ABRController(
                settings = MapSettings(),
                upgradeDelayMs = 0,
                clock = { currentTime },
            )
        // Emergency downgrade at t=1000
        controller.updateBuffer(3000)
        assertEquals(QualityLevel.LOW, controller.currentQuality.value)

        // At t=16000 (15s later, past 10s cooldown)
        currentTime = 16000L
        controller.updateBuffer(40000)
        // eval #1 (updateBuffer): target=MEDIUM (no bandwidth), pendingCount=1 for MEDIUM
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        // eval #2: target=ULTRA (new target), pendingCount resets to 1 for ULTRA
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        // eval #3: pendingCount=2, highBwStart=0 → set to now=16000
        controller.updateThroughputPrediction(prediction(9000.0, stability = 0.9))
        // eval #4: pendingCount=3, highBwStart=16000, 16000-16000=0 >= 0 (delay=0) → switch!
        assertEquals(QualityLevel.ULTRA, controller.currentQuality.value)
    }

    // ---- Quality preference persistence ----

    @Test
    fun saveAndLoadQualityPreference() {
        val settings = MapSettings()
        val controller = ABRController(settings = settings)
        controller.saveQualityPreference(QualityLevel.HIGH)
        assertEquals(QualityLevel.HIGH, controller.loadQualityPreference())
    }

    @Test
    fun loadQualityPreference_defaultIsMedium() {
        val controller = ABRController(settings = MapSettings())
        assertEquals(QualityLevel.MEDIUM, controller.loadQualityPreference())
    }

    // ---- Network quality description ----

    @Test
    fun networkQualityDescription_allLevels() {
        val controller = ABRController(settings = MapSettings())
        assertEquals("未知", controller.networkQualityDescription)
        controller.updateBandwidth(500.0)
        assertEquals("弱网", controller.networkQualityDescription)
        controller.updateBandwidth(1500.0)
        assertEquals("一般", controller.networkQualityDescription)
        controller.updateBandwidth(3500.0)
        assertEquals("良好", controller.networkQualityDescription)
        controller.updateBandwidth(8000.0)
        assertEquals("优秀", controller.networkQualityDescription)
    }
}
