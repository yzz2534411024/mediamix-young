package com.mediamix.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediamix.shared.player.AspectMode
import com.mediamix.shared.player.PlayMode
import com.mediamix.shared.player.SubtitleTrack

// ============================================================================
// 倍速
// ============================================================================

/**
 * 倍速选择。
 *
 * ⚠️ 必须可滚动：12 档速度 + 标题在当前手机屏高下会超出对话框，
 * 旧实现用不可滚动的 Column，底部的 2.5x / 3.0x 根本点不到。
 */
@Composable
fun SpeedSelectorDialog(
    speeds: List<Float>,
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放速度") },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                speeds.forEach { speed ->
                    SelectableRow(
                        label = if (speed % 1f == 0f) "${speed.toInt()}.0x" else "${speed}x",
                        selected = currentSpeed == speed,
                        onClick = {
                            onSelect(speed)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ============================================================================
// 字幕
// ============================================================================

@Composable
fun SubtitleTrackSelectorDialog(
    tracks: List<SubtitleTrack>,
    currentTrack: Int,
    showSubtitles: Boolean,
    subtitleOffsetMs: Long = 0L,
    onSelectTrack: (Int) -> Unit,
    onDisable: () -> Unit,
    onAdjustOffset: (Long) -> Unit = {},
    onResetOffset: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("字幕") },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                SelectableRow(
                    label = "关闭字幕",
                    selected = !showSubtitles,
                    onClick = {
                        onDisable()
                        onDismiss()
                    },
                )
                tracks.forEachIndexed { index, track ->
                    SelectableRow(
                        label = track.label,
                        selected = showSubtitles && currentTrack == index,
                        onClick = {
                            onSelectTrack(index)
                            onDismiss()
                        },
                    )
                }

                if (tracks.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "字幕同步偏移",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "当前 ${if (subtitleOffsetMs > 0) "+" else ""}${subtitleOffsetMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OffsetButton("-200") { onAdjustOffset(-200L) }
                        OffsetButton("-50") { onAdjustOffset(-50L) }
                        OffsetButton("归零") { onResetOffset() }
                        OffsetButton("+50") { onAdjustOffset(50L) }
                        OffsetButton("+200") { onAdjustOffset(200L) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun RowScope.OffsetButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        modifier =
            Modifier
                .weight(1f)
                .height(34.dp),
    ) {
        Text(label, fontSize = 11.sp, maxLines = 1)
    }
}

// ============================================================================
// 选集面板
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSheetContent(
    episodes: List<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "选集",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "共 ${episodes.size} 集",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(episodes) { index, name ->
                val selected = index == currentIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(8.dp),
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    modifier = Modifier.height(38.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 更多面板
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMoreSheet(
    aspectMode: AspectMode,
    playMode: PlayMode,
    decodeLabel: String,
    onAspectSelect: (AspectMode) -> Unit,
    onPlayModeSelect: (PlayMode) -> Unit,
    onLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
    ) {
        Text(
            text = "播放设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 12.dp),
        )

        SettingGroup(icon = Icons.Default.AspectRatio, title = "画面比例") {
            // 只暴露三种真正生效的模式：ExoPlayer 的 TextureView 天然等比适配，
            // "拉伸/裁剪"需要自定义 GL 处理，这里不做假的选项。
            val modes =
                listOf(
                    AspectMode.ORIGINAL to "自适应",
                    AspectMode.RATIO_16_9 to "16:9",
                    AspectMode.RATIO_4_3 to "4:3",
                )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (mode, label) ->
                    FilterChip(
                        selected = aspectMode == mode,
                        onClick = { onAspectSelect(mode) },
                        label = { Text(label, fontSize = 12.sp) },
                    )
                }
            }
        }

        SettingGroup(icon = Icons.Default.PlaylistPlay, title = "播放模式") {
            val modes =
                listOf(
                    PlayMode.SEQUENTIAL to "顺序播放",
                    PlayMode.LOOP_ALL to "列表循环",
                    PlayMode.LOOP_SINGLE to "单集循环",
                )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (mode, label) ->
                    FilterChip(
                        selected = playMode == mode,
                        onClick = { onPlayModeSelect(mode) },
                        label = { Text(label, fontSize = 12.sp) },
                    )
                }
            }
        }

        SettingGroup(icon = Icons.Default.Memory, title = "解码方式") {
            Text(
                text = "$decodeLabel · 可在「设置 → 解码方式」中修改",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onLock,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("锁定屏幕")
        }

        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text("关闭")
        }
    }
}

@Composable
private fun SettingGroup(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

// ============================================================================
// 通用行
// ============================================================================

@Composable
private fun SelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        shape = RoundedCornerShape(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
