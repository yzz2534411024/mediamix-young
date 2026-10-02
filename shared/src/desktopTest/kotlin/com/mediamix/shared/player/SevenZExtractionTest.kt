package com.mediamix.shared.player

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证 commons-compress 能解开官方 mpv-dev 包的 7z（含 BCJ2 过滤器）。
 *
 * 这是「libmpv 按需下载」方案成立的前提：官方 dev 包是 7z，且对 exe/dll 用了
 * BCJ2 分支过滤器 —— Python 的 py7zr 直接不支持（报 UnsupportedCompressionMethodError），
 * 所以必须先确认 Java 侧解压链路可用，否则下载下来也装不上。
 *
 * 素材是开发者本机的 mpv-dev 包，不存在时跳过（不阻塞 CI / 其他机器）。
 * 手动跑：./gradlew :shared:desktopTest --tests "*SevenZ*"
 */
class SevenZExtractionTest {
    private val sample =
        File("E:/mpv-dev-x86_64-20261001-git-3186d369f9.7z").takeIf { it.isFile }

    @Test
    fun canExtractLibmpvFromOfficialDevPackage() {
        assumeTrue("本机没有 mpv-dev 7z 素材，跳过", sample != null)
        val out = File(System.getProperty("java.io.tmpdir"), "mediamix-7z-test")
        out.mkdirs()

        var extracted: File? = null
        SevenZFile(sample!!).use { sz ->
            var entry = sz.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                if (name == "libmpv-2.dll") {
                    val target = File(out, name)
                    target.outputStream().use { os -> sz.getInputStream(entry).copyTo(os) }
                    extracted = target
                    break
                }
                entry = sz.nextEntry
            }
        }

        val f = extracted
        assertTrue(f != null && f.isFile, "应从 7z 中解出 libmpv-2.dll")
        // 官方 dev 包里的 libmpv-2.dll 是 110 MB 级
        assertTrue(f!!.length() > 50L * 1024 * 1024, "解出的 dll 大小异常: ${f.length()} bytes")
        println("解压成功: ${f.absolutePath}, ${f.length() / 1024 / 1024} MB")
    }
}
