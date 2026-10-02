package com.mediamix.shared.player

/** Android 端：ExoPlayer 是系统/依赖自带的能力，无需准备运行库。 */
actual suspend fun ensureNativeRuntime(onProgress: ((Float) -> Unit)?) = Unit

actual fun isNativeRuntimeReady(): Boolean = true

actual fun nativeRuntimeHint(): String = ""
