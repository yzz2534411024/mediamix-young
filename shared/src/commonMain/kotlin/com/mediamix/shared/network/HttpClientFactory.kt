package com.mediamix.shared.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * 共享 Ktor HttpClient 工厂
 *
 * 提供统一的 HTTP 客户端创建方法，配置超时、UA、JSON 序列化等。
 * 各蜘蛛和缓存服务通过此工厂创建 HttpClient 实例。
 */
object HttpClientFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 创建默认配置的 HttpClient
     * 适用于大多数蜘蛛和网络请求的场景
     */
    fun createHttpClient(
        connectTimeoutSeconds: Long = 10,
        requestTimeoutSeconds: Long = 30,
        userAgent: String = "okhttp/3.12.11",
        enableLogging: Boolean = false,
    ): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                connectTimeoutMillis = connectTimeoutSeconds * 1000
                requestTimeoutMillis = requestTimeoutSeconds * 1000
                socketTimeoutMillis = requestTimeoutSeconds * 1000
            }

            install(DefaultRequest) {
                headers.append("User-Agent", userAgent)
                headers.append("Accept", "*/*")
            }

            if (enableLogging) {
                install(Logging) {
                    logger = Logger.SIMPLE
                    level = LogLevel.HEADERS
                }
            }
        }
    }

    /**
     * 创建用于流式下载的 HttpClient
     * 适用于 LocalProxyServer 和 PreloadService 的流式场景
     */
    fun createStreamingClient(
        connectTimeoutSeconds: Long = 10,
        userAgent: String = "okhttp/3.12.11",
    ): HttpClient {
        return HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = connectTimeoutSeconds * 1000
                socketTimeoutMillis = 30 * 60 * 1000 // 30 分钟，流式传输需要长超时
            }

            install(DefaultRequest) {
                headers.append("User-Agent", userAgent)
            }
        }
    }

    /** 共享的 Json 实例 */
    val sharedJson: Json get() = json
}
