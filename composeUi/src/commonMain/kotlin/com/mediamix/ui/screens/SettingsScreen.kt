package com.mediamix.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediamix.ui.prefs.AppPreferences
import com.mediamix.ui.prefs.DecodeMode
import com.mediamix.ui.prefs.description
import com.mediamix.ui.prefs.label
import com.mediamix.ui.theme.ThemeMode
import com.mediamix.ui.theme.label
import com.mediamix.ui.util.formatFileSize
import com.mediamix.ui.viewmodel.SettingsViewModel
import org.koin.compose.koinInject

/**
 * 设置页。
 *
 * 修正点：
 * 1. 主题、解码方式都真正写盘并即时生效（原来主题写进了 Settings 但没人读，
 *    解码方式只是 Composable 里的 `remember`，退出页面就丢）。
 * 2. 清除缓存 / 导出数据都有确认与结果反馈，不再"点完没反应"。
 * 3. 所有选项用底部弹层 + 单选，比一堆 AlertDialog 更容易点准。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinInject(),
    onNavigateToSourceManage: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val decodeMode by viewModel.decodeMode.collectAsState()
    val shareUsageData by viewModel.shareUsageData.collectAsState()
    val skipInterval by viewModel.skipInterval.collectAsState()
    val cacheStats by viewModel.cacheStats.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var showThemeSheet by remember { mutableStateOf(false) }
    var showDecodeSheet by remember { mutableStateOf(false) }
    var showSkipIntervalSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshCacheStats() }

    LaunchedEffect(message?.id) {
        message?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ── 数据源 ──
            item { SectionHeader("数据源") }
            item {
                SettingsTile(
                    icon = Icons.Default.Source,
                    title = "数据源管理",
                    subtitle = "启用 / 停用 CMS 数据源，检测可用性",
                    onClick = onNavigateToSourceManage,
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── 通用 ──
            item { SectionHeader("通用") }
            item {
                SettingsTile(
                    icon = Icons.Default.Download,
                    title = "下载管理",
                    onClick = onNavigateToDownloads,
                )
            }
            item {
                SettingsTile(
                    icon = Icons.Default.BrightnessAuto,
                    title = "主题",
                    subtitle = themeMode.label(),
                    onClick = { showThemeSheet = true },
                )
            }
            item {
                SettingsTile(
                    icon = Icons.Default.Memory,
                    title = "解码方式",
                    subtitle = decodeMode.label(),
                    onClick = { showDecodeSheet = true },
                )
            }
            item {
                SettingsTile(
                    icon = Icons.Default.FastForward,
                    title = "快进 / 快退间隔",
                    subtitle = "$skipInterval 秒 · 双击画面左右两侧也按此间隔跳转",
                    onClick = { showSkipIntervalSheet = true },
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── 隐私与数据 ──
            item { SectionHeader("隐私与数据") }
            item {
                SettingsSwitchTile(
                    icon = Icons.Default.Analytics,
                    title = "使用数据分享",
                    subtitle = if (shareUsageData) "已开启 — 记录播放指标帮助排查问题" else "已关闭",
                    checked = shareUsageData,
                    onCheckedChange = { viewModel.setShareUsageData(it) },
                )
            }
            item {
                SettingsTile(
                    icon = Icons.Default.Storage,
                    title = "清除缓存",
                    subtitle = "当前占用 ${formatFileSize(cacheStats.totalSize)}（${cacheStats.entryCount} 个文件）",
                    onClick = { showClearCacheDialog = true },
                )
            }
            item {
                SettingsTile(
                    icon = Icons.Default.FileDownload,
                    title = "导出数据",
                    subtitle = "把收藏与历史导出为 JSON 文件",
                    onClick = { viewModel.exportData() },
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── 关于 ──
            item { SectionHeader("关于") }
            item {
                SettingsTile(
                    icon = Icons.Default.BugReport,
                    title = "播放诊断",
                    subtitle = "播放指标、缓存占用、各源实测延迟",
                    onClick = onNavigateToDebug,
                )
            }
            item {
                SettingsTile(
                    icon = Icons.Default.Info,
                    title = "关于 MediaMix",
                    subtitle = "版本 0.2.0 · Compose Multiplatform",
                    onClick = { showAboutDialog = true },
                )
            }
        }
    }

    // ── 主题选择 ──
    if (showThemeSheet) {
        OptionSheet(
            title = "主题",
            options = ThemeMode.entries.map { it to it.label() },
            selected = themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                showThemeSheet = false
            },
            onDismiss = { showThemeSheet = false },
        )
    }

    // ── 解码方式 ──
    if (showDecodeSheet) {
        OptionSheet(
            title = "解码方式",
            options = DecodeMode.entries.map { it to it.label() },
            selected = decodeMode,
            descriptionOf = { it.description() },
            onSelect = {
                viewModel.setDecodeMode(it)
                showDecodeSheet = false
            },
            onDismiss = { showDecodeSheet = false },
        )
    }

    // ── 快进 / 快退间隔 ──
    if (showSkipIntervalSheet) {
        OptionSheet(
            title = "快进 / 快退间隔",
            options = AppPreferences.SKIP_INTERVAL_OPTIONS.map { it to "$it 秒" },
            selected = skipInterval,
            onSelect = {
                viewModel.setSkipInterval(it)
                showSkipIntervalSheet = false
            },
            onDismiss = { showSkipIntervalSheet = false },
        )
    }

    // ── 清除缓存确认 ──
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清除缓存") },
            text = {
                Text(
                    "将删除已缓存的视频分片，共 ${formatFileSize(cacheStats.totalSize)}。" +
                        "收藏与观看记录不受影响。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        showClearCacheDialog = false
                    },
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("取消") }
            },
        )
    }

    // ── 关于 ──
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于 MediaMix") },
            text = {
                Column {
                    Text("版本 0.2.0")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "跨平台视频聚合播放器",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kotlin Multiplatform + Compose Multiplatform",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("关闭") }
            },
        )
    }
}

// ============================================================================
// 内部组件
// ============================================================================

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {},
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent =
            subtitle?.let {
                {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SettingsSwitchTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent =
            subtitle?.let {
                {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

/**
 * 通用单选面板。
 *
 * 用底部弹层而不是 AlertDialog：选项多的时候对话框得自己加滚动，
 * 而且在小屏手机上会把内容压成一条缝。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    descriptionOf: ((T) -> String)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Surface(
                    onClick = { onSelect(value) },
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    shape = RoundedCornerShape(10.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                            descriptionOf?.invoke(value)?.let { desc ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
