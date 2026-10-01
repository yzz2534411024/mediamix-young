package com.mediamix.shared.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 观看历史 DAO
 *
 * 封装 SQLDelight 生成的 WatchHistories 查询，
 * 提供 suspend 函数和 Flow 接口。
 */
class WatchHistoryDao(
    private val database: MediaMixDatabase,
) {
    private val queries get() = database.watchHistoriesQueries

    /** 查询所有观看历史（按时间倒序），返回 Flow */
    fun observeAll(): Flow<List<WatchHistoryItemEntity>> =
        queries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toEntity() } }

    /** 查询所有观看历史（一次性） */
    fun getAll(): List<WatchHistoryItemEntity> = queries.selectAll().executeAsList().map { it.toEntity() }

    /** 根据 vodId 查询 */
    fun getByVodId(vodId: String): WatchHistoryItemEntity? = queries.selectById(vodId).executeAsOneOrNull()?.toEntity()

    /** 插入或更新观看历史 */
    fun insertOrReplace(
        vodId: String,
        vodName: String,
        vodPic: String?,
        sourceKey: String,
        episodeName: String?,
        lastWatchTime: Long,
    ) {
        queries.insertOrReplace(vodId, vodName, vodPic, sourceKey, episodeName, lastWatchTime)
    }

    /** 删除单条记录 */
    fun deleteByVodId(vodId: String) {
        queries.deleteById(vodId)
    }

    /** 清空所有记录 */
    fun clearAll() {
        queries.clearAll()
    }
}

/**
 * 观看历史数据实体
 */
data class WatchHistoryItemEntity(
    val vodId: String,
    val vodName: String,
    val vodPic: String?,
    val sourceKey: String,
    val episodeName: String?,
    val lastWatchTime: Long,
)

private fun WatchHistories.toEntity(): WatchHistoryItemEntity =
    WatchHistoryItemEntity(
        vodId = vodId,
        vodName = vodName,
        vodPic = vodPic,
        sourceKey = sourceKey,
        episodeName = episodeName,
        lastWatchTime = lastWatchTime,
    )
