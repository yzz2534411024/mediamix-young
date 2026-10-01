package com.mediamix.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 播放器模型单元测试
 *
 * 由于 ExoPlayerEngine 依赖 Android 环境，无法在 commonTest 中直接测试。
 * 本测试覆盖 ExoPlayerEngine 所依赖的公共模型逻辑：
 * - PlayerState 枚举映射
 * - TrackInfo 数据类构建与相等性
 * - PlayerEngineListener 回调行为
 * - 其他播放相关枚举
 */
class PlayerModelsTest {
    // ==================== PlayerState 枚举测试 ====================

    @Test
    fun playerState_hasAllExpectedValues() {
        // 验证所有播放状态枚举值存在
        val states = PlayerState.entries
        assertEquals(7, states.size)
        assertTrue(states.contains(PlayerState.IDLE))
        assertTrue(states.contains(PlayerState.BUFFERING))
        assertTrue(states.contains(PlayerState.READY))
        assertTrue(states.contains(PlayerState.PLAYING))
        assertTrue(states.contains(PlayerState.PAUSED))
        assertTrue(states.contains(PlayerState.ENDED))
        assertTrue(states.contains(PlayerState.ERROR))
    }

    @Test
    fun playerState_valueOf_roundTrip() {
        // 验证枚举名称到值的往返转换
        for (state in PlayerState.entries) {
            assertEquals(state, PlayerState.valueOf(state.name))
        }
    }

    @Test
    fun playerState_ordinalOrder() {
        // 验证枚举顺序（与 ExoPlayer 状态映射逻辑相关）
        assertEquals(0, PlayerState.IDLE.ordinal)
        assertEquals(1, PlayerState.BUFFERING.ordinal)
        assertEquals(2, PlayerState.READY.ordinal)
        assertEquals(3, PlayerState.PLAYING.ordinal)
        assertEquals(4, PlayerState.PAUSED.ordinal)
        assertEquals(5, PlayerState.ENDED.ordinal)
        assertEquals(6, PlayerState.ERROR.ordinal)
    }

    // ==================== TrackInfo 数据类测试 ====================

    @Test
    fun trackInfo_defaultValues() {
        // 验证 TrackInfo 默认值
        val track = TrackInfo(id = "0", label = "视频")
        assertEquals("0", track.id)
        assertEquals("视频", track.label)
        assertEquals(null, track.language)
        assertEquals(null, track.mimeType)
        assertEquals(null, track.bitrate)
        assertFalse(track.isSelected)
    }

    @Test
    fun trackInfo_fullConstruction() {
        // 验证完整构造
        val track =
            TrackInfo(
                id = "1",
                label = "英语音轨",
                language = "eng",
                mimeType = "audio/mp4a-latm",
                bitrate = 128000,
                isSelected = true,
            )
        assertEquals("1", track.id)
        assertEquals("英语音轨", track.label)
        assertEquals("eng", track.language)
        assertEquals("audio/mp4a-latm", track.mimeType)
        assertEquals(128000, track.bitrate)
        assertTrue(track.isSelected)
    }

    @Test
    fun trackInfo_equality() {
        // 验证数据类相等性
        val track1 = TrackInfo(id = "0", label = "视频", language = "zh")
        val track2 = TrackInfo(id = "0", label = "视频", language = "zh")
        val track3 = TrackInfo(id = "1", label = "视频", language = "zh")

        assertEquals(track1, track2)
        assertNotEquals(track1, track3)
        assertEquals(track1.hashCode(), track2.hashCode())
    }

    @Test
    fun trackInfo_copy() {
        // 验证 copy 方法（模拟轨道选中状态切换）
        val track = TrackInfo(id = "0", label = "字幕", language = "chi", isSelected = false)
        val selected = track.copy(isSelected = true)

        assertFalse(track.isSelected)
        assertTrue(selected.isSelected)
        assertEquals(track.id, selected.id)
        assertEquals(track.label, selected.label)
    }

    @Test
    fun trackInfo_listOperations() {
        // 验证轨道列表操作（模拟 getVideoTracks 等返回结果）
        val tracks =
            listOf(
                TrackInfo(id = "0", label = "高清", bitrate = 2000000, isSelected = true),
                TrackInfo(id = "1", label = "标清", bitrate = 800000, isSelected = false),
                TrackInfo(id = "2", label = "流畅", bitrate = 300000, isSelected = false),
            )

        assertEquals(3, tracks.size)
        assertEquals(1, tracks.count { it.isSelected })
        assertEquals("高清", tracks.first { it.isSelected }.label)

        // 按码率排序
        val sorted = tracks.sortedByDescending { it.bitrate }
        assertEquals("高清", sorted[0].label)
        assertEquals("流畅", sorted[2].label)
    }

    // ==================== PlayerEngineListener 回调测试 ====================

    @Test
    fun playerEngineListener_stateChangeCallback() {
        // 验证监听器状态回调
        var receivedState: PlayerState? = null
        val listener =
            object : PlayerEngineListener {
                override fun onStateChanged(state: PlayerState) {
                    receivedState = state
                }

                override fun onPositionChanged(positionMs: Long) {}

                override fun onBufferChanged(bufferedPercent: Int) {}

                override fun onError(
                    error: String,
                    code: Int?,
                ) {}

                override fun onFirstFrameRendered() {}

                override fun onPlaybackEnded() {}
            }

        listener.onStateChanged(PlayerState.BUFFERING)
        assertEquals(PlayerState.BUFFERING, receivedState)

        listener.onStateChanged(PlayerState.READY)
        assertEquals(PlayerState.READY, receivedState)
    }

    @Test
    fun playerEngineListener_errorCallback() {
        // 验证错误回调参数传递
        var errorMsg: String? = null
        var errorCode: Int? = null
        val listener =
            object : PlayerEngineListener {
                override fun onStateChanged(state: PlayerState) {}

                override fun onPositionChanged(positionMs: Long) {}

                override fun onBufferChanged(bufferedPercent: Int) {}

                override fun onError(
                    error: String,
                    code: Int?,
                ) {
                    errorMsg = error
                    errorCode = code
                }

                override fun onFirstFrameRendered() {}

                override fun onPlaybackEnded() {}
            }

        listener.onError("网络连接失败", 404)
        assertEquals("网络连接失败", errorMsg)
        assertEquals(404, errorCode)
    }

    @Test
    fun playerEngineListener_firstFrameAndEnded() {
        // 验证首帧渲染和播放结束回调
        var firstFrameCalled = false
        var playbackEndedCalled = false
        val listener =
            object : PlayerEngineListener {
                override fun onStateChanged(state: PlayerState) {}

                override fun onPositionChanged(positionMs: Long) {}

                override fun onBufferChanged(bufferedPercent: Int) {}

                override fun onError(
                    error: String,
                    code: Int?,
                ) {}

                override fun onFirstFrameRendered() {
                    firstFrameCalled = true
                }

                override fun onPlaybackEnded() {
                    playbackEndedCalled = true
                }
            }

        listener.onFirstFrameRendered()
        assertTrue(firstFrameCalled)

        listener.onPlaybackEnded()
        assertTrue(playbackEndedCalled)
    }

    @Test
    fun playerEngineListener_fullPlaybackFlow() {
        // 模拟完整播放流程的状态变化序列
        val stateHistory = mutableListOf<PlayerState>()
        val listener =
            object : PlayerEngineListener {
                override fun onStateChanged(state: PlayerState) {
                    stateHistory.add(state)
                }

                override fun onPositionChanged(positionMs: Long) {}

                override fun onBufferChanged(bufferedPercent: Int) {}

                override fun onError(
                    error: String,
                    code: Int?,
                ) {}

                override fun onFirstFrameRendered() {}

                override fun onPlaybackEnded() {}
            }

        // 模拟: IDLE → BUFFERING → READY → PLAYING → PAUSED → PLAYING → ENDED
        listener.onStateChanged(PlayerState.BUFFERING)
        listener.onStateChanged(PlayerState.READY)
        listener.onStateChanged(PlayerState.PLAYING)
        listener.onStateChanged(PlayerState.PAUSED)
        listener.onStateChanged(PlayerState.PLAYING)
        listener.onStateChanged(PlayerState.ENDED)

        assertEquals(6, stateHistory.size)
        assertEquals(PlayerState.BUFFERING, stateHistory[0])
        assertEquals(PlayerState.READY, stateHistory[1])
        assertEquals(PlayerState.PLAYING, stateHistory[2])
        assertEquals(PlayerState.PAUSED, stateHistory[3])
        assertEquals(PlayerState.PLAYING, stateHistory[4])
        assertEquals(PlayerState.ENDED, stateHistory[5])
    }

    // ==================== 其他播放枚举测试 ====================

    @Test
    fun playMode_allValues() {
        assertEquals(3, PlayMode.entries.size)
        assertNotNull(PlayMode.SEQUENTIAL)
        assertNotNull(PlayMode.LOOP_SINGLE)
        assertNotNull(PlayMode.LOOP_ALL)
    }

    @Test
    fun qualityLevel_labels() {
        // 验证画质等级中文标签
        assertEquals("流畅", QualityLevel.LOW.label)
        assertEquals("标清", QualityLevel.MEDIUM.label)
        assertEquals("高清", QualityLevel.HIGH.label)
        assertEquals("超清", QualityLevel.ULTRA.label)
    }

    @Test
    fun aspectMode_allValues() {
        // FILL / COVER 已删除：两端引擎都没有对应实现（见 AspectMode 注释），
        // UI 也从未暴露过这两个选项 —— 留着只会让「调整比例无效」被误判为 bug。
        assertEquals(3, AspectMode.entries.size)
    }
}
