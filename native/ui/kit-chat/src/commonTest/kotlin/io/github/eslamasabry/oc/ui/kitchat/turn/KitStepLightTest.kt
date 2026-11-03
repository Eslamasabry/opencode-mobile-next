package io.github.eslamasabry.oc.ui.kitchat.turn

import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepLightLap
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepLightState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dart `kit_step_light_test`: one lap of 2 s, the split, the states. */
class KitStepLightTest {
    private fun at(ms: Long, state: KitStepLightState = KitStepLightState.Working) =
        KitStepLightLap.frameAt(KitStepLightLap.phase(ms, KitStepLightLap.lapMs(state)!!))

    @Test
    fun `B-KIT-036 one 2-second lap - a light from above, both halves filling, the light going on, a quiet end`() {
        assertEquals(2000, KitStepLightLap.lapMs(KitStepLightState.Working))
        // 0-28 %: one light from above, no outline yet.
        at(200).let { assertEquals(1, it.lines); assertEquals(0, it.paths) }
        // 26-66 %: both halves fill at once with a head each, no light on the rail.
        at(800).let { assertEquals(4, it.paths); assertEquals(0, it.lines) }
        // 64-95 %: one light goes on down the rail, the outline dims.
        at(1500).let {
            assertEquals(2, it.paths)
            assertEquals(1, it.lines)
            assertTrue(it.railBelow != null && it.outlineAlpha < .75f)
        }
        // 95-100 % is quiet, then the lap starts again.
        assertEquals(0, at(1980).lines)
        assertEquals(1, at(1980 + 220).lines)
    }

    @Test
    fun `B-KIT-036 a background process runs a calmer 4-second lap`() {
        assertEquals(4000, KitStepLightLap.lapMs(KitStepLightState.Background))
        // 40 % of 4 s: filling. A 2 s lap would be back at its start.
        assertTrue(at(1600, KitStepLightState.Background).fill > 0f)
    }

    @Test
    fun `B-KIT-036 the halves mirror each other and meet at the bottom centre`() {
        for (stroke in listOf(1.8f, 2.6f)) {
            val l = KitStepLightLap.outlineHalf(20f, stroke, left = true)
            val r = KitStepLightLap.outlineHalf(20f, stroke, left = false)
            assertTrue(abs(KitStepLightLap.length(l) - KitStepLightLap.length(r)) < 1e-3f)
            for (half in listOf(l, r)) {
                assertTrue(hypot(half.first().x - 10f, half.first().y - stroke / 2) < 1e-4f)
                assertTrue(hypot(half.last().x - 10f, half.last().y - (20f - stroke / 2)) < 1e-4f)
            }
            val lMin = l.minOf { it.x }
            val lMax = l.maxOf { it.x }
            val rMax = r.maxOf { it.x }
            assertTrue(abs(lMin - (20f - rMax)) < 1e-3f)
            assertTrue(abs(lMax - 10f) < 1e-3f)
            assertTrue(abs(l.minOf { it.y } - r.minOf { it.y }) < 1e-4f)
            // Inside the tile: the stroke never grows the node.
            assertTrue(abs(lMin - stroke / 2) < 1e-3f)
        }
    }

    @Test
    fun `B-KIT-036 idle has no light and working has one`() {
        assertNull(KitStepLightLap.lapMs(KitStepLightState.Idle))
        assertTrue(at(800).fill > 0f)
    }
}
