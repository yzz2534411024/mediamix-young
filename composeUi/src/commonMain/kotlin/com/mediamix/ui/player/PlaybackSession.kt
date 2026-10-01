package com.mediamix.ui.player

import com.mediamix.shared.models.PlaySource
import com.mediamix.shared.models.VideoDetail
import com.mediamix.shared.models.VideoEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一次播放会话的完整上下文。
 *
 * **为什么要这个对象**：原来详情页点某一集会走导航参数把 `url / title / index`
 * 三个字符串塞进 route，剧集列表本身被丢掉了 —— 于是播放页的「上一集 / 下一集」
 * 永远是灰的，播放失败也没法自动换线路。把整份剧集列表放进这个仓库，
 * 播放页就能做选集、连播和失败回退。
 */
data class PlaybackSession(
    val vodId: String,
    val vodName: String,
    val sourceKey: String,
    val sourceIndex: Int,
    val playSources: List<PlaySource>,
    val episodes: List<VideoEpisode>,
    val startIndex: Int,
    /** 本地文件播放时为 true（下载页 / 本地缓存），此时不做线路回退 */
    val isLocal: Boolean = false,
) {
    val startUrl: String get() = episodes.getOrNull(startIndex)?.url.orEmpty()
    val startTitle: String get() = episodes.getOrNull(startIndex)?.name ?: vodName
    val episodeNames: List<String> get() = episodes.map { it.name }
    val episodeUrls: List<String> get() = episodes.map { it.url }
    val hasMultipleEpisodes: Boolean get() = episodes.size > 1

    /** 第 [index] 集在其它线路里的候选地址，用于播放失败时自动换线 */
    fun fallbackUrlsFor(index: Int): List<String> {
        if (isLocal || playSources.size <= 1) return emptyList()
        val result = mutableListOf<String>()
        for (i in playSources.indices) {
            if (i == sourceIndex) continue
            val url = playSources[i].episodes.getOrNull(index)?.url ?: continue
            if (url.isNotBlank() && url !in result) result.add(url)
        }
        return result
    }
}

/**
 * 播放会话仓库（Koin single）。
 *
 * 详情页在跳转前调用 [start] 写入会话，播放页读取。用 single 是因为
 * 详情页和播放页分属两个 NavBackStackEntry，各自的 ViewModel 拿不到对方的实例。
 */
class PlaybackSessionStore {
    private val _session = MutableStateFlow<PlaybackSession?>(null)
    val session: StateFlow<PlaybackSession?> = _session.asStateFlow()

    /** 详情页：带上完整剧集列表开始播放 */
    fun start(
        detail: VideoDetail,
        sourceIndex: Int,
        episodeIndex: Int,
    ) {
        val playSource = detail.playSources.getOrNull(sourceIndex) ?: return
        if (playSource.episodes.isEmpty()) return
        val safeIndex = episodeIndex.coerceIn(0, playSource.episodes.lastIndex)
        _session.value =
            PlaybackSession(
                vodId = detail.vodId,
                vodName = detail.vodName,
                sourceKey = detail.sourceKey,
                sourceIndex = sourceIndex,
                playSources = detail.playSources,
                episodes = playSource.episodes,
                startIndex = safeIndex,
            )
    }

    /** 下载页 / 历史页：播放单个本地或远程文件 */
    fun startSingle(
        url: String,
        title: String,
    ) {
        if (url.isBlank()) return
        _session.value =
            PlaybackSession(
                vodId = "",
                vodName = title.ifBlank { "本地视频" },
                sourceKey = "",
                sourceIndex = 0,
                playSources = emptyList(),
                episodes = listOf(VideoEpisode(name = title.ifBlank { "本地视频" }, url = url)),
                startIndex = 0,
                isLocal = !url.startsWith("http", ignoreCase = true),
            )
    }

    fun clear() {
        _session.value = null
    }

    /** 取出与给定播放地址匹配的会话；不匹配返回 null（避免串到上一次的剧集列表） */
    fun sessionFor(url: String): PlaybackSession? = _session.value?.takeIf { it.startUrl == url }
}
