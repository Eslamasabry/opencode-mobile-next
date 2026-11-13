package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.ui.geometry.Rect
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.motion.allowed
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.state.ReceiptRules
import io.github.eslamasabry.oc.ui.kit.status.KitMarkSize
import io.github.eslamasabry.oc.ui.kit.status.TaskMarkRules
import io.github.eslamasabry.oc.ui.kit.teaching.TipRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The edge light's pure rules (LAMP-PLAN §2, LAMP-CRAFT §1, §2, §10). */
class KitLampRulesTest {
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
        assertEquals(12, KitLampRules.faceWidthDp(quiet, 1f))
        val open = KitLampFace(2, listOf(m("n", KitLampMarkKind.NeedsYou)), open = true)
        assertEquals(48, KitLampRules.faceHeightDp(open))
        assertEquals(16, KitLampRules.faceWidthDp(open, 1.3f))
        assertEquals(40, KitLampRules.faceHeightDp(KitLampFace(0, listOf(m("w", KitLampMarkKind.Working)))), "one row stays at the 40 dp least")
        assertEquals(53, KitLampRules.faceHeightDp(KitLampFace(1, listOf(m("n", KitLampMarkKind.NeedsYou), m("w", KitLampMarkKind.Working)))))
        val full = KitLampFace(5, List(6) { m("n$it", KitLampMarkKind.NeedsYou) })
        assertEquals(68, KitLampRules.faceHeightDp(full))
        assertEquals(KitLampDims.MAX_H, 68)
        assertEquals(84, KitLampRules.touchHeightDp(68), "the touch box is 16 dp taller than the face")
        assertEquals(24, KitLampDims.TOUCH_W)
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
        assertEquals(20, KitLampRules.faceWidthDp(many, 1.3f))
        assertEquals(16, KitLampRules.faceWidthDp(many, 1f))
        assertEquals(16, KitLampRules.faceWidthDp(many.copy(count = 3), 1.3f))
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
    fun `B-LAMP-K1-010 a tip beside an edge part stands on the page side of it, centred, inside the window`() {
        // A 24 x 84 light on the right edge of a 412 x 860 window; a 200 x 60 tip; 8 px gap.
        val (x, y) = TipRules.besideEdge(388, 500, 412, 584, 412, 860, 200, 60, 8)
        assertEquals(388 - 8 - 200, x)
        assertEquals(542 - 30, y)
        // On the left edge (Arabic): to its right.
        val (lx, _) = TipRules.besideEdge(0, 500, 24, 584, 412, 860, 200, 60, 8)
        assertEquals(32, lx)
        // Near the bottom: kept in the window.
        val (_, by) = TipRules.besideEdge(388, 820, 412, 860, 412, 860, 200, 60, 8)
        assertEquals(800, by)
    }
}
