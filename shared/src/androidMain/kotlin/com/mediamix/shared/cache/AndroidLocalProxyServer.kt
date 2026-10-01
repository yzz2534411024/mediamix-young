package com.mediamix.shared.cache

import co.touchlab.kermit.Logger

/**
 * Android 平台的本地代理 —— 直连实现。
 *
 * **为什么不做进程内代理**：Android 运行时（ART）不包含 JDK 的 `com.sun.net.httpserver`
 * 模块（实测 `android.jar` 中该类条目数为 0），原实现在 Android 上会抛
 * `NoClassDefFoundError`；而 `NoClassDefFoundError` 继承自 `Error`，
 * 调用方的 `catch (Exception)` 无法捕获，会直接把进程打挂。
 *
 * **边播边缓存已由 ExoPlayer 原生接管**（`ExoPlayerEngine.cacheDataSourceFactory`）：
 * 播放器的数据源链上挂了 `CacheDataSource` + `SimpleCache`(LRU 512MB)，
 * 已播分片落盘、复播/续播命中本地 —— 不再需要进程内 HTTP 代理转发。
 * 因此 [proxyUrl] 原样返回 CDN 地址是**正确行为**，播放器直连即可，
 * 缓存在数据源层透明完成。
 *
 * @param cacheService 暂未使用，保留以兼容接口签名。
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
            logger.i { "Android 播放缓存由 ExoPlayer CacheDataSource 接管，无需进程内代理" }
        }
    }

    override fun stop() = Unit

    override fun proxyUrl(
        cdnUrl: String,
        videoId: String,
        quality: String,
    ): String = cdnUrl
}

actual fun createLocalProxyServer(cacheService: VideoCacheService): LocalProxyServer = AndroidLocalProxyServer(cacheService)
