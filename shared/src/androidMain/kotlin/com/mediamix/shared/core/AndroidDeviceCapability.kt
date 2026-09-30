package com.mediamix.shared.core

import android.media.MediaCodecList
import android.os.Build

/**
 * Android actual — DeviceCapability
 *
 * 使用 MediaCodecList 探测硬件编解码能力。
 */
actual class DeviceCapability actual constructor() {

    private val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

    actual fun supportsHardwareDecoding(codec: String): Boolean {
        // TODO: 遍历 MediaCodecList 查找匹配 codec 的硬件解码器
        // 当前骨架：基于已知平台能力返回保守默认值
        return when (codec.uppercase()) {
            "H.264", "AVC" -> true
            "H.265", "HEVC" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            "VP9" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            else -> false
        }
    }

    actual fun getMaxResolution(): Pair<Int, Int> {
        // TODO: 通过 MediaCodecInfo.CodecCapabilities 获取实际最大分辨率
        // 骨架：返回 4K
        return Pair(3840, 2160)
    }

    actual fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }
}