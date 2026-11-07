package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.kit.live.KitLiveGlowSpill
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_glow_spill.dart at 8302de537. */
class KitLiveGlowSpillParityTest {
    private val style = KitLiveGlowSpill()

    @Test fun idAndConstants() {
        LiveChecks.registered(style, "glowSpill")
        assertEquals(1.0, style.ceilingFactor)
        assertEquals(11, KitLiveGlowSpill.SEED)
        assertEquals(90.0, KitLiveGlowSpill.AREA)
    }

    @Test fun onePointOfGrainPerNinetySquareDpInsideThePage() {
        val g = KitLiveGlowSpill.Grain(412.0, 915.0)
        val n = (412.0 * 915.0 / 90).roundToInt()
        assertEquals(n * 2, g.points.size)
        for (i in 0 until n) assertTrue(g.points[i * 2] in 0f..412f && g.points[i * 2 + 1] in 0f..915f)
    }

    @Test fun theGlowRisesFromBelowTheBottomEdge() {
        val p = LiveRender.palette(true)
        val px = LiveRender.pixels(style, p)
        val w = 412
        // Green, the accent's strongest channel, summed along a row.
        fun row(y: Int) = (0 until w).sumOf { (px[y * w + it] shr 8) and 0xFF }
        assertTrue(row(900) > row(100), "brighter at the bottom than near the top")
    }

    @Test fun deterministicVisibleAndAlive() = LiveChecks.deterministicAndAlive(style)
}
