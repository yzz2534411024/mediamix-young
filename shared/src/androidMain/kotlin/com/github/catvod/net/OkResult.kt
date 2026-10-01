package com.github.catvod.net

/**
 * TVBox 蜘蛛生态的 HTTP 结果封装 —— FongMi 版的 OkResult 持有 okhttp3.Response，
 * 这里用等价字段（body/code/url/error）实现，避免与宿主 Ktor 栈耦合。
 */
class OkResult(
    val body: String,
    val code: Int,
    val url: String,
    val error: Throwable? = null,
) {
    /** 大多数蜘蛛只关心「有没有拿到非空内容」。 */
    fun isSuccessful(): Boolean = code in 200..299 && body.isNotEmpty()

    fun bytes(): ByteArray = body.toByteArray(Charsets.UTF_8)

    override fun toString(): String = body
}
