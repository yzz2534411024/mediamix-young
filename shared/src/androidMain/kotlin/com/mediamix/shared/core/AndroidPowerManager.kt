package com.mediamix.shared.core

/**
 * Android actual — PowerManager
 *
 * 使用 Android BatteryManager 获取电池信息。
 * 需要通过 DI 注入 Context。
 */
actual class PowerManager actual constructor() {
    private var context: android.content.Context? = null

    fun init(context: android.content.Context) {
        this.context = context
    }

    actual fun getBatteryLevel(): Int {
        val ctx = context ?: return 100
        val ifilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = ctx.registerReceiver(null, ifilter) ?: return 100
        val level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 100
    }

    actual fun isCharging(): Boolean {
        val ctx = context ?: return true
        val ifilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = ctx.registerReceiver(null, ifilter) ?: return true
        val status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    actual fun getPowerMode(): PowerMode {
        val level = getBatteryLevel()
        val charging = isCharging()
        return when {
            charging || level > 50 -> PowerMode.HIGH_PERFORMANCE
            level >= 20 -> PowerMode.BALANCED
            else -> PowerMode.POWER_SAVING
        }
    }

    actual fun isBatteryLow(): Boolean = getBatteryLevel() < 20 && !isCharging()

    actual fun shouldReduceQuality(): Boolean = getPowerMode() == PowerMode.POWER_SAVING || isBatteryLow()
}
