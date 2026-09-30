package com.mediamix.shared.database

import app.cash.sqldelight.db.SqlDriver

/**
 * SQLDelight SqlDriver 创建函数 — expect 声明
 *
 * Android actual: AndroidSqliteDriver (基于 Android SQLite，通过全局 Context)
 * Desktop actual: JdbcSqliteDriver (基于 sqlite-jdbc)
 */
expect fun createSqlDriver(): SqlDriver

/**
 * 创建数据库实例的便捷函数
 */
fun createDatabase(): MediaMixDatabase {
    val driver = createSqlDriver()
    return MediaMixDatabase(driver)
}
