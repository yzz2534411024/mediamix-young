package com.mediamix.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems =
    listOf(
        BottomNavItem(Screen.Home, "\u89c6\u9891", Icons.Filled.Movie),
        BottomNavItem(Screen.History, "\u5386\u53f2", Icons.Filled.History),
        BottomNavItem(Screen.Favorite, "\u6536\u85cf", Icons.Filled.Favorite),
        BottomNavItem(Screen.Settings, "\u8bbe\u7f6e", Icons.Filled.Settings),
    )

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.screen.route,
                onClick = { onNavigate(item.screen) },
            )
        }
    }
}
