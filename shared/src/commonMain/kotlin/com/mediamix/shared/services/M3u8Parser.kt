package com.mediamix.shared.services

/**
 * m3u8 解析结果。
 *
 * 分两层：master playlist 只列出各码率的 variant，media playlist 才是真正的分片列表。
 */
sealed interface M3u8Playlist {
    /** 主播放列表：多个码率/清晰度的子列表 */
    data class Master(
        val variants: List<M3u8Variant>,
    ) : M3u8Playlist

    /** 媒体播放列表：按顺序排列的分片地址 */
    data class Media(
        val segments: List<String>,
        /** 是否存在 `#EXT-X-KEY`（加密流）—— 加密流本实现无法合并，需明确告知用户 */
        val encrypted: Boolean = false,
    ) : M3u8Playlist

    /** 无法解析 */
    data object Unsupported : M3u8Playlist
}

/** 主列表里的一个码率分支 */
data class M3u8Variant(
    val url: String,
    val bandwidth: Int,
    val resolution: String? = null,
)

/**
 * m3u8 解析器 —— **纯字符串逻辑**，不涉及网络与文件，可在 Desktop 上直接单测。
 *
 * 为什么自己写：下载模块此前把 m3u8 当普通文件裸 GET 存成 `.mp4`，
 * 得到的是一份文本清单而不是视频，产物必然无法播放。要正确下载就必须解析清单，
 * 而引入 HLS 客户端库（如 ExoPlayer 的 HlsDownloader）会把整个播放器依赖拖进下载路径。
 *
 * [baseUrl] 用于把相对路径补全为绝对地址（m3u8 里大量使用相对路径）。
 */
object M3u8Parser {
    /** 分片下载的并发度：HLS 分片小、数量多，适度并发能显著提速且不至于打满带宽。 */
    const val DEFAULT_CONCURRENCY = 4

    fun parse(
        content: String,
        baseUrl: String,
    ): M3u8Playlist {
        val lines =
            content
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        if (lines.isEmpty() || !lines.first().startsWith("#EXTM3U")) {
            // 少数服务端不带 #EXTM3U 头，仍按内容判定，避免误判为不可下载
            if (lines.none { it.startsWith("#EXTINF") || it.startsWith("#EXT-X-STREAM-INF") }) {
                return M3u8Playlist.Unsupported
            }
        }

        val variants = mutableListOf<M3u8Variant>()
        val segments = mutableListOf<String>()
        var encrypted = false
        var pendingBandwidth = -1
        var pendingResolution: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-KEY") -> {
                    // METHOD=NONE 表示未加密；其余（AES-128 等）需要密钥才能解密
                    if (!line.contains("METHOD=NONE", ignoreCase = true)) encrypted = true
                }

                line.startsWith("#EXT-X-STREAM-INF") -> {
                    pendingBandwidth = ATTRIBUTE_BANDWIDTH
                        .find(line)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: -1
                    pendingResolution = ATTRIBUTE_RESOLUTION.find(line)?.groupValues?.get(1)
                }

                line.startsWith("#") -> Unit // 其余标签（EXTINF / EXT-X-ENDLIST 等）此处不需要

                else -> {
                    val url = resolveUrl(baseUrl, line)
                    if (pendingBandwidth >= 0 || pendingResolution != null) {
                        variants.add(M3u8Variant(url = url, bandwidth = pendingBandwidth, resolution = pendingResolution))
                        pendingBandwidth = -1
                        pendingResolution = null
                    } else {
                        segments.add(url)
                    }
                }
            }
        }

        return when {
            variants.isNotEmpty() -> M3u8Playlist.Master(variants)
            segments.isNotEmpty() -> M3u8Playlist.Media(segments = segments, encrypted = encrypted)
            else -> M3u8Playlist.Unsupported
        }
    }

    /**
     * 从主列表里选一路码率。
     *
     * 默认取带宽最高的一路（下载场景用户要的是「最好的那份」）；
     * 带宽缺失时退回第一个。
     */
    fun pickBestVariant(variants: List<M3u8Variant>): M3u8Variant? = variants.maxByOrNull { it.bandwidth } ?: variants.firstOrNull()

    /** 把 m3u8 里的相对地址补成绝对地址。 */
    fun resolveUrl(
        baseUrl: String,
        ref: String,
    ): String {
        if (ref.startsWith("http://", ignoreCase = true) || ref.startsWith("https://", ignoreCase = true)) return ref
        if (ref.startsWith("//")) return "${baseUrl.substringBefore("://")}://${ref.removePrefix("//")}"

        val origin = originOf(baseUrl)
        if (ref.startsWith("/")) return origin + ref

        val baseDir = baseUrl.substringBefore('?').substringBefore('#').substringBeforeLast('/', "")
        return "$baseDir/$ref"
    }

    private fun originOf(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return ""
        val hostEnd = url.indexOf('/', schemeEnd + 3)
        return if (hostEnd < 0) url else url.substring(0, hostEnd)
    }

    private val ATTRIBUTE_BANDWIDTH = Regex("""BANDWIDTH=(\d+)""")
    private val ATTRIBUTE_RESOLUTION = Regex("""RESOLUTION=([0-9]+x[0-9]+)""")
}
