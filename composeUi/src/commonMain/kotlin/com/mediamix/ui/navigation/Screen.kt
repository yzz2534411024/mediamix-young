package com.mediamix.ui.navigation

import com.mediamix.ui.util.encodeUrlComponent

/**
 * 导航路由定义。
 *
 * ⚠️ 所有作为查询参数传递的字符串都必须经过 [encodeUrlComponent]：
 * Navigation 的 route 是 URI 模板，未编码的 `?`、`&`、`=` 会截断参数。
 * 播放地址（常带 `?token=...&sign=...`）和历史/收藏中的中文标题都依赖这一点。
 */
sealed class Screen(
    val route: String,
) {
    data object Home : Screen("video")

    data object Detail : Screen("detail") {
        const val ARG_VOD_ID = "vodId"
        const val ARG_SOURCE_KEY = "sourceKey"
        const val PATTERN = "detail?$ARG_VOD_ID={$ARG_VOD_ID}&$ARG_SOURCE_KEY={$ARG_SOURCE_KEY}"

        fun createRoute(
            vodId: String,
            sourceKey: String,
        ) = "detail?$ARG_VOD_ID=${vodId.encodeUrlComponent()}" +
            "&$ARG_SOURCE_KEY=${sourceKey.encodeUrlComponent()}"
    }

    data object Player : Screen("player") {
        const val ARG_URL = "url"
        const val ARG_TITLE = "title"
        const val ARG_INDEX = "index"
        const val PATTERN = "player?$ARG_URL={$ARG_URL}&$ARG_TITLE={$ARG_TITLE}&$ARG_INDEX={$ARG_INDEX}"

        fun createRoute(
            url: String,
            title: String,
            index: Int = 0,
        ) = "player?$ARG_URL=${url.encodeUrlComponent()}" +
            "&$ARG_TITLE=${title.encodeUrlComponent()}" +
            "&$ARG_INDEX=$index"
    }

    data object Search : Screen("search")

    data object History : Screen("history")

    data object Favorite : Screen("favorite")

    data object Settings : Screen("settings")
    data object UsageStats : Screen("usage-stats")
    data object CrashLog : Screen("crash-log")

    data object SourceManage : Screen("source-manage")

    data object Downloads : Screen("downloads")

    companion object {
        val bottomNavItems = listOf(Home, History, Favorite, Settings)
    }
}
