package com.github.catvod.crawler

import android.text.TextUtils
import java.util.ArrayList

/**
 * TVBox 蜘蛛调试基类 —— 很多资源站蜘蛛（如 WoGG）`extends SpiderDebug`，
 * 或在方法体里调用 [checkVideoCache]。宿主必须提供，否则 NoClassDefFoundError。
 *
 * 方法签名按 FongMi/TV 开源版对齐；checkVideoCache 返回 true 表示放行。
 */
abstract class SpiderDebug : Spider() {

    companion object {
        private val caches = ArrayList<String>()
        private var total = 0
        private var fail = 0

        @JvmStatic
        fun checkVideoCache(result: Map<String, String>): Boolean {
            if (total++ > 20) caches.clear()
            val vodId = result["vod_id"] ?: ""
            val vodPlayUrl = result["vod_play_url"] ?: ""
            if (TextUtils.isEmpty(vodPlayUrl) || caches.contains(vodId)) return false
            if (vodPlayUrl.contains("mp4") || vodPlayUrl.contains("m3u8")) caches.add(vodId)
            return true
        }

        @JvmStatic
        fun failTimes(): Int = fail

        /** 蜘蛛 catch 块里调用的日志方法 —— 签名缺失会 NoSuchMethodError（实测 WoGG）。 */
        @JvmStatic
        fun log(t: Throwable) {
            t?.printStackTrace()
        }

        @JvmStatic
        fun log(msg: String) {
            println(msg)
        }
    }
}
