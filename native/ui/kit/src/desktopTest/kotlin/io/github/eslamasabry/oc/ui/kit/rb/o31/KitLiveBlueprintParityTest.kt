package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.kit.live.KitLiveBlueprint
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_blueprint.dart at 8302de537. */
class KitLiveBlueprintParityTest {
    @Test fun constants() {
        assertEquals("blueprint", KitLiveBlueprint.id)
        assertEquals(2.0, KitLiveBlueprint.ceilingFactor)
        assertEquals(.64, KitLiveBlueprint.CALIBRATED)
        assertEquals(18, KitLiveBlueprint.COLUMNS)
        assertEquals(5, KitLiveBlueprint.MAJOR_EVERY)
        assertEquals(.15, KitLiveBlueprint.DRIFT)
        assertEquals(12, KitLiveBlueprint.framesPerSecond)
    }

    @Test fun theGridDriftsAMajorStepThenRepeats() {
        val m = 412.0 / 18
        assertEquals((7.3 * m * .15) % (m * 5), KitLiveBlueprint.driftAt(7.3, m), 1e-12)
        assertEquals(0.0, KitLiveBlueprint.driftAt(0.0, m))
        assertEquals(KitLiveBlueprint.driftAt(1.0, m), KitLiveBlueprint.driftAt(1.0 + 5 / .15, m), 1e-9)
    }

    @Test fun theScanLineSweepsDownEveryFiveAndAHalfSeconds() {
        assertEquals((7.3 * .18) % 1, KitLiveBlueprint.scanAt(7.3), 1e-12)
        assertEquals(0.0, KitLiveBlueprint.scanAt(0.0))
        assertEquals(KitLiveBlueprint.scanAt(1.0), KitLiveBlueprint.scanAt(1.0 + 1 / .18), 1e-9)
    }

    @Test fun visibleUnderItsCeilingDeterministicAndAlive() = checkLook(KitLiveBlueprint)
}
