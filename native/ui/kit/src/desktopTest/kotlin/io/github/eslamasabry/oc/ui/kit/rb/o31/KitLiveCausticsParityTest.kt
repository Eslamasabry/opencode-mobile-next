package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.kit.live.KitLiveCaustics
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_caustics.dart at 8302de537. */
class KitLiveCausticsParityTest {
    @Test fun constants() {
        assertEquals("caustics", KitLiveCaustics.id)
        assertEquals(1.0, KitLiveCaustics.ceilingFactor)
        assertEquals(8.0, KitLiveCaustics.cellFor(412.0), "a phone: the 8 px floor")
        assertEquals(1280.0 / 56, KitLiveCaustics.cellFor(1280.0))
        assertEquals(.25 * 7.3, KitLiveCaustics.waterTime(7.3))
    }

    /** The values Dart 3.9 prints for the same field (liveFbm, the three waves, smoothstep). */
    @Test fun theLightIsDartsLight() {
        assertEquals(0.0, KitLiveCaustics.lightAt(.1, .2, 1.825), 1e-12)
        assertEquals(0.08161057472244972, KitLiveCaustics.lightAt(.7, 1.9, 3.0), 1e-12)
    }

    @Test fun visibleUnderItsCeilingDeterministicAndAlive() = checkLook(KitLiveCaustics)
}
