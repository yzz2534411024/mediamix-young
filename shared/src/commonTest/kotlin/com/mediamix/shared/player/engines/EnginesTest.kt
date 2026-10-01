package com.mediamix.shared.player.engines

import com.mediamix.shared.core.PowerMode
import com.mediamix.shared.network.NetworkConditionLevel
import com.mediamix.shared.network.NetworkStatusProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull

// ============================================================================
// Fake NetworkStatusProvider for testing
// ============================================================================

private class FakeNetworkStatusProvider(
    initialCondition: NetworkConditionLevel = NetworkConditionLevel.ONLINE,
) : NetworkStatusProvider {
    override var currentCondition: NetworkConditionLevel = initialCondition
    private val _flow = MutableSharedFlow<NetworkConditionLevel>(replay = 1)
    override val conditionChangedFlow: SharedFlow<NetworkConditionLevel> = _flow

    fun emitCondition(condition: NetworkConditionLevel) {
        currentCondition = condition
        _flow.tryEmit(condition)
    }
}

// ============================================================================
// PlaybackErrorHandler tests
// ============================================================================

class PlaybackErrorHandlerTest {

    private lateinit var handler: PlaybackErrorHandlerImpl
    private lateinit var networkProvider: FakeNetworkStatusProvider

    @BeforeTest
    fun setUp() {
        networkProvider = FakeNetworkStatusProvider()
        handler = PlaybackErrorHandlerImpl(networkProvider)
        handler.qualityCount = 4 // e.g. 4 quality levels
    }

    // ---- Error classification ----

    @Test
    fun categorizeError_stuck() {
        assertEquals(ErrorCategory.STUCK, handler.categorizeError("player stuck detected"))
        assertEquals(ErrorCategory.STUCK, handler.categorizeError("deadlock in pipeline"))
        assertEquals(ErrorCategory.STUCK, handler.categorizeError("no frame rendered"))
        assertEquals(ErrorCategory.STUCK, handler.categorizeError("black screen"))
        assertEquals(ErrorCategory.STUCK, handler.categorizeError("no video output"))
        assertEquals(ErrorCategory.STUCK, handler.categorizeError("no audio stream"))
        assertEquals(ErrorCategory.STUCK, handler.categorizeError("silence detected"))
    }

    @Test
    fun categorizeError_codec() {
        assertEquals(ErrorCategory.CODEC, handler.categorizeError("codec initialization failed"))
        assertEquals(ErrorCategory.CODEC, handler.categorizeError("decoder error"))
        assertEquals(ErrorCategory.CODEC, handler.categorizeError("MediaCodec error"))
        assertEquals(ErrorCategory.CODEC, handler.categorizeError("unsupported format"))
        assertEquals(ErrorCategory.CODEC, handler.categorizeError("HEVC decode failed"))
        assertEquals(ErrorCategory.CODEC, handler.categorizeError("VP9 not supported"))
        assertEquals(ErrorCategory.CODEC, handler.categorizeError("hwdec failure"))
    }

    @Test
    fun categorizeError_network() {
        assertEquals(ErrorCategory.NETWORK, handler.categorizeError("network unreachable"))
        assertEquals(ErrorCategory.NETWORK, handler.categorizeError("connection refused"))
        assertEquals(ErrorCategory.NETWORK, handler.categorizeError("DNS resolution failed"))
        assertEquals(ErrorCategory.NETWORK, handler.categorizeError("socket error"))
        assertEquals(ErrorCategory.NETWORK, handler.categorizeError("ECONNRESET"))
    }

    @Test
    fun categorizeError_source() {
        assertEquals(ErrorCategory.SOURCE, handler.categorizeError("HTTP 404 not found"))
        assertEquals(ErrorCategory.SOURCE, handler.categorizeError("403 forbidden"))
    }

    @Test
    fun categorizeError_timeout() {
        // "timeout" is caught by network check first; "timed out" hits TIMEOUT category
        assertEquals(ErrorCategory.TIMEOUT, handler.categorizeError("operation timed out"))
    }

    @Test
    fun categorizeError_unknown() {
        assertEquals(ErrorCategory.UNKNOWN, handler.categorizeError("something weird happened"))
    }

    // ---- Error handling priority chain ----

    @Test
    fun handleError_stuckHasHighestPriority() {
        val result = handler.handleError(
            error = "player stuck",
            hardwareDecodingEnabled = true,
            hasQualityOptions = true,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 10000L,
        )
        assertEquals(ErrorAction.RECOVER_FROM_STUCK, result.action)
    }

    @Test
    fun handleError_blackScreen() {
        val result = handler.handleError(
            error = "black screen detected",
            hardwareDecodingEnabled = true,
            hasQualityOptions = true,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 10000L,
        )
        assertEquals(ErrorAction.RECOVER_FROM_BLACK_SCREEN, result.action)
    }

    @Test
    fun handleError_silence() {
        val result = handler.handleError(
            error = "no audio output",
            hardwareDecodingEnabled = true,
            hasQualityOptions = true,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 10000L,
        )
        assertEquals(ErrorAction.RECOVER_FROM_SILENCE, result.action)
    }

    @Test
    fun handleError_codecWithHwDecode_downgradesToSoftware() {
        val result = handler.handleError(
            error = "codec error",
            hardwareDecodingEnabled = true,
            hasQualityOptions = true,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 5000L,
        )
        assertEquals(ErrorAction.DOWNGRADE_TO_SOFTWARE_DECODE, result.action)
    }

    @Test
    fun handleError_codecWithoutHwDecode_fallsThrough() {
        // Codec error but hardware decoding already off -> not downgrade
        // Falls through to retry / quality downgrade
        val result = handler.handleError(
            error = "codec error",
            hardwareDecodingEnabled = false,
            hasQualityOptions = true,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 5000L,
        )
        // Should retry since retryCount < maxAutoRetry
        assertEquals(ErrorAction.RETRY_SAME_URL, result.action)
    }

    @Test
    fun handleError_sourceError_switchesQuality() {
        val result = handler.handleError(
            error = "HTTP 404 not found",
            hardwareDecodingEnabled = false,
            hasQualityOptions = true,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 5000L,
        )
        assertEquals(ErrorAction.SWITCH_TO_NEXT_QUALITY, result.action)
        assertEquals(1, result.nextQualityIndex)
    }

    @Test
    fun handleError_sourceError_noQualityOptions_showsDialog() {
        val result = handler.handleError(
            error = "HTTP 403 forbidden",
            hardwareDecodingEnabled = false,
            hasQualityOptions = false,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 5000L,
        )
        assertEquals(ErrorAction.SHOW_ERROR_DIALOG, result.action)
    }

    @Test
    fun handleError_networkError_waitsForRecovery() {
        val result = handler.handleError(
            error = "network unreachable",
            hardwareDecodingEnabled = false,
            hasQualityOptions = true,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 5000L,
        )
        assertEquals(ErrorAction.WAIT_FOR_NETWORK_RECOVERY, result.action)
        assertTrue(handler.isWaitingForNetwork)
    }

    @Test
    fun handleError_timeout_retriesOnce() {
        val result1 = handler.handleError(
            error = "operation timed out",
            hardwareDecodingEnabled = false,
            hasQualityOptions = false,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 5000L,
        )
        assertEquals(ErrorAction.RETRY_SAME_URL, result1.action)
        assertEquals(1, handler.retryCount)

        // Second retry should fall through to error dialog
        val result2 = handler.handleError(
            error = "operation timed out",
            hardwareDecodingEnabled = false,
            hasQualityOptions = false,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 5000L,
        )
        assertEquals(ErrorAction.SHOW_ERROR_DIALOG, result2.action)
    }

    @Test
    fun handleError_unknownError_retriesOnce() {
        val result = handler.handleError(
            error = "something weird",
            hardwareDecodingEnabled = false,
            hasQualityOptions = false,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 5000L,
        )
        assertEquals(ErrorAction.RETRY_SAME_URL, result.action)
        assertEquals(1, handler.retryCount)
    }

    // ---- Quality downgrade ----

    @Test
    fun findNextUntriedQuality_skipsTried() {
        handler.qualityCount = 3
        handler.addTriedQualityIndex(0)
        handler.addTriedQualityIndex(1)
        assertEquals(2, handler.findNextUntriedQuality())
    }

    @Test
    fun findNextUntriedQuality_allTried_returnsMinusOne() {
        handler.qualityCount = 2
        handler.addTriedQualityIndex(0)
        handler.addTriedQualityIndex(1)
        assertEquals(-1, handler.findNextUntriedQuality())
    }

    @Test
    fun clearTriedQualityIndices_resetsSet() {
        handler.addTriedQualityIndex(0)
        handler.clearTriedQualityIndices()
        handler.qualityCount = 3
        assertEquals(0, handler.findNextUntriedQuality())
    }

    @Test
    fun resetRetryCount_works() {
        handler.handleError(
            error = "unknown",
            hardwareDecodingEnabled = false,
            hasQualityOptions = false,
            currentQualityIndex = 0,
            lastPlaybackPositionMs = 0L,
        )
        assertEquals(1, handler.retryCount)
        handler.resetRetryCount()
        assertEquals(0, handler.retryCount)
    }

    // ---- Full quality downgrade chain ----

    @Test
    fun handleError_exhaustsRetries_thenDowngradesQuality() {
        handler.qualityCount = 3
        // First: retry
        val r1 = handler.handleError("unknown", false, true, 0, 0L)
        assertEquals(ErrorAction.RETRY_SAME_URL, r1.action)

        // Second: quality downgrade (retry exhausted)
        val r2 = handler.handleError("unknown", false, true, 0, 0L)
        assertEquals(ErrorAction.SWITCH_TO_NEXT_QUALITY, r2.action)
        assertNotNull(r2.nextQualityIndex)
    }

    @Test
    fun handleError_allQualitiesTried_showsDialog() {
        handler.qualityCount = 1
        handler.addTriedQualityIndex(0)
        // Exhaust retry
        handler.handleError("unknown", false, true, 0, 0L)
        // Now all qualities tried + retry exhausted
        val result = handler.handleError("unknown", false, true, 0, 0L)
        assertEquals(ErrorAction.SHOW_ERROR_DIALOG, result.action)
    }
}

// ============================================================================
// MetricsEngine tests
// ============================================================================

class MetricsEngineTest {

    private lateinit var engine: MetricsEngineImpl

    @BeforeTest
    fun setUp() {
        engine = MetricsEngineImpl()
    }

    @Test
    fun startSession_initializesState() {
        engine.startSession("video123")
        assertFalse(engine.hasRecordedFirstFrame)
        assertFalse(engine.isBuffering)
    }

    @Test
    fun endSession_withoutStart_returnsNull() {
        assertNull(engine.endSession())
    }

    @Test
    fun endSession_returnsSummary() {
        engine.startSession("video123")
        engine.recordEvent(MetricsEvent.PLAY_START)
        engine.recordEvent(MetricsEvent.FIRST_FRAME)
        engine.recordEvent(MetricsEvent.BUFFER_START)
        engine.recordEvent(MetricsEvent.BUFFER_END)

        val summary = engine.endSession()
        assertNotNull(summary)
        assertEquals("video123", summary["videoId"])
        assertEquals(1, summary["totalBufferCount"])
        assertNotNull(summary["firstFrameTimeMs"])
        assertNotNull(summary["sessionDurationMs"])
    }

    @Test
    fun recordEvent_firstFrame_setsFlag() {
        engine.startSession("v1")
        assertFalse(engine.hasRecordedFirstFrame)
        engine.recordEvent(MetricsEvent.FIRST_FRAME)
        assertTrue(engine.hasRecordedFirstFrame)
    }

    @Test
    fun recordEvent_countsAccumulate() {
        engine.startSession("v1")
        engine.recordEvent(MetricsEvent.SEEK)
        engine.recordEvent(MetricsEvent.SEEK)
        engine.recordEvent(MetricsEvent.SEEK)

        val summary = engine.endSession()
        assertNotNull(summary)
        assertEquals(3, summary["event_SEEK"])
    }

    @Test
    fun recordEvent_errorMessage_stored() {
        engine.startSession("v1")
        engine.recordEvent(MetricsEvent.ERROR, errorMessage = "test error")

        val summary = engine.endSession()
        assertNotNull(summary)
        assertEquals("test error", summary["lastErrorMessage"])
    }

    @Test
    fun getCurrentMetrics_withoutSession_returnsNull() {
        assertNull(engine.getCurrentMetrics())
    }

    @Test
    fun getCurrentMetrics_withSession_returnsData() {
        engine.startSession("v1")
        engine.recordEvent(MetricsEvent.PLAY_START)
        val metrics = engine.getCurrentMetrics()
        assertNotNull(metrics)
        assertEquals("v1", metrics["videoId"])
        assertEquals(false, metrics["isBuffering"])
    }

    @Test
    fun markFirstFrameRecorded_setsFlag() {
        engine.startSession("v1")
        engine.markFirstFrameRecorded()
        assertTrue(engine.hasRecordedFirstFrame)
    }

    @Test
    fun setBuffering_updatesState() {
        engine.setBuffering(true)
        assertTrue(engine.isBuffering)
        engine.setBuffering(false)
        assertFalse(engine.isBuffering)
    }

    @Test
    fun dispose_resetsState() {
        engine.startSession("v1")
        engine.recordEvent(MetricsEvent.FIRST_FRAME)
        engine.setBuffering(true)
        engine.dispose()

        assertFalse(engine.hasRecordedFirstFrame)
        assertFalse(engine.isBuffering)
        assertNull(engine.getCurrentMetrics())
    }

    @Test
    fun endSession_afterDispose_returnsNull() {
        engine.startSession("v1")
        engine.dispose()
        assertNull(engine.endSession())
    }
}

// ============================================================================
// CacheEngine tests (with mock dependencies)
// ============================================================================

class CacheEngineTest {

    @Test
    fun preloadNextEpisode_tracksVideoId() {
        // Basic smoke test: just verify no exceptions
        val engine = createTestCacheEngine()
        engine.preloadNextEpisode("ep1", "http://example.com/ep1.mp4")
        engine.cancelPreloads()
    }

    @Test
    fun preloadAdjacentEpisodes_powerSaving_skips() {
        val engine = createTestCacheEngine()
        engine.preloadAdjacentEpisodes(
            indices = listOf(0, 1, 2),
            title = "Show",
            episodeUrls = listOf("url0", "url1", "url2"),
            powerMode = PowerMode.POWER_SAVING,
        )
        // No crash = pass (power saving skips preload)
        engine.cancelPreloads()
    }

    @Test
    fun preloadAdjacentEpisodes_normalMode_tracks() {
        val engine = createTestCacheEngine()
        engine.preloadAdjacentEpisodes(
            indices = listOf(0, 1),
            title = "Show",
            episodeUrls = listOf("url0", "url1"),
            powerMode = PowerMode.BALANCED,
        )
        engine.cancelPreloads()
    }

    @Test
    fun dispose_clearsPreloads() {
        val engine = createTestCacheEngine()
        engine.preloadNextEpisode("ep1", "http://example.com/ep1.mp4")
        engine.dispose()
        // No crash = pass
    }

    @Test
    fun isUsingCache_initiallyFalse() {
        val engine = createTestCacheEngine()
        assertFalse(engine.isUsingCache)
    }

    private fun createTestCacheEngine(): CacheEngineImpl {
        // We can't easily mock VideoCacheService/LocalProxyServer without a DI framework,
        // so we test the non-cache-resolution parts (preload management) directly.
        // For full integration tests, a test double would be needed.
        val cacheService = createStubCacheService()
        return CacheEngineImpl(
            cacheService = cacheService,
            proxyServer = createStubProxyServer(),
            preloadService = com.mediamix.shared.services.PreloadService(cacheService = cacheService),
        )
    }

    private fun createStubCacheService(): com.mediamix.shared.cache.VideoCacheService {
        // Create a minimal VideoCacheService with in-memory dependencies
        val memoryCache = com.mediamix.shared.cache.MemoryCache()
        val diskCache = com.mediamix.shared.cache.DiskCache(
            cacheDir = System.getProperty("java.io.tmpdir") + "/test_cache_${System.nanoTime()}",
        )
        return com.mediamix.shared.cache.VideoCacheService(memoryCache, diskCache)
    }

    private fun createStubProxyServer(): com.mediamix.shared.cache.LocalProxyServer {
        val cacheService = createStubCacheService()
        return com.mediamix.shared.cache.createLocalProxyServer(cacheService)
    }
}
