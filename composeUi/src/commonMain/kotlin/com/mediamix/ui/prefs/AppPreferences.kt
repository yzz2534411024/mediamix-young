package com.mediamix.ui.prefs

import com.mediamix.ui.theme.ThemeMode
import com.russhwolf.settings.Settings

/** 解码模式 */
enum class DecodeMode {
    /** 硬解优先，失败自动回退软解 */
    AUTO,

    /** 强制硬解（异常编码可能黑屏，仅给高端机型用） */
    HARDWARE,

    /** 软解优先，兼容性最好、功耗最高 */
    SOFTWARE,
}

/** 展示用名称 */
fun DecodeMode.label(): String =
    when (this) {
        DecodeMode.AUTO -> "自动（硬解优先）"
        DecodeMode.HARDWARE -> "硬件解码优先"
        DecodeMode.SOFTWARE -> "软件解码优先"
    }

/** 一句话说明，设置页副标题用 */
fun DecodeMode.description(): String =
    when (this) {
        DecodeMode.AUTO -> "硬解失败时自动回退，推荐"
        DecodeMode.HARDWARE -> "省电、发热低；老旧设备可能黑屏"
        DecodeMode.SOFTWARE -> "兼容性最好，耗电稍高"
    }

/**
 * 应用级偏好设置。
 *
 * 之前这些开关要么是 Composable 里的 `remember`（离开页面就丢），要么写了
 * Settings 但没人读（主题选完不生效）。统一收在这里，读写都走同一份
 * multiplatform-settings。
 */
class AppPreferences(
    private val settings: Settings,
) {
    /** 主题模式 —— 与 [com.mediamix.ui.theme.ThemeConfig] 双向同步 */
    var themeMode: ThemeMode
        get() =
            ThemeMode.entries.getOrElse(
                settings.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.ordinal),
            ) { ThemeMode.SYSTEM }
        set(value) = settings.putInt(KEY_THEME_MODE, value.ordinal)

    /** 解码模式 */
    var decodeMode: DecodeMode
        get() =
            DecodeMode.entries.getOrElse(
                settings.getInt(KEY_DECODE_MODE, DecodeMode.AUTO.ordinal),
            ) { DecodeMode.AUTO }
        set(value) = settings.putInt(KEY_DECODE_MODE, value.ordinal)

    /** 是否分享使用数据（默认关闭，开启后记录播放指标） */
    var shareUsageData: Boolean
        get() = settings.getBoolean(KEY_SHARE_USAGE, false)
        set(value) = settings.putBoolean(KEY_SHARE_USAGE, value)

    /** 播放器是否软解优先 */
    val preferSoftwareDecoding: Boolean
        get() = decodeMode == DecodeMode.SOFTWARE

    /**
     * 快进 / 快退间隔（秒）。
     *
     * 以前这个值只活在 `PlayerCoreManager` 内部：设置页没有入口、`setSkipInterval()`
     * 全项目无人调用，而播放页又硬编码显示 10 秒 —— 整条链路是死的。
     * 现在以偏好为唯一真源，打开播放页时再推给播放器。
     */
    var skipIntervalSeconds: Int
        get() = settings.getInt(KEY_SKIP_INTERVAL, DEFAULT_SKIP_INTERVAL)
        set(value) =
            settings.putInt(
                KEY_SKIP_INTERVAL,
                if (value in SKIP_INTERVAL_OPTIONS) value else DEFAULT_SKIP_INTERVAL,
            )

    companion object {
        /** 可选间隔，与 `PlayerCoreManager.skipIntervals` 保持一致 */
        val SKIP_INTERVAL_OPTIONS = listOf(5, 10, 15, 30, 60)

        const val DEFAULT_SKIP_INTERVAL = 10

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DECODE_MODE = "decode_mode"
        private const val KEY_SHARE_USAGE = "share_usage_data"
        private const val KEY_SKIP_INTERVAL = "skip_interval_seconds"
    }
}
