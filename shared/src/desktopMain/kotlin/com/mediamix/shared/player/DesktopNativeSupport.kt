package com.mediamix.shared.player

import java.awt.Component

/**
 * Desktop 平台的小工具：把 AWT 组件（Canvas）转成原生窗口句柄（HWND）。
 *
 * 生产路径由 `VideoSurface.desktop.kt`（Compose SwingPanel + Canvas）调用，
 * 这里额外暴露给 `--mpv-probe` 自检模式 —— 它需要自己造一个真实窗口来验证
 * 「mpv + wid + 渲染」整条链路（否则只能靠人手点播放复现）。
 *
 * JNA 在 shared 的 desktopMain 依赖里（implementation），所以必须经由本函数
 * 转出，`desktopApp` 不能直接 import com.sun.jna。
 */
fun awtComponentId(component: Component): Long =
    runCatching { com.sun.jna.Native.getComponentID(component) }.getOrDefault(0L)
