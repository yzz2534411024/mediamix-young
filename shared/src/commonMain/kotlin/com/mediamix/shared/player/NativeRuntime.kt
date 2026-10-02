package com.mediamix.shared.player

/**
 * 播放引擎的原生运行库准备（跨平台入口）。
 *
 * 背景：桌面端播放依赖 mpv 的 `libmpv-2.dll`（原始 115 MB / zip 45 MB），
 * 内置进发行包等于让每个用户都白背这份体积 —— 改为首次播放时按需下载，
 * 之后常驻本地缓存复用。Android 用 ExoPlayer，运行库随系统/应用自带，无需准备。
 *
 * 调用约定：在装载媒体**之前** await 它；失败时抛异常，由调用方转成用户可见的提示。
 *
 * @param onProgress 下载进度 0f..1f（Android 端不会回调）
 */
expect suspend fun ensureNativeRuntime(onProgress: ((Float) -> Unit)? = null)

/** 原生运行库是否已就绪（不发网络请求）。UI 可用它决定是否显示"准备中"提示。 */
expect fun isNativeRuntimeReady(): Boolean

/** 运行库本地目录（用于错误提示里的"手动放置"指引；Android 端为空串）。 */
expect fun nativeRuntimeHint(): String
