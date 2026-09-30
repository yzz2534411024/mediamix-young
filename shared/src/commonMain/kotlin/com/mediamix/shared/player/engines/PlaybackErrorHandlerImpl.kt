package com.mediamix.shared.player.engines

import co.touchlab.kermit.Logger
import com.mediamix.shared.network.NetworkConditionLevel
import com.mediamix.shared.network.NetworkStatusProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow

// ============================================================================
// Error category (internal)
// ============================================================================

internal enum class ErrorCategory {
    NETWORK, CODEC, SOURCE, TIMEOUT, STUCK, UNKNOWN
}

// ============================================================================
// PlaybackErrorHandler implementation
// ============================================================================

/**
 * Playback error handler implementation.
 *
 * Responsible for error classification, retry, quality downgrade,
 * and weak-network auto-reconnect strategies.
 *
 * Migrated from playback_error_handler_impl.dart.
 */
class PlaybackErrorHandlerImpl(
    private val networkStatusProvider: NetworkStatusProvider? = null,
) : PlaybackErrorHandler {

    private val logger = Logger.withTag("PlaybackErrorHandler")

    // ========== Retry logic ==========
    private var _retryCount = 0
    override val retryCount: Int get() = _retryCount
    private val maxAutoRetry = 1

    // ========== Quality downgrade ==========
    private val triedQualityIndices = mutableSetOf<Int>()
    var qualityCount = 0

    // ========== Network recovery ==========
    private var _isWaitingForNetwork = false
    override val isWaitingForNetwork: Boolean get() = _isWaitingForNetwork

    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 10
    private var networkRecoveryJob: Job? = null
    private var onNetworkRecovered: (() -> Unit)? = null

    // ========== Last playback position ==========
    private var lastPlaybackPositionMs: Long = 0L

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ========================================================================
    // Error classification
    // ========================================================================

    internal fun categorizeError(error: String): ErrorCategory {
        val lower = error.lowercase()
        if (lower.contains("stuck") || lower.contains("deadlock") ||
            lower.contains("no frame") || lower.contains("black screen") ||
            lower.contains("no video") || lower.contains("no audio") ||
            lower.contains("silence")) {
            return ErrorCategory.STUCK
        }
        if (isCodecError(error)) return ErrorCategory.CODEC
        if (isErrorNetworkRelated(error)) return ErrorCategory.NETWORK
        if (lower.contains("404") || lower.contains("403") ||
            lower.contains("not found") || lower.contains("forbidden")) {
            return ErrorCategory.SOURCE
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return ErrorCategory.TIMEOUT
        }
        return ErrorCategory.UNKNOWN
    }

    private fun isCodecError(error: String): Boolean {
        val lower = error.lowercase()
        return lower.contains("codec") ||
            lower.contains("decoder") ||
            lower.contains("mediacodec") ||
            lower.contains("unsupported") ||
            lower.contains("format") ||
            lower.contains("avc") ||
            lower.contains("hevc") ||
            lower.contains("vp9") ||
            lower.contains("av1") ||
            lower.contains("hwdec") ||
            lower.contains("hardware") ||
            lower.contains("software")
    }

    private fun isErrorNetworkRelated(error: String): Boolean {
        val lower = error.lowercase()
        return lower.contains("network") ||
            lower.contains("connection") ||
            lower.contains("timeout") ||
            lower.contains("dns") ||
            lower.contains("unreachable") ||
            lower.contains("refused") ||
            lower.contains("socket") ||
            lower.contains("host") ||
            lower.contains("econnreset") ||
            lower.contains("econnrefused") ||
            lower.contains("etimedout") ||
            lower.contains("enotfound")
    }

    // ========================================================================
    // Core error handling
    // ========================================================================

    override fun handleError(
        error: String,
        hardwareDecodingEnabled: Boolean,
        hasQualityOptions: Boolean,
        currentQualityIndex: Int,
        lastPlaybackPositionMs: Long,
    ): ErrorHandleResult {
        this.lastPlaybackPositionMs = lastPlaybackPositionMs
        val category = categorizeError(error)
        val lower = error.lowercase()

        // 0. State machine anomaly detection & recovery
        if (lower.contains("stuck") || lower.contains("deadlock") || lower.contains("no frame")) {
            logger.w("Player stuck detected, attempting recovery")
            return ErrorHandleResult(action = ErrorAction.RECOVER_FROM_STUCK)
        }
        if (lower.contains("black screen") || lower.contains("no video") || lower.contains("rendering_failed")) {
            logger.w("Black screen detected, attempting recovery")
            return ErrorHandleResult(action = ErrorAction.RECOVER_FROM_BLACK_SCREEN)
        }
        if (lower.contains("no audio") || lower.contains("silence") || lower.contains("audio output failed")) {
            logger.w("Silence detected, attempting recovery")
            return ErrorHandleResult(action = ErrorAction.RECOVER_FROM_SILENCE)
        }

        // 1. Codec error + hardware decoding -> downgrade to software
        if (category == ErrorCategory.CODEC && hardwareDecodingEnabled) {
            return ErrorHandleResult(action = ErrorAction.DOWNGRADE_TO_SOFTWARE_DECODE)
        }

        // 2. Source error -> switch source directly (no retry on same URL)
        if (category == ErrorCategory.SOURCE) {
            logger.w("Source error, skipping retry and switching source")
            if (hasQualityOptions) {
                triedQualityIndices.add(currentQualityIndex)
                val nextIndex = findNextUntriedQuality()
                if (nextIndex >= 0) {
                    return ErrorHandleResult(
                        action = ErrorAction.SWITCH_TO_NEXT_QUALITY,
                        nextQualityIndex = nextIndex
                    )
                }
            }
            return ErrorHandleResult(action = ErrorAction.SHOW_ERROR_DIALOG)
        }

        // 3. Network error -> wait for network recovery
        if (category == ErrorCategory.NETWORK) {
            _isWaitingForNetwork = true
            return ErrorHandleResult(action = ErrorAction.WAIT_FOR_NETWORK_RECOVERY)
        }

        // 4. Timeout -> quick retry once
        if (category == ErrorCategory.TIMEOUT && _retryCount < maxAutoRetry) {
            _retryCount++
            return ErrorHandleResult(action = ErrorAction.RETRY_SAME_URL)
        }

        // 5. Unknown error -> retry once
        if (_retryCount < maxAutoRetry) {
            _retryCount++
            return ErrorHandleResult(action = ErrorAction.RETRY_SAME_URL)
        }

        // 6. Downgrade strategy
        if (hasQualityOptions && !_isWaitingForNetwork) {
            triedQualityIndices.add(currentQualityIndex)
            val nextIndex = findNextUntriedQuality()
            if (nextIndex >= 0) {
                _retryCount = 0
                return ErrorHandleResult(
                    action = ErrorAction.SWITCH_TO_NEXT_QUALITY,
                    nextQualityIndex = nextIndex
                )
            }
        }

        return ErrorHandleResult(action = ErrorAction.SHOW_ERROR_DIALOG)
    }

    // ========================================================================
    // Quality downgrade
    // ========================================================================

    override fun findNextUntriedQuality(): Int {
        for (i in 0 until qualityCount) {
            if (!triedQualityIndices.contains(i)) return i
        }
        return -1
    }

    override fun clearTriedQualityIndices() {
        triedQualityIndices.clear()
    }

    override fun addTriedQualityIndex(index: Int) {
        triedQualityIndices.add(index)
    }

    override fun resetRetryCount() {
        _retryCount = 0
    }

    // ========================================================================
    // Network recovery monitoring
    // ========================================================================

    override fun startNetworkRecoveryMonitoring(onNetworkRecovered: () -> Unit) {
        this.onNetworkRecovered = onNetworkRecovered
        networkRecoveryJob?.cancel()
        reconnectJob?.cancel()
        reconnectAttempt = 0

        // Listen for network condition recovery via SharedFlow
        val flow: SharedFlow<NetworkConditionLevel>? = networkStatusProvider?.conditionChangedFlow
        if (flow != null) {
            networkRecoveryJob = scope.launch {
                flow.collect { condition ->
                    if (_isWaitingForNetwork &&
                        condition != NetworkConditionLevel.OFFLINE &&
                        condition != NetworkConditionLevel.WEAK
                    ) {
                        logger.i("Network recovered (${condition.name}), triggering reconnect callback")
                        handleNetworkRecovered()
                    }
                }
            }
        }

        // Also start exponential backoff probe (in case flow events are missed)
        scheduleReconnectProbe()
    }

    override fun stopNetworkRecoveryMonitoring() {
        networkRecoveryJob?.cancel()
        networkRecoveryJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        _isWaitingForNetwork = false
        reconnectAttempt = 0
        onNetworkRecovered = null
    }

    private fun handleNetworkRecovered() {
        networkRecoveryJob?.cancel()
        reconnectJob?.cancel()
        _isWaitingForNetwork = false
        reconnectAttempt = 0
        _retryCount = 0
        onNetworkRecovered?.invoke()
    }

    /**
     * Exponential backoff probe: 1s, 2s, 4s, 8s, 16s, 32s, 32s, 32s...
     * Max probe count: [maxReconnectAttempts] (10)
     */
    private fun scheduleReconnectProbe() {
        if (!_isWaitingForNetwork) return

        if (reconnectAttempt >= maxReconnectAttempts) {
            logger.w("Max reconnect attempts reached ($maxReconnectAttempts), stopping auto-reconnect")
            _isWaitingForNetwork = false
            return
        }

        // Exponential backoff: 1<<0=1s, 1<<1=2s, ..., 1<<5=32s (capped)
        val delaySeconds = 1L shl reconnectAttempt.coerceIn(0, 5)

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delaySeconds * 1000L)
            if (!_isWaitingForNetwork) return@launch

            val condition = networkStatusProvider?.currentCondition
            if (condition != null &&
                condition != NetworkConditionLevel.OFFLINE &&
                condition != NetworkConditionLevel.WEAK
            ) {
                logger.i("Probe detected network recovery, triggering reconnect callback")
                handleNetworkRecovered()
            } else {
                logger.d("Network not recovered, probing again in ${delaySeconds}s")
                reconnectAttempt++
                scheduleReconnectProbe()
            }
        }
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    override fun dispose() {
        networkRecoveryJob?.cancel()
        networkRecoveryJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        onNetworkRecovered = null
        triedQualityIndices.clear()
        scope.cancel()
    }
}