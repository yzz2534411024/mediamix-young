package com.mediamix.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * CatVideo 排版阶梯。
 *
 * 之前这里是 `Typography()` 空实现 —— 所有文字都用 M3 默认字号，标题与正文拉不开
 * 层次，是界面"看着不精致"的主因之一。
 *
 * 原则：
 * - **一屏最多三级层次**，多了反而乱；
 * - 强调优先用**字重与颜色**，不靠放大字号；
 * - 密集列表（集数、时长）行高收紧到 1.25~1.35 倍。
 */
private val Default = FontFamily.Default

// ────────────────── 业务语义 Token（写界面时优先用这些）──────────────────

/** 大标题（详情页片名）：28sp / SemiBold */
val CatDisplayTitle =
    TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    )

/** 区块标题（"继续观看"）：22sp / SemiBold */
val CatTitleLarge =
    TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

/** 卡片标题、页面标题：17sp / SemiBold */
val CatTitleMedium =
    TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    )

/** 正文、简介：14sp / Normal */
val CatBody =
    TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

/** 按钮、Chip、标签：13sp / Medium */
val CatLabel =
    TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

/** 集数、时长等辅助信息：12sp / Normal */
val CatCaption =
    TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

// ────────────────── 映射到 M3 槽位（让现成组件也吃到新排版）──────────────────

/**
 * M3 槽位 ↔ 业务 Token：
 * displaySmall/headlineLarge → CatDisplayLarge；headlineSmall/titleLarge → CatTitleLarge；
 * titleMedium/titleSmall → CatTitleMedium；bodyLarge/bodyMedium → CatBody；
 * labelLarge/labelMedium → CatLabel；labelSmall/bodySmall → CatCaption。
 */
val CatTypography =
    Typography(
        displaySmall = CatDisplayTitle,
        headlineLarge = CatDisplayTitle,
        headlineMedium = CatTitleLarge,
        headlineSmall = CatTitleLarge,
        titleLarge = CatTitleLarge,
        titleMedium = CatTitleMedium,
        titleSmall = CatTitleMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyLarge = CatBody.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = CatBody,
        bodySmall = CatCaption,
        labelLarge = CatLabel,
        labelMedium = CatLabel.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = CatCaption,
    )
