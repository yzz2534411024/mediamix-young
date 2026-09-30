package com.mediamix.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediamix.ui.viewmodel.SettingsViewModel
import com.mediamix.ui.viewmodel.ThemeModeOption
import com.mediamix.ui.util.formatFileSize
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinInject(),
    onNavigateToSourceManage: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val cacheStats by viewModel.cacheStats.collectAsState()

    var showDecodeDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var decodeMode by remember { mutableIntStateOf(0) }
    var metricsEnabled by remember { mutableStateOf(false) }

    val decodeModeLabels = listOf("自动", "硬件优先", "软件优先")
    val themeModeLabels = listOf("跟随系统", "浅色模式", "深色模式")

    LaunchedEffect(Unit) { viewModel.refreshCacheStats() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── 数据源 ──
            item { SectionHeader(title = "数据源") }
            item {
                SettingsTile(
                    icon = { Icon(Icons.Default.Source, contentDescription = null) },
                    title = "数据源管理",
                    subtitle = "管理 CMS / Spider 数据源",
                    onClick = onNavigateToSourceManage,
                )
            }
            item { HorizontalDivider() }

            // ── 通用 ──
            item { SectionHeader(title = "通用") }
            item {
                SettingsTile(
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    title = "下载管理",
                    onClick = onNavigateToDownloads,
                )
            }
            item {
                SettingsTile(
                    icon = { Icon(Icons.Default.Memory, contentDescription = null) },
                    title = "解码模式",
                    subtitle = decodeModeLabels[decodeMode],
                    onClick = { showDecodeDialog = true },
                )
            }
            item {
                val themeIcon = when (themeMode) {
                    ThemeModeOption.DARK -> Icons.Default.DarkMode
                    ThemeModeOption.LIGHT -> Icons.Default.LightMode
                    else -> Icons.Default.BrightnessAuto
                }
                SettingsTile(
                    icon = { Icon(themeIcon, contentDescription = null) },
                    title = "主题设置",
                    subtitle = themeModeLabels[themeMode.ordinal],
                    onClick = { showThemeDialog = true },
                )
            }
            item { HorizontalDivider() }

            // ── 隐私与数据 ──
            item { SectionHeader(title = "隐私与数据") }
            item {
                SettingsSwitchTile(
                    icon = {
                        Icon(
                            if (metricsEnabled) Icons.Default.Analytics else Icons.Default.Analytics,
                            contentDescription = null,
                        )
                    },
                    title = "使用数据分享",
                    subtitle = if (metricsEnabled) "已开启 — 帮助改善应用体验" else "已关闭",
                    checked = metricsEnabled,
                    onCheckedChange = { metricsEnabled = it },
                )
            }
            item {
                SettingsTile(
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    title = "清除缓存",
                    subtitle = formatFileSize(cacheStats.totalSize),
                    onClick = { viewModel.clearCache() },
                )
            }
            item {
                SettingsTile(
                    icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    title = "导出数据",
                    subtitle = "导出收藏 / 历史数据为 JSON",
                    onClick = { viewModel.exportData() /* TODO: share file */ },
                )
            }
            item { HorizontalDivider() }

            // ── 关于 ──
            item {
                SettingsTile(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    title = "关于",
                    onClick = { showAboutDialog = true },
                )
            }
        }
    }

    // ── 解码模式对话框 ──
    if (showDecodeDialog) {
        AlertDialog(
            onDismissRequest = { showDecodeDialog = false },
            title = { Text("选择解码模式") },
            text = {
                Column {
                    decodeModeLabels.forEachIndexed { index, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    decodeMode = index
                                    showDecodeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = decodeMode == index,
                                onClick = {
                                    decodeMode = index
                                    showDecodeDialog = false
                                },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    // ── 主题模式对话框 ──
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择主题模式") },
            text = {
                Column {
                    ThemeModeOption.entries.forEachIndexed { index, option ->
                        val label = themeModeLabels[index]
                        val icon = when (option) {
                            ThemeModeOption.DARK -> Icons.Default.DarkMode
                            ThemeModeOption.LIGHT -> Icons.Default.LightMode
                            else -> Icons.Default.BrightnessAuto
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(option)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = themeMode == option,
                                onClick = {
                                    viewModel.setThemeMode(option)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.width(12.dp))
                            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    // ── 关于对话框 ──
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于 MediaMix") },
            text = {
                Column {
                    Text("版本: 0.2.0")
                    Spacer(Modifier.height(8.dp))
                    Text("跨平台视频点播应用", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("基于 Compose Multiplatform", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("关闭") }
            },
        )
    }
}

// ── 内部组件 ──

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
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {},
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon,
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SettingsSwitchTile(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}