package com.mediamix.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediamix.ui.theme.CatTheme
import com.mediamix.ui.theme.Radius
import com.mediamix.ui.theme.Spacing

/**
 * 海报卡片（首页/搜索/收藏的网格单元）。
 *
 * 规格：2:3 竖版海报 + 底部渐变遮罩（保证角标文字在任何图片上都可读）+
 * 标题单行省略。角标（集数/清晰度）走 [RatingBadge]。
 */
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    // 按下时轻微缩小 + 海报区变暗，给出"点得动"的反馈
    val shape = RoundedCornerShape(Radius.card)
    Column(modifier = modifier.clip(shape).clickable(onClick = onClick)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(Radius.poster))
                    .background(CatTheme.colors.posterPlaceholder),
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 底部渐变：让角标与进度条在任何海报上都可读
            GradientScrim(modifier = Modifier.fillMaxSize())

            if (!badge.isNullOrBlank()) {
                RatingBadge(
                    text = badge,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
                )
            }
            if (progress != null && progress > 0f) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .size(width = 0.dp, height = 3.dp)
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 继续观看卡片：横向条 + 底部进度条（与 [PosterCard] 的区别就是比例与进度）。 */
@Composable
fun ContinueWatchingCard(
    title: String,
    posterUrl: String?,
    progress: Float,
    episodeLabel: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.card)
    Row(
        modifier =
            modifier
                .width(240.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable(onClick = onClick)
                .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 72.dp, height = 54.dp)
                    .clip(RoundedCornerShape(Radius.poster))
                    .background(CatTheme.colors.posterPlaceholder),
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!episodeLabel.isNullOrBlank()) {
                Text(
                    text = episodeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            // 进度条：2px 细条贴在卡片底部
            Box(
                modifier =
                    Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * 角标：清晰度 / 集数 / 更新标记。
 * 半透明深底 + 细描边，任何海报上都清晰。
 */
@Composable
fun RatingBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        maxLines = 1,
        modifier =
            modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = Spacing.sm, vertical = 2.dp),
    )
}

/**
 * 底部渐变遮罩：给图片下方的文字/角标提供可读性。
 * 上下方向：从透明到黑，用于海报与播放控制栏。
 */
@Composable
fun GradientScrim(
    modifier: Modifier = Modifier,
    strength: Float = 1f,
) {
    Box(
        modifier =
            modifier.background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Black.copy(alpha = 0.10f * strength),
                    1f to Color.Black.copy(alpha = 0.62f * strength),
                ),
            ),
    )
}

/**
 * 区块标题行：标题 + 可选"更多"入口。
 * 统一首页各区块的排版（之前每处自己写 Row + Text，间距各不相同）。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionLabel != null && onAction != null) {
            Box(modifier = Modifier.weight(1f))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .clickable(onClick = onAction)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    }
}
