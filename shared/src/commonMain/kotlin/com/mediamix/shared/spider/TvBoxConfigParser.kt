package com.mediamix.shared.spider

import com.mediamix.shared.models.TvBoxConfig
import com.mediamix.shared.models.TvBoxLive
import com.mediamix.shared.models.TvBoxSite
import kotlinx.serialization.json.*

// / TVBox 配置解析器
class TvBoxConfigParser {
    // / 解析 TVBox 配置 JSON（从 Map<String, Any>）
    fun parse(json: Map<String, Any>): TvBoxConfig {
        val spiderUrl = parseSpiderUrl(json["spider"])
        val sites = parseSites(json["sites"])
        val lives = parseLives(json["lives"])
        val flags = parseFlags(json["flags"])

        return TvBoxConfig(
            spiderUrl = spiderUrl,
            spiderSpec = parseSpiderSpec(json["spider"]),
            sites = sites,
            lives = lives,
            flags = flags,
        )
    }

    // / 从 kotlinx.serialization JsonObject 解析
    fun parseFromJsonObject(json: JsonObject): TvBoxConfig {
        val spiderUrl = parseSpiderUrl(json["spider"]?.toAnyValue())
        val sites = parseSites(json["sites"]?.toAnyValue())
        val lives = parseLives(json["lives"]?.toAnyValue())
        val flags = parseFlags(json["flags"]?.toAnyValue())

        return TvBoxConfig(
            spiderUrl = spiderUrl,
            spiderSpec = parseSpiderSpec(json["spider"]?.toAnyValue()),
            sites = sites,
            lives = lives,
            flags = flags,
        )
    }

    /**
     * 取 spider 字段**原文**（含 `;md5;<hash>` 后缀）。
     *
     * [parseSpiderUrl] 负责给出可下载的裸 URL，这里保留原文是为了让
     * `loadSpiderJar` 能拿到 md5 做校验与缓存键 —— 只留 URL 会让校验静默失效。
     */
    internal fun parseSpiderSpec(raw: Any?): String? {
        if (raw !is String) return null
        return raw.trim().ifEmpty { null }
    }

    // / spider 字段支持 "jar_url;md5" 格式，只取 jar_url
    internal fun parseSpiderUrl(raw: Any?): String? {
        if (raw !is String) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val semicolonIndex = trimmed.indexOf(';')
        return if (semicolonIndex == -1) trimmed else trimmed.substring(0, semicolonIndex).trim()
    }

    // / 解析 sites 数组
    internal fun parseSites(raw: Any?): List<TvBoxSite> {
        if (raw !is List<*>) return emptyList()

        return raw.filterIsInstance<Map<String, Any>>().mapNotNull { item ->
            val key = stringValue(item["key"]) ?: return@mapNotNull null
            val name = stringValue(item["name"]) ?: return@mapNotNull null
            val api = stringValue(item["api"]) ?: return@mapNotNull null

            TvBoxSite(
                key = key,
                name = name,
                type = intValue(item["type"]) ?: 0,
                api = api,
                ext = stringValue(item["ext"]),
                jar = stringValue(item["jar"]),
                playerType = intValue(item["playerType"]),
                searchable = (intValue(item["searchable"]) ?: 1) != 0,
                quickSearch = (intValue(item["quickSearch"]) ?: 0) != 0,
                changeable = (intValue(item["changeable"]) ?: 0) != 0,
            )
        }
    }

    // / 解析 lives 数组
    internal fun parseLives(raw: Any?): List<TvBoxLive> {
        if (raw !is List<*>) return emptyList()

        return raw.filterIsInstance<Map<String, Any>>().mapNotNull { item ->
            val name = stringValue(item["name"]) ?: return@mapNotNull null
            val type = stringValue(item["type"])
            val url = stringValue(item["url"]) ?: return@mapNotNull null

            TvBoxLive(
                name = name,
                type = type,
                url = url,
                playerType = intValue(item["playerType"]),
            )
        }
    }

    // / 解析 flags 数组
    internal fun parseFlags(raw: Any?): List<String> {
        if (raw !is List<*>) return emptyList()
        return raw.filterIsInstance<String>()
    }

    internal fun stringValue(raw: Any?): String? {
        if (raw == null) return null
        if (raw is String) {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) null else trimmed
        }
        if (raw is JsonElement) {
            return when (raw) {
                is JsonPrimitive -> {
                    if (raw.isString) {
                        val trimmed = raw.content.trim()
                        if (trimmed.isEmpty()) null else trimmed
                    } else {
                        raw.content
                    }
                }
                is JsonObject, is JsonArray -> raw.toString()
                else -> null
            }
        }
        if (raw is Map<*, *> || raw is List<*>) {
            return raw.toString()
        }
        return raw.toString()
    }

    internal fun intValue(raw: Any?): Int? {
        if (raw == null) return null
        if (raw is Int) return raw
        if (raw is Long) return raw.toInt()
        if (raw is Double) return raw.toInt()
        if (raw is Float) return raw.toInt()
        if (raw is JsonPrimitive) {
            if (raw.isString) {
                val trimmed = raw.content.trim()
                if (trimmed.isEmpty()) return null
                if (trimmed == "true") return 1
                if (trimmed == "false") return 0
                return trimmed.toIntOrNull()
            }
            return raw.intOrNull
        }
        if (raw is String) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed == "true") return 1
            if (trimmed == "false") return 0
            return trimmed.toIntOrNull()
        }
        return null
    }
}

// / 将 JsonElement 转为普通 Kotlin 对象
private fun JsonElement.toAnyValue(): Any? =
    when (this) {
        is JsonPrimitive -> if (isString) content else content
        is JsonObject -> entries.associate { (k, v) -> k to v.toAnyValue() }
        is JsonArray -> map { it.toAnyValue() }
    }
