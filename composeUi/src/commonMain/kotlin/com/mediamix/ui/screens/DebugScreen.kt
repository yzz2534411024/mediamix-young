package com.mediamix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onNavigateToComponentPreview: () -> Unit = {},
) {
    val metrics by viewModel.metrics.collectAsState()
    val cacheStats by viewModel.cacheStats.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val bridge by viewModel.bridge.collectAsState()
    val probeReport by viewModel.probeReport.collectAsState()
    val isProbing by viewModel.isProbing.collectAsState()

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

            item { SectionTitle("TVBox 蜘蛛桥") }
            items(bridge.size) { i -> EntryRow(bridge[i]) }

            item {
                ProbePanel(
                    report = probeReport,
                    isProbing = isProbing,
                    onRun = { viewModel.runProbe() },
                    onSelfTest = { viewModel.runSelfTest() },
                    onClear = { viewModel.clearProbeReport() },
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    SectionTitle("数据源延迟（按快慢排序）")
                    Spacer(Modifier.weight(1f))
                    // 就地提供触发入口：此前只能去「设置 → 数据源管理」跑检测，
                    // 诊断页只显示「未测速」，看着像功能没启用（实测反馈）。
                    OutlinedButton(
                        onClick = { viewModel.runSourceSpeedTest() },
                        enabled = !isProbing,
                    ) {
                        Text(if (isProbing) "测速中…" else "一键测速")
                    }
                }
            }
            if (sources.isEmpty()) {
                item {
                    EmptyHint("还没有测速数据，点右上角「一键测速」跑一次检测")
                }
            } else {
                items(sources.size) { i -> EntryRow(sources[i]) }
            }

            // S2 设计系统的验收入口：一屏看到所有组件在当前主题下的样子
            item {
                OutlinedButton(
                    onClick = onNavigateToComponentPreview,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text("组件预览（设计系统）")
                }
            }
        }
    }
}

/**
 * 一键探测面板。
 *
 * 报告是**可选中复制**的纯文本 —— 排查时经常需要把它贴到 issue 或对话里，
 * 只显示在日志里就得再装一次 adb。
 */
@Composable
private fun ProbePanel(
    report: List<String>,
    isProbing: Boolean,
    onRun: () -> Unit,
    onSelfTest: () -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRun, enabled = !isProbing) {
                Text(if (isProbing) "探测中…" else "一键探测")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onSelfTest, enabled = !isProbing) {
                Text("接口自检")
            }
            if (report.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClear) { Text("清空") }
            }
        }
        Text(
            text =
                "一键探测：依次跑 homeContent → categoryContent → detailContent → playerContent，" +
                    "判定「壳/站点侧无数据」还是「映射层丢数据」。\n接口自检（桌面可跑）：线路连通 → " +
                    "配置拉取+伪装解码 → 站点解析 → 蜘蛛包下载+md5+zip 结构 → 蜘蛛桥状态。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (report.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = report.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp),
                    )
                }
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
