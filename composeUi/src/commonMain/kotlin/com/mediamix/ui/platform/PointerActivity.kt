package com.mediamix.ui.platform

import androidx.compose.ui.Modifier

/**
 * 指针活动回调（桌面端的「鼠标移动」）。
 *
 * 播放页控制栏 4 秒自动隐藏，而桌面用户不看「点击屏幕切换」这套移动端习惯 ——
 * 结果控制栏一闪而过后再也召不回来（实测反馈「播放页没有任何组件」）。
 * 桌面播放器的惯例是**鼠标一移动就显示控制栏**，这里提供跨端入口：
 * Android 是触摸设备、无鼠标移动语义，actual 为 no-op（点按切换照旧）。
 */
expect fun Modifier.onPointerActivity(action: () -> Unit): Modifier
