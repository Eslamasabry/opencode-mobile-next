@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Device QA 2026-11-11 (run 5): the opened list was flat against the screen edge it stands on. Whole, it is a pane a
 * page gutter in from that edge with all four corners round; it comes off the edge with its own growth.
 */
class KitEdgeDockListPaneTest {
    private val title = "Quarterly budget review for the phone app and the release notes for the next beta"
    private val rings =
        listOf(
            KitDockRing("one", "name two animals", null, KitDockRingState.Working),
            KitDockRing("two", title, null, KitDockRingState.Idle, lastLine = "count from 1 to 300 with one fact each"),
            KitDockRing("three", "create a file hello.txt", null, KitDockRingState.Idle),
        )

    private fun scene(width: Int, height: Int, side: KitDockSide, block: ComposeUiTest.(KitEdgeDockState) -> Unit) =
        runDesktopComposeUiTest(width, height) {
            val state = KitEdgeDockState()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(
                            KitDockModel(rings = rings, placement = KitDockPlacement(side, 0.5f)),
                            object : KitDockIntents {},
                            TestDockWords,
                            insets = KitDockInsets(top = 24.dp, bottom = 48.dp),
                            state = state,
                        )
                    }
                }
            }
            waitForIdle()
            block(state)
        }

    private fun ComposeUiTest.grow(state: KitEdgeDockState, g: Float) {
        runOnIdle { runBlocking { state.panelX.snapTo(g); state.panelY.snapTo(g.coerceAtMost(1f)) } }
        waitForIdle()
    }

    private val sizes = listOf(360 to 800, 320 to 700)
    private val sides = listOf(KitDockSide.End, KitDockSide.Start)

    @Test
    fun `list pane - whole, its outer side stands the gutter in from the screen edge and all four corners are equal`() {
        for ((w, h) in sizes) for (side in sides) scene(w, h, side) { state ->
            val at = "$w x $h dp, $side"
            grow(state, 1f)
            val f = runOnIdle { state.computeFrame(); state.frame }
            val b = runOnIdle { state.body() }
            assertTrue(b.hasSecond && b.secondIsList, "$at: the list is box B")
            val g = DockDims.GUTTER
            assertEquals(w - g, f.bRight, 0.01f, "$at: the outer side is the gutter in from the edge (edge frame)")
            val outer = if (side == KitDockSide.End) w - b.secondRight else b.secondLeft
            assertEquals(g, outer / state.density, 0.01f, "$at: physical outer gap in dp")
            assertTrue(b.secondLeft >= 0f && b.secondRight <= w * state.density + 0.5f, "$at: the pane is inside the screen")
            assertTrue(b.secondRadius > 0f, "$at: rounded")
            // Box A rides inside the pane: no strip sliver at the edge once the list is whole.
            assertTrue(b.stripRight <= b.secondRight + 0.5f && b.stripLeft >= b.secondLeft - 0.5f, "$at: the strip box is inside the pane")
            assertTrue(b.stripTop >= b.secondTop - 0.5f && b.stripBottom <= b.secondBottom + 0.5f, "$at: the strip box is within the pane's height")
            // The four corners: the outline's nearest point to each box corner is as far from it as an arc of the same radius.
            val o = runOnIdle { state.bodyOutline() }
            val corners = listOf(f.bLeft to f.bTop, f.bRight to f.bTop, f.bLeft to f.bBottom, f.bRight to f.bBottom)
            val gaps = corners.map { (cx, cy) -> (0 until o.n).minOf { hypot(o.x[it] - cx, o.y[it] - cy) } }
            for (k in gaps.indices) assertEquals(gaps[0], gaps[k], 0.6f, "$at: corner $k gap ${gaps[k]} equals corner 0 ${gaps[0]}")
            assertTrue(gaps[0] > 1f, "$at: the corners are round (${gaps[0]})")
            val mx = (0 until o.n).maxOf { o.x[it] }
            assertTrue(mx <= w - g + 0.01f, "$at: the outline does not reach the edge ($mx)")
            // The inner side keeps today's gutter from the far edge.
            val inner = if (side == KitDockSide.End) b.secondLeft else w * state.density - b.secondRight
            assertTrue(inner / state.density >= DockDims.PANEL_MIN_GAP - 0.5f, "$at: the inner gutter holds")
        }
    }

    @Test
    fun `list pane - at growth 0 the strip is attached to the edge as before`() {
        for ((w, h) in sizes) for (side in sides) scene(w, h, side) { state ->
            val b = runOnIdle { state.body() }
            val d = state.density
            assertTrue(!b.hasSecond, "no second box")
            val reach = if (side == KitDockSide.End) b.stripRight / d else w - b.stripLeft / d
            assertEquals(w + DockDims.OVERHANG, reach, 0.01f, "$w, $side: the strip runs past the edge")
        }
    }

    @Test
    fun `list pane - across 50 samples of growth the outer side only moves in`() {
        for ((w, h) in sizes) for (side in sides) scene(w, h, side) { state ->
            var last = Float.POSITIVE_INFINITY
            var lastRadius = -1f
            for (i in 1..50) {
                grow(state, i / 50f)
                val f = runOnIdle { state.computeFrame(); state.frame }
                assertTrue(f.bRight <= last + 0.001f, "$w, $side: step $i outer ${f.bRight} after $last")
                assertTrue(f.bRadius >= lastRadius - 0.001f, "$w, $side: step $i radius ${f.bRadius} after $lastRadius")
                last = f.bRight
                lastRadius = f.bRadius
            }
            assertEquals(w - DockDims.GUTTER, last, 0.01f, "$w, $side: ends a gutter in")
            // A pull that overshoots 1 holds the pane where it is.
            grow(state, 1.08f)
            assertEquals(w - DockDims.GUTTER, runOnIdle { state.computeFrame(); state.frame.bRight }, 0.01f, "$w, $side: overshoot holds")
        }
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(out) }
        return out.single()
    }

    @Test
    fun `list pane - a row title cut at a word break ends in an ellipsis, Quarterly budget review for the phone app`() {
        for ((w, h) in sizes) scene(w, h, KitDockSide.End) { state ->
            runOnIdle { state.openPanel() }
            waitForIdle()
            val nodes = onAllNodes(hasText("Quarterly", substring = true), useUnmergedTree = true)
            val n = nodes.fetchSemanticsNodes().size
            assertTrue(n > 0, "$w: the title is shown")
            var seen = 0
            for (i in 0 until n) {
                val l = nodes[i].layout()
                // The row's title is one line cut to the pane: it is never whole, so it must carry the mark.
                if (l.layoutInput.text.length < title.length) continue
                seen++
                assertTrue(l.lineCount == 1, "$w: one line")
                assertTrue(l.isLineEllipsized(0), "$w: the title is cut at ${l.getLineEnd(0, true)} of ${title.length} with an ellipsis")
            }
            assertTrue(seen > 0, "$w: the row title was inspected")
        }
    }
}
