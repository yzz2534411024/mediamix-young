package com.mediamix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediamix.shared.core.CrashLogService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 正式版"崩溃日志"页的数据层：列表 / 详情 / 清空。
 * 数据来自 [CrashLogService]（双端入口处安装的未捕获异常处理器落盘的文件）。
 */
class CrashLogViewModel(
    private val crashLogService: CrashLogService,
) : ViewModel() {

    data class Entry(val fileName: String, val sizeBytes: Long, val lastModifiedMs: Long)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _detail = MutableStateFlow<Pair<String, String>?>(null)
    /** 当前查看的崩溃详情：文件名 to 内容；null 表示未在查看。 */
    val detail: StateFlow<Pair<String, String>?> = _detail.asStateFlow()

    private val _cleared = MutableStateFlow(0)
    /** 清空完成信号（自增），页面据此提示。 */
    val cleared: StateFlow<Int> = _cleared.asStateFlow()

    fun load() {
        _entries.value = crashLogService.list().map { Entry(it.fileName, it.sizeBytes, it.lastModifiedMs) }
    }

    fun open(fileName: String) {
        viewModelScope.launch {
            val content = crashLogService.read(fileName) ?: "（读取失败）"
            _detail.value = fileName to content
        }
    }

    fun closeDetail() {
        _detail.value = null
    }

    fun clearAll() {
        viewModelScope.launch {
            crashLogService.clear()
            _cleared.value += 1
            load()
        }
    }
}
