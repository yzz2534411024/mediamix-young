package com.mediamix.shared.player

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 固化「官方 mpv-dev 7z 解不开」这一结论。
 *
 * 官方 dev 包对 dll 用了 **BCJ2**（多输入/输出流过滤器）：
 *  - Python `py7zr` 直接报 UnsupportedCompressionMethodError；
 *  - Java `commons-compress` 报 `Multi input/output stream coders are not yet supported`。
 * 因此本项目**不做"下载官方 7z 再解包"**，改为自托管 zip（java.util.zip 原生可解）。
 *
 * 这个测试断言"确实解不开"，防止以后有人重新走这条死路；素材是开发者本机的
 * 官方包，不存在时跳过（不阻塞其他机器）。
 */
class SevenZExtractionTest {
    private val sample =
        File("E:/mpv-dev-x86_64-20261001-git-3186d369f9.7z").takeIf { it.isFile }

    @Test
    fun officialDevPackageCannotBeExtractedInProcess() {
        assumeTrue("本机没有 mpv-dev 7z 素材，跳过", sample != null)

        val error =
            assertFailsWith<IOException>("预期解码失败（BCJ2 不受支持）") {
                SevenZFile(sample!!).use { sz ->
                    while (sz.nextEntry != null) {
                        // 触发解码即会抛出
                    }
                }
            }
        assertTrue(
            error.message?.contains("Multi input/output") == true,
            "错误信息与预期不符（若 commons-compress 之后支持了 BCJ2，应重新评估该方案）: ${error.message}",
        )
        println("已确认官方 7z 无法进程内解压: ${error.message}")
    }
}
