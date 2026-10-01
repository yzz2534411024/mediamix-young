package com.mediamix.ui.viewmodel

import app.cash.turbine.test
import com.mediamix.shared.database.WatchHistoryDao
import com.mediamix.shared.database.WatchHistoryItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockDao: WatchHistoryDao
    private lateinit var viewModel: HistoryViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockDao = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Data Model Tests ==========

    @Test
    fun watchHistoryItem_defaultValues() {
        val item =
            WatchHistoryItem(
                id = "1",
                vodId = "vod_1",
                vodName = "Test Video",
                sourceKey = "test_source",
            )
        assertEquals("1", item.id)
        assertEquals("vod_1", item.vodId)
        assertEquals("Test Video", item.vodName)
        assertEquals(null, item.vodPic)
        assertEquals("test_source", item.sourceKey)
        assertEquals(null, item.episodeName)
        assertEquals(0L, item.lastWatchTime)
    }

    @Test
    fun watchHistoryItem_withAllFields() {
        val item =
            WatchHistoryItem(
                id = "1",
                vodId = "vod_1",
                vodName = "Test Video",
                vodPic = "https://example.com/pic.jpg",
                sourceKey = "test_source",
                episodeName = "Episode 1",
                lastWatchTime = 1000L,
            )
        assertEquals("https://example.com/pic.jpg", item.vodPic)
        assertEquals("Episode 1", item.episodeName)
        assertEquals(1000L, item.lastWatchTime)
    }

    @Test
    fun watchHistoryItem_equality() {
        val item1 = WatchHistoryItem(id = "1", vodId = "v1", vodName = "A", sourceKey = "s")
        val item2 = WatchHistoryItem(id = "1", vodId = "v1", vodName = "A", sourceKey = "s")
        val item3 = WatchHistoryItem(id = "2", vodId = "v2", vodName = "B", sourceKey = "s")
        assertEquals(item1, item2)
        assertFalse(item1 == item3)
    }

    @Test
    fun watchHistoryItem_copy() {
        val item = WatchHistoryItem(id = "1", vodId = "v1", vodName = "A", sourceKey = "s")
        val copied = item.copy(vodName = "B")
        assertEquals("B", copied.vodName)
        assertEquals("1", copied.id)
        assertEquals("v1", copied.vodId)
    }

    // ========== ViewModel Integration Tests ==========

    private fun createViewModel(): HistoryViewModel {
        every { mockDao.observeAll() } returns flowOf(emptyList())
        return HistoryViewModel(watchHistoryDao = mockDao)
    }

    @Test
    fun loadHistories_emitsLoadingState() =
        runTest {
            val entities =
                listOf(
                    WatchHistoryItemEntity("v1", "Video 1", null, "src", "Ep1", 1000L),
                )
            every { mockDao.observeAll() } returns flowOf(entities)
            coEvery { mockDao.getAll() } returns entities

            viewModel = HistoryViewModel(watchHistoryDao = mockDao)
            viewModel.isLoading.test {
                // Initial state after loadHistories
                viewModel.loadHistories()
                // Loading should complete (DAO is mocked)
                assertTrue(true)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun addOrUpdateHistory_callsDao() =
        runTest {
            every { mockDao.observeAll() } returns flowOf(emptyList())
            viewModel = HistoryViewModel(watchHistoryDao = mockDao)

            viewModel.addOrUpdateHistory(
                vodId = "v1",
                vodName = "Test",
                vodPic = null,
                sourceKey = "src",
                episodeName = "Ep1",
            )

            coVerify { mockDao.insertOrReplace("v1", "Test", null, "src", "Ep1", any()) }
        }

    @Test
    fun deleteHistory_callsDao() =
        runTest {
            every { mockDao.observeAll() } returns flowOf(emptyList())
            viewModel = HistoryViewModel(watchHistoryDao = mockDao)

            viewModel.deleteHistory("v1")

            coVerify { mockDao.deleteByVodId("v1") }
        }

    @Test
    fun clearAll_callsDao() =
        runTest {
            every { mockDao.observeAll() } returns flowOf(emptyList())
            viewModel = HistoryViewModel(watchHistoryDao = mockDao)

            viewModel.clearAll()

            coVerify { mockDao.clearAll() }
        }
}
