package com.mediamix.shared.spider

/**
 * 站点健康度记录 —— 「连续失败 ≥ [MAX_FAILURES] 次的站点暂时隐藏」。
 *
 * **为什么需要**：饭太硬的配置里有 40+ 个 jar 站点，其中相当一部分长期空返回。
 * 每次打开首页都要在它们身上各等一轮网络超时，首屏体验被最慢的那几个站点决定。
 * 记下连续失败次数后，网关在 [com.mediamix.shared.services.SourceContentGateway.loadCatalog]
 * 里直接跳过它们 —— 用户可以在诊断页查看并清除记录（「重试」）。
 *
 * 用「连续」而不是「累计」：一次偶发网络抖动不应该永久拉黑一个站点，
 * 任意一次成功即清零。
 *
 * 持久化由调用方注入 [persistLoad] / [persistSave] 两个回调完成 ——
 * shared 层不直接依赖 UI 的偏好存储实现（进程内两个网关实例也应共享同一份数据）。
 */
class SpiderHealthStore(
    private val persistLoad: () -> String? = { null },
    private val persistSave: (String) -> Unit = {},
) {
    /** 连续失败达到该次数即判定为不健康 */
    val maxFailures: Int = MAX_FAILURES

    /** 失败计数：`站点key -> 连续失败次数` */
    private val failures = mutableMapOf<String, Int>()

    init {
        load()
    }

    fun isUnhealthy(siteKey: String): Boolean = (failures[siteKey] ?: 0) >= MAX_FAILURES

    fun failureCount(siteKey: String): Int = failures[siteKey] ?: 0

    /** 连续失败的站点（key -> 次数），供诊断页展示。 */
    fun unhealthySites(): Map<String, Int> = failures.filterValues { it >= MAX_FAILURES }

    fun recordFailure(siteKey: String) {
        if (siteKey.isBlank()) return
        val next = (failures[siteKey] ?: 0) + 1
        failures[siteKey] = next
        persist()
    }

    fun recordSuccess(siteKey: String) {
        if (siteKey.isBlank()) return
        if (failures.remove(siteKey) != null) persist()
    }

    /** 清除某个站点的失败记录（诊断页「重试」）。 */
    fun reset(siteKey: String) {
        if (failures.remove(siteKey) != null) persist()
    }

    /** 清除全部失败记录。 */
    fun resetAll() {
        if (failures.isEmpty()) return
        failures.clear()
        persist()
    }

    private fun load() {
        val raw = persistLoad().orEmpty()
        raw
            .split('|')
            .mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) return@mapNotNull null
                val count = part.substring(i + 1).toIntOrNull() ?: return@mapNotNull null
                part.substring(0, i) to count
            }.forEach { (key, count) -> failures[key] = count }
    }

    private fun persist() {
        persistSave(failures.entries.joinToString("|") { "${it.key}=${it.value}" })
    }

    companion object {
        const val MAX_FAILURES = 3

        /** Settings 里的存储键（由 UI 层传入回调时使用）。 */
        const val KEY_HEALTH = "spider_site_health"
    }
}
