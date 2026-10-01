package com.mediamix.shared.player

import co.touchlab.kermit.Logger
import com.mediamix.shared.network.HttpClientFactory
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 字幕条目
 */
data class SubtitleEntry(
    val startMs: Long, // 开始时间（毫秒）
    val endMs: Long, // 结束时间（毫秒）
    val text: String, // 字幕文本（可能多行）
)

/**
 * 字幕轨道 — 支持多字幕轨道切换
 */
data class SubtitleTrack(
    val label: String,
    val language: String,
    val entries: List<SubtitleEntry>,
)

/**
 * LRU 字幕缓存 — 最大容量 20 条，避免重复解析
 * 使用 LinkedHashMap 实现，按访问顺序排列（最新访问在末尾）
 */
class SubtitleLruCache(
    private val maxEntries: Int = 20,
) {
    private val cache = LinkedHashMap<String, List<SubtitleEntry>>()

    /** 获取缓存，命中时将条目移到末尾（标记为最近使用） */
    fun get(key: String): List<SubtitleEntry>? {
        val value = cache.remove(key) ?: return null
        cache[key] = value // 移到末尾
        return value
    }

    /** 写入缓存，超容量时淘汰最久未使用的条目 */
    fun put(
        key: String,
        entries: List<SubtitleEntry>,
    ) {
        if (cache.containsKey(key)) {
            cache.remove(key)
        } else if (cache.size >= maxEntries) {
            cache.remove(cache.keys.first())
        }
        cache[key] = entries
    }

    fun containsKey(key: String) = cache.containsKey(key)

    val size: Int get() = cache.size

    fun clear() = cache.clear()
}

/**
 * 字幕服务 — 负责加载和解析 SRT 字幕
 *
 * 核心功能：
 * - SRT 格式解析（支持逗号/点号毫秒分隔符）
 * - 二分查找字幕定位 O(logN)
 * - LRU 缓存（最大 20 条）
 * - PTS 自动同步（中位数偏移算法）
 * - 多字幕轨道支持
 */
class SubtitleService(
    private val httpClient: HttpClient? = null,
) {
    private val logger = Logger.withTag("SubtitleService")
    private val subtitleCache = SubtitleLruCache(maxEntries = 20)
    private var syncOffsetMs: Long = 0

    /**
     * 从 URL 下载 SRT 并解析（带缓存）
     */
    suspend fun loadFromUrl(url: String): List<SubtitleEntry> {
        subtitleCache.get(url)?.let {
            logger.d { "命中字幕缓存: $url" }
            return it
        }
        return try {
            logger.d { "开始下载字幕: $url" }
            val content = downloadSubtitle(url)
            if (content.isBlank()) {
                logger.w { "字幕内容为空: $url" }
                return emptyList()
            }
            val entries = parseSrt(content)
            logger.d { "字幕解析完成，共 ${entries.size} 条: $url" }
            subtitleCache.put(url, entries)
            entries
        } catch (e: Exception) {
            logger.e { "下载字幕失败: $url, 错误: ${e.message}" }
            throw e
        }
    }

    /**
     * 从本地文件读取 SRT 并解析
     * 注意：commonMain 中通过 expect/actual 实现文件 I/O
     */
    suspend fun loadFromFile(filePath: String): List<SubtitleEntry> {
        return try {
            logger.d { "开始读取本地字幕: $filePath" }
            val content = readFileContent(filePath)
            if (content.isBlank()) {
                logger.w { "字幕文件内容为空: $filePath" }
                return emptyList()
            }
            val entries = parseSrt(content)
            logger.d { "本地字幕解析完成，共 ${entries.size} 条: $filePath" }
            entries
        } catch (e: Exception) {
            logger.e { "读取本地字幕失败: $filePath, 错误: ${e.message}" }
            throw e
        }
    }

    /**
     * 预加载多个字幕文件 — 并行下载并解析，结果写入缓存
     * 单个失败不影响其他
     */
    suspend fun preloadSubtitles(urls: List<String>) {
        logger.d { "开始预加载 ${urls.size} 个字幕文件" }
        coroutineScope {
            urls
                .map { url ->
                    async {
                        try {
                            loadFromUrl(url)
                        } catch (e: Exception) {
                            logger.w { "预加载字幕失败: $url, 错误: ${e.message}" }
                        }
                    }
                }.awaitAll()
        }
        logger.d { "预加载完成，缓存大小: ${subtitleCache.size}" }
    }

    /**
     * 加载多轨道字幕 — 根据 URL 和轨道信息加载，支持零延迟切换
     */
    suspend fun loadMultiTrackFromUrl(trackInfos: List<TrackInfo>): List<SubtitleTrack> {
        logger.d { "开始加载 ${trackInfos.size} 条字幕轨道" }
        val results =
            coroutineScope {
                trackInfos
                    .map { info ->
                        async {
                            try {
                                val entries = loadFromUrl(info.id)
                                SubtitleTrack(
                                    label = info.label,
                                    language = info.language ?: "",
                                    entries = entries,
                                )
                            } catch (e: Exception) {
                                logger.e { "加载轨道失败: ${info.label}(${info.language}), 错误: ${e.message}" }
                                SubtitleTrack(
                                    label = info.label,
                                    language = info.language ?: "",
                                    entries = emptyList(),
                                )
                            }
                        }
                    }.awaitAll()
            }
        logger.d { "多轨道加载完成，共 ${results.size} 条轨道" }
        return results
    }

    // ==================== SRT 解析 ====================

    // 预编译正则，避免每次解析都重新创建
    private val srtBlockSplitRegex = Regex("\\r?\\n\\s*\\r?\\n")
    private val srtLineSplitRegex = Regex("\\r?\\n")
    private val ssaTagRegex = Regex("\\{[^}]*\\}") // SSA/ASS 格式标签 {\b1} {\an8} 等
    private val htmlTagRegex = Regex("<[^>]+>") // HTML 标签 <b> <i> <font> 等
    private val timestampRegex = Regex("""(\d{1,2}):(\d{2}):(\d{2})[.,](\d{1,3})""")

    /**
     * 解析 SRT 格式文本
     * SRT 格式：序号 → 时间轴(00:00:01,000 --> 00:00:04,000) → 字幕文本(可多行) → 空行
     *
     * 增强支持：
     * - UTF-8 BOM 自动剥离
     * - SSA/ASS 内联标签剥离 ({\b1}, {\an8} 等)
     * - HTML 标签剥离 (<b>, <i>, <font> 等)
     * - 逗号/点号毫秒分隔符兼容
     * - 非标准时间戳容错（1位小时、1-2位毫秒）
     */
    fun parseSrt(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()

        // 剥离 UTF-8 BOM 和前后空白
        val cleanContent =
            content
                .removePrefix("\uFEFF") // UTF-8 BOM
                .trim()

        // 按空行分割字幕块
        val blocks = cleanContent.split(srtBlockSplitRegex)

        for (block in blocks) {
            val lines = block.trim().split(srtLineSplitRegex)
            if (lines.size < 2) continue

            // 查找时间轴行（包含 --> 的行）
            val timelineIndex = lines.indexOfFirst { it.contains("-->") }
            if (timelineIndex < 0) continue

            // 解析时间轴
            val timelineLine = lines[timelineIndex]
            val timeParts = timelineLine.split("-->")
            if (timeParts.size != 2) continue

            val start = parseTimestamp(timeParts[0].trim()) ?: continue
            val end = parseTimestamp(timeParts[1].trim()) ?: continue

            // 时间轴之后的所有行作为字幕文本
            val textLines = lines.subList(timelineIndex + 1, lines.size)
            val rawText = textLines.joinToString("\n").trim()
            if (rawText.isEmpty()) continue

            // 清理字幕文本：剥离 SSA/ASS 标签和 HTML 标签
            val cleanText = cleanSubtitleText(rawText)
            if (cleanText.isEmpty()) continue

            entries.add(SubtitleEntry(startMs = start, endMs = end, text = cleanText))
        }

        // 按开始时间排序
        entries.sortBy { it.startMs }
        return entries
    }

    /**
     * 清理字幕文本：剥离格式标签，保留纯文本
     */
    private fun cleanSubtitleText(text: String): String =
        text
            .replace(ssaTagRegex, "") // 移除 {\b1} 等 SSA/ASS 标签
            .replace(htmlTagRegex, "") // 移除 <b> <i> 等 HTML 标签
            .replace("&nbsp;", " ") // HTML 实体
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()

    /**
     * 解析 SRT 时间戳，支持多种格式：
     * - 标准: 00:00:01,000 或 00:00:01.000
     * - 容错: 0:00:01,00 (1位小时, 1-3位毫秒)
     * 返回毫秒值
     */
    fun parseTimestamp(timestamp: String): Long? {
        val normalized = timestamp.replace(',', '.')
        val match = timestampRegex.find(normalized) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val millisStr = match.groupValues[4]
        // 补齐到 3 位毫秒（如 "0" -> "000", "12" -> "120"）
        val millis =
            when (millisStr.length) {
                1 -> (millisStr.toLongOrNull() ?: return null) * 100
                2 -> (millisStr.toLongOrNull() ?: return null) * 10
                3 -> millisStr.toLongOrNull() ?: return null
                else -> return null
            }
        return hours * 3600000 + minutes * 60000 + seconds * 1000 + millis
    }

    // ==================== 二分查找 ====================

    /**
     * 根据播放位置获取当前字幕（二分查找优化，O(logN)）
     *
     * @param entries 字幕列表（已按开始时间排序）
     * @param positionMs 当前播放位置（毫秒）
     * @param offsetSec 时间偏移（秒）
     * @param syncOffset PTS 同步偏移（毫秒），null 时使用全局 syncOffsetMs
     */
    fun getSubtitleAt(
        entries: List<SubtitleEntry>,
        positionMs: Long,
        offsetSec: Double = 0.0,
        syncOffset: Long? = null,
    ): SubtitleEntry? {
        if (entries.isEmpty()) return null

        val effectiveSync = syncOffset ?: syncOffsetMs
        val adjustedPosition = positionMs + (offsetSec * 1000).toLong() + effectiveSync

        // 二分查找：找到最后一个 startMs <= adjustedPosition 的条目
        var left = 0
        var right = entries.size - 1
        var result = -1

        while (left <= right) {
            val mid = left + (right - left) / 2
            if (entries[mid].startMs <= adjustedPosition) {
                result = mid
                left = mid + 1 // 继续向右查找更晚的匹配
            } else {
                right = mid - 1
            }
        }

        // 检查找到的条目是否覆盖当前时间
        return if (result >= 0 && adjustedPosition <= entries[result].endMs) {
            entries[result]
        } else {
            null
        }
    }

    // ==================== PTS 自动同步 ====================

    /**
     * 自动计算 PTS 同步偏移量（中位数偏移算法）
     *
     * 算法：
     * 1. 对每个音频时间戳，找到最近的字幕起始时间
     * 2. 计算偏移 = 字幕时间 - 音频时间
     * 3. 取中位数作为最佳偏移量，避免极端值干扰
     * 4. 更新并返回 syncOffsetMs
     *
     * @param entries 字幕条目列表
     * @param audioTimestamps 音频时间戳列表（毫秒）
     * @return 计算出的最佳偏移量（毫秒）
     */
    fun autoSyncOffset(
        entries: List<SubtitleEntry>,
        audioTimestamps: List<Long>,
    ): Long {
        if (entries.isEmpty() || audioTimestamps.isEmpty()) {
            logger.w { "字幕或音频时间戳为空，无法计算同步偏移" }
            return syncOffsetMs
        }

        val offsets = mutableListOf<Long>()

        for (audioTs in audioTimestamps) {
            // 对每个音频时间戳，找到最近的字幕起始时间
            var nearestStart: Long? = null
            var minDiff = Long.MAX_VALUE

            for (entry in entries) {
                val diff = kotlin.math.abs(entry.startMs - audioTs)
                if (diff < minDiff) {
                    minDiff = diff
                    nearestStart = entry.startMs
                }
            }

            if (nearestStart != null) {
                // 偏移 = 字幕时间 - 音频时间
                offsets.add(nearestStart - audioTs)
            }
        }

        if (offsets.isEmpty()) {
            logger.w { "未能计算有效偏移量" }
            return syncOffsetMs
        }

        // 取中位数作为最佳偏移量，避免极端值干扰
        offsets.sort()
        val medianOffset = offsets[offsets.size / 2]

        syncOffsetMs = medianOffset
        logger.d { "自动同步偏移计算完成: ${medianOffset}ms" }
        return syncOffsetMs
    }

    // ==================== 同步偏移控制 ====================

    fun setSyncOffset(offsetMs: Long) {
        syncOffsetMs = offsetMs
        logger.d { "手动设置同步偏移: ${offsetMs}ms" }
    }

    fun getSyncOffset(): Long = syncOffsetMs

    // ==================== 缓存管理 ====================

    fun clearCache() {
        subtitleCache.clear()
        logger.d { "字幕缓存已清空" }
    }

    fun getCacheSize(): Int = subtitleCache.size

    // ==================== 网络下载 ====================

    /**
     * 下载字幕内容（使用 Ktor HttpClient）
     */
    private suspend fun downloadSubtitle(url: String): String {
        val client = httpClient ?: HttpClientFactory.createHttpClient()
        try {
            val response = client.get(url)
            return response.bodyAsText()
        } finally {
            if (httpClient == null) client.close()
        }
    }
}

/**
 * 读取文件内容 — expect 声明
 * 各平台提供 actual 实现
 */
expect suspend fun readFileContent(filePath: String): String
