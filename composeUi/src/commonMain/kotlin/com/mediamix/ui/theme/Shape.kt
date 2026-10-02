package com.mediamix.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距 / 圆角 / 层次 Token。
 *
 * 之前这些数值是散落在各页面里的字面量（`.dp`、`.sp` 手写），导致同级元素间距不一致、
 * 同类元素圆角不同 —— 这是"界面显得乱"的另一半原因。
 * 规则：**只用这里定义的档位**，不要在组件里随手写尺寸。
 */

// ───────────────────────── 间距（4 的倍数）─────────────────────────

object Spacing {
    /** 4dp：图标与文字之间、紧凑 Chip 内边距 */
    val xs: Dp = 4.dp

    /** 8dp：卡片内元素间距、按钮之间 */
    val sm: Dp = 8.dp

    /** 12dp：卡片内边距（紧凑） */
    val md: Dp = 12.dp

    /** 16dp：页面左右边距、卡片标准内边距 */
    val lg: Dp = 16.dp

    /** 20dp：卡片内边距（宽松） */
    val xl: Dp = 20.dp

    /** 24dp：区块之间 */
    val xxl: Dp = 24.dp

    /** 32dp：大区块分隔（如"继续观看"与"最近更新"之间） */
    val xxxl: Dp = 32.dp
}

// ─────────────────────────── 圆角 ───────────────────────────

object Radius {
    /** 8dp：海报图 */
    val poster: Dp = 8.dp

    /** 10dp：按钮 */
    val button: Dp = 10.dp

    /** 12dp：卡片（最常用） */
    val card: Dp = 12.dp

    /** 20dp：弹窗、底部面板 */
    val sheet: Dp = 20.dp

    /** 999dp：胶囊（Chip、标签、头像） */
    val pill: Dp = 999.dp
}

/** 传给 MaterialTheme 的形状集（让 M3 组件自动吃到圆角体系）。 */
val CatShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(Radius.poster),
        medium = RoundedCornerShape(Radius.card),
        large = RoundedCornerShape(Radius.sheet),
        extraLarge = RoundedCornerShape(24.dp),
    )

// ─────────────────────────── 层次 ───────────────────────────

/**
 * 层次表达：**描边 + 极浅阴影**，不用重阴影。
 * 移动端小屏上重阴影会显脏，描边反而更清晰。
 */
object Elevation {
    /** 0 级：平面（贴在背景上的元素） */
    val flat: Dp = 0.dp

    /** 1 级：卡片 */
    val card: Dp = 2.dp

    /** 2 级：悬浮/浮层（下拉菜单、悬浮按钮） */
    val floating: Dp = 8.dp

    /** 3 级：弹窗 */
    val dialog: Dp = 24.dp
}
