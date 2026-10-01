package com.mediamix.shared.spider

import android.content.Context
import co.touchlab.kermit.Logger
import com.mediamix.shared.network.HttpClientFactory
import dalvik.system.DexClassLoader
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
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
    private val httpClient by lazy { HttpClientFactory.createHttpClient(requestTimeoutSeconds = 60) }

    private var classLoader: DexClassLoader? = null
    private val loadedSpiders = mutableMapOf<String, Any>()

    /**
     * 壳的 `Init` 单例（提供 `getSpider` 入口）。
     *
     * ⚠️ **必须全进程唯一**：此前 `loadSpiderJar` 与 `invokeMethod` 各 `newInstance()` 一次，
     * 壳被实例化了两次。当前没炸只是侥幸（native 层可能用静态句柄），
     * 但重复初始化随时可能让两级状态不一致 —— 统一收敛到 [ensureShellInit]。
     */
    @Volatile
    private var shellInitInstance: Any? = null

    /** 已完成 init(Context, ext) 的站点 key。 */
    private val siteInited = mutableMapOf<String, Boolean>()

    actual val isInitialized: Boolean get() = classLoader != null

    /**
     * 取壳的 `Init` 实例，必要时创建并调用 `init(Context)`。
     *
     * 幂等：同一进程内只会真正 `newInstance()` 一次。
     */
    private fun ensureShellInit(
        loader: ClassLoader,
        ctx: Context,
    ): Any {
        shellInitInstance?.let { return it }
        synchronized(this) {
            shellInitInstance?.let { return it }
            val initCls = loader.loadClass(SHELL_INIT_CLASS)
            val inst = initCls.getDeclaredConstructor().newInstance()
            runCatching {
                initCls.getMethod("init", Context::class.java).invoke(inst, ctx.applicationContext)
            }.onFailure { logger.w { "壳 Init.init(Context) 调用失败: ${it.message}" } }
            logger.i { "蜘蛛壳 Init 已实例化" }
            return inst.also { shellInitInstance = it }
        }
    }

    actual suspend fun loadSpiderJar(jarPath: String): Boolean {
        val ctx =
            appContext() ?: run {
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
            // 兼容两种写法：`url;md5;<hash>`（饭太硬实测是这种三段式）与 `url;<hash>`。
            // 不能用固定下标 —— 旧实现取 getOrNull(2)，遇到两段式会直接落空，
            // 一旦落空就同时失去「完整性校验」与「按 md5 缓存命中」两项能力。
            val expectedMd5 = parts.drop(1).firstOrNull { it.length == 32 && it.all(::isHexDigit) }

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
                // 旧版本文件可能是只读的，覆盖前先删
                dest.delete()
                dest.writeBytes(bytes)
                logger.i { "蜘蛛包下载完成: ${dest.name}（${bytes.size} bytes）" }
            }

            // ⚠️ W^X 安全策略（实测荣耀 Magic6 Pro / Android 15 抛出）：
            // API 29+ 禁止 DexClassLoader 加载**应用可写目录**里的 dex，
            // 否则 SecurityException: "Writable dex file ... is not allowed"。
            // 成熟方案（TVBox 原版/FongMi 同款）：加载前置为只读，检查即通过。
            if (dest.canWrite()) {
                dest.setReadOnly()
                logger.i { "蜘蛛包已置只读（绕过 writable-dex 检查）" }
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
                val initInstance = ensureShellInit(classLoader!!, ctx)

                // 5. 全局解密等待（只做一次，所有站点共享）：
                //    壳解密在后台线程异步进行，未完成时 getSpider 拿不到实例。
                //    用壳 dex 里已知存在的站点类当探针轮询，最多约 15 秒；
                //    超时也继续（让具体站点调用时自行报错，不阻塞整个源）。
                val getSpider = initInstance.javaClass.getMethod("getSpider", String::class.java)
                var backoffMs = 1500L
                var decrypted = false
                repeat(5) { attempt ->
                    if (!decrypted) {
                        val probe =
                            try {
                                getSpider.invoke(initInstance, PROBE_SITE_KEY)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (_: Throwable) {
                                null
                            }
                        if (probe != null) {
                            decrypted = true
                            logger.i { "壳解密完成（第 ${attempt + 1} 轮探针命中）" }
                        }
                    }
                    // 命中后不再继续 sleep —— 旧写法用 return@repeat 只跳出「本轮」，
                    // 仍会把后续 4 轮间隔全部睡完。
                    if (!decrypted) {
                        kotlinx.coroutines.delay(backoffMs)
                        backoffMs *= 2
                    }
                }
                if (!decrypted) {
                    logger.w { "壳解密探针未命中（可能仍在解密或站点 key 形式不同，继续加载流程）" }
                }
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
        val ctx = appContext() ?: return mapOf<String, Any?>("code" to -1, "msg" to "缺少 Context")

        return try {
            // 饭太硬壳的公开 API：Init.getSpider(String) 直接返回蜘蛛实例（native 层管理）。
            // 走壳入口而不是自己 loadClass —— Guard 类的定义在解密产物里，直接 loadClass 会失败。
            val initInstance = ensureShellInit(loader, ctx)

            // getSpider 的 key 形式做两个候选：csp_ 原文 / 去前缀
            val keyCandidates =
                listOf(spiderKey, spiderKey.removePrefix("csp_")).filter { it.isNotBlank() }.distinct()
            val getSpider = initInstance.javaClass.getMethod("getSpider", String::class.java)

            var lastErr: Throwable? = null
            var spiderObj: Any? = null

            // 解密等待已集中在 loadSpiderJar（全局探针）；这里只做短重试兜底。
            // ⚠️ 重试条件是「整轮候选都没命中」—— 旧实现在内层把两个候选**无条件都问一遍**，
            // 于是后一个候选返回 null 时会覆盖掉前一个已经拿到手的实例，
            // 明明成功也会被判定成「未取到实例」。pickSpiderInstance 用
            // firstNotNullOfOrNull 从结构上保证「取到即停」。
            for (attempt in 0..2) {
                if (attempt > 0) kotlinx.coroutines.delay(attempt * 2000L)
                spiderObj =
                    pickSpiderInstance(keyCandidates) { key ->
                        try {
                            getSpider.invoke(initInstance, key)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: InvocationTargetException) {
                            lastErr = e.targetException ?: e
                            null
                        } catch (e: Throwable) {
                            lastErr = e
                            null
                        }
                    }
                if (spiderObj != null) {
                    if (attempt > 0) logger.i { "getSpider 在第 ${attempt + 1} 轮成功" }
                    break
                }
            }

            if (spiderObj == null) {
                return mapOf<String, Any?>(
                    "code" to -1,
                    "msg" to "getSpider 未取到蜘蛛实例: $spiderKey（${lastErr?.message ?: "壳未解密"}）",
                )
            }

            val sCls = spiderObj.javaClass

            // 站点级初始化：init(Context, ext) —— ext 是站点级配置（TVBox 约定）。
            // 每个站点只做一次；双参签名失败时回退单参 init(Context)。
            siteInited.getOrPut(spiderKey) {
                val ext = args["ext"] as? String ?: ""
                runCatching {
                    sCls
                        .getMethod("init", Context::class.java, String::class.java)
                        .invoke(spiderObj, ctx.applicationContext, ext)
                }.onFailure {
                    logger.d { "init(Context, ext) 失败，尝试 init(Context): ${it.message}" }
                    runCatching {
                        sCls.getMethod("init", Context::class.java).invoke(spiderObj, ctx.applicationContext)
                    }
                }
                true
            }

            val prepared = prepareInvoke(method, args)
            if (prepared == null) {
                return mapOf<String, Any?>("code" to -1, "msg" to "未知蜘蛛方法: $method")
            }
            val (paramTypes, values) = prepared
            val m = findMethod(sCls, method, paramTypes)
            val raw = m.invoke(spiderObj, *values.toTypedArray())
            val result = convertResult(raw)
            if (result["code"] != -1) {
                // 成功路径也留痕 —— 这是判断「壳返回空」还是「映射丢了」的**唯一**依据：
                //   keys 含 list 且长度 > 0 → 数据回来了，问题在映射层；
                //   list 缺失或长度为 0 → 站点自身没吐数据，不是 app 的缺陷。
                // 原文前若干字符用于确认返回的是 JSON 还是别的形态（少数蜘蛛返回纯文本）。
                val listSize = (result["list"] as? List<*>)?.size
                val preview =
                    (raw as? String)?.take(RAW_PREVIEW_CHARS)
                        ?: raw?.javaClass?.simpleName
                        ?: "null"
                logger.i {
                    "蜘蛛方法成功: $spiderKey.$method → keys=${result.keys.take(8)} " +
                        "list=${listSize ?: "-"} | 原文: $preview"
                }
            }
            result
        } catch (e: ClassNotFoundException) {
            logger.e(e) { "蜘蛛类未找到: $spiderKey" }
            mapOf<String, Any?>("code" to -1, "msg" to "蜘蛛类未找到: ${e.message}")
        } catch (e: NoSuchMethodException) {
            logger.e(e) { "蜘蛛方法不存在: $spiderKey.$method" }
            mapOf<String, Any?>("code" to -1, "msg" to "方法不存在: $method")
        } catch (e: InvocationTargetException) {
            logger.e(e) { "蜘蛛方法执行异常: $spiderKey.$method" }
            mapOf<String, Any?>("code" to -1, "msg" to "方法执行异常: ${e.targetException?.message ?: e.message}")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消必须向上传播，不能吞掉 —— 否则外层 loadJob 取消语义失效
            throw e
        } catch (e: Exception) {
            logger.e(e) { "调用蜘蛛方法失败: $spiderKey.$method" }
            mapOf<String, Any?>("code" to -1, "msg" to (e.message ?: "未知错误"))
        }
    }

    actual fun release() {
        // DexClassLoader 无 close；释放实例缓存即可。
        // 缓存目录随应用卸载清理，重复加载同一 md5 时按缓存命中跳过。
        //
        // ⚠️ 壳实例与站点「已 init」标记都必须一起清 —— 它们持有解密产物的引用，
        // 只清 classLoader 会让下一次 loadSpiderJar 复用已失效的 Init 实例。
        classLoader = null
        loadedSpiders.clear()
        siteInited.clear()
        shellInitInstance = null
        logger.i { "JavaBridgeManager 已释放" }
    }

    // ==================== 内部辅助 ====================

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

    private fun ByteArray.md5(): String = MessageDigest.getInstance("MD5").digest(this).joinToString("") { "%02x".format(it) }

    private fun File.md5(): String = readBytes().md5()

    private fun String.md5(): String = encodeToByteArray().md5()

    actual companion object {
        /** 饭太硬加固壳的初始化入口（实测壳 dex 中存在 `com.github.catvod.spider.Init`）。 */
        private const val SHELL_INIT_CLASS = "com.github.catvod.spider.Init"

        /** 解密探针用的站点 key：壳 dex 字符串表里确认存在 DouDouGuard（对应配置 csp_DouDouGuard）。 */
        private const val PROBE_SITE_KEY = "csp_DouDouGuard"

        /** 成功日志里附带的返回原文预览长度（够看清是 JSON 还是别的形态即可）。 */
        private const val RAW_PREVIEW_CHARS = 400

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

/** 是否是十六进制字符（用于从 `url;md5;<hash>` 里识别 32 位 md5 段）。 */
private fun isHexDigit(c: Char): Boolean = c.isDigit() || c in 'a'..'f' || c in 'A'..'F'
