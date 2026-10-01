package com.mediamix.shared.player

import co.touchlab.kermit.Logger
import com.sun.jna.*

/**
 * Desktop actual 实现 PlayerEngine
 *
 * 使用 JNA 调用 mpv 客户端库，实现统一播放器接口。
 * 事件循环在独立守护线程中运行，通过 mpv_wait_event 轮询事件并回调 listener。
 *
 * mpv_node 结构体布局 (64-bit, 16 bytes):
 *   offset 0: data   (pointer/union, 8 bytes) — 在前
 *   offset 8: format (int, 4 bytes)           — 在后
 */
actual class PlayerEngine actual constructor() {
    private val logger = Logger.withTag("MpvPlayerEngine")
    private var handle: Pointer? = null
    private val mpv: MpvLib by lazy { MpvLib.getInstance() }
    private var listener: PlayerEngineListener? = null
    private var currentState: PlayerState = PlayerState.IDLE
    private var firstFrameReported = false
    private var eventThread: Thread? = null

    @Volatile
    private var running = false

    // ==================== 生命周期 ====================

    actual fun initialize() {
        handle = mpv.mpv_create()
            ?: throw RuntimeException("Failed to create mpv instance")

        // 禁用默认键绑定和 OSC
        val flagOff = Memory(4).apply { setInt(0, 0) }
        mpv.mpv_set_option(handle!!, "input-default-bindings", MpvLib.MPV_FORMAT_FLAG, flagOff)
        mpv.mpv_set_option(handle!!, "osc", MpvLib.MPV_FORMAT_FLAG, flagOff)
        // 禁用 OSD 消息显示
        val osdLevel = Memory(4).apply { setInt(0, 0) }
        mpv.mpv_set_option(handle!!, "osd-level", MpvLib.MPV_FORMAT_INT64, osdLevel)

        val rc = mpv.mpv_initialize(handle!!)
        if (rc < 0) {
            mpv.mpv_destroy(handle!!)
            handle = null
            throw RuntimeException("mpv_initialize failed: ${mpv.mpv_error_string(rc)}")
        }

        // 观察关键属性
        mpv.mpv_observe_property(handle!!, 1, "time-pos", MpvLib.MPV_FORMAT_DOUBLE)
        mpv.mpv_observe_property(handle!!, 2, "duration", MpvLib.MPV_FORMAT_DOUBLE)
        mpv.mpv_observe_property(handle!!, 3, "pause", MpvLib.MPV_FORMAT_FLAG)
        mpv.mpv_observe_property(handle!!, 4, "idle-active", MpvLib.MPV_FORMAT_FLAG)
        mpv.mpv_observe_property(handle!!, 5, "demuxer-cache-duration", MpvLib.MPV_FORMAT_DOUBLE)
        mpv.mpv_observe_property(handle!!, 6, "eof-reached", MpvLib.MPV_FORMAT_FLAG)

        startEventLoop()
    }

    actual fun release() {
        running = false
        val thread = eventThread
        eventThread = null
        try {
            thread?.interrupt()
            thread?.join(5000) // Increased from 2s to 5s
            if (thread?.isAlive == true) {
                logger.e("mpv event thread did not exit within 5s")
            }
        } catch (_: InterruptedException) {
        }
        synchronized(this) {
            handle?.let { mpv.mpv_destroy(it) }
            handle = null
        }
        currentState = PlayerState.IDLE
    }

    // ==================== 播放控制 ====================

    actual fun setSource(
        url: String,
        headers: Map<String, String>?,
    ) {
        val h = handle ?: return
        // mpv 的 HTTP 头通过 `--http-header-fields` 属性下发；没有头时清空，避免沿用上一集
        val headerValue =
            headers
                ?.entries
                ?.filter { it.key.isNotBlank() && it.value.isNotBlank() }
                ?.joinToString(",") { "${it.key}: ${it.value}" }
                ?: ""
        setPropertyString(h, "http-header-fields", headerValue)
        mpv.mpv_command(h, arrayOf("loadfile", url, "replace"))
        firstFrameReported = false
        updateState(PlayerState.BUFFERING)
    }

    /** Desktop 侧由 mpv 自行决定解码器，这里保留接口一致性。 */
    actual fun setDecodeMode(preferSoftware: Boolean) {
        val h = handle ?: return
        setPropertyString(h, "hwdec", if (preferSoftware) "no" else "auto-safe")
    }

    actual fun play() {
        val h = handle ?: return
        val mem = Memory(4).apply { setInt(0, 0) } // false = not paused
        mpv.mpv_set_property(h, "pause", MpvLib.MPV_FORMAT_FLAG, mem)
    }

    actual fun pause() {
        val h = handle ?: return
        val mem = Memory(4).apply { setInt(0, 1) } // true = paused
        mpv.mpv_set_property(h, "pause", MpvLib.MPV_FORMAT_FLAG, mem)
    }

    actual fun stop() {
        val h = handle ?: return
        mpv.mpv_command(h, arrayOf("stop"))
    }

    actual fun seekTo(positionMs: Long) {
        val h = handle ?: return
        val seconds = positionMs / 1000.0
        mpv.mpv_command(h, arrayOf("seek", "%.3f".format(seconds), "absolute"))
    }

    actual fun setPlaybackSpeed(speed: Float) {
        val h = handle ?: return
        setPropertyDouble(h, "speed", speed.toDouble())
    }

    actual fun setVolume(volume: Float) {
        val h = handle ?: return
        // mpv 音量范围 0-100，接口范围 0.0-1.0
        setPropertyDouble(h, "volume", (volume * 100).toDouble())
    }

    // ==================== 状态查询 ====================

    actual fun getPosition(): Long {
        val h = handle ?: return 0L
        return (getPropertyDouble(h, "time-pos") * 1000).toLong()
    }

    actual fun getDuration(): Long {
        val h = handle ?: return 0L
        return (getPropertyDouble(h, "duration") * 1000).toLong()
    }

    actual fun isPlaying(): Boolean {
        val h = handle ?: return false
        // pause == false 表示正在播放
        return !getPropertyFlag(h, "pause")
    }

    actual fun getPlayerState(): PlayerState = currentState

    actual fun setListener(listener: PlayerEngineListener?) {
        this.listener = listener
    }

    actual fun getBufferedPercentage(): Int {
        val h = handle ?: return 0
        val cacheDuration = getPropertyDouble(h, "demuxer-cache-duration")
        val duration = getPropertyDouble(h, "duration")
        if (duration <= 0) return 0
        return ((cacheDuration / duration) * 100).toInt().coerceIn(0, 100)
    }

    // ==================== Surface ====================

    actual fun setSurface(surface: Any?) {
        val h = handle ?: return
        if (surface is Long) {
            val mem = Memory(8).apply { setLong(0, surface) }
            mpv.mpv_set_option(h, "wid", MpvLib.MPV_FORMAT_INT64, mem)
        }
    }

    // ==================== 视频轨道 ====================

    actual fun getVideoTracks(): List<TrackInfo> {
        val h = handle ?: return emptyList()
        val tracks = parseTrackList(h)
        val currentVid = getPropertyString(h, "vid")
        return tracks
            .filter { it.mimeType == "video" }
            .map { it.copy(isSelected = it.id == currentVid) }
    }

    actual fun setVideoTrack(trackId: String): Boolean {
        val h = handle ?: return false
        return try {
            setPropertyString(h, "vid", trackId)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ==================== 音频轨道 ====================

    actual fun getAudioTracks(): List<TrackInfo> {
        val h = handle ?: return emptyList()
        val tracks = parseTrackList(h)
        val currentAid = getPropertyString(h, "aid")
        return tracks
            .filter { it.mimeType == "audio" }
            .map { it.copy(isSelected = it.id == currentAid) }
    }

    actual fun setAudioTrack(trackId: String): Boolean {
        val h = handle ?: return false
        return try {
            setPropertyString(h, "aid", trackId)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ==================== 字幕轨道 ====================

    actual fun getSubtitleTracks(): List<TrackInfo> {
        val h = handle ?: return emptyList()
        val tracks = parseTrackList(h)
        val currentSid = getPropertyString(h, "sid")
        return tracks
            .filter { it.mimeType == "sub" }
            .map { it.copy(isSelected = it.id == currentSid) }
    }

    actual fun setSubtitleTrack(trackId: String): Boolean {
        val h = handle ?: return false
        return try {
            setPropertyString(h, "sid", trackId)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ==================== 内部辅助方法 ====================

    private fun updateState(newState: PlayerState) {
        if (currentState != newState) {
            currentState = newState
            listener?.onStateChanged(newState)
        }
    }

    // --- mpv 属性读写 ---

    private fun getPropertyDouble(
        h: Pointer,
        name: String,
    ): Double {
        val mem = Memory(8).apply { setDouble(0, 0.0) }
        val rc = mpv.mpv_get_property(h, name, MpvLib.MPV_FORMAT_DOUBLE, mem)
        return if (rc >= 0) mem.getDouble(0) else 0.0
    }

    private fun getPropertyFlag(
        h: Pointer,
        name: String,
    ): Boolean {
        val mem = Memory(4).apply { setInt(0, 0) }
        val rc = mpv.mpv_get_property(h, name, MpvLib.MPV_FORMAT_FLAG, mem)
        return if (rc >= 0) mem.getInt(0) != 0 else false
    }

    private fun getPropertyString(
        h: Pointer,
        name: String,
    ): String {
        val mem = Memory(8).apply { setPointer(0, null) }
        val rc = mpv.mpv_get_property(h, name, MpvLib.MPV_FORMAT_STRING, mem)
        if (rc < 0) return ""
        val ptr = mem.getPointer(0) ?: return ""
        return try {
            ptr.getString(0)
        } finally {
            mpv.mpv_free(ptr)
        }
    }

    private fun setPropertyDouble(
        h: Pointer,
        name: String,
        value: Double,
    ) {
        val mem = Memory(8).apply { setDouble(0, value) }
        mpv.mpv_set_property(h, name, MpvLib.MPV_FORMAT_DOUBLE, mem)
    }

    private fun setPropertyString(
        h: Pointer,
        name: String,
        value: String,
    ) {
        val mem = Memory(8).apply { setString(0, value) }
        mpv.mpv_set_property(h, name, MpvLib.MPV_FORMAT_STRING, mem)
    }

    // --- mpv_node 解析 ---
    // mpv_node 布局 (64-bit, 16 bytes):
    //   offset 0: data   (pointer/union, 8 bytes)
    //   offset 8: format (int, 4 bytes)
    //   total sizeof = 16

    /**
     * 解析 track-list 属性，返回所有轨道信息。
     * mimeType 字段存储轨道类型 ("video" / "audio" / "sub")。
     */
    private fun parseTrackList(h: Pointer): List<TrackInfo> {
        // 分配 mpv_node (16 bytes): data=0, format=0
        val nodeMem =
            Memory(16).apply {
                setPointer(0, null) // data
                setInt(8, MpvLib.MPV_FORMAT_NONE) // format
            }
        val rc = mpv.mpv_get_property(h, "track-list", MpvLib.MPV_FORMAT_NODE, nodeMem)
        if (rc < 0) return emptyList()

        val format = nodeMem.getInt(8)
        if (format != MpvLib.NODE_FORMAT_NODE_ARRAY) return emptyList()

        val listPtr = nodeMem.getPointer(0) ?: return emptyList()
        // mpv_node_list: { int num; mpv_node *values; ... }
        val numEntries = listPtr.getInt(0).toLong()
        val valuesPtr = listPtr.getPointer(8) ?: return emptyList()

        val result = mutableListOf<TrackInfo>()
        for (i in 0 until numEntries) {
            val nodePtr =
                Pointer(
                    Pointer.nativeValue(valuesPtr) + i * MpvNode.SIZE,
                )
            val nodeFormat = nodePtr.getInt(8) // format at offset 8
            if (nodeFormat != MpvLib.NODE_FORMAT_NODE_MAP) continue
            val mapPtr = nodePtr.getPointer(0) ?: continue // data at offset 0
            val trackMap = parseNodeMap(mapPtr)

            val id = trackMap["id"]?.toString() ?: continue
            val type = trackMap["type"]?.toString() ?: continue
            val title = trackMap["title"]?.toString()
            val lang = trackMap["lang"]?.toString()

            result.add(
                TrackInfo(
                    id = id,
                    label = title ?: lang ?: "Track $id",
                    language = lang,
                    mimeType = type,
                    bitrate = null,
                ),
            )
        }

        // 释放 mpv 分配的 node 内存
        val dataPtr = nodeMem.getPointer(0)
        if (dataPtr != null) mpv.mpv_free(dataPtr)
        return result
    }

    /**
     * 解析 mpv_node map，返回 key -> value 映射。
     * mapPtr 指向 mpv_node_list: { int num; char **keys; mpv_node *values; }
     */
    private fun parseNodeMap(mapPtr: Pointer): Map<String, Any?> {
        val numKeys = mapPtr.getInt(0).toLong()
        val keysPtr = mapPtr.getPointer(8) ?: return emptyMap() // char **keys
        val valuesPtr = mapPtr.getPointer(16) ?: return emptyMap() // mpv_node *values

        val map = mutableMapOf<String, Any?>()
        for (i in 0 until numKeys) {
            val keyPtr = keysPtr.getPointer(i * 8L)
            val key = keyPtr?.getString(0) ?: continue
            val nodePtr =
                Pointer(
                    Pointer.nativeValue(valuesPtr) + i * MpvNode.SIZE,
                )
            map[key] = readNodeValue(nodePtr)
        }
        return map
    }

    /**
     * 读取 mpv_node 的值。
     * nodePtr 布局: offset 0 = data, offset 8 = format
     */
    private fun readNodeValue(nodePtr: Pointer): Any? {
        val dataPtr = nodePtr.getPointer(0) // data at offset 0
        val nodeFormat = nodePtr.getInt(8) // format at offset 8
        return when (nodeFormat) {
            MpvLib.NODE_FORMAT_STRING -> dataPtr?.getString(0)
            MpvLib.NODE_FORMAT_FLAG -> dataPtr?.getInt(0) != 0
            MpvLib.NODE_FORMAT_INT64 -> dataPtr?.getLong(0)
            MpvLib.NODE_FORMAT_DOUBLE -> dataPtr?.getDouble(0)
            else -> null
        }
    }

    // --- 事件循环 ---

    private fun startEventLoop() {
        running = true
        eventThread =
            Thread({
                while (running) {
                    try {
                        processEvents()
                    } catch (_: Exception) {
                        break
                    }
                }
            }, "mpv-event-loop").apply {
                isDaemon = true
                start()
            }
    }

    private fun processEvents() {
        val h = handle ?: return
        val event = mpv.mpv_wait_event(h, 0.1) ?: return

        // mpv_event 结构体布局 (64-bit, 24 bytes):
        //   offset 0:  event_id       (int, 4 bytes)
        //   offset 4:  error          (int, 4 bytes)
        //   offset 8:  reply_userdata (long, 8 bytes)
        //   offset 16: data           (pointer, 8 bytes)
        val eventId = event.getInt(0)
        val data = event.getPointer(16)

        when (eventId) {
            MpvLib.MPV_EVENT_NONE -> { /* timeout, no event */ }

            MpvLib.MPV_EVENT_SHUTDOWN -> {
                updateState(PlayerState.ENDED)
                running = false
            }

            MpvLib.MPV_EVENT_START_FILE -> {
                firstFrameReported = false
                updateState(PlayerState.BUFFERING)
            }

            MpvLib.MPV_EVENT_FILE_LOADED -> {
                updateState(PlayerState.READY)
            }

            MpvLib.MPV_EVENT_PLAYBACK_RESTART -> {
                if (!getPropertyFlag(h, "pause")) {
                    updateState(PlayerState.PLAYING)
                }
            }

            MpvLib.MPV_EVENT_END_FILE -> {
                if (data != null) {
                    // mpv_event_end_file: { int reason; int error; ... }
                    val reason = data.getInt(0)
                    when (reason) {
                        MpvLib.MPV_END_FILE_REASON_EOF -> {
                            updateState(PlayerState.ENDED)
                            listener?.onPlaybackEnded()
                        }
                        MpvLib.MPV_END_FILE_REASON_ERROR -> {
                            val errorCode = data.getInt(4)
                            val errorMsg = mpv.mpv_error_string(errorCode) ?: "Unknown error"
                            updateState(PlayerState.ERROR)
                            listener?.onError(errorMsg, errorCode)
                        }
                        MpvLib.MPV_END_FILE_REASON_STOP -> {
                            updateState(PlayerState.IDLE)
                        }
                    }
                }
            }

            MpvLib.MPV_EVENT_PROPERTY_CHANGE -> {
                if (data != null) handlePropertyChange(data)
            }

            MpvLib.MPV_EVENT_IDLE -> {
                updateState(PlayerState.IDLE)
            }
        }
    }

    /**
     * 处理 MPV_EVENT_PROPERTY_CHANGE 事件。
     * data 指向 mpv_event_property 结构体 (64-bit, 24 bytes):
     *   offset 0:  name   (char*, 8 bytes) — 属性名
     *   offset 8:  format (int, 4 bytes)
     *   offset 16: data   (void*, 8 bytes) — 属性值
     */
    private fun handlePropertyChange(data: Pointer) {
        val namePtr = data.getPointer(0) ?: return
        val propName = namePtr.getString(0)
        val format = data.getInt(8)
        val valuePtr = data.getPointer(16)

        when (propName) {
            "time-pos" -> {
                if (format == MpvLib.MPV_FORMAT_DOUBLE && valuePtr != null) {
                    val posMs = (valuePtr.getDouble(0) * 1000).toLong()
                    listener?.onPositionChanged(posMs)
                    if (currentState == PlayerState.BUFFERING) {
                        updateState(PlayerState.PLAYING)
                    }
                }
            }

            "duration" -> {
                // 时长变化通知，可用于更新 UI
            }

            "pause" -> {
                if (format == MpvLib.MPV_FORMAT_FLAG && valuePtr != null) {
                    val paused = valuePtr.getInt(0) != 0
                    if (paused) {
                        updateState(PlayerState.PAUSED)
                    } else if (currentState == PlayerState.PAUSED) {
                        updateState(PlayerState.PLAYING)
                    }
                }
            }

            "idle-active" -> {
                if (format == MpvLib.MPV_FORMAT_FLAG && valuePtr != null) {
                    val idle = valuePtr.getInt(0) != 0
                    if (idle) updateState(PlayerState.IDLE)
                }
            }

            "demuxer-cache-duration" -> {
                if (format == MpvLib.MPV_FORMAT_DOUBLE && valuePtr != null) {
                    val cacheDuration = valuePtr.getDouble(0)
                    val duration = getPropertyDouble(handle ?: return, "duration")
                    if (duration > 0) {
                        val pct = ((cacheDuration / duration) * 100).toInt().coerceIn(0, 100)
                        listener?.onBufferChanged(pct)
                    }
                }
            }

            "eof-reached" -> {
                if (format == MpvLib.MPV_FORMAT_FLAG && valuePtr != null) {
                    val eof = valuePtr.getInt(0) != 0
                    if (eof) {
                        updateState(PlayerState.ENDED)
                        listener?.onPlaybackEnded()
                    }
                }
            }
        }

        // 首帧检测: time-pos 首次变化且状态为 PLAYING 时触发
        if (propName == "time-pos" && !firstFrameReported && currentState == PlayerState.PLAYING) {
            firstFrameReported = true
            listener?.onFirstFrameRendered()
        }
    }

    // --- mpv_node 结构体常量 ---

    /**
     * mpv_node C 结构体 (64-bit 布局, 16 bytes):
     *   union { char*, int, int64_t, double, mpv_node_list* } data;  // offset 0, 8 bytes
     *   mpv_format format;                                            // offset 8, 4 bytes
     *   // + 4 bytes padding to align to 8
     */
    private object MpvNode {
        const val SIZE = 16
    }
}
