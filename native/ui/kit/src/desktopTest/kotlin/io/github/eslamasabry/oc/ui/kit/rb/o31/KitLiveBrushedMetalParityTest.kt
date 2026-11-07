package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.kit.live.KitLiveBrushedMetal
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart at 8302de537. */
class KitLiveBrushedMetalParityTest {
    @Test fun constants() {
        assertEquals("brushedMetal", KitLiveBrushedMetal.id)
        assertEquals(1.0, KitLiveBrushedMetal.ceilingFactor)
        assertEquals(.55, KitLiveBrushedMetal.GRAIN)
        assertEquals(.45, KitLiveBrushedMetal.SHINE)
    }

    @Test fun theHighlightCrossesFromOffStartToOffEnd() {
        assertEquals(-.6, KitLiveBrushedMetal.shineAt(0.0), 1e-12)
        assertEquals(((7.3 * .05) % 1.6 - .3) * 2, KitLiveBrushedMetal.shineAt(7.3), 1e-12)
        assertEquals(KitLiveBrushedMetal.shineAt(1.0), KitLiveBrushedMetal.shineAt(1.0 + 1.6 / .05), 1e-9)
    }

    @Test fun visibleUnderItsCeilingDeterministicAndAlive() = checkLook(KitLiveBrushedMetal)
}
