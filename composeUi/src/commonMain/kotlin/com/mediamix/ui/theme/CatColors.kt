package com.mediamix.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * CatVideo 扩展色（M3 的 ColorScheme 里没有的槽位）。
 *
 * 为什么要扩展：M3 只定义了 primary/secondary/tertiary 等角色色，没有
 * "情感强调"和"成功/警告"这类语义。本项目需要：
 * - [accent]：收藏/点赞/成就 —— 与功能蓝区分，让用户一眼分辨"操作"与"反馈"；
 * - [success] / [warning]：下载完成、线路异常等状态。
 *
 * 用法：`CatTheme.colors.accent`（见 [CatTheme]）。
 */
@Immutable
data class CatColors(
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    /** 海报占位底色（图片加载前/失败时） */
    val posterPlaceholder: Color,
    /** 播放页遮罩（恒深色，与主题无关） */
    val playerScrim: Color,
)

val LightCatColors =
    CatColors(
        accent = CatAccentLight,
        onAccent = CatOnAccentLight,
        success = CatSuccessLight,
        onSuccess = CatOnSuccessLight,
        warning = CatWarningLight,
        onWarning = CatOnWarningLight,
        posterPlaceholder = CatSurfaceVariantLight,
        playerScrim = PlayerScrimBottom,
    )

val DarkCatColors =
    CatColors(
        accent = CatAccentDark,
        onAccent = CatOnAccentDark,
        success = CatSuccessDark,
        onSuccess = CatOnSuccessDark,
        warning = CatWarningDark,
        onWarning = CatOnWarningDark,
        posterPlaceholder = CatSurfaceVariantDark,
        playerScrim = PlayerScrimBottom,
    )

val LocalCatColors = staticCompositionLocalOf { LightCatColors }
