package com.mediamix.shared.di

import com.mediamix.shared.cache.CacheStrategyManager
import com.mediamix.shared.cache.DiskCache
import com.mediamix.shared.cache.createLocalProxyServer
import com.mediamix.shared.cache.MemoryCache
import com.mediamix.shared.cache.VideoCacheService
import com.mediamix.shared.core.DeviceCapability
import com.mediamix.shared.core.PlatformPaths
import com.mediamix.shared.core.PowerManager
import com.mediamix.shared.database.DownloadDao
import com.mediamix.shared.database.FavoriteDao
import com.mediamix.shared.database.MediaMixDatabase
import com.mediamix.shared.database.PlaybackProgressDao
import com.mediamix.shared.database.WatchHistoryDao
import com.mediamix.shared.database.createDatabase
import com.mediamix.shared.network.HttpClientFactory
import com.mediamix.shared.player.ABRController
import com.mediamix.shared.player.BufferManager
import com.mediamix.shared.player.PlayerCoreManager
import com.mediamix.shared.player.PlayerEngine
import com.mediamix.shared.player.SubtitleService
import com.mediamix.shared.player.engines.CacheEngine
import com.mediamix.shared.player.engines.CacheEngineImpl
import com.mediamix.shared.player.engines.MetricsEngine
import com.mediamix.shared.player.engines.MetricsEngineImpl
import com.mediamix.shared.player.engines.PlaybackErrorHandler
import com.mediamix.shared.player.engines.PlaybackErrorHandlerImpl
import com.mediamix.shared.services.PreloadService
import com.mediamix.shared.spider.SpiderRegistry
import com.mediamix.shared.spider.SpiderService
import com.russhwolf.settings.Settings
import io.ktor.client.*
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 共享层 Koin DI 模块
 *
 * 提供：
 * - 网络层（Ktor HttpClient）
 * - 蜘蛛引擎（SpiderRegistry、SpiderService）
 * - 缓存系统（MemoryCache、DiskCache、VideoCacheService、LocalProxyServer）
 * - 数据库（SQLDelight DriverFactory、DAO）
 * - 预加载（PreloadService）
 * - 播放核心（PlayerEngine、PlayerCoreManager、CacheEngine、PlaybackErrorHandler、
 *   MetricsEngine、ABRController、BufferManager、SubtitleService、PowerManager、DeviceCapability）
 */
val sharedModule: Module = module {
    // ==================== 网络层 ====================

    // Ktor HttpClient 单例
    single<HttpClient> { HttpClientFactory.createHttpClient() }

    // ==================== 蜘蛛引擎 ====================

    single { SpiderRegistry.instance }
    single { SpiderService(registry = get(), httpClient = get()) }

    // ==================== 缓存系统 ====================

    single { Settings() }
    single { MemoryCache() }
    single { DiskCache(cacheDir = PlatformPaths.cacheDir) }
    single { CacheStrategyManager(settings = get()) }
    single {
        VideoCacheService(
            memoryCache = get(),
            diskCache = get(),
            cacheStrategyManager = get(),
        )
    }
    // 平台相关的本地代理：Desktop 走 JDK HttpServer，Android 走降级实现
    single { createLocalProxyServer(get()) }

    // ==================== 数据库 ====================

    single<MediaMixDatabase> { createDatabase() }
    single { WatchHistoryDao(get()) }
    single { FavoriteDao(get()) }
    single { PlaybackProgressDao(get()) }
    single { DownloadDao(get()) }

    // ==================== 预加载 ====================

    single { PreloadService(cacheService = get()) }

    // ==================== 播放核心 — 平台相关 ====================

    // expect/actual 类：无参构造，各平台提供 actual 实现
    single { PlayerEngine() }
    single { PowerManager() }
    single { DeviceCapability() }

    // ==================== 播放核心 — 子模块 ====================

    single { ABRController(settings = get()) }
    single { BufferManager() }
    single { SubtitleService(httpClient = get()) }

    // ==================== 播放核心 — 引擎（接口 → 实现） ====================

    single<CacheEngine> { CacheEngineImpl(cacheService = get(), proxyServer = get()) }
    single<PlaybackErrorHandler> { PlaybackErrorHandlerImpl() }
    single<MetricsEngine> { MetricsEngineImpl() }

    // ==================== 播放核心 — 编排器 ====================

    single {
        PlayerCoreManager(
            playerEngine = get(),
            cacheEngine = get(),
            errorHandler = get(),
            metricsEngine = get(),
            abrController = get(),
            bufferManager = get(),
            subtitleService = get(),
            powerManager = get(),
            settings = get(),
        )
    }
}