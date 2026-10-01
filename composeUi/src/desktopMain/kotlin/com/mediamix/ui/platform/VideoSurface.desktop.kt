package com.mediamix.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.sun.jna.Native
import java.awt.Canvas
import java.awt.Color
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener

/**
 * Desktop actual 实现 VideoSurface
 *
 * 使用 SwingPanel 包裹 AWT Canvas，通过 JNA Native.getComponentID()
 * 获取原生窗口句柄 (HWND)，传递给 mpv 的 wid 选项进行视频渲染。
 */
@Composable
actual fun VideoSurface(
    modifier: Modifier,
    onSurfaceCreated: (Any) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    SwingPanel(
        factory = {
            val canvas = Canvas()
            canvas.background = Color.BLACK

            // 当 Canvas 被添加到可显示的窗口层级时，获取 HWND 并回调
            canvas.addHierarchyListener(
                HierarchyListener { event ->
                    if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                        if (canvas.isDisplayable) {
                            val hwnd: Long = Native.getComponentID(canvas)
                            if (hwnd != 0L) {
                                onSurfaceCreated(hwnd)
                            }
                        } else {
                            onSurfaceDestroyed()
                        }
                    }
                },
            )

            canvas
        },
        modifier = modifier,
    )
}
