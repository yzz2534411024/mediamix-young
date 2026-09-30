package com.mediamix.shared.cache

import co.touchlab.kermit.Logger

/**
 * Android 平台的本地代理 —— 降级实现。
 *
 * **为什么是降级实现**：Android 运行时（ART）不包含 JDK 的 `com.sun.net.httpserver` 模块
 * （实测 `android.jar` 中该类条目数为 0），原来的实现在 Android 上会抛
 * `NoClassDefFoundError`；而 `NoClassDefFoundError` 继承自 `Error`，
 * 调用方的 `catch (Exception)` 无法捕获，会直接把进程打挂。
 *
 * 因此本实现不做进程内代理：
 * - [start] / [stop] 为空操作，[isRunning] 恒为 false，[currentPort] 恒为 0
 * - [proxyUrl] 原样返回 CDN 地址，由播放器直连
 *
 * 代价是失去「边播边缓存」。后续如需补回，可引入 Ktor Server(CIO) 或基于
 * `ServerSocket` 自实现一个轻量 HTTP 服务，但务必保证异常不被吞掉。
 *
 * @param cacheService 暂时未使用，保留以便后续实现真实代理时直接接入缓存层。
 */
class AndroidLocalProxyServer(
    @Suppress("UNUSED_PARAMETER") private val cacheService: VideoCacheService,
) : LocalProxyServer {

    private val logger = Logger.withTag("LocalProxyServer")
    private var warned = false

    override val currentPort: Int get() = 0
    override val isRunning: Boolean get() = false

    override fun start() {
        if (!warned) {
            warned = true
            logger.i { "Android 不支持进程内代理，播放时直连 CDN" }
        }
    }

    override fun stop() = Unit

    override fun proxyUrl(cdnUrl: String, videoId: String, quality: String): String = cdnUrl
}

actual fun createLocalProxyServer(cacheService: VideoCacheService): LocalProxyServer =
    AndroidLocalProxyServer(cacheService)
