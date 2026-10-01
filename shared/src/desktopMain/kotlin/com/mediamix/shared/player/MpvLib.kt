package com.mediamix.shared.player

import com.sun.jna.*

/**
 * mpv C API 的 JNA 绑定
 * 参考: https://github.com/mpv-player/mpv/blob/master/libmpv/client.h
 */
interface MpvLib : Library {
    fun mpv_create(): Pointer?

    fun mpv_initialize(handle: Pointer): Int

    fun mpv_destroy(handle: Pointer)

    fun mpv_command(
        handle: Pointer,
        args: Array<String?>,
    ): Int

    fun mpv_command_string(
        handle: Pointer,
        args: String,
    ): Int

    fun mpv_get_property(
        handle: Pointer,
        name: String,
        format: Int,
        data: Pointer,
    ): Int

    fun mpv_set_property(
        handle: Pointer,
        name: String,
        format: Int,
        data: Pointer,
    ): Int

    fun mpv_observe_property(
        handle: Pointer,
        replyUserData: Long,
        name: String,
        format: Int,
    ): Int

    fun mpv_unobserve_property(
        handle: Pointer,
        replyUserData: Long,
    ): Int

    fun mpv_wait_event(
        handle: Pointer,
        timeout: Double,
    ): Pointer?

    fun mpv_set_option(
        handle: Pointer,
        name: String,
        format: Int,
        data: Pointer?,
    ): Int

    fun mpv_error_string(error: Int): String?

    fun mpv_client_name(handle: Pointer): String?

    fun mpv_free(data: Pointer?)

    companion object {
        // mpv_error
        const val MPV_ERROR_SUCCESS = 0
        const val MPV_ERROR_EVENT_QUEUE_FULL = -1
        const val MPV_ERROR_PROPERTY_NOT_FOUND = -3
        const val MPV_ERROR_PROPERTY_FORMAT = -4
        const val MPV_ERROR_PROPERTY_UNAVAILABLE = -5

        // mpv_format 枚举 — 与 mpv_format.h 一致
        const val MPV_FORMAT_NONE = 0
        const val MPV_FORMAT_STRING = 1
        const val MPV_FORMAT_OSD_STRING = 2
        const val MPV_FORMAT_FLAG = 3
        const val MPV_FORMAT_INT64 = 4
        const val MPV_FORMAT_DOUBLE = 5
        const val MPV_FORMAT_NODE = 6

        // mpv_node 内部格式标识 — 与 mpv node.h mpv_node.format 一致
        const val NODE_FORMAT_NONE = 0
        const val NODE_FORMAT_STRING = 1
        const val NODE_FORMAT_FLAG = 2
        const val NODE_FORMAT_INT64 = 3
        const val NODE_FORMAT_DOUBLE = 4
        const val NODE_FORMAT_NODE_ARRAY = 7
        const val NODE_FORMAT_NODE_MAP = 8

        // mpv_event_id — 与 mpv client.h 保持一致
        const val MPV_EVENT_NONE = 0
        const val MPV_EVENT_SHUTDOWN = 1
        const val MPV_EVENT_LOG_MESSAGE = 2
        const val MPV_EVENT_GET_PROPERTY_REPLY = 3
        const val MPV_EVENT_SET_PROPERTY_REPLY = 4
        const val MPV_EVENT_COMMAND_REPLY = 5
        const val MPV_EVENT_START_FILE = 6
        const val MPV_EVENT_END_FILE = 7
        const val MPV_EVENT_FILE_LOADED = 8
        const val MPV_EVENT_IDLE = 11
        const val MPV_EVENT_TICK = 14
        const val MPV_EVENT_CLIENT_MESSAGE = 16
        const val MPV_EVENT_VIDEO_RECONFIG = 17
        const val MPV_EVENT_AUDIO_RECONFIG = 18
        const val MPV_EVENT_SEEK = 20
        const val MPV_EVENT_PLAYBACK_RESTART = 21
        const val MPV_EVENT_PROPERTY_CHANGE = 22

        // mpv_end_file_reason
        const val MPV_END_FILE_REASON_EOF = 0
        const val MPV_END_FILE_REASON_STOP = 2
        const val MPV_END_FILE_REASON_ERROR = 3
        const val MPV_END_FILE_REASON_REDIRECT = 4

        /**
         * 加载 mpv 客户端库。
         *
         * 不同发行版的 DLL 名不同：老版本是 `mpv-1.dll`，
         * 新一代 libmpv（如 `mpv-dev-x86_64-*.7z` 里的）是 `libmpv-2.dll`，
         * 因此按顺序回退尝试。全部缺失时抛出带明确指引的 [UnsatisfiedLinkError]。
         */
        fun getInstance(): MpvLib {
            val candidates = listOf("mpv-1", "libmpv-2", "libmpv")
            var lastError: Throwable? = null
            for (libName in candidates) {
                try {
                    return Native.load(libName, MpvLib::class.java)
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            throw UnsatisfiedLinkError(
                "无法加载 mpv 运行库（已尝试：${candidates.joinToString()}）。" +
                    "Desktop 端播放需要 mpv-1.dll 或 libmpv-2.dll；" +
                    "请把该 DLL 放到应用可执行文件（MediaMix.exe）同目录。" +
                    "底层错误：${lastError?.message}",
            )
        }
    }
}
