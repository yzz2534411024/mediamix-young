package com.mediamix.shared.player.engines

import co.touchlab.kermit.Logger
import com.mediamix.shared.cache.LocalProxyServer
import com.mediamix.shared.cache.VideoCacheService
import com.mediamix.shared.core.PlatformInfo
import com.mediamix.shared.core.PowerMode
import com.mediamix.shared.services.PreloadPriority
import com.mediamix.shared.services.PreloadService
import com.mediamix.shared.services.PreloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Cache engine implementation — resolves video URLs via local cache / proxy.
 *
 * Migrated from cache_engine_impl.dart.
 */
class CacheEngineImpl(
    private val cacheService: VideoCacheService,
    private val proxyServer: LocalProxyServer,
    private val preloadService: PreloadService,
) : CacheEngine {
    /**
     * 预加载是挂起操作，而 [CacheEngine] 的接口是非挂起的，这里用自己的作用域桥接。
     *
     * 注意：此前 [preloadNextEpisode] 只是往 Set 里丢了个 id、**并没有真的预取**，
     * 所以「播放到 80% 时预热下一集」实际上是空转。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val logger = Logger.withTag("CacheEngine")

    private var _isUsingCache = false
    override val isUsingCache: Boolean get() = _isUsingCache

    // Preloaded but unused videoId set (for recycling)
    private val preloadedVideoIds = mutableSetOf<String>()

    /** 播放器是否正在缓冲 —— 真时不再发起新的预加载（见 [notifyPreloadBuffering]）。 */
    @Volatile
    private var isPlayerBuffering = false

    // ========================================================================
    // URL resolution
    // ========================================================================

    override suspend fun resolveVideoUrl(
        url: String,
        videoId: String,
    ): String {
        return try {
            // Check full cache (L3 disk cache)
            val cachedPath = cacheService.getCachePath(videoId)
            if (cachedPath != null) {
                logger.i("Cache hit, using local file: $videoId")
                _isUsingCache = true
                preloadedVideoIds.remove(videoId)
                return cachedPath
            }

            _isUsingCache = false

            // Android 没有进程内代理（见 AndroidLocalProxyServer 的说明）：播放缓存
            // 由 Media3 的 CacheDataSource 在数据源层完成，这里原样放行即可 ——
            // 之前仍然去 start() + proxyUrl()，等于每次播放都绕一圈恒等调用。
            if (PlatformInfo.isAndroid) return url

            // Start local proxy for stream-and-cache mode
            try {
                proxyServer.start()
            } catch (e: Exception) {
                logger.w("Local proxy start failed, using original URL: $e")
                _isUsingCache = false
                return url
            }

            val proxyUrl = proxyServer.proxyUrl(url, videoId)
            logger.i("Playing via local proxy: $videoId -> 127.0.0.1:${proxyServer.currentPort}")
            proxyUrl
        } catch (e: Exception) {
            logger.w("Cache/proxy query failed, using network URL: $e")
            _isUsingCache = false
            url
        }
    }

    override suspend fun resolveVideoUrlWithFallback(
        url: String,
        videoId: String,
        preferredQuality: String?,
    ): CacheResolveResult {
        return try {
            val quality = preferredQuality ?: "720p"

            // 1. Exact match: requested quality is cached
            val exactPath = cacheService.getCachePath(videoId, quality = quality)
            if (exactPath != null) {
                logger.i("Cache exact hit: $videoId@$quality")
                _isUsingCache = true
                preloadedVideoIds.remove(videoId)
                return CacheResolveResult(url = exactPath, isUsingCache = true)
            }

            // 2. Fallback match: other quality cached
            val fallback = cacheService.getAnyQualityCachePath(videoId, preferredQuality = preferredQuality)
            if (fallback != null) {
                val (fallbackPath, fallbackQuality) = fallback
                logger.i("Cache fallback hit: $videoId requested ${preferredQuality ?: "default"}, using $fallbackQuality")
                _isUsingCache = true
                preloadedVideoIds.remove(videoId)
                return CacheResolveResult(
                    url = fallbackPath,
                    isUsingCache = true,
                    fallbackQuality = fallbackQuality,
                )
            }

            // 3. Miss: Android 直连（缓存由 Media3 CacheDataSource 在数据源层完成），
            //    Desktop 走进程内代理边播边存。
            _isUsingCache = false
            if (PlatformInfo.isAndroid) return CacheResolveResult(url = url, isUsingCache = false)
            try {
                proxyServer.start()
                val proxyUrl = proxyServer.proxyUrl(url, videoId)
                CacheResolveResult(url = proxyUrl, isUsingCache = false)
            } catch (e: Exception) {
                logger.w("Local proxy start failed, using original URL: $e")
                CacheResolveResult(url = url, isUsingCache = false)
            }
        } catch (e: Exception) {
            logger.w("Cache/proxy query failed, using network URL: $e")
            _isUsingCache = false
            CacheResolveResult(url = url, isUsingCache = false)
        }
    }

    // ========================================================================
    // Preload management
    // ========================================================================

    /**
     * 播放器进入/退出缓冲。
     *
     * 此前是个空实现 —— 于是「缓冲中还在抢带宽预加载下一集」这件事完全没被抑制，
     * 弱网下会让本来就慢的当前集更慢。现在用这个标志位挡住**新的**预加载，
     * 但不取消已在途的任务（取消会让已下载的部分白费）。
     */
    override fun notifyPreloadBuffering(isBuffering: Boolean) {
        isPlayerBuffering = isBuffering
        logger.d("Preload gating: player buffering=$isBuffering")
    }

    override fun preloadNextEpisode(
        videoId: String,
        url: String,
    ) {
        preloadedVideoIds.add(videoId)
        if (url.isBlank()) return
        if (isPlayerBuffering) {
            logger.d("Player is buffering, skipping preload of next episode: $videoId")
            return
        }
        scope.launch {
            try {
                preloadService.preloadNextEpisode(videoId, url)
            } catch (e: Exception) {
                logger.w("Preload next episode failed: $e")
            }
        }
    }

    override fun preloadAdjacentEpisodes(
        indices: List<Int>,
        title: String,
        episodeUrls: List<String>,
        powerMode: PowerMode,
    ) {
        if (powerMode == PowerMode.POWER_SAVING) {
            logger.d("Power saving mode, skipping preload")
            return
        }
        val tasks = mutableListOf<PreloadTask>()
        for (i in indices) {
            if (i >= 0 && i < episodeUrls.size) {
                val id = "${title}_$i"
                preloadedVideoIds.add(id)
                tasks.add(
                    PreloadTask(
                        videoId = id,
                        videoUrl = episodeUrls[i],
                        quality = "720p",
                        priority = PreloadPriority.ADJACENT_ITEM,
                    ),
                )
            }
        }
        if (tasks.isEmpty()) return
        scope.launch {
            try {
                preloadService.preloadVideos(tasks)
            } catch (e: Exception) {
                logger.w("Preload adjacent episodes failed: $e")
            }
        }
    }

    override fun cancelPreloads() {
        val unusedIds = preloadedVideoIds.toList()
        preloadedVideoIds.clear()

        for (videoId in unusedIds) {
            try {
                logger.d("Marking unused preload as inactive: $videoId")
            } catch (e: Exception) {
                logger.w("Failed to mark preload inactive: $videoId, error: $e")
            }
        }

        logger.i("Cancelled all preloads and recycled resources (${unusedIds.size} items)")
    }

    override fun dispose() {
        cancelPreloads()
        scope.cancel()
    }
}
