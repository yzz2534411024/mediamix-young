package com.mediamix.shared.spider

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * JavaBridgeManager 单元测试
 *
 * 测试失败场景（文件不存在、未初始化调用等）。
 * 成功场景需要真实 JAR 文件，在 commonTest 中无法直接测试，
 * 由集成测试覆盖。
 */
class JavaBridgeManagerTest {

    @Test
    fun testSingletonInstance() {
        val instance1 = JavaBridgeManager.instance
        val instance2 = JavaBridgeManager.instance
        assertEquals(instance1, instance2, "单例应返回同一实例")
    }

    @Test
    fun testLoadNonExistentJar() = runTest {
        val manager = JavaBridgeManager.instance
        val result = manager.loadSpiderJar("/nonexistent/path/spider.jar")
        assertFalse(result, "加载不存在的 JAR 应返回 false")
    }

    @Test
    fun testInvokeWithoutInitialization() = runTest {
        val manager = JavaBridgeManager.instance
        // 确保未初始化状态
        manager.release()

        val result = manager.invokeMethod("csp_Test", "home")
        assertNotNull(result, "未初始化时调用应返回错误 Map")
        assertEquals(-1, result["code"], "错误码应为 -1")
        assertNotNull(result["msg"], "应包含错误消息")
    }

    @Test
    fun testInvokeWithInvalidSpiderKey() = runTest {
        val manager = JavaBridgeManager.instance
        manager.release()

        // 即使尝试加载不存在的 JAR 后调用
        manager.loadSpiderJar("/nonexistent/spider.jar")
        val result = manager.invokeMethod("csp_NonExistent", "home")
        assertEquals(-1, result["code"], "无效蜘蛛 key 应返回错误")
    }

    @Test
    fun testReleaseClearsState() {
        val manager = JavaBridgeManager.instance
        manager.release()
        assertFalse(manager.isInitialized, "释放后 isInitialized 应为 false")
    }
}
