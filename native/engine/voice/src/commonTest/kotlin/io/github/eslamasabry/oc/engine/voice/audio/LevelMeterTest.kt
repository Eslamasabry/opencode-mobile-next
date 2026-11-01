package io.github.eslamasabry.oc.engine.voice.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LevelMeterTest {
    @Test
    fun `B-MEDIA-003 bar i is lit when the level reaches (i + 1) over 12`() {
        assertEquals(9, LevelMeter.BARS)
        assertEquals(0, LevelMeter.litFor(0f))
        assertEquals(0, LevelMeter.litFor(1f / 12f - 0.0001f))
        assertEquals(1, LevelMeter.litFor(1f / 12f))
        assertEquals(6, LevelMeter.litFor(0.5f)) // ".5 lights the first six"
        assertEquals(9, LevelMeter.litFor(0.75f))
        assertEquals(9, LevelMeter.litFor(1f))
        assertTrue(LevelMeter.isLit(5, 0.5f))
        assertFalse(LevelMeter.isLit(6, 0.5f))
        assertFalse(LevelMeter.isLit(9, 1f))
    }
}
