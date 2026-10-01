package com.mediamix.ui.platform

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

/**
 * 桌面端 actual：把垂直滚轮事件转成横向滚动。
 *
 * 用 [LazyListState.dispatchRawDelta]（非 suspend）而不是 scrollBy ——
 * Modifier 回调里拿不到协程作用域。
 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.horizontalWheelScroll(state: LazyListState): Modifier =
    this.onPointerEvent(PointerEventType.Scroll) { event ->
        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        if (dy != 0f) {
            // 系数 120：一格滚轮（通常 1.0）≈ 滚动一行 tab 的观感
            state.dispatchRawDelta(dy * 120f)
        }
    }
