package io.github.eslamasabry.oc.ui.kit.dock

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The hand-over's guarantee to the glass owner: the outline the Solid face fills ([KitEdgeDockState.bodyOutline], the
 * path the body node builds) lies on the outline of the two boxes [KitEdgeDockState.body] hands over, joined by the
 * cubic smooth-minimum field with the reach it hands over, within one pixel. Checked at growth 0.3, 0.6 and 1, for the
 * swell, the peek and rename, on both edges, in physical pixels at a phone's density.
 *
 * The field ([DockJoinField]) is the dock's copy of the glass branch's `LiquidJoin.sample` (not on this branch): once
 * both meet, this test should sample `LiquidJoin.sample` itself.
 */
class DockBodyHandOverTest {
    private val width = 412f
    private val density = 2.625f

    private suspend fun dock(onRight: Boolean, kind: SwellKind, growth: Float): Pair<KitEdgeDockState, SwellLayout> {
        val s = KitEdgeDockState()
        s.widthDp = width
        s.heightDp = 915f
        s.safeTop = 24f
        s.safeBottom = 915f - 48f
        s.edgeInset = 0f
        s.farInset = 0f
        s.onRight = onRight
        s.density = density
        s.shoulders = true
        s.column = DockColumn.of(5, 0, s.safeBottom - s.safeTop)
        s.top.snapTo(placeTop(s.column.height, 0.5f, s.safeTop, s.safeBottom))
        val layout =
            swellLayout(kind, KitDockRingState.NeedsYou, 3, 6, false, 1f, width - DockDims.REST_W - DockDims.EDGE_MARGIN, DockDims.REST_W)
        s.swell = layout
        s.swellCenter.snapTo(s.slotY(2))
        s.swellH.snapTo(layout.height)
        s.swellLen.snapTo(layout.length)
        s.swellGrow.snapTo(growth)
        return s to layout
    }

    @Test
    fun `the outline Solid draws lies on the join of the two boxes body hands over, within one pixel, at growth three tenths, six tenths and full`() =
        runTest {
            for (onRight in listOf(true, false)) {
                for (kind in SwellKind.entries) {
                    for (g in listOf(0.3f, 0.6f, 1f)) {
                        val (s, layout) = dock(onRight, kind, g)
                        val at = "${if (onRight) "right" else "left"} edge, $kind, growth $g"
                        val body = s.body(KitDockBody())
                        assertTrue(body.hasSecond, at)
                        assertFalse(body.secondIsList, at)
                        // B is handed over as it is now: growth x length out of the strip, plus its overlap into it.
                        assertEquals(g * layout.length + DockDims.SW_OVERLAP, (body.secondRight - body.secondLeft) / density, 0.01f, at)
                        assertEquals(layout.radius * density, body.secondRadius, 0.01f, at)
                        assertEquals(2f * body.filletBlend, body.joinReach, 0.01f, at)

                        val field =
                            DockJoinField().set(
                                body.stripLeft, body.stripTop, body.stripRight, body.stripBottom, body.stripRadius,
                                body.secondLeft, body.secondTop, body.secondRight, body.secondBottom, body.secondRadius,
                                body.joinReach,
                            )
                        val o = s.bodyOutline()
                        assertTrue(o.swellFrom >= 0 && o.swellTo > o.swellFrom + 50, "$at: the swell is traced")
                        val edgeX = s.frame.edgeX
                        var checked = 0
                        var swellChecked = 0
                        for (i in 0 until o.n) {
                            // The shoulders into the screen edge are a third shape outside the two-box join.
                            if (o.x[i] > edgeX - DockDims.SHOULDER_REACH - 0.5f) continue
                            val px = s.phys(o.x[i]) * density
                            val py = o.y[i] * density
                            val dist = field.d(px, py)
                            assertTrue(abs(dist) <= 1f, "$at: point $i (${o.x[i]}, ${o.y[i]}) dp is ${dist} px off the join")
                            checked++
                            if (i in o.swellFrom..o.swellTo) swellChecked++
                        }
                        assertEquals(o.swellTo - o.swellFrom + 1, swellChecked, "$at: every swell point is checked")
                        assertTrue(checked > 100, "$at: $checked points")
                        // The drawn tip is B's left edge (its right edge on the left side), not a squeezed copy of it.
                        val tip = s.phys(o.minX) * density
                        val bTip = if (onRight) body.secondLeft else body.secondRight
                        assertEquals(bTip, tip, 1f, at)
                    }
                }
            }
        }

    @Test
    fun `the list hands over B alone with no join, and the strip at rest hands over no second box`() =
        runTest {
            val (s, _) = dock(true, SwellKind.Scrub, 1f)
            s.swellGrow.snapTo(0f)
            val rest = s.body(KitDockBody())
            assertFalse(rest.hasSecond)
            assertEquals(0f, rest.joinReach)
            s.panelX.snapTo(1f)
            s.panelY.snapTo(1f)
            val list = s.body(KitDockBody())
            assertTrue(list.hasSecond && list.secondIsList)
            assertEquals(0f, list.joinReach)
            assertTrue(list.secondLeft <= list.stripLeft && list.secondTop <= list.stripTop && list.secondBottom >= list.stripBottom)
        }
}
