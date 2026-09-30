package com.mediamix.shared.core

/**
 * 平台文件存储路径 �?expect 声明
 *
 * Android actual: 通过 Context 获取外部存储路径
 * Desktop actual: 使用 System.getProperty("user.home")
 */
expect object PlatformPaths {
    val dataDir: String
    val cacheDir: String
    val downloadDir: String
    val tempDir: String
}
