package com.mediamix.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mediamix.ui.App
import com.mediamix.ui.theme.MediaMixTheme
import com.mediamix.ui.theme.ThemeConfig
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
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
