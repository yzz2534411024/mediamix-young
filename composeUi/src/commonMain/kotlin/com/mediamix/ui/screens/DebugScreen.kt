package com.mediamix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediamix.ui.viewmodel.DebugEntry
import com.mediamix.ui.viewmodel.DebugViewModel
import org.koin.compose.koinInject

/**
 * 播放诊断面板。
 *
 * 只读展示：播放指标 / 缓存统计 / 数据源实测延迟。每秒刷新一次，
 * 便于在播放过程中直接观察缓冲与首帧变化，不用再连 logcat。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel = koinInject(),
    onBack: () -> Unit = {},
) {
    val metrics by viewModel.metrics.collectAsState()
    val cacheStats by viewModel.cacheStats.collectAsState()
    val sources by viewModel.sources.collectAsState()

    // 只在页面可见时轮询
    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放诊断") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SectionTitle("播放指标") }
            if (metrics.isEmpty()) {
                item { EmptyHint("暂无数据") }
            } else {
                items(metrics.size) { i -> EntryRow(metrics[i]) }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            item { SectionTitle("缓存") }
            if (cacheStats.isEmpty()) {
                item { EmptyHint("暂无数据") }
            } else {
                items(cacheStats.size) { i -> EntryRow(cacheStats[i]) }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            item { SectionTitle("数据源延迟（按快慢排序）") }
            if (sources.isEmpty()) {
                item {
                    EmptyHint("还没有测速数据，去「设置 → 数据源管理」跑一次检测")
                }
            } else {
                items(sources.size) { i -> EntryRow(sources[i]) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun EntryRow(entry: DebugEntry) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
