package com.mediamix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediamix.shared.models.VideoDetail
import com.mediamix.shared.models.VideoEpisode
import com.mediamix.shared.models.VideoItem
import com.mediamix.ui.components.ErrorContent
import com.mediamix.ui.components.LoadingScreen
import com.mediamix.ui.player.PlaybackSessionStore
import com.mediamix.ui.viewmodel.VideoDetailViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoDetailScreen(
    vodId: String,
    sourceKey: String,
    viewModel: VideoDetailViewModel = koinInject(),
    sessionStore: PlaybackSessionStore = koinInject(),
    onNavigateToPlayer: (url: String, title: String, index: Int) -> Unit = { _, _, _ -> },
    onNavigateToDetail: (vodId: String, sourceKey: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    val detail by viewModel.detail.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val relatedVideos by viewModel.relatedVideos.collectAsState()
    val selectedSourceIndex by viewModel.selectedSourceIndex.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var isContentExpanded by remember { mutableStateOf(false) }
    var lastPlayedIndex by remember { mutableStateOf(0) }

    LaunchedEffect(vodId, sourceKey) { viewModel.loadDetail(vodId, sourceKey) }

    val d = detail
    val episodes = d?.playSources?.getOrNull(selectedSourceIndex)?.episodes.orEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = d?.vodName ?: "影片详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            tint = if (isFavorite) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (episodes.isNotEmpty()) {
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Button(
                        onClick = {
                            val index = lastPlayedIndex.coerceIn(0, episodes.lastIndex)
                            playEpisode(d, selectedSourceIndex, index, sessionStore, onNavigateToPlayer)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (episodes.size > 1) {
                                "播放 ${episodes.getOrNull(lastPlayedIndex)?.name ?: "第 1 集"}"
                            } else {
                                "立即播放"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                isLoading && d == null -> LoadingScreen()

                error != null && d == null -> ErrorContent(
                    message = error.orEmpty(),
                    onRetry = { viewModel.retry(vodId, sourceKey) },
                    title = "无法打开这部影片"
                )

                d != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 24.dp)
                ) {
                    Header(detail = d)

                    Spacer(Modifier.height(18.dp))

                    val descText = d.vodContent?.replace(Regex("<[^>]*>"), "")?.trim()
                    if (!descText.isNullOrEmpty()) {
                        SectionTitle("简介") {
                            TextButton(onClick = { isContentExpanded = !isContentExpanded }) {
                                Text(if (isContentExpanded) "收起" else "展开", fontSize = 12.sp)
                                Icon(
                                    imageVector = if (isContentExpanded) Icons.Filled.ExpandLess
                                    else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = descText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp,
                            maxLines = if (isContentExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(18.dp))
                    }

                    d.vodActor?.takeIf { it.isNotEmpty() }?.let { actor ->
                        MetaBlock("演员", actor, maxLines = 2)
                    }
                    d.vodDirector?.takeIf { it.isNotEmpty() }?.let { director ->
                        MetaBlock("导演", director, maxLines = 1)
                    }

                    if (d.playSources.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(14.dp))

                        if (d.playSources.size > 1) {
                            SectionTitle("播放线路")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(d.playSources.indices.toList()) { idx ->
                                    val selected = selectedSourceIndex == idx
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            viewModel.selectSource(idx)
                                            lastPlayedIndex = 0
                                        },
                                        label = { Text(d.playSources[idx].name, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        SectionTitle("选集 · ${episodes.size}")

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            episodes.forEachIndexed { index, episode ->
                                EpisodeChip(
                                    episode = episode,
                                    selected = index == lastPlayedIndex,
                                    onClick = {
                                        lastPlayedIndex = index
                                        playEpisode(d, selectedSourceIndex, index, sessionStore, onNavigateToPlayer)
                                    }
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(20.dp))
                        EmptyPlaySourceHint()
                    }

                    if (relatedVideos.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(14.dp))
                        SectionTitle("相关推荐")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(relatedVideos, key = { it.vodId }) { item ->
                                RelatedVideoCard(
                                    item = item,
                                    onClick = { onNavigateToDetail(item.vodId, item.sourceKey ?: sourceKey) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun playEpisode(
    detail: VideoDetail?,
    sourceIndex: Int,
    episodeIndex: Int,
    sessionStore: PlaybackSessionStore,
    onNavigateToPlayer: (url: String, title: String, index: Int) -> Unit
) {
    val d = detail ?: return
    val episode = d.playSources.getOrNull(sourceIndex)?.episodes?.getOrNull(episodeIndex) ?: return
    // 先把整份剧集列表交给会话仓库，播放页才能支持选集 / 上下集 / 失败换线
    sessionStore.start(d, sourceIndex, episodeIndex)
    onNavigateToPlayer(episode.url, episode.name, episodeIndex)
}

// ============================================================================
// 子组件
// ============================================================================

@Composable
private fun Header(detail: VideoDetail) {
    Row(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = detail.vodPic,
            contentDescription = detail.vodName,
            modifier = Modifier
                .width(112.dp)
                .height(158.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.vodName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))

            val chips = listOfNotNull(
                detail.typeName?.takeIf { it.isNotEmpty() }?.let { Icons.Filled.Category to it },
                detail.vodYear?.takeIf { it.isNotEmpty() }?.let { Icons.Filled.CalendarToday to it },
                detail.vodArea?.takeIf { it.isNotEmpty() }?.let { Icons.Filled.Public to it },
            )
            chips.forEach { (icon, text) -> InfoChip(icon, text) }

            detail.vodRemarks?.takeIf { it.isNotEmpty() }?.let { remarks ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = remarks,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun MetaBlock(label: String, value: String, maxLines: Int) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpisodeChip(
    episode: VideoEpisode,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.height(34.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = episode.name,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun EmptyPlaySourceHint() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "这个源没有提供播放地址",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "换一个数据源再试试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RelatedVideoCard(item: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.vodPic,
            contentDescription = item.vodName,
            modifier = Modifier
                .fillMaxWidth()
                .height(138.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.vodName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
