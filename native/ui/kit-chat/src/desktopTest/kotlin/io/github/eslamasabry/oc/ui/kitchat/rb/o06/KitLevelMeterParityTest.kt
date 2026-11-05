package io.github.eslamasabry.oc.ui.kitchat.rb.o06

import io.github.eslamasabry.oc.ui.kitchat.composer.KitLevelMeterRules
import kotlin.test.*

class KitLevelMeterParityTest {
    @Test fun boundaries_and_silence() {
        for (i in 0 until KitLevelMeterRules.BARS) {
            val threshold = (i + 1) / 12f
            assertTrue(KitLevelMeterRules.isLit(i, threshold))
            assertFalse(KitLevelMeterRules.isLit(i, java.lang.Math.nextDown(threshold)))
            assertFalse(KitLevelMeterRules.isLit(i, Float.NaN))
            assertFalse(KitLevelMeterRules.isLit(i, Float.NEGATIVE_INFINITY))
            assertTrue(KitLevelMeterRules.isLit(i, Float.POSITIVE_INFINITY))
        }
        assertFalse(KitLevelMeterRules.isLit(KitLevelMeterRules.BARS, 1f))
    }
}
