package com.mediamix.shared.spider

import com.mediamix.shared.models.*
import com.mediamix.shared.network.HttpClientFactory
import com.mediamix.shared.network.resolveHostAddresses
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * CMS API 视频服务
 *
 * 支持采集站 API 格式：
 * - {apiUrl}?ac=detail&pg=1 获取影片列表
 * - {apiUrl}?ac=detail&ids={vodId} 获取详情
 * - {apiUrl}?wd=关键词 搜索影片
 *
 * 优化项：
 * - DNS 预解析缓存（5 分钟 TTL）
 * - 接口预请求缓存（10 分钟 TTL）
 * - TVBoxImageDecoder 解码（调用 Task 35 的 TvBoxImageDecoder）
 */
class VideoApiService {
    private val httpClient: HttpClient =
        HttpClientFactory.createHttpClient(
            connectTimeoutSeconds = 3,
            requestTimeoutSeconds = 30,
        )
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    // DNS 预解析缓存，5 分钟 TTL
    private val dnsCache = mutableMapOf<String, DnsCacheEntry>()
    private val dnsCacheMutex = Mutex()
    private val dnsCacheTtlMs = 5 * 60 * 1000L

    // 接口预请求缓存，10 分钟 TTL
    private val prefetchCache = mutableMapOf<String, PrefetchCacheEntry>()
    private val prefetchCacheMutex = Mutex()
    private val prefetchCacheTtlMs = 10 * 60 * 1000L

    // 列表接口结果缓存，5 分钟 TTL
    //
    // 采集站列表接口实测要 2~9 秒（响应体 60~450KB），而用户的典型操作是
    // 「切分类 → 切回来」「进详情 → 返回首页」，没有缓存时每次都要重等。
    // 5 分钟足以覆盖这些来回操作，又不至于让内容显得陈旧。
    private val listCache = mutableMapOf<String, ListCacheEntry>()
    private val listCacheMutex = Mutex()
    private val listCacheTtlMs = 5 * 60 * 1000L

    /** 清空列表缓存（下拉刷新时调用）。 */
    suspend fun invalidateListCache() {
        listCacheMutex.withLock { listCache.clear() }
    }

    // 缓存的 TVBox 站点列表
    private var tvboxSites: List<Map<String, Any>>? = null

    // ==================== DNS 预解析 ====================

    suspend fun prefetchDns(apiUrls: List<String>) {
        val now = currentTimeMillis()
        val hosts = mutableSetOf<String>()

        for (url in apiUrls) {
            try {
                val normalizedUrl = if (url.startsWith("http")) url else "http://$url"
                val host = URLBuilder(normalizedUrl).host
                if (host.isEmpty()) continue

                dnsCacheMutex.withLock {
                    val cached = dnsCache[host]
                    if (cached != null && (now - cached.timeMs) < dnsCacheTtlMs) {
                        return@withLock
                    }
                    hosts.add(host)
                }
            } catch (_: Exception) {
            }
        }

        if (hosts.isEmpty()) return

        for (host in hosts) {
            resolveHost(host)
        }
    }

    /**
     * 真正解析一次主机名并缓存。
     *
     * 此前这里只是把 host 原样塞进缓存（等于没解析）。现在走 [resolveHostAddresses]，
     * 结果交给系统 DNS 缓存，紧接着发出的列表请求就能省掉一次解析往返。
     */
    private suspend fun resolveHost(host: String) {
        val addresses = resolveHostAddresses(host)
        if (addresses.isEmpty()) return
        dnsCacheMutex.withLock {
            dnsCache[host] =
                DnsCacheEntry(
                    addresses = addresses,
                    timeMs = currentTimeMillis(),
                )
        }
    }

    // ==================== 接口预请求 ====================

    suspend fun prefetchVideoInfo(
        apiUrl: String,
        vodId: String,
    ) {
        val cacheKey = "${apiUrl}_$vodId"
        val now = currentTimeMillis()

        prefetchCacheMutex.withLock {
            val cached = prefetchCache[cacheKey]
            if (cached != null && (now - cached.timeMs) < prefetchCacheTtlMs) {
                return
            }
        }

        try {
            val detail = fetchVideoDetailInternal(apiUrl, vodId, sourceKey = "")
            prefetchCacheMutex.withLock {
                prefetchCache[cacheKey] =
                    PrefetchCacheEntry(
                        data = detail,
                        timeMs = currentTimeMillis(),
                    )
            }
        } catch (_: Exception) {
        }
    }

    // ==================== 核心 API ====================

    suspend fun fetchCategories(apiUrl: String): List<VideoCategory> {
        try {
            val url = buildUrl(apiUrl, mapOf("ac" to "list"))
            val data = fetchAndDecode(url)

            // TVBox 配置格式：sites 数组 → 转为 class 分类
            val sites = data["sites"]
            if (sites is List<*>) {
                val categories = mutableListOf<VideoCategory>()
                for (i in sites.indices) {
                    val s = JsonSpider.asStringMap(sites[i]) ?: continue
                    categories.add(
                        VideoCategory(
                            typeId = i + 1,
                            typeName = (s["name"] ?: s["key"] ?: "").toString(),
                            typePid = 0,
                        ),
                    )
                }
                tvboxSites = sites.mapNotNull { JsonSpider.asStringMap(it) }
                return categories
            }

            // 标准 CMS 格式
            val categories = mutableListOf<VideoCategory>()
            val classList = data["class"] as? List<*> ?: return categories
            for (c in classList) {
                val map = JsonSpider.asStringMap(c) ?: continue
                categories.add(VideoCategory.fromJson(map))
            }
            return categories
        } catch (e: Exception) {
            throw Exception("获取分类失败: ${e.message}")
        }
    }

    suspend fun fetchVideoList(
        apiUrl: String,
        page: Int = 1,
        typeId: Int? = null,
        forceRefresh: Boolean = false,
    ): VideoListResponse {
        // 命中 5 分钟内的缓存直接返回，避免「切分类再切回来」重复等 2~9 秒
        val cacheKey = "list|$apiUrl|$page|${typeId ?: ""}"
        if (!forceRefresh) {
            listCacheMutex.withLock {
                val entry = listCache[cacheKey]
                if (entry != null && (currentTimeMillis() - entry.atMs) < listCacheTtlMs) {
                    return entry.response
                }
            }
        }
        try {
            var effectiveUrl = apiUrl

            val cachedSites = tvboxSites
            if (cachedSites != null && typeId != null && typeId > 0 && typeId <= cachedSites.size) {
                val site = cachedSites[typeId - 1]
                val ext = site["ext"]
                if (ext is String && (ext.startsWith("http://") || ext.startsWith("https://"))) {
                    effectiveUrl = ext
                }
            }

            val params = mutableMapOf("ac" to "detail", "pg" to page.toString())
            if (typeId != null && effectiveUrl == apiUrl) {
                params["t"] = typeId.toString()
            }
            val url = buildUrl(effectiveUrl, params)
            val data = fetchAndDecode(url)

            val sites = data["sites"]
            if (sites is List<*>) {
                tvboxSites = sites.mapNotNull { JsonSpider.asStringMap(it) }
                return VideoListResponse(list = emptyList(), page = page, pageCount = 0, total = 0)
            }

            val result = VideoListResponse.fromJson(data)
            listCacheMutex.withLock {
                listCache[cacheKey] = ListCacheEntry(result, currentTimeMillis())
            }
            return result
        } catch (e: Exception) {
            throw Exception("加载失败: ${e.message}")
        }
    }

    suspend fun fetchVideoDetail(
        apiUrl: String,
        vodId: String,
        sourceKey: String = "",
    ): VideoDetail {
        val cacheKey = "${apiUrl}_$vodId"
        val now = currentTimeMillis()

        val cached =
            prefetchCacheMutex.withLock {
                val entry = prefetchCache[cacheKey]
                if (entry != null && (now - entry.timeMs) < prefetchCacheTtlMs) {
                    prefetchCache.remove(cacheKey)
                    entry
                } else {
                    null
                }
            }

        if (cached != null) {
            return if (sourceKey.isNotEmpty() && cached.data.sourceKey != sourceKey) {
                cached.data.copy(sourceKey = sourceKey)
            } else {
                cached.data
            }
        }

        return fetchVideoDetailInternal(apiUrl, vodId, sourceKey = sourceKey)
    }

    private suspend fun fetchVideoDetailInternal(
        apiUrl: String,
        vodId: String,
        sourceKey: String = "",
    ): VideoDetail {
        try {
            val url = buildUrl(apiUrl, mapOf("ac" to "detail", "ids" to vodId))
            val data = fetchAndDecode(url)

            val list = data["list"] as? List<*>
            if (list == null || list.isEmpty()) {
                throw Exception("影片不存在")
            }

            val first =
                JsonSpider.asStringMap(list.first())
                    ?: throw Exception("影片数据格式错误")
            return VideoDetail.fromJson(first, sourceKey = sourceKey)
        } catch (e: Exception) {
            throw Exception("加载失败: ${e.message}")
        }
    }

    suspend fun searchVideos(
        apiUrl: String,
        keyword: String,
    ): VideoListResponse {
        try {
            val url = buildUrl(apiUrl, mapOf("wd" to keyword))
            val data = fetchAndDecode(url)

            val sites = data["sites"]
            if (sites is List<*>) {
                return VideoListResponse(list = emptyList(), page = 1, pageCount = 0, total = 0)
            }

            return VideoListResponse.fromJson(data)
        } catch (e: Exception) {
            throw Exception("搜索失败: ${e.message}")
        }
    }

    suspend fun clearAllCache() {
        dnsCacheMutex.withLock { dnsCache.clear() }
        prefetchCacheMutex.withLock { prefetchCache.clear() }
    }

    // ==================== 内部请求方法 ====================

    private suspend fun fetchAndDecode(url: String): Map<String, Any> {
        try {
            val response =
                httpClient.get(url) {
                    headers {
                        append(HttpHeaders.AcceptEncoding, "identity")
                    }
                }
            val statusCode = response.status.value
            if (statusCode != 200) {
                throw Exception("HTTP $statusCode")
            }

            val bytes = response.readBytes()

            // TODO: 调用 Task 35 的 TvBoxImageDecoder.decode(bytes)

            // 直接 UTF-8 解码后 JSON 解析
            val text = bytes.decodeToString().trim()
            if (text.startsWith("{")) {
                val parsed = parseJsonWithComments(text)
                if (parsed != null) return parsed
            }

            // 提取 ASCII 文本后尝试 Base64 解码
            val asciiText = bytes.filter { it in 32..126 }.toByteArray().decodeToString()
            if (asciiText.isNotEmpty()) {
                val result = tryParseJsonOrBase64(asciiText)
                if (result != null) return result
            }

            throw Exception("JSON 解析失败: 无法识别的响应格式 (${bytes.size} bytes)")
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * 构建请求 URL
     *
     * 对应 Dart 的 _buildUrl()
     */
    fun buildUrl(
        apiUrl: String,
        params: Map<String, String>,
    ): String {
        var url = apiUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        val builder = URLBuilder(url)
        val scheme = builder.protocol.name
        val host = builder.host
        val port = builder.port
        val path = builder.encodedPath
        val portStr = if (port != 0 && port != 80 && port != 443) ":$port" else ""
        val query =
            params.entries.joinToString("&") { (k, v) ->
                "$k=${JsonSpider.encodeUrlComponent(v)}"
            }

        // 检查原始 URL 是否已有查询参数
        val existingQuery = url.substringAfter('?', "")
        val separator = if (existingQuery.isNotEmpty()) "&" else "?"

        return "$scheme://$host$portStr$path$separator$query"
    }

    /**
     * 尝试解析含 ** 分隔符的 Base64 或纯 Base64 文本
     */
    fun tryParseJsonOrBase64(text: String): Map<String, Any>? {
        // 方式1：找 ** 分隔符后的 Base64
        val markerIdx = text.indexOf("**")
        if (markerIdx >= 0) {
            val b64Data = text.substring(markerIdx + 2).trim()
            if (b64Data.isNotEmpty()) {
                try {
                    val cleaned = b64Data.replace(Regex("[^A-Za-z0-9+/=]"), "")
                    if (cleaned.length >= 4) {
                        val decoded = decodeBase64(cleaned)
                        if (decoded != null) {
                            val jsonStr = decoded.decodeToString()
                            val result = parseJsonWithComments(jsonStr)
                            if (result != null) return result
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // 方式2：整个文本作为 Base64
        if (text.length >= 4) {
            try {
                val cleaned = text.replace(Regex("[^A-Za-z0-9+/=]"), "")
                if (cleaned.length >= 4) {
                    val decoded = decodeBase64(cleaned)
                    if (decoded != null) {
                        val jsonStr = decoded.decodeToString()
                        val result = parseJsonWithComments(jsonStr)
                        if (result != null) return result
                    }
                }
            } catch (_: Exception) {
            }
        }

        return null
    }

    /** 跨平台 Base64 解码 */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun decodeBase64(input: String): ByteArray? =
        try {
            kotlin.io.encoding.Base64
                .decode(input)
        } catch (_: Exception) {
            null
        }

    fun parseJsonWithComments(text: String): Map<String, Any>? {
        try {
            val element = json.parseToJsonElement(text)
            val result = JsonSpider.jsonElementToMap(element)
            if (result != null) return result
        } catch (_: Exception) {
        }

        val cleaned = stripJsonComments(text)
        try {
            val element = json.parseToJsonElement(cleaned)
            val result = JsonSpider.jsonElementToMap(element)
            if (result != null) return result
        } catch (_: Exception) {
        }

        return null
    }

    fun stripJsonComments(text: String): String {
        val sb = StringBuilder()
        var i = 0
        var inString = false
        var stringChar = ' '

        while (i < text.length) {
            if (inString) {
                val ch = text[i]
                sb.append(ch)
                if (ch == '\\' && i + 1 < text.length) {
                    i++
                    sb.append(text[i])
                } else if (ch == stringChar) {
                    inString = false
                }
                i++
                continue
            }

            if (text[i] == '"' || text[i] == '\'') {
                inString = true
                stringChar = text[i]
                sb.append(text[i])
                i++
            } else if (i + 1 < text.length && text[i] == '/' && text[i + 1] == '/') {
                i += 2
                while (i < text.length && text[i] != '\n' && text[i] != '\r') {
                    i++
                }
            } else if (i + 1 < text.length && text[i] == '/' && text[i + 1] == '*') {
                i += 2
                while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) {
                    i++
                }
                i += 2
            } else {
                sb.append(text[i])
                i++
            }
        }

        return sb.toString()
    }

    // ==================== 缓存内部类 ====================

    data class DnsCacheEntry(
        val addresses: List<String>,
        val timeMs: Long,
    )

    data class ListCacheEntry(
        val response: VideoListResponse,
        val atMs: Long,
    )

    data class PrefetchCacheEntry(
        val data: VideoDetail,
        val timeMs: Long,
    )

    companion object {
        /**
         * 进程内共享实例。
         *
         * 缓存挂在实例上，多个实例等于多份缓存；[CmsSpider] 与首页都从这里取，
         * 才能共享同一份列表缓存与 DNS 缓存。
         */
        val shared: VideoApiService by lazy { VideoApiService() }

        fun currentTimeMillis(): Long =
            kotlinx.datetime.Clock.System
                .now()
                .toEpochMilliseconds()
    }
}
