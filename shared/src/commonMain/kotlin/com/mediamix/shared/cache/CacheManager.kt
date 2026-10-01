package com.mediamix.shared.cache

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.CacheStats
import com.mediamix.shared.models.MemoryUsageInfo

/**
 * 缓存种类 —— 门面只暴露「清哪一类」，不暴露任何实现细节。
 */
enum class CacheKind(
    val displayName: String,
) {
    /** 视频数据缓存（磁盘 + 内存） */
    VIDEO("视频缓存"),

    /** 接口元数据内存缓存（列表 / 详情 / DNS） */
    METADATA("接口元数据"),

    /** 全部 */
    ALL("全部缓存"),
}

/** 缓存的统一快照，供设置页与诊断页展示。 */
data class CacheSnapshot(
    val memory: MemoryUsageInfo,
    val stats: CacheStats,
) {
    val totalBytes: Long get() = memory.l1Bytes + memory.l2Bytes + stats.totalSize
}

/**
 * 缓存门面。
 *
 * 背景：项目里同时存在**三套互不相通**的缓存 ——
 * ExoPlayer `SimpleCache`（边播边缓存）、`VideoApiService` 的内存缓存、
 * 以及 `VideoCacheService + MemoryCache + DiskCache + CacheStrategyManager` 四级体系。
 * 上层（设置页 / 诊断页）此前各自直接引用其中一两套，清理入口不一致，
 * 「清了缓存但占用没降」这类问题因此很难解释。
 *
 * 门面只做两件事：
 * 1. 给出**唯一的**占用读数（[snapshot]）；
 * 2. 给出**唯一的**清理入口（[clear]），按 [CacheKind] 分类。
 *
 * ⚠️ ExoPlayer 的 `SimpleCache` 由 Media3 托管，不在本门面内 ——
 * 它的清理在 Android 侧随 `SimpleCache` 实例的 `keys` 释放完成（见 Android 播放缓存实现）。
 */
class CacheManager(
    private val videoCacheService: VideoCacheService,
) {
    private val logger = Logger.withTag("CacheManager")

    fun snapshot(): CacheSnapshot =
        CacheSnapshot(
            memory = videoCacheService.getMemoryUsage(),
            stats = videoCacheService.getStats(),
        )

    fun totalBytes(): Long = snapshot().totalBytes

    /**
     * 清理缓存。
     *
     * [CacheKind.METADATA] 只清内存（接口元数据重建成本极低，清完立刻可用）；
     * [CacheKind.VIDEO] 清磁盘 + 内存视频缓存。
     */
    suspend fun clear(kind: CacheKind = CacheKind.ALL) {
        try {
            when (kind) {
                CacheKind.METADATA -> videoCacheService.clearAllMemoryCaches()
                CacheKind.VIDEO -> videoCacheService.clearAll()
                CacheKind.ALL -> {
                    videoCacheService.clearAll()
                    videoCacheService.clearAllMemoryCaches()
                }
            }
            logger.i("Cache cleared: ${kind.displayName}")
        } catch (e: Exception) {
            logger.e(e) { "Clear cache failed: ${kind.displayName}" }
        }
    }
}
