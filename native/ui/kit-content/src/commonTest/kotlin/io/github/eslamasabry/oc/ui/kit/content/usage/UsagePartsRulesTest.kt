package io.github.eslamasabry.oc.ui.kit.content.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pure rules behind KitLimitBar (B-159) and KitTrendLine (B-160). */
class UsagePartsRulesTest {
    @Test
    fun `B-159 the bar takes the attention colour only when less is left than an even pace leaves`() {
        assertTrue(LimitBarRules.ahead(left = 0.20f, evenLeft = 0.25f))
        assertFalse(LimitBarRules.ahead(left = 0.25f, evenLeft = 0.25f))
        assertFalse(LimitBarRules.ahead(left = 0.247f, evenLeft = 0.25f)) // within the half-percent slack
        assertFalse(LimitBarRules.ahead(left = 0.9f, evenLeft = 0.25f))
    }

    @Test
    fun `B-160 the line scales to its largest value and never divides by zero`() {
        assertEquals(listOf(0f, 0.5f, 1f), TrendLineRules.normalise(listOf(0.0, 2.0, 4.0)))
        assertEquals(listOf(0f, 0f), TrendLineRules.normalise(listOf(0.0, 0.0)))
        assertEquals(listOf(0f, 1f, 0f), TrendLineRules.normalise(listOf(-3.0, 5.0, Double.NaN)))
        assertEquals(emptyList(), TrendLineRules.normalise(emptyList()))
    }
}
