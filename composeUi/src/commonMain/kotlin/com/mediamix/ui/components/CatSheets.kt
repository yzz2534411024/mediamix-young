package com.mediamix.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mediamix.ui.theme.Radius
import com.mediamix.ui.theme.Spacing

/**
 * 底部面板外壳（选集 / 画质 / 倍速 / 音轨都用它）。
 *
 * 之前每个面板各写一遍 ModalBottomSheet，标题栏高度、内边距、圆角都不一样，
 * 切换时视觉上"跳"一下。统一到这里后，各面板只提供内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetShell(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            content()
            // 底部留出安全区，避免最后一列表项贴到系统手势条上
            Box(modifier = Modifier.padding(bottom = Spacing.xxl))
        }
    }
}

/**
 * 图标按钮：统一点击区（40dp）与按压反馈。
 *
 * 注意：必须给足点击区 —— 播放控制栏的图标原先只有图标本身大小（约 24dp），
 * 低于 40dp 在手机上很难点准。
 */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color? = null,
    badge: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconActionPress",
    )
    Box(
        modifier =
            modifier
                .size(40.dp)
                .scale(scale)
                .clip(RoundedCornerShape(Radius.pill))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        if (!badge.isNullOrBlank()) {
            RatingBadge(text = badge, modifier = Modifier.align(Alignment.TopEnd).padding(1.dp))
        }
    }
}

/**
 * 骨架屏网格：与真实网格同列数、同尺寸，加载完成时不发生布局跳动。
 *
 * 之前用一张静态灰块占位，数据回来后内容"啪"地弹出来，视觉上很突兀。
 */
@Composable
fun SkeletonGrid(
    columns: Int,
    modifier: Modifier = Modifier,
    itemCount: Int = columns * 2,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        repeat((itemCount + columns - 1) / columns) { rowIndex ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(columns) { colIndex ->
                    val index = rowIndex * columns + colIndex
                    if (index < itemCount) {
                        Column(modifier = Modifier.weight(1f)) {
                            SkeletonBlock(
                                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                shape = RoundedCornerShape(Radius.poster),
                            )
                            SkeletonBlock(
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                                shape = RoundedCornerShape(4.dp),
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
