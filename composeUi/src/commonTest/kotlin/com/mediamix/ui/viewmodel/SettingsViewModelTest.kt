package com.mediamix.ui.viewmodel

import com.mediamix.shared.cache.VideoCacheService
import com.mediamix.shared.database.FavoriteDao
import com.mediamix.shared.database.WatchHistoryDao
import com.russhwolf.settings.MapSettings
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsViewModelTest {

    private fun createMockDeps(): Pair<VideoCacheService, SettingsViewModel> {
        val videoCacheService = mockk<VideoCacheService>(relaxed = true)
        val watchHistoryDao = mockk<WatchHistoryDao>(relaxed = true)
        val favoriteDao = mockk<FavoriteDao>(relaxed = true)
        every { videoCacheService.getStats() } returns com.mediamix.shared.models.CacheStats()
        every { videoCacheService.getMemoryUsage() } returns com.mediamix.shared.models.MemoryUsageInfo()

        val mapSettings = MapSettings()
        val vm = SettingsViewModel(
            settings = mapSettings,
            videoCacheService = videoCacheService,
            watchHistoryDao = watchHistoryDao,
            favoriteDao = favoriteDao,
        )
        return videoCacheService to vm
    }

    @Test
    fun themeModeOption_hasAllValues() {
        val entries = ThemeModeOption.entries
        assertEquals(3, entries.size)
        assertEquals(ThemeModeOption.SYSTEM, entries[0])
        assertEquals(ThemeModeOption.LIGHT, entries[1])
        assertEquals(ThemeModeOption.DARK, entries[2])
    }

    @Test
    fun themeModeOption_ordinals() {
        assertEquals(0, ThemeModeOption.SYSTEM.ordinal)
        assertEquals(1, ThemeModeOption.LIGHT.ordinal)
        assertEquals(2, ThemeModeOption.DARK.ordinal)
    }

    @Test
    fun cacheStatsInfo_defaultValues() {
        val stats = CacheStatsInfo()
        assertEquals(0L, stats.memoryCacheSize)
        assertEquals(0L, stats.diskCacheSize)
        assertEquals(0L, stats.totalSize)
        assertEquals(0, stats.entryCount)
        assertEquals(0.0, stats.hitRate)
    }

    @Test
    fun cacheStatsInfo_customValues() {
        val stats = CacheStatsInfo(
            memoryCacheSize = 100L,
            diskCacheSize = 200L,
            totalSize = 300L,
            entryCount = 5,
            hitRate = 0.8,
        )
        assertEquals(100L, stats.memoryCacheSize)
        assertEquals(200L, stats.diskCacheSize)
        assertEquals(300L, stats.totalSize)
        assertEquals(5, stats.entryCount)
        assertEquals(0.8, stats.hitRate)
    }

    @Test
    fun cacheStatsInfo_equality() {
        val a = CacheStatsInfo(1L, 2L, 3L, 4, 0.5)
        val b = CacheStatsInfo(1L, 2L, 3L, 4, 0.5)
        assertEquals(a, b)
    }

    @Test
    fun cacheStatsInfo_copy() {
        val stats = CacheStatsInfo(1L, 2L, 3L, 4, 0.5)
        val copied = stats.copy(totalSize = 99L)
        assertEquals(99L, copied.totalSize)
        assertEquals(1L, copied.memoryCacheSize)
        assertEquals(2L, copied.diskCacheSize)
    }

    @Test
    fun settingsViewModel_initReadsThemeFromSettings() {
        val mapSettings = MapSettings()
        mapSettings.putInt("theme_mode", 2)
        val videoCacheService = mockk<VideoCacheService>(relaxed = true)
        val watchHistoryDao = mockk<WatchHistoryDao>(relaxed = true)
        val favoriteDao = mockk<FavoriteDao>(relaxed = true)
        every { videoCacheService.getStats() } returns com.mediamix.shared.models.CacheStats()
        every { videoCacheService.getMemoryUsage() } returns com.mediamix.shared.models.MemoryUsageInfo()
        val vm = SettingsViewModel(mapSettings, videoCacheService, watchHistoryDao, favoriteDao)
        assertEquals(ThemeModeOption.DARK, vm.themeMode.value)
    }

    @Test
    fun settingsViewModel_defaultThemeIsSystem() {
        val (_, vm) = createMockDeps()
        assertEquals(ThemeModeOption.SYSTEM, vm.themeMode.value)
    }

    @Test
    fun settingsViewModel_setThemeModeUpdatesState() {
        val mapSettings = MapSettings()
        val videoCacheService = mockk<VideoCacheService>(relaxed = true)
        val watchHistoryDao = mockk<WatchHistoryDao>(relaxed = true)
        val favoriteDao = mockk<FavoriteDao>(relaxed = true)
        every { videoCacheService.getStats() } returns com.mediamix.shared.models.CacheStats()
        every { videoCacheService.getMemoryUsage() } returns com.mediamix.shared.models.MemoryUsageInfo()
        val vm = SettingsViewModel(mapSettings, videoCacheService, watchHistoryDao, favoriteDao)
        vm.setThemeMode(ThemeModeOption.LIGHT)
        assertEquals(ThemeModeOption.LIGHT, vm.themeMode.value)
        assertEquals(1, mapSettings.getInt("theme_mode", 0))
    }

    @Test
    fun settingsViewModel_setThemeModeDark() {
        val mapSettings = MapSettings()
        val videoCacheService = mockk<VideoCacheService>(relaxed = true)
        val watchHistoryDao = mockk<WatchHistoryDao>(relaxed = true)
        val favoriteDao = mockk<FavoriteDao>(relaxed = true)
        every { videoCacheService.getStats() } returns com.mediamix.shared.models.CacheStats()
        every { videoCacheService.getMemoryUsage() } returns com.mediamix.shared.models.MemoryUsageInfo()
        val vm = SettingsViewModel(mapSettings, videoCacheService, watchHistoryDao, favoriteDao)
        vm.setThemeMode(ThemeModeOption.DARK)
        assertEquals(ThemeModeOption.DARK, vm.themeMode.value)
        assertEquals(2, mapSettings.getInt("theme_mode", 0))
    }

    @Test
    fun settingsViewModel_cacheStatsInitiallyZero() {
        val (_, vm) = createMockDeps()
        assertEquals(CacheStatsInfo(), vm.cacheStats.value)
    }

    @Test
    fun settingsViewModel_invalidThemeIndexFallsBackToSystem() {
        val mapSettings = MapSettings()
        mapSettings.putInt("theme_mode", 99)
        val videoCacheService = mockk<VideoCacheService>(relaxed = true)
        val watchHistoryDao = mockk<WatchHistoryDao>(relaxed = true)
        val favoriteDao = mockk<FavoriteDao>(relaxed = true)
        every { videoCacheService.getStats() } returns com.mediamix.shared.models.CacheStats()
        every { videoCacheService.getMemoryUsage() } returns com.mediamix.shared.models.MemoryUsageInfo()
        val vm = SettingsViewModel(mapSettings, videoCacheService, watchHistoryDao, favoriteDao)
        assertEquals(ThemeModeOption.SYSTEM, vm.themeMode.value)
    }
}
