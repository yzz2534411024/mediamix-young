package com.mediamix.shared.spider

import co.touchlab.kermit.Logger
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URLClassLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Desktop actual — 使用 URLClassLoader 加载 TVBox 蜘蛛 JAR
 *
 * 通过反射调用蜘蛛方法，零网络开销。
 * TVBox 蜘蛛标准方法签名：
 * - init(Map)
 * - home(int)
 * - category(String, int, Map)
 * - detail(String)
 * - search(String, int)
 * - player(String, String)
 */
actual class JavaBridgeManager private constructor() {

    private val logger = Logger.withTag("JavaBridgeManager")
    private var classLoader: URLClassLoader? = null
    private val loadedSpiders = mutableMapOf<String, Any>()
    private val json = Json { ignoreUnknownKeys = true }

    actual val isInitialized: Boolean get() = classLoader != null

    actual suspend fun loadSpiderJar(jarPath: String): Boolean {
        return try {
            val jarFile = File(jarPath)
            if (!jarFile.exists()) {
                logger.w { "JAR 文件不存在: $jarPath" }
                return false
            }

            // 创建 URLClassLoader，父加载器为当前类加载器
            classLoader = URLClassLoader(
                arrayOf(jarFile.toURI().toURL()),
                this::class.java.classLoader,
            )
            logger.i { "蜘蛛 JAR 加载成功: $jarPath" }
            true
        } catch (e: Exception) {
            logger.e(e) { "加载蜘蛛 JAR 失败: $jarPath" }
            false
        }
    }

    actual suspend fun invokeMethod(
        spiderKey: String,
        method: String,
        args: Map<String, Any?>,
    ): Map<String, Any?> {
        val loader = classLoader ?: return mapOf<String, Any?>("code" to -1, "msg" to "未初始化，请先调用 loadSpiderJar")

        return try {
            // 根据 spiderKey 查找对应的 Java 类名
            val className = mapSpiderKeyToClassName(spiderKey)
            val clazz = loader.loadClass(className)

            // 获取或创建蜘蛛实例（缓存）
            val spiderInstance = loadedSpiders.getOrPut(spiderKey) {
                clazz.getDeclaredConstructor().newInstance().also {
                    logger.i { "蜘蛛实例已创建: $spiderKey ($className)" }
                }
            }

            // 查找方法
            val methodObj = findMethod(clazz, method)

            // 准备参数并调用
            val preparedArgs = prepareArgs(method, args)
            val result = methodObj.invoke(spiderInstance, *preparedArgs)

            // 转换结果为 Map
            convertToMap(result)
        } catch (e: ClassNotFoundException) {
            logger.e(e) { "蜘蛛类未找到: spiderKey=$spiderKey" }
            mapOf<String, Any?>("code" to -1, "msg" to "蜘蛛类未找到: ${e.message}")
        } catch (e: NoSuchMethodException) {
            logger.e(e) { "蜘蛛方法不存在: spiderKey=$spiderKey, method=$method" }
            mapOf<String, Any?>("code" to -1, "msg" to "方法不存在: $method")
        } catch (e: InvocationTargetException) {
            logger.e(e) { "蜘蛛方法调用异常: spiderKey=$spiderKey, method=$method" }
            mapOf<String, Any?>("code" to -1, "msg" to "方法执行异常: ${e.targetException?.message ?: e.message}")
        } catch (e: Exception) {
            logger.e(e) { "调用蜘蛛方法失败: spiderKey=$spiderKey, method=$method" }
            mapOf<String, Any?>("code" to -1, "msg" to (e.message ?: "未知错误"))
        }
    }

    actual fun release() {
        try {
            classLoader?.close()
        } catch (e: Exception) {
            logger.w(e) { "关闭 ClassLoader 时出错" }
        }
        classLoader = null
        loadedSpiders.clear()
        logger.i { "JavaBridgeManager 已释放" }
    }

    // ---- 内部辅助方法 ----

    /**
     * 将 spiderKey 映射为 Java 类名
     *
     * TVBox 蜘蛛类名通常与 api 字段一致，如 csp_FanTaiYing。
     * 如果包含包名分隔符则直接使用，否则作为简单类名处理。
     */
    private fun mapSpiderKeyToClassName(spiderKey: String): String {
        // 如果 spiderKey 本身就是全限定类名（含点号），直接使用
        // 否则保持原名（TVBox 蜘蛛通常用 csp_XXX 格式）
        return spiderKey
    }

    /**
     * 根据 TVBox 蜘蛛标准方法名查找对应的 Method 对象
     *
     * TVBox 蜘蛛标准方法签名：
     * - init(Map context)
     * - homeContent(int page) → 返回 String (JSON)
     * - categoryContent(String tid, int page, Map filter) → String
     * - detailContent(String id) → String
     * - searchContent(String keyword, int page) → String
     * - playerContent(String flag, String id) → String
     */
    private fun findMethod(clazz: Class<*>, methodName: String): Method {
        return when (methodName) {
            "init" -> clazz.getMethod("init", Map::class.java)
            "home", "homeContent" -> clazz.getMethod("homeContent", Int::class.java)
            "category", "categoryContent" -> clazz.getMethod(
                "categoryContent",
                String::class.java,
                Int::class.java,
                Map::class.java,
            )
            "detail", "detailContent" -> clazz.getMethod("detailContent", String::class.java)
            "search", "searchContent" -> clazz.getMethod(
                "searchContent",
                String::class.java,
                Int::class.java,
            )
            "player", "playerContent" -> clazz.getMethod(
                "playerContent",
                String::class.java,
                String::class.java,
            )
            else -> throw NoSuchMethodException("未知蜘蛛方法: $methodName")
        }
    }

    /**
     * 将 Kotlin Map 参数转换为 Java 方法所需的参数数组
     */
    @Suppress("UNCHECKED_CAST")
    private fun prepareArgs(methodName: String, args: Map<String, Any?>): Array<Any?> {
        return when (methodName) {
            "init" -> arrayOf(args)
            "home", "homeContent" -> arrayOf(args["page"] as? Int ?: 1)
            "category", "categoryContent" -> arrayOf(
                args["tid"] as? String ?: "",
                args["page"] as? Int ?: 1,
                args["filter"] as? Map<String, String> ?: emptyMap<String, String>(),
            )
            "detail", "detailContent" -> arrayOf(args["id"] as? String ?: "")
            "search", "searchContent" -> arrayOf(
                args["keyword"] as? String ?: "",
                args["page"] as? Int ?: 1,
            )
            "player", "playerContent" -> arrayOf(
                args["flag"] as? String ?: "",
                args["id"] as? String ?: "",
            )
            else -> emptyArray()
        }
    }

    /**
     * 将 Java 方法返回值转换为 Map<String, Any?>
     *
     * TVBox 蜘蛛方法通常返回 JSON 字符串，需要解析为 Map。
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertToMap(result: Any?): Map<String, Any?> {
        return when (result) {
            is Map<*, *> -> result as Map<String, Any?>
            is String -> {
                // 尝试解析 JSON 字符串
                try {
                    val element = json.parseToJsonElement(result)
                    if (element is JsonObject) {
                        element.jsonObject.mapValues { (_, v) ->
                            val str = v.toString()
                            // 尝试还原为基本类型
                            when {
                                str == "null" -> null
                                str.toIntOrNull() != null -> str.toInt()
                                str.toLongOrNull() != null -> str.toLong()
                                str.toDoubleOrNull() != null -> str.toDouble()
                                str == "true" || str == "false" -> str.toBoolean()
                                else -> str
                            }
                        }
                    } else {
                        mapOf("data" to result)
                    }
                } catch (_: Exception) {
                    // 非 JSON 字符串，直接包装
                    mapOf("data" to result)
                }
            }
            null -> emptyMap()
            else -> mapOf("data" to result.toString())
        }
    }

    actual companion object {
        @Volatile
        private var _instance: JavaBridgeManager? = null

        actual val instance: JavaBridgeManager
            get() = _instance ?: synchronized(this) {
                _instance ?: JavaBridgeManager().also { _instance = it }
            }
    }
}
