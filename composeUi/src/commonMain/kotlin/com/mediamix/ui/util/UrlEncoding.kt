package com.mediamix.ui.util

/**
 * 对 URL 组件做 RFC 3986 percent-encoding（按 UTF-8 字节）。
 *
 * 为什么不用 `java.net.URLEncoder`：
 * 1. 它是 JVM-only，在 commonMain 里不可用（会把源码变成"伪 common"）；
 * 2. 它把空格编成 `+` —— 那只在 `application/x-www-form-urlencoded` 的 query 里成立，
 *    用在导航参数或路径里是错的。
 *
 * ⚠️ 注意不能用 `Char.isLetterOrDigit()` 判断是否需要编码：
 * 它对 CJK 字符同样返回 true，会导致中文标题原样进入 route 而未被转义。
 * 因此这里只放行 ASCII 的 unreserved 集合。
 */
internal fun String.encodeUrlComponent(): String {
    val sb = StringBuilder(length * 3)
    for (ch in this) {
        if (isUnreservedAscii(ch)) {
            sb.append(ch)
        } else {
            for (b in ch.toString().encodeToByteArray()) {
                val v = b.toInt() and 0xFF
                sb.append('%')
                sb.append(HEX_DIGITS[v shr 4])
                sb.append(HEX_DIGITS[v and 0x0F])
            }
        }
    }
    return sb.toString()
}

private fun isUnreservedAscii(ch: Char): Boolean =
    ch in 'a'..'z' ||
        ch in 'A'..'Z' ||
        ch in '0'..'9' ||
        ch == '-' ||
        ch == '_' ||
        ch == '.' ||
        ch == '~'

private const val HEX_DIGITS = "0123456789ABCDEF"
