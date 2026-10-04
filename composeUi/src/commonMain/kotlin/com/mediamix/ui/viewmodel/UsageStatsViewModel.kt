package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediamix.shared.database.FavoriteDao
import com.mediamix.shared.database.PlaybackProgressDao
import com.mediamix.shared.database.WatchHistoryDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 正式版"使用记录"页：基于本地数据库的观影统计。
 *
 * 数据口径：
 * - 观看影片数 = 观看历史里的不同影片（历史表本身按影片去重）
 * - 累计时长 = 各影片进度的 position 之和（看到哪算哪，不含重复观看）
 * - 看完 = position ≥ duration × 0.95 的影片数
 * - 收藏数 = 收藏表条数
 * - 最近活跃 = 历史里最新一条的 lastWatchTime
 */
class UsageStatsViewModel(
    private val watchHistoryDao: WatchHistoryDao,
    private val playbackProgressDao: PlaybackProgressDao,
    private val favoriteDao: FavoriteDao,
) : ViewModel() {

    data class Stats(
        val watchedCount: Int = 0,
        val inProgressCount: Int = 0,
        val finishedCount: Int = 0,
        val totalWatchedMs: Long = 0,
        val favoriteCount: Int = 0,
        val latestActiveMs: Long = 0,
    )

    private val _stats = MutableStateFlow<Stats?>(null)
    val stats: StateFlow<Stats?> = _stats.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val histories = runCatching { watchHistoryDao.getAll() }.getOrDefault(emptyList())
            val progresses = runCatching { playbackProgressDao.getAll() }.getOrDefault(emptyList())
            val favorites = runCatching { favoriteDao.getAll() }.getOrDefault(emptyList())

            val totalWatchedMs = progresses.sumOf { it.position }
            val finished = progresses.count { p -> p.duration > 0 && p.position >= p.duration * 0.95 }
            val inProgress = progresses.count { p -> p.duration > 0 && p.position < p.duration * 0.95 }

            _stats.value =
                Stats(
                    watchedCount = histories.size,
                    inProgressCount = inProgress,
                    finishedCount = finished,
                    totalWatchedMs = totalWatchedMs,
                    favoriteCount = favorites.size,
                    latestActiveMs = histories.maxOfOrNull { it.lastWatchTime } ?: 0L,
                )
        }
    }
}
