package com.mediamix.shared.spider

/**
 * 按候选 key 依次向壳申请蜘蛛实例，**取到即止**。
 *
 * 抽成纯函数是为了能在 commonTest / desktopTest 里覆盖——这段逻辑此前写在
 * Android 端的反射调用里，出过一个隐蔽的错：内层把两个候选 key **无条件都问了一遍**，
 * 用后一个候选的返回值覆盖了前一个已经拿到的实例；只要第二个候选返回 null，
 * 明明已经成功的调用也会被判定成「未取到实例」。抽象成 firstNotNullOfOrNull 之后，
 * 「取到即停」是结构上保证的，不依赖调用方记得 break。
 *
 * @param keyCandidates 候选 key（如 `csp_DouDouGuard` 与去前缀的 `DouDouGuard`），按优先级排列
 * @param fetch 向壳申请实例；返回 null 表示该 key 未命中
 * @return 首个非 null 的实例；全部落空返回 null
 */
internal fun <T : Any> pickSpiderInstance(
    keyCandidates: List<String>,
    fetch: (String) -> T?,
): T? = keyCandidates.firstNotNullOfOrNull { key -> fetch(key) }
