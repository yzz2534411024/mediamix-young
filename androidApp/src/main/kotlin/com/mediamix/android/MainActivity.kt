package com.mediamix.android

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mediamix.ui.App
import com.mediamix.ui.theme.MediaMixTheme
import com.mediamix.ui.theme.ThemeConfig

class MainActivity : ComponentActivity() {
    /**
     * 画中画（PiP）基础版：播放中用户按 Home 离开时自动缩小成悬浮窗继续播。
     * 是否"正在播放"由 PlayerEngine 单例的当前状态判定（无需感知导航栈）。
     */
    private fun isInPlayback(): Boolean =
        try {
            com.mediamix.shared.player.PlayerCoreManager.lastInstance
                ?.playerState == com.mediamix.shared.player.PlayerState.PLAYING
        } catch (_: Exception) {
            false
        }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPlayback()) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
            )
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        // 边到边：内容可绘制到状态栏 / 导航栏下方，播放页才能真全屏。
        // 各页面的内边距由 Compose 侧按 windowInsets 自行处理
        // （Scaffold 的 contentWindowInsets / TopAppBar 的 windowInsets）。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by ThemeConfig.themeMode.collectAsState()
            MediaMixTheme(themeMode = themeMode) {
                App()
            }
        }
    }
}
