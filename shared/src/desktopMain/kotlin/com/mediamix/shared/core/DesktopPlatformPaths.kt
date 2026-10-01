package com.mediamix.shared.core

import java.io.File

/**
 * Desktop actual 实现 PlatformPaths
 *
 * 使用 System.getProperty("user.home") 获取平台文件路径。
 */
actual object PlatformPaths {

    private val homeDir = System.getProperty("user.home") ?: "."

    actual val dataDir: String = File(homeDir, ".mediamix/data").absolutePath

    actual val cacheDir: String = File(homeDir, ".mediamix/cache").absolutePath

    actual val downloadDir: String = File(homeDir, ".mediamix/downloads").absolutePath

    actual val tempDir: String = File(System.getProperty("java.io.tmpdir"), "mediamix").absolutePath
}
