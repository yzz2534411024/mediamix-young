package com.mediamix.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Android 实现：通过 Activity.requestedOrientation 切换方向。
 *
 * 之所以用 `SENSOR_LANDSCAPE` 而不是 `LANDSCAPE`：前者会在横屏的基础上继续
 * 允许左右两个方向随重力感应翻转，用户躺在床上看时不会看到倒置画面；
 * 后者会把方向锁死成一侧。
 *
 * Manifest 里已经声明了 `configChanges="orientation|screenSize|..."`，
 * 所以旋转不会重建 Activity —— 播放器实例与播放进度都不会丢。
 */
@Composable
actual fun ApplyScreenOrientation(mode: ScreenOrientationMode) {
    val context = LocalContext.current

    DisposableEffect(mode) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation

        activity?.requestedOrientation =
            when (mode) {
                ScreenOrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ScreenOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ScreenOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }

        onDispose {
            // 离开播放页务必还原，否则整个 App 会一直被锁在横屏。
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

/**
 * 从 Context 里往上找 Activity。
 *
 * Compose 的 LocalContext 在有些宿主里拿到的是 ContextWrapper（如 ContextThemeWrapper），
 * 不是 Activity 本身，直接强转会失败。
 */
/** 从 Context 向上找宿主 Activity（屏幕方向与亮度两个平台实现共用）。 */
internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
