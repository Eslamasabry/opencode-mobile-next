package io.github.eslamasabry.oc.ui.kit.content.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pure geometry and non-overlapping label rules for KitPaceBendChart (B-508). */
class KitPaceBendChartRulesTest {
    private fun evenPaceY(x: Float, input: PaceGuideLayoutInput): Float {
        val span = input.x1 - input.x0
        val frac = (if (input.rtl) (input.x1 - x) / span else (x - input.x0) / span).coerceIn(0f, 1f)
        return PaceBendRules.yForLeft(1f - frac, input.top, input.plotHeight)
    }

    private fun assertGuideRectValid(
        rect: PaceLabelRect,
        input: PaceGuideLayoutInput,
        name: String,
    ) {
        val base = input.top + input.plotHeight
        assertTrue(rect.left >= input.x0 && rect.right <= input.x1, "$name must stay within x bounds: $rect")
        assertTrue(rect.top >= input.top && rect.bottom <= base, "$name must stay between top and base: $rect")
        val evenY = evenPaceY(rect.centerX, input)
        assertTrue(
            evenY < rect.top || evenY > rect.bottom,
            "$name must not contain even-pace line y=$evenY at centerX=${rect.centerX}: $rect",
        )
    }

    private fun claudeWeeklyInput(scale: Float, rtl: Boolean = false): PaceGuideLayoutInput =
        PaceGuideLayoutInput(
            x0 = 5f,
            x1 = 323f,
            top = if (scale > 1f) 29f else 24f,
            plotHeight = 104f,
            rtl = rtl,
            elapsed = 3f / 7f,
            left = 0.80f,
            atReset = 8f / 15f,
            usedAbove = true,
            landingSize = PaceLabelSize(width = 120f * scale, height = 16f * scale),
            evenPaceSize = PaceLabelSize(width = 56f * scale, height = 16f * scale),
            pad = 8f,
            minGap = 6f,
        )

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

    @Test
    fun `B-508 landing label never overlaps the even-pace label or the base line`() {
        // Claude weekly case from the after pictures (3 days into 7, 80% left, 53% at reset) plus 5-hour case.
        val fiveHour =
            claudeWeeklyInput(scale = 1.3f).copy(
                plotHeight = 56f,
                elapsed = 0.60f,
                left = 0.70f,
                atReset = 0.50f,
                evenPaceSize = null,
            )
        val cases =
            listOf(
                claudeWeeklyInput(scale = 1.0f),
                claudeWeeklyInput(scale = 1.3f),
                claudeWeeklyInput(scale = 1.3f, rtl = true),
                fiveHour,
            )
        for (input in cases) {
            val placed = PaceBendRules.placeGuideLabels(input)
            val landing = assertNotNull(placed.landing, "landing label must be placed for $input")
            assertGuideRectValid(landing, input, "landing")
            placed.evenPace?.let { even ->
                assertGuideRectValid(even, input, "evenPace")
                assertFalse(
                    landing.overlaps(even, input.minGap),
                    "landing $landing and evenPace $even must not overlap",
                )
            }
        }
    }
}
