package io.github.eslamasabry.oc.ui.design.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The glass rim's strength (owner 2026-11-13: a pane whose edge disappears at a corner reads as a broken shape): its
 * lit arcs keep the peak, the corners at right angles to the light keep the floor, and nothing steps between.
 */
class RimLightTest {
    @Test
    fun `rim light - the lit arcs keep the peak and the weak corners keep the floor`() {
        assertEquals(1f, RimLight.lit(1f), 1e-6f, "facing the light")
        assertEquals(1f, RimLight.lit(-1f), 1e-6f, "facing away (the bottom-end arc)")
        assertEquals(RimLight.FLOOR, RimLight.lit(0f), 1e-6f, "along the light (the top-end and bottom-start corners)")
        assertTrue(RimLight.FLOOR >= .35f, "a floor a dark page shows")
    }

    @Test
    fun `rim light - it rises smoothly from the floor to the peak, with no step`() {
        var last = RimLight.lit(0f)
        for (i in 1..1000) {
            val now = RimLight.lit(i / 1000f)
            assertTrue(now >= last, "never falls as the edge turns to the light (at ${i / 1000f})")
            assertTrue(now - last < .01f, "no step (at ${i / 1000f}: ${now - last})")
            last = now
        }
    }

    @Test
    fun `rim light - a straight side stays well below the lit arcs, so the pane still reads lit from one side`() {
        // A straight side meets the 45 degree light at cos 45.
        val side = RimLight.lit(.70710678f)
        assertTrue(side < .7f, "a side at $side of the peak")
        assertTrue(side > RimLight.FLOOR, "above the floor")
    }
}
