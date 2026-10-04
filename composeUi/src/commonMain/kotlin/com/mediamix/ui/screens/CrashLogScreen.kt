package com.mediamix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediamix.ui.viewmodel.CrashLogViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 正式版"崩溃日志"页：列出本地崩溃记录，点开看详情，支持一键清空。
 * 数据由双端入口安装的未捕获异常处理器写入（最多保留 20 份）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen(
    onBack: () -> Unit,
    viewModel: CrashLogViewModel = viewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val cleared by viewModel.cleared.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(cleared) { if (cleared > 0) viewModel.load() }

    // 详情视图
    detail?.let { (fileName, content) ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(fileName, fontSize = 14.sp) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeDetail() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回列表")
                        }
                    },
                )
            },
        ) { padding ->
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("崩溃日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearAll() }) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("没有崩溃记录", style = MaterialTheme.typography.titleMedium)
                Text(
                    "应用运行期间未发生未捕获异常",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "共 ${entries.size} 条 · 仅保存在本机",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(entries, key = { it.fileName }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.open(entry.fileName) },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                .format(Date(entry.lastModifiedMs)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "${entry.sizeBytes / 1024} KB · 点按查看详情",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
