package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.database.FavoriteDao
import com.mediamix.shared.database.FavoriteItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * 收藏数据模型
 */
data class FavoriteItem(
    val id: String,
    val vodId: String,
    val vodName: String,
    val vodPic: String? = null,
    val sourceKey: String,
    val typeName: String? = null,
    val lastEpisodeCount: Int = 0,
    val addTime: Long = 0L,
)

/**
 * 收藏 ViewModel
 * 通过 FavoriteDao 连接 SQLDelight 数据库，
 * 使用 Flow 实现响应式数据更新。
 */
class FavoriteViewModel(
    private val favoriteDao: FavoriteDao,
) : ViewModel() {
    private val logger = Logger.withTag("FavoriteViewModel")

    private val _favorites = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val favorites: StateFlow<List<FavoriteItem>> = _favorites.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        observeFavorites()
    }

    /** 监听数据库变化，实时更新收藏列表 */
    private fun observeFavorites() {
        favoriteDao
            .observeAll()
            .flowOn(Dispatchers.Default)
            .onEach { entities ->
                _favorites.value = entities.map { it.toUiModel() }
            }.catch { e ->
                logger.e { "Observe favorites failed: ${e.message}" }
            }.launchIn(viewModelScope)
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val entities = favoriteDao.getAll()
                _favorites.value = entities.map { it.toUiModel() }
            } catch (e: Exception) {
                logger.e { "Load favorites failed: ${e.message}" }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFavorite(
        vodId: String,
        vodName: String,
        vodPic: String?,
        sourceKey: String,
        typeName: String?,
        episodeCount: Int,
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val isFav = favoriteDao.isFavorite(vodId)
                if (isFav) {
                    favoriteDao.deleteByVodId(vodId)
                    logger.d { "Removed favorite: $vodName" }
                } else {
                    favoriteDao.insertOrReplace(
                        vodId = vodId,
                        vodName = vodName,
                        vodPic = vodPic,
                        sourceKey = sourceKey,
                        typeName = typeName,
                        lastEpisodeCount = episodeCount.toLong(),
                        addTime = Clock.System.now().toEpochMilliseconds(),
                    )
                    logger.d { "Added favorite: $vodName" }
                }
            } catch (e: Exception) {
                logger.e { "Toggle favorite failed: ${e.message}" }
            }
        }
    }

    fun removeFavorite(id: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                favoriteDao.deleteByVodId(id)
            } catch (e: Exception) {
                logger.e { "Remove favorite failed: ${e.message}" }
            }
        }
    }

    fun isFavorite(vodId: String): Boolean =
        try {
            favoriteDao.isFavorite(vodId)
        } catch (e: Exception) {
            logger.e { "isFavorite check failed: ${e.message}" }
            false
        }
}

private fun FavoriteItemEntity.toUiModel(): FavoriteItem =
    FavoriteItem(
        id = vodId,
        vodId = vodId,
        vodName = vodName,
        vodPic = vodPic,
        sourceKey = sourceKey,
        typeName = typeName,
        lastEpisodeCount = lastEpisodeCount.toInt(),
        addTime = addTime,
    )
