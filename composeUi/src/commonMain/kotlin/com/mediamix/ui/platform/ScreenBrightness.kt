package com.mediamix.ui.platform

import androidx.compose.runtime.Composable

/**
 * 把播放页的亮度值应用到屏幕（0f..1f）。
 *
 * 为什么必须是 expect/actual：Compose Multiplatform 没有跨平台的"屏幕亮度"概念。
 * Android 通过 Activity 窗口的 `screenBrightness` 实现（**只影响本应用窗口**，
 * 不会改动系统设置）；Desktop 上移动端的"手势调亮度"没有对应物，故为空实现 ——
 * 桌面端亮度手势只保留 UI 反馈即可。
 *
 * 实现约定：离开播放页时**恢复系统亮度**（置 -1f），由实现方在组合销毁时自动完成，
 * 否则用户会以为"手机亮度坏了"。
 */
@Composable
expect fun ApplyScreenBrightness(value: Float)
