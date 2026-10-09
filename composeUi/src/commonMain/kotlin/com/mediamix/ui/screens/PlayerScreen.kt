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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediamix.shared.player.AspectMode
import com.mediamix.shared.player.PlayMode
import com.mediamix.shared.player.PlayerState
import com.mediamix.ui.components.DesktopPlayerBar
import com.mediamix.ui.components.*
import com.mediamix.ui.platform.ApplyScreenBrightness
import com.mediamix.ui.platform.ApplyScreenOrientation
import com.mediamix.ui.platform.ScreenOrientationMode
import com.mediamix.ui.platform.VideoSurface
import com.mediamix.shared.core.PlatformInfo
import com.mediamix.ui.platform.onPointerActivity
import com.mediamix.ui.player.PlaybackSessionStore
import com.mediamix.ui.prefs.AppPreferences
import com.mediamix.ui.prefs.label
import com.mediamix.ui.viewmodel.DownloadViewModel
import com.mediamix.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.abs
import com.mediamix.ui.icons.AppIcons

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
    downloadViewModel: DownloadViewModel = koinInject(),
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
    // 视频真实宽高比（Android 用它约束画面尺寸；桌面端恒为 0，由 mpv 自理）
    val videoAspectRatio by viewModel.videoAspectRatio.collectAsState()
    // 首次播放组件准备（桌面端 mpv 按需下载）的进度；null = 无需准备
    val runtimeProgress by viewModel.runtimeProgress.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    // 把亮度状态真正落到屏幕（Android 走窗口亮度，离开播放页自动恢复系统亮度）。
    // 此前亮度只存在于状态里，手势只有动画没有效果（用户实测反馈）。
    ApplyScreenBrightness(brightness)
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
    val audioTracks by viewModel.audioTracks.collectAsState()
    val videoTracks by viewModel.videoTracks.collectAsState()
    val qualityLabels by viewModel.qualityLabels.collectAsState()
    val currentQualityIndex by viewModel.currentQualityIndex.collectAsState()

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
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showVideoTrackDialog by remember { mutableStateOf(false) }

    val isPlaying = playerState == PlayerState.PLAYING
    val resumePositionMs by viewModel.resumePositionMs.collectAsState()
    // 会话查找键同时接受「剧集标识」与「解析后的地址」：TVBox 场景下导航参数是
    // 解析结果，而 sessionFor 用 resolveKey 对齐 —— 旧实现只比 startUrl 会静默拿不到会话。
    val sessionState by sessionStore.session.collectAsState()
    val session = sessionState

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

    // 会话在解析出结果后会被改写（写入 resolved），从而触发重组。
    // 若不记住「已经装载过的目标」，这个 effect 会再跑一遍 openVideo ——
    // 那等于把播放器重新 initialize 一次（重建协程作用域、重置指标与计时器），
    // 用户看到的是「刚开始播就闪一下重来」。
    //
    // ⚠️ key 只能用**本页不变**的值（导航参数）：不能用 session.resolveKey ——
    // 它会随解析结果回写而变化，反而会让同一次导航被当成「新目标」再装一次。
    var openedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url, episodeIndex) {
        if (url.isEmpty()) return@LaunchedEffect
        val s = session
        val openKey = "$url#$episodeIndex"
        if (openedKey == openKey) return@LaunchedEffect
        // TVBox 会话：先确保「要播的这一集」已解析出真实地址与请求头，再交给播放器。
        val startResolved =
            if (s != null && s.isResolvable && s.sourceKey.isNotBlank()) {
                viewModel.resolveEpisode(s, episodeIndex)
            } else {
                null
            }
        val playUrl = startResolved?.url ?: url
        val playHeaders =
            if (s?.isResolvable == true) startResolved?.headers else s?.headers
        openedKey = openKey
        viewModel.openVideo(
            url = playUrl,
            title = title,
            episodeIndex = episodeIndex,
            episodeNames = s?.episodeNames,
            // TVBox 的 episodeUrls 必须是**待解析标识**：切集时交给 episodeResolver 换地址。
            episodeUrls = s?.episodeUrls,
            // 优先用会话里的真实请求头（TVBox 蜘蛛 playerContent 给出）；
            // 没有时才退回按 URL 猜 Referer 的兜底逻辑。
            headers = playHeaders ?: buildPlaybackHeaders(playUrl),
            preferSoftwareDecoding = appPreferences.preferSoftwareDecoding,
            fallbackUrls = s?.fallbackUrlsFor(episodeIndex),
        )
    }

    // TVBox 切集/连播：把「待解析标识」按需换成真实地址与请求头。
    // 装在 PlayerCoreManager 上而不是这里，是因为自动连播发生在管理器内部。
    LaunchedEffect(viewModel, session) {
        viewModel.installEpisodeResolver(session)
    }

    var activityTick by remember { mutableIntStateOf(0) }

    // 控制栏自动隐藏：拖拽/预览/锁定时不隐藏。
    // `activityTick` 由「鼠标移动」（桌面）或点按递增 —— 它变化会重启这个计时器，
    // 否则 controlsVisible 已是 true 时再置 true 不触发重组，计时不会被重置。
    //
    // ⚠️ 仅 Android（触摸设备）自动隐藏：桌面端鼠标操作下改为**常显** ——
    // 隐藏后只能靠鼠标移动/点按召回，实测用户直接判定成「播放页没有任何组件」。
    // 桌面播放器（VLC/mpv 等）也都是控制栏常显。
    LaunchedEffect(controlsVisible, showSeekPreview, isLocked, activityTick) {
        if (controlsVisible && !showSeekPreview && !isLocked && PlatformInfo.isAndroid) {
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

    // ── 全覆盖沉浸式（桌面 + 移动统一）──
    //
    // 桌面端播放控制由 mpv 内置 OSC 承担（osc=yes + AWT 鼠标事件转发 osc-show），
    // 它画在视频帧内，不受 AWT Canvas 层级遮挡。
    // 键盘快捷键（Esc/Space/←→）需要组合内持有焦点：进入页面主动 requestFocus。
    val keyboardFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!PlatformInfo.isAndroid) keyboardFocus.requestFocus()
    }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                // 桌面端键盘控制：鼠标事件会被 AWT Canvas 吞掉，但键盘事件能到 Compose。
                // Esc=返回 / Space=播放暂停 / ←→=快退快进
                .focusRequester(keyboardFocus)
                .focusable()
                .then(
                    if (!PlatformInfo.isAndroid) {
                        Modifier.onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.Escape -> { onBack(); true }
                                Key.Spacebar -> { viewModel.togglePlayPause(); true }
                                Key.DirectionLeft -> { viewModel.fastSeek(position - skipInterval * 1000L); true }
                                Key.DirectionRight -> { viewModel.fastSeek(position + skipInterval * 1000L); true }
                                else -> false
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .onPointerActivity {
                    if (!controlsVisible) controlsVisible = true
                    activityTick++
                },
    ) {
        // 用容器宽高比判断横竖屏，而不是读平台配置 —— commonMain 拿不到
        // Android 的 LocalConfiguration，桌面端也没有"屏幕方向"这个概念。
        val isLandscape = maxWidth > maxHeight
        // 横屏时控制层远离屏幕边缘（单手操作 / 刘海 / 圆角都不易误触），竖屏贴边即可
        val sidePadding = if (isLandscape) 24.dp else 4.dp

        // 画面比例约束：
        //  - 桌面端交给 mpv 原生处理（keepaspect / panscan / video-aspect-override），
        //    这里保持填满画布即可；
        //  - Android 的 TextureView **不会**自动适配视频比例，不约束就是默认拉伸填满
        //    （实测用户反馈「自适应比例太大」的根因）。
        val targetRatio = if (videoAspectRatio > 0f) videoAspectRatio else 16f / 9f
        val containerRatio =
            if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            VideoSurface(
                onMouseActivity = { viewModel.notifyMouseActivity() },
                modifier =
                    when {
                        viewModel.engineHandlesAspect -> Modifier.fillMaxSize()

                        // 铺满裁剪：按视频比例放大到覆盖容器，溢出部分被上面裁掉
                        aspectMode == AspectMode.CROP ->
                            if (targetRatio >= containerRatio) {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(targetRatio)
                            } else {
                                Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(targetRatio)
                            }

                        // 拉伸铺满：接受画面变形
                        aspectMode == AspectMode.STRETCH -> Modifier.fillMaxSize()

                        aspectMode == AspectMode.RATIO_16_9 -> Modifier.aspectRatio(16f / 9f)

                        aspectMode == AspectMode.RATIO_4_3 -> Modifier.aspectRatio(4f / 3f)

                        aspectMode == AspectMode.RATIO_21_9 -> Modifier.aspectRatio(21f / 9f)

                        // 自适应 / 原始比例：等比完整显示，多余空间留黑边
                        else -> Modifier.aspectRatio(targetRatio)
                    },
                onSurfaceCreated = { surface -> viewModel.setSurface(surface) },
                onSurfaceDestroyed = { viewModel.setSurface(null) },
            )
        }

        // ---- 首次播放组件准备（桌面端 mpv 按需下载）----
        // 放在最上层：这段时间里既没有画面也没有控制栏，必须给出明确反馈，
        // 否则用户看到的就是与「白屏 bug」一模一样的长时间黑屏。
        runtimeProgress?.let { p ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("正在准备播放组件", color = Color.White)
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.width(260.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "首次播放需下载约 45 MB，之后不再重复下载 · ${(p * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        GestureLayer(
            onTap = { controlsVisible = !controlsVisible },
            onLongPressStart = { viewModel.startLongPressSpeed() },
            onLongPressEnd = { viewModel.stopLongPressSpeed() },
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

        // ---- 控制层（仅移动端）----
        // 桌面端由 mpv 内置 OSC 承担播放控制：Compose 绘制的控制层会被 AWT Canvas
        // 完全遮住且收不到鼠标事件（见文件头说明），所以这里只给移动端渲染。
        if (controlsVisible && !isLocked && PlatformInfo.isAndroid) {
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

    // ---- 断点续播确认 ----
    resumePositionMs?.let { savedPos ->
        androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.startFromBeginning() },
                title = { Text("继续播放？") },
                text = {
                    Text(
                        "上次看到 " + formatDuration(savedPos) +
                            "，是否从上次位置继续？（播放进度来自本地记录）",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.resumeFromSaved() }) { Text("继续播放") }
                },
            dismissButton = {
                TextButton(onClick = { viewModel.startFromBeginning() }) { Text("从头开始") }
            },
        )
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
                audioTracks = audioTracks,
                videoTracks = videoTracks,
                qualityLabels = qualityLabels,
                currentQualityIndex = currentQualityIndex,
                onOpenAudioTracks = {
                    showMoreSheet = false
                    showAudioTrackDialog = true
                },
                onOpenVideoTracks = {
                    showMoreSheet = false
                    showVideoTrackDialog = true
                },
                onQualitySelect = { index ->
                    viewModel.switchQuality(index)
                    showMoreSheet = false
                },
                onDownload = {
                    showMoreSheet = false
                    val downloadUrl = viewModel.currentPlayableUrl()
                    if (downloadUrl.isNotBlank()) {
                        // 把会话里解析出的**真实请求头**一起交给下载器：
                        // 防盗链 CDN 缺了它必然 403，这正是「下载下来的文件打不开」的主因。
                        downloadViewModel.addDownload(
                            vodId = session?.vodId.orEmpty(),
                            vodName = session?.vodName ?: title,
                            episodeName = session?.episodes?.getOrNull(currentEpisodeIndex)?.name ?: title,
                            videoUrl = downloadUrl,
                            headers = session?.startHeaders.orEmpty(),
                        )
                    }
                },
            )
        }
    }

    if (showAudioTrackDialog) {
        TrackSelectorDialog(
            title = "音轨",
            tracks = audioTracks,
            onSelect = { viewModel.selectAudioTrack(it.id) },
            onDismiss = { showAudioTrackDialog = false },
        )
    }

    if (showVideoTrackDialog) {
        TrackSelectorDialog(
            title = "视频轨",
            tracks = videoTracks,
            emptyHint = "当前媒体没有可切换的视频轨（画质请用「画质」入口）",
            onSelect = { viewModel.selectVideoTrack(it.id) },
            onDismiss = { showVideoTrackDialog = false },
        )
    }
}

// ============================================================================
// 手势层
// ============================================================================

@Composable
private fun GestureLayer(
    onTap: () -> Unit,
    /** 长按 3 倍速：按下触发、松手恢复（主流播放器交互）。null=不支持。 */
    onLongPressStart: (() -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
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
                        // 长按 3 倍速：长按阈值触发 start，松手无条件通知 end
                        //（stopLongPressSpeed 内部按"当前是否 3x"幂等恢复，未长按时 no-op）
                        onPress = {
                            // this = PressGestureScope：挂起至松手/取消，随后幂等恢复倍速
                            tryAwaitRelease()
                            onLongPressEnd?.invoke()
                        },
                        onLongPress = { onLongPressStart?.invoke() },
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
                    color = Color.White,
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
                imageVector = AppIcons.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
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
                        Icon(AppIcons.PlaylistPlay, contentDescription = null, modifier = Modifier.size(16.dp))
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
