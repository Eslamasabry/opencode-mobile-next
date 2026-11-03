package io.github.eslamasabry.oc.ui.kitchat.composer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KitLevelMeterRulesTest {
    @Test
    fun `B-MEDIA-003 bar i lights when the level reaches (i + 1) over 12`() {
        assertFalse(KitLevelMeterRules.isLit(0, 0f))
        assertTrue(KitLevelMeterRules.isLit(0, 1f / 12f))
        assertFalse(KitLevelMeterRules.isLit(1, 1f / 12f))
        assertEquals(4, (0 until KitLevelMeterRules.BARS).count { KitLevelMeterRules.isLit(it, 0.4f) })
        assertEquals(KitLevelMeterRules.BARS, (0 until KitLevelMeterRules.BARS).count { KitLevelMeterRules.isLit(it, 1f) })
    }
}
