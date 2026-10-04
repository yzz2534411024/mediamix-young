package com.mediamix.shared.di

import com.mediamix.shared.cache.CacheManager
import com.mediamix.shared.cache.CacheStrategyManager
import com.mediamix.shared.cache.DiskCache
import com.mediamix.shared.cache.MemoryCache
import com.mediamix.shared.cache.VideoCacheService
import com.mediamix.shared.cache.createLocalProxyServer
import com.mediamix.shared.core.DeviceCapability
import com.mediamix.shared.core.CrashLogService
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
import com.mediamix.shared.services.DownloadService
import com.mediamix.shared.services.PlaybackResolver
import com.mediamix.shared.services.PreloadService
import com.mediamix.shared.services.SourceContentGateway
import com.mediamix.shared.spider.SpiderRegistry
import com.mediamix.shared.spider.SpiderService
import com.mediamix.shared.spider.VideoApiService
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
val sharedModule: Module =
    module {
        // ==================== 网络层 ====================

        // Ktor HttpClient 单例
        single<HttpClient> { HttpClientFactory.createHttpClient() }

        // ==================== 蜘蛛引擎 ====================

        single { SpiderRegistry.instance }
        single { SpiderService(registry = get(), httpClient = get()) }

        // CMS 接口服务：带 DNS 预解析（5min）与列表结果缓存（5min）。
        // 用 shared 单例，保证 CmsSpider 与首页读到的是同一份缓存。
        single { VideoApiService.shared }

        // 内容网关：**唯一**分流「CMS 协议」与「TVBox 蜘蛛」的地方。
        // ViewModel 不应再自己写 if (site.isTvBox)。
        // 站点健康度用 UI 层用 Settings 持久化，由 UiModule 的网关定义注入。
        single { SourceContentGateway(videoApiService = get(), spiderService = get()) }

        // 剧集标识 → 真实播放地址（含请求头）。详情页与播放页共用，
        // 使 TVBox 的切集/连播也能按需解析（不依赖详情页是否还在返回栈上）。
        single { PlaybackResolver(spiderService = get<SpiderService>(), gateway = get<SourceContentGateway>()) }

        // ==================== 缓存系统 ====================

        single { Settings() }
        single { MemoryCache() }
        single { DiskCache(cacheDir = PlatformPaths.cacheDir) }
        single { CrashLogService(dataDir = PlatformPaths.dataDir) }
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

        // 缓存门面：设置页 / 诊断页统一用它读写占用与清理，
        // 不再各自直接引用 MemoryCache / DiskCache / VideoCacheService 中的某一套。
        single { CacheManager(videoCacheService = get()) }

        // 下载引擎：HLS 分片合并 / 请求头透传 / 断点续传 / 并发闸门都在它内部。
        single { DownloadService() }

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

        single<CacheEngine> { CacheEngineImpl(cacheService = get(), proxyServer = get(), preloadService = get()) }
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
