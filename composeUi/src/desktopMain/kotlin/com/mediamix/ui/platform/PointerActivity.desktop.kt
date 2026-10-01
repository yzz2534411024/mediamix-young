package com.mediamix.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

/** 桌面端 actual：鼠标移动即回调（用于唤起播放控制栏并重置自动隐藏计时）。 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onPointerActivity(action: () -> Unit): Modifier =
    this.onPointerEvent(PointerEventType.Move) { action() }
