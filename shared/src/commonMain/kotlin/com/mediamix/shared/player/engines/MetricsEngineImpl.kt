package com.mediamix.shared.player.engines

import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

/**
 * Metrics engine implementation — self-contained metrics collection.
 *
 * Since PlayerMetricsService and MetricsCollectorService have not been migrated yet,
 * metrics logic is embedded directly in this class:
 * - Internal Map tracks event counts and timestamps
 * - startSession: records videoId + startTime
 * - recordEvent: accumulates event counts
 * - endSession: computes summary metrics (total buffer count, buffer duration, first frame time, etc.)
 * - getCurrentMetrics: returns real-time metrics for the current session
 *
 * Migrated from metrics_engine_impl.dart.
 */
class MetricsEngineImpl : MetricsEngine {

    private val logger = Logger.withTag("MetricsEngine")

    // ========== First frame ==========
    private var _hasRecordedFirstFrame = false
    override val hasRecordedFirstFrame: Boolean get() = _hasRecordedFirstFrame

    // ========== Buffering state ==========
    private var _isBuffering = false
    override val isBuffering: Boolean get() = _isBuffering

    // ========== Session state ==========
    private var hasActiveSession = false
    private var sessionVideoId: String? = null
    private var sessionStartTimeMs: Long = 0L

    // ========== Internal metrics storage ==========
    private val eventCounts = mutableMapOf<MetricsEvent, Int>()
    private var firstFrameTimeMs: Long? = null
    private var bufferStartMs: Long? = null
    private var totalBufferDurationMs: Long = 0L
    private var lastErrorMessage: String? = null
    private var lastAvSyncOffsetMs: Int? = null

    // ========================================================================
    // Session management
    // ========================================================================

    override fun startSession(videoId: String) {
        _hasRecordedFirstFrame = false
        hasActiveSession = true
        sessionVideoId = videoId
        sessionStartTimeMs = Clock.System.now().toEpochMilliseconds()

        // Reset internal state
        eventCounts.clear()
        firstFrameTimeMs = null
        bufferStartMs = null
        totalBufferDurationMs = 0L
        lastErrorMessage = null
        lastAvSyncOffsetMs = null

        logger.i("Metrics session started: $videoId")
    }

    override fun endSession(): Map<String, Any?>? {
        if (!hasActiveSession) return null
        hasActiveSession = false

        val now = Clock.System.now().toEpochMilliseconds()
        val sessionDurationMs = now - sessionStartTimeMs

        // Finalize buffer tracking if still buffering
        if (_isBuffering && bufferStartMs != null) {
            totalBufferDurationMs += now - bufferStartMs!!
            bufferStartMs = null
            _isBuffering = false
        }

        val summary = mutableMapOf<String, Any?>(
            "videoId" to sessionVideoId,
            "sessionDurationMs" to sessionDurationMs,
            "totalBufferCount" to (eventCounts[MetricsEvent.BUFFER_START] ?: 0),
            "totalBufferDurationMs" to totalBufferDurationMs,
            "firstFrameTimeMs" to firstFrameTimeMs,
            "firstFrameLatencyMs" to firstFrameTimeMs?.let { it - sessionStartTimeMs },
            "lastErrorMessage" to lastErrorMessage,
            "lastAvSyncOffsetMs" to lastAvSyncOffsetMs,
        )

        // Add per-event counts
        for ((event, count) in eventCounts) {
            summary["event_${event.name}"] = count
        }

        logger.i("Metrics session ended: ${sessionVideoId}, duration=${sessionDurationMs}ms")
        return summary
    }

    // ========================================================================
    // Event recording
    // ========================================================================

    override fun recordEvent(event: MetricsEvent, errorMessage: String?, avSyncOffsetMs: Int?) {
        eventCounts[event] = (eventCounts[event] ?: 0) + 1

        if (errorMessage != null) {
            lastErrorMessage = errorMessage
        }
        if (avSyncOffsetMs != null) {
            lastAvSyncOffsetMs = avSyncOffsetMs
        }

        // Track first frame timing
        if (event == MetricsEvent.FIRST_FRAME && firstFrameTimeMs == null) {
            firstFrameTimeMs = Clock.System.now().toEpochMilliseconds()
            _hasRecordedFirstFrame = true
        }

        // Track buffer duration
        if (event == MetricsEvent.BUFFER_START) {
            bufferStartMs = Clock.System.now().toEpochMilliseconds()
        }
        if (event == MetricsEvent.BUFFER_END && bufferStartMs != null) {
            totalBufferDurationMs += Clock.System.now().toEpochMilliseconds() - bufferStartMs!!
            bufferStartMs = null
        }
    }

    // ========================================================================
    // Real-time metrics
    // ========================================================================

    override fun getCurrentMetrics(): Map<String, Any?>? {
        if (!hasActiveSession) return null

        val now = Clock.System.now().toEpochMilliseconds()
        val elapsedMs = now - sessionStartTimeMs

        val currentBufferDurationMs = if (_isBuffering && bufferStartMs != null) {
            totalBufferDurationMs + (now - bufferStartMs!!)
        } else {
            totalBufferDurationMs
        }

        return mapOf(
            "videoId" to sessionVideoId,
            "elapsedMs" to elapsedMs,
            "totalBufferCount" to (eventCounts[MetricsEvent.BUFFER_START] ?: 0),
            "totalBufferDurationMs" to currentBufferDurationMs,
            "firstFrameTimeMs" to firstFrameTimeMs,
            "firstFrameLatencyMs" to firstFrameTimeMs?.let { it - sessionStartTimeMs },
            "isBuffering" to _isBuffering,
            "lastErrorMessage" to lastErrorMessage,
            "lastAvSyncOffsetMs" to lastAvSyncOffsetMs,
        )
    }

    // ========================================================================
    // First frame & buffer state management
    // ========================================================================

    override fun markFirstFrameRecorded() {
        _hasRecordedFirstFrame = true
    }

    override fun setBuffering(value: Boolean) {
        _isBuffering = value
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    override fun dispose() {
        _hasRecordedFirstFrame = false
        _isBuffering = false
        hasActiveSession = false
        eventCounts.clear()
        firstFrameTimeMs = null
        bufferStartMs = null
        totalBufferDurationMs = 0L
    }
}