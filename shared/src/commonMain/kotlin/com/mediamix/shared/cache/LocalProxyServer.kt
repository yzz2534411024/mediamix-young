package com.mediamix.shared.cache

/**
 * 本地 HTTP 代理服务器 —— 跨平台接口。
 *
 * 职责：拦截播放器请求，命中缓存时直接回本地文件/分片，
 * 未命中时转发 CDN 并把流数据边下边存（512KB 分片）。
 *
 * 请求路径约定：`/vod/{videoId}?url={cdn_url}&quality={quality}`
 *
 * 平台实现：
 * - Desktop → [JdkLocalProxyServer]（基于 `com.sun.net.httpserver`，JDK 自带）
 * - Android → [AndroidLocalProxyServer]（Android 运行时**没有** `com.sun.net.httpserver`，
 *   降级为直连 CDN）
 *
 * ⚠️ 本文件必须保持 platform-agnostic：不要在这里 import `java.*` 或 `com.sun.*`，
 * 否则 Android 侧会在运行时抛 `NoClassDefFoundError`。
 */
interface LocalProxyServer {
    /** 当前监听端口；未启动时为 0。 */
    val currentPort: Int

    /** 代理是否已启动。 */
    val isRunning: Boolean

    /**
     * 启动代理服务，绑定 127.0.0.1 的随机可用端口。
     *
     * 约定：幂等（已启动时直接返回）；启动失败不得抛异常，只记录日志并保持未启动状态。
     */
    fun start()

    /** 停止代理并释放资源。未启动时为空操作。 */
    fun stop()

    /**
     * 把 CDN 地址包装成走本地代理的地址。
     *
     * 若当前平台不支持进程内代理，实现应原样返回 [cdnUrl]。
     */
    fun proxyUrl(
        cdnUrl: String,
        videoId: String,
        quality: String = "720p",
    ): String

    /**
     * 解析 HTTP `Range` 头，返回 `(start, end)` 闭区间；非法或无法满足时返回 null。
     *
     * 同时支持 `bytes=start-end`、`bytes=start-`、`bytes=-suffix` 之外的常见形式，
     * 并会把 `end` 收敛到 `fileSize - 1`。属于协议层逻辑，平台实现共用。
     */
    fun parseRange(
        header: String,
        fileSize: Long,
    ): Pair<Long, Long>? {
        if (fileSize <= 0) return null
        return try {
            val match = Regex("""bytes=(\d+)-(\d*)""").find(header) ?: return null
            val start = match.groupValues[1].toLong()
            val endStr = match.groupValues[2]
            val end = if (endStr.isNotEmpty()) endStr.toLong() else fileSize - 1
            if (start >= fileSize || start > end) return null
            Pair(start, minOf(end, fileSize - 1))
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * 创建当前平台的本地代理实现。
 *
 * 由各平台 source set 提供 actual：Desktop 走 JdkLocalProxyServer，Android 走降级实现。
 */
expect fun createLocalProxyServer(cacheService: VideoCacheService): LocalProxyServer
