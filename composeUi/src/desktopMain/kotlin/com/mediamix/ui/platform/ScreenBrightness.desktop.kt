package com.mediamix.ui.platform

import androidx.compose.runtime.Composable

/** Desktop 实现：桌面没有"屏幕亮度"这个概念，亮度手势只保留 UI 反馈。 */
@Composable
actual fun ApplyScreenBrightness(value: Float) = Unit
