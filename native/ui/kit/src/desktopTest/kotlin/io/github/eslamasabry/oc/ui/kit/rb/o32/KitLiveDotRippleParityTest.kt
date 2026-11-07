package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.kit.live.KitLiveDotRipple
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart at 8302de537. */
class KitLiveDotRippleParityTest {
    private val style = KitLiveDotRipple()

    @Test fun idAndConstants() {
        LiveChecks.registered(style, "dotRipple")
        assertEquals(1.0, style.ceilingFactor)
        assertEquals(12, style.framesPerSecond)
    }

    @Test fun aRingEverySevenSecondsAndFasterWhileAReplyRuns() {
        assertEquals(7.0, KitLiveDotRipple.periodFor(0.0))
        assertEquals(7.0, KitLiveDotRipple.periodFor(.5))
        assertEquals(2.2, KitLiveDotRipple.periodFor(.51))
        assertEquals(2.2, KitLiveDotRipple.periodFor(1.0))
    }

    @Test fun deterministicVisibleAndAlive() = LiveChecks.deterministicAndAlive(style)
}
