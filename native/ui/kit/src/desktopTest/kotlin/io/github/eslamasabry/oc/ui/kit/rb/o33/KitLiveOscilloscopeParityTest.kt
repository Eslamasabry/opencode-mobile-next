package io.github.eslamasabry.oc.ui.kit.rb.o33

import io.github.eslamasabry.oc.ui.kit.live.KitLiveOscilloscope as O
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart at 8302de537. */
class KitLiveOscilloscopeParityTest {
    @Test fun idAndCeiling() {
        assertEquals("oscilloscope", O.id)
        assertEquals(2.0, O.ceilingFactor)
        assertEquals(12, O.framesPerSecond)
    }

    @Test fun constants() {
        assertEquals(8, O.COLUMNS)
        assertEquals(.15, O.GRID_ALPHA)
        assertEquals(.55, O.MID)
        assertEquals(.35, O.AMP_IDLE)
        assertEquals(.9, O.AMP_RUN)
        assertEquals(3.0, O.STEP)
        assertEquals(6f, O.GLOW_WIDTH)
        assertEquals(.2, O.GLOW_ALPHA)
        assertEquals(1.3f, O.LINE_WIDTH)
        assertEquals(.65, O.LINE_ALPHA)
    }

    @Test fun traceIsTheDartSum() {
        // u .25 at t 7.3, idle, on the phone: mid 915 * .55, amp 412 / 8 * .35.
        assertEquals(504.1446500519817, O.traceY(.25, 7.3, 0.0, 915 * .55, 412.0 / 8 * .35), 1e-9)
    }

    @Test fun visibleUnderItsCeilingDeterministicAndAlive() = checkLook(O)
}
