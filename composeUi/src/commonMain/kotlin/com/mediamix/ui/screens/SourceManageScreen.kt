package com.mediamix.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.SourceStatus
import com.mediamix.ui.viewmodel.SourceManageViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManageScreen(
    viewModel: SourceManageViewModel = koinInject(),
    onBack: () -> Unit = {},
) {
    val sites by viewModel.sites.collectAsState()
    val sourceStatuses by viewModel.sourceStatuses.collectAsState()
    val isChecking by viewModel.isChecking.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CmsApiSite?>(null) }

    LaunchedEffect(Unit) { viewModel.loadSources() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("数据源管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.checkAllSources() },
                        enabled = !isChecking,
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = "检测全部")
                        }
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加源")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            items(sites, key = { it.key }) { site ->
                val status = sourceStatuses[site.key]
                SourceTile(
                    site = site,
                    status = status,
                    onToggle = { viewModel.toggleSourceEnabled(site.key) },
                    onDelete =
                        if (!site.isBuiltIn) {
                            { deleteTarget = site }
                        } else {
                            null
                        },
                    onCheck = { viewModel.checkSource(site) },
                )
                HorizontalDivider()
            }
        }
    }

    // ── 删除确认对话框 ──
    deleteTarget?.let { site ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除数据源") },
            text = { Text("确定要删除「${site.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeSource(site.key)
                        deleteTarget = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // ── 添加源对话框 ──
    if (showAddDialog) {
        AddSourceDialog(
            isImporting = isImporting,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                // 地址可能是 CMS 接口，也可能是 TVBox 配置（图片伪装的 JSON）——
                // 由 ViewModel 探测后再决定按哪种源导入并给出结论。
                viewModel.importSource(name, url)
                showAddDialog = false
            },
        )
    }

    // 导入结果（成功/失败原因）用 Snackbar 形式提示一次
    importMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeImportMessage()
        }
    }
}

@Composable
private fun SourceTile(
    site: CmsApiSite,
    status: SourceStatus?,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)?,
    onCheck: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = site.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (site.isTvBox) {
                    Spacer(Modifier.width(8.dp))
                    // TVBox 源依赖 dex 格式的蜘蛛包 —— Android 端专属能力，
                    // 桌面端（JVM）加载不了 dex，标注清楚避免用户反复试。
                    Text(
                        text = "TVBox·手机端",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                    MaterialTheme.shapes.extraSmall,
                                ).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (site.isBuiltIn) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "内置",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.shapes.extraSmall,
                                ).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        },
        supportingContent = {
            Column {
                Text(
                    text = site.apiUrl,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp,
                )
                if (status != null) {
                    val color =
                        when {
                            status.isAvailable && status.latencyMs < 500 -> Color(0xFF4CAF50)
                            status.isAvailable -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                    Text(
                        text = if (status.isAvailable) "可用 · ${status.latencyMs}ms" else "不可用",
                        fontSize = 11.sp,
                        color = color,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        leadingContent = {
            val statusPair: Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> =
                when {
                    status == null -> Icons.Default.Cloud to Color.Gray
                    status.isAvailable && status.latencyMs < 500 -> Icons.Default.CloudDone to Color(0xFF4CAF50)
                    status.isAvailable -> Icons.Default.CloudQueue to Color(0xFFFF9800)
                    else -> Icons.Default.CloudOff to Color(0xFFF44336)
                }
            Icon(statusPair.first, contentDescription = null, tint = statusPair.second)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = site.enabled, onCheckedChange = { onToggle() })
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        modifier = Modifier,
    )
}

@Composable
private fun AddSourceDialog(
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加数据源") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("源名称") },
                    placeholder = { Text("如：我的资源站") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("地址") },
                    placeholder = { Text("CMS 接口或 TVBox 配置地址") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    // 两种地址都从同一个入口进：先探测再决定按 CMS 还是 TVBox 导入，
                    // 用户不需要先知道自己的地址属于哪一类。
                    text =
                        "CMS 接口（…/api.php/provide/vod/）与 TVBox 配置（图片伪装的 JSON）都支持，" +
                            "导入前会先探测一次确认可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Button(
                    onClick = {
                        if (url.isNotBlank()) onConfirm(name.trim(), url.trim())
                    },
                    enabled = url.isNotBlank(),
                ) { Text("验证并添加") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
