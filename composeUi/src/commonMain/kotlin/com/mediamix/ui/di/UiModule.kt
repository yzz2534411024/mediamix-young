package com.mediamix.ui.di

import com.mediamix.shared.spider.SpiderHealthStore
import com.mediamix.ui.player.PlaybackSessionStore
import com.mediamix.ui.prefs.AppPreferences
import com.mediamix.ui.source.SourceRepository
import com.mediamix.ui.viewmodel.*
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * UI 层 Koin DI 模块
 *
 * 注册所有 ViewModel 与跨页面共享状态，依赖由 sharedModule 提供
 */
val uiModule: Module =
    module {
        // ==================== 跨页面共享状态 ====================

        // 数据源仓库：首页与「数据源管理」页共用一份，并负责落盘
        single { SourceRepository(settings = get()) }

        // 应用偏好（主题 / 解码 / 数据分享）
        single { AppPreferences(settings = get()) }

        // 播放会话：详情页写入完整剧集列表，播放页读取
        // （选集、上下集、失败换线都依赖它）
        single { PlaybackSessionStore() }

        // 内容网关：UI 层负责两项 shared 层不该管的持久化绑定 ——
        // 1. 站点健康度落盘（连续失败的站点跨启动继续隐藏）；
        // 2. 备用线路探测成功后的接口地址写回源仓库。
        // sharedModule 里的默认网关会被这里 override，因此全进程只有一份实例。
        single {
            val settings: com.russhwolf.settings.Settings = get()
            val repository: SourceRepository = get()
            com.mediamix.shared.services
                .SourceContentGateway(
                    videoApiService = get(),
                    spiderService = get(),
                    healthStore =
                        com.mediamix.shared.spider.SpiderHealthStore(
                            persistLoad = { settings.getStringOrNull(SpiderHealthStore.KEY_HEALTH) },
                            persistSave = { settings.putString(SpiderHealthStore.KEY_HEALTH, it) },
                        ),
                ).also { gateway ->
                    gateway.onEndpointResolved = { key, url -> repository.updateApiUrl(key, url) }
                }
        }

        // ==================== ViewModel ====================

        // ⚠️ 用 single 而非 factory：VideoHomeViewModel 需要跨页面共享 ——
        // 「换个源」、详情页数据源解析都依赖同一份当前源状态。用 factory 时
        // 每个注入点都会拿到各自的新实例，状态就会割裂。
        single {
            VideoHomeViewModel(
                sourceRepository = get(),
                gateway = get(),
            )
        }
        factory {
            VideoDetailViewModel(
                httpClient = get(),
                favoriteDao = get(),
                sourceRepository = get(),
                preloadService = get(),
                gateway = get(),
                resolver = get(),
            )
        }
        factory {
            PlayerViewModel(
                playerCoreManager = get(),
                playbackProgressDao = get(),
                watchHistoryDao = get(),
                subtitleService = get(),
                appPreferences = get(),
                sourceRepository = get(),
                resolver = get(),
                sessionStore = get(),
            )
        }
        factory {
            UsageStatsViewModel(
                watchHistoryDao = get(),
                playbackProgressDao = get(),
                favoriteDao = get(),
            )
        }
        factory { CrashLogViewModel(crashLogService = get()) }
        factory {
            DebugViewModel(
                metricsEngine = get(),
                cacheManager = get(),
                sourceRepository = get(),
                spiderService = get(),
                httpClient = get(),
            )
        }
        factory { SearchViewModel(httpClient = get(), spiderService = get()) }
        factory { HistoryViewModel(watchHistoryDao = get()) }
        factory { FavoriteViewModel(favoriteDao = get()) }
        factory {
            SettingsViewModel(
                preferences = get(),
                cacheManager = get(),
                watchHistoryDao = get(),
                favoriteDao = get(),
                metricsEngine = get(),
            )
        }
        factory {
            SourceManageViewModel(
                httpClient = get(),
                spiderService = get(),
                sourceRepository = get(),
            )
        }
        // 下载任务用 single：播放页可以「下载本集」，下载页展示同一份任务列表 ——
        // 用 factory 会让两处各自持有一份 activeDownloads/并发闸门，形同两套调度。
        single {
            DownloadViewModel(
                downloadDao = get(),
                downloadService = get(),
                settings = get(),
            )
        }
    }
