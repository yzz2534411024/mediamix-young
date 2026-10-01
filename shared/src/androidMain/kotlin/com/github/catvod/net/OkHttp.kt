package com.github.catvod.net

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/**
 * TVBox 蜘蛛生态的 HTTP 入口 —— spider.jar 里的 csp_* 类大量调用
 * `OkHttp.string(url, headers)` / `OkHttp.newCall(...)` 等静态方法，
 * 宿主必须提供。签名按 FongMi/TV 开源版对齐，实现基于 HttpURLConnection
 * （避免与宿主自身的 Ktor 栈纠缠）。
 *
 * 常用 UA：很多资源站校验 User-Agent，缺省给 Chrome 桌面 UA。
 */
object OkHttp {

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36"

    @JvmStatic
    fun string(url: String): String = string(url, null)

    @JvmStatic
    fun string(url: String, headers: Map<String, String>?): String = newCall(url, headers, null, "GET").body

    @JvmStatic
    fun string(url: String, headers: Map<String, String>?, body: String?): String =
        if (body == null) string(url, headers) else post(url, headers, body).body

    @JvmStatic
    fun get(url: String): ByteArray = newCall(url, null, null, "GET").bytes()

    @JvmStatic
    fun post(url: String, body: String): OkResult = post(url, null, body)

    @JvmStatic
    fun post(url: String, headers: Map<String, String>?, body: String?): OkResult =
        newCall(url, headers, body, "POST")

    @JvmStatic
    fun newCall(url: String, headers: Map<String, String>?): OkResult = newCall(url, headers, null, "GET")

    @JvmStatic
    fun newCall(
        url: String,
        headers: Map<String, String>?,
        body: String?,
        method: String,
    ): OkResult = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TimeUnit.SECONDS.toMillis(10).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
            instanceFollowRedirects = true
            requestMethod = method
            setRequestProperty("User-Agent", DEFAULT_UA)
            headers?.forEach { (k, v) -> if (k.isNotBlank() && v != null) setRequestProperty(k, v) }
            if (body != null && method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                outputStream.use { it.write(body!!.toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { r -> r.readText() } ?: ""
        conn.disconnect()
        OkResult(text, code, url)
    }.getOrElse { OkResult("", 0, url, it) }

    @JvmStatic
    fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}
