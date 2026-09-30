package com.mediamix.shared.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 收藏 DAO
 *
 * 封装 SQLDelight 生成的 Favorites 查询，
 * 提供 suspend 函数和 Flow 接口。
 */
class FavoriteDao(private val database: MediaMixDatabase) {

    private val queries get() = database.favoritesQueries

    /** 查询所有收藏（按收藏时间倒序），返回 Flow */
    fun observeAll(): Flow<List<FavoriteItemEntity>> {
        return queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toEntity() } }
    }

    /** 查询所有收藏（一次性） */
    fun getAll(): List<FavoriteItemEntity> {
        return queries.selectAll().executeAsList().map { it.toEntity() }
    }

    /** 根据 vodId 查询 */
    fun getByVodId(vodId: String): FavoriteItemEntity? {
        return queries.selectById(vodId).executeAsOneOrNull()?.toEntity()
    }

    /** 查询是否已收藏 */
    fun isFavorite(vodId: String): Boolean {
        return queries.isFavorite(vodId).executeAsOne()
    }

    /** 插入或更新收藏 */
    fun insertOrReplace(
        vodId: String,
        vodName: String,
        vodPic: String?,
        sourceKey: String,
        typeName: String?,
        lastEpisodeCount: Long,
        addTime: Long,
    ) {
        queries.insertOrReplace(vodId, vodName, vodPic, sourceKey, typeName, lastEpisodeCount, addTime)
    }

    /** 更新追番集数 */
    fun updateEpisodeCount(vodId: String, episodeCount: Long) {
        queries.updateEpisodeCount(episodeCount, vodId)
    }

    /** 删除收藏 */
    fun deleteByVodId(vodId: String) {
        queries.deleteById(vodId)
    }
}

/**
 * 收藏数据实体
 */
data class FavoriteItemEntity(
    val vodId: String,
    val vodName: String,
    val vodPic: String?,
    val sourceKey: String,
    val typeName: String?,
    val lastEpisodeCount: Long,
    val addTime: Long,
)

private fun Favorites.toEntity(): FavoriteItemEntity {
    return FavoriteItemEntity(
        vodId = vodId,
        vodName = vodName,
        vodPic = vodPic,
        sourceKey = sourceKey,
        typeName = typeName,
        lastEpisodeCount = lastEpisodeCount,
        addTime = addTime,
    )
}
