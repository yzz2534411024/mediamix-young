package com.mediamix.ui.di

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

        // ==================== ViewModel ====================

        // ⚠️ 用 single 而非 factory：VideoHomeViewModel 需要跨页面共享 ——
        // 「换个源」、详情页数据源解析都依赖同一份当前源状态。用 factory 时
        // 每个注入点都会拿到各自的新实例，状态就会割裂。
        single {
            VideoHomeViewModel(
                spiderService = get(),
                httpClient = get(),
                sourceRepository = get(),
                videoApiService = get(),
            )
        }
        factory {
            VideoDetailViewModel(
                httpClient = get(),
                spiderService = get(),
                favoriteDao = get(),
                sourceRepository = get(),
                preloadService = get(),
            )
        }
        factory {
            PlayerViewModel(
                playerCoreManager = get(),
                playbackProgressDao = get(),
                subtitleService = get(),
                appPreferences = get(),
            )
        }
        factory { SearchViewModel(httpClient = get(), spiderService = get()) }
        factory { HistoryViewModel(watchHistoryDao = get()) }
        factory { FavoriteViewModel(favoriteDao = get()) }
        factory {
            SettingsViewModel(
                preferences = get(),
                videoCacheService = get(),
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
        factory { DownloadViewModel(downloadDao = get(), httpClient = get()) }
        factory {
            DebugViewModel(
                metricsEngine = get(),
                videoCacheService = get(),
                sourceRepository = get(),
                spiderService = get(),
            )
        }
    }
