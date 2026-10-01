package com.mediamix.shared.spider

import java.nio.charset.Charset

// / GBK 解码实现（JVM 平台）
internal actual fun decodeGbkBytes(bytes: ByteArray): String? =
    try {
        String(bytes, Charset.forName("GBK"))
    } catch (_: Exception) {
        null
    }
