package com.mediamix.shared.spider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 站点健康度回归测试。
 *
 * 「连续失败 ≥3 次就隐藏」直接解决「43 个 jar 站点每次首屏都要白等」，
 * 但误判的代价同样高（把一个好站点藏起来），因此阈值与清零语义都要锁住。
 */
class SpiderHealthStoreTest {
    @Test
    fun isUnhealthyOnlyAfterConsecutiveFailures() {
        val store = SpiderHealthStore()

        repeat(SpiderHealthStore.MAX_FAILURES - 1) { store.recordFailure("cDouDou") }
        assertFalse(store.isUnhealthy("cDouDou"), "未达阈值不应隐藏")

        store.recordFailure("cDouDou")
        assertTrue(store.isUnhealthy("cDouDou"), "连续 $SpiderHealthStore.MAX_FAILURES 次失败后应当隐藏")
    }

    @Test
    fun successResetsTheCounter() {
        val store = SpiderHealthStore()
        repeat(SpiderHealthStore.MAX_FAILURES - 1) { store.recordFailure("cDouDou") }

        // 偶发抖动不该永久拉黑：一次成功即清零
        store.recordSuccess("cDouDou")

        assertEquals(0, store.failureCount("cDouDou"))
        assertFalse(store.isUnhealthy("cDouDou"))
    }

    @Test
    fun resetClearsSingleSiteOnly() {
        val store = SpiderHealthStore()
        repeat(SpiderHealthStore.MAX_FAILURES) { store.recordFailure("a") }
        repeat(SpiderHealthStore.MAX_FAILURES) { store.recordFailure("b") }

        store.reset("a")

        assertFalse(store.isUnhealthy("a"))
        assertTrue(store.isUnhealthy("b"))
        assertEquals(setOf("b"), store.unhealthySites().keys)
    }

    @Test
    fun resetAllClearsEverything() {
        val store = SpiderHealthStore()
        repeat(SpiderHealthStore.MAX_FAILURES) { store.recordFailure("a") }
        repeat(SpiderHealthStore.MAX_FAILURES) { store.recordFailure("b") }

        store.resetAll()

        assertTrue(store.unhealthySites().isEmpty())
    }

    @Test
    fun blankKeyIsIgnored() {
        val store = SpiderHealthStore()
        store.recordFailure("")
        store.recordSuccess("")

        assertTrue(store.unhealthySites().isEmpty())
    }

    @Test
    fun persistsThroughInjectedCallbacks() {
        // 用一份内存「存储」模拟 Settings 落盘 —— 进程重启后应当仍记得哪些站点不健康
        var persisted: String? = null
        val writer = SpiderHealthStore(persistLoad = { persisted }, persistSave = { persisted = it })
        repeat(SpiderHealthStore.MAX_FAILURES) { writer.recordFailure("cDouDou") }

        val reader = SpiderHealthStore(persistLoad = { persisted })

        assertTrue(reader.isUnhealthy("cDouDou"), "失败记录必须跨进程保留，否则每次冷启都要重新白等")
        assertEquals(SpiderHealthStore.MAX_FAILURES, reader.failureCount("cDouDou"))
    }

    @Test
    fun toleratesCorruptPersistedData() {
        val store = SpiderHealthStore(persistLoad = { "garbage||=x|a=notanumber|b=2" })
        assertEquals(2, store.failureCount("b"))
        assertFalse(store.isUnhealthy("b"))
    }
}
