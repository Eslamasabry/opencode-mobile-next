package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.kit.live.KitLiveBokeh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_bokeh.dart at 8302de537. */
class KitLiveBokehParityTest {
    @Test fun constants() {
        assertEquals("bokeh", KitLiveBokeh.id)
        assertEquals(1.0, KitLiveBokeh.ceilingFactor)
        assertEquals(20, KitLiveBokeh.COUNT)
        assertEquals(12, KitLiveBokeh.FIRST_GREEN)
    }

    /** The values Dart 3.9 prints for the same seed (math.Random(seed of 'bokeh')). */
    @Test fun theLightsAreDartsLights() {
        val l = KitLiveBokeh.lights()
        assertEquals(20, l.size)
        assertEquals(0.29964286686324304, l[0].x, 1e-15)
        assertEquals(0.1510474606187267, l[0].y, 1e-15)
        assertEquals(0.07215761165809118, l[0].r, 1e-15)
        assertEquals(0.006685845867428496, l[0].v, 1e-15)
        assertEquals(0.41762234794214526, l[19].x, 1e-15)
        assertEquals(0.5784430250472257, l[19].y, 1e-15)
        assertEquals(0.04410461817316606, l[19].r, 1e-15)
        assertEquals(0.005863381388230622, l[19].v, 1e-15)
        assertEquals(listOf(0, 1, 2), l.take(3).map { it.c })
        assertFalse(l[11].green)
        assertTrue(l[12].green)
    }

    @Test fun lightsRiseAndWrapBelowTheBottom() {
        val b = KitLiveBokeh.lights()[0]
        assertEquals(b.y, KitLiveBokeh.yAt(b, 0.0), 1e-12)
        assertTrue(KitLiveBokeh.yAt(b, 1.0) < b.y, "it rises")
        assertEquals(b.y, KitLiveBokeh.yAt(b, 1.4 / b.v), 1e-9)
        for (c in 0..400) {
            val y = KitLiveBokeh.yAt(b, c.toDouble())
            assertTrue(y >= -.2 && y < 1.2)
        }
    }

    @Test fun visibleUnderItsCeilingDeterministicAndAlive() = checkLook(KitLiveBokeh)
}
