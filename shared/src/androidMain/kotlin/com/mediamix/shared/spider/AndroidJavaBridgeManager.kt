package com.mediamix.shared.spider

import android.content.Context
import co.touchlab.kermit.Logger
import com.mediamix.shared.network.HttpClientFactory
import dalvik.system.DexClassLoader
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest

/**
 * Android actual — TVBox 蜘蛛包（dex/zip）加载与反射调用。
 *
 * 参考成熟实现（FongMi/TVBox 的标准加载路径）：
 * 1. 下载 spider 包（`url;md5;hash` 语法），md5 校验后缓存到 `filesDir/spiders/`
 * 2. `DexClassLoader` 直接加载 zip（其内含 classes.dex；实测饭太硬的 spider.zip
 *    是**加固壳**：壳 dex 只含 `com.github.catvod.spider.XxxGuard` 系列类，
 *    真实代码加密在 assets 里由壳解密注入 —— 对调用方透明）
 * 3. 触发壳初始化：`com.github.catvod.spider.Init` + `init(Context)`
 * 4. 类名解析：`csp_XXX` → 依次尝试
 *    `com.github.catvod.spider.XXX`（饭太硬 Guard 包）→
 *    `com.github.catvod.crawler.SpiderXXX`（官方 CatVod 约定）
 * 5. 反射调用 TVBox 标准方法签名（String 返回 JSON），多签名候选兼容不同 jar：
 *    - `homeContent(boolean)` / `homeContent()`
 *    - `categoryContent(String tid, String pg, boolean, HashMap)` / `(String, int, Map)`
 *    - `detailContent(List<String>)` / `detailContent(String)`
 *    - `searchContent(String, boolean)` / `searchContent(String, boolean, HashMap)` / `(String, int)`
 *    - `playerContent(String, String, List)` / `(String, String)`
 */
actual class JavaBridgeManager private constructor() {
    private val logger = Logger.withTag("JavaBridgeManager")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val httpClient by lazy { HttpClientFactory.createHttpClient(requestTimeoutSeconds = 60) }

    private var classLoader: DexClassLoader? = null
    private val loadedSpiders = mutableMapOf<String, Any>()

    actual val isInitialized: Boolean get() = classLoader != null

    actual suspend fun loadSpiderJar(jarPath: String): Boolean {
        val ctx = appContext() ?: run {
            logger.w { "loadSpiderJar 前必须 attach(context)：蜘蛛壳普遍需要 Context 读写解密产物" }
            return false
        }

        return try {
            // 1. 解析 `url;md5;hash` 语法（实测饭太硬：url;md5;<hash>）
            val parts = jarPath.split(";").map { it.trim() }
            val url = parts.firstOrNull { it.startsWith("http") }
            if (url == null) {
                logger.w { "spider 字段中无有效 URL: $jarPath" }
                return false
            }
            val expectedMd5 = parts.getOrNull(2)?.takeIf { it.length == 32 }

            // 2. 下载（按 md5 缓存，命中跳过）
            val spiderDir = File(ctx.filesDir, "spiders").apply { mkdirs() }
            val dest = File(spiderDir, expectedMd5 ?: "spider_${url.md5().take(16)}.zip")

            if (expectedMd5 != null && dest.exists() && dest.length() > 0 && dest.md5() == expectedMd5) {
                logger.i { "蜘蛛包缓存命中: ${dest.name}" }
            } else {
                val bytes = httpClient.get(url).readRawBytes()
                if (expectedMd5 != null) {
                    val actual = bytes.md5()
                    if (!actual.equals(expectedMd5, ignoreCase = true)) {
                        logger.e { "蜘蛛包 md5 不匹配: 期望=$expectedMd5 实际=$actual（size=${bytes.size}）" }
                        return false
                    }
                }
                dest.writeBytes(bytes)
                logger.i { "蜘蛛包下载完成: ${dest.name}（${bytes.size} bytes）" }
            }

            // 3. DexClassLoader 直接加载 zip（内含 classes.dex）
            classLoader =
                DexClassLoader(
                    dest.absolutePath,
                    ctx.codeCacheDir.absolutePath,
                    null,
                    ctx.classLoader,
                )

            // 4. 触发壳初始化（饭太硬壳有 Init 类，负责解密 assets 里的真实代码）
            runCatching {
                val initCls = classLoader!!.loadClass(SHELL_INIT_CLASS)
                val initInstance = initCls.getDeclaredConstructor().newInstance()
                runCatching {
                    initCls.getMethod("init", Context::class.java).invoke(initInstance, ctx.applicationContext)
                }
                logger.i { "蜘蛛壳 Init 已执行" }
            }.onFailure { logger.w { "壳 Init 触发失败（非致命，部分壳无此入口）: ${it.message}" } }

            logger.i { "蜘蛛 JAR 加载成功: ${dest.name}" }
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
            val clazz = resolveSpiderClass(loader, spiderKey)
            if (clazz == null) {
                return mapOf<String, Any?>("code" to -1, "msg" to "蜘蛛类未找到: $spiderKey")
            }

            val instance =
                loadedSpiders.getOrPut(spiderKey) {
                    clazz
                        .getDeclaredConstructor()
                        .newInstance()
                        .also { inst ->
                            // TVBox 标准：实例创建后先 init(Context)；
                            // 部分蜘蛛无此方法，失败不致命
                            runCatching {
                                clazz.getMethod("init", Context::class.java).invoke(inst, appContext())
                            }.onFailure { logger.d { "init(Context) 未调用成功（可忽略）: ${it.message}" } }
                        }
                }

            val prepared = prepareInvoke(method, args)
            if (prepared == null) {
                return mapOf<String, Any?>("code" to -1, "msg" to "未知蜘蛛方法: $method")
            }
            val (paramTypes, values) = prepared
            val m = findMethod(clazz, method, paramTypes)
            val raw = m.invoke(instance, *values.toTypedArray())
            convertResult(raw)
        } catch (e: ClassNotFoundException) {
            logger.e(e) { "蜘蛛类未找到: $spiderKey" }
            mapOf<String, Any?>("code" to -1, "msg" to "蜘蛛类未找到: ${e.message}")
        } catch (e: NoSuchMethodException) {
            logger.e(e) { "蜘蛛方法不存在: $spiderKey.$method" }
            mapOf<String, Any?>("code" to -1, "msg" to "方法不存在: $method")
        } catch (e: InvocationTargetException) {
            logger.e(e) { "蜘蛛方法执行异常: $spiderKey.$method" }
            mapOf<String, Any?>("code" to -1, "msg" to "方法执行异常: ${e.targetException?.message ?: e.message}")
        } catch (e: Exception) {
            logger.e(e) { "调用蜘蛛方法失败: $spiderKey.$method" }
            mapOf<String, Any?>("code" to -1, "msg" to (e.message ?: "未知错误"))
        }
    }

    actual fun release() {
        // DexClassLoader 无 close；释放实例缓存即可。
        // 缓存目录随应用卸载清理，重复加载同一 md5 时按缓存命中跳过。
        classLoader = null
        loadedSpiders.clear()
        logger.i { "JavaBridgeManager 已释放" }
    }

    // ==================== 内部辅助 ====================

    /**
     * 类名解析序（首个 loadClass 成功者胜出）：
     * 1. api 本身含点号 → 视为全限定类名
     * 2. `com.github.catvod.spider.X`（饭太硬 Guard 包，实测 csp_DouDouGuard → DouDouGuard）
     * 3. `com.github.catvod.crawler.SpiderX`（官方 CatVod 约定）
     */
    private fun resolveSpiderClass(
        loader: DexClassLoader,
        spiderKey: String,
    ): Class<*>? {
        val candidates =
            buildList {
                if (spiderKey.contains('.')) {
                    add(spiderKey)
                } else {
                    val simple = spiderKey.removePrefix("csp_")
                    add("com.github.catvod.spider.$simple")
                    add("com.github.catvod.crawler.Spider$simple")
                }
            }
        for (name in candidates) {
            runCatching { return loader.loadClass(name) }
        }
        logger.e { "所有候选类名均未找到: $candidates" }
        return null
    }

    /**
     * 按 TVBox 标准签名准备反射调用。
     *
     * 返回 (参数类型列表, 参数值列表)；null 表示未知方法。
     * 同一方法保留多签名候选（不同 jar 的重载不一），由 [findMethod] 逐一尝试。
     */
    private fun prepareInvoke(
        method: String,
        args: Map<String, Any?>,
    ): Pair<List<Class<*>>, List<Any?>>? =
        when (method) {
            "init" -> Pair(listOf(Context::class.java), listOf(appContext()))
            "home", "homeContent" -> {
                val filter = args["filter"] as? Boolean ?: false
                Pair(listOf(Boolean::class.java), listOf(filter))
            }
            "category", "categoryContent" -> {
                val tid = args["tid"] as? String ?: ""
                val pg = (args["page"] as? Int ?: 1).toString() // TVBox 标准：pg 是 String
                val filter = args["filter"] as? Boolean ?: false
                val extend = HashMap(args["extend"] as? Map<String, String> ?: emptyMap())
                Pair(
                    listOf(String::class.java, String::class.java, Boolean::class.java, HashMap::class.java),
                    listOf(tid, pg, filter, extend),
                )
            }
            "detail", "detailContent" -> {
                val id = args["id"] as? String ?: ""
                Pair(listOf(List::class.java), listOf(listOf(id)))
            }
            "search", "searchContent" -> {
                val key = args["keyword"] as? String ?: ""
                val quick = args["quick"] as? Boolean ?: false
                Pair(
                    listOf(String::class.java, Boolean::class.java),
                    listOf(key, quick),
                )
            }
            "player", "playerContent" -> {
                val flag = args["flag"] as? String ?: ""
                val id = args["id"] as? String ?: ""
                Pair(
                    listOf(String::class.java, String::class.java, List::class.java),
                    listOf(flag, id, emptyList<String>()),
                )
            }
            else -> null
        }

    /** 在 [clazz] 上按参数类型找公开方法；严格匹配失败时按同名同参数个数兜底。 */
    private fun findMethod(
        clazz: Class<*>,
        method: String,
        paramTypes: List<Class<*>>,
    ) = try {
        clazz.getMethod(method, *paramTypes.toTypedArray())
    } catch (_: NoSuchMethodException) {
        // 部分蜘蛛的重载签名不同：宽松匹配同名方法（按参数个数最接近者）
        clazz.methods
            .filter { it.name == method && it.parameterCount == paramTypes.size }
            .firstOrNull()
            ?: throw NoSuchMethodException("$method(${paramTypes.joinToString { it.simpleName }})")
    }

    /** 结果转换：TVBox 蜘蛛统一返回 JSON 字符串（可能含 // 注释）。 */
    private fun convertResult(raw: Any?): Map<String, Any?> =
        when (raw) {
            is Map<*, *> -> raw.entries.associate { it.key.toString() to it.value }
            is String -> {
                val element = TvBoxImageDecoder.parseJsonWithComments(raw)
                if (element != null) {
                    jsonElementToMap(element)
                } else {
                    mapOf<String, Any?>("data" to raw)
                }
            }
            null -> emptyMap()
            else -> mapOf<String, Any?>("data" to raw.toString())
        }

    private fun jsonElementToMap(element: JsonElement): Map<String, Any?> =
        (element as? JsonObject)?.entries?.associate { it.key to jsonToAny(it.value) }
            ?: emptyMap()

    private fun jsonToAny(e: JsonElement): Any? =
        when (e) {
            is JsonNull -> null
            is JsonPrimitive ->
                if (e.isString) {
                    e.content
                } else {
                    e.content.toIntOrNull() ?: e.content.toLongOrNull() ?: e.content.toDoubleOrNull() ?: e.content
                }
            is JsonObject -> e.entries.associate { it.key to jsonToAny(it.value) }
            is JsonArray -> e.map { jsonToAny(it) }
        }

    private fun ByteArray.md5(): String =
        MessageDigest.getInstance("MD5").digest(this).joinToString("") { "%02x".format(it) }

    private fun File.md5(): String = readBytes().md5()

    private fun String.md5(): String = encodeToByteArray().md5()

    actual companion object {
        /** 饭太硬加固壳的初始化入口（实测壳 dex 中存在 `com.github.catvod.spider.Init`）。 */
        private const val SHELL_INIT_CLASS = "com.github.catvod.spider.Init"

        @Volatile
        private var appContextRef: Context? = null

        @Volatile
        private var _instance: JavaBridgeManager? = null

        actual val instance: JavaBridgeManager
            get() =
                _instance ?: synchronized(this) {
                    _instance ?: JavaBridgeManager().also { _instance = it }
                }

        /**
         * 注入 Application Context —— 必须在 [loadSpiderJar] 之前调用。
         * 在 App.onCreate（MediaMixApp）里调用一次即可。
         */
        fun attach(context: Context) {
            appContextRef = context.applicationContext
        }

        internal fun appContext(): Context? = appContextRef
    }
}
