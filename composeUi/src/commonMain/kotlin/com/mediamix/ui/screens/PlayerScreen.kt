package com.mediamix.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediamix.shared.player.AspectMode
import com.mediamix.shared.player.PlayMode
import com.mediamix.shared.player.PlayerState
import com.mediamix.ui.components.*
import com.mediamix.ui.platform.ApplyScreenOrientation
import com.mediamix.ui.platform.ScreenOrientationMode
import com.mediamix.ui.platform.VideoSurface
import com.mediamix.ui.player.PlaybackSessionStore
import com.mediamix.ui.prefs.AppPreferences
import com.mediamix.ui.prefs.label
import com.mediamix.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.abs

private const val HIDE_CONTROLS_DELAY_MS = 4000L
private const val SPEED_INDICATOR_DURATION_MS = 2000L
private const val DOUBLE_TAP_ZONE_FRACTION = 0.3f
private const val SEEK_ANIM_MS = 400

/**
 * 播放页。
 *
 * 相比旧实现的主要修正：
 * 1. **能播了** —— 地址在数据层被 JSON 引号污染过（`...m3u8"`），现在已清洗；
 *    同时 ExoPlayer 带上了 UA / Referer，被 CDN 拒的概率大幅下降。
 * 2. **组件不再"混乱"** —— 顶部栏只留 4 个入口（选集 / 倍速 / 字幕 / 更多），
 *    底部栏图标与实际行为对齐（旧的 Forward30 实际只跳 10 秒）。
 * 3. **双击/长按不再重复触发** —— 旧代码把 `pointerInput` 挂在 `IconButton` 上，
 *    onClick 和 onTap 会各触发一次，点一下实际跳 20 秒。
 * 4. **选集 / 上下集可用** —— 剧集列表来自 [PlaybackSessionStore]，不再丢失。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    url: String = "",
    title: String = "",
    episodeIndex: Int = 0,
    viewModel: PlayerViewModel = koinInject(),
    sessionStore: PlaybackSessionStore = koinInject(),
    appPreferences: AppPreferences = koinInject(),
    onBack: () -> Unit = {},
) {
    // ---- 状态 ----
    val playerState by viewModel.playerState.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val bufferedPercentage by viewModel.bufferedPercentage.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val isSeeking by viewModel.isSeeking.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val currentSubtitleTrack by viewModel.currentSubtitleTrack.collectAsState()
    val currentEpisodeName by viewModel.currentEpisodeName.collectAsState()
    val currentEpisodeIndex by viewModel.currentEpisodeIndex.collectAsState()
    val episodeList by viewModel.episodeList.collectAsState()
    val playMode by viewModel.playMode.collectAsState()
    val aspectMode by viewModel.aspectMode.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val hasPrevEpisode by viewModel.hasPrevEpisode.collectAsState()
    val hasNextEpisode by viewModel.hasNextEpisode.collectAsState()
    val showSubtitles by viewModel.showSubtitles.collectAsState()
    val speedOptions by viewModel.speedOptions.collectAsState()
    val currentSubtitleText by viewModel.currentSubtitleText.collectAsState()
    val subtitleOffsetMs by viewModel.subtitleOffsetMs.collectAsState()
    val skipInterval by viewModel.skipInterval.collectAsState()

    // ---- UI 局部状态 ----
    var controlsVisible by remember { mutableStateOf(true) }
    var showSpeedIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var seekPreviewPositionMs by remember { mutableStateOf(0L) }
    var showSeekPreview by remember { mutableStateOf(false) }
    var seekDeltaMs by remember { mutableStateOf(0L) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    val isPlaying = playerState == PlayerState.PLAYING
    val session = remember(url) { sessionStore.sessionFor(url) }

    // ---- 屏幕方向 ----
    // 播放页默认横屏（视频全屏体验），顶栏的旋转按钮可在本页切回竖屏。
    var orientationMode by remember { mutableStateOf(ScreenOrientationMode.LANDSCAPE) }
    ApplyScreenOrientation(orientationMode)

    // ---- 生命周期 ----
    // ⚠️ 拆成两段，不要合并回单个 DisposableEffect(url)：
    // 旧写法里 url 一旦变化就会先触发 onDispose（dispose() 会把 PlayerCoreManager
    // 标记为已释放并 release 掉引擎），再重新 openVideo —— 中间那一瞬
    // isDisposed=true，任何播放控制（含"点播放"）都会被直接忽略。
    // 现在 initialize/dispose 只在进出页面时各跑一次，openVideo 单独跟随 url。
    DisposableEffect(Unit) {
        viewModel.initialize()
        onDispose { viewModel.dispose() }
    }

    LaunchedEffect(url, episodeIndex) {
        if (url.isNotEmpty()) {
            viewModel.openVideo(
                url = url,
                title = title,
                episodeIndex = episodeIndex,
                episodeNames = session?.episodeNames,
                episodeUrls = session?.episodeUrls,
                headers = buildPlaybackHeaders(url),
                preferSoftwareDecoding = appPreferences.preferSoftwareDecoding,
                fallbackUrls = session?.fallbackUrlsFor(episodeIndex),
            )
        }
    }

    // 控制栏自动隐藏：拖拽/预览/锁定时不隐藏
    LaunchedEffect(controlsVisible, showSeekPreview, isLocked) {
        if (controlsVisible && !showSeekPreview && !isLocked) {
            delay(HIDE_CONTROLS_DELAY_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(showSpeedIndicator) {
        if (showSpeedIndicator) {
            delay(SPEED_INDICATOR_DURATION_MS)
            showSpeedIndicator = false
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        // 用容器宽高比判断横竖屏，而不是读平台配置 —— commonMain 拿不到
        // Android 的 LocalConfiguration，桌面端也没有"屏幕方向"这个概念。
        val isLandscape = maxWidth > maxHeight
        // 横屏时控制层远离屏幕边缘（单手操作 / 刘海 / 圆角都不易误触），竖屏贴边即可
        val sidePadding = if (isLandscape) 24.dp else 4.dp

        // 画面比例：自适应时铺满（ExoPlayer 自己是等比适配），16:9 / 4:3 时
        // 让渲染面本身变成对应比例并居中，超出的部分自然留黑边。
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            VideoSurface(
                modifier =
                    when (aspectMode) {
                        AspectMode.RATIO_16_9 ->
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        AspectMode.RATIO_4_3 ->
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                        else -> Modifier.fillMaxSize()
                    },
                onSurfaceCreated = { surface -> viewModel.setSurface(surface) },
                onSurfaceDestroyed = { viewModel.setSurface(null) },
            )
        }

        GestureLayer(
            onTap = { controlsVisible = !controlsVisible },
            onDoubleTapLeft = { viewModel.fastSeek(position - skipInterval * 1000L) },
            onDoubleTapRight = { viewModel.fastSeek(position + skipInterval * 1000L) },
            onDoubleTapCenter = { viewModel.togglePlayPause() },
            onHorizontalDragStart = {
                seekPreviewPositionMs = position
                seekDeltaMs = 0L
            },
            onHorizontalDrag = { deltaFraction ->
                val deltaMs = (deltaFraction * duration).toLong()
                seekDeltaMs = deltaMs
                seekPreviewPositionMs = (position + deltaMs).coerceIn(0L, duration)
                showSeekPreview = true
            },
            onHorizontalDragEnd = {
                if (showSeekPreview) viewModel.fastSeek(seekPreviewPositionMs)
                showSeekPreview = false
            },
            onVerticalDragStart = { isLeft ->
                if (isLeft) showBrightnessIndicator = true else showVolumeIndicator = true
            },
            onVerticalDrag = { isLeft, deltaFraction ->
                if (isLeft) {
                    viewModel.setBrightness((brightness + deltaFraction).coerceIn(0f, 1f))
                } else {
                    viewModel.setVolume((volume + deltaFraction).coerceIn(0f, 1f))
                }
            },
            onVerticalDragEnd = {
                showBrightnessIndicator = false
                showVolumeIndicator = false
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ---- 覆盖层 ----
        if (showSubtitles && currentSubtitleText != null) {
            SubtitleOverlay(subtitleText = currentSubtitleText, modifier = Modifier.fillMaxSize())
        }
        if (subtitleOffsetMs != 0L && showSubtitles) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 110.dp, end = 16.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text(
                    text = "字幕偏移 ${if (subtitleOffsetMs > 0) "+" else ""}${subtitleOffsetMs}ms",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    modifier =
                        Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (isSeeking) {
            SeekingOverlay(seekPositionText = "跳转中…", modifier = Modifier.fillMaxSize())
        }
        if (showSpeedIndicator && playbackSpeed != 1.0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = 72.dp, end = 20.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(
                    text = "${playbackSpeed}x",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        if (showSeekPreview) {
            SeekPreviewOverlay(
                targetMs = seekPreviewPositionMs,
                deltaMs = seekDeltaMs,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showBrightnessIndicator) {
            BrightnessIndicator(brightness = brightness, modifier = Modifier.fillMaxSize())
        }
        if (showVolumeIndicator) {
            VolumeIndicator(volume = volume, modifier = Modifier.fillMaxSize())
        }
        if (isLocked) {
            LockIcon(
                onUnlock = {
                    viewModel.unlockScreen()
                    controlsVisible = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---- 控制层 ----
        if (controlsVisible && !isLocked) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // 顺序很重要：background 先铺满整屏 → clickable 覆盖全屏用于吞掉
                        // 穿透到手势层的点击 → 最后 safeDrawingPadding 只把**内容**收进安全区。
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.6f),
                                0.3f to Color.Transparent,
                                0.7f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.72f),
                            ),
                        ).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* 吞掉点击，避免穿透到手势层 */ }
                        // 四边安全区一次处理：横屏时状态栏/导航栏跑到侧边，
                        // 只加 bottom 内边距是不够的。
                        .safeDrawingPadding(),
            ) {
                TopControlsBar(
                    title = currentEpisodeName.ifEmpty { title },
                    subtitle =
                        if (episodeList.size > 1) {
                            "第 ${currentEpisodeIndex + 1} / ${episodeList.size} 集"
                        } else {
                            null
                        },
                    showEpisodeEntry = episodeList.size > 1,
                    hasSubtitles = subtitleTracks.isNotEmpty(),
                    speed = playbackSpeed,
                    isLandscape = isLandscape,
                    onBack = onBack,
                    onEpisodeClick = { showEpisodeSheet = true },
                    onSpeedClick = { showSpeedDialog = true },
                    onSubtitleClick = { showSubtitleDialog = true },
                    onRotateClick = {
                        orientationMode =
                            if (orientationMode == ScreenOrientationMode.LANDSCAPE) {
                                ScreenOrientationMode.PORTRAIT
                            } else {
                                ScreenOrientationMode.LANDSCAPE
                            }
                        controlsVisible = true
                    },
                    onMoreClick = { showMoreSheet = true },
                    modifier = Modifier.padding(horizontal = sidePadding),
                )

                Spacer(Modifier.weight(1f))

                PlayerProgressBar(
                    positionMs = position,
                    durationMs = duration,
                    bufferedPercentage = bufferedPercentage,
                    onSeek = { pos ->
                        viewModel.seekTo(pos)
                        controlsVisible = true
                    },
                    modifier = Modifier.padding(horizontal = sidePadding),
                )

                Spacer(Modifier.height(10.dp))

                BottomControlsBar(
                    playMode = playMode,
                    isPlaying = isPlaying,
                    hasPrevEpisode = hasPrevEpisode,
                    hasNextEpisode = hasNextEpisode,
                    skipInterval = skipInterval,
                    onPlayModeClick = {
                        val next =
                            when (playMode) {
                                PlayMode.SEQUENTIAL -> PlayMode.LOOP_ALL
                                PlayMode.LOOP_ALL -> PlayMode.LOOP_SINGLE
                                PlayMode.LOOP_SINGLE -> PlayMode.SEQUENTIAL
                            }
                        viewModel.setPlayMode(next)
                    },
                    onPrevEpisode = { viewModel.playPrevEpisode() },
                    onRewind = { viewModel.fastSeek(position - skipInterval * 1000L) },
                    onPlayPause = { viewModel.togglePlayPause() },
                    onForward = { viewModel.fastSeek(position + skipInterval * 1000L) },
                    onNextEpisode = { viewModel.playNextEpisode() },
                    modifier = Modifier.padding(horizontal = sidePadding),
                )

                Spacer(Modifier.height(14.dp))
            }
        }

        if (isBuffering && lastError == null && !isSeeking) {
            BufferingIndicator(
                bufferedPercentage = bufferedPercentage,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 96.dp),
                contentAlignment = Alignment.Center,
            )
        }

        if (lastError != null) {
            PlaybackErrorPanel(
                errorMessage = lastError.orEmpty(),
                onRetry = { viewModel.retryPlayback() },
                onPickAnother =
                    if (episodeList.size > 1) {
                        { showEpisodeSheet = true }
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // ---- 对话框 / 面板（放在 Box 之外，避免被控制层的 clickable 吞掉） ----

    if (showSpeedDialog) {
        SpeedSelectorDialog(
            speeds = speedOptions,
            currentSpeed = playbackSpeed,
            onSelect = { speed ->
                viewModel.setPlaybackSpeed(speed)
                showSpeedIndicator = true
            },
            onDismiss = { showSpeedDialog = false },
        )
    }

    if (showEpisodeSheet && episodeList.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showEpisodeSheet = false }) {
            EpisodeSheetContent(
                episodes = episodeList,
                currentIndex = currentEpisodeIndex,
                onSelect = { index ->
                    viewModel.playEpisodeAt(index)
                    showEpisodeSheet = false
                },
            )
        }
    }

    if (showSubtitleDialog) {
        SubtitleTrackSelectorDialog(
            tracks = subtitleTracks,
            currentTrack = currentSubtitleTrack,
            showSubtitles = showSubtitles,
            subtitleOffsetMs = subtitleOffsetMs,
            onSelectTrack = { index -> viewModel.setSubtitleTrack(index) },
            onDisable = { viewModel.toggleSubtitles() },
            onAdjustOffset = { viewModel.adjustSubtitleOffset(it) },
            onResetOffset = { viewModel.resetSubtitleOffset() },
            onDismiss = { showSubtitleDialog = false },
        )
    }

    if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
            PlayerMoreSheet(
                aspectMode = aspectMode,
                playMode = playMode,
                decodeLabel = appPreferences.decodeMode.label(),
                onAspectSelect = { viewModel.setAspectMode(it) },
                onPlayModeSelect = { viewModel.setPlayMode(it) },
                onLock = {
                    viewModel.lockScreen()
                    controlsVisible = false
                    showMoreSheet = false
                },
                onDismiss = { showMoreSheet = false },
            )
        }
    }
}

// ============================================================================
// 手势层
// ============================================================================

@Composable
private fun GestureLayer(
    onTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    onHorizontalDragStart: () -> Unit,
    onHorizontalDrag: (Float) -> Unit,
    onHorizontalDragEnd: () -> Unit,
    onVerticalDragStart: (isLeft: Boolean) -> Unit,
    onVerticalDrag: (isLeft: Boolean, delta: Float) -> Unit,
    onVerticalDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragStartPositionMs by remember { mutableStateOf(0L) }
    var isLeftSide by remember { mutableStateOf(false) }
    var accumulatedDx by remember { mutableStateOf(0f) }
    var accumulatedDy by remember { mutableStateOf(0f) }
    var axisDecided by remember { mutableStateOf(false) }
    var horizontalMode by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isLeftSide = offset.x < size.width / 2
                            accumulatedDx = 0f
                            accumulatedDy = 0f
                            axisDecided = false
                            horizontalMode = false
                            dragStartPositionMs = 0L
                        },
                        onDrag = { change, dragDelta ->
                            accumulatedDx += abs(dragDelta.x)
                            accumulatedDy += abs(dragDelta.y)
                            if (!axisDecided) {
                                if (accumulatedDx > 12f || accumulatedDy > 12f) {
                                    axisDecided = true
                                    horizontalMode = accumulatedDx > accumulatedDy
                                    if (horizontalMode) {
                                        onHorizontalDragStart()
                                    } else {
                                        onVerticalDragStart(isLeftSide)
                                    }
                                }
                            }
                            if (!axisDecided) return@detectDragGestures

                            if (horizontalMode) {
                                val screenWidth = size.width.toFloat().coerceAtLeast(1f)
                                // 用「当前累计位移占屏宽的比例」换算，而不是每次都拿绝对值算，
                                // 否则拖动过程中会反复基于起点计算，预览位置对不上手指
                                val fraction = (change.position.x - size.width / 2f) / screenWidth * 1.5f
                                onHorizontalDrag(fraction)
                            } else {
                                val screenHeight = size.height.toFloat().coerceAtLeast(1f)
                                onVerticalDrag(isLeftSide, -dragDelta.y / screenHeight)
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            if (horizontalMode) onHorizontalDragEnd()
                            onVerticalDragEnd()
                            axisDecided = false
                        },
                        onDragCancel = {
                            if (horizontalMode) onHorizontalDragEnd()
                            onVerticalDragEnd()
                            axisDecided = false
                        },
                    )
                }.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { offset ->
                            val w = size.width.toFloat()
                            when {
                                offset.x < w * DOUBLE_TAP_ZONE_FRACTION -> onDoubleTapLeft()
                                offset.x > w * (1f - DOUBLE_TAP_ZONE_FRACTION) -> onDoubleTapRight()
                                else -> onDoubleTapCenter()
                            }
                        },
                    )
                },
    )
}

// ============================================================================
// 拖动预览
// ============================================================================

@Composable
private fun SeekPreviewOverlay(
    targetMs: Long,
    deltaMs: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Text(
                text = formatDuration(targetMs),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            if (deltaMs != 0L) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = (if (deltaMs > 0) "+" else "-") + formatDuration(abs(deltaMs)),
                    color = Color(0xFF80CBC4),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ============================================================================
// 缓冲 / 错误
// ============================================================================

@Composable
private fun BufferingIndicator(
    bufferedPercentage: Int,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
) {
    Box(modifier = modifier, contentAlignment = contentAlignment) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
            if (bufferedPercentage > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "缓冲中 $bufferedPercentage%",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PlaybackErrorPanel(
    errorMessage: String,
    onRetry: () -> Unit,
    onPickAnother: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFFF7043),
                modifier = Modifier.size(46.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "播放失败",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = disguiseError(errorMessage),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.4f),
                        ),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重试")
                }
                if (onPickAnother != null) {
                    Button(onClick = onPickAnother) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("换一集")
                    }
                }
            }
        }
    }
}

/** 把 ExoPlayer 的英文原始错误转成用户能看懂的一句话 */
private fun disguiseError(raw: String): String =
    when {
        raw.contains("Source error", ignoreCase = true) ->
            "视频源拒绝了播放请求（可能是地址失效或防盗链）。"
        raw.contains("Unable to connect", ignoreCase = true) ||
            raw.contains("Failed to connect", ignoreCase = true) ->
            "网络连接失败，请检查网络后重试。"
        raw.contains("timeout", ignoreCase = true) -> "连接超时，稍后重试。"
        raw.contains("UnrecognizedInputFormat", ignoreCase = true) ||
            raw.contains("ParserException", ignoreCase = true) ->
            "这个地址不是可播放的视频流格式。"
        raw.length > 90 -> raw.take(90) + "…"
        else -> raw
    }

/** 给播放地址补上默认 Referer —— 部分 CDN 用 Referer 做防盗链校验 */
private fun buildPlaybackHeaders(url: String): Map<String, String>? {
    if (!url.startsWith("http", ignoreCase = true)) return null
    val origin =
        runCatching {
            val schemeEnd = url.indexOf("://")
            val hostEnd = url.indexOf('/', schemeEnd + 3)
            if (schemeEnd < 0 || hostEnd < 0) null else url.substring(0, hostEnd) + "/"
        }.getOrNull() ?: return null
    return mapOf("Referer" to origin)
}
