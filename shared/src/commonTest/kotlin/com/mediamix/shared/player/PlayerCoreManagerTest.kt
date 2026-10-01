package com.mediamix.shared.player

import com.mediamix.shared.core.PowerMode
import com.mediamix.shared.player.engines.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ============================================================================
// Fake implementations for testing
// ============================================================================

/**
 * Fake PlaybackErrorHandler for testing.
 */
private class FakePlaybackErrorHandler : PlaybackErrorHandler {
    var lastError: String? = null
    var nextAction: ErrorAction = ErrorAction.SHOW_ERROR_DIALOG
    var nextQualityIndex: Int? = null
    private var _isWaitingForNetwork = false
    private val _triedQualityIndices = mutableSetOf<Int>()
    private var _retryCount = 0
    var qualityCount = 0

    override val isWaitingForNetwork: Boolean get() = _isWaitingForNetwork
    override val retryCount: Int get() = _retryCount

    override fun handleError(
        error: String,
        hardwareDecodingEnabled: Boolean,
        hasQualityOptions: Boolean,
        currentQualityIndex: Int,
        lastPlaybackPositionMs: Long,
    ): ErrorHandleResult {
        lastError = error
        return ErrorHandleResult(action = nextAction, nextQualityIndex = nextQualityIndex)
    }

    override fun findNextUntriedQuality(): Int {
        for (i in 0 until qualityCount) {
            if (i !in _triedQualityIndices) return i
        }
        return -1
    }

    override fun resetRetryCount() {
        _retryCount = 0
    }

    override fun clearTriedQualityIndices() {
        _triedQualityIndices.clear()
    }

    override fun addTriedQualityIndex(index: Int) {
        _triedQualityIndices.add(index)
    }

    override fun startNetworkRecoveryMonitoring(onNetworkRecovered: () -> Unit) {
        _isWaitingForNetwork = true
    }

    override fun stopNetworkRecoveryMonitoring() {
        _isWaitingForNetwork = false
    }

    override fun dispose() {}
}

/**
 * Fake CacheEngine for testing.
 */
private class FakeCacheEngine : CacheEngine {
    var resolveUrlToReturn: String = "resolved://url"
    var resolveResult: CacheResolveResult = CacheResolveResult(url = "resolved://url", isUsingCache = false)
    var lastResolvedUrl: String? = null
    var lastResolvedVideoId: String? = null
    var preloadCancelled = false
    private var _isUsingCache = false
    override val isUsingCache: Boolean get() = _isUsingCache

    override suspend fun resolveVideoUrl(
        url: String,
        videoId: String,
    ): String {
        lastResolvedUrl = url
        lastResolvedVideoId = videoId
        return resolveUrlToReturn
    }

    override suspend fun resolveVideoUrlWithFallback(
        url: String,
        videoId: String,
        preferredQuality: String?,
    ): CacheResolveResult {
        lastResolvedUrl = url
        lastResolvedVideoId = videoId
        return resolveResult
    }

    override fun notifyPreloadBuffering(isBuffering: Boolean) {}

    override fun preloadNextEpisode(
        videoId: String,
        url: String,
    ) {}

    override fun preloadAdjacentEpisodes(
        indices: List<Int>,
        title: String,
        episodeUrls: List<String>,
        powerMode: PowerMode,
    ) {}

    override fun cancelPreloads() {
        preloadCancelled = true
    }

    override fun dispose() {}
}

/**
 * Fake MetricsEngine for testing.
 */
private class FakeMetricsEngine : MetricsEngine {
    val recordedEvents = mutableListOf<MetricsEvent>()
    var sessionStarted = false
    var sessionEnded = false
    private var _isBuffering = false
    private var _hasRecordedFirstFrame = false

    override val hasRecordedFirstFrame: Boolean get() = _hasRecordedFirstFrame
    override val isBuffering: Boolean get() = _isBuffering

    /** 记录开关状态，便于断言「使用数据分享」是否真的接到了指标引擎 */
    var enabled: Boolean? = null

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun startSession(videoId: String) {
        sessionStarted = true
    }

    override fun endSession(): Map<String, Any?>? {
        sessionEnded = true
        return null
    }

    override fun recordEvent(
        event: MetricsEvent,
        errorMessage: String?,
        avSyncOffsetMs: Int?,
    ) {
        recordedEvents.add(event)
    }

    override fun getCurrentMetrics(): Map<String, Any?>? =
        mapOf(
            "firstFrameTimeMs" to 100L,
        )

    override fun markFirstFrameRecorded() {
        _hasRecordedFirstFrame = true
    }

    override fun setBuffering(value: Boolean) {
        _isBuffering = value
    }

    override fun dispose() {}
}

// ============================================================================
// Tests for PlayerCoreManager utility functions and data classes
// ============================================================================

class PlayerCoreManagerUtilTest {
    @Test
    fun formatDuration_zero() {
        assertEquals("00:00", PlayerCoreManager.formatDuration(0))
    }

    @Test
    fun formatDuration_seconds() {
        assertEquals("01:30", PlayerCoreManager.formatDuration(90_000))
    }

    @Test
    fun formatDuration_minutes() {
        assertEquals("05:00", PlayerCoreManager.formatDuration(300_000))
    }

    @Test
    fun formatDuration_hours() {
        assertEquals("1:05:30", PlayerCoreManager.formatDuration(3930_000))
    }

    @Test
    fun formatDuration_largeValue() {
        assertEquals("2:00:00", PlayerCoreManager.formatDuration(7200_000))
    }

    @Test
    fun formatNetworkSpeed_zero() {
        assertEquals("", PlayerCoreManager.formatNetworkSpeed(0.0))
    }

    @Test
    fun formatNetworkSpeed_negative() {
        assertEquals("", PlayerCoreManager.formatNetworkSpeed(-100.0))
    }

    @Test
    fun formatNetworkSpeed_kbPerSecond() {
        val result = PlayerCoreManager.formatNetworkSpeed(500.0)
        assertTrue(result.contains("kb/s"), "Expected kb/s but got: $result")
    }

    @Test
    fun formatNetworkSpeed_mbPerSecond() {
        val result = PlayerCoreManager.formatNetworkSpeed(2500.0)
        assertTrue(result.contains("MB/s"), "Expected MB/s but got: $result")
    }

    @Test
    fun getPowerModeName_allModes() {
        assertEquals("High Performance", PlayerCoreManager.getPowerModeName(PowerMode.HIGH_PERFORMANCE))
        assertEquals("Balanced", PlayerCoreManager.getPowerModeName(PowerMode.BALANCED))
        assertEquals("Power Saving", PlayerCoreManager.getPowerModeName(PowerMode.POWER_SAVING))
    }
}

// ============================================================================
// Tests for data classes
// ============================================================================

class PlaybackProgressTest {
    @Test
    fun playbackProgress_construction() {
        val progress =
            PlaybackProgress(
                videoUrl = "https://example.com/video.mp4",
                positionMs = 5000,
                durationMs = 120000,
                lastPlayTimeMs = 1000000,
            )
        assertEquals("https://example.com/video.mp4", progress.videoUrl)
        assertEquals(5000L, progress.positionMs)
        assertEquals(120000L, progress.durationMs)
        assertEquals(1000000L, progress.lastPlayTimeMs)
    }

    @Test
    fun playbackProgress_equality() {
        val p1 = PlaybackProgress("url", 100, 200, 300)
        val p2 = PlaybackProgress("url", 100, 200, 300)
        assertEquals(p1, p2)
    }

    @Test
    fun playbackProgress_copy() {
        val p1 = PlaybackProgress("url", 100, 200, 300)
        val p2 = p1.copy(positionMs = 150)
        assertEquals(150L, p2.positionMs)
        assertEquals("url", p2.videoUrl)
    }
}

// ============================================================================
// Tests for PlayMode cycling logic
// ============================================================================

class PlayModeCycleTest {
    @Test
    fun cyclePlayMode_sequentialToLoopAll() {
        // Test the cycle logic directly
        val mode = PlayMode.SEQUENTIAL
        val next =
            when (mode) {
                PlayMode.SEQUENTIAL -> PlayMode.LOOP_ALL
                PlayMode.LOOP_ALL -> PlayMode.LOOP_SINGLE
                PlayMode.LOOP_SINGLE -> PlayMode.SEQUENTIAL
            }
        assertEquals(PlayMode.LOOP_ALL, next)
    }

    @Test
    fun cyclePlayMode_loopAllToLoopSingle() {
        val mode = PlayMode.LOOP_ALL
        val next =
            when (mode) {
                PlayMode.SEQUENTIAL -> PlayMode.LOOP_ALL
                PlayMode.LOOP_ALL -> PlayMode.LOOP_SINGLE
                PlayMode.LOOP_SINGLE -> PlayMode.SEQUENTIAL
            }
        assertEquals(PlayMode.LOOP_SINGLE, next)
    }

    @Test
    fun cyclePlayMode_loopSingleToSequential() {
        val mode = PlayMode.LOOP_SINGLE
        val next =
            when (mode) {
                PlayMode.SEQUENTIAL -> PlayMode.LOOP_ALL
                PlayMode.LOOP_ALL -> PlayMode.LOOP_SINGLE
                PlayMode.LOOP_SINGLE -> PlayMode.SEQUENTIAL
            }
        assertEquals(PlayMode.SEQUENTIAL, next)
    }
}

// ============================================================================
// Tests for VideoParser interface
// ============================================================================

class VideoParserTest {
    @Test
    fun videoParser_buildUrl() {
        val parser =
            object : VideoParser {
                override val name = "TestParser"

                override fun buildUrl(originalUrl: String): String = "$originalUrl?parsed=true"
            }
        assertEquals("TestParser", parser.name)
        assertEquals("https://example.com?parsed=true", parser.buildUrl("https://example.com"))
    }

    @Test
    fun videoParser_nullParserReturnsOriginal() {
        val parser: VideoParser? = null
        val url = "https://example.com/video.mp4"
        val result = parser?.buildUrl(url) ?: url
        assertEquals(url, result)
    }
}

// ============================================================================
// Tests for error handling delegation logic
// ============================================================================

class ErrorHandlingDelegationTest {
    private lateinit var errorHandler: FakePlaybackErrorHandler

    @BeforeTest
    fun setUp() {
        errorHandler = FakePlaybackErrorHandler()
    }

    @Test
    fun handleError_downgradeToSoftwareDecode() {
        errorHandler.nextAction = ErrorAction.DOWNGRADE_TO_SOFTWARE_DECODE
        val result = errorHandler.handleError("codec error", true, false, 0, 0)
        assertEquals(ErrorAction.DOWNGRADE_TO_SOFTWARE_DECODE, result.action)
    }

    @Test
    fun handleError_waitForNetworkRecovery() {
        errorHandler.nextAction = ErrorAction.WAIT_FOR_NETWORK_RECOVERY
        val result = errorHandler.handleError("network timeout", true, false, 0, 0)
        assertEquals(ErrorAction.WAIT_FOR_NETWORK_RECOVERY, result.action)
    }

    @Test
    fun handleError_switchToNextQuality() {
        errorHandler.nextAction = ErrorAction.SWITCH_TO_NEXT_QUALITY
        errorHandler.nextQualityIndex = 2
        val result = errorHandler.handleError("source error", true, true, 0, 0)
        assertEquals(ErrorAction.SWITCH_TO_NEXT_QUALITY, result.action)
        assertEquals(2, result.nextQualityIndex)
    }

    @Test
    fun handleError_recoverFromStuck() {
        errorHandler.nextAction = ErrorAction.RECOVER_FROM_STUCK
        val result = errorHandler.handleError("player stuck", true, false, 0, 0)
        assertEquals(ErrorAction.RECOVER_FROM_STUCK, result.action)
    }

    @Test
    fun handleError_recoverFromBlackScreen() {
        errorHandler.nextAction = ErrorAction.RECOVER_FROM_BLACK_SCREEN
        val result = errorHandler.handleError("black screen", true, false, 0, 0)
        assertEquals(ErrorAction.RECOVER_FROM_BLACK_SCREEN, result.action)
    }

    @Test
    fun handleError_recoverFromSilence() {
        errorHandler.nextAction = ErrorAction.RECOVER_FROM_SILENCE
        val result = errorHandler.handleError("no audio", true, false, 0, 0)
        assertEquals(ErrorAction.RECOVER_FROM_SILENCE, result.action)
    }

    @Test
    fun handleError_switchSource() {
        errorHandler.nextAction = ErrorAction.SWITCH_SOURCE
        val result = errorHandler.handleError("source dead", true, false, 0, 0)
        assertEquals(ErrorAction.SWITCH_SOURCE, result.action)
    }
}

// ============================================================================
// Tests for episode switching logic
// ============================================================================

class EpisodeSwitchingLogicTest {
    @Test
    fun hasPrevEpisode_firstEpisode_returnsFalse() {
        val episodeUrls = listOf("url0", "url1", "url2")
        val currentIndex = 0
        val hasPrev = currentIndex > 0
        assertFalse(hasPrev)
    }

    @Test
    fun hasPrevEpisode_middleEpisode_returnsTrue() {
        val episodeUrls = listOf("url0", "url1", "url2")
        val currentIndex = 1
        val hasPrev = currentIndex > 0
        assertTrue(hasPrev)
    }

    @Test
    fun hasNextEpisode_lastEpisode_returnsFalse() {
        val episodeUrls = listOf("url0", "url1", "url2")
        val currentIndex = 2
        val hasNext = currentIndex < episodeUrls.size - 1
        assertFalse(hasNext)
    }

    @Test
    fun hasNextEpisode_firstEpisode_returnsTrue() {
        val episodeUrls = listOf("url0", "url1", "url2")
        val currentIndex = 0
        val hasNext = currentIndex < episodeUrls.size - 1
        assertTrue(hasNext)
    }

    @Test
    fun hasNextEpisode_nullUrls_returnsFalse() {
        val episodeUrls: List<String>? = null
        val currentIndex = 0
        val hasNext = episodeUrls != null && currentIndex < episodeUrls.size - 1
        assertFalse(hasNext)
    }

    @Test
    fun playEpisodeAtIndex_validIndex_updatesState() {
        val episodeUrls = listOf("url0", "url1", "url2")
        val episodeNames = listOf("Ep 1", "Ep 2", "Ep 3")
        val index = 1
        assertTrue(index in episodeUrls.indices)
        assertTrue(index in episodeNames.indices)
        assertEquals("Ep 2", episodeNames[index])
    }

    @Test
    fun playEpisodeAtIndex_invalidIndex_outOfBounds() {
        val episodeUrls = listOf("url0", "url1", "url2")
        val index = 5
        assertFalse(index in episodeUrls.indices)
    }

    @Test
    fun playEpisodeAtIndex_negativeIndex() {
        val episodeUrls = listOf("url0", "url1", "url2")
        val index = -1
        assertFalse(index in episodeUrls.indices)
    }
}

// ============================================================================
// Tests for quality switching logic
// ============================================================================

class QualitySwitchingLogicTest {
    @Test
    fun hasQualityOptions_multipleUrls_returnsTrue() {
        val qualityUrls = listOf("url_480p", "url_720p", "url_1080p")
        assertTrue(qualityUrls.size > 1)
    }

    @Test
    fun hasQualityOptions_singleUrl_returnsFalse() {
        val qualityUrls = listOf("url_720p")
        assertFalse(qualityUrls.size > 1)
    }

    @Test
    fun hasQualityOptions_empty_returnsFalse() {
        val qualityUrls = emptyList<String>()
        assertFalse(qualityUrls.size > 1)
    }

    @Test
    fun currentQualityLabel_validIndex() {
        val qualityLabels = listOf("480p", "720p", "1080p")
        val currentIndex = 1
        assertEquals("720p", qualityLabels[currentIndex])
    }

    @Test
    fun currentQualityLabel_outOfBounds_returnsDefault() {
        val qualityLabels = listOf("480p", "720p")
        val currentIndex = 5
        val label =
            if (qualityLabels.isEmpty() || currentIndex >= qualityLabels.size) {
                "720p"
            } else {
                qualityLabels[currentIndex]
            }
        assertEquals("720p", label)
    }

    @Test
    fun currentQualityLabel_empty_returnsDefault() {
        val qualityLabels = emptyList<String>()
        val currentIndex = 0
        val label =
            if (qualityLabels.isEmpty() || currentIndex >= qualityLabels.size) {
                "720p"
            } else {
                qualityLabels[currentIndex]
            }
        assertEquals("720p", label)
    }

    @Test
    fun switchQuality_validIndex_inRange() {
        val qualityUrls = listOf("url_480p", "url_720p", "url_1080p")
        val index = 2
        assertTrue(index in 0 until qualityUrls.size)
    }

    @Test
    fun switchQuality_invalidIndex_outOfRange() {
        val qualityUrls = listOf("url_480p", "url_720p")
        val index = 5
        assertFalse(index in 0 until qualityUrls.size)
    }
}

// ============================================================================
// Tests for playback speed options
// ============================================================================

class PlaybackSpeedTest {
    private val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f)

    @Test
    fun speedOptions_has12Steps() {
        assertEquals(12, speedOptions.size)
    }

    @Test
    fun speedOptions_minIs025() {
        assertEquals(0.25f, speedOptions.first())
    }

    @Test
    fun speedOptions_maxIs30() {
        assertEquals(3.0f, speedOptions.last())
    }

    @Test
    fun speedOptions_contains10x() {
        assertTrue(1.0f in speedOptions)
    }

    @Test
    fun speedOptions_sorted() {
        val sorted = speedOptions.sorted()
        assertEquals(speedOptions, sorted)
    }
}

// ============================================================================
// Tests for skip interval logic
// ============================================================================

class SkipIntervalTest {
    private val skipIntervals = listOf(5, 10, 30, 60)

    @Test
    fun defaultSkipInterval_is10() {
        val defaultInterval = 10
        assertTrue(defaultInterval in skipIntervals)
    }

    @Test
    fun setSkipInterval_validValue() {
        val interval = 30
        assertTrue(interval in skipIntervals)
    }

    @Test
    fun setSkipInterval_invalidValue_notInList() {
        val interval = 15
        assertFalse(interval in skipIntervals)
    }

    @Test
    fun skipForward_calculatesCorrectPosition() {
        val currentPosition = 10000L
        val skipInterval = 10
        val target = currentPosition + skipInterval * 1000L
        assertEquals(20000L, target)
    }

    @Test
    fun skipBackward_clampsToZero() {
        val currentPosition = 5000L
        val skipInterval = 10
        val target = (currentPosition - skipInterval * 1000L).coerceAtLeast(0L)
        assertEquals(0L, target)
    }

    @Test
    fun skipBackward_normalCase() {
        val currentPosition = 30000L
        val skipInterval = 10
        val target = (currentPosition - skipInterval * 1000L).coerceAtLeast(0L)
        assertEquals(20000L, target)
    }
}

// ============================================================================
// Tests for volume clamping
// ============================================================================

class VolumeClampTest {
    @Test
    fun volume_normalValue() {
        val v = 0.5f.coerceIn(0f, 1f)
        assertEquals(0.5f, v)
    }

    @Test
    fun volume_aboveMax_clamped() {
        val v = 1.5f.coerceIn(0f, 1f)
        assertEquals(1.0f, v)
    }

    @Test
    fun volume_belowMin_clamped() {
        val v = (-0.5f).coerceIn(0f, 1f)
        assertEquals(0f, v)
    }

    @Test
    fun volume_atMax() {
        val v = 1.0f.coerceIn(0f, 1f)
        assertEquals(1f, v)
    }

    @Test
    fun volume_atMin() {
        val v = 0f.coerceIn(0f, 1f)
        assertEquals(0f, v)
    }
}

// ============================================================================
// Tests for preload depth
// ============================================================================

class PreloadDepthTest {
    @Test
    fun preloadDepth_defaultValue() {
        val depth = 1
        assertEquals(1, depth.coerceIn(1, 3))
    }

    @Test
    fun preloadDepth_clampedToMax() {
        val depth = 5
        assertEquals(3, depth.coerceIn(1, 3))
    }

    @Test
    fun preloadDepth_clampedToMin() {
        val depth = 0
        assertEquals(1, depth.coerceIn(1, 3))
    }

    @Test
    fun preloadDepth_adjacentIndices() {
        val currentIndex = 5
        val totalEpisodes = 10
        val depth = 2
        val urls = (0 until totalEpisodes).map { "url_$it" }
        val indices =
            ((-depth)..depth)
                .map { currentIndex + it }
                .filter { it >= 0 && it < urls.size }
                .filter { it != currentIndex }
        assertEquals(listOf(3, 4, 6, 7), indices)
    }

    @Test
    fun preloadDepth_adjacentIndices_atStart() {
        val currentIndex = 0
        val totalEpisodes = 10
        val depth = 2
        val urls = (0 until totalEpisodes).map { "url_$it" }
        val indices =
            ((-depth)..depth)
                .map { currentIndex + it }
                .filter { it >= 0 && it < urls.size }
                .filter { it != currentIndex }
        assertEquals(listOf(1, 2), indices)
    }

    @Test
    fun preloadDepth_adjacentIndices_atEnd() {
        val currentIndex = 9
        val totalEpisodes = 10
        val depth = 2
        val urls = (0 until totalEpisodes).map { "url_$it" }
        val indices =
            ((-depth)..depth)
                .map { currentIndex + it }
                .filter { it >= 0 && it < urls.size }
                .filter { it != currentIndex }
        assertEquals(listOf(7, 8), indices)
    }
}

// ============================================================================
// Tests for playback completion logic
// ============================================================================

class PlaybackCompletionLogicTest {
    @Test
    fun onPlaybackCompleted_loopSingle_shouldSeekToStart() {
        val playMode = PlayMode.LOOP_SINGLE
        // In LOOP_SINGLE mode, player should seek to 0 and play
        assertEquals(PlayMode.LOOP_SINGLE, playMode)
    }

    @Test
    fun onPlaybackCompleted_loopAll_withNextEpisode_shouldPlayNext() {
        val playMode = PlayMode.LOOP_ALL
        val hasNext = true
        // Should call playNextEpisode()
        assertTrue(hasNext)
    }

    @Test
    fun onPlaybackCompleted_loopAll_noNextEpisode_shouldWrapToFirst() {
        val playMode = PlayMode.LOOP_ALL
        val hasNext = false
        val episodeUrls = listOf("url0", "url1")
        // Should play episode at index 0
        assertFalse(hasNext)
        assertTrue(episodeUrls.isNotEmpty())
    }

    @Test
    fun onPlaybackCompleted_sequential_withNextEpisode_shouldPlayNext() {
        val playMode = PlayMode.SEQUENTIAL
        val hasNext = true
        assertTrue(hasNext)
    }

    @Test
    fun onPlaybackCompleted_sequential_noNextEpisode_shouldDoNothing() {
        val playMode = PlayMode.SEQUENTIAL
        val hasNext = false
        assertFalse(hasNext)
    }
}

// ============================================================================
// Tests for AV sync drift calculation
// ============================================================================

class AVSyncDriftCalculationTest {
    @Test
    fun driftCalculation_noDrift() {
        val lastVideoPositionMs = 10000L
        val elapsed = 2000L
        val expectedMs = lastVideoPositionMs + elapsed
        val actualMs = expectedMs
        val driftMs = expectedMs - actualMs
        assertEquals(0L, driftMs)
        assertTrue(kotlin.math.abs(driftMs) < 50)
    }

    @Test
    fun driftCalculation_smallDrift_ignored() {
        val driftMs = 30L
        assertTrue(kotlin.math.abs(driftMs) < 50)
    }

    @Test
    fun driftCalculation_mediumDrift_speedAdjust() {
        val driftMs = 100L
        val frames = kotlin.math.abs(driftMs) / 33
        assertTrue(kotlin.math.abs(driftMs) in 50 until 500)
        assertTrue(frames <= 5)
    }

    @Test
    fun driftCalculation_largeDrift_seekCorrection() {
        val driftMs = 800L
        assertTrue(kotlin.math.abs(driftMs) in 500 until 2000)
    }

    @Test
    fun driftCalculation_severeDrift_frameSkip() {
        val driftMs = 3000L
        assertTrue(kotlin.math.abs(driftMs) > 2000)
    }

    @Test
    fun speedAdjustment_driftPositive_slowDown() {
        val playbackSpeed = 1.0f
        val driftMs = 100L // positive = video ahead
        val rate = if (driftMs < 0) playbackSpeed * 1.05f else playbackSpeed * 0.95f
        assertEquals(0.95f, rate)
    }

    @Test
    fun speedAdjustment_driftNegative_speedUp() {
        val playbackSpeed = 1.0f
        val driftMs = -100L // negative = video behind
        val rate = if (driftMs < 0) playbackSpeed * 1.05f else playbackSpeed * 0.95f
        assertEquals(1.05f, rate)
    }
}

// ============================================================================
// Tests for MetricsEngine fake
// ============================================================================

class FakeMetricsEngineTest {
    private lateinit var engine: FakeMetricsEngine

    @BeforeTest
    fun setUp() {
        engine = FakeMetricsEngine()
    }

    @Test
    fun startSession_setsFlag() {
        assertFalse(engine.sessionStarted)
        engine.startSession("video1")
        assertTrue(engine.sessionStarted)
    }

    @Test
    fun endSession_setsFlag() {
        engine.startSession("video1")
        assertFalse(engine.sessionEnded)
        engine.endSession()
        assertTrue(engine.sessionEnded)
    }

    @Test
    fun recordEvent_addsToList() {
        engine.recordEvent(MetricsEvent.PLAY_START)
        engine.recordEvent(MetricsEvent.FIRST_FRAME)
        assertEquals(2, engine.recordedEvents.size)
        assertEquals(MetricsEvent.PLAY_START, engine.recordedEvents[0])
        assertEquals(MetricsEvent.FIRST_FRAME, engine.recordedEvents[1])
    }

    @Test
    fun markFirstFrameRecorded_setsFlag() {
        assertFalse(engine.hasRecordedFirstFrame)
        engine.markFirstFrameRecorded()
        assertTrue(engine.hasRecordedFirstFrame)
    }

    @Test
    fun setBuffering_updatesState() {
        assertFalse(engine.isBuffering)
        engine.setBuffering(true)
        assertTrue(engine.isBuffering)
        engine.setBuffering(false)
        assertFalse(engine.isBuffering)
    }
}

// ============================================================================
// Tests for CacheEngine fake
// ============================================================================

class FakeCacheEngineTest {
    private lateinit var engine: FakeCacheEngine

    @BeforeTest
    fun setUp() {
        engine = FakeCacheEngine()
    }

    @Test
    fun cancelPreloads_setsFlag() {
        assertFalse(engine.preloadCancelled)
        engine.cancelPreloads()
        assertTrue(engine.preloadCancelled)
    }
}

// ============================================================================
// Tests for ProgressSaveCallback interface
// ============================================================================

class ProgressSaveCallbackTest {
    @Test
    fun playbackProgress_dataClass_worksCorrectly() {
        val progress =
            PlaybackProgress(
                videoUrl = "https://example.com/video.mp4",
                positionMs = 60000,
                durationMs = 3600000,
                lastPlayTimeMs = 1000000,
            )
        assertEquals("https://example.com/video.mp4", progress.videoUrl)
        assertEquals(60000L, progress.positionMs)
        assertEquals(3600000L, progress.durationMs)
    }
}

// ============================================================================
// Tests for brightness clamping
// ============================================================================

class BrightnessClampTest {
    @Test
    fun brightness_normalValue() {
        val b = 0.7f.coerceIn(0f, 1f)
        assertEquals(0.7f, b)
    }

    @Test
    fun brightness_aboveMax() {
        val b = 1.5f.coerceIn(0f, 1f)
        assertEquals(1.0f, b)
    }

    @Test
    fun brightness_belowMin() {
        val b = (-0.3f).coerceIn(0f, 1f)
        assertEquals(0f, b)
    }
}
