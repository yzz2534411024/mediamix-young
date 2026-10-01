package com.mediamix.shared.spider

import co.touchlab.kermit.Logger

/**
 * Android actual — JavaBridgeManager 存根实现
 *
 * TVBox 蜘蛛 JAR 可能依赖 Android API（如 Context），
 * 当前阶段提供存根实现，后续根据需要完善。
 *
 * 注意：Android 运行在 ART 虚拟机上，理论上支持 URLClassLoader，
 * 但蜘蛛 JAR 中的 Android 依赖需要额外处理。
 */
actual class JavaBridgeManager private constructor() {
    private val logger = Logger.withTag("JavaBridgeManager")

    actual val isInitialized: Boolean = false

    actual suspend fun loadSpiderJar(jarPath: String): Boolean {
        logger.w { "Android 端 JavaBridgeManager 暂未实现" }
        return false
    }

    actual suspend fun invokeMethod(
        spiderKey: String,
        method: String,
        args: Map<String, Any?>,
    ): Map<String, Any?> = mapOf<String, Any?>("code" to -1, "msg" to "Android 端 JavaBridgeManager 暂未实现")

    actual fun release() {
        // No-op for stub
    }

    actual companion object {
        @Volatile
        private var _instance: JavaBridgeManager? = null

        actual val instance: JavaBridgeManager
            get() =
                _instance ?: synchronized(this) {
                    _instance ?: JavaBridgeManager().also { _instance = it }
                }
    }
}
