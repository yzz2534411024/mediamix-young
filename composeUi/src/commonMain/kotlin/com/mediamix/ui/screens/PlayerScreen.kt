package com.mediamix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mediamix.shared.player.PlayMode
import com.mediamix.shared.player.PlayerState
import com.mediamix.ui.components.*
import com.mediamix.ui.platform.VideoSurface
import com.mediamix.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.abs

private const val HIDE_CONTROLS_DELAY_MS = 3000L
private const val SPEED_INDICATOR_DURATION_MS = 3000L
private const val DOUBLE_TAP_ZONE_FRACTION = 0.3f
private const val SKIP_INTERVAL = 10

@Composable
fun PlayerScreen(
    url: String = "",
    title: String = "",
    episodeIndex: Int = 0,
    viewModel: PlayerViewModel = koinInject(),
    onBack: () -> Unit = {}
) {
    // Collect all state from ViewModel
    val playerState by viewModel.playerState.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val bufferedPercentage by viewModel.bufferedPercentage.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val currentQualityIndex by viewModel.currentQualityIndex.collectAsState()
    val qualityLabels by viewModel.qualityLabels.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val isSeeking by viewModel.isSeeking.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val currentSubtitleTrack by viewModel.currentSubtitleTrack.collectAsState()
    val currentEpisodeName by viewModel.currentEpisodeName.collectAsState()
    val playMode by viewModel.playMode.collectAsState()
    val aspectMode by viewModel.aspectMode.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingText by viewModel.loadingText.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val hasPrevEpisode by viewModel.hasPrevEpisode.collectAsState()
    val hasNextEpisode by viewModel.hasNextEpisode.collectAsState()
    val showSubtitles by viewModel.showSubtitles.collectAsState()
    val speedOptions by viewModel.speedOptions.collectAsState()
    val currentSubtitleText by viewModel.currentSubtitleText.collectAsState()
    val subtitleOffsetMs by viewModel.subtitleOffsetMs.collectAsState()

    // UI-only state
    var controlsVisible by remember { mutableStateOf(true) }
    var showSpeedIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var seekPreviewPositionMs by remember { mutableStateOf(0L) }
    var showSeekPreview by remember { mutableStateOf(false) }

    // Dialog visibility state
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showAspectDialog by remember { mutableStateOf(false) }
    var showPowerDialog by remember { mutableStateOf(false) }
    var showSkipIntervalDialog by remember { mutableStateOf(false) }
    var showParserDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSubtitleOffsetDialog by remember { mutableStateOf(false) }

    // Gesture state
    var dragStartPositionMs by remember { mutableStateOf(0L) }
    var isDragLeft by remember { mutableStateOf(false) }
    var dragAccumulatedDx by remember { mutableStateOf(0f) }
    var dragAccumulatedDy by remember { mutableStateOf(0f) }
    var dragTypeDecided by remember { mutableStateOf(false) }
    var isHorizontalSeekDrag by remember { mutableStateOf(false) }

    val skipIntervals = remember { listOf(5, 10, 30, 60) }
    val isPlaying = playerState == PlayerState.PLAYING

    // Lifecycle: initialize + dispose tied to url identity
    DisposableEffect(url) {
        viewModel.initialize()
        if (url.isNotEmpty()) {
            viewModel.openVideo(url = url, title = title, episodeIndex = episodeIndex)
        }
        onDispose { viewModel.dispose() }
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(HIDE_CONTROLS_DELAY_MS)
            controlsVisible = false
        }
    }

    // Speed indicator auto-hide
    LaunchedEffect(showSpeedIndicator) {
        if (showSpeedIndicator) {
            delay(SPEED_INDICATOR_DURATION_MS)
            showSpeedIndicator = false
        }
    }

    val showControls: () -> Unit = { controlsVisible = true }

    // Main layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video rendering surface
        VideoSurface(
            modifier = Modifier.fillMaxSize(),
            onSurfaceCreated = { surface -> viewModel.setSurface(surface) },
            onSurfaceDestroyed = { viewModel.setSurface(null) }
        )

        // Gesture layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartPositionMs = position
                            isDragLeft = offset.x < size.width / 2
                            dragAccumulatedDx = 0f
                            dragAccumulatedDy = 0f
                            dragTypeDecided = false
                            isHorizontalSeekDrag = false
                        },
                        onDrag = { change, dragDelta ->
                            dragAccumulatedDx += abs(dragDelta.x)
                            dragAccumulatedDy += abs(dragDelta.y)

                            if (!dragTypeDecided) {
                                if (dragAccumulatedDx > 10f || dragAccumulatedDy > 10f) {
                                    dragTypeDecided = true
                                    isHorizontalSeekDrag = dragAccumulatedDx > dragAccumulatedDy
                                }
                            }

                            if (isHorizontalSeekDrag) {
                                val screenWidth = size.width.toFloat().coerceAtLeast(1f)
                                val durationMs = duration.toFloat().coerceAtLeast(1f)
                                val deltaMs = (dragAccumulatedDx / screenWidth) * durationMs
                                val sign = if (dragDelta.x >= 0) 1f else -1f
                                val newPos = (dragStartPositionMs + (sign * deltaMs).toLong())
                                    .coerceIn(0L, duration)
                                seekPreviewPositionMs = newPos
                                showSeekPreview = true
                                controlsVisible = false
                                change.consume()
                            } else if (dragTypeDecided) {
                                val screenHeight = size.height.toFloat().coerceAtLeast(1f)
                                val delta = -dragDelta.y / screenHeight
                                if (isDragLeft) {
                                    viewModel.setBrightness((brightness + delta).coerceIn(0f, 1f))
                                    showBrightnessIndicator = true
                                } else {
                                    viewModel.setVolume((volume + delta).coerceIn(0f, 1f))
                                    showVolumeIndicator = true
                                }
                                controlsVisible = false
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (isHorizontalSeekDrag && showSeekPreview) {
                                viewModel.fastSeek(seekPreviewPositionMs)
                            }
                            isHorizontalSeekDrag = false
                            showSeekPreview = false
                            showBrightnessIndicator = false
                            showVolumeIndicator = false
                            showControls()
                        },
                        onDragCancel = {
                            isHorizontalSeekDrag = false
                            showSeekPreview = false
                            showBrightnessIndicator = false
                            showVolumeIndicator = false
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val screenWidth = size.width.toFloat()
                            if (offset.x < screenWidth * DOUBLE_TAP_ZONE_FRACTION) {
                                viewModel.fastSeek(position - SKIP_INTERVAL * 1000L)
                            } else if (offset.x > screenWidth * (1f - DOUBLE_TAP_ZONE_FRACTION)) {
                                viewModel.fastSeek(position + SKIP_INTERVAL * 1000L)
                            } else {
                                viewModel.togglePlayPause()
                            }
                            showControls()
                        }
                    )
                }
        )

        // ================================================================
        // UI Overlay Layer
        // ================================================================

        // Subtitle overlay
        if (showSubtitles && currentSubtitleText != null) {
            SubtitleOverlay(
                subtitleText = currentSubtitleText,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Subtitle offset indicator
        if (subtitleOffsetMs != 0L && showSubtitles) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp, end = 16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "\u5B57\u5E55\u504F\u79FB: ${if (subtitleOffsetMs > 0) "+" else ""}${subtitleOffsetMs}ms",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Seeking indicator
        if (isSeeking) {
            SeekingOverlay(seekPositionText = "Seeking...", modifier = Modifier.fillMaxSize())
        }

        // Speed indicator
        if (showSpeedIndicator && playbackSpeed != 1.0f) {
            SpeedIndicator(speed = playbackSpeed, modifier = Modifier.fillMaxSize())
        }

        // Seek preview position
        if (showSeekPreview) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Black.copy(alpha = 0.87f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = formatDuration(seekPreviewPositionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            }
        }

        // Lock icon
        if (isLocked) {
            LockIcon(
                onUnlock = { viewModel.unlockScreen(); showControls() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Brightness/Volume indicators
        if (showBrightnessIndicator) {
            BrightnessIndicator(brightness = brightness, modifier = Modifier.fillMaxSize())
        }
        if (showVolumeIndicator) {
            VolumeIndicator(volume = volume, modifier = Modifier.fillMaxSize())
        }

        // Controls layer
        if (controlsVisible && !isLocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.26f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { /* consume */ })
                    }
            ) {
                TopControlsBar(
                    title = currentEpisodeName.ifEmpty { title },
                    playbackSpeed = playbackSpeed,
                    qualityLabels = qualityLabels,
                    currentQualityIndex = currentQualityIndex,
                    showSubtitles = showSubtitles,
                    subtitleTracks = subtitleTracks,
                    isLocked = isLocked,
                    onBack = onBack,
                    onSpeedClick = { showSpeedDialog = true },
                    onQualityClick = { showQualityDialog = true },
                    onSubtitleClick = {
                        if (subtitleTracks.size <= 1) showSubtitleOffsetDialog = true
                        else showSubtitleDialog = true
                    },
                    onMoreClick = { showMoreMenu = true },
                    onLockClick = { viewModel.lockScreen(); controlsVisible = false }
                )

                Spacer(modifier = Modifier.weight(1f))

                PlayerProgressBar(
                    positionMs = position,
                    durationMs = duration,
                    bufferedPercentage = bufferedPercentage,
                    onSeek = { pos -> viewModel.fastSeek(pos); showControls() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                BottomControlsBar(
                    playMode = playMode,
                    isPlaying = isPlaying,
                    hasPrevEpisode = hasPrevEpisode,
                    hasNextEpisode = hasNextEpisode,
                    skipInterval = SKIP_INTERVAL,
                    onPlayModeClick = {
                        val next = when (playMode) {
                            PlayMode.SEQUENTIAL -> PlayMode.LOOP_SINGLE
                            PlayMode.LOOP_SINGLE -> PlayMode.LOOP_ALL
                            PlayMode.LOOP_ALL -> PlayMode.SEQUENTIAL
                        }
                        viewModel.setPlayMode(next)
                        showControls()
                    },
                    onPrevEpisode = { viewModel.playPrevEpisode(); showControls() },
                    onRewind = { viewModel.fastSeek(position - SKIP_INTERVAL * 1000L); showControls() },
                    onPlayPause = { viewModel.togglePlayPause(); showControls() },
                    onForward = { viewModel.fastSeek(position + SKIP_INTERVAL * 1000L); showControls() },
                    onNextEpisode = { viewModel.playNextEpisode(); showControls() },
                    onSkipIntervalLongClick = { showSkipIntervalDialog = true }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Loading overlay
        if (isBuffering || isLoading) {
            LoadingOverlay(
                loadingText = loadingText,
                bufferedPercentage = bufferedPercentage,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Error overlay
        if (lastError != null) {
            ErrorOverlay(
                errorMessage = lastError!!,
                onRetry = { viewModel.retryPlayback() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // More menu
        if (showMoreMenu) {
            PlayerMoreMenuDialog(
                onDismiss = { showMoreMenu = false },
                onAspectClick = { showMoreMenu = false; showAspectDialog = true },
                onPowerClick = { showMoreMenu = false; showPowerDialog = true },
                onParserClick = { showMoreMenu = false; showParserDialog = true },
            )
        }
    }

    // ====================================================================
    // Dialogs (outside main Box)
    // ====================================================================

    if (showSpeedDialog) {
        SpeedSelectorDialog(
            speeds = speedOptions,
            currentSpeed = playbackSpeed,
            onSelect = { speed -> viewModel.setPlaybackSpeed(speed); showSpeedIndicator = true; showControls() },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showQualityDialog && qualityLabels.size > 1) {
        QualitySelectorDialog(
            labels = qualityLabels,
            currentIndex = currentQualityIndex,
            onSelect = { index -> if (index != currentQualityIndex) viewModel.switchQuality(index); showControls() },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showSubtitleDialog) {
        SubtitleTrackSelectorDialog(
            tracks = subtitleTracks,
            currentTrack = currentSubtitleTrack,
            showSubtitles = showSubtitles,
            onSelectTrack = { index -> viewModel.setSubtitleTrack(index); showControls() },
            onDisable = { viewModel.toggleSubtitles(); showControls() },
            onDismiss = { showSubtitleDialog = false }
        )
    }

    if (showAspectDialog) {
        AspectModeSelectorDialog(
            currentMode = aspectMode,
            onSelect = { mode -> viewModel.setAspectMode(mode); showControls() },
            onDismiss = { showAspectDialog = false }
        )
    }

    if (showPowerDialog) {
        val powerModes = listOf(
            "fullPerformance" to "High Performance",
            "balanced" to "Balanced",
            "powerSaving" to "Power Saving"
        )
        PowerModeSelectorDialog(
            powerModes = powerModes,
            currentMode = "balanced",
            onSelect = { _ -> showControls() },
            onDismiss = { showPowerDialog = false }
        )
    }

    if (showSkipIntervalDialog) {
        SkipIntervalSelectorDialog(
            intervals = skipIntervals,
            currentInterval = SKIP_INTERVAL,
            onSelect = { _ -> showControls() },
            onDismiss = { showSkipIntervalDialog = false }
        )
    }

    if (showParserDialog) {
        ParserSelectorDialog(
            parsers = emptyList(),
            currentParserKey = null,
            onSelect = { _ -> showControls() },
            onDismiss = { showParserDialog = false }
        )
    }

    if (showSubtitleOffsetDialog) {
        SubtitleOffsetDialog(
            subtitleOffsetMs = subtitleOffsetMs,
            showSubtitles = showSubtitles,
            onAdjust = { viewModel.adjustSubtitleOffset(it) },
            onReset = { viewModel.resetSubtitleOffset() },
            onToggleSubtitles = { viewModel.toggleSubtitles() },
            onDismiss = { showSubtitleOffsetDialog = false }
        )
    }
}
