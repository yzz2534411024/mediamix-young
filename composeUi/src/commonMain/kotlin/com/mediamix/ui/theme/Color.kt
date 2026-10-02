package com.mediamix.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * CatVideo 品牌配色。
 *
 * 色值不是随手挑的 —— 直接从**应用图标**反推，保证界面与图标是同一套视觉语言：
 * 图标是「蓝渐变底 + 白猫 + 淡粉耳」，所以主色取渐变主色 `#2D6BF5`，
 * 强调色取耳内的淡粉 `#FFC4D6`（用于收藏/点赞这类情感动作）。
 *
 * 使用规则：组件里**不要再写 `Color(0xFF…)`**，
 * 一律走 `MaterialTheme.colorScheme.*`（M3 标准槽位）或 `CatTheme.colors`（本项目扩展槽位）。
 */

// ─────────────────────────── 浅色 ───────────────────────────

val CatPrimaryLight = Color(0xFF2D6BF5)
val CatOnPrimaryLight = Color(0xFFFFFFFF)
val CatPrimaryContainerLight = Color(0xFFE3ECFF)
val CatOnPrimaryContainerLight = Color(0xFF0A2A66)

val CatSecondaryLight = Color(0xFF5B6472)
val CatOnSecondaryLight = Color(0xFFFFFFFF)
val CatSecondaryContainerLight = Color(0xFFEDF1F7)
val CatOnSecondaryContainerLight = Color(0xFF2A3038)

val CatBackgroundLight = Color(0xFFF7F8FB)
val CatOnBackgroundLight = Color(0xFF10131A)
val CatSurfaceLight = Color(0xFFFFFFFF)
val CatOnSurfaceLight = Color(0xFF10131A)
val CatSurfaceVariantLight = Color(0xFFEEF1F6)
val CatOnSurfaceVariantLight = Color(0xFF5B6472)

val CatOutlineLight = Color(0xFFDFE4EC)
val CatOutlineVariantLight = Color(0xFFEBEFF5)

val CatErrorLight = Color(0xFFE5484D)
val CatOnErrorLight = Color(0xFFFFFFFF)
val CatErrorContainerLight = Color(0xFFFDEBEC)

// ─────────────────────────── 深色 ───────────────────────────

val CatPrimaryDark = Color(0xFF5B8DFF)
val CatOnPrimaryDark = Color(0xFF0A1428)
val CatPrimaryContainerDark = Color(0xFF1B2A4A)
val CatOnPrimaryContainerDark = Color(0xFFD6E3FF)

val CatSecondaryDark = Color(0xFFA8B0BD)
val CatOnSecondaryDark = Color(0xFF1A1D22)
val CatSecondaryContainerDark = Color(0xFF262B33)
val CatOnSecondaryContainerDark = Color(0xFFDDE2EA)

val CatBackgroundDark = Color(0xFF101216)
val CatOnBackgroundDark = Color(0xFFF2F4F8)
val CatSurfaceDark = Color(0xFF171A20)
val CatOnSurfaceDark = Color(0xFFF2F4F8)
val CatSurfaceVariantDark = Color(0xFF1F232B)
val CatOnSurfaceVariantDark = Color(0xFFA8B0BD)

val CatOutlineDark = Color(0xFF2A2F38)
val CatOutlineVariantDark = Color(0xFF23272F)

val CatErrorDark = Color(0xFFF2555A)
val CatOnErrorDark = Color(0xFF2A0A0B)
val CatErrorContainerDark = Color(0xFF3A1A1C)

// ─────────────────────── 扩展槽位（非 M3 标准）───────────────────────

/**
 * 强调色：图标耳内的淡粉。用于收藏、点赞、成就这类**情感动作**，
 * 与主色的"功能性蓝色"形成区分 —— 用户能一眼看出哪个是操作、哪个是情感反馈。
 */
val CatAccentLight = Color(0xFFFFC4D6)
val CatOnAccentLight = Color(0xFF4A1528)
val CatAccentDark = Color(0xFFFFB3CB)
val CatOnAccentDark = Color(0xFF3A0E1E)

/** 状态语义色（成功/警告）。M3 的 ColorScheme 没有对应槽位，放进扩展色里。 */
val CatSuccessLight = Color(0xFF2E9E6B)
val CatOnSuccessLight = Color(0xFFFFFFFF)
val CatSuccessDark = Color(0xFF4CC38A)
val CatOnSuccessDark = Color(0xFF06281A)

val CatWarningLight = Color(0xFFE8A33D)
val CatOnWarningLight = Color(0xFF3A2606)
val CatWarningDark = Color(0xFFF0B45C)
val CatOnWarningDark = Color(0xFF3A2606)

/** 播放页专用：视频区域恒为深色，与主题无关，故单独常量。 */
val PlayerBackground = Color(0xFF000000)
val PlayerScrimTop = Color(0x99000000)
val PlayerScrimBottom = Color(0xB8000000)
