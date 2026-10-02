package com.mediamix.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mediamix.ui.theme.CatTheme
import com.mediamix.ui.theme.Radius
import com.mediamix.ui.theme.Spacing

/**
 * 统一按钮：三态（主/次/文字）+ 统一尺寸与点击反馈。
 *
 * 之前各页面直接用 M3 的 Button / OutlinedButton / TextButton，尺寸、圆角、高度、
 * 按下反馈各不相同 —— 同一屏里三个按钮看起来像三个产品。这里收敛成一套。
 */
enum class CatButtonStyle { PRIMARY, SECONDARY, TEXT }

/** 统一按钮高度（保证一排按钮对齐）。 */
private val ButtonHeight = 44.dp

@Composable
fun CatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CatButtonStyle = CatButtonStyle.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    // 按下时轻微缩小（120ms 反馈），这是"手感"的主要来源
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonPress",
    )
    val shape = RoundedCornerShape(Radius.button)
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 2.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }

    val base = modifier.defaultMinSize(minHeight = ButtonHeight).scale(scale)

    when (style) {
        CatButtonStyle.PRIMARY ->
            Button(
                onClick = onClick,
                modifier = base,
                enabled = enabled,
                shape = shape,
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                interactionSource = interaction,
            ) { content() }

        CatButtonStyle.SECONDARY ->
            OutlinedButton(
                onClick = onClick,
                modifier = base,
                enabled = enabled,
                shape = shape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                interactionSource = interaction,
            ) { content() }

        CatButtonStyle.TEXT ->
            TextButton(
                onClick = onClick,
                modifier = base,
                enabled = enabled,
                shape = shape,
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                colors =
                    ButtonDefaults.textButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                interactionSource = interaction,
            ) { content() }
    }
}

/**
 * 胶囊标签 / 筛选项。
 *
 * 选中态用 `primaryContainer`（浅蓝底）而不是实心主色 —— 实心主色在一排 Chip 里
 * 太抢眼，选中项应该是"轻微强调"。
 */
@Composable
fun CatChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val bg =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val fg =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.padding(end = 2.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/**
 * 收藏按钮（强调色）。
 *
 * 用 [CatTheme.colors.accent]（图标耳内的淡粉）与功能蓝区分：这是"情感反馈"
 * 而不是"功能操作"，用户扫一眼就知道点它会发生什么。
 */
@Composable
fun CatFavoriteButton(
    favorited: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
) {
    // 收藏瞬间的"心跳"：缩放 1 → 1.25 → 1
    val scale by animateFloatAsState(
        targetValue = if (favorited) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "favoriteHeart",
    )
    val tint = if (favorited) CatTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            Icon(icon, contentDescription = if (favorited) "取消收藏" else "收藏", modifier = Modifier.scale(scale))
        }
    }
}
