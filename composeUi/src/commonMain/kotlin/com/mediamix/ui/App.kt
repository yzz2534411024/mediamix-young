package com.mediamix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mediamix.ui.navigation.MainScaffold
import com.mediamix.ui.navigation.Screen
import com.mediamix.ui.player.PlaybackSessionStore
import com.mediamix.ui.screens.*
import org.koin.compose.koinInject

@Composable
fun App() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 详情页写入、播放页读取的播放会话
    val sessionStore: PlaybackSessionStore = koinInject()

    // 判断是否显示底部导航栏（播放器、详情页、源码管理页不显示）
    val showBottomBar = currentRoute != Screen.Player.route &&
            currentRoute?.startsWith("detail") != true &&
            currentRoute?.startsWith("player") != true &&
            currentRoute != Screen.SourceManage.route

    MainScaffold(
        currentRoute = currentRoute,
        onNavigate = { screen ->
            navController.navigate(screen.route) {
                popUpTo(Screen.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        showBottomBar = showBottomBar
    ) { paddingModifier ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = paddingModifier
        ) {
            composable(Screen.Home.route) {
                VideoHomeScreen(
                    onNavigateToDetail = { vodId, sourceKey ->
                        navController.navigate(Screen.Detail.createRoute(vodId, sourceKey)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route)
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateToDetail = { vodId, sourceKey ->
                        navController.navigate(Screen.Detail.createRoute(vodId, sourceKey)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Favorite.route) {
                FavoriteScreen(
                    onNavigateToDetail = { vodId, sourceKey ->
                        navController.navigate(Screen.Detail.createRoute(vodId, sourceKey)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToSourceManage = { navController.navigate(Screen.SourceManage.route) },
                    onNavigateToDownloads = { navController.navigate(Screen.Downloads.route) },
                    onNavigateToDebug = { navController.navigate(Screen.Debug.route) }
                )
            }
            composable(Screen.Debug.route) {
                DebugScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Detail.PATTERN) { backStackEntry ->
                val vodId = backStackEntry.arguments?.getString("vodId") ?: ""
                val sourceKey = backStackEntry.arguments?.getString("sourceKey") ?: ""
                VideoDetailScreen(
                    vodId = vodId,
                    sourceKey = sourceKey,
                    onNavigateToPlayer = { url, title, index ->
                        navController.navigate(Screen.Player.createRoute(url, title, index)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToDetail = { nextVodId, nextSourceKey ->
                        navController.navigate(Screen.Detail.createRoute(nextVodId, nextSourceKey)) {
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Search.route) {
                VideoSearchScreen(
                    onNavigateToDetail = { vodId, sourceKey ->
                        navController.navigate(Screen.Detail.createRoute(vodId, sourceKey)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Player.PATTERN) { backStackEntry ->
                val url = backStackEntry.arguments?.getString("url") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""
                val index = backStackEntry.arguments?.getString("index")?.toIntOrNull() ?: 0
                PlayerScreen(
                    url = url,
                    title = title,
                    episodeIndex = index,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.SourceManage.route) {
                SourceManageScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Downloads.route) {
                DownloadScreen(
                    onBack = { navController.popBackStack() },
                    onPlayVideo = { localPath, title ->
                        // 下载页没有剧集概念，写入单集会话，播放页才能正确显示标题
                        sessionStore.startSingle(localPath, title)
                        navController.navigate(Screen.Player.createRoute(localPath, title, 0)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
