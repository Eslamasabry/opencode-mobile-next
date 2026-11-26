package io.github.eslamasabry.oc.ui.kit.content.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pure geometry and non-overlapping label rules for KitPaceBendChart (B-508). */
class KitPaceBendChartRulesTest {
    private fun evenPaceY(x: Float, x0: Float, x1: Float, top: Float, plotHeight: Float, rtl: Boolean): Float {
        val frac = (if (rtl) (x1 - x) / (x1 - x0) else (x - x0) / (x1 - x0)).coerceIn(0f, 1f)
        return PaceBendRules.yForLeft(1f - frac, top, plotHeight)
    }

    private fun assertGuideRectValid(
        rect: PaceLabelRect,
        input: PaceGuideLayoutInput,
        name: String,
    ) {
        val base = input.top + input.plotHeight
        assertTrue(rect.left >= input.x0 && rect.right <= input.x1, "$name must stay within x bounds: $rect")
        assertTrue(rect.top >= input.top && rect.bottom <= base, "$name must stay between top and base: $rect")
        val evenY = evenPaceY(rect.centerX, input.x0, input.x1, input.top, input.plotHeight, input.rtl)
        assertTrue(
            evenY < rect.top || evenY > rect.bottom,
            "$name must not contain even-pace line y=$evenY at centerX=${rect.centerX}: $rect",
        )
    }

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
        // Claude weekly case from the after pictures: 3 days into 7, 80% left, 53% at reset.
        val cases =
            listOf(
                // 360 dp at 1.0 text scale (LTR)
                PaceGuideLayoutInput(
                    x0 = 5f,
                    x1 = 323f,
                    top = 24f,
                    plotHeight = 104f,
                    rtl = false,
                    elapsed = 3f / 7f,
                    left = 0.80f,
                    atReset = 8f / 15f,
                    usedAbove = true,
                    landingSize = PaceLabelSize(width = 120f, height = 16f),
                    evenPaceSize = PaceLabelSize(width = 56f, height = 16f),
                    pad = 8f,
                    minGap = 6f,
                ),
                // 360 dp at 1.3 text scale (LTR)
                PaceGuideLayoutInput(
                    x0 = 5f,
                    x1 = 323f,
                    top = 29f,
                    plotHeight = 104f,
                    rtl = false,
                    elapsed = 3f / 7f,
                    left = 0.80f,
                    atReset = 8f / 15f,
                    usedAbove = true,
                    landingSize = PaceLabelSize(width = 156f, height = 21f),
                    evenPaceSize = PaceLabelSize(width = 73f, height = 21f),
                    pad = 8f,
                    minGap = 6f,
                ),
                // 360 dp at 1.3 text scale (RTL)
                PaceGuideLayoutInput(
                    x0 = 5f,
                    x1 = 323f,
                    top = 29f,
                    plotHeight = 104f,
                    rtl = true,
                    elapsed = 3f / 7f,
                    left = 0.80f,
                    atReset = 8f / 15f,
                    usedAbove = true,
                    landingSize = PaceLabelSize(width = 156f, height = 21f),
                    evenPaceSize = PaceLabelSize(width = 73f, height = 21f),
                    pad = 8f,
                    minGap = 6f,
                ),
                // Claude five-hour case: 3h into 5h, 70% left, 50% at reset (short chart, no even-pace label)
                PaceGuideLayoutInput(
                    x0 = 5f,
                    x1 = 323f,
                    top = 29f,
                    plotHeight = 56f,
                    rtl = false,
                    elapsed = 0.60f,
                    left = 0.70f,
                    atReset = 0.50f,
                    usedAbove = true,
                    landingSize = PaceLabelSize(width = 156f, height = 21f),
                    evenPaceSize = null,
                    pad = 8f,
                    minGap = 6f,
                ),
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
