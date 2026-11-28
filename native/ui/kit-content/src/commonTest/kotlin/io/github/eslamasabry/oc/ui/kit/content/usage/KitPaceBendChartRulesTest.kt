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
            nowDotRadius = NOW_RING_DP,
        )

    /** The box the landing label must keep clear of the now dot: its ring, grown by the gap on every side. */
    private fun nowDotBox(input: PaceGuideLayoutInput): PaceLabelRect {
        val cx = PaceBendRules.xForFraction(input.elapsed, input.x0, input.x1, input.rtl)
        val cy = PaceBendRules.yForLeft(input.left, input.top, input.plotHeight)
        val half = NOW_RING_DP + input.minGap
        return PaceLabelRect(cx - half, cy - half, cx + half, cy + half)
    }

    private fun nowDotBox(input: PaceNoneLeftInput, minGap: Float): PaceLabelRect {
        val cx = PaceBendRules.xForFraction(input.elapsed, input.x0, input.x1, input.rtl)
        val cy = PaceBendRules.yForLeft(0f, input.top, input.plotHeight)
        val half = NOW_RING_DP + minGap
        return PaceLabelRect(cx - half, cy - half, cx + half, cy + half)
    }

    /** True when the closed line segment from `(ax, ay)` to `(bx, by)` intersects [rect]. */
    private fun segmentEntersRect(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        rect: PaceLabelRect,
    ): Boolean {
        val tx = axisRange(ax, bx, rect.left, rect.right)
        val ty = axisRange(ay, by, rect.top, rect.bottom)
        return tx != null && ty != null && maxOf(0f, tx.first, ty.first) <= minOf(1f, tx.second, ty.second)
    }

    private fun axisRange(start: Float, end: Float, low: Float, high: Float): Pair<Float, Float>? {
        val delta = end - start
        return if (delta == 0f) {
            if (start in low..high) 0f to 1f else null
        } else {
            val u1 = (low - start) / delta
            val u2 = (high - start) / delta
            minOf(u1, u2) to maxOf(u1, u2)
        }
    }

    private fun outCase(
        plotHeight: Float,
        elapsed: Float,
        labelWidth: Float,
        labelHeight: Float,
        rtl: Boolean = false,
    ): PaceNoneLeftInput {
        val top = 8f + labelHeight
        val canvasHeight = top + plotHeight + 10f + labelHeight + 4f + labelHeight + 6f
        return PaceNoneLeftInput(
            x0 = 21f,
            x1 = 339f,
            top = top,
            plotHeight = plotHeight,
            canvasHeight = canvasHeight,
            rtl = rtl,
            elapsed = elapsed,
            labelSize = PaceLabelSize(labelWidth, labelHeight),
            axisRowHeight = labelHeight,
            axisLabelGap = 6f,
            rowGap = 4f,
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
        // Claude weekly case from the after pictures (3 days into 7, 80% left, 53% at reset) plus 5-hour case.
        val fiveHour =
            claudeWeeklyInput(scale = 1.3f).copy(
                plotHeight = 56f,
                elapsed = 0.60f,
                left = 0.70f,
                atReset = 0.50f,
                evenPaceSize = null,
            )
        // The same short chart at the height a landing label gives it, the dot mid-way between start and reset,
        // where the label fits on neither side of it.
        val fiveHourMidDot =
            claudeWeeklyInput(scale = 1.3f).copy(
                plotHeight = 2f * (16f * 1.3f + NOW_RING_DP + 6f) + 6f,
                elapsed = 0.50f,
                left = 0.55f,
                atReset = 0.45f,
                evenPaceSize = null,
            )
        val cases =
            listOf(
                claudeWeeklyInput(scale = 1.0f),
                claudeWeeklyInput(scale = 1.3f),
                claudeWeeklyInput(scale = 1.3f, rtl = true),
                claudeWeeklyInput(scale = 1.0f).copy(left = 0.90f, atReset = 0.80f),
                claudeWeeklyInput(scale = 1.3f).copy(left = 0.90f, atReset = 0.80f),
                claudeWeeklyInput(scale = 1.0f, rtl = true).copy(left = 0.90f, atReset = 0.80f),
                claudeWeeklyInput(scale = 1.3f, rtl = true).copy(left = 0.90f, atReset = 0.80f),
                fiveHour,
                fiveHourMidDot,
            )
        for (input in cases) {
            val placed = PaceBendRules.placeGuideLabels(input)
            val landing = assertNotNull(placed.landing, "landing label must be placed for $input")
            assertGuideRectValid(landing, input, "landing")
            val dot = nowDotBox(input)
            assertFalse(landing.overlaps(dot), "landing $landing must stay clear of the now dot $dot for $input")
            val nowX = PaceBendRules.xForFraction(input.elapsed, input.x0, input.x1, input.rtl)
            val nowY = PaceBendRules.yForLeft(input.left, input.top, input.plotHeight)
            val resetX = PaceBendRules.xForFraction(1f, input.x0, input.x1, input.rtl)
            val landY = PaceBendRules.yForLeft(checkNotNull(input.atReset), input.top, input.plotHeight)
            assertFalse(
                segmentEntersRect(nowX, nowY, resetX, landY, landing),
                "forecast segment must not enter landing $landing for $input",
            )
            placed.evenPace?.let { even ->
                assertGuideRectValid(even, input, "evenPace")
                assertFalse(
                    landing.overlaps(even, input.minGap),
                    "landing $landing and evenPace $even must not overlap",
                )
            }
        }
    }

    @Test
    fun `B-508 none-left label in the out state is clear of the even-pace line and the now dot`() {
        val cases =
            listOf(
                outCase(plotHeight = 56f, elapsed = 0.40f, labelWidth = 57f, labelHeight = 16f),
                outCase(plotHeight = 56f, elapsed = 0.40f, labelWidth = 72f, labelHeight = 20.8f),
                outCase(plotHeight = 56f, elapsed = 0.40f, labelWidth = 72f, labelHeight = 20.8f, rtl = true),
                outCase(plotHeight = 104f, elapsed = 0.50f, labelWidth = 57f, labelHeight = 16f),
                outCase(plotHeight = 104f, elapsed = 0.50f, labelWidth = 72f, labelHeight = 20.8f),
            )
        for (input in cases) {
            val rect = assertNotNull(PaceBendRules.placeNoneLeftLabel(input), "none-left label missing for $input")
            val base = input.top + input.plotHeight
            assertTrue(rect.left >= input.x0 && rect.right <= input.x1, "label $rect outside x bounds")
            assertTrue(rect.top >= 0f && rect.bottom <= input.canvasHeight, "label $rect outside canvas")
            val xStart = PaceBendRules.xForFraction(0f, input.x0, input.x1, input.rtl)
            val xReset = PaceBendRules.xForFraction(1f, input.x0, input.x1, input.rtl)
            assertFalse(
                segmentEntersRect(xStart, input.top, xReset, base, rect),
                "even-pace segment must not enter none-left label $rect for $input",
            )
            val dot = nowDotBox(input, minGap = 6f)
            assertFalse(rect.overlaps(dot), "none-left label $rect must stay clear of now dot $dot for $input")
        }
    }
}
