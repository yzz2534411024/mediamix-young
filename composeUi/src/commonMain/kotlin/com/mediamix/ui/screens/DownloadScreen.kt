package com.mediamix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediamix.ui.util.formatFileSize
import com.mediamix.ui.viewmodel.DownloadTaskStatus
import com.mediamix.ui.viewmodel.DownloadViewModel
import org.koin.compose.koinInject
import com.mediamix.ui.icons.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onBack: () -> Unit = {},
    onPlayVideo: (localPath: String, title: String) -> Unit = { _, _ -> },
    viewModel: DownloadViewModel = koinInject(),
) {
    val tasks by viewModel.tasks.collectAsState()

    // Delete confirmation dialog state
    var taskToDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("\u4e0b\u8f7d\u7ba1\u7406") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.clearCompleted() },
                    ) {
                        Icon(AppIcons.CleaningServices, contentDescription = "\u6e05\u9664\u5df2\u5b8c\u6210")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        AppIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "\u6682\u65e0\u4e0b\u8f7d\u4efb\u52a1",
                        fontSize = 16.sp,
                        color = Color.Gray,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskTile(
                        task = task,
                        onPause = { viewModel.pauseDownload(task.id) },
                        onResume = { viewModel.resumeDownload(task.id) },
                        onDelete = { taskToDelete = task.id },
                        onPlay = {
                            if (task.localPath.isNotEmpty()) {
                                onPlayVideo(task.localPath, "${task.vodName} - ${task.episodeName}")
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Delete confirmation dialog
    if (taskToDelete != null) {
        val taskId = taskToDelete!!
        val task = tasks.find { it.id == taskId }
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("\u786e\u8ba4\u5220\u9664") },
            text = { Text("\u786e\u5b9a\u8981\u5220\u9664\u201c${task?.vodName ?: ""}\u201d\u7684\u4e0b\u8f7d\u4efb\u52a1\u5417\uff1f") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(taskId)
                    taskToDelete = null
                }) {
                    Text("\u5220\u9664", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("\u53d6\u6d88")
                }
            },
        )
    }
}

@Composable
private fun DownloadTaskTile(
    task: com.mediamix.ui.viewmodel.DownloadTaskItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
) {
    val statusColor =
        when (task.status) {
            DownloadTaskStatus.WAITING -> Color.Gray
            DownloadTaskStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
            DownloadTaskStatus.PAUSED -> Color(0xFFFF9800)
            DownloadTaskStatus.COMPLETED -> Color(0xFF4CAF50)
            DownloadTaskStatus.FAILED -> Color(0xFFF44336)
        }

    val statusIcon =
        when (task.status) {
            DownloadTaskStatus.WAITING -> AppIcons.Schedule
            DownloadTaskStatus.DOWNLOADING -> AppIcons.Downloading
            DownloadTaskStatus.PAUSED -> AppIcons.PauseCircle
            DownloadTaskStatus.COMPLETED -> Icons.Default.CheckCircle
            DownloadTaskStatus.FAILED -> AppIcons.Error
        }

    ListItem(
        headlineContent = {
            Text(
                text = task.vodName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(text = task.episodeName, fontSize = 12.sp)
                if (task.status == DownloadTaskStatus.DOWNLOADING) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "\u4e0b\u8f7d\u4e2d ${task.progress}%",
                            fontSize = 11.sp,
                            color = statusColor,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (task.downloadSpeed.isNotEmpty()) {
                                Text(
                                    text = task.downloadSpeed,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (task.fileSize > 0L) {
                                Text(
                                    text = formatFileSize(task.fileSize),
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                } else if (task.status == DownloadTaskStatus.PAUSED) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFF9800),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "\u5df2\u6682\u505c ${task.progress}%",
                        fontSize = 11.sp,
                        color = statusColor,
                    )
                } else {
                    Text(
                        text = getStatusText(task),
                        fontSize = 11.sp,
                        color = statusColor,
                    )
                }
            }
        },
        leadingContent = {
            Icon(statusIcon, contentDescription = null, tint = statusColor)
        },
        trailingContent = {
            Row {
                when (task.status) {
                    DownloadTaskStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) {
                            Icon(AppIcons.Pause, contentDescription = "\u6682\u505c")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(AppIcons.Cancel, contentDescription = "\u53d6\u6d88")
                        }
                    }
                    DownloadTaskStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "\u7ee7\u7eed")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(AppIcons.Cancel, contentDescription = "\u53d6\u6d88")
                        }
                    }
                    DownloadTaskStatus.WAITING -> {
                        IconButton(onClick = onDelete) {
                            Icon(AppIcons.Cancel, contentDescription = "\u53d6\u6d88")
                        }
                    }
                    DownloadTaskStatus.COMPLETED -> {
                        // 播放按钮
                        if (task.localPath.isNotEmpty()) {
                            IconButton(onClick = onPlay) {
                                Icon(
                                    AppIcons.PlayCircle,
                                    contentDescription = "\u64ad\u653e",
                                    tint = Color(0xFF4CAF50),
                                )
                            }
                        }
                        IconButton(onClick = onDelete) {
                            Icon(AppIcons.DeleteOutline, contentDescription = "\u5220\u9664")
                        }
                    }
                    DownloadTaskStatus.FAILED -> {
                        IconButton(onClick = { onResume() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "\u91cd\u8bd5")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(AppIcons.DeleteOutline, contentDescription = "\u5220\u9664")
                        }
                    }
                }
            }
        },
    )
}

private fun getStatusText(task: com.mediamix.ui.viewmodel.DownloadTaskItem): String =
    when (task.status) {
        DownloadTaskStatus.WAITING -> "\u7b49\u5f85\u4e0b\u8f7d"
        DownloadTaskStatus.DOWNLOADING -> "\u4e0b\u8f7d\u4e2d ${task.progress}%"
        DownloadTaskStatus.PAUSED -> "\u5df2\u6682\u505c ${task.progress}%"
        DownloadTaskStatus.COMPLETED -> "\u5df2\u5b8c\u6210 \u00b7 ${formatFileSize(task.fileSize)}"
        DownloadTaskStatus.FAILED -> "\u4e0b\u8f7d\u5931\u8d25"
    }
