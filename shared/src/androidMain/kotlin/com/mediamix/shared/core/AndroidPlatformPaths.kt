package com.mediamix.shared.core

import java.io.File

/**
 * Android actual 实现 PlatformPaths
 *
 * 通过 DI 注入 Context 来获取平台文件路径。
 * 需要在应用启动时调用 init(context) 完成初始化。
 */
actual object PlatformPaths {

    private var appDataDir: String = ""
    private var appCacheDir: String = ""
    private var appDownloadDir: String = ""
    private var appTempDir: String = ""

    /**
     * 通过 Android Context 初始化路径。
     * 应在 Application.onCreate() 或 DI 模块中调用。
     */
    fun init(context: android.content.Context) {
        appDataDir = context.filesDir.absolutePath
        appCacheDir = context.cacheDir.absolutePath
        // 使用「应用专属外部目录」。Android 10+ 的 scoped storage 下，
        // 公共 Download 目录（getExternalStoragePublicDirectory）需要额外权限且写入会被拒，
        // 因此改用 getExternalFilesDir —— 免运行时权限，卸载时自动清理。
        val externalDownloads =
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        appDownloadDir =
            (externalDownloads ?: File(context.filesDir, "downloads")).absolutePath
        appTempDir = context.cacheDir.resolve("temp").absolutePath
    }

    actual val dataDir: String get() = appDataDir
    actual val cacheDir: String get() = appCacheDir
    actual val downloadDir: String get() = appDownloadDir
    actual val tempDir: String get() = appTempDir
}