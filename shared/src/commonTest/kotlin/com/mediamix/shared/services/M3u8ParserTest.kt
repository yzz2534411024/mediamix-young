package com.mediamix.shared.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * HLS 清单解析回归测试。
 *
 * 下载链路此前把 m3u8 当普通文件裸 GET 存成 `.mp4` —— 产物是文本清单，
 * 播放器必然打不开。解析器是这条修复里的**唯一**纯逻辑部分，放在 Desktop 上直接跑。
 */
class M3u8ParserTest {
    private val base = "https://cdn.example.com/hls/movie/index.m3u8"

    // ==================== media playlist ====================

    @Test
    fun parsesMediaPlaylistSegments() {
        val content =
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:9.009,
            seg0.ts
            #EXTINF:9.009,
            seg1.ts
            #EXTINF:3.003,
            https://other.cdn/seg2.ts
            #EXT-X-ENDLIST
            """.trimIndent()

        val playlist = M3u8Parser.parse(content, base)

        assertIs<M3u8Playlist.Media>(playlist)
        assertEquals(3, playlist.segments.size)
        // 相对路径必须补成绝对地址，否则分片请求会打到 localhost
        assertEquals("https://cdn.example.com/hls/movie/seg0.ts", playlist.segments[0])
        assertEquals("https://cdn.example.com/hls/movie/seg1.ts", playlist.segments[1])
        assertEquals("https://other.cdn/seg2.ts", playlist.segments[2])
        assertFalse(playlist.encrypted)
    }

    @Test
    fun detectsEncryptedStream() {
        val content =
            """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXTINF:9.0,
            seg0.ts
            """.trimIndent()

        val playlist = M3u8Parser.parse(content, base)

        assertIs<M3u8Playlist.Media>(playlist)
        assertTrue(playlist.encrypted, "AES-128 加密流必须被标记，否则合并出的文件无法播放")
    }

    @Test
    fun methodNoneIsNotEncrypted() {
        val content =
            """
            #EXTM3U
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:9.0,
            seg0.ts
            """.trimIndent()

        val playlist = M3u8Parser.parse(content, base)

        assertIs<M3u8Playlist.Media>(playlist)
        assertFalse(playlist.encrypted)
    }

    // ==================== master playlist ====================

    @Test
    fun parsesMasterPlaylistVariants() {
        val content =
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            360/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720
            720/index.m3u8
            """.trimIndent()

        val playlist = M3u8Parser.parse(content, base)

        assertIs<M3u8Playlist.Master>(playlist)
        assertEquals(2, playlist.variants.size)
        assertEquals("https://cdn.example.com/hls/movie/360/index.m3u8", playlist.variants[0].url)
        assertEquals(800000, playlist.variants[0].bandwidth)
        assertEquals("640x360", playlist.variants[0].resolution)
    }

    @Test
    fun picksHighestBandwidthVariant() {
        val variants =
            listOf(
                M3u8Variant("http://a/360.m3u8", bandwidth = 800_000),
                M3u8Variant("http://a/1080.m3u8", bandwidth = 5_000_000),
                M3u8Variant("http://a/720.m3u8", bandwidth = 2_400_000),
            )

        assertEquals("http://a/1080.m3u8", M3u8Parser.pickBestVariant(variants)?.url)
    }

    @Test
    fun picksFirstVariantWhenBandwidthMissing() {
        val variants = listOf(M3u8Variant("http://a/x.m3u8", bandwidth = -1))
        assertEquals("http://a/x.m3u8", M3u8Parser.pickBestVariant(variants)?.url)
        assertEquals(null, M3u8Parser.pickBestVariant(emptyList()))
    }

    // ==================== 边界 ====================

    @Test
    fun unsupportedForNonPlaylistResponse() {
        // 站点用 .m3u8 后缀返回直链 mp4 的情况真实存在 —— 此时必须让调用方
        // 回退到普通文件下载，而不是产出 0 字节的「合并结果」。
        assertIs<M3u8Playlist.Unsupported>(M3u8Parser.parse("<html>403</html>", base))
        assertIs<M3u8Playlist.Unsupported>(M3u8Parser.parse("", base))
    }

    @Test
    fun toleratesMissingExtm3uHeader() {
        val content =
            """
            #EXTINF:9.0,
            seg0.ts
            """.trimIndent()

        assertIs<M3u8Playlist.Media>(M3u8Parser.parse(content, base))
    }

    // ==================== URL 补全 ====================

    @Test
    fun resolvesRelativeUrls() {
        // 绝对地址原样返回
        assertEquals("https://x/y.ts", M3u8Parser.resolveUrl(base, "https://x/y.ts"))
        // 协议相对
        assertEquals("https://x/y.ts", M3u8Parser.resolveUrl(base, "//x/y.ts"))
        // 根相对
        assertEquals("https://cdn.example.com/y.ts", M3u8Parser.resolveUrl(base, "/y.ts"))
        // 同级相对
        assertEquals("https://cdn.example.com/hls/movie/y.ts", M3u8Parser.resolveUrl(base, "y.ts"))
        // 带上查询串的 base 不应把参数带进目录
        assertEquals(
            "https://cdn.example.com/hls/movie/y.ts",
            M3u8Parser.resolveUrl("$base?token=abc", "y.ts"),
        )
    }
}
