package com.mediamix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import kotlinx.coroutines.flow.MutableStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 主题模式展示名 */
fun ThemeMode.label(): String =
    when (this) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色模式"
        ThemeMode.DARK -> "深色模式"
    }

object ThemeConfig {
    val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
}

private val LightColorScheme =
    lightColorScheme(
        primary = CatPrimaryLight,
        onPrimary = CatOnPrimaryLight,
        primaryContainer = CatPrimaryContainerLight,
        onPrimaryContainer = CatOnPrimaryContainerLight,
        secondary = CatSecondaryLight,
        onSecondary = CatOnSecondaryLight,
        secondaryContainer = CatSecondaryContainerLight,
        onSecondaryContainer = CatOnSecondaryContainerLight,
        tertiary = CatAccentLight,
        onTertiary = CatOnAccentLight,
        background = CatBackgroundLight,
        onBackground = CatOnBackgroundLight,
        surface = CatSurfaceLight,
        onSurface = CatOnSurfaceLight,
        surfaceVariant = CatSurfaceVariantLight,
        onSurfaceVariant = CatOnSurfaceVariantLight,
        surfaceContainer = CatSurfaceLight,
        surfaceContainerHigh = CatSurfaceLight,
        surfaceContainerHighest = CatSurfaceVariantLight,
        surfaceContainerLow = CatSurfaceLight,
        surfaceContainerLowest = CatBackgroundLight,
        outline = CatOutlineLight,
        outlineVariant = CatOutlineVariantLight,
        error = CatErrorLight,
        onError = CatOnErrorLight,
        errorContainer = CatErrorContainerLight,
        scrim = PlayerScrimTop,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = CatPrimaryDark,
        onPrimary = CatOnPrimaryDark,
        primaryContainer = CatPrimaryContainerDark,
        onPrimaryContainer = CatOnPrimaryContainerDark,
        secondary = CatSecondaryDark,
        onSecondary = CatOnSecondaryDark,
        secondaryContainer = CatSecondaryContainerDark,
        onSecondaryContainer = CatOnSecondaryContainerDark,
        tertiary = CatAccentDark,
        onTertiary = CatOnAccentDark,
        background = CatBackgroundDark,
        onBackground = CatOnBackgroundDark,
        surface = CatSurfaceDark,
        onSurface = CatOnSurfaceDark,
        surfaceVariant = CatSurfaceVariantDark,
        onSurfaceVariant = CatOnSurfaceVariantDark,
        surfaceContainer = CatSurfaceDark,
        surfaceContainerHigh = CatSurfaceVariantDark,
        surfaceContainerHighest = CatSurfaceVariantDark,
        surfaceContainerLow = CatSurfaceDark,
        surfaceContainerLowest = CatBackgroundDark,
        outline = CatOutlineDark,
        outlineVariant = CatOutlineVariantDark,
        error = CatErrorDark,
        onError = CatOnErrorDark,
        errorContainer = CatErrorContainerDark,
        scrim = PlayerScrimTop,
    )

/**
 * 主题入口。
 *
 * 名字保留 `MediaMixTheme` 是为了不改所有调用点（desktopApp / androidApp 各一处），
 * 内部已完全换成 CatVideo 的 Token 体系。
 */
@Composable
fun MediaMixTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark =
        when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    CompositionLocalProvider(
        LocalCatColors provides if (isDark) DarkCatColors else LightCatColors,
    ) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            typography = CatTypography,
            shapes = CatShapes,
            content = content,
        )
    }
}

/** 扩展色的便捷入口：`CatTheme.colors.accent`。 */
object CatTheme {
    val colors: CatColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCatColors.current
}
