@file:OptIn(ExperimentalEncodingApi::class)

package com.mediamix.shared.spider

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*

class TvBoxImageDecoderTest {

    // ============================================================
    // JPEG 伪装格式检测
    // ============================================================

    @Test
    fun testIsJpegDisguise() {
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00, 0xFF.toByte(), 0xD9.toByte())
        assertTrue(TvBoxImageDecoder.isJpegDisguise(jpegBytes))
    }

    @Test
    fun testIsNotJpegDisguise() {
        assertFalse(TvBoxImageDecoder.isJpegDisguise(byteArrayOf(0x42, 0x4D, 0x00, 0x00)))
        assertFalse(TvBoxImageDecoder.isJpegDisguise(byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00)))
        assertFalse(TvBoxImageDecoder.isJpegDisguise(byteArrayOf(0xFF.toByte())))
        assertFalse(TvBoxImageDecoder.isJpegDisguise(byteArrayOf()))
    }

    // ============================================================
    // BMP 伪装格式检测
    // ============================================================

    @Test
    fun testIsBmpDisguise() {
        val bmpBytes = byteArrayOf(0x42, 0x4D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertTrue(TvBoxImageDecoder.isBmpDisguise(bmpBytes))
    }

    @Test
    fun testIsNotBmpDisguise() {
        assertFalse(TvBoxImageDecoder.isBmpDisguise(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00)))
        assertFalse(TvBoxImageDecoder.isBmpDisguise(byteArrayOf(0x42, 0x00, 0x00, 0x00)))
    }

    // ============================================================
    // JPEG 伪装格式解码
    // ============================================================

    @Test
    fun testDecodeJpegDisguise() {
        val jsonContent = """{"spider":"https://example.com/spider.jar","sites":[]}"""
        val base64Json = Base64.encode(jsonContent.encodeToByteArray())
        val identifier = "ABCD1234" // 8字符标识

        // 构造: [JPEG数据] [8字符标识]**[Base64 JSON]
        val jpegData = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // JPEG 开始
            0x00, 0x01, 0x02, // 一些JPEG数据
            0xFF.toByte(), 0xD9.toByte(), // JPEG 结束
        )
        val trailing = (identifier + "**" + base64Json).encodeToByteArray()
        val fullData = jpegData + trailing

        val result = TvBoxImageDecoder.decode(fullData)
        assertNotNull(result)
        assertEquals("https://example.com/spider.jar", result["spider"]?.toString()?.trim('"'))
    }

    @Test
    fun testDecodeJpegWithEmbeddedThumbnail() {
        // JPEG 含嵌入缩略图（中间有 FF D9），应找最后一个 FF D9
        val jsonContent = """{"key":"value"}"""
        val base64Json = Base64.encode(jsonContent.encodeToByteArray())

        val jpegData = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // JPEG 开始
            0x00, 0x01, // 缩略图数据
            0xFF.toByte(), 0xD9.toByte(), // 缩略图结束（不是真正的结束）
            0x03, 0x04, 0x05, // 主图数据
            0xFF.toByte(), 0xD9.toByte(), // 真正的主图结束
        )
        val trailing = ("IDENT01*" + "*" + base64Json).encodeToByteArray()
        val fullData = jpegData + trailing

        val result = TvBoxImageDecoder.decode(fullData)
        assertNotNull(result)
        assertEquals("value", result["key"]?.toString()?.trim('"'))
    }

    // ============================================================
    // BMP 伪装格式解码
    // ============================================================

    @Test
    fun testDecodeBmpDisguise() {
        val jsonContent = """{"name":"test"}"""
        val base64Json = Base64.encode(jsonContent.encodeToByteArray())

        // 构造 BMP 头（最小有效头 14 字节）
        val pixelDataSize = 100
        val declaredSize = 14 + pixelDataSize
        val dataOffset = 14

        val bmpHeader = byteArrayOf(
            0x42, 0x4D, // BM
            (declaredSize and 0xFF).toByte(),
            ((declaredSize shr 8) and 0xFF).toByte(),
            ((declaredSize shr 16) and 0xFF).toByte(),
            ((declaredSize shr 24) and 0xFF).toByte(),
            0x00, 0x00, 0x00, 0x00, // 保留
            (dataOffset and 0xFF).toByte(),
            ((dataOffset shr 8) and 0xFF).toByte(),
            ((dataOffset shr 16) and 0xFF).toByte(),
            ((dataOffset shr 24) and 0xFF).toByte(),
        )

        // 文件实际大小 > 声明大小，附加数据在声明大小之后
        val pixelData = ByteArray(pixelDataSize)
        val trailing = ("ABCDEFGH**" + base64Json).encodeToByteArray()
        val fullData = bmpHeader + pixelData + trailing

        val result = TvBoxImageDecoder.decode(fullData)
        assertNotNull(result)
        assertEquals("test", result["name"]?.toString()?.trim('"'))
    }

    // ============================================================
    // 纯文本解码
    // ============================================================

    @Test
    fun testDecodePureJsonText() {
        val jsonText = """{"spider":"https://example.com/jar.jar"}"""
        val bytes = jsonText.encodeToByteArray()

        val result = TvBoxImageDecoder.decode(bytes)
        assertNotNull(result)
        assertEquals("https://example.com/jar.jar", result["spider"]?.toString()?.trim('"'))
    }

    @Test
    fun testDecodeBase64Text() {
        val jsonContent = """{"key":"value"}"""
        val base64Text = Base64.encode(jsonContent.encodeToByteArray())
        val bytes = base64Text.encodeToByteArray()

        val result = TvBoxImageDecoder.decode(bytes)
        assertNotNull(result)
        assertEquals("value", result["key"]?.toString()?.trim('"'))
    }

    // ============================================================
    // JSON 注释剥离
    // ============================================================

    @Test
    fun testStripSingleLineComments() {
        val jsonWithComments = """
            {
                // 这是注释
                "key": "value" // 行尾注释
            }
        """.trimIndent()

        val result = TvBoxImageDecoder.parseJsonWithComments(jsonWithComments)
        assertNotNull(result)
        assertEquals("value", result["key"]?.toString()?.trim('"'))
    }

    @Test
    fun testStripMultiLineComments() {
        val jsonWithComments = """
            {
                /* 多行
                   注释 */
                "key": "value"
            }
        """.trimIndent()

        val result = TvBoxImageDecoder.parseJsonWithComments(jsonWithComments)
        assertNotNull(result)
        assertEquals("value", result["key"]?.toString()?.trim('"'))
    }

    @Test
    fun testCommentsInsideStringsPreserved() {
        val jsonWithCommentsInStrings = """
            {
                "url": "https://example.com/path" // 注释
            }
        """.trimIndent()

        val result = TvBoxImageDecoder.parseJsonWithComments(jsonWithCommentsInStrings)
        assertNotNull(result)
        assertEquals("https://example.com/path", result["url"]?.toString()?.trim('"'))
    }

    @Test
    fun testStripJsonCommentsStateful() {
        // 验证状态机逻辑：字符串内的 // 不应被当作注释
        val input = """{"url":"http://test.com","key":"val"}"""
        val stripped = TvBoxImageDecoder.stripJsonComments(input)
        assertEquals(input, stripped)
    }

    // ============================================================
    // 空数据和边界情况
    // ============================================================

    @Test
    fun testDecodeEmptyBytes() {
        assertNull(TvBoxImageDecoder.decode(byteArrayOf()))
    }

    @Test
    fun testDecodeRandomBytesNoCrash() {
        // 无法解析的随机数据，不应崩溃
        val randomBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        TvBoxImageDecoder.decode(randomBytes)
    }

    // ============================================================
    // 饭太硬实际样例模拟
    // ============================================================

    @Test
    fun testFanTaiYingJpegDisguise() {
        // 模拟饭太硬接口返回的 JPEG 伪装格式
        val configJson = """
            {
                "spider": "https://example.com/fantaiying.jar;md5hash123",
                "sites": [
                    {
                        "key": "csp_douban",
                        "name": "豆瓣",
                        "type": 3,
                        "api": "csp_Douban",
                        "searchable": 1,
                        "quickSearch": 0,
                        "changeable": 1
                    }
                ],
                "flags": ["qq", "iqiyi"]
            }
        """.trimIndent()

        val base64Json = Base64.encode(configJson.encodeToByteArray())
        val identifier = "FTY20260" // 8字符标识

        // 构造 JPEG 数据
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val jpegBody = ByteArray(200) { (it % 256).toByte() }
        val jpegEnd = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val trailing = (identifier + "**" + base64Json).encodeToByteArray()

        val fullData = jpegHeader + jpegBody + jpegEnd + trailing

        // 验证格式检测
        assertTrue(TvBoxImageDecoder.isJpegDisguise(fullData))
        assertFalse(TvBoxImageDecoder.isBmpDisguise(fullData))

        // 验证解码
        val result = TvBoxImageDecoder.decode(fullData)
        assertNotNull(result)

        // 验证 spider URL（含 ;md5 部分，因为这是原始 JSON 值，Parser 才做分割）
        val spiderUrl = result["spider"]?.toString()?.trim('"')
        assertEquals("https://example.com/fantaiying.jar;md5hash123", spiderUrl)

        // 验证 sites 数组存在
        val sites = result["sites"]
        assertNotNull(sites)
    }
}
