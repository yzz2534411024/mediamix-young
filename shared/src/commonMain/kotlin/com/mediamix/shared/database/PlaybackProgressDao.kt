package com.mediamix.shared.database

/**
 * 播放进度 DAO
 *
 * 封装 SQLDelight 生成的 PlaybackProgresses 查询。
 */
class PlaybackProgressDao(private val database: MediaMixDatabase) {

    private val queries get() = database.playbackProgressesQueries

    /** 查询所有播放进度 */
    fun getAll(): List<PlaybackProgressEntity> {
        return queries.selectAll().executeAsList().map { it.toEntity() }
    }

    /** 查询指定视频的播放进度 */
    fun getByVideoUrl(videoUrl: String): PlaybackProgressEntity? {
        return queries.selectByVideoUrl(videoUrl).executeAsOneOrNull()?.toEntity()
    }

    /** 保存播放进度（插入或替换） */
    fun insertOrReplace(
        videoUrl: String,
        position: Long,
        duration: Long,
        lastPlayTime: Long,
    ) {
        queries.insertOrReplace(videoUrl, position, duration, lastPlayTime)
    }

    /** 删除指定视频的播放进度 */
    fun deleteByVideoUrl(videoUrl: String) {
        queries.deleteByVideoUrl(videoUrl)
    }
}

/**
 * 播放进度数据实体
 */
data class PlaybackProgressEntity(
    val videoUrl: String,
    val position: Long,
    val duration: Long,
    val lastPlayTime: Long,
)

private fun PlaybackProgresses.toEntity(): PlaybackProgressEntity {
    return PlaybackProgressEntity(
        videoUrl = videoUrl,
        position = position,
        duration = duration,
        lastPlayTime = lastPlayTime,
    )
}
