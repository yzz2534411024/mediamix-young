package com.mediamix.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 平台视频渲染 Surface — expect 声明
 *
 * Android actual: AndroidView + SurfaceView
 * Desktop actual: SwingPanel + AWT Panel (mpv wid)
 *
 * @param onSurfaceCreated 渲染目标创建时回调，传递平台原生的渲染对象
 *   - Android: `android.view.TextureView`（交给 `ExoPlayer.setVideoTextureView`）
 *   - Desktop: `Long`（AWT Canvas 的 window handle / wid，交给 mpv 的 wid 选项）
 * @param onSurfaceDestroyed Surface 销毁时回调
 */
@Composable
expect fun VideoSurface(
    modifier: Modifier = Modifier,
    onSurfaceCreated: (Any) -> Unit,
    onSurfaceDestroyed: () -> Unit,
)
