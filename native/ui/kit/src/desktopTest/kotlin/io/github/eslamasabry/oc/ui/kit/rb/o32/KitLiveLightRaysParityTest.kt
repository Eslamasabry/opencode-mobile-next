package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.kit.live.KitLiveLightRays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_light_rays.dart at 8302de537. */
class KitLiveLightRaysParityTest {
    private val style = KitLiveLightRays()

    @Test fun idAndConstants() {
        LiveChecks.registered(style, "lightRays")
        assertEquals(1.0, style.ceilingFactor)
        assertEquals(5, KitLiveLightRays.BEAMS)
    }

    @Test fun theBeamsFallFromTheTopLeftAndFade() {
        val p = LiveRender.palette(true)
        val px = LiveRender.pixels(style, p)
        val w = 412
        val g = (p.ground.blue * 255).toInt()
        fun delta(x: Int, y: Int): Int = (px[y * w + x] and 0xFF) - g
        fun sum(x0: Int, y0: Int) = (x0 until x0 + 206).sumOf { x -> (y0 until y0 + 457).sumOf { y -> delta(x, y) } }
        // The beams fade along their length: more light in the top-left quarter than the bottom-right one.
        assertTrue(sum(0, 0) > sum(206, 457), "top-left ${sum(0, 0)} bottom-right ${sum(206, 457)}")
        // Nothing in the bottom-left corner, outside every beam.
        assertEquals(0, delta(2, 912))
    }

    @Test fun deterministicVisibleAndAlive() = LiveChecks.deterministicAndAlive(style)
}
