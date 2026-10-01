package com.github.catvod.crawler

import android.content.Context

/**
 * TVBox 蜘蛛基类 —— 供第三方 spider.jar（dex）中的 csp_* 类继承。
 *
 * ⚠️ 这是宿主必须提供的「契约类」：饭太硬等加固壳的全部 Guard 类都
 * `extends com.github.catvod.crawler.Spider`，而基类不在 spider 包里 ——
 * 宿主不提供它时，DexClassLoader 解析父类失败，所有 Guard 类无法加载
 * （实测表现为 loadClass ClassNotFoundException / getSpider 静默失败）。
 *
 * 方法签名与壳 dex 方法表逐一核对（2026-10-01，饭太硬 spider_2cc088af）：
 * init(Context) / init(Context, String ext) / homeContent(Z) / homeVideoContent() /
 * categoryContent(String, String, Z, HashMap) / detailContent(List) /
 * searchContent(String, Z) / searchContent(String, Z, String) /
 * playerContent(String, String, List) / action(String) / destroy()
 *
 * ⚠️ 用 Kotlin 而不是 Java：KMP 的 androidTarget 默认不编译 `src/androidMain/java`
 * 源码（实测 Spider.java 未进 APK）。JVM 层面 Kotlin open class 与 Java 完全等价，
 * 壳的 Java 类继承 Kotlin 类没有障碍。
 *
 * 本类必须被 ProGuard keep（见 androidApp/proguard-rules.pro 的 catvod 规则），
 * 且包名/方法签名不可改动 —— spider.jar 按此契约解析。
 */
open class Spider {

    open fun init(context: Context?) {
    }

    /** 部分壳（饭太硬 Guard 系）使用双参签名，第二个参数为站点 ext 配置。 */
    open fun init(context: Context?, extend: String?) {
    }

    open fun homeContent(filter: Boolean): String = ""

    open fun homeVideoContent(): String = ""

    open fun categoryContent(
        tid: String?,
        pg: String?,
        filter: Boolean,
        extend: HashMap<String, String>?,
    ): String = ""

    open fun detailContent(ids: List<String>?): String = ""

    open fun searchContent(key: String?, quick: Boolean): String = ""

    open fun searchContent(key: String?, quick: Boolean, extend: String?): String = ""

    open fun playerContent(flag: String?, id: String?, vipFlags: List<String>?): String = ""

    open fun action(action: String?): String = ""

    open fun destroy() {
    }
}
