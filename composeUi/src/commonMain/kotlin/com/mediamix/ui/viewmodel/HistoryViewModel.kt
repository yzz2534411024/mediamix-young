package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mediamix.shared.database.WatchHistoryDao
import com.mediamix.shared.database.WatchHistoryItemEntity
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
 * 观看历史数据模型
 */
data class WatchHistoryItem(
    val id: String,
    val vodId: String,
    val vodName: String,
    val vodPic: String? = null,
    val sourceKey: String,
    val episodeName: String? = null,
    val lastWatchTime: Long = 0L,
)

/**
 * 观看历史 ViewModel
 * 通过 WatchHistoryDao 连接 SQLDelight 数据库，
 * 使用 Flow 实现响应式数据更新。
 */
class HistoryViewModel(
    private val watchHistoryDao: WatchHistoryDao,
) : ViewModel() {
    private val logger = Logger.withTag("HistoryViewModel")

    private val _histories = MutableStateFlow<List<WatchHistoryItem>>(emptyList())
    val histories: StateFlow<List<WatchHistoryItem>> = _histories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        observeHistories()
    }

    /** 监听数据库变化，实时更新历史列表 */
    private fun observeHistories() {
        watchHistoryDao
            .observeAll()
            .flowOn(Dispatchers.Default)
            .onEach { entities ->
                _histories.value = entities.map { it.toUiModel() }
            }.catch { e ->
                logger.e { "Observe history failed: ${e.message}" }
            }.launchIn(viewModelScope)
    }

    fun loadHistories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val entities = watchHistoryDao.getAll()
                _histories.value = entities.map { it.toUiModel() }
            } catch (e: Exception) {
                logger.e { "Load history failed: ${e.message}" }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addOrUpdateHistory(
        vodId: String,
        vodName: String,
        vodPic: String?,
        sourceKey: String,
        episodeName: String?,
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                watchHistoryDao.insertOrReplace(
                    vodId = vodId,
                    vodName = vodName,
                    vodPic = vodPic,
                    sourceKey = sourceKey,
                    episodeName = episodeName,
                    lastWatchTime = Clock.System.now().toEpochMilliseconds(),
                )
            } catch (e: Exception) {
                logger.e { "Add/update history failed: ${e.message}" }
            }
        }
    }

    fun deleteHistory(id: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                watchHistoryDao.deleteByVodId(id)
            } catch (e: Exception) {
                logger.e { "Delete history failed: ${e.message}" }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                watchHistoryDao.clearAll()
            } catch (e: Exception) {
                logger.e { "Clear all history failed: ${e.message}" }
            }
        }
    }
}

private fun WatchHistoryItemEntity.toUiModel(): WatchHistoryItem =
    WatchHistoryItem(
        id = vodId,
        vodId = vodId,
        vodName = vodName,
        vodPic = vodPic,
        sourceKey = sourceKey,
        episodeName = episodeName,
        lastWatchTime = lastWatchTime,
    )
