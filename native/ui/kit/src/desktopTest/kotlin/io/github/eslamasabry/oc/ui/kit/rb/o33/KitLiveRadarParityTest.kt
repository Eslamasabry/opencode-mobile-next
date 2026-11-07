package io.github.eslamasabry.oc.ui.kit.rb.o33

import io.github.eslamasabry.oc.ui.kit.live.KitLiveRadar as R
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_radar.dart at 8302de537. */
class KitLiveRadarParityTest {
    @Test fun idAndCeiling() {
        assertEquals("radar", R.id)
        assertEquals(2.0, R.ceilingFactor)
        assertEquals(.72, R.CALIBRATED)
    }

    @Test fun constants() {
        assertEquals(.62f, R.CENTRE_Y)
        assertEquals(.95f, R.RADIUS)
        assertEquals(4, R.RINGS)
        assertEquals(.3, R.RING_ALPHA)
        assertEquals(PI * 2, R.TAU)
        assertEquals(2.0, R.RUN_RATE)
        assertEquals(.35, R.TURN)
        assertEquals(.5, R.BEAM_ALPHA)
        assertEquals(listOf(0f, .14f, .143f, 1f), R.BEAM_STOPS)
        assertEquals(.9, R.BEAM_LEAD)
        assertEquals(14, R.BLIPS)
        assertEquals(.9, R.BLIP_REACH)
        assertEquals(.08, R.BLIP_INSET)
        assertEquals(6, R.IDLE_BLIPS)
        assertEquals(.02, R.MIN_SHOWN)
        assertEquals(2.6, R.FADE)
        assertEquals(6f, R.HALO_R)
        assertEquals(.15, R.HALO_ALPHA)
        assertEquals(2f, R.DOT_R)
        assertEquals(.55, R.DOT_ALPHA)
    }

    @Test fun contactsAreDartsSeededOnes() {
        // The radar golden shows contact 4 lit at (113.5, 668.5) on the phone; its angle and distance come from here.
        val r = 412.0 * .95f
        val b = R.blipsFor(r)
        assertEquals(0.6364420707251958 * PI * 2, b[0], 1e-12)
        assertEquals(0.8389828018322434 * r * .9 + r * .08, b[1], 1e-9)
    }

    @Test fun aContactFadesOverTheTurnBehindTheBeam() {
        assertEquals(1.0, R.lit(1.0, 1.0, 1.0), 1e-12)
        assertEquals(.5, R.lit(2.3, 1.0, 1.0), 1e-12)
        assertEquals(0.0, R.lit(4.0, 1.0, 1.0))
        // Ahead of the beam it is dark until the beam comes round (the angle wraps).
        assertEquals(0.0, R.lit(1.0, 1.5, 1.0))
        assertEquals(.25, R.lit(2.3, 1.0, .5), 1e-12)
    }

    @Test fun visibleUnderItsCeilingDeterministicAndAlive() = checkLook(R)
}
