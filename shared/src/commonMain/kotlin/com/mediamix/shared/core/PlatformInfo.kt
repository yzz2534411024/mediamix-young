package com.mediamix.shared.core

/**
 * 平台标识。
 *
 * **为什么需要它**：项目里有多处「同一份 commonMain 代码，在两个平台上必须走不同路径」
 * 的地方 —— 例如播放缓存（Android 由 Media3 `SimpleCache` 在数据源层接管，
 * Desktop 需要进程内 HTTP 代理）、文件 IO（Desktop 可用 `java.io.File`，
 * Android 要按 scoped storage 规则走）。用运行时类型猜测（如
 * `Class.forName("android.os.Build")`）既慢又容易被混淆器破坏，
 * 因此提供一个编译期确定的 `expect/actual` 常量。
 */
expect object PlatformInfo {
    /** 当前是否为 Android 运行时 */
    val isAndroid: Boolean

    /** 平台名（诊断展示用） */
    val name: String
}
