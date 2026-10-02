package com.mediamix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.mediamix.shared.models.VideoItem
import com.mediamix.ui.components.ErrorContent
import com.mediamix.ui.components.SkeletonCard
import com.mediamix.ui.platform.horizontalWheelScroll
import com.mediamix.ui.viewmodel.VideoHomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.koinInject
import androidx.compose.foundation.lazy.grid.items as gridItems
import com.mediamix.ui.icons.AppIcons

/** 骨架屏用的低列数（真实网格按宽度自适应，见 [CARD_MIN_WIDTH_DP]）。 */
private const val GRID_COLUMNS = 3

/** 影片卡片的最小宽度：网格列数 = 可用宽度 / 此值（手机 3 列、桌面约 7 列）。 */
private const val CARD_MIN_WIDTH_DP = 165

private const val BANNER_HEIGHT_DP = 168

/**
 * 首页。
 *
 * 版式（自上而下）：标题栏 → 数据源 → 分类 → 提示条 → 影片网格。
 *
 * 原来的实现在 [MainScaffold] 之外又套了一层带 TopAppBar 的 Scaffold，
 * 加上 MainScaffold 自己的内边距，会出现「两个 MediaMix 标题 + 大块空白」。
 * 这里只保留一个 topBar，所有间距由内容自己控制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoHomeScreen(
    viewModel: VideoHomeViewModel = koinInject(),
    onNavigateToDetail: (vodId: String, sourceKey: String) -> Unit = { _, _ -> },
    onNavigateToSearch: () -> Unit = {},
) {
    val sites by viewModel.sites.collectAsState()
    val currentSite by viewModel.currentSite.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val siteNodes by viewModel.siteNodes.collectAsState()
    val selectedSiteNode by viewModel.selectedSiteNode.collectAsState()
    val siteClasses by viewModel.siteClasses.collectAsState()
    val selectedClass by viewModel.selectedClass.collectAsState()
    val isTvBoxSource by viewModel.isTvBoxSource.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val unsupported by viewModel.isSourceUnsupported.collectAsState()

    val gridState = rememberLazyGridState()
    val enabledSites = remember(sites) { sites.filter { it.enabled } }

    LaunchedEffect(Unit) { viewModel.loadSites() }

    // 触底分页：用 snapshotFlow 代替「在组合里算布尔值」——后者只在重组时求值，
    // 用户停手不滚动时不会触发，最后几屏经常加载不出来。
    LaunchedEffect(gridState, hasMore) {
        snapshotFlow {
            val last =
                gridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 4
        }.distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMore() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("CatVideo", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            SourceRow(
                sites = enabledSites,
                currentSite = currentSite,
                onSelect = { viewModel.selectSite(it) },
            )

            // TVBox 源：两级目录（站点 → 站内分类）。旧实现把 47 个站点当成
            // CMS 的「分类」并以下标伪装 typeId，导致点任何一项都只看到首页推荐。
            if (isTvBoxSource && siteNodes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                SiteNodeRow(
                    sites = siteNodes,
                    selectedKey = selectedSiteNode?.key,
                    onSelect = { viewModel.selectSiteNode(it) },
                )
                if (selectedSiteNode != null && siteClasses.isNotEmpty()) {
                    ClassRow(
                        classes = siteClasses,
                        selectedId = selectedClass?.typeId,
                        onSelect = { viewModel.selectClass(it) },
                    )
                }
            }

            if (!isTvBoxSource && categories.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                CategoryRow(
                    categories = categories,
                    selectedId = selectedCategory?.typeId,
                    onSelect = { viewModel.selectCategory(it) },
                )
            }

            notice?.let { text ->
                NoticeBanner(
                    text = text,
                    showSwitchAction = unsupported,
                    onSwitchSource = {
                        viewModel.switchToAvailableSource()
                        viewModel.dismissNotice()
                    },
                    onDismiss = { viewModel.dismissNotice() },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            HomeContent(
                videos = videos,
                isLoading = isLoading,
                hasMore = hasMore,
                error = error,
                hasSource = currentSite != null,
                gridState = gridState,
                onRetry = { viewModel.refresh() },
                onSwitchSource = { viewModel.switchToAvailableSource() },
                onLoadMore = { viewModel.loadMore() },
                onOpenDetail = { item ->
                    // 优先用条目自带 sourceKey（TVBox 是 `配置源::站点` 复合标识，
                    // 详情页据此找回蜘蛛）；缺失时退回当前源 key。
                    onNavigateToDetail(item.vodId, item.sourceKey ?: currentSite?.key.orEmpty())
                },
            )
        }
    }
}

// ============================================================================
// 数据源
// ============================================================================

@Composable
private fun SourceRow(
    sites: List<CmsApiSite>,
    currentSite: CmsApiSite?,
    onSelect: (CmsApiSite) -> Unit,
) {
    if (sites.isEmpty()) return
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalWheelScroll(state)
                .padding(top = 2.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(sites, key = { it.key }) { site ->
            val selected = currentSite?.key == site.key
            FilterChip(
                selected = selected,
                onClick = { onSelect(site) },
                label = {
                    Text(
                        text = site.name,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                shape = RoundedCornerShape(8.dp),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

// ============================================================================
// TVBox 两级目录
// ============================================================================

/**
 * 一级目录：TVBox 配置里的站点。
 *
 * 与 CMS 分类分开渲染 —— 站点的 `key` 是字符串（`csp_DouDouGuard`），
 * 不能塞进 `VideoCategory(typeId: Int)`，旧实现正是用下标伪造 Int 才丢失层级。
 */
@Composable
private fun SiteNodeRow(
    sites: List<com.mediamix.shared.services.SiteNode>,
    selectedKey: String?,
    onSelect: (com.mediamix.shared.services.SiteNode) -> Unit,
) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().horizontalWheelScroll(state),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(sites, key = { it.key }) { node ->
            val selected = selectedKey == node.key
            FilterChip(
                selected = selected,
                onClick = { onSelect(node) },
                label = { Text(node.name, fontSize = 12.sp, maxLines = 1) },
                shape = RoundedCornerShape(8.dp),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

/**
 * 二级目录：选中站点内部的 class。
 *
 * `typeId` 是 String（`"1"` / `"dianying"`），第一个 chip「首页」对应
 * `tid = null` —— 即该站点的 `homeContent` 推荐。
 */
@Composable
private fun ClassRow(
    classes: List<com.mediamix.shared.models.SpiderCategory>,
    selectedId: String?,
    onSelect: (com.mediamix.shared.models.SpiderCategory?) -> Unit,
) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().horizontalWheelScroll(state),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            CategoryChip("首页", selectedId == null) { onSelect(null) }
        }
        items(classes, key = { it.typeId }) { cat ->
            CategoryChip(cat.typeName, selectedId == cat.typeId) { onSelect(cat) }
        }
    }
}

// ============================================================================
// 分类
// ============================================================================

@Composable
private fun CategoryRow(
    categories: List<com.mediamix.shared.models.VideoCategory>,
    selectedId: Int?,
    onSelect: (com.mediamix.shared.models.VideoCategory?) -> Unit,
) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().horizontalWheelScroll(state),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            CategoryChip("全部", selectedId == null) { onSelect(null) }
        }
        items(categories, key = { it.typeId }) { cat ->
            CategoryChip(cat.typeName, selectedId == cat.typeId) { onSelect(cat) }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        shape = RoundedCornerShape(8.dp),
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                labelColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
        border =
            if (selected) {
                null
            } else {
                AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                )
            },
    )
}

// ============================================================================
// 提示条
// ============================================================================

@Composable
private fun NoticeBanner(
    text: String,
    showSwitchAction: Boolean,
    onSwitchSource: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
                lineHeight = 17.sp,
            )
            if (showSwitchAction) {
                TextButton(onClick = onSwitchSource) {
                    Icon(
                        AppIcons.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("换个源", fontSize = 12.sp)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭提示",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

// ============================================================================
// 内容区
// ============================================================================

@Composable
private fun HomeContent(
    videos: List<VideoItem>,
    isLoading: Boolean,
    hasMore: Boolean,
    error: String?,
    hasSource: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (VideoItem) -> Unit,
) {
    when {
        isLoading && videos.isEmpty() -> SkeletonContent()

        !isLoading && videos.isEmpty() && error != null -> {
            ErrorContent(message = error, onRetry = onRetry)
            SecondaryAction(onSwitchSource)
        }

        !isLoading && videos.isEmpty() && hasSource -> {
            EmptyState()
        }

        else -> {
            // 响应式列数：每列至少 [CARD_MIN_WIDTH_DP] dp。手机 3 列、平板 5 列、
            // 桌面（1280dp 窗口）约 7 列 —— 固定 3 列在桌面端会把一张海报拉到
            // 近 400dp 宽（占半屏），这是「桌面端画报过大」的根因。
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = (maxWidth / CARD_MIN_WIDTH_DP.dp).toInt().coerceIn(3, 8)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Banner 只在「全部」分类下展示，且至少 3 部影片才够轮播
                    if (videos.size >= 3) {
                        item(span = { GridItemSpan(columns) }, key = "banner") {
                            BannerSection(
                                items = videos.take(5),
                                onOpenDetail = onOpenDetail,
                            )
                        }
                    }

                    // 豆瓣系源（豆豆等）不返回 vod_id —— key 用「id，或 名字+海报」兜底，
                    // 否则空 key 全部重复直接崩 LazyGrid（实测 IllegalArgumentException: Key ""）
                    gridItems(
                        videos,
                        key = { "${it.vodId}|${it.vodName}|${it.vodPic}" },
                    ) { item ->
                        VideoGridCard(item = item, onClick = { onOpenDetail(item) })
                    }

                    if (isLoading && videos.isNotEmpty()) {
                        item(span = { GridItemSpan(columns) }, key = "loading-more") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    } else if (!hasMore && videos.isNotEmpty()) {
                        item(span = { GridItemSpan(columns) }, key = "list-end") {
                            Text(
                                text = "已经到底啦",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryAction(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        TextButton(onClick = onClick) {
            Icon(AppIcons.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("换个数据源")
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                AppIcons.Movie,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "还没有影片数据",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "下拉刷新或换一个数据源试试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ============================================================================
// 轮播 Banner
// ============================================================================

@Composable
private fun BannerSection(
    items: List<VideoItem>,
    onOpenDetail: (VideoItem) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(6000L)
            val next = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(next)
        }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 0.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(BANNER_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(12.dp)),
        ) { page ->
            val item = items[page]
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable { onOpenDetail(item) },
            ) {
                AsyncImage(
                    model = item.vodPic,
                    contentDescription = item.vodName,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.35f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.78f),
                                ),
                            ),
                )
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = item.vodName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val caption =
                        listOfNotNull(
                            item.typeName?.takeIf { it.isNotEmpty() },
                            item.vodYear?.takeIf { it.isNotEmpty() },
                            item.vodRemarks?.takeIf { it.isNotEmpty() },
                        ).joinToString(" · ")
                    if (caption.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = caption,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (items.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                items.indices.forEach { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier =
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(width = if (active) 14.dp else 6.dp, height = 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                                    },
                                ),
                    )
                }
            }
        }
    }
}

// ============================================================================
// 影片卡片
// ============================================================================

@Composable
private fun VideoGridCard(
    item: VideoItem,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.vodPic,
                contentDescription = item.vodName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            val remarks = item.vodRemarks
            if (!remarks.isNullOrEmpty()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        text = remarks,
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.vodName,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ============================================================================
// 骨架屏
// ============================================================================

@Composable
private fun SkeletonContent() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .height(BANNER_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        // 骨架列数与真实网格保持一致（否则加载完成瞬间 3 列变 7 列会跳一下）
        BoxWithConstraints {
            val columns = (maxWidth / CARD_MIN_WIDTH_DP.dp).toInt().coerceIn(3, 8)
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(columns) {
                            SkeletonCard(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
