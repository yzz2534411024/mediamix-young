package com.mediamix.shared.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 下载任务 DAO
 *
 * 封装 SQLDelight 生成的 DownloadTasks 查询，
 * 提供 suspend 函数和 Flow 接口。
 */
class DownloadDao(private val database: MediaMixDatabase) {

    private val queries get() = database.downloadTasksQueries

    /** 查询所有下载任务（按创建时间倒序），返回 Flow */
    fun observeAll(): Flow<List<DownloadTaskEntity>> {
        return queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toEntity() } }
    }

    /** 查询所有下载任务（一次性） */
    fun getAll(): List<DownloadTaskEntity> {
        return queries.selectAll().executeAsList().map { it.toEntity() }
    }

    /** 根据 ID 查询 */
    fun getById(id: String): DownloadTaskEntity? {
        return queries.selectById(id).executeAsOneOrNull()?.toEntity()
    }

    /** 插入或替换下载任务 */
    fun insertOrReplace(
        id: String,
        vodId: String,
        vodName: String,
        episodeName: String,
        videoUrl: String,
        localPath: String = "",
        status: Long = 0L,
        progress: Long = 0L,
        fileSize: Long = 0L,
        createTime: Long,
    ) {
        queries.insertOrReplace(id, vodId, vodName, episodeName, videoUrl, localPath, status, progress, fileSize, createTime)
    }

    /** 更新下载任务 */
    fun update(
        id: String,
        vodId: String,
        vodName: String,
        episodeName: String,
        videoUrl: String,
        localPath: String,
        status: Long,
        progress: Long,
        fileSize: Long,
    ) {
        queries.update(vodId, vodName, episodeName, videoUrl, localPath, status, progress, fileSize, id)
    }

    /** 更新下载进度 */
    fun updateProgress(id: String, progress: Long, status: Long) {
        val existing = queries.selectById(id).executeAsOneOrNull() ?: return
        queries.update(
            existing.vodId, existing.vodName, existing.episodeName,
            existing.videoUrl, existing.localPath, status, progress,
            existing.fileSize, id
        )
    }

    /** 删除下载任务 */
    fun deleteById(id: String) {
        queries.deleteById(id)
    }

    /** 删除已完成的下载任务 */
    fun deleteCompleted() {
        queries.deleteCompleted()
    }
}

/**
 * 下载任务数据实体
 */
data class DownloadTaskEntity(
    val id: String,
    val vodId: String,
    val vodName: String,
    val episodeName: String,
    val videoUrl: String,
    val localPath: String,
    val status: Long,
    val progress: Long,
    val fileSize: Long,
    val createTime: Long,
)

private fun DownloadTasks.toEntity(): DownloadTaskEntity {
    return DownloadTaskEntity(
        id = id,
        vodId = vodId,
        vodName = vodName,
        episodeName = episodeName,
        videoUrl = videoUrl,
        localPath = localPath,
        status = status,
        progress = progress,
        fileSize = fileSize,
        createTime = createTime,
    )
}
