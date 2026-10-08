package com.mediamix.ui.util

/**
 * 构建形态标记。
 *
 * - **Android**：[androidApp] 的 MainActivity 在启动时写入 `BuildConfig.DEBUG`
 *   （debug 包 = true，release 包 = false）。
 * - **Desktop**：`Main.kt` 读取启动参数，`--debug` 时为 true（开发自用），
 *   打包产物默认 false（视为正式版）。
 *
 * 设置页的"播放诊断"入口、调试路由等**仅调试功能**据此显隐：
 * release 正式包完全不可见（使用记录 / 崩溃日志等正式功能不受影响）。
 */
object BuildFlavor {
    /** 由各平台入口在应用启动最早时刻写入。 */
    var debugMode: Boolean = false
}
