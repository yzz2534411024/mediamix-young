package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.core.PlatformPaths
import com.mediamix.shared.database.DownloadDao
import com.mediamix.shared.database.DownloadTaskEntity
import com.mediamix.shared.services.DownloadService
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 下载任务 UI 模型
 */
data class DownloadTaskItem(
    val id: String,
    val vodId: String,
    val vodName: String,
    val episodeName: String,
    val videoUrl: String,
    val localPath: String,
    val progress: Int,
    val status: DownloadTaskStatus,
    val fileSize: Long = 0L,
    val downloadSpeed: String = "",
)

/**
 * 下载任务状态
 */
enum class DownloadTaskStatus {
    WAITING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    ;

    companion object {
        fun fromDb(status: Long): DownloadTaskStatus =
            when (status) {
                0L -> WAITING
                1L -> DOWNLOADING
                2L -> COMPLETED
                3L -> FAILED
                4L -> PAUSED
                else -> WAITING
            }
    }

    fun toDb(): Long =
        when (this) {
            DownloadTaskStatus.WAITING -> 0L
            DownloadTaskStatus.DOWNLOADING -> 1L
            DownloadTaskStatus.PAUSED -> 4L
            DownloadTaskStatus.COMPLETED -> 2L
            DownloadTaskStatus.FAILED -> 3L
        }
}

/**
 * 下载管理 ViewModel。
 *
 * 只负责**任务编排**：状态落库、并发闸门、UI 状态映射。
 * 实际的字节搬运在 [DownloadService]（shared，含 HLS 分片合并、请求头、续传）。
 *
 * 修掉的问题：
 * - m3u8 不再被裸 GET 存成无效 `.mp4`；
 * - 防盗链请求头随任务持久化，下载与播放用的是同一份头；
 * - 暂停/失败后再点「继续」从断点续传，而不是从 0 开始；
 * - 并发上限（[MAX_CONCURRENT_DOWNLOADS] 路），不再把带宽打满拖垮播放。
 */
class DownloadViewModel(
    private val downloadDao: DownloadDao,
    private val downloadService: DownloadService,
    private val settings: Settings,
) : ViewModel() {
    private val logger = Logger.withTag("DownloadViewModel")

    private val _tasks = MutableStateFlow<List<DownloadTaskItem>>(emptyList())
    val tasks: StateFlow<List<DownloadTaskItem>> = _tasks.asStateFlow()

    private val activeDownloads = mutableMapOf<String, Job>()
    private val downloadSpeedMap = mutableMapOf<String, String>()

    /** 并发闸门：同时最多下载 [MAX_CONCURRENT_DOWNLOADS] 个任务。 */
    private val slots = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    init {
        observeTasks()
    }

    private fun observeTasks() {
        downloadDao
            .observeAll()
            .flowOn(Dispatchers.Default)
            .onEach { entities ->
                _tasks.value =
                    entities.map { entity ->
                        entity.toUiModel().copy(
                            downloadSpeed = downloadSpeedMap[entity.id] ?: "",
                        )
                    }
            }.catch { e ->
                logger.e { "Observe download tasks failed: ${e.message}" }
            }.launchIn(viewModelScope)
    }

    /**
     * 添加下载任务。
     *
     * [headers] 是播放时解析出的真实请求头（TVBox 蜘蛛的 `header`），
     * 带防盗链的 CDN 缺了它必定 403 —— 这是「下载下来的文件打不开」的主因之一。
     */
    fun addDownload(
        vodId: String,
        vodName: String,
        episodeName: String,
        videoUrl: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        if (videoUrl.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val id = "${vodId}_${episodeName}_${Clock.System.now().toEpochMilliseconds()}"
                downloadDao.insertOrReplace(
                    id = id,
                    vodId = vodId,
                    vodName = vodName,
                    episodeName = episodeName,
                    videoUrl = videoUrl,
                    status = 0L, // WAITING
                    createTime = Clock.System.now().toEpochMilliseconds(),
                )
                if (headers.isNotEmpty()) saveHeaders(id, headers)
                startDownload(id, videoUrl)
            } catch (e: Exception) {
                logger.e { "Add download failed: ${e.message}" }
            }
        }
    }

    /**
     * 开始（或继续）下载。
     *
     * [resume] 为 true 时保留已下载部分 —— 分片目录与产物文件都会被复用。
     */
    private fun startDownload(
        taskId: String,
        videoUrl: String,
        resume: Boolean = false,
    ) {
        if (activeDownloads.containsKey(taskId)) return

        val job =
            viewModelScope.launch(Dispatchers.IO) {
                // 并发闸门：排队等待空闲槽位，避免 N 个任务一起抢带宽
                slots.withPermit {
                    try {
                        updateTaskStatus(taskId, DownloadTaskStatus.DOWNLOADING)
                        val targetPath = targetPathFor(taskId)
                        val headers = loadHeaders(taskId)

                        var lastSpeedMs = 0L
                        var lastSpeedBytes = 0L
                        val startedAt = Clock.System.now().toEpochMilliseconds()

                        val totalBytes =
                            downloadService.download(
                                url = videoUrl,
                                targetPath = targetPath,
                                headers = headers,
                                resume = resume,
                                workspaceDir = workspaceDirFor(taskId),
                                onProgress = { downloaded, total ->
                                    val now = Clock.System.now().toEpochMilliseconds()
                                    if (now - lastSpeedMs >= SPEED_UPDATE_INTERVAL_MS) {
                                        val elapsed = (now - lastSpeedMs).coerceAtLeast(1L)
                                        val delta = downloaded - lastSpeedBytes
                                        downloadSpeedMap[taskId] = formatSpeed(delta * 1000.0 / elapsed)
                                        lastSpeedMs = now
                                        lastSpeedBytes = downloaded
                                    }
                                    val progress =
                                        if (total > 0L) {
                                            ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                        } else {
                                            0
                                        }
                                    runCatching { downloadDao.updateProgress(taskId, progress.toLong(), 1L) }
                                },
                                isCancelled = { !viewModelScope.isActive },
                            )

                        logger.d {
                            "Download completed: $taskId ($totalBytes bytes, " +
                                "${Clock.System.now().toEpochMilliseconds() - startedAt}ms)"
                        }
                        finishTask(taskId, targetPath)
                        downloadSpeedMap.remove(taskId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // 暂停/取消：状态已由调用方置为 PAUSED，这里保留分片以便续传
                        logger.i { "Download cancelled: $taskId" }
                        throw e
                    } catch (e: Exception) {
                        logger.e { "Download failed: $taskId, error: ${e.message}" }
                        updateTaskStatusOnly(taskId, DownloadTaskStatus.FAILED)
                        downloadSpeedMap.remove(taskId)
                    } finally {
                        activeDownloads.remove(taskId)
                    }
                }
            }
        activeDownloads[taskId] = job
    }

    private suspend fun finishTask(
        taskId: String,
        targetPath: String,
    ) {
        val existing = downloadDao.getById(taskId) ?: return
        val size = fileSizeOf(targetPath)
        downloadDao.update(
            id = taskId,
            vodId = existing.vodId,
            vodName = existing.vodName,
            episodeName = existing.episodeName,
            videoUrl = existing.videoUrl,
            localPath = targetPath,
            status = 2L, // COMPLETED
            progress = 100L,
            fileSize = size,
        )
        settings.remove(headersKey(taskId))
    }

    /**
     * 暂停下载（取消 Job，标记 PAUSED 并保留进度与已下分片）
     */
    fun pauseDownload(taskId: String) {
        activeDownloads[taskId]?.cancel()
        activeDownloads.remove(taskId)
        downloadSpeedMap.remove(taskId)
        updateTaskStatusOnly(taskId, DownloadTaskStatus.PAUSED)
    }

    /**
     * 恢复下载 —— **从断点续传**，不重置进度。
     *
     * 旧实现明确写着「重置进度为 0 重新开始」，一集中断就得从头再来。
     */
    fun resumeDownload(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status == DownloadTaskStatus.DOWNLOADING) return
        viewModelScope.launch(Dispatchers.Default) {
            updateTaskStatusOnly(taskId, DownloadTaskStatus.WAITING)
            startDownload(taskId, task.videoUrl, resume = true)
        }
    }

    /**
     * 删除下载任务（含产物文件与分片临时目录）
     */
    fun deleteTask(taskId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                activeDownloads[taskId]?.cancel()
                activeDownloads.remove(taskId)

                val task = downloadDao.getById(taskId)
                if (task != null && task.localPath.isNotEmpty()) {
                    deleteFile(task.localPath)
                }
                deleteDirectory(workspaceDirFor(taskId))
                settings.remove(headersKey(taskId))
                downloadDao.deleteById(taskId)
            } catch (e: Exception) {
                logger.e { "Delete task failed: ${e.message}" }
            }
        }
    }

    /**
     * 清除已完成的下载
     */
    fun clearCompleted() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val completedTasks = downloadDao.getAll().filter { it.status == 2L }
                completedTasks.forEach { task ->
                    if (task.localPath.isNotEmpty()) deleteFile(task.localPath)
                    deleteDirectory(workspaceDirFor(task.id))
                    settings.remove(headersKey(task.id))
                }
                downloadDao.deleteCompleted()
            } catch (e: Exception) {
                logger.e { "Clear completed failed: ${e.message}" }
            }
        }
    }

    // ==================== 请求头持久化 ====================
    //
    // 存在 Settings 而不是数据库列：加列需要 SQLDelight schema 迁移，
    // 而请求头是「可再生的辅助信息」（丢了顶多 403，重新解析一次即可），
    // 不值得为它增加一次有风险的库结构变更。

    private fun saveHeaders(
        taskId: String,
        headers: Map<String, String>,
    ) {
        runCatching { settings.putString(headersKey(taskId), json.encodeToString(headers)) }
    }

    private fun loadHeaders(taskId: String): Map<String, String> =
        runCatching {
            settings
                .getStringOrNull(headersKey(taskId))
                ?.let { raw -> json.decodeFromString<Map<String, String>>(raw) }
                .orEmpty()
        }.getOrDefault(emptyMap())

    private fun headersKey(taskId: String): String = "download_headers_$taskId"

    // ==================== 文件路径 ====================

    private fun targetPathFor(taskId: String): String = "$downloadDirPath/$taskId${extensionFor(taskId)}"

    /**
     * 产物扩展名：m3u8 合并后是 MPEG-TS/MP4 片段流，用 `.ts` 更贴近真实内容 ——
     * 之前一律写 `.mp4`，播放器按 mp4 解析 ts 流会失败。
     */
    private fun extensionFor(taskId: String): String {
        val url =
            _tasks.value
                .find { it.id == taskId }
                ?.videoUrl
                .orEmpty()
        return if (downloadService.isHlsUrl(url)) ".ts" else ".mp4"
    }

    private fun workspaceDirFor(taskId: String): String = "$downloadDirPath/.parts/$taskId"

    private val downloadDirPath: String get() = PlatformPaths.downloadDir

    private fun updateTaskStatus(
        taskId: String,
        status: DownloadTaskStatus,
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                downloadDao.updateProgress(taskId, 0L, status.toDb())
            } catch (e: Exception) {
                logger.e { "Update task status failed: ${e.message}" }
            }
        }
    }

    /**
     * 仅更新任务状态，保留当前进度
     */
    private fun updateTaskStatusOnly(
        taskId: String,
        status: DownloadTaskStatus,
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val existing = downloadDao.getById(taskId) ?: return@launch
                downloadDao.update(
                    id = taskId,
                    vodId = existing.vodId,
                    vodName = existing.vodName,
                    episodeName = existing.episodeName,
                    videoUrl = existing.videoUrl,
                    localPath = existing.localPath,
                    status = status.toDb(),
                    progress = existing.progress,
                    fileSize = existing.fileSize,
                )
            } catch (e: Exception) {
                logger.e { "Update task status only failed: ${e.message}" }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
        downloadSpeedMap.clear()
    }

    private companion object {
        /** 并发上限：2~3 路足够跑满常见带宽，再多只会拖慢正在播放的流。 */
        const val MAX_CONCURRENT_DOWNLOADS = 3
        const val SPEED_UPDATE_INTERVAL_MS = 500L
    }
}

private fun fileSizeOf(path: String): Long = runCatching { java.io.File(path).length() }.getOrDefault(0L)

private fun deleteFile(path: String) {
    runCatching { java.io.File(path).delete() }
}

private fun deleteDirectory(path: String) {
    runCatching { java.io.File(path).deleteRecursively() }
}

private fun formatSpeed(bytesPerSecond: Double): String =
    when {
        bytesPerSecond <= 0 -> ""
        bytesPerSecond < 1024 -> "${bytesPerSecond.toInt()} B/s"
        bytesPerSecond < 1024 * 1024 -> "${(bytesPerSecond / 1024).toInt()} KB/s"
        else -> "${String.format("%.1f", bytesPerSecond / (1024 * 1024))} MB/s"
    }

private fun DownloadTaskEntity.toUiModel(): DownloadTaskItem =
    DownloadTaskItem(
        id = id,
        vodId = vodId,
        vodName = vodName,
        episodeName = episodeName,
        videoUrl = videoUrl,
        localPath = localPath,
        progress = progress.toInt(),
        status = DownloadTaskStatus.fromDb(status),
        fileSize = fileSize,
    )
