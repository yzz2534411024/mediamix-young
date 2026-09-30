package com.mediamix.shared.core

/**
 * 电源管理接口 — expect 声明
 *
 * Android actual: 使用 BatteryManager
 * Desktop actual: 返回默认值（桌面端无电池管理）
 */
expect class PowerManager() {
    fun getBatteryLevel(): Int
    fun isCharging(): Boolean
    fun getPowerMode(): PowerMode

    /** 电量 < 20% 或未充电时返回 true */
    fun isBatteryLow(): Boolean

    /** 省电模式下应返回 true，提示降低质量 */
    fun shouldReduceQuality(): Boolean
}

/**
 * 电源模式枚举
 */
enum class PowerMode {
    HIGH_PERFORMANCE,
    BALANCED,
    POWER_SAVING
}