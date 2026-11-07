package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.kit.live.KitLiveHalftone
import io.github.eslamasabry.oc.ui.kit.live.smooth
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_halftone.dart at 8302de537. */
class KitLiveHalftoneParityTest {
    private val style = KitLiveHalftone()

    @Test fun idAndConstants() {
        LiveChecks.registered(style, "halftone")
        assertEquals(1.0, style.ceilingFactor)
        assertEquals(12, style.framesPerSecond)
    }

    @Test fun theRunningWarmthGrowsDownThePage() {
        assertEquals(0.0, smooth(.4, 1.0, .3))
        assertEquals(1.0, smooth(.4, 1.0, 1.0))
        assertEquals(.5, smooth(.4, 1.0, .7), 1e-12)
    }

    @Test fun idleItDoesNotWarm() {
        // Idle (running 0) the dots are ink only: no pixel leans to the accent's green.
        val p = LiveRender.palette(true)
        val px = LiveRender.pixels(style, p)
        val g = (p.ground.green * 255).toInt()
        for (c in px) {
            val r = (c shr 16) and 0xFF
            val gr = (c shr 8) and 0xFF
            kotlin.test.assertTrue(gr - g <= (r - (p.ground.red * 255).toInt()) + 2)
        }
    }

    @Test fun deterministicVisibleAndAlive() = LiveChecks.deterministicAndAlive(style)
}
