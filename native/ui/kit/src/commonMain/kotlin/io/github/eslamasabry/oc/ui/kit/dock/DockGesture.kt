package io.github.eslamasabry.oc.ui.kit.dock

import kotlin.math.abs
import kotlin.math.hypot

/*
 * The dock's gesture rules, pure: no Compose, no clock of its own (times come in), no geometry of its own (the hits
 * come in through [DockHits]). Edge frame, dp: x grows toward the dock's screen edge.
 *
 * How the holds and moves are told apart (the owner's approved mapping, 2026-11-11):
 * - Move along the dock (past the slop) = SCRUB: the swell shows the ring under the finger; sliding sideways into the
 *   swell picks a sub-agent or an action; lifting on a ring opens it, on a target runs it, in the swell's empty part
 *   keeps the swell open for taps.
 * - Move across the dock early (before the swell shows, mostly sideways) = PULL the list out (toward the middle) or
 *   TUCK the dock (toward the edge, with the release's speed).
 * - Hold still on a ring: after [DockRules.swellDelayMs] the swell shows; a charge arc fills; at [DockRules.holdMs]
 *   the PEEK opens (box B grows into a card with the conversation's tail). Lifting without moving keeps the peek open
 *   and changes nothing; sliding onto a target and lifting runs it; sliding back onto the ring and lifting switches.
 *   Rename is a target in the swell and the peek (and a custom accessibility action, and F2).
 * - After the peek opened, a deliberate move ALONG the strip (not into the card) picks the ring up: REORDER. Lifting
 *   reports the new index and whether it landed above the pin line. A hold first, so a scrub never reorders by mistake.
 * - The tucked nub: a tap brings the dock back; a drag along the edge MOVES it; a drag across past the screen's middle
 *   flips it to the other edge. The grip at the dock's head moves it the same way. A FLICK from the grip (letting go at
 *   [DockRules.flickSpeed] or more, mostly sideways) toward the middle opens the list and never changes edge.
 */

/** The thresholds, dp and ms. */
internal data class DockRules(
    val slop: Float = 8f,
    val swellDelayMs: Long = 120L,
    val holdMs: Long = 450L,
    /** A move counts as sideways when |dx| > [axisBias] x |dy|. */
    val axisBias: Float = 1.15f,
    /** A drag in from the nub this far brings the dock back out. */
    val nubPullIn: Float = 24f,
    /**
     * A drag from the dock's head that lets go at this sideways speed (dp/s) or more, mostly sideways, is a flick: it is
     * never a change of edge. Toward the middle it opens the list, as a flick from a ring does. The dock changes edge
     * only by a slower drag that lets go on the other half of the screen.
     */
    val flickSpeed: Float = 900f,
)

/** Where a finger came down. */
internal enum class DockZone { None, Nub, Grip, Ring, Foot, Swell, Outside }

/** What the gesture is doing now. */
internal enum class DockMode { Idle, Pressed, Scrub, Peek, Reorder, Pull, NubPress, NubDrag, GripPress, Move, FootPress, SwellTap }

/** A one-shot feedback a move or a timer asks for (haptics and appearances). */
internal enum class DockFeedback { None, ShowSwell, RingTick, AlertTick, TargetTick, Peek, PickUp }

/** What the gesture decided when it ended. */
internal sealed interface DockOutcome {
    data object None : DockOutcome

    /** Switch to ring [ring]. */
    data class Open(val ring: Int) : DockOutcome

    /** A swell or peek target was released on (the header opens the ring; a child opens that child). */
    data class Target(val ring: Int, val kind: DockTargetKind, val child: Int) : DockOutcome

    /** The swell (or the peek) stays open for taps. */
    data class Keep(val ring: Int, val peek: Boolean) : DockOutcome

    /** The swell or peek closes; nothing changes. */
    data object Close : DockOutcome

    /** A ring was dropped at [to]; [pinned] when it landed above the pin line. */
    data class Drop(val from: Int, val to: Int, val pinned: Boolean) : DockOutcome

    /** The list pull ended with the finger's sideways speed [velocity] (dp/s, positive toward the edge). */
    data class PullEnd(val velocity: Float) : DockOutcome

    data object Untuck : DockOutcome

    /** The dock was moved: to the physical side under [x] and the height [y] (edge frame). */
    data class Place(val x: Float, val y: Float) : DockOutcome

    data object NewConversation : DockOutcome
}

/** What the gesture asks of the geometry. */
internal interface DockHits {
    /** The ring under height [y] in the strip, or -1. */
    fun ringAt(y: Float): Int

    /** True when [x] lies in the strip's column (not out in the swell). */
    fun inStrip(x: Float): Boolean

    /** The swell's target at ([x], [y]), or null (only while the swell is out). */
    fun targetAt(x: Float, y: Float): DockTarget?

    /** True when ([x], [y]) is inside the swell's box. */
    fun inSwell(x: Float, y: Float): Boolean

    /** True when ring [ring] needs the person or failed (a firmer tick). */
    fun alerting(ring: Int): Boolean

    /** The index a ring dragged to [y] lands on, and whether that is above the pin line. */
    fun dropIndex(y: Float): Int

    fun pinsAt(y: Float): Boolean
}

/** The gesture state machine. One instance per dock; [down] .. [up] or [cancel] per gesture. */
internal class DockGesture(private val rules: DockRules = DockRules()) {
    var mode: DockMode = DockMode.Idle
        private set

    /** The ring the gesture is about (pressed, scrubbed, peeked, carried). */
    var ring: Int = -1
        private set

    /** The target under the finger in the swell or the peek, or null. */
    var target: DockTarget? = null
        private set

    /** The finger's current point (edge frame). */
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    /** True once the swell is showing for this gesture (after a scrub began or the swell delay passed). */
    var swellShown: Boolean = false
        private set

    /** Peek: the finger left the ring after the peek opened. */
    var movedInPeek: Boolean = false
        private set

    private var x0 = 0f
    private var y0 = 0f
    private var t0 = 0L
    private var pinnedOpen = false

    /** The swell or peek is open without a finger (after [DockOutcome.Keep]); taps on targets run them. */
    val keptOpen: Boolean get() = pinnedOpen

    fun down(zone: DockZone, x: Float, y: Float, timeMs: Long, hits: DockHits) {
        this.x = x
        this.y = y
        x0 = x
        y0 = y
        t0 = timeMs
        target = null
        movedInPeek = false
        swellShown = false
        mode =
            when (zone) {
                DockZone.Nub -> DockMode.NubPress
                DockZone.Grip -> DockMode.GripPress
                DockZone.Foot -> DockMode.FootPress
                DockZone.Swell -> if (pinnedOpen) DockMode.SwellTap else DockMode.Idle
                DockZone.Ring -> DockMode.Pressed
                DockZone.Outside, DockZone.None -> DockMode.Idle
            }
        if (mode == DockMode.Pressed) {
            pinnedOpen = false
            ring = hits.ringAt(y)
        }
        if (mode == DockMode.SwellTap) target = hits.targetAt(x, y)
    }

    /** Timers: call when [DockRules.swellDelayMs] or [DockRules.holdMs] may have passed with the finger still. */
    fun elapsed(timeMs: Long): DockFeedback {
        if (mode != DockMode.Pressed || ring < 0) return DockFeedback.None
        val held = timeMs - t0
        if (held >= rules.holdMs) {
            mode = DockMode.Peek
            swellShown = true
            return DockFeedback.Peek
        }
        if (held >= rules.swellDelayMs && !swellShown) {
            swellShown = true
            return DockFeedback.ShowSwell
        }
        return DockFeedback.None
    }

    /** The next point. Returns the feedback the move asks for. */
    fun move(x: Float, y: Float, timeMs: Long, hits: DockHits): DockFeedback {
        this.x = x
        this.y = y
        val dx = x - x0
        val dy = y - y0
        val far = hypot(dx, dy) > rules.slop
        return when (mode) {
            DockMode.Pressed -> {
                // A timer may be late: settle the hold first.
                val timed = elapsed(timeMs)
                if (mode == DockMode.Peek) return peekMove(dx, dy, far, hits, timed)
                if (!far) return timed
                val sideways = abs(dx) > rules.axisBias * abs(dy)
                if (!swellShown && sideways) {
                    mode = DockMode.Pull
                    DockFeedback.None
                } else {
                    mode = DockMode.Scrub
                    swellShown = true
                    scrubMove(hits).let { if (it == DockFeedback.None && timed == DockFeedback.ShowSwell) timed else it }
                }
            }
            DockMode.Scrub -> scrubMove(hits)
            DockMode.Peek -> peekMove(dx, dy, far, hits, DockFeedback.None)
            DockMode.Reorder -> DockFeedback.None
            DockMode.Pull, DockMode.Move -> DockFeedback.None
            DockMode.NubPress, DockMode.GripPress -> {
                if (!far) return DockFeedback.None
                mode = if (mode == DockMode.NubPress && abs(dx) > rules.axisBias * abs(dy)) DockMode.NubDrag else DockMode.Move
                DockFeedback.None
            }
            DockMode.NubDrag -> DockFeedback.None
            DockMode.FootPress -> {
                if (far) mode = DockMode.Idle
                DockFeedback.None
            }
            DockMode.SwellTap -> {
                if (far) {
                    val t = hits.targetAt(x, y)
                    if (t != target) {
                        target = t
                        if (t != null) return DockFeedback.TargetTick
                    }
                }
                DockFeedback.None
            }
            DockMode.Idle -> DockFeedback.None
        }
    }

    private fun scrubMove(hits: DockHits): DockFeedback {
        if (!hits.inStrip(x) && hits.inSwell(x, y)) {
            val t = hits.targetAt(x, y)
            if (t != target) {
                target = t
                if (t != null) return DockFeedback.TargetTick
            }
            return DockFeedback.None
        }
        target = null
        if (!hits.inStrip(x)) return DockFeedback.None
        val r = hits.ringAt(y)
        if (r >= 0 && r != ring) {
            ring = r
            return if (hits.alerting(r)) DockFeedback.AlertTick else DockFeedback.RingTick
        }
        return DockFeedback.None
    }

    private fun peekMove(dx: Float, dy: Float, far: Boolean, hits: DockHits, timed: DockFeedback): DockFeedback {
        // The slop only guards the first move: once the finger has left, coming back near the press point must still
        // drop the target it left (else lifting on the ring ran the last target crossed, such as Deny).
        if (!far && !movedInPeek) return timed
        // Still on the strip and moving along it before entering the card: pick the ring up.
        if (!movedInPeek && hits.inStrip(x) && abs(dy) > abs(dx)) {
            mode = DockMode.Reorder
            target = null
            return DockFeedback.PickUp
        }
        movedInPeek = true
        val t = if (hits.inSwell(x, y)) hits.targetAt(x, y) else null
        if (t != target) {
            target = t
            if (t != null) return DockFeedback.TargetTick
        }
        return timed
    }

    /** The finger lifted with sideways speed [velocityX] (dp/s, positive toward the edge). */
    fun up(x: Float, y: Float, velocityX: Float, hits: DockHits): DockOutcome {
        this.x = x
        this.y = y
        val m = mode
        val t = target
        mode = DockMode.Idle
        target = null
        val dx = x - x0
        return when (m) {
            DockMode.Pressed -> DockOutcome.Open(ring)
            DockMode.Scrub ->
                when {
                    t != null -> DockOutcome.Target(ring, t.kind, t.child)
                    !hits.inStrip(x) && hits.inSwell(x, y) -> keep(peek = false)
                    else -> hits.ringAt(y).let { if (it >= 0) DockOutcome.Open(it) else DockOutcome.Close }
                }
            DockMode.Peek ->
                when {
                    t != null -> DockOutcome.Target(ring, t.kind, t.child)
                    !movedInPeek -> keep(peek = true)
                    hits.inStrip(x) && hits.ringAt(y) == ring -> DockOutcome.Open(ring)
                    else -> DockOutcome.Close
                }
            DockMode.Reorder -> DockOutcome.Drop(ring, hits.dropIndex(y), hits.pinsAt(y))
            DockMode.Pull -> DockOutcome.PullEnd(velocityX)
            DockMode.NubPress -> DockOutcome.Untuck
            DockMode.NubDrag -> if (dx < -rules.nubPullIn) DockOutcome.Untuck else DockOutcome.None
            DockMode.Move ->
                // A fast sideways flick toward the middle is the list's flick, not a move to the other edge (device QA
                // 2026-11-11, run 5: the flick from the head carried the dock across and no list opened).
                if (velocityX <= -rules.flickSpeed && abs(dx) > rules.axisBias * abs(y - y0)) DockOutcome.PullEnd(velocityX)
                else DockOutcome.Place(x, y)
            DockMode.GripPress -> DockOutcome.None
            DockMode.FootPress -> DockOutcome.NewConversation
            DockMode.SwellTap -> if (t != null) DockOutcome.Target(ring, t.kind, t.child).also { pinnedOpen = false } else DockOutcome.None
            DockMode.Idle -> if (pinnedOpen) DockOutcome.Close.also { pinnedOpen = false } else DockOutcome.None
        }
    }

    private fun keep(peek: Boolean): DockOutcome {
        pinnedOpen = true
        return DockOutcome.Keep(ring, peek)
    }

    /** An alert opened the swell for ring [ring] without a finger: taps on its targets run them, as after [DockOutcome.Keep]. */
    fun keepOpenForAlert(ring: Int) {
        this.ring = ring
        pinnedOpen = true
    }

    /** The swell or peek that was kept open is closed by the host (a tap outside, Back, a model change). */
    fun closeKept() {
        pinnedOpen = false
    }

    fun cancel() {
        mode = DockMode.Idle
        target = null
    }

    /** True when the gesture so far is a press that has not moved (the hold timer should keep running). */
    val holding: Boolean get() = mode == DockMode.Pressed

    /** How long the current press has been held, for the charge arc. */
    fun heldMs(timeMs: Long): Long = if (mode == DockMode.Pressed) timeMs - t0 else 0L
}

/** Where a released list pull settles: open the list, tuck the dock, or rest. Projected with the release speed. */
internal enum class PullSettle { Panel, Rest, Tuck }

/**
 * The pull's end: the strip's width [out] now (dp) plus where the release speed [velocity] (dp/s toward the edge)
 * would carry it in 0.16 s. Past half the way to the list's width [panel] opens the list; below 62 % of the rest width
 * tucks (unless the pull began from the open list, [fromPanel]).
 */
internal fun pullSettle(out: Float, velocity: Float, panel: Float, fromPanel: Boolean = false): PullSettle {
    val projected = out - velocity * 0.16f
    return when {
        projected > DockDims.REST_W + (panel - DockDims.REST_W) * 0.5f -> PullSettle.Panel
        projected < DockDims.REST_W * 0.62f && !fromPanel -> PullSettle.Tuck
        else -> PullSettle.Rest
    }
}

/** The strip's width while pulled by [drag] dp toward the middle from [start]: rubber beyond the list and the nub. */
internal fun pullOut(start: Float, drag: Float, panel: Float): Float {
    val raw = start + drag
    return when {
        raw > panel -> panel + (raw - panel) * 0.22f
        raw < DockDims.NUB_W -> (DockDims.NUB_W - (DockDims.NUB_W - raw) * 0.3f).coerceAtLeast(3f)
        else -> raw
    }
}
