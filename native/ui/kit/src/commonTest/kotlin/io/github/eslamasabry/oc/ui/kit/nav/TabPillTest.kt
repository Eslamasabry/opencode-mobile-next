package io.github.eslamasabry.oc.ui.kit.nav

import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The finger-linked pill as numbers: where its edges are for a place between two tabs, and where the strip scrolls. */
class TabPillTest {
    // English: a narrow tab, then a wide one, 8 px apart.
    private val lefts = floatArrayOf(16f, 84f, 212f)
    private val widths = floatArrayOf(60f, 120f, 40f)

    // Arabic: the first tab is the rightmost one.
    private val rtlLefts = floatArrayOf(300f, 172f, 124f)
    private val rtlWidths = floatArrayOf(60f, 120f, 40f)

    private fun near(want: Float, got: Float, what: String) = assertTrue(abs(want - got) < 0.01f, "$what: wanted $want, got $got")

    @Test
    fun `on a tab the pill is exactly that tab, plain or elastic`() {
        for (elastic in listOf(false, true)) {
            for (i in lefts.indices) {
                near(lefts[i], TabPill.left(lefts, widths, i.toFloat(), elastic), "left of tab $i")
                near(lefts[i] + widths[i], TabPill.right(lefts, widths, i.toFloat(), elastic), "right of tab $i")
            }
        }
    }

    @Test
    fun `half-way the plain pill is half-way with the width in between`() {
        val l = TabPill.left(lefts, widths, 0.5f, elastic = false)
        val r = TabPill.right(lefts, widths, 0.5f, elastic = false)
        near(50f, l, "left")
        near(140f, r, "right")
        near(90f, r - l, "the width between 60 and 120")
        near(33f, TabPill.left(lefts, widths, 0.25f, elastic = false), "a quarter of the way")
    }

    @Test
    fun `the elastic pill's leading edge runs ahead and its trailing edge stays behind, both ways`() {
        val plainL = TabPill.left(lefts, widths, 0.5f, elastic = false)
        val plainR = TabPill.right(lefts, widths, 0.5f, elastic = false)
        val l = TabPill.left(lefts, widths, 0.5f, elastic = true)
        val r = TabPill.right(lefts, widths, 0.5f, elastic = true)
        assertTrue(r > plainR, "the edge facing the next tab leads ($r > $plainR)")
        assertTrue(l < plainL, "the other edge trails ($l < $plainL)")
        // One curve mirrored: the pill a quarter of the way there is the pill a quarter of the way back.
        near(TabPill.lead(0.25f), 1f - TabPill.trail(0.75f), "lead mirrors trail")
        near(0f, TabPill.trail(0f), "trail starts on the tab")
        near(1f, TabPill.lead(1f), "lead ends on the tab")
        assertEquals(0f, TabPill.stretch(1f, elastic = true))
        assertEquals(0f, TabPill.stretch(0.5f, elastic = false))
        assertTrue(TabPill.stretch(0.5f, elastic = true) > 0.25f)
    }

    @Test
    fun `right to left the pill moves leftwards and the left edge is the one that leads`() {
        near(236f, TabPill.left(rtlLefts, rtlWidths, 0.5f, elastic = false), "left, plain")
        near(326f, TabPill.right(rtlLefts, rtlWidths, 0.5f, elastic = false), "right, plain")
        val l = TabPill.left(rtlLefts, rtlWidths, 0.5f, elastic = true)
        val r = TabPill.right(rtlLefts, rtlWidths, 0.5f, elastic = true)
        assertTrue(l < 236f, "the left edge faces the next tab and leads ($l)")
        assertTrue(r > 326f, "the right edge trails ($r)")
        near(172f, TabPill.left(rtlLefts, rtlWidths, 1f, elastic = true), "on the second tab")
        near(292f, TabPill.right(rtlLefts, rtlWidths, 1f, elastic = true), "on the second tab")
    }

    @Test
    fun `a place past either end is the end tab`() {
        for (elastic in listOf(false, true)) {
            near(16f, TabPill.left(lefts, widths, -0.4f, elastic), "before the first")
            near(76f, TabPill.right(lefts, widths, -0.4f, elastic), "before the first")
            near(212f, TabPill.left(lefts, widths, 7f, elastic), "after the last")
            near(252f, TabPill.right(lefts, widths, 7f, elastic), "after the last")
        }
        near(5f, TabPill.left(floatArrayOf(5f), floatArrayOf(9f), 0.6f, elastic = true), "one tab")
        assertEquals(0f, TabPill.left(FloatArray(0), FloatArray(0), 1f, elastic = true), "no tabs laid out yet")
    }

    @Test
    fun `the strip scrolls to keep the pill in the middle and stops at its ends`() {
        assertEquals(0f, TabPill.stripScroll(0f, 100f, viewport = 400f, content = 1000f, rtl = false), "near the start nothing scrolls")
        assertEquals(300f, TabPill.stripScroll(450f, 550f, viewport = 400f, content = 1000f, rtl = false), "the pill's middle in the window's middle")
        assertEquals(600f, TabPill.stripScroll(900f, 1000f, viewport = 400f, content = 1000f, rtl = false), "never past the end")
        assertEquals(0f, TabPill.stripScroll(100f, 200f, viewport = 400f, content = 300f, rtl = false), "a strip that fits never scrolls")
        // Right to left the scroll is counted from the right edge.
        assertEquals(0f, TabPill.stripScroll(900f, 1000f, viewport = 400f, content = 1000f, rtl = true))
        assertEquals(300f, TabPill.stripScroll(450f, 550f, viewport = 400f, content = 1000f, rtl = true))
        assertEquals(600f, TabPill.stripScroll(0f, 100f, viewport = 400f, content = 1000f, rtl = true))
    }

    @Test
    fun `the strip's scroll follows the fraction without a step`() {
        val l = floatArrayOf(0f, 300f, 600f, 900f)
        val w = floatArrayOf(280f, 280f, 280f, 280f)
        var last = -1f
        for (step in 0..30) {
            val p = step / 10f
            val s = TabPill.stripScroll(TabPill.left(l, w, p, true), TabPill.right(l, w, p, true), 400f, 1180f, rtl = false)
            assertTrue(s >= last, "it only moves forward as the pages do")
            assertTrue(last < 0f || s - last < 60f, "and never jumps ($last to $s at $p)")
            last = s
        }
    }

    @Test
    fun `a tap's travel carries the pill across every tab while the pages slide one page`() {
        val far = TabTravel(from = 0f, via = 4f, to = 5f)
        assertEquals(0f, far.at(4f), "the neighbour was brought next door: the pill has not moved yet")
        assertEquals(2.5f, far.at(4.5f), "half of the slide is half of the way")
        assertEquals(5f, far.at(5f))
        assertEquals(5f, far.at(5.2f), "a spring's overshoot does not push the pill past its tab")
        assertEquals(0f, far.at(0f), "before the pages jump the pill stays where it was")
        val back = TabTravel(from = 3.2f, via = 1f, to = 0f)
        assertEquals(3.2f, back.at(1f))
        assertEquals(1.6f, back.at(0.5f))
        assertEquals(7f, TabTravel(from = 2f, via = 7f, to = 7f).at(7f), "nothing to slide: the pill is there")
    }

    @Test
    fun `motion levels, the pill stretches only at Full and a tap slides unless motion is Off`() {
        assertTrue(TabRules.pillStretches(MotionLevel.Full))
        assertFalse(TabRules.pillStretches(MotionLevel.Calm))
        assertFalse(TabRules.pillStretches(MotionLevel.Off))
        assertTrue(TabRules.tapSlides(MotionLevel.Full))
        assertTrue(TabRules.tapSlides(MotionLevel.Calm))
        assertFalse(TabRules.tapSlides(MotionLevel.Off))
    }

    @Test
    fun `the pill's box keeps its inset, thins a little at full stretch and takes whole pixels when asked`() {
        val model = TabPillModel()
        model.slots = TabSlots(lefts, widths, height = 48f, content = 268f)
        model.measure(1f, elastic = true, inset = 6f, squash = 2f, corner = -1f, snap = false)
        assertEquals(84f, model.left)
        assertEquals(204f, model.right)
        assertEquals(6f, model.top)
        assertEquals(42f, model.bottom)
        assertEquals(18f, model.radius, "a full round end")
        model.measure(0.5f, elastic = true, inset = 6f, squash = 2f, corner = -1f, snap = false)
        near(8f, model.top, "2 px thinner at the longest stretch")
        near(40f, model.bottom, "on both sides")
        model.measure(0.5f, elastic = false, inset = 6f, squash = 2f, corner = 4f, snap = true)
        assertEquals(50f, model.left)
        assertEquals(6f, model.top, "the plain pill keeps its height")
        assertEquals(4f, model.radius, "a sharp style's corner")
        model.measure(0.37f, elastic = true, inset = 6f, squash = 2f, corner = -1f, snap = true)
        assertEquals(model.left, kotlin.math.floor(model.left), "whole pixels")
        assertEquals(model.right, kotlin.math.floor(model.right), "whole pixels")
    }
}
