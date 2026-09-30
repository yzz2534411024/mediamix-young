package com.mediamix.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MainScaffold(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    showBottomBar: Boolean = true,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(currentRoute = currentRoute, onNavigate = onNavigate)
            }
        }
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}