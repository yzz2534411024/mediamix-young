package com.mediamix.shared.spider

/**
 * Java Bridge 管理器 — JVM 内直接加载 TVBox 蜘蛛 JAR
 *
 * 替代原 HTTP Bridge 进程间通信方案，直接在 JVM 内通过 ClassLoader 加载 JAR，
 * 反射调用蜘蛛方法，零网络开销。
 *
 * Desktop actual: 使用 URLClassLoader 加载 JAR 并反射调用
 * Android actual: 存根实现（TVBox 蜘蛛可能依赖 Android API）
 */
expect class JavaBridgeManager {
    companion object {
        val instance: JavaBridgeManager
    }

    /** 是否已初始化 */
    val isInitialized: Boolean

    /** 加载蜘蛛 JAR */
    suspend fun loadSpiderJar(jarPath: String): Boolean

    /**
     * 调用蜘蛛方法
     *
     * @param spiderKey 蜘蛛标识（如 csp_FanTaiYing）
     * @param method    方法名（init/home/category/detail/search/player）
     * @param args      方法参数
     * @return 结果 Map，失败时包含 code=-1 和 msg
     */
    suspend fun invokeMethod(
        spiderKey: String,
        method: String,
        args: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?>

    /** 释放资源 */
    fun release()
}
