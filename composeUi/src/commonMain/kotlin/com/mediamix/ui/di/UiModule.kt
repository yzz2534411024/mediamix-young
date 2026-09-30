package com.mediamix.ui.di

import com.mediamix.ui.viewmodel.*
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * UI 层 Koin DI 模块
 *
 * 注册所有 ViewModel，依赖由 sharedModule 提供
 */
val uiModule: Module = module {
    factory { VideoHomeViewModel(spiderService = get(), httpClient = get()) }
    factory { VideoDetailViewModel(httpClient = get(), spiderService = get(), favoriteDao = get()) }
    factory { PlayerViewModel(playerCoreManager = get(), playbackProgressDao = get(), subtitleService = get()) }
    factory { SearchViewModel(httpClient = get(), spiderService = get()) }
    factory { HistoryViewModel(watchHistoryDao = get()) }
    factory { FavoriteViewModel(favoriteDao = get()) }
    factory { SettingsViewModel(settings = get(), videoCacheService = get(), watchHistoryDao = get(), favoriteDao = get()) }
    factory { SourceManageViewModel(httpClient = get(), spiderService = get()) }
    factory { DownloadViewModel(downloadDao = get(), httpClient = get()) }
}
