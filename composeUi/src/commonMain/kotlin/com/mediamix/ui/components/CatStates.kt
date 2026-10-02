package com.mediamix.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mediamix.ui.theme.CatTheme
import com.mediamix.ui.theme.Radius
import com.mediamix.ui.theme.Spacing

/**
 * 空态：插画/图标 + 说明 + 可选行动按钮。
 *
 * 全应用有 9 处空态，之前每处自己写居中 Column，文案层级和留白不一致
 * （有的把按钮放很下面、有的没有说明文字），统一到这里。
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (icon != null) Spacing.lg else 0.dp),
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        if (actionLabel != null && onAction != null) {
            CatButton(
                text = actionLabel,
                onClick = onAction,
                style = CatButtonStyle.SECONDARY,
                modifier = Modifier.padding(top = Spacing.xl),
            )
        }
    }
}

/**
 * 骨架屏块：shimmer 扫过。
 *
 * 之前只有一张静态灰块（没有扫动），用户不确定是"加载中"还是"卡死"。
 * 这里用 1200ms 循环渐变，视觉上明确表达"还在来"。
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(Radius.card),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmerProgress",
    )
    val base = CatTheme.colors.posterPlaceholder
    val highlight = base.copy(alpha = 0.55f)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = androidx.compose.ui.geometry.Offset(progress * 800f - 400f, 0f),
                        end = androidx.compose.ui.geometry.Offset(progress * 800f, 400f),
                    ),
                ),
    )
}

/** 骨架屏网格项：2:3 海报 + 下方两行文字占位（与真实 [PosterCard] 同尺寸，避免加载完跳动）。 */
@Composable
fun SkeletonGridItem(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SkeletonBlock(
            modifier = Modifier.fillMaxWidth().size(width = 0.dp, height = 0.dp).then(Modifier),
            shape = RoundedCornerShape(Radius.poster),
        )
    }
}

/** 加载中（带文字，比裸 CircularProgressIndicator 更明确）。 */
@Composable
fun CatLoading(
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(32.dp),
        )
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }
    }
}

/**
 * 错误态：图标 + 原因 + 重试。
 * 与 [EmptyState] 区分：错误要给出「可重试」的出路，不能只是显示"出错了"。
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "出错了",
    icon: ImageVector? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "重试",
) {
    EmptyState(
        title = title,
        description = message,
        icon = icon,
        actionLabel = if (onRetry != null) retryLabel else null,
        onAction = onRetry,
        modifier = modifier,
    )
}
