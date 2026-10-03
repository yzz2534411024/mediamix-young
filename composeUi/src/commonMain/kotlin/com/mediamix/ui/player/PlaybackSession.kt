package com.mediamix.ui.player

import com.mediamix.shared.models.PlaySource
import com.mediamix.shared.models.VideoDetail
import com.mediamix.shared.models.VideoEpisode
import com.mediamix.shared.services.ResolvedPlay
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
    /** 海报地址：观看历史列表要用（Dao 允许 null，但没图的历史页很难看）。 */
    val vodPic: String? = null,
    val sourceKey: String,
    val sourceIndex: Int,
    val playSources: List<PlaySource>,
    val episodes: List<VideoEpisode>,
    val startIndex: Int,
    /** 本地文件播放时为 true（下载页 / 本地缓存），此时不做线路回退 */
    val isLocal: Boolean = false,
    /**
     * 本次播放**必须携带的请求头**（TVBox 蜘蛛给出）。
     *
     * 此前播放页只能靠 URL 猜一个 Referer，对带防盗链的蜘蛛源必被 403。
     * 这里把蜘蛛 `playerContent` 返回的真实头透传下去。
     */
    val headers: Map<String, String>? = null,
    /**
     * 是否为「按需解析」的 TVBox 会话。
     *
     * TVBox 的剧集标识要经 `playerContent` 才能变成地址，因此这类会话的
     * [episodes] 里存的是**标识**、[resolved] 里存的是已解析结果；
     * 切集时由播放页调用解析器按需补解析。
     */
    val isResolvable: Boolean = false,
    /**
     * 已解析的剧集：`剧集标识 -> 解析结果`。
     *
     * 对齐键必须是**剧集标识**而不是解析后的 URL —— TVBox 场景下详情页给的 id
     * 与 `playerContent` 解析出的地址完全不同，按 URL 对齐会让切集/失败回退静默失效。
     */
    val resolved: Map<String, ResolvedPlay> = emptyMap(),
) {
    val startUrl: String get() = episodes.getOrNull(startIndex)?.url.orEmpty()

    /**
     * 会话对齐键。
     *
     * 取「本集解析后的真实地址」；未解析时退回剧集标识 —— 播放页用它把导航参数
     * 与当前会话对上，避免串到上一次的剧集列表。
     */
    val resolveKey: String get() = resolved[startUrl]?.url ?: startUrl

    /** 本集真实可播的地址（TVBox 用解析结果，CMS 直接用剧集 url）。 */
    val startPlayableUrl: String get() = resolved[startUrl]?.url ?: startUrl

    /** 本集所需的请求头（TVBox 蜘蛛给出；CMS 为空）。 */
    val startHeaders: Map<String, String>? get() = resolved[startUrl]?.headers?.takeIf { it.isNotEmpty() } ?: headers

    val startTitle: String get() = episodes.getOrNull(startIndex)?.name ?: vodName
    val episodeNames: List<String> get() = episodes.map { it.name }
    val episodeUrls: List<String> get() = episodes.map { it.url }
    val hasMultipleEpisodes: Boolean get() = episodes.size > 1

    /** 该集是否已经解析出真实地址 */
    fun isResolved(index: Int): Boolean = episodes.getOrNull(index)?.let { resolved.containsKey(it.url) } == true

    /**
     * 第 [index] 集在其它线路里的候选地址，用于播放失败时自动换线。
     *
     * TVBox 未解析的标识不参与回退 —— 拿它去播必然失败。
     */
    fun fallbackUrlsFor(index: Int): List<String> {
        if (isLocal || playSources.size <= 1) return emptyList()
        val result = mutableListOf<String>()
        for (i in playSources.indices) {
            if (i == sourceIndex) continue
            val raw = playSources[i].episodes.getOrNull(index)?.url ?: continue
            val url = resolved[raw]?.url ?: raw
            if (url.isNotBlank() && url != startPlayableUrl && url !in result) result.add(url)
        }
        return result
    }
}

/**
 * 播放会话仓库（Koin single）。
 *
 * 详情页在跳转前调用 [start] / [startResolved] 写入会话，播放页读取。用 single 是因为
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
                vodPic = detail.vodPic,
                sourceKey = detail.sourceKey,
                sourceIndex = sourceIndex,
                playSources = detail.playSources,
                episodes = playSource.episodes,
                startIndex = safeIndex,
            )
    }

    /**
     * TVBox 源：写入**完整剧集列表** + 已解析的那一集。
     *
     * 旧实现只写单集，于是「下一集」永远是灰的 —— 对多集站点等于不可用。
     * 现在保留完整列表（切集时有名字和标识可用），已解析的结果放进 [PlaybackSession.resolved]，
     * 其余集在播放页切到时再按需解析。
     */
    fun startResolved(
        detail: VideoDetail,
        sourceIndex: Int,
        episodeIndex: Int,
        resolvedUrl: String,
        headers: Map<String, String>?,
    ) {
        val playSource = detail.playSources.getOrNull(sourceIndex) ?: return
        if (playSource.episodes.isEmpty()) return
        val safeIndex = episodeIndex.coerceIn(0, playSource.episodes.lastIndex)
        val rawId = playSource.episodes[safeIndex].url
        if (rawId.isBlank()) return
        _session.value =
            PlaybackSession(
                vodId = detail.vodId,
                vodName = detail.vodName,
                vodPic = detail.vodPic,
                sourceKey = detail.sourceKey,
                sourceIndex = sourceIndex,
                playSources = detail.playSources,
                episodes = playSource.episodes,
                startIndex = safeIndex,
                headers = headers,
                isResolvable = true,
                resolved =
                    mapOf(
                        rawId to
                            ResolvedPlay(
                                url = resolvedUrl,
                                headers = headers.orEmpty(),
                            ),
                    ),
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

    /**
     * 记录一集的解析结果（播放页按需解析后回写）。
     *
     * 回写而不是重建会话，是为了不丢失 `startIndex` 之外的上下集上下文。
     */
    fun recordResolved(
        rawEpisodeId: String,
        resolved: ResolvedPlay,
    ) {
        val current = _session.value ?: return
        if (rawEpisodeId.isBlank()) return
        _session.value = current.copy(resolved = current.resolved + (rawEpisodeId to resolved))
    }

    fun clear() {
        _session.value = null
    }

    /**
     * 取出与给定播放地址匹配的会话。
     *
     * ⚠️ 不能只比 [PlaybackSession.startUrl]：TVBox 场景下「剧集标识」与
     * 「`playerContent` 解析出的地址」是两个不同的字符串，只比前者会让
     * 播放页拿不到会话（选集/上下集/失败回退全部静默失效）。这里同时接受
     * 解析前的标识与解析后的地址。
     */
    fun sessionFor(url: String): PlaybackSession? =
        _session.value?.takeIf { session ->
            session.startUrl == url || session.resolveKey == url || session.startPlayableUrl == url
        }
}
