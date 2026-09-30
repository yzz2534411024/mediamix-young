package com.mediamix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.VideoCategory
import com.mediamix.shared.models.VideoItem
import com.mediamix.ui.components.ErrorContent
import com.mediamix.ui.components.SkeletonCard
import com.mediamix.ui.viewmodel.VideoHomeViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoHomeScreen(
    viewModel: VideoHomeViewModel = koinInject(),
    onNavigateToDetail: (vodId: String, sourceKey: String) -> Unit = { _, _ -> },
    onNavigateToSearch: () -> Unit = {}
) {
    val sites by viewModel.sites.collectAsState()
    val currentSite by viewModel.currentSite.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()

    val gridState = rememberLazyGridState()

    // Cache filtered lists to avoid recomputation on every recomposition
    val enabledSites = remember(sites) { sites.filter { it.enabled } }
    val topCategories = remember(categories) { categories.filter { it.typePid == 0 } }

    // Initial load
    LaunchedEffect(Unit) {
        viewModel.loadSites()
        if (viewModel.currentSite.value != null) {
            viewModel.loadCategories()
            viewModel.loadVideos()
        }
    }

    // Infinite scroll: detect near bottom
    val shouldLoadMore = remember(gridState.layoutInfo, isLoading, hasMore) {
        if (!hasMore || isLoading) return@remember false
        val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = gridState.layoutInfo.totalItemsCount
        lastVisibleIndex >= totalItems - 6
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MediaMix") },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Site switching row
            if (enabledSites.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(enabledSites) { site ->
                        FilterChip(
                            selected = currentSite?.key == site.key,
                            onClick = { viewModel.selectSite(site) },
                            label = { Text(site.name, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Category navigation row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("\u5168\u90E8", fontSize = 12.sp) }
                    )
                }
                for (cat in topCategories) {
                    item {
                        FilterChip(
                            selected = selectedCategory?.typeId == cat.typeId,
                            onClick = { viewModel.selectCategory(cat) },
                            label = { Text(cat.typeName, fontSize = 12.sp) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Content area
            when {
                isLoading && videos.isEmpty() -> {
                    SkeletonContent()
                }
                error != null && videos.isEmpty() -> {
                    ErrorContent(
                        message = error ?: "Unknown error",
                        onRetry = { viewModel.refresh() }
                    )
                }
                videos.isEmpty() && !isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Movie,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "\u6682\u65e0\u5f71\u7247\u6570\u636e",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    val showBanner = videos.size >= 2 && selectedCategory == null
                    val bannerItems = if (showBanner) videos.take(5) else emptyList()

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (showBanner) {
                            item(span = { GridItemSpan(3) }) {
                                BannerSection(
                                    items = bannerItems,
                                    sourceKey = currentSite?.key ?: "",
                                    onNavigateToDetail = onNavigateToDetail
                                )
                            }
                        }

                        items(videos, key = { it.vodId }) { item ->
                            VideoGridCard(
                                item = item,
                                onClick = {
                                    onNavigateToDetail(item.vodId, currentSite?.key ?: "")
                                }
                            )
                        }

                        if (isLoading && videos.isNotEmpty()) {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerSection(
    items: List<VideoItem>,
    sourceKey: String,
    onNavigateToDetail: (vodId: String, sourceKey: String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000L)
            val nextPage = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onNavigateToDetail(item.vodId, sourceKey) }
            ) {
                AsyncImage(
                    model = item.vodPic,
                    contentDescription = item.vodName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                ),
                                startY = 200f
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = item.vodName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val typeName = item.typeName
                        if (typeName != null && typeName.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = typeName,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val remarks = item.vodRemarks
                        if (remarks != null && remarks.isNotEmpty()) {
                            Text(
                                text = remarks,
                                color = Color(0xFFFFCC80),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEachIndexed { index, _ ->
                val isActive = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (isActive) 16.dp else 8.dp, height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

@Composable
private fun VideoGridCard(
    item: VideoItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            AsyncImage(
                model = item.vodPic,
                contentDescription = item.vodName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = item.vodName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.W500
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val remarks = item.vodRemarks
                if (!remarks.isNullOrEmpty()) {
                    Text(
                        text = remarks,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE65100),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonContent() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(3) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        items(9) {
            SkeletonCard(modifier = Modifier.fillMaxWidth())
        }
    }
}
