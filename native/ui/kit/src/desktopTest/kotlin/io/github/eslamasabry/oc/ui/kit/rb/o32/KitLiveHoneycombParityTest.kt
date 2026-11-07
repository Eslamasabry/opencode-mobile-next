package io.github.eslamasabry.oc.ui.kit.rb.o32

import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.live.KitLiveHoneycomb
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.liveClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_honeycomb.dart at 8302de537. */
class KitLiveHoneycombParityTest {
    private val style = KitLiveHoneycomb()

    @Test fun idAndConstants() {
        LiveChecks.registered(style, "honeycomb")
        assertEquals(2.0, style.ceilingFactor)
        assertEquals(12, style.framesPerSecond)
    }

    @Test fun theLatticeCoversThePageWithCellsOfASixteenthOfItsWidth() {
        LiveRender.ensureGraphics()
        val r = 412 / 16.0
        val geo = KitLiveHoneycomb.Geo.of(Size(412f, 915f), r)
        // Rows half a hexagon's height apart, from one above the page to past its bottom; odd rows shifted 1.5 r.
        assertEquals(-r * 3 + r * 1.5, geo.xs[0], 1e-9) // row -1 is odd
        assertEquals(-r * kotlin.math.sqrt(3.0) * .5, geo.ys[0], 1e-9)
        assertTrue(geo.ys.max() >= 915.0)
        assertTrue(geo.xs.max() >= 412.0)
        val oddRow = geo.ys.indices.first { geo.ys[it] == 0.0 }
        assertEquals(-r * 3, geo.xs[oddRow], 1e-9) // row 0 is even
    }

    @Test fun theBandSlidesOnTheLooksOwnClock() {
        val size = Size(412f, 915f)
        // At the golden's clock (7.3 s, idle): (7.3 * .07 % 1.4 - .2) * (w + h).
        assertEquals((7.3 * .07 % 1.4 - .2) * 1327, KitLiveHoneycomb.bandAt(7.3, size), 1e-9)
        // It wraps every 20 s of its clock.
        assertEquals(KitLiveHoneycomb.bandAt(1.0, size), KitLiveHoneycomb.bandAt(21.0, size), 1e-9)
        // While a reply runs its clock runs 2.7 times as fast, without a jump.
        val memory = KitLiveMemory()
        val p = LiveRender.palette(true)
        assertEquals(7.3, liveClock(LiveRender.frame(p, t = 7.3, memory = memory), "honeycomb", 1.0), 1e-12)
        assertEquals(7.3 + .1 * 2.7, liveClock(LiveRender.frame(p, t = 7.4, memory = memory), "honeycomb", 2.7), 1e-9)
    }

    @Test fun deterministicVisibleAndAlive() = LiveChecks.deterministicAndAlive(style)

    @Test fun underItsCeiling() = LiveChecks.underCeiling(style)
}
