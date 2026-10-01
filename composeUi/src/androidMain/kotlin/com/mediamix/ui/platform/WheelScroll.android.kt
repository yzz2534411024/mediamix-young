package com.mediamix.ui.platform

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier

/** Android 端 actual：触摸横向滑动是原生行为，无需处理滚轮。 */
actual fun Modifier.horizontalWheelScroll(state: LazyListState): Modifier = this
