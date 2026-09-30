package com.mediamix.shared.player.engines

import co.touchlab.kermit.Logger
import com.mediamix.shared.cache.LocalProxyServer
import com.mediamix.shared.cache.VideoCacheService
import com.mediamix.shared.core.PowerMode

/**
 * Cache engine implementation — resolves video URLs via local cache / proxy.
 *
 * Migrated from cache_engine_impl.dart.
 */
class CacheEngineImpl(
    private val cacheService: VideoCacheService,
    private val proxyServer: LocalProxyServer,
) : CacheEngine {

    private val logger = Logger.withTag("CacheEngine")

    private var _isUsingCache = false
    override val isUsingCache: Boolean get() = _isUsingCache

    // Preloaded but unused videoId set (for recycling)
    private val preloadedVideoIds = mutableSetOf<String>()

    // ========================================================================
    // URL resolution
    // ========================================================================

    override suspend fun resolveVideoUrl(url: String, videoId: String): String {
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
        preferredQuality: String?
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
                    fallbackQuality = fallbackQuality
                )
            }

            // 3. Miss: go through proxy
            _isUsingCache = false
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

    override fun notifyPreloadBuffering(isBuffering: Boolean) {
        // Delegated to external preload service via callback if needed
    }

    override fun preloadNextEpisode(videoId: String, url: String) {
        preloadedVideoIds.add(videoId)
    }

    override fun preloadAdjacentEpisodes(
        indices: List<Int>,
        title: String,
        episodeUrls: List<String>,
        powerMode: PowerMode
    ) {
        if (powerMode == PowerMode.POWER_SAVING) {
            logger.d("Power saving mode, skipping preload")
            return
        }
        for (i in indices) {
            if (i >= 0 && i < episodeUrls.size) {
                preloadedVideoIds.add("${title}_$i")
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
    }
}