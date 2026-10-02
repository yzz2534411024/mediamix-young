package com.mediamix.shared.player

/** 桌面端：交给 [MpvNativeRuntime]（按需下载 + 本地缓存复用）。 */
actual suspend fun ensureNativeRuntime(onProgress: ((Float) -> Unit)?) {
    MpvNativeRuntime.ensure(onProgress)
}

actual fun isNativeRuntimeReady(): Boolean = MpvNativeRuntime.isReady

actual fun nativeRuntimeHint(): String = MpvNativeRuntime.runtimeDir.absolutePath
