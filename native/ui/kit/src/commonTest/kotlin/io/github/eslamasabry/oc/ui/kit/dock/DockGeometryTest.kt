package io.github.eslamasabry.oc.ui.kit.dock

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The edge dock's shapes, measured from the outline itself (the owner's limits for the swell: at least 64 dp tall, at
 * most 2.2 x as long as tall, a base at least 1.3 x the tip, an end corner under 0.35 x the height), at phone widths 360
 * and 412 dp, with 0 to 5 sub-agents, at text scale 1 and 1.3.
 */
class DockGeometryTest {
    private val safeTop = 24f
    private val safeBottom = 915f - 48f

    private class Scene(val width: Float, val column: DockColumn, val strip: DockBox)

    private fun scene(width: Float, rings: Int = 5, pinned: Int = 0): Scene {
        val c = DockColumn.of(rings, pinned, safeBottom - safeTop)
        val top = placeTop(c.height, 0.5f, safeTop, safeBottom)
        return Scene(width, c, stripBox(width, 0f, DockDims.REST_W, top, top + c.height))
    }

    private class Measured(val length: Float, val height: Float, val base: Float, val endCorner: Float, val tip: Float)

    /** Builds the body for a swell beside ring [ring] and measures it from the outline's points. */
    private fun swellBody(s: Scene, layout: SwellLayout, ring: Int, o: DockOutline = DockOutline()): Pair<Measured, PlacedSwell> {
        val cy = s.column.slotCenter(ring, s.strip.top, 0f)
        val placed = placeSwell(layout, cy, s.strip, safeTop, safeBottom)
        val a = s.strip.copy(top = placed.stripTop, bottom = placed.stripBottom)
        buildOutline(o, a, placed.b, joinReachFor(placed.blend, 1f), s.width, shoulders = true)
        assertTrue(o.swellFrom >= 0, "the outline has a swell part")
        val lo = a.left
        var tip = Float.POSITIVE_INFINITY
        for (i in o.swellFrom..o.swellTo) tip = minOf(tip, o.x[i])
        val len = lo - tip
        var baseTop = Float.POSITIVE_INFINITY
        var baseBottom = Float.NEGATIVE_INFINITY
        var tipTop = Float.POSITIVE_INFINITY
        var tipBottom = Float.NEGATIVE_INFINITY
        for (i in o.swellFrom..o.swellTo) {
            val d = lo - o.x[i]
            if (d > 2f) {
                baseTop = minOf(baseTop, o.y[i])
                baseBottom = maxOf(baseBottom, o.y[i])
            }
            if (d >= len - layout.radius) {
                tipTop = minOf(tipTop, o.y[i])
                tipBottom = maxOf(tipBottom, o.y[i])
            }
        }
        return Measured(len, tipBottom - tipTop, baseBottom - baseTop, layout.radius, tip) to placed
    }

    private fun maxTurnDegrees(o: DockOutline): Float {
        var m = 0f
        for (i in 1 until o.n - 1) {
            val a = atan2(o.y[i] - o.y[i - 1], o.x[i] - o.x[i - 1])
            val b = atan2(o.y[i + 1] - o.y[i], o.x[i + 1] - o.x[i])
            var d = b - a
            while (d > PI) d -= (2 * PI).toFloat()
            while (d < -PI) d += (2 * PI).toFloat()
            m = maxOf(m, abs(d))
        }
        return (m * 180f / PI).toFloat()
    }

    @Test
    fun `the swell is broad and short for 0 to 5 sub-agents at both phone widths and both text scales`() {
        for (width in listOf(360f, 412f)) {
            val s = scene(width)
            for (kids in 0..5) {
                for (scale in listOf(1f, 1.3f)) {
                    val layout = swellLayout(SwellKind.Scrub, KitDockRingState.WorkingWithTasks(3, 7), kids, 0, false, scale, width - DockDims.REST_W - DockDims.EDGE_MARGIN)
                    val (m, _) = swellBody(s, layout, 2)
                    val at = "width $width, $kids sub-agents, scale $scale"
                    assertTrue(m.height >= 64f, "$at: tall ${m.height}")
                    assertTrue(m.length <= 2.2f * m.height, "$at: long ${m.length} for tall ${m.height}")
                    assertTrue(m.base >= 1.3f * m.height, "$at: base ${m.base} for tip ${m.height}")
                    assertTrue(m.endCorner < 0.35f * m.height, "$at: end corner ${m.endCorner}")
                }
            }
        }
    }

    @Test
    fun `sub-agents make the swell taller, never longer`() {
        val lengths =
            (0..5).map { kids -> swellLayout(SwellKind.Scrub, KitDockRingState.Working, kids, 0, false, 1f, 400f) }
        assertEquals(1, lengths.map { it.length }.distinct().size)
        assertTrue(lengths[3].height > lengths[0].height)
    }

    @Test
    fun `the swell keeps its symmetric shape at the first and the last ring, the strip's end extends instead`() {
        val s = scene(412f)
        val layout = swellLayout(SwellKind.Scrub, KitDockRingState.Working, 0, 0, false, 1f, 330f)
        for (ring in listOf(0, s.column.count - 1)) {
            val o = DockOutline()
            val (m, placed) = swellBody(s, layout, ring, o)
            // The fillet keeps its full reach on both sides: the swell never pools into the dock's end.
            assertEquals(layout.filletBlend, placed.blend, 0.01f, "ring $ring")
            val b = placed.b
            val above = b.top - (placed.stripTop + DockDims.CORNER)
            val below = (placed.stripBottom - DockDims.CORNER) - b.bottom
            assertTrue(above >= layout.filletBlend && below >= layout.filletBlend, "ring $ring: room above $above, below $below")
            assertTrue(m.base >= 1.3f * m.height)
            // The ring's own band stays beside the swell.
            val cy = s.column.slotCenter(ring, s.strip.top, 0f)
            assertTrue(b.top <= cy - DockDims.SW_RING_BAND + 0.01f && b.bottom >= cy + DockDims.SW_RING_BAND - 0.01f)
        }
        val first = placeSwell(layout, s.column.slotCenter(0, s.strip.top, 0f), s.strip, safeTop, safeBottom)
        assertTrue(first.stripTop < s.strip.top, "the strip's top extends for the first ring")
    }

    @Test
    fun `the outline has no kink anywhere - rest, swell, peek, rename and list`() {
        val s = scene(412f)
        val o = DockOutline()
        buildOutline(o, s.strip, null, 0f, 412f, shoulders = true)
        assertTrue(maxTurnDegrees(o) < 15f, "rest: ${maxTurnDegrees(o)}")
        for (kind in SwellKind.entries) {
            val layout = swellLayout(kind, KitDockRingState.NeedsYou, 3, 6, false, 1f, 330f)
            swellBody(s, layout, 2, o)
            assertTrue(maxTurnDegrees(o) < 15f, "$kind: ${maxTurnDegrees(o)}")
        }
        val panel = panelBox(412f, 0f, 0f, s.strip, 600f, safeTop, safeBottom)
        buildOutline(o, s.strip, panelAt(s.strip, panel, 1f, 1f), 0f, 412f, shoulders = true)
        assertTrue(maxTurnDegrees(o) < 15f, "list: ${maxTurnDegrees(o)}")
    }

    @Test
    fun `the root fillet's tightest radius follows its blend and its ends meet the edges with no curvature`() {
        val s = scene(412f)
        for (kind in listOf(SwellKind.Scrub, SwellKind.Peek)) {
            val layout = swellLayout(kind, KitDockRingState.Working, 0, 4, false, 1f, 330f)
            val o = DockOutline()
            val (_, placed) = swellBody(s, layout, 2, o)
            // The top fillet: the traced points between A's side and B's top edge.
            var tightest = Float.POSITIVE_INFINITY
            for (i in o.swellFrom + 1 until o.swellTo) {
                if (o.y[i] < placed.b.top - 0.05f && o.x[i] < s.strip.left - 0.05f) tightest = minOf(tightest, threePointRadius(o, i, 1))
            }
            val want = layout.filletBlend * DockDims.FILLET_RADIUS_PER_BLEND
            assertTrue(abs(tightest - want) < want * 0.2f, "$kind: tightest $tightest, wanted about $want")
            // Near both ends of the fillet the radius is huge (curvature runs out to zero: no step in the rim light).
            assertTrue(threePointRadius(o, o.swellFrom + 1, 1) > 4f * want, "$kind: the walk starts on A's straight side")
        }
        // The design targets: 22.7 dp at the swell's blend, 33.4 dp at the peek's (widened for the base rule).
        assertEquals(22.7f, filletFor(224f, 118f) * DockDims.FILLET_RADIUS_PER_BLEND, 0.05f)
        assertEquals(33.45f, filletFor(DockDims.PEEK_MAX_LEN, 288f) * DockDims.FILLET_RADIUS_PER_BLEND, 0.05f)
    }

    @Test
    fun `the cubic fillet point starts on edge A and ends on edge B at its reach`() {
        filletPoint(-1f, 40f) { da, db ->
            assertEquals(0f, da, 1e-4f)
            assertEquals(40f, db, 1e-4f)
        }
        filletPoint(1f, 40f) { da, db ->
            assertEquals(40f, da, 1e-4f)
            assertEquals(0f, db, 1e-4f)
        }
        filletPoint(0f, 60f) { da, db ->
            assertEquals(10f, da, 1e-4f)
            assertEquals(10f, db, 1e-4f)
        }
    }

    @Test
    fun `every target in the swell and the peek is at least 48 dp each way`() {
        for (scale in listOf(1f, 1.3f)) {
            for (kind in listOf(SwellKind.Scrub, SwellKind.Peek)) {
                for (state in listOf(KitDockRingState.NeedsYou, KitDockRingState.Working, KitDockRingState.Idle)) {
                    val l = swellLayout(kind, state, 5, 6, true, scale, 280f)
                    for (t in l.targets.filter { it.kind != DockTargetKind.Header }) {
                        assertTrue(t.rect.width >= 48f && t.rect.height >= 48f, "$kind $state ${t.kind}: ${t.rect}")
                    }
                }
            }
        }
    }

    @Test
    fun `the swell offers Allow and Deny for a request, Stop while working, and always Rename and Remove`() {
        assertEquals(listOf(DockTargetKind.Allow, DockTargetKind.Deny, DockTargetKind.Rename, DockTargetKind.Remove), swellActions(KitDockRingState.NeedsYou, SwellKind.Scrub))
        assertEquals(listOf(DockTargetKind.Stop, DockTargetKind.Rename, DockTargetKind.Remove), swellActions(KitDockRingState.Thinking, SwellKind.Scrub))
        assertEquals(listOf(DockTargetKind.Rename, DockTargetKind.Remove), swellActions(KitDockRingState.Finished, SwellKind.Scrub))
        assertEquals(DockTargetKind.Open, swellActions(KitDockRingState.Idle, SwellKind.Peek).first())
        assertTrue(swellActions(KitDockRingState.NeedsYou, SwellKind.Rename).isEmpty())
    }

    @Test
    fun `hit-testing picks the right target, mini ring and ring`() {
        val l = swellLayout(SwellKind.Scrub, KitDockRingState.NeedsYou, 3, 0, false, 1f, 330f)
        val allow = l.targets.first { it.kind == DockTargetKind.Allow }
        assertEquals(DockTargetKind.Allow, l.targetAt(allow.rect.left + 2f, allow.rect.top + 2f)?.kind)
        val second = l.targets.first { it.kind == DockTargetKind.Child && it.child == 1 }
        val hit = assertNotNull(l.targetAt((second.rect.left + second.rect.right) / 2f, second.rect.bottom - 1f))
        assertEquals(DockTargetKind.Child, hit.kind)
        assertEquals(1, hit.child)
        val header = l.targets.first { it.kind == DockTargetKind.Header }
        assertEquals(DockTargetKind.Header, l.targetAt(header.rect.left + 1f, header.rect.top + 1f)?.kind)
        assertEquals(null, l.targetAt(-5f, -5f))
        // More than four sub-agents: three mini rings and "+n".
        val five = swellLayout(SwellKind.Scrub, KitDockRingState.Working, 5, 0, false, 1f, 330f)
        assertEquals(3, five.targets.count { it.kind == DockTargetKind.Child })
        assertEquals(2, five.targets.first { it.kind == DockTargetKind.More }.child)

        val c = DockColumn.of(5, 0, 800f)
        val top = 100f
        assertEquals(0, c.ringAt(top + DockDims.HEAD + 1f, top, 0f))
        assertEquals(2, c.ringAt(c.slotCenter(2, top, 0f), top, 0f))
        assertEquals(-1, c.ringAt(top + 2f, top, 0f), "the grip band is not a ring")
        assertEquals(-1, c.ringAt(top + c.height - 2f, top, 0f), "the foot is not a ring")
    }

    @Test
    fun `the column fits up to seven rings and scrolls beyond, never below a 56 dp pitch`() {
        val seven = DockColumn.of(7, 0, 820f)
        assertEquals(7, seven.visible)
        assertTrue(seven.pitch >= DockDims.PITCH_MIN)
        val twelve = DockColumn.of(12, 0, 820f)
        assertTrue(twelve.visible <= DockDims.MAX_VISIBLE)
        assertTrue(twelve.maxScroll > 0f)
        val short = DockColumn.of(7, 0, 420f)
        assertTrue(short.pitch >= DockDims.PITCH_MIN && short.visible < 7)
    }

    @Test
    fun `a dragged ring lands on the slot under it, and above the pin line it pins`() {
        val c = DockColumn.of(5, 2, 820f)
        val top = 80f
        assertEquals(0, c.reorderIndex(c.slotCenter(0, top, 0f), top, 0f))
        assertEquals(3, c.reorderIndex(c.slotCenter(3, top, 0f), top, 0f))
        assertEquals(4, c.reorderIndex(10_000f, top, 0f))
        val line = c.pinLineY(top, 0f)
        assertTrue(c.pinsAt(line - 1f, top, 0f))
        assertFalse(c.pinsAt(line + 1f, top, 0f))
        assertTrue(c.slotCenter(2, top, 0f) - c.slotCenter(1, top, 0f) > c.pitch, "the pin gap separates pinned rings")
        // While reordering with nothing pinned, the pin line sits above the first ring.
        val none = DockColumn.of(5, 0, 820f, reordering = true)
        assertTrue(none.pinLineY(top, 0f) < none.slotCenter(0, top, 0f))
    }

    @Test
    fun `carrying a ring makes room for it`() {
        // Ring 2 carried up to the top: rings 0 and 1 step down one place.
        assertEquals(listOf(1, 2, 0, 3), (0..3).map { visualIndex(it, 2, 0) })
        // Ring 0 carried down to 2: rings 1 and 2 step up.
        assertEquals(2, visualIndex(0, 0, 2))
        assertEquals(0, visualIndex(1, 0, 2))
        assertEquals(1, visualIndex(2, 0, 2))
        assertEquals(3, visualIndex(3, 0, 2))
        assertEquals(1, visualIndex(1, -1, 3))
    }

    @Test
    fun `placement keeps clear of the bars and of keep-clear spots in the dock's band`() {
        val h = 400f
        val high = placeTop(h, 0f, safeTop, safeBottom)
        val low = placeTop(h, 1f, safeTop, safeBottom)
        val room = DockDims.EDGE_MARGIN + DockDims.SHOULDER_REACH
        assertEquals(safeTop + room, high, 0.01f)
        assertEquals(safeBottom - room - h, low, 0.01f)
        // The send button sits low at the dock's edge: the dock moves above it.
        val send = DockRect(340f, 780f, 412f, 840f)
        val t = placeTop(h, 1f, safeTop, safeBottom, listOf(send), 330f, 412f)
        assertTrue(t + h + room <= send.top + 0.01f, "dock bottom ${t + h} vs send top ${send.top}")
        // A spot outside the dock's band changes nothing.
        assertEquals(low, placeTop(h, 1f, safeTop, safeBottom, listOf(DockRect(0f, 780f, 80f, 840f)), 330f, 412f), 0.01f)
        // The bias round-trips.
        val mid = placeTop(h, 0.3f, safeTop, safeBottom)
        assertEquals(0.3f, biasFor(mid + h / 2f, h, safeTop, safeBottom), 0.001f)
    }

    @Test
    fun `the start edge and RTL mirror the geometry and the side snaps to the nearer edge`() {
        assertTrue(physicallyRight(KitDockSide.End, rtl = false))
        assertFalse(physicallyRight(KitDockSide.End, rtl = true))
        assertTrue(physicallyRight(KitDockSide.Start, rtl = true))
        assertEquals(KitDockSide.End, sideAt(300f, 412f, rtl = false))
        assertEquals(KitDockSide.Start, sideAt(100f, 412f, rtl = false))
        assertEquals(KitDockSide.Start, sideAt(300f, 412f, rtl = true))
        assertEquals(12f, mirrorX(400f, 412f))
        val r = mirrorRect(DockRect(340f, 0f, 400f, 10f), 412f)
        assertEquals(DockRect(12f, 0f, 72f, 10f), r)
        // Mirroring a whole outline keeps every distance (the same shape on the other edge).
        val s = scene(412f)
        val o = DockOutline()
        buildOutline(o, s.strip, null, 0f, 412f, shoulders = true)
        for (i in 0 until o.n) assertEquals(412f - o.x[i], mirrorX(o.x[i], 412f), 1e-3f)
    }

    @Test
    fun `the list's box holds the strip, so the body is one box while it grows`() {
        val s = scene(360f)
        val p = panelBox(360f, 0f, 0f, s.strip, 900f, safeTop, safeBottom)
        assertTrue(p.top <= s.strip.top && p.bottom >= s.strip.bottom && p.left < s.strip.left)
        assertTrue(360f - p.left <= DockDims.PANEL_MAX)
        for (g in listOf(0.1f, 0.5f, 1f, 1.08f)) {
            val b = panelAt(s.strip, p, g, minOf(g, 1f))
            assertTrue(b.left <= s.strip.left && b.top <= s.strip.top && b.bottom >= s.strip.bottom, "g $g")
        }
        // The overshoot runs along the pull only: the width may pass the list's, the height never does.
        val over = panelAt(s.strip, p, 1.08f, 1.08f)
        assertTrue(over.left < p.left)
        assertEquals(p.top, over.top, 0.01f)
    }

    @Test
    fun `without shoulders the strip runs straight off the screen edge`() {
        val s = scene(412f)
        val o = DockOutline()
        buildOutline(o, s.strip, null, 0f, 412f, shoulders = false)
        assertEquals(412f, o.x[0], 0.01f)
        assertEquals(s.strip.top, o.y[0], 0.01f)
        assertEquals(412f, o.x[o.n - 1], 0.01f)
        buildOutline(o, s.strip, null, 0f, 412f, shoulders = true)
        assertTrue(o.y[0] < s.strip.top, "the shoulder starts above the strip on the screen edge")
    }

    @Test
    fun `a growing swell emerges from the strip's side without moving the strip's edge`() {
        val s = scene(412f)
        val layout = swellLayout(SwellKind.Scrub, KitDockRingState.Working, 0, 0, false, 1f, 330f)
        val placed = placeSwell(layout, s.column.slotCenter(2, s.strip.top, 0f), s.strip, safeTop, safeBottom)
        val o = DockOutline()
        for (g in listOf(0.25f, 0.6f, 1.08f)) {
            val b = grownSwell(placed.b, s.strip.left, g)
            // B is the box as it is now: growth x length out of the strip, its end corner fixed (no squeeze).
            assertEquals(s.strip.left - g * layout.length, b.left, 0.01f)
            assertEquals(layout.radius, b.radius, 0.01f, "g $g")
            buildOutline(o, s.strip, b, joinReachFor(placed.blend, g), 412f, shoulders = true)
            assertEquals(b.left, o.minX, 0.05f, "g $g: the drawn tip is B's left edge")
            assertTrue(o.x.take(o.n).all { it <= 412f + 0.01f }, "g $g: nothing past the screen edge")
        }
        // The join's reach is twice the blend once grown, and arrives with the swell.
        assertEquals(2f * placed.blend, joinReachFor(placed.blend, 1f), 0.01f)
        assertEquals(DockDims.JOIN_MIN_REACH, joinReachFor(placed.blend, 0f), 0.01f)
        assertTrue(joinReachFor(placed.blend, 0.1f) < joinReachFor(placed.blend, 0.2f))
    }

    @Test
    fun `the join field is each box's own distance away from the join, and a reach of twice the blend makes the blend's fillet`() {
        val a = DockBox(344f, 200f, 452f, 700f, 28f)
        val b = DockBox(120f, 400f, 344f + DockDims.SW_OVERLAP, 520f, 24f)
        val reach = 2f * DockDims.SW_FILLET_BLEND
        // Far from the join: the exact distance to the nearer box.
        assertEquals(-20f, dockJoinDistance(364f, 300f, a, b, reach), 0.01f)
        assertEquals(10f, dockJoinDistance(110f, 460f, a, b, reach), 0.01f)
        assertEquals(30f, dockJoinDistance(200f, 370f, a, b, reach), 0.01f)
        // On the corner's diagonal (da = db = t) the cubic kernel subtracts blend / 6: the outline crosses at t = blend / 6.
        val t = DockDims.SW_FILLET_BLEND / 6f
        assertEquals(0f, dockJoinDistance(344f - t, 400f - t, a, b, reach), 0.01f)
        // Projection lands on the zero line and walks down A's side with the body on its right.
        val f = DockJoinField().set(a.left, a.top, a.right, a.bottom, a.radius, b.left, b.top, b.right, b.bottom, b.radius, reach)
        f.project(330f, 250f)
        assertEquals(344f, f.px, 0.01f)
        assertEquals(0f, f.tx, 0.01f)
        assertEquals(1f, f.ty, 0.01f)
    }

    @Test
    fun `the pulled-out list counts sub-agent rows, collapsing more than three to alerts and one row`() {
        val kids = List(5) { KitDockChild("k$it", "k$it", if (it == 4) KitDockRingState.NeedsYou else KitDockRingState.Working) }
        assertEquals(2, panelChildRows(kids, expanded = false))
        assertEquals(5, panelChildRows(kids, expanded = true))
        assertEquals(3, panelChildRows(kids.take(3), expanded = false))
        assertEquals(listOf(4, 0, 1, 2, 3), childOrder(kids))
    }

    @Test
    fun `the list's drop index is the row under the dragged centre`() {
        val spans = mapOf(0 to (0f to 70f), 1 to (70f to 190f), 2 to (190f to 260f))
        assertEquals(1, panelDropIndex(spans, 3, 100f))
        assertEquals(2, panelDropIndex(spans, 3, 999f))
        assertEquals(0, panelDropIndex(spans, 3, -40f))
    }
}
