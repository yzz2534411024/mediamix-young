package com.mediamix.shared.player

/**
 * HLS 主清单（master playlist）多码率变体解析。
 *
 * 为什么需要它：片源站点给的播放地址如果是 **master m3u8**（内含多条不同分辨率
 * 的子流），播放器本身会自动挑一条（ABR），但用户没有"手动选 720P/1080P"的入口。
 * 这里把变体解析成带标签的列表，交给画质面板 —— 选择后直接以变体地址重新装载，
 * ExoPlayer / mpv 都天然支持播子流。
 *
 * 解析目标（master playlist 形态）：
 * ```
 * #EXTM3U
 * #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1920x1080
 * 1080/index.m3u8
 * #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720
 * 720/index.m3u8
 * ```
 * 媒体播放清单（只有分片的普通 m3u8）不含 STREAM-INF，返回空列表。
 */
data class HlsVariant(
    val label: String,
    val url: String,
    val bandwidth: Long,
    val width: Int,
    val height: Int,
)

object HlsVariantParser {
    private val bandwidthRegex = Regex("BANDWIDTH=(\\d+)")
    private val resolutionRegex = Regex("RESOLUTION=(\\d+)x(\\d+)")

    /** 变体标签：按高度映射常见档位，缺失时用码率。 */
    private fun labelFor(height: Int, bandwidth: Long): String =
        when {
            height >= 2000 -> "4K"
            height >= 1080 -> "1080P"
            height >= 720 -> "720P"
            height >= 576 -> "576P"
            height >= 480 -> "480P"
            height >= 360 -> "360P"
            bandwidth > 0 -> "${bandwidth / 1000}kbps"
            else -> "未知"
        }

    /** 相对地址 → 绝对地址（变体经常是 `720/index.m3u8` 这种相对路径）。 */
    private fun resolveUrl(raw: String, baseUrl: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val baseNoQuery = baseUrl.substringBefore('?')
        val dir = baseNoQuery.substringBeforeLast('/', "") + "/"
        return when {
            raw.startsWith("/") ->
                Regex("(https?://[^/]+)").find(baseUrl)?.groupValues?.get(1)?.plus(raw) ?: raw
            dir.startsWith("http") -> dir + raw
            else -> raw
        }
    }

    /**
     * 解析 master m3u8。无变体（单码率/媒体清单）返回空列表，
     * 上层以 `size > 1` 决定是否提供清晰度切换。
     */
    fun parse(content: String, baseUrl: String): List<HlsVariant> {
        if (!content.contains("#EXT-X-STREAM-INF")) return emptyList()
        val variants = mutableListOf<HlsVariant>()
        var pendingBandwidth = -1L
        var pendingWidth = 0
        var pendingHeight = 0

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = line.removePrefix("#EXT-X-STREAM-INF:")
                pendingBandwidth = bandwidthRegex.find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
                val res = resolutionRegex.find(attrs)?.groupValues
                pendingWidth = res?.get(1)?.toIntOrNull() ?: 0
                pendingHeight = res?.get(2)?.toIntOrNull() ?: 0
            } else if (line.isNotEmpty() && !line.startsWith("#") && pendingBandwidth >= 0) {
                val url = resolveUrl(line, baseUrl)
                variants +=
                    HlsVariant(
                        label = labelFor(pendingHeight, pendingBandwidth),
                        url = url,
                        bandwidth = pendingBandwidth,
                        width = pendingWidth,
                        height = pendingHeight,
                    )
                pendingBandwidth = -1
                pendingWidth = 0
                pendingHeight = 0
            }
        }
        return variants
    }
}
