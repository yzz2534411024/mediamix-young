package com.mediamix.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MainScaffold(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    showBottomBar: Boolean = true,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(currentRoute = currentRoute, onNavigate = onNavigate)
            }
        },
        // 外层只负责让出底部导航栏的高度。状态栏 / 导航栏的系统内边距由
        // 各页面自己的 Scaffold + TopAppBar 处理 —— 两层都吃掉一次的话，
        // 内容顶部会多出一整条状态栏高度的空白。
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}
