package io.github.eslamasabry.oc.ui.kit.dock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The dock's gesture rules: scrub (moving) shows the swell; a quick sideways move pulls the list or tucks; a still hold
 * opens the peek; after the peek a move along the strip reorders; the nub and the grip move the dock.
 */
class DockGestureTest {
    /** A strip from x 344 to 412 with five rings 64 dp apart from y 100; the swell from x 120 to 344, y 200..320. */
    private class Hits(var swellOpen: Boolean = false) : DockHits {
        val swell = swellLayout(SwellKind.Scrub, KitDockRingState.NeedsYou, 3, 0, false, 1f, 224f)
        val swellLeft = 344f - swell.length
        val swellTop = 200f

        override fun ringAt(y: Float): Int = ((y - 100f) / 64f).toInt().takeIf { y >= 100f && it in 0..4 } ?: -1

        override fun inStrip(x: Float): Boolean = x >= 340f

        override fun targetAt(x: Float, y: Float): DockTarget? = if (swellOpen) swell.targetAt(x - swellLeft, y - swellTop) else null

        override fun inSwell(x: Float, y: Float): Boolean = swellOpen && x >= swellLeft && x <= 344f && y >= swellTop && y <= swellTop + swell.height

        override fun alerting(ring: Int): Boolean = ring == 3

        override fun dropIndex(y: Float): Int = ((y - 100f) / 64f).toInt().coerceIn(0, 4)

        override fun pinsAt(y: Float): Boolean = y < 110f

        fun centreOf(kind: DockTargetKind): Pair<Float, Float> {
            val r = swell.targets.first { it.kind == kind }.rect
            return (swellLeft + (r.left + r.right) / 2f) to (swellTop + (r.top + r.bottom) / 2f)
        }
    }

    private val x = 378f

    @Test
    fun `a tap on a ring opens it`() {
        val h = Hits()
        val g = DockGesture()
        g.down(DockZone.Ring, x, 132f, 0L, h)
        assertEquals(DockOutcome.Open(0), g.up(x, 133f, 0f, h))
    }

    @Test
    fun `moving along the dock scrubs, ticks once per ring crossed and firmer on an alert, and opens the ring under the lift`() {
        val h = Hits()
        val g = DockGesture()
        g.down(DockZone.Ring, x, 132f, 0L, h)
        assertEquals(DockFeedback.None, g.move(x, 136f, 16L, h), "within the slop nothing happens")
        assertEquals(DockMode.Pressed, g.mode)
        g.move(x, 150f, 32L, h)
        assertEquals(DockMode.Scrub, g.mode)
        assertTrue(g.swellShown)
        assertEquals(DockFeedback.RingTick, g.move(x, 200f, 48L, h))
        assertEquals(DockFeedback.None, g.move(x, 210f, 64L, h), "no tick inside the same ring")
        g.move(x, 260f, 80L, h)
        assertEquals(DockFeedback.AlertTick, g.move(x, 300f, 96L, h))
        assertEquals(DockOutcome.Open(3), g.up(x, 300f, 0f, h))
    }

    @Test
    fun `an early sideways move pulls the list (or tucks) instead of scrubbing`() {
        val h = Hits()
        val g = DockGesture()
        g.down(DockZone.Ring, x, 132f, 0L, h)
        g.move(x - 30f, 135f, 40L, h)
        assertEquals(DockMode.Pull, g.mode)
        val end = g.up(x - 120f, 135f, -900f, h)
        assertIs<DockOutcome.PullEnd>(end)
        assertEquals(-900f, end.velocity)
    }

    @Test
    fun `once the swell shows, a sideways move goes into the swell, not into a pull`() {
        val h = Hits()
        val g = DockGesture()
        g.down(DockZone.Ring, x, 260f, 0L, h)
        assertEquals(DockFeedback.ShowSwell, g.elapsed(130L))
        h.swellOpen = true
        g.move(x - 30f, 262f, 200L, h)
        assertEquals(DockMode.Scrub, g.mode)
    }

    @Test
    fun `sliding onto a swell target and lifting runs it; a mini ring opens that sub-agent`() {
        val h = Hits(swellOpen = true)
        val g = DockGesture()
        g.down(DockZone.Ring, x, 260f, 0L, h)
        g.move(x, 280f, 20L, h)
        val (ax, ay) = h.centreOf(DockTargetKind.Allow)
        assertEquals(DockFeedback.TargetTick, g.move(ax, ay, 60L, h))
        assertEquals(DockOutcome.Target(2, DockTargetKind.Allow, -1), g.up(ax, ay, 0f, h))

        val g2 = DockGesture()
        g2.down(DockZone.Ring, x, 260f, 0L, h)
        g2.move(x, 280f, 20L, h)
        val child = h.swell.targets.first { it.kind == DockTargetKind.Child && it.child == 1 }.rect
        val cx = h.swellLeft + (child.left + child.right) / 2f
        val cy = h.swellTop + (child.top + child.bottom) / 2f
        g2.move(cx, cy, 60L, h)
        assertEquals(DockOutcome.Target(2, DockTargetKind.Child, 1), g2.up(cx, cy, 0f, h))
    }

    @Test
    fun `lifting in the swell's empty part keeps it open for taps; a tap on a target then runs it`() {
        val h = Hits(swellOpen = true)
        val g = DockGesture()
        g.down(DockZone.Ring, x, 260f, 0L, h)
        g.move(x, 280f, 20L, h)
        val emptyX = h.swellLeft + 3f
        val emptyY = h.swellTop + 3f
        g.move(emptyX, emptyY, 60L, h)
        assertEquals(DockOutcome.Keep(2, peek = false), g.up(emptyX, emptyY, 0f, h))
        assertTrue(g.keptOpen)
        val (rx, ry) = h.centreOf(DockTargetKind.Rename)
        g.down(DockZone.Swell, rx, ry, 1000L, h)
        assertEquals(DockMode.SwellTap, g.mode)
        assertEquals(DockOutcome.Target(2, DockTargetKind.Rename, -1), g.up(rx, ry, 0f, h))
    }

    @Test
    fun `a still hold shows the swell, then opens the peek; lifting without moving keeps the peek and changes nothing`() {
        val h = Hits()
        val g = DockGesture()
        g.down(DockZone.Ring, x, 132f, 0L, h)
        assertEquals(DockFeedback.None, g.elapsed(60L))
        assertEquals(DockFeedback.ShowSwell, g.elapsed(DockRules().swellDelayMs))
        assertEquals(DockFeedback.Peek, g.elapsed(DockRules().holdMs))
        assertEquals(DockMode.Peek, g.mode)
        assertEquals(DockOutcome.Keep(0, peek = true), g.up(x, 133f, 0f, h))
    }

    @Test
    fun `in the peek, Open switches, the ring switches after leaving it, anywhere else closes`() {
        val h = Hits(swellOpen = true)
        val peek = DockGesture()
        peek.down(DockZone.Ring, x, 260f, 0L, h)
        peek.elapsed(500L)
        val (ox, oy) = h.centreOf(DockTargetKind.Header)
        peek.move(ox, oy, 600L, h)
        assertEquals(DockOutcome.Target(2, DockTargetKind.Header, -1), peek.up(ox, oy, 0f, h))

        val back = DockGesture()
        back.down(DockZone.Ring, x, 260f, 0L, h)
        back.elapsed(500L)
        back.move(200f, 330f, 600L, h)
        back.move(x, 262f, 700L, h)
        assertEquals(DockOutcome.Open(2), back.up(x, 262f, 0f, h))

        val away = DockGesture()
        away.down(DockZone.Ring, x, 260f, 0L, h)
        away.elapsed(500L)
        away.move(20f, 700f, 600L, h)
        assertEquals(DockOutcome.Close, away.up(20f, 700f, 0f, h))
    }

    @Test
    fun `after the peek, a move along the strip picks the ring up; the drop reports the index and the pin`() {
        val h = Hits()
        val g = DockGesture()
        g.down(DockZone.Ring, x, 260f, 0L, h)
        g.elapsed(500L)
        assertEquals(DockFeedback.PickUp, g.move(x, 230f, 600L, h))
        assertEquals(DockMode.Reorder, g.mode)
        g.move(x, 140f, 700L, h)
        assertEquals(DockOutcome.Drop(2, 0, pinned = false), g.up(x, 140f, 0f, h))

        val pin = DockGesture()
        pin.down(DockZone.Ring, x, 260f, 0L, h)
        pin.elapsed(500L)
        pin.move(x, 230f, 600L, h)
        assertEquals(DockOutcome.Drop(2, 0, pinned = true), pin.up(x, 104f, 0f, h))
    }

    @Test
    fun `a scrub never reorders: only a hold first picks a ring up`() {
        val h = Hits()
        val g = DockGesture()
        g.down(DockZone.Ring, x, 260f, 0L, h)
        g.move(x, 140f, 30L, h)
        assertEquals(DockMode.Scrub, g.mode)
        assertIs<DockOutcome.Open>(g.up(x, 140f, 0f, h))
    }

    @Test
    fun `the nub: a tap brings the dock back, a drag along the edge moves it, a drag in brings it out`() {
        val h = Hits()
        val tap = DockGesture()
        tap.down(DockZone.Nub, 405f, 300f, 0L, h)
        assertEquals(DockOutcome.Untuck, tap.up(405f, 301f, 0f, h))

        val move = DockGesture()
        move.down(DockZone.Nub, 405f, 300f, 0L, h)
        move.move(405f, 360f, 50L, h)
        assertEquals(DockMode.Move, move.mode)
        assertEquals(DockOutcome.Place(405f, 500f), move.up(405f, 500f, 0f, h))

        val pull = DockGesture()
        pull.down(DockZone.Nub, 405f, 300f, 0L, h)
        pull.move(370f, 302f, 50L, h)
        assertEquals(DockMode.NubDrag, pull.mode)
        assertEquals(DockOutcome.Untuck, pull.up(360f, 302f, 0f, h))
    }

    @Test
    fun `the grip moves the dock, and across the screen it reports the far side`() {
        val h = Hits()
        val g = DockGesture()
        g.down(DockZone.Grip, x, 90f, 0L, h)
        g.move(x - 20f, 120f, 40L, h)
        assertEquals(DockMode.Move, g.mode)
        assertEquals(DockOutcome.Place(40f, 400f), g.up(40f, 400f, 0f, h))
        assertEquals(KitDockSide.Start, sideAt(40f, 412f, rtl = false))
    }

    @Test
    fun `the foot's tap starts a new conversation; a drag off it does nothing`() {
        val h = Hits()
        val g = DockGesture()
        g.down(DockZone.Foot, x, 500f, 0L, h)
        assertEquals(DockOutcome.NewConversation, g.up(x, 501f, 0f, h))
        g.down(DockZone.Foot, x, 500f, 0L, h)
        g.move(x - 40f, 500f, 30L, h)
        assertEquals(DockOutcome.None, g.up(x - 40f, 500f, 0f, h))
    }

    @Test
    fun `a pull settles by where its release speed carries it`() {
        val panel = 300f
        assertEquals(PullSettle.Panel, pullSettle(200f, 0f, panel))
        assertEquals(PullSettle.Panel, pullSettle(120f, -900f, panel), "a fling toward the middle opens the list")
        assertEquals(PullSettle.Rest, pullSettle(100f, 0f, panel))
        assertEquals(PullSettle.Tuck, pullSettle(60f, 600f, panel), "a flick to the edge tucks")
        assertEquals(PullSettle.Rest, pullSettle(30f, 600f, panel, fromPanel = true), "closing the list never tucks")
        assertEquals(300f + 100f * 0.22f, pullOut(68f, 332f, panel), 0.01f)
        assertTrue(pullOut(68f, -100f, panel) >= 3f)
    }

    @Test
    fun `an alert's swell takes taps like a kept one`() {
        val h = Hits(swellOpen = true)
        val g = DockGesture()
        g.keepOpenForAlert(3)
        val (sx, sy) = h.centreOf(DockTargetKind.Deny)
        g.down(DockZone.Swell, sx, sy, 0L, h)
        assertEquals(DockOutcome.Target(3, DockTargetKind.Deny, -1), g.up(sx, sy, 0f, h))
    }
}
