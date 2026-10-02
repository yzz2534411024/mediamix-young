package com.mediamix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediamix.shared.player.PlayMode
import com.mediamix.ui.icons.AppIcons

// ============================================================================
// 顶部控制栏
// ============================================================================

/**
 * 顶部控制栏。
 *
 * 入口固定为 5 个：选集 / 倍速 / 字幕 / 横竖屏 / 更多。旧版把「画面比例、解码、锁屏、
 * 跳过间隔、解析器」全塞在一行里，7 个图标挤在 360dp 宽度上，手指根本点不准。
 * 次要功能收进"更多"面板。
 *
 * ⚠️ 这里刻意**不做 `statusBarsPadding()`**：播放页的控制层外层已经统一用
 * `safeDrawingPadding()` 处理四边安全区，两处都加会叠加成双倍顶部留白。
 */
@Composable
fun TopControlsBar(
    title: String,
    subtitle: String?,
    showEpisodeEntry: Boolean,
    hasSubtitles: Boolean,
    speed: Float,
    isLandscape: Boolean,
    onBack: () -> Unit,
    onEpisodeClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onRotateClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
        ) {
            Text(
                text = title.ifEmpty { "正在播放" },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }

        if (showEpisodeEntry) {
            IconButton(onClick = onEpisodeClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = AppIcons.PlaylistPlay,
                    contentDescription = "选集",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        TextButton(
            onClick = onSpeedClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.height(40.dp),
        ) {
            Text(
                text = "${trimSpeed(speed)}x",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        IconButton(onClick = onSubtitleClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector =
                    if (hasSubtitles) {
                        AppIcons.ClosedCaption
                    } else {
                        AppIcons.ClosedCaptionOff
                    },
                contentDescription = "字幕",
                tint = Color.White.copy(alpha = if (hasSubtitles) 1f else 0.45f),
                modifier = Modifier.size(20.dp),
            )
        }

        // 横竖屏切换。播放页默认横屏，这里给出回到竖屏的入口
        // （有些用户习惯躺着竖屏看，强制横屏反而别扭）。
        IconButton(onClick = onRotateClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = AppIcons.ScreenRotation,
                contentDescription = if (isLandscape) "竖屏播放" else "横屏播放",
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        }

        IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多",
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

private fun trimSpeed(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

// ============================================================================
// 底部控制栏
// ============================================================================

/**
 * 底部控制栏。
 *
 * 图标与实际行为对齐：快退/快进统一用 FastRewind / FastForward 并叠加秒数角标，
 * 而不是旧版那个"图标写着 30、实际跳 10 秒"的 Forward30。
 *
 * 长按继续沿用「跳过间隔设置」入口，但这里用 [combinedClickable] 而不是把
 * `pointerInput(detectTapGestures)` 叠在 IconButton 上 —— 后者会让单击被
 * onClick 和 onTap 各处理一次，点一下实际跳两次。
 */
@Composable
fun BottomControlsBar(
    playMode: PlayMode,
    isPlaying: Boolean,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    skipInterval: Int,
    onPlayModeClick: () -> Unit,
    onPrevEpisode: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPlayModeClick, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector =
                    when (playMode) {
                        PlayMode.SEQUENTIAL -> AppIcons.PlaylistPlay
                        PlayMode.LOOP_SINGLE -> AppIcons.RepeatOne
                        PlayMode.LOOP_ALL -> AppIcons.Repeat
                    },
                contentDescription = "播放模式",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp),
            )
        }

        if (hasPrevEpisode) {
            IconButton(onClick = onPrevEpisode, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = AppIcons.SkipPrevious,
                    contentDescription = "上一集",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        SkipButton(
            icon = AppIcons.FastRewind,
            contentDescription = "后退 $skipInterval 秒",
            seconds = skipInterval,
            onClick = onRewind,
            onLongClick = onRewind,
        )

        // 主按钮：白色实心圆底，视觉重心明确
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        SkipButton(
            icon = AppIcons.FastForward,
            contentDescription = "前进 $skipInterval 秒",
            seconds = skipInterval,
            onClick = onForward,
            onLongClick = onForward,
        )

        if (hasNextEpisode) {
            IconButton(onClick = onNextEpisode, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = AppIcons.SkipNext,
                    contentDescription = "下一集",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SkipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    seconds: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

// ============================================================================
// 进度条
// ============================================================================

/**
 * 进度条。
 *
 * 拖动时只更新本地预览值，松手才真正 seek —— 否则 [Slider] 的
 * `onValueChange` 每一帧都会触发一次 seek，拖动过程中播放器会被反复打断。
 */
@Composable
fun PlayerProgressBar(
    positionMs: Long,
    durationMs: Long,
    bufferedPercentage: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxMs = durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (dragging) dragValue else positionMs.toFloat().coerceIn(0f, maxMs.toFloat())

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(dragValue.toLong())
            },
            valueRange = 0f..maxMs.toFloat(),
            colors =
                SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 只留「当前 / 总时长」两端对齐。缓冲百分比原先挤在正中间，
            // 既打断视线、又和 BufferingIndicator 的信息重复。
            Text(
                text = formatDuration(sliderValue.toLong()),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = formatDuration(durationMs),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
}
