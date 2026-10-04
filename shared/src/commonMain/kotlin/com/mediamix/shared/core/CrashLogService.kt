package com.mediamix.shared.core

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 崩溃日志：写入 / 列出 / 读取 / 清空。
 *
 * 正式版的"崩溃日志查看"功能的数据源（替代原先的调试页 DebugScreen）。
 * 文件放 [dataDir]/crash/，最多保留 [MAX_FILES] 份，超限自动删最旧的。
 *
 * 平台接入：Android 在 MainActivity、Desktop 在 Main 的入口处调用 [installGlobalHandler]，
 * 未捕获异常会自动落盘后再交回系统默认处理器（保证系统仍能正常崩溃上报）。
 */
class CrashLogService(private val dataDir: String) {

    private val crashDir: File
        get() = File(dataDir, "crash").apply { mkdirs() }

    data class Entry(val fileName: String, val sizeBytes: Long, val lastModifiedMs: Long)

    /** 安装全局未捕获异常处理器（幂等：重复调用会覆盖前一次的安装）。 */
    fun installGlobalHandler(deviceInfo: () -> String = { "" }) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(throwable, deviceInfo()) }
            // 交回系统默认处理（Android 的崩溃对话框 / 进程退出）
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 手动记录一条异常（用于被 catch 住但值得留痕的严重错误）。 */
    fun write(throwable: Throwable, deviceInfo: String = "") {
        runCatching {
            val stamp = System.currentTimeMillis()
            val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            File(crashDir, "crash-$stamp.log").writeText(
                buildString {
                    appendLine("time: $stamp")
                    if (deviceInfo.isNotBlank()) {
                        appendLine("device: $deviceInfo")
                    }
                    appendLine("exception: ${throwable.javaClass.name}")
                    appendLine("message: ${throwable.message.orEmpty()}")
                    appendLine()
                    append(stack)
                },
            )
            trim()
        }
    }

    /** 全部崩溃记录，新的在前。 */
    fun list(): List<Entry> =
        crashDir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.map { Entry(it.name, it.length(), it.lastModified()) }
            ?.sortedByDescending { it.lastModifiedMs }
            .orEmpty()

    fun read(fileName: String): String? {
        if (!fileName.startsWith("crash-") || fileName.contains("..")) return null // 防路径穿越
        return File(crashDir, fileName).takeIf { it.isFile }?.readText()
    }

    fun clear() {
        crashDir.listFiles { f -> f.name.endsWith(".log") }?.forEach { it.delete() }
    }

    private fun trim() {
        val files = list()
        files.drop(MAX_FILES).forEach { File(crashDir, it.fileName).delete() }
    }

    companion object {
        private const val MAX_FILES = 20
    }
}
