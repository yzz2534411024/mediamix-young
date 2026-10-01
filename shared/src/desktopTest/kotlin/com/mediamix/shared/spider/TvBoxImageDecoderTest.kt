package com.mediamix.shared.spider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 用真实抓包数据验证 TVBox 图片伪装解码。
 *
 * 数据来源：http://www.xn--sss604efuw.net/tv（饭太硬当前实际可用的配置地址，
 * 响应为「JPEG 伪装」格式）。格式：`FF D8 ... 图片 ... FF D9 + 随机标识 + ** + Base64(JSON)`，
 * 且 JSON 内含 `//` 注释行（TVBox 生态惯例，用于停用站点）。
 *
 * 与 commonTest 里的 [TvBoxImageDecoderTest]（合成数据用例）互补：本类专门用**线上真实响应**
 * 做回归，防止解码器对真实世界的杂格式（随机标识、padding、注释行）失配。
 */
class TvBoxImageDecoderRealDataTest {

    private fun loadFixture(): ByteArray {
        val stream =
            javaClass.getResourceAsStream("/tvbox/fantaiying.bin")
                ?: error("测试资源缺失: /tvbox/fantaiying.bin")
        return stream.readBytes()
    }

    @Test
    fun `decode real fantaiying response`() {
        val bytes = loadFixture()
        assertTrue(TvBoxImageDecoder.isJpegDisguise(bytes), "应识别为 JPEG 伪装格式")

        val json = TvBoxImageDecoder.decode(bytes)
        assertNotNull(json, "解码不应返回 null —— 真实饭太硬响应必须能解出配置")

        val sites = json["sites"]?.let {
            (it as? kotlinx.serialization.json.JsonArray)?.size
        } ?: 0
        assertEquals(47, sites, "饭太硬当前配置应为 47 个站点")
    }

    @Test
    fun `decoded json contains spider and lives`() {
        val json = assertNotNull(TvBoxImageDecoder.decode(loadFixture()))

        val spider = (json["spider"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        assertTrue(spider?.startsWith("http") == true, "spider 字段应为 jar 地址，实际: $spider")

        val lives = json["lives"]
        assertNotNull(lives, "配置应含直播分组")
    }
}
