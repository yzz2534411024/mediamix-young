package com.mediamix.shared.player

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * mpv 运行库的获取与缓存（**按需下载**，不再打进发行包）。
 *
 * 为什么改：libmpv-2.dll 原始 115 MB（zip 后仍 45 MB），只有「桌面端真播视频」
 * 才需要它 —— 内置等于让每次下载发行包的用户都白白背着它。改为首次运行时下载，
 * 之后常驻本地缓存复用。
 *
 * 查找顺序：
 *  1. **本地缓存**（`%LOCALAPPDATA%\CatVideo\native\`）—— 上次下载的；
 *  2. **程序同目录**（用户自己放 dll，最省事的手动方式）；
 *  3. **打包资源**（若某次构建又内置了，兼容旧路径）；
 *  4. 都没有 → 调用 [ensure] 下载：镜像回退 + zip 解压 + MD5 校验。
 *
 * 注意 7z 不可用：官方 dev 包是 7z 且带 BCJ2 过滤器，py7zr 与 commons-compress
 * 都解不开（实测 IOException: Multi input/output stream coders are not yet supported），
 * 所以本项目自托管的是 **zip**（java.util.zip 原生可解，零额外依赖）。
 */
object MpvNativeRuntime {
    private val logger = Logger.withTag("MpvNativeRuntime")

    private const val MAIN_LIB = "libmpv-2.dll"

    /** 校验值：与本项目自托管的 libmpv-2.zip 内文件一致（zhongfly/mpv-winbuild 2026-10-01）。 */
    private const val EXPECTED_MD5 = "948b3a07c57794c56c94eae6bbff6ba9"
    private const val EXPECTED_BYTES = 121129472L

    /**
     * 下载源，按序尝试（国内直连 GitHub 常不可用，故镜像优先）。
     * 换新版本时：上传新的 zip 到 Release 并改这里。
     */
    private val DOWNLOAD_URLS =
        listOf(
            "https://gh-proxy.com/https://github.com/yzz2534411024/mediamix-young/releases/download/native-runtime-v1/libmpv-2.zip",
            "https://ghproxy.net/https://github.com/yzz2534411024/mediamix-young/releases/download/native-runtime-v1/libmpv-2.zip",
            "https://github.com/yzz2534411024/mediamix-young/releases/download/native-runtime-v1/libmpv-2.zip",
        )

    /** 本地缓存目录（Windows 用 LOCALAPPDATA；其他平台退回 home）。 */
    val runtimeDir: File by lazy {
        val base =
            System.getenv("LOCALAPPDATA")
                ?: System.getProperty("user.home")
        File(base, "CatVideo/native").apply { mkdirs() }
    }

    /** 本地已有的可用 dll；需要下载时返回 null（不会触发网络）。 */
    fun existing(): File? {
        candidates().forEach { f ->
            if (f.isFile && f.length() > MIN_VALID_BYTES) return f
        }
        return null
    }

    val isReady: Boolean get() = existing() != null

    /**
     * 确保运行库可用；必要时下载（可反复调用，已就绪则立即返回）。
     *
     * @param onProgress 下载进度 0f..1f（解压/校验阶段给 1f）
     */
    suspend fun ensure(onProgress: ((Float) -> Unit)? = null): File =
        withContext(Dispatchers.IO) {
            existing()?.let { return@withContext it }

            var lastError: Throwable? = null
            for (url in DOWNLOAD_URLS) {
                try {
                    logger.i { "下载 mpv 运行库: ${url.substringBefore("//").let { url.take(60) }}…" }
                    val zip = File(runtimeDir, "libmpv-2.zip.part")
                    download(url, zip, onProgress)
                    onProgress?.invoke(1f)
                    val dll = unzipMainLib(zip)
                    zip.delete()
                    verify(dll)
                    logger.i { "mpv 运行库就绪: ${dll.absolutePath}（${dll.length() / 1024 / 1024} MB）" }
                    return@withContext dll
                } catch (t: Throwable) {
                    lastError = t
                    logger.w { "下载源失败（换下一个）: ${t.javaClass.simpleName}: ${t.message}" }
                    File(runtimeDir, "libmpv-2.zip.part").delete()
                }
            }
            throw IllegalStateException(
                "mpv 运行库下载失败（已尝试 ${DOWNLOAD_URLS.size} 个源）。" +
                    "可以把 libmpv-2.dll 手动放到：${runtimeDir.absolutePath}",
                lastError,
            )
        }

    // ---------------------------------------------------------------- 内部

    private const val MIN_VALID_BYTES = 50L * 1024 * 1024

    private fun candidates(): List<File> {
        val appDir = runCatching { File(System.getProperty("user.dir")) }.getOrNull()
        return buildList {
            add(File(runtimeDir, MAIN_LIB))
            appDir?.let { add(File(it, MAIN_LIB)) }
        }
    }

    private fun download(
        url: String,
        target: File,
        onProgress: ((Float) -> Unit)?,
    ) {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CatVideo/1.0")
        }
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) throw java.io.IOException("HTTP $code")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0) onProgress?.invoke((read.toFloat() / total).coerceIn(0f, 0.99f))
                }
            }
        }
        if (total > 0 && target.length() < total) {
            throw java.io.IOException("下载不完整: ${target.length()}/$total")
        }
    }

    private fun unzipMainLib(zip: File): File {
        val out = File(runtimeDir, MAIN_LIB)
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name.substringAfterLast('/') == MAIN_LIB) {
                    out.outputStream().use { os -> zis.copyTo(os) }
                    return out
                }
                zis.closeEntry()
            }
        }
        throw IllegalStateException("zip 内没有 $MAIN_LIB")
    }

    private fun verify(dll: File) {
        if (dll.length() != EXPECTED_BYTES) {
            throw IllegalStateException("运行库大小不符: ${dll.length()}（期望 $EXPECTED_BYTES）")
        }
        val md5 =
            MessageDigest.getInstance("MD5").let { md ->
                dll.inputStream().use { input ->
                    val buf = ByteArray(1 shl 20)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        md.update(buf, 0, n)
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
        if (!md5.equals(EXPECTED_MD5, ignoreCase = true)) {
            dll.delete()
            throw IllegalStateException("运行库校验失败: $md5")
        }
    }
}
