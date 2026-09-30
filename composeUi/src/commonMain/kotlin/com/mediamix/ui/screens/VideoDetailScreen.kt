package com.mediamix.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.VideoDetail
import com.mediamix.shared.models.VideoEpisode
import com.mediamix.shared.models.VideoItem
import com.mediamix.ui.components.ErrorContent
import com.mediamix.ui.components.LoadingScreen
import com.mediamix.ui.viewmodel.VideoDetailViewModel
import com.mediamix.ui.viewmodel.VideoHomeViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailScreen(
    vodId: String,
    sourceKey: String,
    viewModel: VideoDetailViewModel = koinInject(),
    homeViewModel: VideoHomeViewModel = koinInject(),
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

    val currentSite by homeViewModel.currentSite.collectAsState()
    val sites by homeViewModel.sites.collectAsState()

    var isContentExpanded by remember { mutableStateOf(false) }

    // Resolve API URL from sites
    val apiUrl = remember(sourceKey, sites) {
        sites.find { it.key == sourceKey }?.apiUrl
            ?: CmsApiSite.defaultSites.find { it.key == sourceKey }?.apiUrl
            ?: ""
    }

    // Load detail on first composition
    LaunchedEffect(vodId, sourceKey) {
        viewModel.loadDetail(vodId, sourceKey, apiUrl)
    }

    // Load related videos when detail is available
    LaunchedEffect(detail) {
        val d = detail
        if (d != null) {
            viewModel.loadRelated(apiUrl, d.typeId, d.vodId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u5f71\u7247\u8be6\u60c5") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                isLoading && detail == null -> LoadingScreen()
                error != null && detail == null -> ErrorContent(
                    message = error ?: "Unknown error",
                    onRetry = { viewModel.loadDetail(vodId, sourceKey, apiUrl) }
                )
                detail != null -> {
                    val d = detail!!
                    val sources = d.playSources
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Top: Cover + Basic info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            AsyncImage(
                                model = d.vodPic,
                                contentDescription = d.vodName,
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = d.vodName,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val typeName = d.typeName
                                if (typeName != null && typeName.isNotEmpty()) {
                                    InfoChip(icon = Icons.Filled.Category, text = typeName)
                                }
                                val year = d.vodYear
                                if (year != null && year.isNotEmpty()) {
                                    InfoChip(icon = Icons.Filled.CalendarToday, text = year)
                                }
                                val area = d.vodArea
                                if (area != null && area.isNotEmpty()) {
                                    InfoChip(icon = Icons.Filled.Public, text = area)
                                }
                                val remarks = d.vodRemarks
                                if (remarks != null && remarks.isNotEmpty()) {
                                    InfoChip(
                                        icon = Icons.Filled.Info,
                                        text = remarks,
                                        color = Color(0xFFFF9800)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description (expandable)
                        val descText = d.vodContent
                            ?.replace(Regex("<[^>]*>"), "")
                            ?.trim()
                        if (descText != null && descText.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "\u7b80\u4ecb",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.W600
                                    )
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { isContentExpanded = !isContentExpanded }
                                ) {
                                    Text(
                                        if (isContentExpanded) "\u6536\u8d77" else "\u5c55\u5f00",
                                        fontSize = 12.sp
                                    )
                                    Icon(
                                        imageVector = if (isContentExpanded)
                                            Icons.Filled.ExpandLess
                                        else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            AnimatedVisibility(visible = !isContentExpanded) {
                                Text(
                                    text = descText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )
                            }
                            AnimatedVisibility(visible = isContentExpanded) {
                                Text(
                                    text = descText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Actor
                        val actor = d.vodActor
                        if (actor != null && actor.isNotEmpty()) {
                            Text(
                                "\u6f14\u5458",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.W500
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = actor,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Director
                        val director = d.vodDirector
                        if (director != null && director.isNotEmpty()) {
                            Text(
                                "\u5bfc\u6f14",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.W500
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = director,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Play sources + Episodes
                        if (sources.isNotEmpty()) {
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            if (sources.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (idx in sources.indices) {
                                        item {
                                            val source = sources[idx]
                                            FilterChip(
                                                selected = selectedSourceIndex == idx,
                                                onClick = { viewModel.selectSource(idx) },
                                                label = {
                                                    Text(source.name, fontSize = 12.sp)
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            val currentEpisodes = sources[selectedSourceIndex].episodes
                            Text(
                                "\u9009\u96c6 (${currentEpisodes.size})",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.W600
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Episodes as wrapping chips
                            FlowRowCompat(
                                episodes = currentEpisodes,
                                onEpisodeClick = { epIndex ->
                                    val ep = currentEpisodes[epIndex]
                                    onNavigateToPlayer(ep.url, ep.name, epIndex)
                                }
                            )
                        }

                        if (sources.isEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Filled.PlayCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "\u6682\u65e0\u64ad\u653e\u6e90",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Related videos
                        if (relatedVideos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "\u76f8\u5173\u63a8\u8350",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.W600
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(relatedVideos) { item ->
                                    RelatedVideoCard(
                                        item = item,
                                        onClick = {
                                            onNavigateToDetail(
                                                item.vodId,
                                                item.sourceKey ?: sourceKey
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

// ============================================================
// FlowRow compatibility wrapper using Column + Row
// ============================================================

@Composable
private fun FlowRowCompat(
    episodes: List<VideoEpisode>,
    onEpisodeClick: (Int) -> Unit
) {
    val columns = 4
    val rows = (episodes.size + columns - 1) / columns
    for (row in 0 until rows) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (col in 0 until columns) {
                val index = row * columns + col
                if (index < episodes.size) {
                    val ep = episodes[index]
                    FilterChip(
                        selected = false,
                        onClick = { onEpisodeClick(index) },
                        label = {
                            Text(
                                text = ep.name,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ============================================================
// Info Chip
// ============================================================

@Composable
private fun InfoChip(
    icon: ImageVector,
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================
// Related Video Card
// ============================================================

@Composable
private fun RelatedVideoCard(
    item: VideoItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.vodPic,
            contentDescription = item.vodName,
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.vodName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
