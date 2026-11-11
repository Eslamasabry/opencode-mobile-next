@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Device QA 2026-11-11 (run 5, d): the list opened by an inward flick stood beyond the wrong screen edge. Driven
 * through the real pointer loop at the widths of a phone, on both edges.
 */
class KitEdgeDockListGestureTest {
    private val rings =
        List(7) { KitDockRing("r$it", "conversation number $it", null, KitDockRingState.Idle) }

    private class Run(val state: KitEdgeDockState, val sideNow: () -> KitDockSide, val placements: MutableList<KitDockSide>)

    private fun scene(widthDp: Int, side: KitDockSide, bottom: Int = 48, block: ComposeUiTest.(Run) -> Unit) =
        runDesktopComposeUiTest(widthDp, 800) {
            var now by mutableStateOf(side)
            val placements = mutableListOf<KitDockSide>()
            val state = KitEdgeDockState()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(
                            KitDockModel(rings = rings, placement = KitDockPlacement(now, 0.5f)),
                            object : KitDockIntents {
                                override fun onPlacementChange(side: KitDockSide, verticalBias: Float) {
                                    placements += side
                                    now = side
                                }
                            },
                            TestDockWords,
                            insets = KitDockInsets(top = 24.dp, bottom = bottom.dp),
                            state = state,
                        )
                    }
                }
            }
            waitForIdle()
            block(Run(state, { now }, placements))
        }

    /** The physical x of the strip's middle and the y of ring [i]'s centre. */
    private fun ComposeUiTest.ringPoint(run: Run, i: Int): Offset =
        runOnIdle {
            val w = run.state.widthDp
            val x = if (run.state.onRight) w - DockDims.REST_W / 2f else DockDims.REST_W / 2f
            Offset(x, run.state.slotY(i))
        }

    private fun ComposeUiTest.headPoint(run: Run): Offset = runOnIdle { ringPoint(run, 0).copy(y = run.state.columnTop + 12f) }

    private fun inward(side: KitDockSide) = if (side == KitDockSide.End) -1f else 1f

    /** After the list opened: flush with the dock's own edge, a gutter on the inner side, rounded there, dock unmoved. */
    private fun ComposeUiTest.assertListOpenOnItsOwnEdge(run: Run, w: Int, side: KitDockSide, how: String) {
        val at = "$how, $w dp, $side"
        val b = runOnIdle { run.state.body() }
        assertTrue(runOnIdle { run.state.panelOpen }, "$at: the list is open")
        assertTrue(b.hasSecond && b.secondIsList, "$at: box B is the list")
        assertEquals(emptyList(), run.placements, "$at: the dock did not change edge")
        assertEquals(side == KitDockSide.End, runOnIdle { run.state.onRight }, "$at: the dock stands on its edge")
        assertTrue(b.secondRadius > 0f, "$at: the corners are rounded")
        if (side == KitDockSide.End) {
            assertTrue(b.secondRight >= w.toFloat(), "$at: the pane reaches the right edge (${b.secondRight})")
            assertTrue(b.secondLeft >= DockDims.PANEL_MIN_GAP - 0.5f && b.secondLeft < w - DockDims.REST_W, "$at: the page gutter on the inner side (${b.secondLeft})")
        } else {
            assertTrue(b.secondLeft <= 0f, "$at: the pane reaches the left edge (${b.secondLeft})")
            assertTrue(b.secondRight <= w - DockDims.PANEL_MIN_GAP + 0.5f && b.secondRight > DockDims.REST_W, "$at: the page gutter on the inner side (${b.secondRight})")
        }
    }

    @Test
    fun `list flick - a flick inward from a ring opens the list on the dock's own edge`() {
        for (w in listOf(360, 320)) for (side in listOf(KitDockSide.End, KitDockSide.Start)) scene(w, side) { run ->
            val p = ringPoint(run, 2)
            onRoot().performTouchInput { swipe(p, Offset(p.x + inward(side) * 200f, p.y), durationMillis = 120) }
            waitForIdle()
            assertListOpenOnItsOwnEdge(run, w, side, "flick from a ring")
        }
    }

    @Test
    fun `list flick - a flick inward from the head opens the list and does not carry the dock to the other edge`() {
        for (w in listOf(360, 320)) for (side in listOf(KitDockSide.End, KitDockSide.Start)) scene(w, side) { run ->
            val p = headPoint(run)
            onRoot().performTouchInput { swipe(p, Offset(p.x + inward(side) * 200f, p.y), durationMillis = 120) }
            waitForIdle()
            assertListOpenOnItsOwnEdge(run, w, side, "flick from the head")
        }
    }

    @Test
    fun `list flick - a slow drag from the head across the middle still moves the dock to the other edge`() {
        for (w in listOf(360, 320)) for (side in listOf(KitDockSide.End, KitDockSide.Start)) scene(w, side) { run ->
            val p = headPoint(run)
            onRoot().performTouchInput { swipe(p, Offset(p.x + inward(side) * (w - 120f), p.y), durationMillis = 2500) }
            waitForIdle()
            val other = if (side == KitDockSide.End) KitDockSide.Start else KitDockSide.End
            assertEquals(listOf(other), run.placements, "$w dp, $side: the slow drag asks for the other edge")
            assertTrue(!runOnIdle { run.state.panelOpen }, "$w dp, $side: no list opens")
        }
    }

    @Test
    fun `list flick - a slow drag from the head that stays on its half keeps the edge and opens nothing`() {
        for (w in listOf(360, 320)) for (side in listOf(KitDockSide.End, KitDockSide.Start)) scene(w, side) { run ->
            val p = headPoint(run)
            onRoot().performTouchInput { swipe(p, Offset(p.x + inward(side) * 40f, p.y + 60f), durationMillis = 1500) }
            waitForIdle()
            assertTrue(run.placements.all { it == side }, "$w dp, $side: stays on its edge ${run.placements}")
            assertTrue(!runOnIdle { run.state.panelOpen }, "$w dp, $side: no list opens")
        }
    }

    @Test
    fun `ring column - a vertical drag in the strip scrubs the rings and does not scroll the column`() =
        scene(360, KitDockSide.End, bottom = 48 + 378) { run ->
            val (maxScroll, before) = runOnIdle { run.state.column.maxScroll to run.state.scrollNow }
            assertTrue(maxScroll > 0f, "the column holds more rings than whole slots (maxScroll $maxScroll)")
            val p = ringPoint(run, 0)
            onRoot().performTouchInput { swipe(p, Offset(p.x, p.y + 150f), durationMillis = 400) }
            waitForIdle()
            assertEquals(before, runOnIdle { run.state.scrollNow }, "the drag along the strip did not scroll the column")
        }

    @Test
    fun `list height - in an area that holds three whole rings the list is as tall as the area allows and scrolls its seven rows`() =
        scene(360, KitDockSide.End, bottom = 48 + 400) { run ->
            val p = ringPoint(run, 0)
            onRoot().performTouchInput { swipe(p, Offset(p.x - 200f, p.y), durationMillis = 120) }
            waitForIdle()
            val (b, span, content, rows) =
                runOnIdle {
                    val body = run.state.body()
                    val s = run.state
                    listOf(body.secondBottom - body.secondTop, s.safeBottom - s.safeTop - 2f * (DockDims.EDGE_MARGIN + DockDims.SHOULDER_REACH), s.panelContent, s.rings.size.toFloat())
                }
            assertTrue(runOnIdle { run.state.panelOpen }, "the list is open")
            assertEquals(7f, rows, "the model holds seven rings")
            assertTrue(content + 2f * DockDims.PANEL_PAD + DockDims.HEAD > span, "seven rows are taller than the area ($content over $span): the list scrolls inside itself")
            assertEquals(span, b, 0.5f, "the list takes all the height the area allows, $b of $span")
        }
}
