package com.mediamix.shared.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android actual 实现 createSqlDriver
 *
 * 使用 AndroidSqliteDriver，通过 DbContextHolder 获取 Application Context。
 * 必须在 Application.onCreate() 中调用 DbHolder.init(context)。
 */
actual fun createSqlDriver(): SqlDriver {
    val context =
        DbHolder.context
            ?: throw IllegalStateException("DbContextHolder.init(context) must be called in Application.onCreate()")
    return AndroidSqliteDriver(
        schema = MediaMixDatabase.Schema,
        context = context,
        name = "mediamix.db",
    )
}

/**
 * Android 全局 Context 持有者
 * 应在 Application.onCreate() 中初始化
 */
object DbHolder {
    private var _context: Context? = null

    val context: Context? get() = _context

    fun init(context: Context) {
        _context = context.applicationContext
    }
}
