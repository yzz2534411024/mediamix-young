package com.mediamix.ui.platform

import androidx.compose.ui.Modifier

/** Android 端 actual：触摸设备无鼠标移动语义，点按切换控制栏的行为保持不变。 */
actual fun Modifier.onPointerActivity(action: () -> Unit): Modifier = this
