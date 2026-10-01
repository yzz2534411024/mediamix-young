package com.mediamix.shared.services

import co.touchlab.kermit.Logger
import com.mediamix.shared.cache.VideoCacheService
import com.mediamix.shared.network.HttpClientFactory
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import kotlinx.datetime.Clock

// =====================================================================
// Enums & Data Classes
// =====================================================================

/**
 * Network condition for preload strategy selection.
 */
enum class NetworkCondition {
    WIFI, LTE, THREE_G, POOR, OFFLINE
}

/**
 * Preload priority levels. Lower value = higher priority.
 */
enum class PreloadPriority(val value: Int) {
    CURRENT_PLAYBACK(0),
    NEXT_EPISODE(1),
    ADJACENT_ITEM(2),
    PLAYLIST_ITEM(3),
    HISTORY_REPLAY(4);
}

/**
 * Preload task status.
 */
enum class PreloadTaskStatus {
    WAITING, DOWNLOADING, COMPLETED, FAILED, CANCELLED
}

/**
 * Snapshot of preload task counts by status.
 */
data class PreloadStatusInfo(
    val pendingCount: Int,
    val downloadingCount: Int,
    val completedCount: Int,
    val cancelledCount: Int,
    val failedCount: Int,
    val currentDepth: Int,
) {
    val totalCount: Int get() = pendingCount + downloadingCount + completedCount + cancelledCount + failedCount
    val hasActiveTasks: Boolean get() = downloadingCount > 0
}

/**
 * A single preload task request.
 */
data class PreloadTask(
    val videoId: String,
    val videoUrl: String,
    val quality: String,
    val priority: PreloadPriority,
    val category: String? = null,
)

/**
 * Internal mutable task with runtime state.
 */
internal class PreloadTaskEntry(
    val id: String,
    val videoId: String,
    val url: String,
    val priority: PreloadPriority,
    val preloadBytes: Long,
    var loadedBytes: Long = 0L,
    var status: PreloadTaskStatus = PreloadTaskStatus.WAITING,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    var job: Job? = null,
)

// =====================================================================
// PreloadDepthCalculator
// =====================================================================

/**
 * Calculates dynamic preload depth based on network condition,
 * user dwell time, bounce rate, and available disk space.
 *
 * Result is clamped to [minDepth, maxDepth] = [1, 3].
 * Returns 0 for OFFLINE.
 */
object PreloadDepthCalculator {
    const val minDepth = 1
    const val maxDepth = 3
    const val defaultDepth = 2

    // Thresholds
    private const val longDwellThresholdSec = 600.0   // 10 min
    private const val shortDwellThresholdSec = 120.0  // 2 min
    private const val highBounceRateThreshold = 0.6
    private const val lowDiskThresholdMB = 500L        // 500 MB

    fun calculateDepth(
        networkCondition: NetworkCondition,
        avgDwellTimeSec: Double? = null,
        bounceRate: Double? = null,
        availableDiskMB: Long? = null,
    ): Int {
        // Offline / poor → edge cases
        if (networkCondition == NetworkCondition.OFFLINE) return 0
        if (networkCondition == NetworkCondition.POOR) return minDepth

        // Base depth by network
        val baseDepth = when (networkCondition) {
            NetworkCondition.WIFI -> maxDepth   // 3
            NetworkCondition.LTE -> 2
            NetworkCondition.THREE_G -> minDepth // 1
            NetworkCondition.POOR -> minDepth
            NetworkCondition.OFFLINE -> 0
        }

        // Dwell time adjustment (-1 ~ +1)
        val dwellAdj = when {
            avgDwellTimeSec != null && avgDwellTimeSec >= longDwellThresholdSec -> 1
            avgDwellTimeSec != null && avgDwellTimeSec < shortDwellThresholdSec -> -1
            else -> 0
        }

        // Bounce rate adjustment (-1 ~ 0)
        val bounceAdj = when {
            bounceRate != null && bounceRate >= highBounceRateThreshold -> -1
            else -> 0
        }

        // Disk space adjustment (-1 ~ 0)
        val diskAdj = when {
            availableDiskMB != null && availableDiskMB < lowDiskThresholdMB -> -1
            else -> 0
        }

        return (baseDepth + dwellAdj + bounceAdj + diskAdj).coerceIn(minDepth, maxDepth)
    }
}

// =====================================================================
// PreloadStrategy
// =====================================================================

/**
 * Preload strategy configuration per network condition.
 */
object PreloadStrategy {

    data class StrategyConfig(
        val cacheFullVideo: Boolean,
        val preloadCount: Int,
        val firstSegmentBytes: Long,
        val bandwidthRatio: Double,
    )

    val WIFI = StrategyConfig(
        cacheFullVideo = true,
        preloadCount = 3,
        firstSegmentBytes = 0L,
        bandwidthRatio = 0.30,
    )

    val MOBILE = StrategyConfig(
        cacheFullVideo = false,
        preloadCount = 1,
        firstSegmentBytes = 512L * 1024,  // 512 KB
        bandwidthRatio = 0.20,
    )

    val POOR = StrategyConfig(
        cacheFullVideo = false,
        preloadCount = 0,
        firstSegmentBytes = 128L * 1024,  // 128 KB
        bandwidthRatio = 0.10,
    )

    val OFFLINE = StrategyConfig(
        cacheFullVideo = false,
        preloadCount = 0,
        firstSegmentBytes = 0L,
        bandwidthRatio = 0.0,
    )

    fun getStrategy(condition: NetworkCondition): StrategyConfig = when (condition) {
        NetworkCondition.WIFI -> WIFI
        NetworkCondition.LTE -> MOBILE
        NetworkCondition.THREE_G -> MOBILE
        NetworkCondition.POOR -> POOR
        NetworkCondition.OFFLINE -> OFFLINE
    }
}

// =====================================================================
// PreloadService
// =====================================================================

/**
 * Smart video preload service.
 *
 * Dynamically calculates preload depth based on user behaviour and network
 * condition, manages a priority-sorted task queue with bounded concurrency,
 * and cooperates with the playback buffer (pauses downloads when the player
 * is rebuffering).
 *
 * Interacts with [VideoCacheService] for cache reads (`hasCache`) and writes
 * (`putVideo` / `putSegment`).
 */
class PreloadService(
    private val cacheService: VideoCacheService,
    private val maxConcurrentDownloads: Int = 3,
) {
    private val logger = Logger.withTag("PreloadService")

    // ---- HTTP ----
    private val httpClient: HttpClient = HttpClientFactory.createStreamingClient()

    // ---- Concurrency ----
    private val semaphore = Semaphore(maxConcurrentDownloads)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ---- Task queue (priority-sorted) ----
    private val tasks = mutableListOf<PreloadTaskEntry>()
    private var taskIdCounter = 0

    // ---- Task update flow (replaces Dart StreamController) ----
    private val _tasksFlow = MutableSharedFlow<List<PreloadTaskEntry>>(replay = 1)
    internal val tasksFlow: SharedFlow<List<PreloadTaskEntry>> = _tasksFlow.asSharedFlow()

    // ---- Network / strategy state ----
    var networkCondition: NetworkCondition = NetworkCondition.WIFI
        private set
    var strategy: PreloadStrategy.StrategyConfig = PreloadStrategy.WIFI
        private set

    // ---- Depth calculator state ----
    private val depthCalculator = PreloadDepthCalculator
    private var currentPreloadDepth: Int = PreloadDepthCalculator.defaultDepth
    private var avgDwellTimeSec: Double? = null
    private var bounceRate: Double? = null
    private var availableDiskMB: Long? = null

    // ---- Playback buffering guard ----
    private var isPlaybackBuffering = false

    // ---- Lifecycle ----
    private var disposed = false

    // ==================================================================
    // Public API – Queries
    // ==================================================================

    /** Current active (downloading) tasks snapshot. */
    internal val activeTasks: List<PreloadTaskEntry>
        get() = tasks.filter { it.status == PreloadTaskStatus.DOWNLOADING }

    /** Current dynamic preload depth. */
    fun getCurrentDepth(): Int = currentPreloadDepth

    /** Build a status snapshot. */
    fun getStatusInfo(): PreloadStatusInfo {
        var pending = 0; var downloading = 0; var completed = 0
        var cancelled = 0; var failed = 0
        for (t in tasks) {
            when (t.status) {
                PreloadTaskStatus.WAITING -> pending++
                PreloadTaskStatus.DOWNLOADING -> downloading++
                PreloadTaskStatus.COMPLETED -> completed++
                PreloadTaskStatus.CANCELLED -> cancelled++
                PreloadTaskStatus.FAILED -> failed++
            }
        }
        return PreloadStatusInfo(
            pendingCount = pending,
            downloadingCount = downloading,
            completedCount = completed,
            cancelledCount = cancelled,
            failedCount = failed,
            currentDepth = currentPreloadDepth,
        )
    }

    // ==================================================================
    // Public API – Mutation
    // ==================================================================

    /**
     * Add a single preload task.
     *
     * If a task for the same [videoId] already exists with equal or higher
     * priority, the request is skipped.  If the new task has strictly higher
     * priority, the old one is cancelled and replaced.
     */
    suspend fun preloadVideo(
        videoId: String,
        url: String,
        priority: PreloadPriority = PreloadPriority.NEXT_EPISODE,
        quality: String = "720p",
        targetBytes: Long? = null,
        category: String? = null,
    ) {
        if (disposed) return
        if (networkCondition == NetworkCondition.OFFLINE) {
            logger.d("Offline, skipping preload: $videoId")
            return
        }

        // Dedup
        val existing = tasks.find { it.videoId == videoId }
        if (existing != null) {
            if (existing.priority.value <= priority.value) {
                logger.d("Preload task already exists with equal/higher priority: $videoId")
                return
            }
            cancelPreload(videoId)
        }

        // Already cached?
        if (cacheService.hasCache(videoId, quality)) {
            logger.d("Video already cached, skipping preload: $videoId")
            return
        }

        val bytes = targetBytes ?: if (strategy.cacheFullVideo) 0L else strategy.firstSegmentBytes

        val entry = PreloadTaskEntry(
            id = "preload_${taskIdCounter++}",
            videoId = videoId,
            url = url,
            priority = priority,
            preloadBytes = bytes,
        )

        synchronized(tasks) {
            tasks.add(entry)
            sortTasks()
        }
        notifyTasksUpdate()
        logger.d("Added preload task: $videoId, priority=${priority.name}, bytes=$bytes")

        processQueue()
    }

    /** Batch preload. */
    suspend fun preloadVideos(items: List<PreloadTask>) {
        for (item in items) {
            preloadVideo(
                videoId = item.videoId,
                url = item.videoUrl,
                priority = item.priority,
                quality = item.quality,
                category = item.category,
            )
        }
    }

    /**
     * 轻量预热：只建立连接（DNS / TCP / TLS / 收到响应头），**不下载 body**。
     *
     * 用在「打开详情页 → 用户点播放」这段空窗期：等到真正播放时，握手开销已经付过，
     * 首帧能明显更快。与 [preloadVideo] 的区别是它不会把整集拉下来浪费流量。
     */
    suspend fun warmUp(url: String) {
        if (url.isBlank()) return
        try {
            httpClient.prepareGet(url).execute { /* 只握手，不读 body */ }
        } catch (e: Exception) {
            // 预热失败不影响任何功能，静默处理
            logger.d("Warm up skipped: ${e.message}")
        }
    }

    /** Convenience: preload next episode. */
    suspend fun preloadNextEpisode(videoId: String, url: String, quality: String = "720p") =
        preloadVideo(videoId, url, PreloadPriority.NEXT_EPISODE, quality)

    /** Convenience: preload adjacent item. */
    suspend fun preloadAdjacent(videoId: String, url: String, quality: String = "720p") =
        preloadVideo(videoId, url, PreloadPriority.ADJACENT_ITEM, quality)

    // ---- Cancel ----

    /** Cancel preload for a specific video. */
    fun cancelPreload(videoId: String) {
        val entry = tasks.find { it.videoId == videoId } ?: return
        entry.job?.cancel()
        entry.status = PreloadTaskStatus.CANCELLED
        synchronized(tasks) { tasks.remove(entry) }
        notifyTasksUpdate()
        logger.d("Cancelled preload: $videoId")
    }

    /** Cancel all preload tasks. */
    fun cancelAll() {
        synchronized(tasks) {
            for (t in tasks) {
                t.job?.cancel()
                t.status = PreloadTaskStatus.CANCELLED
            }
            tasks.clear()
        }
        notifyTasksUpdate()
        logger.d("Cancelled all preload tasks")
    }

    /**
     * Trim waiting tasks to the current preload depth.
     * Low-priority WAITING tasks beyond [depth] are cancelled.
     */
    fun trimTasksToDepth(depth: Int = currentPreloadDepth) {
        val waitingSorted = tasks
            .filter { it.status == PreloadTaskStatus.WAITING }
            .sortedBy { it.priority.value }

        for (i in depth until waitingSorted.size) {
            val t = waitingSorted[i]
            t.job?.cancel()
            t.status = PreloadTaskStatus.CANCELLED
            synchronized(tasks) { tasks.remove(t) }
        }
        if (waitingSorted.size > depth) {
            notifyTasksUpdate()
        }
    }

    // ---- Pause / Resume (cooperate with player buffering) ----

    /** Pause downloading tasks to release bandwidth for playback. */
    fun pauseDownloadingTasks() {
        val downloading = tasks.filter { it.status == PreloadTaskStatus.DOWNLOADING }
        for (t in downloading) {
            t.job?.cancel()
            t.status = PreloadTaskStatus.CANCELLED
            logger.d("Paused preload (player buffering): ${t.videoId}")
        }
        synchronized(tasks) {
            tasks.removeAll { it.status == PreloadTaskStatus.CANCELLED }
        }
        notifyTasksUpdate()
    }

    /** Resume preloading after player buffering subsides. */
    fun resumeDownloading() {
        processQueue()
    }

    // ---- Network / behaviour updates ----

    /**
     * Update network condition; automatically adjusts strategy and depth.
     * Cancels all tasks when going OFFLINE.
     */
    fun updateNetworkCondition(condition: NetworkCondition) {
        if (networkCondition == condition) return
        networkCondition = condition
        strategy = PreloadStrategy.getStrategy(condition)
        logger.i("Network changed: ${condition.name}, strategy: " +
            "fullVideo=${strategy.cacheFullVideo}, count=${strategy.preloadCount}, " +
            "segmentBytes=${strategy.firstSegmentBytes}, bw=${(strategy.bandwidthRatio * 100).toInt()}%")

        // Recalculate depth
        updateUserBehavior()

        if (condition == NetworkCondition.OFFLINE) {
            cancelAll()
            return
        }

        if (condition == NetworkCondition.POOR) {
            // Cancel low-priority waiting tasks
            val toRemove = tasks.filter {
                it.priority.value > PreloadPriority.NEXT_EPISODE.value &&
                    it.status != PreloadTaskStatus.DOWNLOADING
            }
            for (t in toRemove) {
                t.job?.cancel()
                t.status = PreloadTaskStatus.CANCELLED
            }
            synchronized(tasks) { tasks.removeAll(toRemove.toSet()) }
            notifyTasksUpdate()
        }

        processQueue()
    }

    /**
     * Update user behaviour data and recalculate preload depth.
     * If depth decreases, excess low-priority tasks are trimmed.
     */
    fun updateUserBehavior(
        avgDwellTimeSec: Double? = null,
        bounceRate: Double? = null,
        availableDiskMB: Long? = null,
    ) {
        if (avgDwellTimeSec != null) this.avgDwellTimeSec = avgDwellTimeSec
        if (bounceRate != null) this.bounceRate = bounceRate
        if (availableDiskMB != null) this.availableDiskMB = availableDiskMB

        val newDepth = depthCalculator.calculateDepth(
            networkCondition = networkCondition,
            avgDwellTimeSec = this.avgDwellTimeSec,
            bounceRate = this.bounceRate,
            availableDiskMB = this.availableDiskMB,
        )

        if (newDepth != currentPreloadDepth) {
            logger.i("Preload depth adjusted: $currentPreloadDepth → $newDepth " +
                "(dwell=${this.avgDwellTimeSec?.let { "%.0f".format(it) } ?: "?"}s, " +
                "bounce=${this.bounceRate?.let { "%.2f".format(it) } ?: "?"}, " +
                "disk=${this.availableDiskMB?.let { "${it}MB" } ?: "?"}, " +
                "net=${networkCondition.name})")
            currentPreloadDepth = newDepth
            trimTasksToDepth()
        }
    }

    /**
     * Notify playback buffering state change.
     * When buffering, pauses downloads to free bandwidth.
     */
    fun notifyPlaybackBuffering(isBuffering: Boolean) {
        if (isPlaybackBuffering == isBuffering) return
        isPlaybackBuffering = isBuffering
        if (isBuffering) {
            logger.d("Player buffering detected, pausing preloads")
            pauseDownloadingTasks()
        } else {
            logger.d("Player stable, resuming preloads")
            processQueue()
        }
    }

    // ==================================================================
    // Lifecycle
    // ==================================================================

    fun dispose() {
        disposed = true
        cancelAll()
        httpClient.close()
        scope.cancel()
        logger.i("PreloadService disposed")
    }

    // ==================================================================
    // Internal
    // ==================================================================

    private fun sortTasks() {
        tasks.sortBy { it.priority.value }
    }

    private fun notifyTasksUpdate() {
        _tasksFlow.tryEmit(tasks.toList())
    }

    private fun processQueue() {
        if (disposed) return
        if (isPlaybackBuffering) return
        if (networkCondition == NetworkCondition.OFFLINE) return

        val waiting = tasks.filter { it.status == PreloadTaskStatus.WAITING }
        for (task in waiting) {
            executeTask(task)
        }
    }

    private fun executeTask(entry: PreloadTaskEntry) {
        scope.launch {
            semaphore.withPermit {
                if (disposed || entry.status == PreloadTaskStatus.CANCELLED) return@launch

                entry.status = PreloadTaskStatus.DOWNLOADING
                entry.job = currentCoroutineContext()[Job]
                notifyTasksUpdate()

                try {
                    logger.d("Start preload: ${entry.videoId}, bytes=${entry.preloadBytes}")
                    downloadTask(entry)
                    entry.status = PreloadTaskStatus.COMPLETED
                    logger.i("Preload completed: ${entry.videoId}, loaded=${entry.loadedBytes}B")
                } catch (ce: CancellationException) {
                    // Normal cancellation (target bytes reached or manual cancel)
                    if (entry.status != PreloadTaskStatus.CANCELLED) {
                        entry.status = PreloadTaskStatus.COMPLETED
                        logger.d("Preload terminated normally: ${entry.videoId}")
                    }
                    throw ce // re-throw to cooperate with coroutine cancellation
                } catch (e: Exception) {
                    entry.status = PreloadTaskStatus.FAILED
                    logger.e("Preload failed: ${entry.videoId}", e)
                } finally {
                    notifyTasksUpdate()
                    // Remove finished / cancelled tasks
                    synchronized(tasks) {
                        tasks.removeAll {
                            it.status == PreloadTaskStatus.COMPLETED ||
                                it.status == PreloadTaskStatus.CANCELLED ||
                                it.status == PreloadTaskStatus.FAILED
                        }
                    }
                    processQueue()
                }
            }
        }.also { entry.job = it }
    }

    /**
     * Download video data via Ktor and write into [VideoCacheService].
     *
     * - Full video (preloadBytes == 0 && cacheFullVideo): download all, `putVideo`.
     * - Partial (Range request): download up to preloadBytes, `putSegment`.
     */
    private suspend fun downloadTask(entry: PreloadTaskEntry) {
        val response: HttpResponse = httpClient.get(entry.url) {
            header(HttpHeaders.UserAgent, "okhttp/3.12.11")
            // Range header for partial downloads
            if (entry.preloadBytes > 0 && !strategy.cacheFullVideo) {
                header(HttpHeaders.Range, "bytes=0-${entry.preloadBytes - 1}")
            }
        }

        val bytes = response.readRawBytes()
        if (bytes.isEmpty()) return

        entry.loadedBytes = bytes.size.toLong()

        val isFullVideo = entry.preloadBytes == 0L
        if (isFullVideo) {
            // Write to temp file then putVideo
            val tempDir = File(System.getProperty("java.io.tmpdir"), "yl_preload")
            tempDir.mkdirs()
            val ext = when {
                entry.url.contains(".ts") -> ".ts"
                entry.url.contains(".m3u8") -> ".m3u8"
                else -> ".mp4"
            }
            val tempFile = File(tempDir, "video_${entry.id}$ext")
            try {
                tempFile.writeBytes(bytes)
                cacheService.putVideo(
                    videoId = entry.videoId,
                    filePath = tempFile.absolutePath,
                    quality = "720p",
                )
                logger.i("Full video cached (L3): ${entry.videoId}")
            } finally {
                try { tempFile.delete(); tempDir.delete() } catch (_: Exception) {}
            }
        } else {
            cacheService.putSegment(
                videoId = entry.videoId,
                segmentKey = "preload_${entry.id}",
                data = bytes,
                quality = "720p",
            )
        }
    }
}
