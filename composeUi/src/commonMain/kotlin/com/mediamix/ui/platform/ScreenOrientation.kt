package com.mediamix.ui.platform

import androidx.compose.runtime.Composable

/**
 * 播放页期望的屏幕方向。
 */
enum class ScreenOrientationMode {
    /** 交还系统决定（退出播放页时用） */
    AUTO,

    /** 强制横屏 */
    LANDSCAPE,

    /** 强制竖屏 */
    PORTRAIT,
}

/**
 * 请求宿主切换屏幕方向。
 *
 * 为什么必须是 expect/actual：Compose Multiplatform 没有跨平台的"窗口方向"概念。
 * Android 靠 Activity.requestedOrientation，Desktop 根本不存在方向，
 * 所以 Desktop 侧是空实现。
 *
 * 实现约定：调用方只需声明"我希望是横屏"，**恢复动作由实现方在离开组合时自动完成**，
 * 否则很容易出现"退出播放页后整个 App 被锁死在横屏"。
 */
@Composable
expect fun ApplyScreenOrientation(mode: ScreenOrientationMode)
