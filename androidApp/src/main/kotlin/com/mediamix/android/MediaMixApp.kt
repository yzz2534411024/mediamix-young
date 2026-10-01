package com.mediamix.android

import android.app.Application
import com.mediamix.shared.core.PlatformPaths
import com.mediamix.shared.database.DbHolder
import com.mediamix.shared.di.sharedModule
import com.mediamix.shared.player.PlayerEngine
import com.mediamix.ui.di.uiModule
import org.koin.core.context.startKoin

class MediaMixApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 初始化平台依赖
        // PlatformPaths 必须在使用数据库/缓存/下载任何路径之前初始化，
        // 否则 dataDir / cacheDir / downloadDir 会返回空字符串。
        PlatformPaths.init(this)
        DbHolder.init(this)
        PlayerEngine.init(this)

        startKoin {
            modules(sharedModule, uiModule)
        }
    }
}
