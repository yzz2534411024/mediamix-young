package com.mediamix.ui.platform

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier

/**
 * 让横向 [androidx.compose.foundation.lazy.LazyRow] 支持鼠标滚轮（桌面端）。
 *
 * Compose 的横向滚动默认**不响应垂直滚轮** —— 桌面用户把鼠标放在源/分类/站点
 * 这几行 tab 上滚动会「纹丝不动」，只能按住拖动（实测用户反馈）。
 *
 * Android 端触摸横向滑动是原生行为，actual 为 no-op。
 */
expect fun Modifier.horizontalWheelScroll(state: LazyListState): Modifier
