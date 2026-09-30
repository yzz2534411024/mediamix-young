package com.mediamix.shared.core

/**
 * 设备能力探测接口 — expect 声明
 *
 * Android actual: 使用 MediaCodecList
 * Desktop actual: 骨架实现，返回默认值
 */
expect class DeviceCapability() {
    fun supportsHardwareDecoding(codec: String): Boolean
    fun getMaxResolution(): Pair<Int, Int>
    fun getDeviceName(): String
}