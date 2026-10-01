package com.mediamix.ui.viewmodel

import com.mediamix.shared.player.AspectMode
import com.mediamix.shared.player.PlayMode
import com.mediamix.shared.player.PlayerState
import com.mediamix.shared.player.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlayerViewModelTest {
    @Test
    fun playerState_hasAllExpectedValues() {
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
    fun playMode_hasAllExpectedValues() {
        val modes = PlayMode.entries
        assertEquals(3, modes.size)
        assertTrue(modes.contains(PlayMode.SEQUENTIAL))
        assertTrue(modes.contains(PlayMode.LOOP_SINGLE))
        assertTrue(modes.contains(PlayMode.LOOP_ALL))
    }

    @Test
    fun aspectMode_hasAllExpectedValues() {
        val modes = AspectMode.entries
        assertEquals(5, modes.size)
        assertTrue(modes.contains(AspectMode.ORIGINAL))
        assertTrue(modes.contains(AspectMode.RATIO_16_9))
        assertTrue(modes.contains(AspectMode.RATIO_4_3))
        assertTrue(modes.contains(AspectMode.FILL))
        assertTrue(modes.contains(AspectMode.COVER))
    }

    @Test
    fun subtitleTrack_creation() {
        val track =
            SubtitleTrack(
                label = "English",
                language = "en",
                entries = emptyList(),
            )
        assertEquals("English", track.label)
        assertEquals("en", track.language)
        assertTrue(track.entries.isEmpty())
    }

    @Test
    fun subtitleTrack_equality() {
        val t1 = SubtitleTrack(label = "English", language = "en", entries = emptyList())
        val t2 = SubtitleTrack(label = "English", language = "en", entries = emptyList())
        val t3 = SubtitleTrack(label = "Chinese", language = "zh", entries = emptyList())
        assertEquals(t1, t2)
        assertNotEquals(t1, t3)
    }

    @Test
    fun speedOptions_defaultList() {
        val options = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f)
        assertEquals(12, options.size)
        assertEquals(0.25f, options.first())
        assertEquals(3.0f, options.last())
        assertTrue(options.contains(1.0f))
    }
}
