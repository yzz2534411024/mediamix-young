package com.mediamix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediamix.ui.components.formatDuration
import com.mediamix.ui.viewmodel.UsageStatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 正式版"使用记录"页：本地观影统计（数据只在本机，不上传）。
 * 替代原先的调试页（DebugScreen）入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    onBack: () -> Unit,
    viewModel: UsageStatsViewModel = viewModel(),
) {
    val stats by viewModel.stats.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("数据仅保存在本机", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "统计来自你的观看历史与播放进度",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatRow("看过影片", "${stats?.watchedCount ?: 0} 部")
                        StatRow("观看中", "${stats?.inProgressCount ?: 0} 部")
                        StatRow("已看完", "${stats?.finishedCount ?: 0} 部")
                        StatRow("累计观看时长", formatDuration(stats?.totalWatchedMs ?: 0L))
                        StatRow("收藏影片", "${stats?.favoriteCount ?: 0} 部")
                        StatRow(
                            "最近活跃",
                            stats?.latestActiveMs?.takeIf { it > 0 }?.let {
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
                            } ?: "暂无",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (label == "最近活跃") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        )
    }
}
