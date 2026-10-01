package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.core.PlatformPaths
import com.mediamix.shared.database.DownloadDao
import com.mediamix.shared.database.DownloadTaskEntity
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.io.File

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
            DownloadTaskStatus.COMPLETED -> 2L
            DownloadTaskStatus.FAILED -> 3L
            DownloadTaskStatus.PAUSED -> 4L
        }
}

/**
 * 下载管理 ViewModel
 *
 * 通过 DownloadDao 管理下载任务状态，
 * 使用 Ktor HttpClient 执行实际下载。
 */
class DownloadViewModel(
    private val downloadDao: DownloadDao,
    private val httpClient: HttpClient,
) : ViewModel() {
    private val logger = Logger.withTag("DownloadViewModel")

    private val _tasks = MutableStateFlow<List<DownloadTaskItem>>(emptyList())
    val tasks: StateFlow<List<DownloadTaskItem>> = _tasks.asStateFlow()

    private val activeDownloads = mutableMapOf<String, Job>()
    private val downloadSpeedMap = mutableMapOf<String, String>()

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
     * 添加下载任务
     */
    fun addDownload(
        vodId: String,
        vodName: String,
        episodeName: String,
        videoUrl: String,
    ) {
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
                startDownload(id, videoUrl)
            } catch (e: Exception) {
                logger.e { "Add download failed: ${e.message}" }
            }
        }
    }

    /**
     * 开始下载
     */
    private fun startDownload(
        taskId: String,
        videoUrl: String,
    ) {
        if (activeDownloads.containsKey(taskId)) return

        // 检测 m3u8/HLS 流地址，这类地址无法通过普通 HTTP 下载为有效视频文件
        if (isHlsStreamUrl(videoUrl)) {
            logger.w { "HLS stream detected: $videoUrl, download may produce invalid file" }
        }

        val job =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // 更新状态为下载中
                    updateTaskStatus(taskId, DownloadTaskStatus.DOWNLOADING)

                    val downloadDir = File(PlatformPaths.downloadDir)
                    if (!downloadDir.exists()) downloadDir.mkdirs()
                    val targetFile = File(downloadDir, "$taskId.mp4")

                    val response: HttpResponse = httpClient.get(videoUrl)
                    val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var totalBytes = 0L
                        var lastSpeedUpdateMs = 0L
                        var lastSpeedBytes = 0L
                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            // 更新进度和下载速度
                            if (contentLength > 0) {
                                val progress =
                                    ((totalBytes * 100) / contentLength)
                                        .toInt()
                                        .coerceIn(0, 100)
                                val now = Clock.System.now().toEpochMilliseconds()
                                // 每 500ms 更新一次速度显示
                                if (now - lastSpeedUpdateMs >= 500) {
                                    val elapsed = (now - lastSpeedUpdateMs).coerceAtLeast(1)
                                    val bytesDelta = totalBytes - lastSpeedBytes
                                    val speedBps = bytesDelta * 1000.0 / elapsed
                                    val speedText = formatSpeed(speedBps)
                                    downloadSpeedMap[taskId] = speedText
                                    lastSpeedUpdateMs = now
                                    lastSpeedBytes = totalBytes
                                }
                                try {
                                    downloadDao.updateProgress(taskId, progress.toLong(), 1L)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }

                    // 验证下载文件有效性
                    val fileLength = targetFile.length()
                    if (fileLength < 1024) {
                        logger.w { "Downloaded file too small ($fileLength bytes), likely not a valid video" }
                    }

                    // 更新为完成状态
                    val existing = downloadDao.getById(taskId)
                    if (existing != null) {
                        downloadDao.update(
                            id = taskId,
                            vodId = existing.vodId,
                            vodName = existing.vodName,
                            episodeName = existing.episodeName,
                            videoUrl = existing.videoUrl,
                            localPath = targetFile.absolutePath,
                            status = 2L, // COMPLETED
                            progress = 100L,
                            fileSize = targetFile.length(),
                        )
                    }
                    logger.d { "Download completed: $taskId (${targetFile.length()} bytes)" }
                    downloadSpeedMap.remove(taskId)
                } catch (e: Exception) {
                    logger.e { "Download failed: $taskId, error: ${e.message}" }
                    updateTaskStatusOnly(taskId, DownloadTaskStatus.FAILED)
                    downloadSpeedMap.remove(taskId)
                } finally {
                    activeDownloads.remove(taskId)
                }
            }
        activeDownloads[taskId] = job
    }

    /**
     * 检测是否为 HLS/m3u8 流地址
     */
    private fun isHlsStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains(".ts?")
    }

    /**
     * 暂停下载（取消下载 Job，标记为 PAUSED 保留进度）
     */
    fun pauseDownload(taskId: String) {
        activeDownloads[taskId]?.cancel()
        activeDownloads.remove(taskId)
        downloadSpeedMap.remove(taskId)
        updateTaskStatusOnly(taskId, DownloadTaskStatus.PAUSED)
    }

    /**
     * 恢复下载（支持 PAUSED / WAITING / FAILED 状态）
     */
    fun resumeDownload(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status == DownloadTaskStatus.DOWNLOADING) return
        // 重置进度为 0 重新开始（如需断点续传可扩展）
        updateTaskStatusOnly(taskId, DownloadTaskStatus.WAITING)
        startDownload(taskId, task.videoUrl)
    }

    /**
     * 删除下载任务
     */
    fun deleteTask(taskId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                activeDownloads[taskId]?.cancel()
                activeDownloads.remove(taskId)

                // 删除本地文件
                val task = downloadDao.getById(taskId)
                if (task != null && task.localPath.isNotEmpty()) {
                    File(task.localPath).delete()
                }
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
                    if (task.localPath.isNotEmpty()) {
                        File(task.localPath).delete()
                    }
                }
                downloadDao.deleteCompleted()
            } catch (e: Exception) {
                logger.e { "Clear completed failed: ${e.message}" }
            }
        }
    }

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
}

private fun formatSpeed(bytesPerSecond: Double): String =
    when {
        bytesPerSecond <= 0 -> ""
        bytesPerSecond < 1024 -> "${"%.0f".format(bytesPerSecond)} B/s"
        bytesPerSecond < 1024 * 1024 -> "${"%.1f".format(bytesPerSecond / 1024)} KB/s"
        else -> "${"%.1f".format(bytesPerSecond / (1024 * 1024))} MB/s"
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
