package com.mediamix.shared.core

/**
 * Desktop actual 实现 DeviceCapability
 *
 * 骨架实现，返回保守默认值。
 * TODO: 通过 JNA / Runtime.exec 获取真实硬件信息。
 */
actual class DeviceCapability actual constructor() {
    actual fun supportsHardwareDecoding(codec: String): Boolean {
        // mpv 通常支持硬件加速解码（通过 GPU API）
        return when (codec.uppercase()) {
            "H.264", "AVC" -> true
            "H.265", "HEVC" -> true
            "VP9" -> true
            "AV1" -> false // 部分硬件不支持 AV1 硬解
            else -> false
        }
    }

    actual fun getMaxResolution(): Pair<Int, Int> {
        // 桌面端默认支持 4K
        return Pair(3840, 2160)
    }

    actual fun getDeviceName(): String {
        val os = System.getProperty("os.name", "Unknown")
        val arch = System.getProperty("os.arch", "unknown")
        return "$os ($arch)"
    }
}
