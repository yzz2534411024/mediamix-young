package com.mediamix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediamix.ui.icons.AppIcons
import com.mediamix.ui.theme.Radius
import com.mediamix.ui.theme.Spacing

/**
 * 桌面端播放控制条（**位于视频区之外**）。
 *
 * 为什么不用覆盖式：桌面端视频渲染在 `SwingPanel{ AWT Canvas }` 上，而 AWT Canvas 是
 * **heavyweight 组件（独立原生 HWND）**，在 AWT 层级里永远盖在 Skia/Compose 之上 ——
 * 任何 Compose 绘制的控制层都看不见也点不到（实测整个播放页完全不可操作）。
 * AWT 层级无法逆转，因此桌面端把控制条放在视频区**下方**独立区域。
 * 移动端（TextureView 可被 Compose 覆盖）仍用覆盖式控制层。
 *
 * 视频区内另有 mpv 内置 OSC（`osc=yes`）作为沉浸式补充，两者共存。
 */
@Composable
fun DesktopPlayerBar(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedPercentage: Int,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }

        // 进度条（复用移动端同一组件，交互一致）
        PlayerProgressBar(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedPercentage = bufferedPercentage,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // 上一集 / 播放暂停 / 下一集
            IconAction(
                icon = AppIcons.SkipPrevious,
                contentDescription = "上一集",
                onClick = onPrevEpisode,
            )
            Box(
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(Radius.pill))
                        .padding(4.dp),
            ) {
                IconAction(
                    icon = if (isPlaying) AppIcons.Pause else AppIcons.PlayCircle,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    onClick = onTogglePlayPause,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            IconAction(
                icon = AppIcons.SkipNext,
                contentDescription = "下一集",
                onClick = onNextEpisode,
            )

            Text(
                text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}
