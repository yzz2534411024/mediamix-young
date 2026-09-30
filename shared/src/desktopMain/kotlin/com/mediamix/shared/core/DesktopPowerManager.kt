package com.mediamix.shared.core

/**
 * Desktop actual 实现 PowerManager
 *
 * 骨架实现，返回默认值（桌面端通常无电池管理）。
 * TODO: 可通过 JNA 调用系统 API 获取真实电源信息。
 */
actual class PowerManager actual constructor() {

    actual fun getBatteryLevel(): Int {
        // 桌面端默认返回满电
        return 100
    }

    actual fun isCharging(): Boolean {
        // 桌面端默认视为已充电
        return true
    }

    actual fun getPowerMode(): PowerMode {
        // 桌面端默认高性能模式
        return PowerMode.HIGH_PERFORMANCE
    }

    actual fun isBatteryLow(): Boolean {
        // 桌面端默认电量充足
        return false
    }

    actual fun shouldReduceQuality(): Boolean {
        // 桌面端默认无需降低质量
        return false
    }
}