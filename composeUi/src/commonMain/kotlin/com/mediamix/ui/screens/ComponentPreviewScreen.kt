package com.mediamix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediamix.ui.components.CatButton
import com.mediamix.ui.components.CatButtonStyle
import com.mediamix.ui.components.CatChip
import com.mediamix.ui.components.CatFavoriteButton
import com.mediamix.ui.components.CatLoading
import com.mediamix.ui.components.ContinueWatchingCard
import com.mediamix.ui.components.EmptyState
import com.mediamix.ui.components.ErrorState
import com.mediamix.ui.components.GradientScrim
import com.mediamix.ui.components.IconAction
import com.mediamix.ui.components.PosterCard
import com.mediamix.ui.components.RatingBadge
import com.mediamix.ui.components.SectionHeader
import com.mediamix.ui.components.SkeletonBlock
import com.mediamix.ui.components.SkeletonGrid
import com.mediamix.ui.icons.AppIcons
import com.mediamix.ui.theme.CatTheme
import com.mediamix.ui.theme.Elevation
import com.mediamix.ui.theme.Radius
import com.mediamix.ui.theme.Spacing

/**
 * 组件预览页。
 *
 * 存在的意义：改样式时**不用翻遍各个页面**就能看到效果 —— 所有组件在一屏内、
 * 双主题可直接对比。这是 S2 的验收工具，也是后续 S3 逐页重构时的"参照物"。
 *
 * 入口：设置 → 播放诊断 → 组件预览。
 */
@Composable
fun ComponentPreviewScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scroll),
    ) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconAction(icon = Icons.Filled.ArrowBack, contentDescription = "返回", onClick = onBack)
            Text(
                text = "组件预览",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        PreviewSection("配色") {
            ColorSwatch("primary", MaterialTheme.colorScheme.primary)
            ColorSwatch("onPrimary", MaterialTheme.colorScheme.onPrimary)
            ColorSwatch("primaryContainer", MaterialTheme.colorScheme.primaryContainer)
            ColorSwatch("surface", MaterialTheme.colorScheme.surface)
            ColorSwatch("surfaceVariant", MaterialTheme.colorScheme.surfaceVariant)
            ColorSwatch("outline", MaterialTheme.colorScheme.outline)
            ColorSwatch("error", MaterialTheme.colorScheme.error)
            ColorSwatch("accent（强调）", CatTheme.colors.accent)
            ColorSwatch("success", CatTheme.colors.success)
            ColorSwatch("warning", CatTheme.colors.warning)
        }

        PreviewSection("排版阶梯") {
            listOf(
                "CatDisplayTitle 28sp" to MaterialTheme.typography.displaySmall,
                "CatTitleLarge 22sp" to MaterialTheme.typography.titleLarge,
                "CatTitleMedium 17sp" to MaterialTheme.typography.titleMedium,
                "CatBody 14sp 正文示例" to MaterialTheme.typography.bodyMedium,
                "CatLabel 13sp 按钮标签" to MaterialTheme.typography.labelLarge,
                "CatCaption 12sp 集数/时长" to MaterialTheme.typography.bodySmall,
            ).forEach { (label, style) ->
                Text(label, style = style, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        PreviewSection("按钮") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CatButton("主要", onClick = {})
                CatButton("次要", onClick = {}, style = CatButtonStyle.SECONDARY)
                CatButton("文字", onClick = {}, style = CatButtonStyle.TEXT)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(top = Spacing.md),
            ) {
                CatButton("禁用", onClick = {}, enabled = false)
                CatChip("未选中", selected = false, onClick = {})
                CatChip("已选中", selected = true, onClick = {})
            }
        }

        PreviewSection("图标与角标") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(icon = AppIcons.Download, contentDescription = "下载", onClick = {})
                IconAction(icon = Icons.Filled.Star, contentDescription = "评分", onClick = {}, badge = "9.2")
                IconAction(icon = Icons.Filled.Settings, contentDescription = "设置", onClick = {}, tint = MaterialTheme.colorScheme.primary)
                CatFavoriteButton(favorited = true, onToggle = {}, icon = Icons.Filled.Favorite)
                CatFavoriteButton(favorited = false, onToggle = {}, icon = Icons.Filled.FavoriteBorder)
                RatingBadge("1080P")
            }
        }

        PreviewSection("海报卡片（2:3）") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(modifier = Modifier.size(width = 110.dp, height = 165.dp)) {
                    PosterCard(
                        title = "示例影片标题",
                        subtitle = "更新至 12 集",
                        badge = "1080P",
                        posterUrl = null,
                        onClick = {},
                    )
                }
            }
        }

        PreviewSection("继续观看") {
            ContinueWatchingCard(
                title = "示例影片标题",
                posterUrl = null,
                progress = 0.42f,
                episodeLabel = "看到第 8 集",
                onClick = {},
            )
        }

        PreviewSection("区块标题") {
            SectionHeader("继续观看", actionLabel = "全部", onAction = {})
        }

        PreviewSection("渐变遮罩") {
            Box(
                modifier =
                    Modifier
                        .size(width = 220.dp, height = 110.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Radius.poster)),
            ) { GradientScrim(modifier = Modifier.fillMaxSize()) }
        }

        PreviewSection("空态 / 错误态") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radius.card),
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            ) {
                EmptyState(
                    title = "还没有收藏",
                    description = "点击影片卡片右下角的星标即可收藏",
                    icon = Icons.Filled.FavoriteBorder,
                    actionLabel = "去逛逛",
                    onAction = {},
                    modifier = Modifier.height(200.dp),
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radius.card),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            ) {
                ErrorState(
                    title = "加载失败",
                    message = "网络连接失败，请检查网络后重试",
                    onRetry = {},
                    modifier = Modifier.height(200.dp),
                )
            }
        }

        PreviewSection("加载中 / 骨架屏") {
            SkeletonGrid(columns = 3, itemCount = 3, modifier = Modifier.height(200.dp))
            CatLoading(label = "正在加载…", modifier = Modifier.height(160.dp))
        }

        Box(modifier = Modifier.size(Spacing.xxxl))
    }
}

@Composable
private fun PreviewSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        SectionHeader(title)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.card),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.padding(Spacing.lg)) { content() }
        }
    }
}

@Composable
private fun ColorSwatch(name: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .background(color, RoundedCornerShape(6.dp)),
        )
        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "#${color.value.toString(16).takeLast(6).uppercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
