package com.mediamix.shared.player.engines

import com.mediamix.shared.core.PowerMode

// ============================================================================
// Data classes
// ============================================================================

/**
 * Cache resolve result.
 */
data class CacheResolveResult(
    val url: String,
    val isUsingCache: Boolean,
    val fallbackQuality: String? = null
)

/**
 * Error action enum.
 */
enum class ErrorAction {
    DOWNGRADE_TO_SOFTWARE_DECODE,
    WAIT_FOR_NETWORK_RECOVERY,
    RETRY_SAME_URL,
    SWITCH_TO_NEXT_QUALITY,
    SHOW_ERROR_DIALOG,
    RECOVER_FROM_STUCK,
    RECOVER_FROM_BLACK_SCREEN,
    RECOVER_FROM_SILENCE,
    SWITCH_SOURCE
}

/**
 * Error handle result.
 */
data class ErrorHandleResult(
    val action: ErrorAction,
    val nextQualityIndex: Int? = null
)

/**
 * Metrics event enum.
 */
enum class MetricsEvent {
    PLAY_START, PLAY_PAUSE, PLAY_RESUME, PLAY_COMPLETE,
    SEEK, BUFFER_START, BUFFER_END,
    CACHE_HIT, CACHE_MISS,
    QUALITY_CHANGE, ERROR, FIRST_FRAME
}

// ============================================================================
// Engine interfaces
// ============================================================================

/**
 * Cache engine interface — resolves video URLs via local cache / proxy.
 */
interface CacheEngine {
    suspend fun resolveVideoUrl(url: String, videoId: String): String
    suspend fun resolveVideoUrlWithFallback(
        url: String,
        videoId: String,
        preferredQuality: String? = null
    ): CacheResolveResult
    val isUsingCache: Boolean
    fun notifyPreloadBuffering(isBuffering: Boolean)
    fun preloadNextEpisode(videoId: String, url: String)
    fun preloadAdjacentEpisodes(
        indices: List<Int>,
        title: String,
        episodeUrls: List<String>,
        powerMode: PowerMode
    )
    fun cancelPreloads()
    fun dispose()
}

/**
 * Playback error handler interface.
 */
interface PlaybackErrorHandler {
    fun handleError(
        error: String,
        hardwareDecodingEnabled: Boolean,
        hasQualityOptions: Boolean,
        currentQualityIndex: Int,
        lastPlaybackPositionMs: Long
    ): ErrorHandleResult
    val isWaitingForNetwork: Boolean
    fun findNextUntriedQuality(): Int
    fun resetRetryCount()
    fun clearTriedQualityIndices()
    fun addTriedQualityIndex(index: Int)
    fun startNetworkRecoveryMonitoring(onNetworkRecovered: () -> Unit)
    fun stopNetworkRecoveryMonitoring()
    val retryCount: Int
    fun dispose()
}

/**
 * Metrics engine interface.
 */
interface MetricsEngine {
    /**
     * 是否收集指标 —— 对应设置页的「使用数据分享」开关。
     *
     * 关闭后 [startSession] / [recordEvent] 直接返回，不再累积数据。
     */
    fun setEnabled(enabled: Boolean)

    fun startSession(videoId: String)
    fun endSession(): Map<String, Any?>?
    fun recordEvent(event: MetricsEvent, errorMessage: String? = null, avSyncOffsetMs: Int? = null)
    fun getCurrentMetrics(): Map<String, Any?>?
    val hasRecordedFirstFrame: Boolean
    fun markFirstFrameRecorded()
    val isBuffering: Boolean
    fun setBuffering(value: Boolean)
    fun dispose()
}
