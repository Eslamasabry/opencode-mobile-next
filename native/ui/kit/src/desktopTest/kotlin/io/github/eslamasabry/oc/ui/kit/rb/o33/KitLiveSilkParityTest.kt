package io.github.eslamasabry.oc.ui.kit.rb.o33

import io.github.eslamasabry.oc.ui.kit.live.KitLiveSilk as S
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_silk.dart at 8302de537. */
class KitLiveSilkParityTest {
    @Test fun idAndCeiling() {
        assertEquals("silk", S.id)
        assertEquals(1.0, S.ceilingFactor)
    }

    @Test fun constants() {
        assertEquals(.7, S.RUN_RATE)
        assertEquals(48, S.STEPS)
        assertEquals(5, S.RIBBONS)
        assertEquals(.15, S.TOP)
        assertEquals(.18, S.GAP)
        assertEquals(.06, S.AMP)
        assertEquals(.5, S.AMP_RUN)
        assertEquals(.18, S.SPEED)
        assertEquals(.03, S.SPEED_STEP)
        assertEquals(4.0, S.GLOW_SPREAD)
        assertEquals(3.0, S.WAVE)
        assertEquals(7.0, S.RIPPLE)
        assertEquals(.6, S.RIPPLE_SPEED)
        assertEquals(.3, S.RIPPLE_AMP)
        assertEquals(.8, S.FOLD)
        assertEquals(2.0, S.BAND)
        assertEquals(.95, S.ALPHA)
        assertEquals(listOf(0f, .5f, 1f), S.STOPS)
    }

    @Test fun edgesAreTheDartSines() {
        val w = 412.0
        val x = 100.0
        val base = 915 * .15
        val amp = 915 * .06
        val ph = 1.3
        val ripple = sin(x / w * 7 - ph * .6) * amp * .3
        assertEquals(base + sin(x / w * 3 + ph) * amp + ripple - amp, S.top(x, w, base, amp, ph), 1e-12)
        assertEquals(base + sin(x / w * 3 + ph + .8) * amp + ripple + amp, S.bottom(x, w, base, amp, ph), 1e-12)
    }

    @Test fun visibleUnderItsCeilingDeterministicAndAlive() = checkLook(S)
}
