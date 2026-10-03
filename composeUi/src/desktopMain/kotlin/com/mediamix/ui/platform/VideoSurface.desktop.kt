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
    onMouseActivity: () -> Unit,
) {
    SwingPanel(
        factory = {
            val canvas = Canvas()
            canvas.background = Color.BLACK
            // 不可聚焦：点击视频后 AWT 焦点保持在 Compose 侧，
            // 键盘事件（Esc/Space/←→）才能到达 Compose 的快捷键处理。
            canvas.isFocusable = false

            // mpv 嵌在 AWT Canvas（wid 模式）里收不到鼠标移动消息，
            // OSC 无法自行唤醒 —— 这里监听 AWT 鼠标事件，转发给引擎
            //（script-message osc-show）。OSC 显示后自带 ~3s 自动隐藏。
            val activity: (java.awt.event.MouseEvent?) -> Unit = { onMouseActivity() }
            canvas.addMouseListener(object : java.awt.event.MouseListener {
                override fun mouseClicked(e: java.awt.event.MouseEvent) = activity(e)
                override fun mousePressed(e: java.awt.event.MouseEvent) = activity(e)
                override fun mouseReleased(e: java.awt.event.MouseEvent) = activity(e)
                override fun mouseEntered(e: java.awt.event.MouseEvent) = activity(e)
                override fun mouseExited(e: java.awt.event.MouseEvent) {}
            })
            canvas.addMouseMotionListener(object : java.awt.event.MouseMotionListener {
                override fun mouseDragged(e: java.awt.event.MouseEvent) = activity(e)
                override fun mouseMoved(e: java.awt.event.MouseEvent) = activity(e)
            })

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
