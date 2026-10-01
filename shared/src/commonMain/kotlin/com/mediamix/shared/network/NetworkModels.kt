package com.mediamix.shared.network

import kotlinx.coroutines.flow.SharedFlow

/**
 * Throughput prediction result — produced by BandwidthEstimator, consumed by ABRController.
 */
data class ThroughputPrediction(
    val predictedKbps: Double,
    val confidence: Double,      // 0.0 - 1.0
    val trendKbps: Double,       // short-term trend
    val longTermAverageKbps: Double,
    val stability: Double        // 0.0 - 1.0
) {
    companion object {
        val EMPTY = ThroughputPrediction(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * Network condition levels (aligned with PreloadService.NetworkCondition).
 */
enum class NetworkConditionLevel {
    ONLINE,     // good connection
    WEAK,       // poor / weak signal
    OFFLINE     // no connectivity
}

/**
 * Abstraction for network status — to be implemented by NetworkEngine when fully migrated.
 * Used by PlaybackErrorHandler for network recovery monitoring.
 */
interface NetworkStatusProvider {
    val currentCondition: NetworkConditionLevel
    val conditionChangedFlow: SharedFlow<NetworkConditionLevel>
}
