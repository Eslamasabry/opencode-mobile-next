package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.ui.geometry.Rect
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.motion.allowed
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.state.ReceiptRules
import io.github.eslamasabry.oc.ui.kit.status.KitMarkSize
import io.github.eslamasabry.oc.ui.kit.status.TaskMarkRules
import io.github.eslamasabry.oc.ui.kit.teaching.TipRules
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The edge light's pure rules (LAMP-PLAN §2, LAMP-CRAFT §1, §2, §10). */
class KitLampRulesTest {
    // The digits come from the phone's locale (Resources.getSystem), which a plain host or JVM test has none of: pin them.
    @BeforeTest fun latin() { Numbers.pinned = NumberSymbols.Latin }

    @AfterTest fun unpin() { Numbers.pinned = null }

    private fun m(key: String, kind: KitLampMarkKind, progress: Float? = null) = KitLampMark(key, kind, progress)

    @Test
    fun `B-LAMP-K1-001 face_f2 shows at most three marks, needs you first, then failed, then runs by progress`() {
        val marks =
            listOf(
                m("w1", KitLampMarkKind.Working, 0.2f),
                m("f1", KitLampMarkKind.Failed),
                m("w2", KitLampMarkKind.Working, 0.9f),
                m("n1", KitLampMarkKind.NeedsYou),
                m("n2", KitLampMarkKind.NeedsYou),
            )
        assertEquals(listOf("n1", "n2", "f1"), KitLampRules.shown(marks).map { it.key })
        val runs = listOf(m("a", KitLampMarkKind.Working, null), m("b", KitLampMarkKind.Working, 0.5f), m("c", KitLampMarkKind.Finished), m("d", KitLampMarkKind.Working, 0.7f))
        assertEquals(listOf("c", "d", "b"), KitLampRules.shown(runs).map { it.key }, "a just-finished run leads, an unknown share comes last")
    }

    @Test
    fun `B-LAMP-K1-002 the face is quiet 12 x 40, open 16 x 48, else 8 + 15 per row between 40 and 68`() {
        val quiet = KitLampFace()
        assertTrue(quiet.quiet)
        assertEquals(40, KitLampRules.faceHeightDp(quiet))
        assertEquals(12, KitLampRules.faceWidthDp(quiet))
        val open = KitLampFace(2, listOf(m("n", KitLampMarkKind.NeedsYou)), open = true)
        assertEquals(48, KitLampRules.faceHeightDp(open))
        assertEquals(16, KitLampRules.faceWidthDp(open))
        assertEquals(40, KitLampRules.faceHeightDp(KitLampFace(0, listOf(m("w", KitLampMarkKind.Working)))), "one row stays at the 40 dp least")
        assertEquals(53, KitLampRules.faceHeightDp(KitLampFace(1, listOf(m("n", KitLampMarkKind.NeedsYou), m("w", KitLampMarkKind.Working)))))
        val full = KitLampFace(5, List(6) { m("n$it", KitLampMarkKind.NeedsYou) })
        assertEquals(68, KitLampRules.faceHeightDp(full))
        assertEquals(KitLampDims.MAX_H, 68)
        assertEquals(68, KitLampRules.touchHeightDp(68), "a tall mark is its own touch box")
        assertEquals(48, KitLampRules.touchHeightDp(40), "a short mark gets the 48 dp box, centred on it")
    }

    @Test
    fun `B-LAMP-K1-003 the number counts needs and failed, writes 9+ above nine, and is amber only when someone needs you`() {
        assertEquals(Numbers.format(3), KitLampRules.numberText(3))
        assertEquals(Numbers.format(9) + "+", KitLampRules.numberText(12))
        val failedOnly = KitLampFace(1, listOf(m("f", KitLampMarkKind.Failed)))
        assertEquals(TextTone.Primary, KitLampRules.numberTone(failedOnly))
        assertEquals(TextTone.Attention, KitLampRules.numberTone(failedOnly.copy(marks = failedOnly.marks + m("n", KitLampMarkKind.NeedsYou))))
        assertFalse(KitLampRules.numberShown(KitLampFace(0, listOf(m("w", KitLampMarkKind.Working)))))
        assertFalse(KitLampRules.numberShown(failedOnly.copy(open = true)), "the open tab shows its × only")
    }

    @Test
    fun `B-LAMP-K1-004 the face widens to 20 dp only for 9+ at the frame text cap`() {
        val many = KitLampFace(12, listOf(m("n", KitLampMarkKind.NeedsYou)))
        assertEquals(16, KitLampRules.faceWidthDp(many), "never wider than the 16 dp page gutter")
        assertTrue(KitLampRules.numberCompact(many), "9+ is written small at any text size")
        assertTrue(KitLampRules.numberCompact(many))
        assertEquals(16, KitLampRules.faceWidthDp(many.copy(count = 3)))
    }

    @Test
    fun `B-LAMP-K1-005 rtl_startEdge the end side is the right edge in English and the left in Arabic`() {
        assertFalse(KitLampRules.onLeft(KitLampSide.End, rtl = false))
        assertTrue(KitLampRules.onLeft(KitLampSide.End, rtl = true))
        assertTrue(KitLampRules.onLeft(KitLampSide.Start, rtl = false))
        assertFalse(KitLampRules.onLeft(KitLampSide.Start, rtl = true))
    }

    @Test
    fun `B-LAMP-K1-006 the mini mark is 11 dp and a working arc never vanishes`() {
        assertEquals(11, KitMarkSize.Mini.dp)
        assertEquals(KitLampDims.ROW, KitMarkSize.Mini.dp + 4)
        assertNull(TaskMarkRules.miniArc(null), "no task list: the still dashed ring")
        assertEquals(TaskMarkRules.MINI_LEAST_ARC, TaskMarkRules.miniArc(0f))
        assertEquals(0.5f, TaskMarkRules.miniArc(0.5f))
        assertEquals(1f, TaskMarkRules.miniArc(3f))
    }

    @Test
    fun `B-LAMP-K1-007 the light's motion tokens and its haptic`() {
        assertEquals(250, MotionTokens.LIGHT_PULSE)
        assertEquals(10, MotionTokens.LIGHT_HALO_DP)
        assertEquals(3000, MotionTokens.HELD_ANSWER)
        assertTrue(KitHaptic.Attention.allowed())
        assertFalse(KitHaptic.Attention.allowed(enabled = false), "the global haptics switch wins")
        assertTrue(KitHaptic.Attention.allowed(reduced = true), "reduced motion keeps the haptic")
    }

    @Test
    fun `B-LAMP-K1-008 the grow runs between the anchor and the pane and shrinks toward the anchor`() {
        val anchor = Rect(280f, 300f, 296f, 348f)
        val pane = Rect(0f, 0f, 300f, 400f)
        assertEquals(anchor, GrowFromAnchorRules.bounds(anchor, pane, 0f))
        assertEquals(pane, GrowFromAnchorRules.bounds(anchor, pane, 1f))
        assertEquals(pane, GrowFromAnchorRules.bounds(anchor, pane, 1.02f), "a spring's overshoot never grows past the pane")
        val half = GrowFromAnchorRules.bounds(anchor, pane, 0.5f)
        assertEquals(140f, half.left)
        assertEquals(298f, half.right)
        val o = GrowFromAnchorRules.origin(anchor, pane)
        assertEquals(288f / 300f, o.pivotFractionX)
        assertEquals(324f / 400f, o.pivotFractionY)
    }

    @Test
    fun `B-LAMP-K1-009 a held receipt says its whole seconds, rounded up`() {
        assertEquals(3, ReceiptRules.holdSeconds(3_000))
        assertEquals(4, ReceiptRules.holdSeconds(3_001))
        assertEquals(10, ReceiptRules.holdSeconds(10_000))
    }

    @Test
    fun `B-LAMP-K1-010 a tip beside an edge part stands on the page side of it, above its mark, inside the window`() {
        // A 16 x 48 gutter box on the right edge of a 412 x 860 window; a 200 x 60 tip; 8 px gap.
        val (x, y) = TipRules.besideEdge(396, 500, 412, 548, 412, 860, 200, 60, 8)
        assertEquals(396 - 200, x, "its end edge meets the gutter's inner edge")
        assertEquals(500 - 8 - 60, y, "it stands above the light, never beside it")
        // On the left edge (Arabic): to its right.
        val (lx, _) = TipRules.besideEdge(0, 500, 16, 548, 412, 860, 200, 60, 8)
        assertEquals(16, lx)
        // No room above: below the light, inside the window.
        val (_, by) = TipRules.besideEdge(396, 20, 412, 68, 412, 860, 200, 60, 8)
        assertEquals(76, by)
        // Near the bottom: above it and kept in the window.
        val (_, ny) = TipRules.besideEdge(396, 800, 412, 848, 412, 860, 200, 60, 8)
        assertEquals(800 - 8 - 60, ny)
    }

    @Test
    fun `B-LAMP-014 the light's tip never covers a footer button flush at the content edge`() {
        val windowW = 412
        val gutter = 16
        val tipW = 280
        val tipH = 60
        // Footer buttons 48 dp square, flush at the content's edge, level with the light (a reply's copy and more).
        for (lightTop in listOf(100, 300, 500, 700, 800)) {
            val (x, y) = TipRules.besideEdge(windowW - gutter, lightTop, windowW, lightTop + 48, windowW, 860, tipW, tipH, 8)
            val tip = intArrayOf(x, y, x + tipW, y + tipH)
            val edge = windowW - gutter
            val copy = intArrayOf(edge - 48, lightTop, edge, lightTop + 48)
            val more = intArrayOf(edge - 96, lightTop, edge - 48, lightTop + 48)
            for (button in listOf(copy, more)) {
                val overlaps = tip[0] < button[2] && button[0] < tip[2] && tip[1] < button[3] && button[1] < tip[3]
                assertTrue(!overlaps, "light at $lightTop: the tip ${tip.toList()} covers ${button.toList()}")
            }
        }
    }

    @Test
    fun `B-LAMP-015 a tip that yields waits while any other tip is on screen, and never over a modal`() {
        fun may(modal: Boolean = false, yields: Boolean = true, others: Int = 0, visible: Boolean = true) =
            TipRules.mayShow(visible, modal, yields, others)
        assertTrue(may())
        assertTrue(!may(others = 1), "another tip is up: wait")
        assertTrue(may(yields = false, others = 2), "a plain tip is as before")
        assertTrue(!may(modal = true))
        assertTrue(!may(visible = false))
    }

    @Test
    fun `B-LAMP-015 each page root counts its own tips, two roots never see each other, and none means no other tip`() {
        val a = io.github.eslamasabry.oc.ui.kit.teaching.KitTipPresence()
        val b = io.github.eslamasabry.oc.ui.kit.teaching.KitTipPresence()
        a.enter()
        assertEquals(1, a.others)
        assertEquals(0, b.others, "another root's tip is not this root's")
        a.leave()
        a.leave()
        assertEquals(0, a.others, "never below zero")
    }
}
