package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.ui.geometry.Offset
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPanelRules
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPlaceRules
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampHeight
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSide
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.kit.row.ReorderRules
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The edge light panel's pure rules (LAMP-CRAFT §4-5, §4a-b; LAMP-PLAN §B): the tree's lines, the cut, reorder, place. */
class StepTreeRulesTest {
    private val ROOT = StepTreeRules.ROOT
    private val r = 4.675f

    private fun mark(key: Any, parent: Any?, depth: Int, state: KitTreeStepState, x: Float, y: Float, radius: Float = r) =
        StepMark(key, parent, depth, state, Offset(x, y), radius)

    private fun near(a: Float, b: Float, what: String) = assertTrue(abs(a - b) < 0.01f, "$what: expected $a, was $b")

    // Root mark (24 dp) at x 28; children at 52 + 5.5; grandchildren at 75 + 5.5; rows 44 dp apart.
    private val parallel =
        listOf(
            mark(ROOT, null, 0, KitTreeStepState.Working, 28f, 22f, 10.2f),
            mark("a", ROOT, 1, KitTreeStepState.Done, 57.5f, 100f),
            mark("b", ROOT, 1, KitTreeStepState.Working, 57.5f, 144f),
            mark("b1", "b", 2, KitTreeStepState.Done, 80.5f, 188f),
            mark("b2", "b", 2, KitTreeStepState.Working, 80.5f, 232f),
            mark("c", ROOT, 1, KitTreeStepState.NeedsYou, 57.5f, 276f),
        )

    @Test
    fun `B-LAMP-K2-001 a parallel trunk starts at the parent mark's ring and is never progress`() {
        val strokes = StepTreeRules.parallel(parallel, 8f)
        val trunks = strokes.filter { it.toKey == null }
        assertEquals(2, trunks.size, "one trunk under the conversation, one under the sub-agent with sub-agents")
        val root = trunks.first { it.from.x == 28f }
        near(22f + 10.2f, root.from.y, "the trunk starts at the root mark's ring")
        near(276f - 8f, root.end.y, "the trunk ends one branch corner above the last child's centre")
        assertTrue(trunks.all { it.tone == StepTone.Guide }, "a parallel trunk is never coloured as progress")
        val inner = trunks.first { it.from.x == 57.5f }
        near(144f + r, inner.from.y, "a sub-agent's own trunk starts at its ring")
    }

    @Test
    fun `B-LAMP-K2-002 every branch ends exactly on its child's ring, in its child's state, mirrored on the other side`() {
        for (mirror in listOf(false, true)) {
            val w = 400f
            val marks = if (!mirror) parallel else parallel.map { it.copy(center = Offset(w - it.center.x, it.center.y)) }
            val branches = StepTreeRules.parallel(marks, 8f).filter { it.toKey != null }
            assertEquals(5, branches.size)
            for (b in branches) {
                val k = marks.first { it.key == b.toKey }
                near(k.center.y, b.end.y, "$mirror ${k.key}: the branch arrives at the mark's centre line")
                val edge = if (!mirror) k.center.x - k.radius else k.center.x + k.radius
                near(edge, b.end.x, "$mirror ${k.key}: the branch ends exactly on the ring")
                val curve = b.segs.first() as StepSeg.Quad
                near(b.from.x, curve.control.x, "$mirror ${k.key}: the branch leaves the trunk along it (rounded corner)")
            }
            assertEquals(StepTone.Accent, branches.first { it.toKey == "b" }.tone, "working: accent")
            assertEquals(StepTone.Attention, branches.first { it.toKey == "c" }.tone, "waiting for you: amber")
            assertEquals(StepTone.Guide, branches.first { it.toKey == "a" }.tone, "finished: quiet")
        }
    }

    @Test
    fun `B-LAMP-K2-003 a sequence is accent as far as the work got, into the current step and never past a waiting one`() {
        val s = KitTreeStepState.Done
        assertEquals(2, StepTreeRules.accentSegments(listOf(KitTreeStepState.Working, s, s, KitTreeStepState.NotStarted)))
        assertEquals(3, StepTreeRules.accentSegments(listOf(KitTreeStepState.Working, s, s, KitTreeStepState.Working, KitTreeStepState.NotStarted)))
        assertEquals(2, StepTreeRules.accentSegments(listOf(KitTreeStepState.Working, s, KitTreeStepState.NeedsYou, s, KitTreeStepState.Working)), "never past a waiting step")
        assertEquals(0, StepTreeRules.accentSegments(listOf(KitTreeStepState.Working, KitTreeStepState.NotStarted, s)))
        val seq =
            listOf(
                mark(ROOT, null, 0, KitTreeStepState.Working, 28f, 22f, 10.2f),
                mark("1", ROOT, 1, s, 57.5f, 100f),
                mark("2", ROOT, 1, KitTreeStepState.Working, 57.5f, 144f),
                mark("3", ROOT, 1, KitTreeStepState.NotStarted, 57.5f, 188f),
            )
        val strokes = StepTreeRules.sequence(seq, 16f)
        assertEquals(listOf(StepTone.Accent, StepTone.Accent, StepTone.Guide, StepTone.Guide), strokes.map { it.tone })
        // Into the deeper column: down, then an S-bend with vertical tangents, ending on the next mark's top.
        val bend = strokes[0].segs.last() as StepSeg.Cubic
        near(28f, bend.c1.x, "the bend leaves vertically")
        near(57.5f, bend.c2.x, "the bend arrives vertically")
        near(100f - r, bend.to.y, "the trunk meets the step's ring")
        near(144f - r, strokes[1].end.y, "straight down the column, onto the next ring")
        near(100f + r, strokes[1].from.y, "from the previous ring")
    }

    @Test
    fun `B-LAMP-K2-004 the five most urgent children show, in their real order, and a block folds once all are done`() {
        val kids = (1..7).map { i -> KitTreeStep("k$i", "k$i", if (i == 6) KitTreeStepState.NeedsYou else if (i == 2) KitTreeStepState.Failed else KitTreeStepState.Done) }
        val shown = StepTreeRules.shown(kids)
        assertEquals(5, shown.size)
        assertEquals(shown.map { it.key }, shown.sortedBy { kids.indexOf(it) }.map { it.key }, "real order kept")
        assertTrue(shown.any { it.key == "k6" } && shown.any { it.key == "k2" }, "needs you and failed are among them")
        assertEquals(7, StepTreeRules.shown(kids, all = true).size)
        assertTrue(StepTreeRules.open(kids))
        assertFalse(StepTreeRules.open(kids.map { it.copy(state = KitTreeStepState.Done) }), "all done: the block folds itself")
        val deep = KitTreeStep("a", "a", KitTreeStepState.Working, children = listOf(KitTreeStep("b", "b", KitTreeStepState.Done, children = listOf(KitTreeStep("c", "c", KitTreeStepState.Done)))))
        assertEquals(3, StepTreeRules.descendants(KitTreeStep("x", "x", KitTreeStepState.Working, children = listOf(deep))))
        assertEquals(75, StepTreeDims.GRAND_INDENT)
        assertEquals(52, StepTreeDims.CHILD_INDENT)
        assertEquals(44, StepTreeDims.ROW)
        assertEquals(StepTreeRules.seconds(KitTreeStep("t", "t", KitTreeStepState.Working, seconds = 40), 3), 43L, "a working timer counts on")
        assertEquals(StepTreeRules.seconds(KitTreeStep("t", "t", KitTreeStepState.Done, seconds = 40), 3), 40L, "a finished step keeps the time it took")
    }

    @Test
    fun `B-LAMP-K2-005 panel_topCutOnGroupBoundary - the list keeps whole groups from the bottom when they fill 45 percent`() {
        assertEquals(400, KitLampPanelRules.viewport(listOf(100, 120, 180), 500), "all fit")
        assertEquals(300, KitLampPanelRules.viewport(listOf(100, 120, 120, 180), 400), "cut above the Working group: 300 of 400")
        assertEquals(400, KitLampPanelRules.viewport(listOf(100, 300, 150), 400), "one group is under 45 % of the room: use the whole room")
        assertEquals(KitCornersCheck.sheetPanel, KitLampPanelRules.corners(lightAtEnd = true).let { it.topStart to it.topEnd })
        assertEquals(KitCornersCheck.panelSheet, KitLampPanelRules.corners(lightAtEnd = false).let { it.topStart to it.topEnd })
    }

    @Test
    fun `B-LAMP-K2-006 reorder - the target place, the neighbours' room and the settle`() {
        val spans = listOf(0f to 50f, 50f to 100f, 100f to 150f, 150f to 200f)
        assertEquals(0, ReorderRules.target(0, 40f, spans))
        assertEquals(1, ReorderRules.target(0, 80f, spans), "past the next row's middle")
        assertEquals(3, ReorderRules.target(0, 190f, spans))
        assertEquals(0, ReorderRules.target(2, 10f, spans))
        assertEquals(-50f, ReorderRules.offsetFor(1, 0, 2, 50f), "a row the lifted one passed moves up by its height")
        assertEquals(0f, ReorderRules.offsetFor(3, 0, 2, 50f))
        assertEquals(50f, ReorderRules.offsetFor(1, 3, 1, 50f))
        assertEquals(100f, ReorderRules.settledTop(0, 2, spans))
        assertEquals(50f, ReorderRules.settledTop(3, 1, spans))
    }

    @Test
    fun `B-LAMP-K2-007 the place - Low 196 above the bottom, Middle and High by share, sides by the physical edge`() {
        near(800f - 196f - 56f, KitLampPlaceRules.touchTop(KitLampHeight.Low, 800f, 56f, 196f), "Low")
        near(800f - 360f - 28f, KitLampPlaceRules.touchTop(KitLampHeight.Middle, 800f, 56f, 196f), "Middle")
        near(800f - 512f - 28f, KitLampPlaceRules.touchTop(KitLampHeight.High, 800f, 56f, 196f), "High")
        assertEquals(KitLampSide.Start, KitLampPlaceRules.sideOf(left = true, rtl = false))
        assertEquals(KitLampSide.End, KitLampPlaceRules.sideOf(left = true, rtl = true), "in Arabic the left edge is the end")
        assertEquals(KitLampSide.End, KitLampPlaceRules.sideOf(left = false, rtl = false))
    }

    private object KitCornersCheck {
        val sheetPanel = KitShapeId.Sheet to KitShapeId.Panel
        val panelSheet = KitShapeId.Panel to KitShapeId.Sheet
    }
}
