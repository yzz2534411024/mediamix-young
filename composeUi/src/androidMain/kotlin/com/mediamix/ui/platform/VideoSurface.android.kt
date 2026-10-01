package com.mediamix.ui.platform

import android.view.TextureView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android actual 实现 VideoSurface —— 使用 [TextureView]。
 *
 * **为什么不用 SurfaceView**：SurfaceView 拥有位于窗口 layer 之外的独立 Surface，
 * 其 z-order 与普通 View 不同源，会破坏与 Compose 图层（字幕、控制栏、手势层）的
 * 叠加顺序 —— 而本播放器页面的字幕与控件全部是覆盖在视频之上的 Compose 内容。
 * TextureView 参与常规 View 绘制，叠加顺序天然正确。
 *
 * 代价：TextureView 比 SurfaceView 多一次纹理拷贝，且不支持 DRM 安全视频 ——
 * 这两点本项目都不涉及。
 *
 * ⚠️ 这里不注册 `surfaceTextureListener`：`ExoPlayer.setVideoTextureView()` 会自行注册
 * 并覆盖掉外部监听器，注册了反而会让本回调失效。因此改为在 View attach 到窗口时
 * 直接把 TextureView 交给播放器，由 ExoPlayer 负责等待 SurfaceTexture 可用。
 */
@Composable
actual fun VideoSurface(
    modifier: Modifier,
    onSurfaceCreated: (Any) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            onSurfaceCreated(this@apply)
                        }

                        override fun onViewDetachedFromWindow(v: View) {
                            onSurfaceDestroyed()
                        }
                    },
                )
            }
        },
        modifier = modifier,
    )
}
