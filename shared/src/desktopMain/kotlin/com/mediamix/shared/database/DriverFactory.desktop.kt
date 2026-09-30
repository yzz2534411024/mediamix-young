package com.mediamix.shared.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.mediamix.shared.core.PlatformPaths
import java.io.File
import java.util.Properties

/**
 * Desktop actual 实现 createSqlDriver
 *
 * 使用 JdbcSqliteDriver (sqlite-jdbc)，数据库文件存储在 PlatformPaths.dataDir。
 */
actual fun createSqlDriver(): SqlDriver {
    val dbDir = File(PlatformPaths.dataDir)
    if (!dbDir.exists()) dbDir.mkdirs()
    val dbFile = File(dbDir, "mediamix.db")

    // .sq 里的建表语句均为裸 CREATE TABLE（没有 IF NOT EXISTS），
    // 重复执行 Schema.create() 会抛 "table WatchHistories already exists"。
    // 因此只在数据库文件首次创建时建表。
    val isNewDatabase = !dbFile.exists() || dbFile.length() == 0L

    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", Properties())
    if (isNewDatabase) {
        MediaMixDatabase.Schema.create(driver)
    }
    return driver
}
