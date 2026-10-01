package com.mediamix.shared.player

import com.mediamix.shared.services.NetworkCondition

/**
 * 缓冲区水位线配置 — 根据不同网络条件设定缓冲阈值。
 * 所有时间单位使用毫秒，避免 Duration 平台差异。
 */
data class BufferWaterLines(
    val lowMs: Long,
    val highMs: Long,
    val maxMs: Long
) {
    companion object {
        val WIFI = BufferWaterLines(3_000, 20_000, 60_000)
        val MOBILE_4G = BufferWaterLines(5_000, 15_000, 30_000)
        val WEAK = BufferWaterLines(8_000, 10_000, 15_000)

        fun forCondition(condition: NetworkCondition): BufferWaterLines = when (condition) {
            NetworkCondition.WIFI -> WIFI
            NetworkCondition.LTE -> MOBILE_4G
            NetworkCondition.THREE_G, NetworkCondition.POOR, NetworkCondition.OFFLINE -> WEAK
        }
    }
}

/**
 * 缓冲区管理器 — 跟踪当前缓冲水位并根据网络条件动态调整水位线。
 */
class BufferManager(
    private val onBufferStateChanged: ((Boolean) -> Unit)? = null
) {
    private var waterLines: BufferWaterLines = BufferWaterLines.WIFI
    private var currentBufferMs: Long = 0
    private var isLowBuffer: Boolean = true // 初始缓冲为 0，低于任何水位线

    /** 根据网络条件更新水位线 */
    fun updateNetworkCondition(condition: NetworkCondition) {
        waterLines = BufferWaterLines.forCondition(condition)
        checkBufferState()
    }

    /** 更新当前缓冲时长（毫秒） */
    fun updateBuffer(bufferMs: Long) {
        currentBufferMs = bufferMs
        checkBufferState()
    }

    private fun checkBufferState() {
        val wasLow = isLowBuffer
        isLowBuffer = currentBufferMs < waterLines.lowMs
        if (wasLow != isLowBuffer) {
            onBufferStateChanged?.invoke(isLowBuffer)
        }
    }

    /** 当前缓冲占最大水位线的百分比 (0.0 - 1.0) */
    val bufferPercent: Double
        get() {
            if (waterLines.maxMs <= 0) return 0.0
            return (currentBufferMs.toDouble() / waterLines.maxMs).coerceIn(0.0, 1.0)
        }

    fun getCurrentBufferMs(): Long = currentBufferMs
    fun getIsLowBuffer(): Boolean = isLowBuffer
    fun getWaterLines(): BufferWaterLines = waterLines
}
