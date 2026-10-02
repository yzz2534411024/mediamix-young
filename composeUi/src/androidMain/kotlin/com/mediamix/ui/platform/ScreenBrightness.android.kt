package com.mediamix.ui.platform

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android 实现：写 Activity 窗口的 `screenBrightness`。
 *
 * 要点：
 * - 用**窗口级**亮度（`attributes.screenBrightness`）而不是系统亮度 —— 只在本 App 生效，
 *   退出播放页即失效，不会污染用户的系统设置；
 * - 下限收敛到 0.01f：真实 0f 会让屏幕全黑，用户会以为 App 崩了；
 * - 退出播放页时置回 -1f（`BRIGHTNESS_OVERRIDE_NONE`）恢复系统亮度。
 */
@Composable
actual fun ApplyScreenBrightness(value: Float) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    SideEffect {
        val window = activity?.window ?: return@SideEffect
        val attrs = window.attributes
        attrs.screenBrightness = value.coerceIn(0.01f, 1f)
        window.attributes = attrs
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                val window = activity?.window ?: return@onDispose
                val attrs = window.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = attrs
            }
        }
    }
}
