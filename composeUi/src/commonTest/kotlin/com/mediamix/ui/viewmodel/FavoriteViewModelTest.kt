package com.mediamix.ui.viewmodel

import app.cash.turbine.test
import com.mediamix.shared.database.FavoriteDao
import com.mediamix.shared.database.FavoriteItemEntity
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
class FavoriteViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockDao: FavoriteDao
    private lateinit var viewModel: FavoriteViewModel

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
    fun favoriteItem_defaultValues() {
        val item = FavoriteItem(
            id = "1",
            vodId = "vod_1",
            vodName = "Test Video",
            sourceKey = "test_source"
        )
        assertEquals("1", item.id)
        assertEquals("vod_1", item.vodId)
        assertEquals("Test Video", item.vodName)
        assertEquals(null, item.vodPic)
        assertEquals("test_source", item.sourceKey)
        assertEquals(null, item.typeName)
        assertEquals(0, item.lastEpisodeCount)
        assertEquals(0L, item.addTime)
    }

    @Test
    fun favoriteItem_withAllFields() {
        val item = FavoriteItem(
            id = "1",
            vodId = "vod_1",
            vodName = "Test Video",
            vodPic = "https://example.com/pic.jpg",
            sourceKey = "test_source",
            typeName = "Movie",
            lastEpisodeCount = 10,
            addTime = 5000L
        )
        assertEquals("https://example.com/pic.jpg", item.vodPic)
        assertEquals("Movie", item.typeName)
        assertEquals(10, item.lastEpisodeCount)
        assertEquals(5000L, item.addTime)
    }

    @Test
    fun favoriteItem_equality() {
        val item1 = FavoriteItem(id = "1", vodId = "v1", vodName = "A", sourceKey = "s")
        val item2 = FavoriteItem(id = "1", vodId = "v1", vodName = "A", sourceKey = "s")
        val item3 = FavoriteItem(id = "2", vodId = "v2", vodName = "B", sourceKey = "s")
        assertEquals(item1, item2)
        assertFalse(item1 == item3)
    }

    @Test
    fun favoriteItem_copy() {
        val item = FavoriteItem(id = "1", vodId = "v1", vodName = "A", sourceKey = "s")
        val copied = item.copy(vodName = "B", lastEpisodeCount = 5)
        assertEquals("B", copied.vodName)
        assertEquals(5, copied.lastEpisodeCount)
        assertEquals("1", copied.id)
    }

    // ========== ViewModel Integration Tests ==========

    @Test
    fun toggleFavorite_addsNewFavorite() = runTest {
        every { mockDao.observeAll() } returns flowOf(emptyList())
        coEvery { mockDao.isFavorite("v1") } returns false
        viewModel = FavoriteViewModel(favoriteDao = mockDao)

        viewModel.toggleFavorite(
            vodId = "v1",
            vodName = "Test",
            vodPic = null,
            sourceKey = "src",
            typeName = "Movie",
            episodeCount = 10,
        )

        coVerify {
            mockDao.insertOrReplace("v1", "Test", null, "src", "Movie", 10L, any())
        }
    }

    @Test
    fun toggleFavorite_removesExistingFavorite() = runTest {
        every { mockDao.observeAll() } returns flowOf(emptyList())
        coEvery { mockDao.isFavorite("v1") } returns true
        viewModel = FavoriteViewModel(favoriteDao = mockDao)

        viewModel.toggleFavorite(
            vodId = "v1",
            vodName = "Test",
            vodPic = null,
            sourceKey = "src",
            typeName = null,
            episodeCount = 0,
        )

        coVerify { mockDao.deleteByVodId("v1") }
    }

    @Test
    fun removeFavorite_callsDao() = runTest {
        every { mockDao.observeAll() } returns flowOf(emptyList())
        viewModel = FavoriteViewModel(favoriteDao = mockDao)

        viewModel.removeFavorite("v1")

        coVerify { mockDao.deleteByVodId("v1") }
    }

    @Test
    fun isFavorite_delegatesToDao() = runTest {
        every { mockDao.observeAll() } returns flowOf(emptyList())
        coEvery { mockDao.isFavorite("v1") } returns true
        viewModel = FavoriteViewModel(favoriteDao = mockDao)

        assertTrue(viewModel.isFavorite("v1"))
    }

    @Test
    fun isFavorite_returnsFalseOnException() = runTest {
        every { mockDao.observeAll() } returns flowOf(emptyList())
        coEvery { mockDao.isFavorite(any()) } throws RuntimeException("DB error")
        viewModel = FavoriteViewModel(favoriteDao = mockDao)

        assertFalse(viewModel.isFavorite("v1"))
    }
}
