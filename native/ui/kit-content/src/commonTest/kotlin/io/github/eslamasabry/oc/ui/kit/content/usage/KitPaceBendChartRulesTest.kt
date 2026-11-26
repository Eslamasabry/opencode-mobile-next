package io.github.eslamasabry.oc.ui.kit.content.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pure geometry and non-overlapping label rules for KitPaceBendChart (B-508). */
class KitPaceBendChartRulesTest {
    @Test
    fun `B-508 x coordinate mirrors cleanly in RTL and y maps full to top and empty to base`() {
        assertEquals(10f, PaceBendRules.xForFraction(0f, 10f, 110f, rtl = false))
        assertEquals(60f, PaceBendRules.xForFraction(0.5f, 10f, 110f, rtl = false))
        assertEquals(110f, PaceBendRules.xForFraction(1f, 10f, 110f, rtl = false))
        assertEquals(110f, PaceBendRules.xForFraction(0f, 10f, 110f, rtl = true))
        assertEquals(10f, PaceBendRules.xForFraction(1f, 10f, 110f, rtl = true))

        assertEquals(20f, PaceBendRules.yForLeft(1f, top = 20f, plotHeight = 100f))
        assertEquals(70f, PaceBendRules.yForLeft(0.5f, top = 20f, plotHeight = 100f))
        assertEquals(120f, PaceBendRules.yForLeft(0f, top = 20f, plotHeight = 100f))
    }

    @Test
    fun `B-508 priority label Today wins over crowded neighbours and stays inside bounds`() {
        val candidates =
            listOf(
                PaceLabelCandidate(anchorX = 12f, width = 30f, priority = false, payload = "Fri"),
                PaceLabelCandidate(anchorX = 48f, width = 40f, priority = true, payload = "Today"),
                PaceLabelCandidate(anchorX = 64f, width = 30f, priority = false, payload = "Sun"),
                PaceLabelCandidate(anchorX = 150f, width = 30f, priority = false, payload = "Mon"),
            )
        val placed = PaceBendRules.placeLabels(candidates, x0 = 5f, x1 = 160f, minGap = 6f)
        val labels = placed.map { it.payload }
        assertTrue("Today" in labels, "priority label must always be placed")
        assertTrue("Sun" !in labels, "overlapping neighbour must be dropped")
        assertTrue("Mon" in labels, "non-overlapping label must be kept")
        for (item in placed) {
            assertTrue(item.left >= 5f && item.right <= 160f, "placed label must stay inside bounds: $item")
        }
    }
}
