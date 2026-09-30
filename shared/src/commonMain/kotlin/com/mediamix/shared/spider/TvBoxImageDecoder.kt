@file:OptIn(ExperimentalEncodingApi::class)

package com.mediamix.shared.spider

import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/// TVBox 图片伪装配置解码器
/// 饭太硬等 TVBox 接口使用图片伪装技术隐藏 JSON 配置：
/// [图片数据] [8字符标识] ** [Base64编码的JSON]
/// 支持 JPEG (FF D8...FF D9) 和 BMP (BM...) 两种伪装格式
object TvBoxImageDecoder {

    /// 从二进制数据中提取 TVBox JSON 配置
    /// 支持以下格式：
    /// 1. JPEG 伪装：FF D8 ... FF D9 [标识]**[Base64 JSON]
    /// 2. BMP 伪装：BM ... [标识]**[Base64 JSON]
    /// 3. 纯 Base64 文本
    /// 4. 纯 JSON 文本
    fun decode(bytes: ByteArray): JsonObject? {
        if (bytes.isEmpty()) return null

        // 尝试方式1：JPEG 伪装格式
        val jpegResult = decodeFromJpeg(bytes)
        if (jpegResult != null) return jpegResult

        // 尝试方式2：BMP 伪装格式
        val bmpResult = decodeFromBmp(bytes)
        if (bmpResult != null) return bmpResult

        // 尝试方式3：直接作为 UTF-8 文本解析
        try {
            val text = bytes.toString(Charsets.UTF_8)
            val decoded = decodeText(text)
            if (decoded != null) return decoded
        } catch (_: Exception) {}

        // 方式4：尝试 GBK 解码（部分 CMS 站点使用 GBK 编码）
        try {
            val text = decodeGbkBytes(bytes)
            if (text != null) {
                val decoded = decodeText(text)
                if (decoded != null) return decoded
            }
        } catch (_: Exception) {}

        // 方式5：提取所有可打印 ASCII 字符后尝试解码
        val asciiText = bytes.filter { it in 32..126 }.toByteArray().toString(Charsets.UTF_8)
        if (asciiText.isNotEmpty()) {
            val decoded = decodeText(asciiText)
            if (decoded != null) return decoded
        }

        return null
    }

    /// 从 JPEG 伪装格式中提取 JSON
    /// JPEG 可能包含嵌入缩略图（也有 FF D9 标记），
    /// 必须找到最后一个 FF D9 才是真正的主图结束位置
    internal fun decodeFromJpeg(bytes: ByteArray): JsonObject? {
        // 检查是否为 JPEG 文件（FF D8 开头）
        if (bytes.size < 4) return null
        if (bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null

        // 查找最后一个 JPEG 结束标记 FF D9（避免缩略图干扰）
        var jpegEnd = -1
        for (i in bytes.size - 2 downTo 0) {
            if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD9.toByte()) {
                jpegEnd = i + 2
                break
            }
        }

        if (jpegEnd < 0 || jpegEnd >= bytes.size - 2) return null

        // 提取 JPEG 之后的附加数据
        val trailing = bytes.copyOfRange(jpegEnd, bytes.size)
        return decodeTrailing(trailing)
    }

    /// 从 BMP 伪装格式中提取 JSON
    /// BMP 文件头格式：BM [4字节文件大小] [4字节保留] [4字节偏移]
    /// 附加数据可能在 BMP 数据之后
    internal fun decodeFromBmp(bytes: ByteArray): JsonObject? {
        // 检查是否为 BMP 文件（BM 开头）
        if (bytes.size < 14) return null
        if (bytes[0] != 0x42.toByte() || bytes[1] != 0x4D.toByte()) return null

        // 读取 BMP 文件头中的数据偏移量（字节 10-13，小端序）
        val dataOffset = (bytes[10].toInt() and 0xFF) or
                ((bytes[11].toInt() and 0xFF) shl 8) or
                ((bytes[12].toInt() and 0xFF) shl 16) or
                ((bytes[13].toInt() and 0xFF) shl 24)

        // 读取 BMP 文件大小（字节 2-5，小端序）
        val declaredSize = (bytes[2].toInt() and 0xFF) or
                ((bytes[3].toInt() and 0xFF) shl 8) or
                ((bytes[4].toInt() and 0xFF) shl 16) or
                ((bytes[5].toInt() and 0xFF) shl 24)

        // 策略1：如果文件实际大小大于声明大小，附加数据在声明大小之后
        if (declaredSize > 0 && bytes.size > declaredSize + 2) {
            val trailing = bytes.copyOfRange(declaredSize, bytes.size)
            val result = decodeTrailing(trailing)
            if (result != null) return result
        }

        // 策略2：如果文件实际大小大于数据偏移+像素数据，尝试从数据偏移处开始搜索
        if (dataOffset > 0 && dataOffset < bytes.size) {
            // 尝试从不同位置查找 ** 标记
            for (start in dataOffset until bytes.size - 10) {
                // 查找 ** 标记（0x2A 0x2A）
                if (bytes[start] == 0x2A.toByte() && bytes[start + 1] == 0x2A.toByte()) {
                    // 找到了 ** 标记，提取从标记前8字符开始的数据
                    val textStart = if (start >= 8) start - 8 else 0
                    val trailing = bytes.copyOfRange(textStart, bytes.size)
                    val result = decodeTrailing(trailing)
                    if (result != null) return result
                    break
                }
            }
        }

        // 策略3：直接提取所有可打印 ASCII 字符
        val asciiText = bytes.filter { it in 32..126 }.toByteArray().toString(Charsets.UTF_8)
        return decodeText(asciiText)
    }

    /// 解码图片数据之后的附加数据
    internal fun decodeTrailing(trailing: ByteArray): JsonObject? {
        if (trailing.isEmpty()) return null

        // 先尝试 UTF-8 解码（Base64 文本是 ASCII，UTF-8 兼容）
        try {
            val text = trailing.toString(Charsets.UTF_8)
            val result = decodeText(text)
            if (result != null) return result
        } catch (_: Exception) {}

        // 兜底：只取可打印 ASCII 字符
        val asciiText = trailing.filter { it in 32..126 }.toByteArray().toString(Charsets.UTF_8)
        return decodeText(asciiText)
    }

    /// 从文本中提取并解码 JSON
    internal fun decodeText(text: String): JsonObject? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // 方式1：直接是 JSON（支持注释）
        if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
            val result = parseJsonWithComments(trimmed)
            if (result != null) return result
        }

        // 方式2：包含 ** 分隔符的 Base64 格式（标识**Base64）
        val markerIdx = trimmed.indexOf("**")
        if (markerIdx >= 0) {
            // 跳过标识符，取 ** 之后的 Base64 数据
            val b64Data = trimmed.substring(markerIdx + 2).trim()
            val decoded = tryBase64Decode(b64Data)
            if (decoded != null) return decoded
        }

        // 方式3：纯 Base64 编码
        return tryBase64Decode(trimmed)
    }

    /// 尝试 Base64 解码并解析为 JSON
    internal fun tryBase64Decode(b64: String): JsonObject? {
        try {
            // 清理 Base64 字符串（移除可能的空白和非法字符）
            val cleaned = b64.replace(Regex("[^A-Za-z0-9+/=]"), "")
            if (cleaned.isEmpty()) return null

            val decoded = Base64.decode(cleaned)
            val jsonStr = decoded.toString(Charsets.UTF_8)
            return parseJsonWithComments(jsonStr)
        } catch (_: Exception) {
            return null
        }
    }

    /// 解析可能包含 JavaScript 风格注释的 JSON
    /// TVBox 配置文件常含 // 单行注释和 /* */ 多行注释
    internal fun parseJsonWithComments(text: String): JsonObject? {
        // 先尝试直接解析
        try {
            val element = Json.parseToJsonElement(text)
            if (element is JsonObject) return element
            return null
        } catch (_: Exception) {}

        // 剥离注释后再解析
        val cleaned = stripJsonComments(text)
        try {
            val element = Json.parseToJsonElement(cleaned)
            if (element is JsonObject) return element
        } catch (_: Exception) {}

        return null
    }

    /// 剥离 JSON 中的 JavaScript 风格注释
    /// 支持 // 单行注释和 /* */ 多行注释
    /// 注意：不能简单用正则，因为注释标记可能出现在字符串内
    internal fun stripJsonComments(text: String): String {
        val sb = StringBuilder()
        var i = 0
        var inString = false
        var stringChar: Char? = null

        while (i < text.length) {
            // 在字符串内，直接输出
            if (inString) {
                val ch = text[i]
                sb.append(ch)
                if (ch == '\\' && i + 1 < text.length) {
                    // 转义字符，输出下一个字符
                    i++
                    sb.append(text[i])
                } else if (ch == stringChar) {
                    inString = false
                }
                i++
                continue
            }

            // 不在字符串内
            if (text[i] == '"' || text[i] == '\'') {
                inString = true
                stringChar = text[i]
                sb.append(text[i])
                i++
            } else if (i + 1 < text.length && text[i] == '/' && text[i + 1] == '/') {
                // 单行注释：跳到行尾
                i += 2
                while (i < text.length && text[i] != '\n' && text[i] != '\r') {
                    i++
                }
                // 保留换行符以维持行号
            } else if (i + 1 < text.length && text[i] == '/' && text[i + 1] == '*') {
                // 多行注释：跳到 */
                i += 2
                while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) {
                    i++
                }
                if (i + 1 < text.length) {
                    i += 2 // 跳过 */
                }
            } else {
                sb.append(text[i])
                i++
            }
        }

        return sb.toString()
    }

    /// 检测二进制数据是否为 JPEG 伪装格式
    fun isJpegDisguise(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
    }

    /// 检测二进制数据是否为 BMP 伪装格式
    fun isBmpDisguise(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()
    }
}

/// GBK 解码 - expect 声明，actual 实现在各平台
internal expect fun decodeGbkBytes(bytes: ByteArray): String?
