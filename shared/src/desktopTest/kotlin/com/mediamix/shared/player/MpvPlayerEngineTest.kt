package com.mediamix.shared.player

import kotlin.test.*

/**
 * MpvPlayerEngine 相关单元测试。
 * 由于 mpv 运行时不可用，测试覆盖：
 * - PlayerState 状态枚举
 * - TrackInfo 数据类构建
 * - MpvLib JNA 常量值正确性
 * - PlayerEngineListener 接口行为
 */
class MpvPlayerEngineTest {

    // ==================== PlayerState 枚举测试 ====================

    @Test
    fun playerState_hasAllExpectedValues() {
        val states = PlayerState.entries
        assertEquals(7, states.size)
        assertTrue(PlayerState.IDLE in states)
        assertTrue(PlayerState.BUFFERING in states)
        assertTrue(PlayerState.READY in states)
        assertTrue(PlayerState.PLAYING in states)
        assertTrue(PlayerState.PAUSED in states)
        assertTrue(PlayerState.ENDED in states)
        assertTrue(PlayerState.ERROR in states)
    }

    @Test
    fun playerState_valueOf_roundTrip() {
        for (state in PlayerState.entries) {
            assertEquals(state, PlayerState.valueOf(state.name))
        }
    }

    // ==================== TrackInfo 数据类测试 ====================

    @Test
    fun trackInfo_basicConstruction() {
        val track = TrackInfo(
            id = "1",
            label = "Video Track 1",
            language = "eng",
            mimeType = "video",
            bitrate = 5000000
        )
        assertEquals("1", track.id)
        assertEquals("Video Track 1", track.label)
        assertEquals("eng", track.language)
        assertEquals("video", track.mimeType)
        assertEquals(5000000, track.bitrate)
        assertFalse(track.isSelected)
    }

    @Test
    fun trackInfo_withSelection() {
        val track = TrackInfo(
            id = "2",
            label = "Audio Track 2",
            language = "chi",
            mimeType = "audio",
            isSelected = true
        )
        assertTrue(track.isSelected)
        assertEquals("audio", track.mimeType)
    }

    @Test
    fun trackInfo_defaults() {
        val track = TrackInfo(id = "1", label = "Track 1")
        assertNull(track.language)
        assertNull(track.mimeType)
        assertNull(track.bitrate)
        assertFalse(track.isSelected)
    }

    @Test
    fun trackInfo_copyWithSelection() {
        val original = TrackInfo(id = "1", label = "Track", language = "eng", mimeType = "sub")
        val selected = original.copy(isSelected = true)
        assertEquals("1", selected.id)
        assertEquals("eng", selected.language)
        assertTrue(selected.isSelected)
    }

    @Test
    fun trackInfo_equality() {
        val a = TrackInfo(id = "1", label = "Track", language = "eng")
        val b = TrackInfo(id = "1", label = "Track", language = "eng")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ==================== PlayMode 枚举测试 ====================

    @Test
    fun playMode_hasAllExpectedValues() {
        val modes = PlayMode.entries
        assertEquals(3, modes.size)
        assertTrue(PlayMode.SEQUENTIAL in modes)
        assertTrue(PlayMode.LOOP_SINGLE in modes)
        assertTrue(PlayMode.LOOP_ALL in modes)
    }

    // ==================== AspectMode 枚举测试 ====================

    @Test
    fun aspectMode_hasAllExpectedValues() {
        val modes = AspectMode.entries
        assertEquals(5, modes.size)
        assertTrue(AspectMode.ORIGINAL in modes)
        assertTrue(AspectMode.FILL in modes)
        assertTrue(AspectMode.COVER in modes)
    }

    // ==================== MpvLib 常量测试 ====================

    @Test
    fun mpvLib_formatConstants_areCorrect() {
        assertEquals(0, MpvLib.MPV_FORMAT_NONE)
        assertEquals(1, MpvLib.MPV_FORMAT_STRING)
        assertEquals(2, MpvLib.MPV_FORMAT_OSD_STRING)
        assertEquals(3, MpvLib.MPV_FORMAT_FLAG)
        assertEquals(4, MpvLib.MPV_FORMAT_INT64)
        assertEquals(5, MpvLib.MPV_FORMAT_DOUBLE)
        assertEquals(6, MpvLib.MPV_FORMAT_NODE)
    }

    @Test
    fun mpvLib_nodeFormatConstants_areCorrect() {
        assertEquals(0, MpvLib.NODE_FORMAT_NONE)
        assertEquals(1, MpvLib.NODE_FORMAT_STRING)
        assertEquals(2, MpvLib.NODE_FORMAT_FLAG)
        assertEquals(3, MpvLib.NODE_FORMAT_INT64)
        assertEquals(4, MpvLib.NODE_FORMAT_DOUBLE)
        assertEquals(7, MpvLib.NODE_FORMAT_NODE_ARRAY)
        assertEquals(8, MpvLib.NODE_FORMAT_NODE_MAP)
    }

    @Test
    fun mpvLib_eventConstants_areCorrect() {
        assertEquals(0, MpvLib.MPV_EVENT_NONE)
        assertEquals(1, MpvLib.MPV_EVENT_SHUTDOWN)
        assertEquals(6, MpvLib.MPV_EVENT_START_FILE)
        assertEquals(7, MpvLib.MPV_EVENT_END_FILE)
        assertEquals(8, MpvLib.MPV_EVENT_FILE_LOADED)
        assertEquals(11, MpvLib.MPV_EVENT_IDLE)
        assertEquals(21, MpvLib.MPV_EVENT_PLAYBACK_RESTART)
        assertEquals(22, MpvLib.MPV_EVENT_PROPERTY_CHANGE)
    }

    @Test
    fun mpvLib_endFileReasonConstants_areCorrect() {
        assertEquals(0, MpvLib.MPV_END_FILE_REASON_EOF)
        assertEquals(2, MpvLib.MPV_END_FILE_REASON_STOP)
        assertEquals(3, MpvLib.MPV_END_FILE_REASON_ERROR)
        assertEquals(4, MpvLib.MPV_END_FILE_REASON_REDIRECT)
    }

    @Test
    fun mpvLib_errorConstants_areCorrect() {
        assertEquals(0, MpvLib.MPV_ERROR_SUCCESS)
        assertEquals(-1, MpvLib.MPV_ERROR_EVENT_QUEUE_FULL)
        assertEquals(-3, MpvLib.MPV_ERROR_PROPERTY_NOT_FOUND)
        assertEquals(-4, MpvLib.MPV_ERROR_PROPERTY_FORMAT)
        assertEquals(-5, MpvLib.MPV_ERROR_PROPERTY_UNAVAILABLE)
    }

    // ==================== PlayerEngineListener 接口测试 ====================

    @Test
    fun playerEngineListener_canBeImplemented() {
        val stateChanges = mutableListOf<PlayerState>()
        val positions = mutableListOf<Long>()

        val listener = object : PlayerEngineListener {
            override fun onStateChanged(state: PlayerState) { stateChanges.add(state) }
            override fun onPositionChanged(positionMs: Long) { positions.add(positionMs) }
            override fun onBufferChanged(bufferedPercent: Int) {}
            override fun onError(error: String, code: Int?) {}
            override fun onFirstFrameRendered() {}
            override fun onPlaybackEnded() {}
        }

        listener.onStateChanged(PlayerState.PLAYING)
        listener.onStateChanged(PlayerState.PAUSED)
        listener.onPositionChanged(5000L)
        listener.onPositionChanged(10000L)

        assertEquals(listOf(PlayerState.PLAYING, PlayerState.PAUSED), stateChanges)
        assertEquals(listOf(5000L, 10000L), positions)
    }

    // ==================== 数据模型集成测试 ====================

    @Test
    fun trackInfo_filterByType() {
        val tracks = listOf(
            TrackInfo(id = "1", label = "Video 1", mimeType = "video"),
            TrackInfo(id = "2", label = "Audio EN", mimeType = "audio", language = "eng"),
            TrackInfo(id = "3", label = "Audio ZH", mimeType = "audio", language = "chi"),
            TrackInfo(id = "4", label = "Sub EN", mimeType = "sub", language = "eng"),
        )

        val videoTracks = tracks.filter { it.mimeType == "video" }
        val audioTracks = tracks.filter { it.mimeType == "audio" }
        val subTracks = tracks.filter { it.mimeType == "sub" }

        assertEquals(1, videoTracks.size)
        assertEquals(2, audioTracks.size)
        assertEquals(1, subTracks.size)
        assertEquals("Audio EN", audioTracks[0].label)
    }

    @Test
    fun qualityLevel_labels() {
        assertEquals("流畅", QualityLevel.LOW.label)
        assertEquals("标清", QualityLevel.MEDIUM.label)
        assertEquals("高清", QualityLevel.HIGH.label)
        assertEquals("超清", QualityLevel.ULTRA.label)
    }
}
