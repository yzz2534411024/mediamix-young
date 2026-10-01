package com.mediamix.ui.platform

import androidx.compose.runtime.Composable

/**
 * Desktop 实现：空操作。
 *
 * 桌面窗口没有"屏幕方向"的概念 —— 用户可以任意拖拽窗口尺寸。
 * 播放页在桌面端靠 Compose 的 `BoxWithConstraints` 自行适配宽高比即可。
 */
@Composable
actual fun ApplyScreenOrientation(mode: ScreenOrientationMode) {
    // no-op
}
