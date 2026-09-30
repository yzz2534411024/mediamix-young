package com.mediamix.shared.models

import kotlinx.serialization.Serializable

// ============================================================
// Cache Policy
// ============================================================

@Serializable
enum class CachePolicy {
    NORMAL,
    AGGRESSIVE,
    CONSERVATIVE,
    EMERGENCY,
}

// ============================================================
// Cache Priority
// ============================================================

@Serializable
enum class CachePriority {
    HIGH,
    NORMAL,
    LOW,
}

// ============================================================
// Cache Entry
// ============================================================

@Serializable
data class CacheEntry(
    val cacheId: String,
    val videoId: String,
    val quality: String,
    val filePath: String,
    val fileSize: Long = 0L,
    val segments: List<String> = emptyList(),
    val hitCount: Int = 0,
    val lastAccess: Long = 0L,
    val createdAt: Long = 0L,
    val ttl: Int = 604800,
    val priority: Int = 0,
    val isComplete: Boolean = true,
) {
    fun isExpired(currentTimeMillis: Long): Boolean {
        val expiresAt = createdAt + (ttl * 1000L)
        return currentTimeMillis > expiresAt
    }
}

// ============================================================
// Segment Cache Result
// ============================================================

@Serializable
data class SegmentCacheResult(
    val hit: Boolean,
    val data: List<Byte>? = null,
    val path: String? = null,
)

// ============================================================
// Memory Pressure Level
// ============================================================

@Serializable
enum class MemoryPressureLevel {
    NONE,
    NORMAL,
    WARNING,
    CRITICAL,
}

// ============================================================
// Memory Usage Info
// ============================================================

data class MemoryUsageInfo(
    val l1Bytes: Long = 0L,
    val l2Bytes: Long = 0L,
    val processRssBytes: Long = 0L,
    val pressureLevel: MemoryPressureLevel = MemoryPressureLevel.NONE,
    val l1MaxEntries: Int = 0,
    val l2MaxEntries: Int = 0,
)

// ============================================================
// Cache Stats
// ============================================================

@Serializable
data class CacheStats(
    val totalSize: Long = 0L,
    val entryCount: Int = 0,
    val hitCount: Long = 0L,
    val missCount: Long = 0L,
    val hitRate: Double = 0.0,
    val diskUsagePercent: Double = 0.0,
)

// ============================================================
// Viewing Habit Snapshot
// ============================================================

@Serializable
data class ViewingHabitSnapshot(
    val isPeakHour: Boolean,
    val currentHourFrequency: Double,
    val preferredCategories: List<String>,
    val highReplayVideoIds: List<String>,
    val predictedCategories: List<String>,
)

// ============================================================
// Cache Strategy Suggestion
// ============================================================

@Serializable
data class CacheStrategySuggestion(
    val ttlMultiplier: Double = 1.0,
    val capacityMultiplier: Double = 1.0,
    val priority: CachePriority = CachePriority.NORMAL,
) {
    companion object {
        val defaultSuggestion = CacheStrategySuggestion()
    }
}