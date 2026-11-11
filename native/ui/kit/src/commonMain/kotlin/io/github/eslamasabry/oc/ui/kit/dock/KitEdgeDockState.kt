package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * The dock's body for the host, in pixels of the dock area, PHYSICAL (already mirrored for a dock on the left).
 * Box A is the strip, box B the swell, the peek card or the list. Both boxes are AS THEY ARE NOW: B's width already
 * follows the growth (B stands growth x its length out of the strip, its corner radius fixed), so the host scales
 * nothing. The glass owner feeds the two boxes, their radii and [joinReach] into the joined-glass field; the Solid face
 * fills that same field's zero line ([DockJoinField]), so both draw one outline at every moment. Read it with
 * [KitEdgeDockState.body] in a draw or layout phase: it follows the motion frame by frame without recomposition.
 */
public class KitDockBody {
    public var stripLeft: Float = 0f
    public var stripTop: Float = 0f
    public var stripRight: Float = 0f
    public var stripBottom: Float = 0f
    public var stripRadius: Float = 0f

    /** True while box B is out (swell, peek or list). */
    public var hasSecond: Boolean = false
    public var secondLeft: Float = 0f
    public var secondTop: Float = 0f
    public var secondRight: Float = 0f
    public var secondBottom: Float = 0f
    public var secondRadius: Float = 0f

    /** True when B is the list: B then holds A entirely and the body is B alone (no fillet). */
    public var secondIsList: Boolean = false

    /** The fillet's tightest radius now, px (the design targets: 22.7 dp for the swell, 33.4 dp for the peek). */
    public var filletRadius: Float = 0f

    /**
     * The fillet's EFFECTIVE BLEND WIDTH now, px: the `k` of the cubic smooth minimum `min(da, db) - h^3 k / 6` where
     * B's top (or bottom) edge meets A's side at a right angle. Not the join's reach: see [joinReach].
     */
    public var filletBlend: Float = 0f

    /**
     * The reach to hand the joined-glass field (`KitLiquidJoin`), px: 2 x [filletBlend]. The field blends with `k =
     * reach x facing x inside`, and `facing` (0.5 - 0.5 x the dot of the two boxes' outward directions) is 0.5 all
     * along a right-angle fillet, `inside` 1; so the reach that yields this fillet is twice its blend width. It rises
     * from nothing over the first fifth of B's growth (the fillet arrives with the swell). 0 when B is the list.
     */
    public var joinReach: Float = 0f

    /** 0 at rest, 1 fully grown (B's width already follows it; for timing words and the like). */
    public var growth: Float = 0f

    /** The kit draws concave shoulders into the screen edge (off under Glass for now). */
    public var shoulders: Boolean = false

    /** The dock is on the physical right edge. */
    public var onRight: Boolean = true
}

/** One frame of the body in the edge frame, dp; reused (no allocation per frame). */
internal class DockFrame {
    var edgeX = 0f
    var aLeft = 0f
    var aTop = 0f
    var aRight = 0f
    var aBottom = 0f
    var aRadius = 0f
    var hasB = false
    var panel = false
    /** B's left edge now (the swell's tip follows its growth). */
    var bLeft = 0f
    /** B's left edge at full growth: what the swell's words and targets are laid out from. */
    var bRestLeft = 0f
    var bTop = 0f
    var bRight = 0f
    var bBottom = 0f
    var bRadius = 0f
    var grow = 0f
    /** The fitted fillet's effective blend width (dp). */
    var blend = 0f
    /** The join field's reach now (dp; see [KitDockBody.joinReach]). */
    var joinReach = 0f
}

/**
 * The dock's state: its motion values (read in draw and layout phases only, so a moving dock never recomposes), what is
 * open, and the gesture. Hoist it with [rememberKitEdgeDockState] when the host reads [body] or [gestureExclusion].
 */
@Stable
public class KitEdgeDockState internal constructor() {
    // Layout, written in composition when the window, insets, model or text scale change (not per frame). Snapshot
    // state: the rings, the "+", the swell, the list and the body read them in layout and draw lambdas, and every one of
    // those must run again when they change, or the rings stay where the old area put them while the body (drawn again
    // for another reason, such as the glass behind it) follows the new one (device QA 2026-11-11, M1: the rings over the
    // chat's header, the body below them, after the keyboard closed). Writing an equal value invalidates nothing.
    internal var widthDp by mutableFloatStateOf(0f)
    internal var heightDp by mutableFloatStateOf(0f)
    internal var safeTop by mutableFloatStateOf(0f)
    internal var safeBottom by mutableFloatStateOf(0f)
    internal var edgeInset by mutableFloatStateOf(0f)
    internal var farInset by mutableFloatStateOf(0f)
    /**
     * The dock's physical side. Snapshot state: the rings, the "+", the swell, the peek and the list are placed in
     * layout lambdas that read it, so a change of side (the dock dragged to the other edge) places them again with the
     * body (device QA 2026-11-11: the body moved to the left edge while the rings and the "+" stayed on the right).
     */
    internal var onRight by mutableStateOf(true)
    internal var density by mutableFloatStateOf(1f)
    internal var textScale by mutableFloatStateOf(1f)
    internal var column: DockColumn by mutableStateOf(DockColumn(0, 0, 0, DockDims.PITCH, 0f))
    internal var keepClear: List<DockRect> = emptyList()
    internal var panelContent by mutableFloatStateOf(0f)
    internal var reduced = false
    internal var shoulders = true
    internal var rings: List<KitDockRing> by mutableStateOf(emptyList())
    internal var scope: CoroutineScope? = null
    internal var haptic: (KitHaptic) -> Unit = {}
    internal var intents: KitDockIntents = object : KitDockIntents {}
    /** The host's words (for the swell's layout: a blank state line takes no row). */
    internal var words: KitDockWords? = null

    // Motion, dp.
    internal val out = Animatable(DockDims.REST_W)
    internal val top = Animatable(0f)
    internal val lean = Animatable(0f)
    internal val scroll = Animatable(0f)
    internal val swellGrow = Animatable(0f)
    /** The swell's centre height in the column's CONTENT ([contentY]): it rides with the strip when the area moves it. */
    internal val swellCenter = Animatable(0f)
    internal val swellH = Animatable(DockDims.RENAME_H)
    internal val swellLen = Animatable(0f)
    internal val panelX = Animatable(0f)
    internal val panelY = Animatable(0f)
    internal val charge = Animatable(0f)
    internal val ride = Animatable(0f)
    internal val pulse = Animatable(1f)
    internal var pulseRing = -1

    /** The finger's height while a ring is carried (reorder) or the dock is moved. */
    internal var carryY by mutableFloatStateOf(0f)

    // What is open (changes recompose; they change on decisions, not per frame).
    internal var swell by mutableStateOf<SwellLayout?>(null)
    internal var swellRing by mutableIntStateOf(-1)
    internal var target by mutableStateOf<DockTarget?>(null)
    internal var renaming by mutableStateOf<String?>(null)

    /** The lines the rename field asks for (what the name measures, 1..[DockDims.RENAME_LINES]); the swell's height follows. */
    internal var renameLines by mutableIntStateOf(1)
    internal var panelShown by mutableStateOf(false)
    internal var panelOpen by mutableStateOf(false)
    internal var carrying by mutableIntStateOf(-1)
    internal var moving by mutableStateOf(false)
    internal var placed = false
    internal val expanded = mutableStateOf(emptySet<String>())
    internal var hiddenOpen by mutableStateOf(false)

    internal val gesture = DockGesture()
    internal val frame = DockFrame()
    internal val outline = DockOutline()
    internal val pulseDx = FloatArray(outline.x.size)
    internal val pulseDy = FloatArray(outline.x.size)

    /** The ring under a still press (its charge arc shows), or -1. */
    internal var pressRing by mutableIntStateOf(-1)

    /** Where a carried ring would land now (the others make room), or -1. */
    internal var carryTo by mutableIntStateOf(-1)

    /** The touch area's top-left (physical dp), written by its layout. */
    internal var hitLeft = 0f
    internal var hitTop = 0f

    /** The rename field's text as typed (saved on a tap outside). */
    internal var renameText = ""
    private var panelCache: DockBox? = null
    private val panelKey = FloatArray(PANEL_KEY_SIZE) { Float.NaN }
    private var alertJob: Job? = null
    private var swellJob: Job? = null
    private var panelJob: Job? = null

    /** Rises after the person drops the dock somewhere, so the placement settles again even if the model ignores it. */
    internal var placeGeneration by mutableIntStateOf(0)
    private var pullStart = DockDims.REST_W
    private var pullFromPanel = false

    /** True while anything is open over the content (a tap outside closes it). */
    internal val anythingOpen: Boolean get() = swell != null || panelOpen || renaming != null

    /** The strip's column top now (follows the placement, or the finger while the dock is moved). */
    internal val columnTop: Float get() = top.value

    /**
     * The column's scroll now, inside what the column can scroll. Everything that places or hits a ring reads this,
     * never [scroll] itself: a column that grew back (the keyboard closed, the area grew) has nothing left to scroll, and
     * a scroll kept from the small column put the rings above the body (device QA 2026-11-11, M1). The [scroll]
     * animatable settles to it a frame later; the rings never wait for that.
     */
    internal val scrollNow: Float get() = scroll.value.coerceIn(0f, column.maxScroll)

    /** A height in the area (dp) as a height in the column's content (its scroll and its top taken out), and back. */
    internal fun contentY(y: Float): Float = y - top.value + scrollNow

    internal fun areaY(contentY: Float): Float = contentY + top.value - scrollNow

    /**
     * The band the rings show in (dp, area): the column's viewport, a few dp more each way for the last name's
     * descender. Rings scrolled out of it are cut at its edges, so a scrolled column never draws rings over the head,
     * the "+" or past the body (device QA 2026-11-11, M1 and M3: rings above the body, a "+" amid the rings).
     */
    internal fun ringBandTop(): Float = top.value + DockDims.HEAD - RING_BAND_SPILL

    internal fun ringBandBottom(): Float = top.value + DockDims.HEAD + column.viewportHeight + RING_BAND_SPILL

    /** Fills [frame] from the motion values. Reads animated values: call only in draw and layout phases. */
    internal fun computeFrame(f: DockFrame = frame): DockFrame {
        val w = widthDp
        f.edgeX = w - edgeInset
        val o = out.value + lean.value
        val cTop = top.value
        val cBottom = cTop + column.height
        f.aLeft = w - edgeInset - o
        f.aTop = cTop
        f.aRight = w + DockDims.OVERHANG
        f.aBottom = cBottom
        // The visible width (to the screen edge) bounds the corner, as the outline draws it.
        f.aRadius = min(DockDims.CORNER, min((f.edgeX - f.aLeft) / 2f, (cBottom - cTop) / 2f)).coerceAtLeast(0f)
        f.hasB = false
        f.panel = false
        f.grow = 0f
        f.blend = 0f
        f.joinReach = 0f
        val px = panelX.value
        val py = panelY.value
        val layout = swell
        if (px > 0.001f || py > 0.001f) {
            val p = panelBoxNow(cTop)
            val y = py.coerceIn(0f, 1f)
            f.hasB = true
            f.panel = true
            f.bLeft = min(f.aLeft, lerp(f.aLeft, p.left, px))
            f.bRestLeft = p.left
            f.bTop = lerp(f.aTop, p.top, y)
            f.bBottom = lerp(f.aBottom, p.bottom, y)
            // The pane comes off the screen edge as it grows (a strip is attached; a whole list stands a gutter in). The
            // strip's box rides inside it: nothing of the strip is left at the edge once the list is whole.
            f.bRight = listOuterSide(w, px)
            f.aRight = min(f.aRight, f.bRight)
            f.bRadius = lerp(f.aRadius, p.radius, y)
            f.grow = px
        } else if (layout != null && swellGrow.value > 0.001f) {
            val h = swellH.value
            val len = swellLen.value
            val k = filletFor(len, h)
            // The swell's height is kept in the column's content, so it moves with the strip when the area moves it.
            val sTop = swellTop(h, k, swellCenterFor(areaY(swellCenter.value)), cTop, cBottom, safeTop, safeBottom)
            val g = swellGrow.value
            val gc = g.coerceIn(0f, 1f)
            val extTop = extendedStripTop(sTop, k, cTop, safeTop)
            val extBottom = extendedStripBottom(sTop + h, k, cBottom, safeBottom)
            f.aTop = lerp(cTop, extTop, gc)
            f.aBottom = lerp(cBottom, extBottom, gc)
            f.hasB = true
            // B as it is now: growth x length out of the strip, the end corner fixed (no squeeze).
            f.bRestLeft = f.aLeft - len
            f.bLeft = grownLeft(f.aLeft, len, g)
            f.bTop = sTop
            f.bBottom = sTop + h
            f.bRight = f.aLeft + DockDims.SW_OVERLAP
            f.bRadius = swellRadius(h, f.bRight - f.bLeft)
            f.blend = fittedFillet(k, sTop, sTop + h, f.aTop, f.aBottom)
            f.joinReach = joinReachFor(f.blend, g)
            f.grow = g
        }
        return f
    }

    private fun panelBoxNow(cTop: Float): DockBox {
        // Keyed on everything the box depends on: a list kept from another area (the keyboard up, then closed) ran
        // under the message box or past the far edge.
        val k = panelKey
        val cached = panelCache
        if (
            cached != null && k[0] == cTop && k[1] == panelContent && k[2] == widthDp && k[3] == edgeInset && k[4] == farInset &&
            k[5] == safeTop && k[6] == safeBottom && k[7] == column.height
        ) return cached
        val strip = DockBox(widthDp - edgeInset - DockDims.REST_W, cTop, widthDp + DockDims.OVERHANG, cTop + column.height, DockDims.CORNER)
        val p = panelBox(widthDp, edgeInset, farInset, strip, panelContent, safeTop, safeBottom, column.foot)
        panelCache = p
        k[0] = cTop
        k[1] = panelContent
        k[2] = widthDp
        k[3] = edgeInset
        k[4] = farInset
        k[5] = safeTop
        k[6] = safeBottom
        k[7] = column.height
        return p
    }

    /**
     * Builds the body's outline from the frame now into [outline]: the path the Solid face fills and the failure
     * pulse runs along. The same boxes and reach as [body]; the swell's part is the join field's zero line.
     */
    internal fun bodyOutline(f: DockFrame = computeFrame()): DockOutline {
        buildOutline(
            outline, f.aLeft, f.aTop, f.aRight, f.aBottom, f.aRadius,
            f.hasB, f.panel, f.bLeft, f.bTop, f.bRight, f.bBottom, f.bRadius,
            f.joinReach, f.edgeX, shoulders,
        )
        return outline
    }

    /** The list's box at full size (edge frame, dp). */
    internal fun panelTarget(): DockBox = panelBoxNow(top.value)

    /** Edge-frame x to physical x (dp). */
    internal fun phys(x: Float): Float = if (onRight) x else mirrorX(x, widthDp)

    /**
     * The body now, physical px (see [KitDockBody]). Reads animated values: call in a draw or layout phase.
     * Writes into [into] (no allocation per frame).
     */
    public fun body(into: KitDockBody = KitDockBody()): KitDockBody {
        val f = computeFrame()
        val d = density
        fun x(v: Float) = phys(v) * d
        val aL = x(f.aLeft)
        val aR = x(f.aRight)
        into.stripLeft = min(aL, aR)
        into.stripRight = max(aL, aR)
        into.stripTop = f.aTop * d
        into.stripBottom = f.aBottom * d
        into.stripRadius = f.aRadius * d
        into.hasSecond = f.hasB
        if (f.hasB) {
            val bL = x(f.bLeft)
            val bR = x(f.bRight)
            into.secondLeft = min(bL, bR)
            into.secondRight = max(bL, bR)
            into.secondTop = f.bTop * d
            into.secondBottom = f.bBottom * d
            into.secondRadius = f.bRadius * d
        }
        into.secondIsList = f.panel
        val blendNow = f.joinReach / DockDims.JOIN_REACH_PER_BLEND
        into.joinReach = if (f.hasB && !f.panel) f.joinReach * d else 0f
        into.filletBlend = if (f.hasB && !f.panel) blendNow * d else 0f
        into.filletRadius = into.filletBlend * DockDims.FILLET_RADIUS_PER_BLEND
        into.growth = f.grow
        into.shoulders = shoulders
        into.onRight = onRight
        return into
    }

    /**
     * The rectangle (px, dock-area coordinates) the host should exclude from the system back gesture: the scrub zone
     * along the dock's edge, at most 200 dp tall (Android honours at most 200 dp per edge), centred on the column; the
     * nub's touch band when tucked. Null when nothing is shown. Apply it with `Modifier.systemGestureExclusion`.
     */
    public fun gestureExclusion(): Rect? {
        if (column.count == 0) return null
        val d = density
        val tucked = out.value < DockDims.REST_W * 0.5f
        val w = if (tucked) DockDims.NUB_TOUCH else DockDims.REST_W
        val h = min(EXCLUSION_MAX_DP, column.height)
        val cy = top.value + column.height / 2f
        val l = phys(widthDp - edgeInset - w)
        val r = phys(widthDp)
        return Rect(min(l, r) * d, (cy - h / 2f) * d, max(l, r) * d, (cy + h / 2f) * d)
    }

    // -----------------------------------------------------------------------------------------------------------
    // Hit-testing (edge frame, dp).

    internal val hits: DockHits =
        object : DockHits {
            override fun ringAt(y: Float): Int = column.ringAt(y, top.value, scrollNow)

            override fun inStrip(x: Float): Boolean = x >= frame.aLeft - 4f

            override fun targetAt(x: Float, y: Float): DockTarget? {
                val l = swell ?: return null
                if (swellGrow.value < 0.7f) return null
                return l.targetAt(x - frame.bRestLeft, y - frame.bTop)
            }

            override fun inSwell(x: Float, y: Float): Boolean =
                swell != null && frame.hasB && !frame.panel && x >= frame.bLeft && x <= frame.aLeft && y >= frame.bTop && y <= frame.bBottom

            override fun alerting(ring: Int): Boolean =
                rings.getOrNull(ring)?.state.let { it == KitDockRingState.NeedsYou || it == KitDockRingState.Failed }

            override fun dropIndex(y: Float): Int = column.reorderIndex(y, top.value, scrollNow)

            override fun pinsAt(y: Float): Boolean = column.pinsAt(y, top.value, scrollNow)
        }

    /** The zone under a finger at ([x], [y]) (edge frame). */
    internal fun zoneAt(x: Float, y: Float): DockZone {
        computeFrame()
        val f = frame
        val tucked = out.value < DockDims.REST_W * 0.5f
        if (tucked) {
            val cTop = top.value
            return if (x >= widthDp - edgeInset - DockDims.NUB_TOUCH && y >= cTop - 12f && y <= cTop + column.height + 12f) DockZone.Nub else DockZone.Outside
        }
        if (swell != null && hits.inSwell(x, y)) return DockZone.Swell
        if (x < f.aLeft - 4f || y < f.aTop - 8f || y > f.aBottom + 8f) return DockZone.Outside
        val cTop = top.value
        if (y < cTop + DockDims.HEAD) return DockZone.Grip
        if (y >= cTop + DockDims.HEAD + column.viewportHeight) return if (column.foot > 0f) DockZone.Foot else DockZone.Outside
        return if (column.ringAt(y, cTop, scrollNow) >= 0) DockZone.Ring else DockZone.Outside
    }

    // -----------------------------------------------------------------------------------------------------------
    // Reactions. Everything animates through [scope]; reduced motion turns travel into an instant change.

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope?.launch(block = block)
    }

    /** The ring centre y (slot centre) of ring [i] now. */
    internal fun slotY(i: Int): Float = column.slotCenter(i, top.value, scrollNow)

    /** The swell's centre for a finger at [y]: the finger's height, clamped to the first and last visible ring. */
    internal fun swellCenterFor(y: Float): Float {
        if (column.count == 0) return y
        val first = column.slotCenter(0, top.value, scrollNow).coerceAtLeast(top.value + DockDims.HEAD + column.pitch / 2f)
        val last = column.slotCenter(column.count - 1, top.value, scrollNow).coerceAtMost(top.value + DockDims.HEAD + column.viewportHeight - column.pitch / 2f)
        return y.coerceIn(min(first, last), max(first, last))
    }

    internal fun layoutFor(kind: SwellKind, ring: KitDockRing): SwellLayout =
        swellLayout(
            kind = kind,
            state = ring.state,
            childTotal = ring.childTotal,
            peekLines = ring.peek.size,
            server = ring.server != null,
            s = textScale,
            maxLength = widthDp - edgeInset - DockDims.REST_W - farInset - DockDims.GUTTER,
            stateLine = words?.hasStateLine(ring) ?: true,
            renameLines = renameLines,
            maxHeight = safeBottom - safeTop,
        )

    /** Opens (or retargets) box B for ring [i] as [kind]; B grows out with one small overshoot. */
    internal fun openSwell(kind: SwellKind, i: Int, centerY: Float = slotY(i)) {
        val ring = rings.getOrNull(i) ?: return
        val l = layoutFor(kind, ring)
        val first = swell == null || swellGrow.value < 0.03f
        swell = l
        swellRing = i
        alertJob?.cancel()
        swellJob?.cancel()
        swellJob = scope?.launch {
            if (first) {
                swellCenter.snapTo(contentY(swellCenterFor(centerY)))
                swellH.snapTo(l.height)
                swellLen.snapTo(l.length)
            }
            // One overshoot along the travel: the growth drops (B's width is growth x length); the length and the
            // height fill calmly, so the tip passes its rest once by the drop's ~8 %, never by a product of two.
            launch { swellH.animateTo(l.height, DockSprings.fill(reduced)) }
            launch { swellLen.animateTo(l.length, DockSprings.fill(reduced)) }
            swellGrow.animateTo(1f, DockSprings.drop(reduced))
        }
    }

    /** Closes box B: the words go first, the body follows a beat later, nothing bounces. */
    internal fun closeSwell() {
        alertJob?.cancel()
        gesture.closeKept()
        target = null
        swellJob?.cancel()
        swellJob = scope?.launch {
            if (!reduced) delay(70)
            swellGrow.animateTo(0f, DockSprings.close(reduced))
            if (swellGrow.value < 0.01f) {
                swell = null
                swellRing = -1
            }
        }
        launch { ride.animateTo(0f, DockSprings.close(reduced)) }
    }

    /** The carried ring's card rides to the drop place [to] (the slot the carried ring stands in). */
    private fun followCarry(to: Int) {
        if (swell == null) return
        val c = contentY(swellCenterFor(slotY(to)))
        launch { swellCenter.animateTo(c, DockSprings.follow(reduced)) }
    }

    /** The swell follows the finger's height while scrubbing (the finger is the spring). */
    internal fun followFinger(y: Float) {
        val c = contentY(swellCenterFor(y))
        launch { swellCenter.snapTo(c) }
    }

    internal fun startCharge() {
        launch {
            charge.snapTo(0f)
            if (!reduced) delay(90)
            charge.animateTo(1f, DockSprings.timed(reduced, DockRules().holdMs.toInt() - 90))
        }
    }

    internal fun stopCharge() {
        launch { charge.animateTo(0f, DockSprings.timed(reduced, 120)) }
    }

    internal fun onFeedback(fb: DockFeedback) {
        when (fb) {
            DockFeedback.None -> Unit
            DockFeedback.ShowSwell -> openSwell(SwellKind.Scrub, gesture.ring.coerceAtLeast(0), gesture.y)
            DockFeedback.RingTick -> {
                haptic(KitHaptic.Select)
                openSwell(SwellKind.Scrub, gesture.ring)
            }
            DockFeedback.AlertTick -> {
                haptic(KitHaptic.LongPress)
                openSwell(SwellKind.Scrub, gesture.ring)
            }
            DockFeedback.TargetTick -> haptic(KitHaptic.Select)
            DockFeedback.Peek -> {
                haptic(KitHaptic.LongPress)
                stopCharge()
                openSwell(SwellKind.Peek, gesture.ring)
            }
            DockFeedback.PickUp -> {
                // The held ring's card stays beside it while it is carried (it says which conversation moves; the names
                // in the strip are short), riding to each drop place; its targets rest until the ring is dropped.
                haptic(KitHaptic.LongPress)
                carrying = gesture.ring
                carryTo = gesture.ring
                carryY = gesture.y
                followCarry(gesture.ring)
            }
        }
        target = gesture.target
    }

    /** The gesture's continuous part: called on every move. */
    internal fun onMove(x: Float, y: Float, startX: Float) {
        when (gesture.mode) {
            DockMode.Scrub -> if (hits.inStrip(x)) followFinger(y)
            DockMode.Reorder -> {
                carryY = y
                val to = hits.dropIndex(y)
                if (to != carryTo) {
                    carryTo = to
                    haptic(KitHaptic.Select)
                    followCarry(to)
                }
            }
            DockMode.Move -> {
                moving = true
                carryY = y
                val room = DockDims.EDGE_MARGIN + DockDims.SHOULDER_REACH
                val t = (y - column.height / 2f).coerceIn(safeTop + room, max(safeTop + room, safeBottom - room - column.height))
                launch { top.snapTo(t) }
            }
            DockMode.Pull -> {
                val panelW = panelTarget().let { widthDp - edgeInset - it.left }
                val o = pullOut(pullStart, startX - x, panelW)
                val p = ((o - DockDims.REST_W) / (panelW - DockDims.REST_W)).coerceAtLeast(0f)
                if (!panelShown && p > 0.02f) panelShown = true
                panelJob?.cancel()
                launch {
                    if (o < DockDims.REST_W) {
                        out.snapTo(o)
                        panelX.snapTo(0f)
                        panelY.snapTo(0f)
                    } else {
                        out.snapTo(DockDims.REST_W)
                        panelX.snapTo(p)
                        panelY.snapTo(p.coerceAtMost(1f))
                    }
                }
            }
            else -> Unit
        }
        target = gesture.target
    }

    internal fun onDown(zone: DockZone) {
        when (zone) {
            DockZone.Ring -> startCharge()
            DockZone.Grip, DockZone.Nub -> carryY = top.value + column.height / 2f
            else -> Unit
        }
        pullStart = if (panelOpen) widthDp - edgeInset - panelTarget().left else out.value
        pullFromPanel = panelOpen
    }

    /** Closes everything and puts the body back at rest at once (the dock has nothing to show). */
    internal suspend fun clearAll() {
        alertJob?.cancel()
        swellJob?.cancel()
        panelJob?.cancel()
        gesture.cancel()
        swell = null
        swellRing = -1
        target = null
        renaming = null
        panelOpen = false
        panelShown = false
        carrying = -1
        carryTo = -1
        moving = false
        pressRing = -1
        swellGrow.snapTo(0f)
        panelX.snapTo(0f)
        panelY.snapTo(0f)
        ride.snapTo(0f)
    }

    internal fun ringKey(i: Int): String? = rings.getOrNull(i)?.key

    internal fun shownChild(ring: KitDockRing, cell: Int): KitDockChild? {
        val order = childOrder(ring.children)
        return order.getOrNull(cell)?.let { ring.children[it] }
    }

    internal fun onOutcome(o: DockOutcome) {
        stopCharge()
        when (o) {
            DockOutcome.None -> Unit
            is DockOutcome.Open -> {
                ringKey(o.ring)?.let(intents::onOpen)
                closeSwell()
            }
            is DockOutcome.Target -> runTarget(o.ring, o.kind, o.child)
            is DockOutcome.Keep -> Unit
            DockOutcome.Close -> {
                if (renaming != null) renaming = null
                closeSwell()
                if (panelOpen) closePanel()
            }
            is DockOutcome.Drop -> {
                val from = o.from
                closeSwell()
                carrying = -1
                carryTo = -1
                if (o.to != from) intents.onReorder(from, o.to)
                val ring = rings.getOrNull(from)
                if (ring != null && ring.pinned != o.pinned) intents.onPinChange(ring.key, o.pinned)
            }
            is DockOutcome.PullEnd -> {
                // A flick from the head: the dock was carried with the finger; it goes back to its place on its own edge.
                if (moving) {
                    moving = false
                    placeGeneration++
                }
                settlePull(o.velocity)
            }
            DockOutcome.Untuck -> {
                moving = false
                launch { out.animateTo(DockDims.REST_W, DockSprings.drop(reduced)) }
                intents.onTuckChange(false)
            }
            is DockOutcome.Place -> {
                moving = false
                placeGeneration++
                val side = sideAt(phys(o.x), widthDp, rtl = !onRightForEnd)
                intents.onPlacementChange(side, biasFor(o.y, column.height, safeTop, safeBottom))
            }
            DockOutcome.NewConversation -> intents.onNewConversation()
        }
    }

    /** Whether the End side is the physical right (LTR); set from the layout direction. */
    internal var onRightForEnd by mutableStateOf(true)

    internal fun runTarget(i: Int, kind: DockTargetKind, child: Int) {
        val ring = rings.getOrNull(i) ?: return
        when (kind) {
            DockTargetKind.Header, DockTargetKind.Open -> intents.onOpen(ring.key)
            DockTargetKind.Child -> shownChild(ring, child)?.let { intents.onOpenChild(ring.key, it.key) }
            DockTargetKind.More -> {
                expanded.value = expanded.value + ring.key
                closeSwell()
                openPanel()
                return
            }
            DockTargetKind.Allow -> intents.onAllow(ring.key)
            DockTargetKind.Deny -> intents.onDeny(ring.key)
            DockTargetKind.Stop -> intents.onStop(ring.key)
            DockTargetKind.Remove -> intents.onRemove(ring.key)
            DockTargetKind.Rename -> {
                startRename(i)
                return
            }
        }
        closeSwell()
    }

    /** Rename: the ring rides out on the swell's tip and the field sits to its right. */
    internal fun startRename(i: Int) {
        val ring = rings.getOrNull(i) ?: return
        haptic(KitHaptic.LongPress)
        renaming = ring.key
        renameLines = (ring.name.length / RENAME_CHARS_PER_LINE + 1).coerceIn(1, DockDims.RENAME_LINES)
        openSwell(SwellKind.Rename, i)
        launch { ride.animateTo(1f, DockSprings.drop(reduced)) }
    }

    /** The field measured its name as [lines] lines: the swell grows or shrinks to hold them (up to four). */
    internal fun renameMeasured(lines: Int) {
        if (renaming == null) return
        val n = lines.coerceIn(1, DockDims.RENAME_LINES)
        if (n == renameLines) return
        renameLines = n
        val ring = rings.getOrNull(swellRing) ?: return
        if (swell?.kind != SwellKind.Rename) return
        val l = layoutFor(SwellKind.Rename, ring)
        if (l.height == swell?.height && l.fieldLines == swell?.fieldLines) return
        swell = l
        launch { swellH.animateTo(l.height, DockSprings.fill(reduced)) }
    }

    internal fun endRename(save: Boolean, text: String) {
        val key = renaming ?: return
        renaming = null
        if (save) {
            val clean = text.trim().replace(Regex("\\s+"), " ")
            val old = rings.firstOrNull { it.key == key }?.name
            if (clean != old) intents.onRename(key, clean)
        }
        closeSwell()
    }

    internal fun openPanel() {
        panelShown = true
        panelOpen = true
        panelJob?.cancel()
        panelJob = scope?.launch {
            launch { panelY.animateTo(1f, DockSprings.fill(reduced)) }
            panelX.animateTo(1f, DockSprings.drop(reduced))
        }
        launch { out.animateTo(DockDims.REST_W, DockSprings.close(reduced)) }
    }

    internal fun closePanel() {
        panelOpen = false
        panelJob?.cancel()
        panelJob = scope?.launch {
            // The rows empty first; the body follows a beat later, with no bounce.
            if (!reduced) delay(90)
            launch { panelY.animateTo(0f, DockSprings.close(reduced)) }
            panelX.animateTo(0f, DockSprings.close(reduced))
            if (!panelOpen) panelShown = false
        }
    }

    /** The open list dragged sideways by [deltaDp] (edge frame: positive toward the edge). */
    internal fun panelDrag(deltaDp: Float) {
        val panelW = widthDp - edgeInset - panelTarget().left
        val p = (panelX.value - deltaDp / (panelW - DockDims.REST_W)).coerceAtLeast(0f)
        pullFromPanel = true
        panelJob?.cancel()
        launch {
            panelX.snapTo(p)
            panelY.snapTo(p.coerceAtMost(1f))
        }
    }

    /** The list's drag ended with [velocity] dp/s toward the edge. */
    internal fun panelDragEnd(velocity: Float) {
        pullFromPanel = true
        settlePull(velocity)
    }

    private fun settlePull(velocity: Float) {
        val panelW = widthDp - edgeInset - panelTarget().left
        val now = if (panelX.value > 0f) DockDims.REST_W + panelX.value * (panelW - DockDims.REST_W) else out.value
        when (pullSettle(now, velocity, panelW, pullFromPanel)) {
            PullSettle.Panel -> openPanel()
            PullSettle.Rest -> {
                closePanel()
                launch { out.animateTo(DockDims.REST_W, DockSprings.drop(reduced)) }
            }
            PullSettle.Tuck -> {
                closePanel()
                launch { out.animateTo(DockDims.NUB_W, DockSprings.close(reduced)) }
                intents.onTuckChange(true)
            }
        }
    }

    /**
     * A ring's alert (its pulse rose): a request leans the dock out and shows the words in the swell for about three
     * seconds; a failure sends a pulse along the outline. Nothing shows over an open swell, list or a finger.
     */
    internal fun alert(i: Int, failed: Boolean) {
        if (!failed) {
            launch {
                if (!reduced) {
                    lean.animateTo(7f, DockSprings.drop(reduced))
                    delay(3000)
                }
                lean.animateTo(0f, DockSprings.close(reduced))
            }
        } else if (!reduced) {
            pulseRing = i
            launch {
                pulse.snapTo(0f)
                pulse.animateTo(1f, DockSprings.timed(reduced, 600))
            }
        }
        if (gesture.mode != DockMode.Idle || anythingOpen || out.value < DockDims.REST_W * 0.5f) return
        alertJob?.cancel()
        alertJob =
            scope?.launch {
                delay(if (reduced) 0L else 200L)
                openSwell(SwellKind.Scrub, i)
                gesture.keepOpenForAlert(i)
                delay(3100)
                if (gesture.mode == DockMode.Idle) closeSwell()
            }
    }

    private companion object {
        const val EXCLUSION_MAX_DP = 200f
        const val PANEL_KEY_SIZE = 8

        /** A first guess of how many characters a rename line holds, until the field measures its name. */
        const val RENAME_CHARS_PER_LINE = 22

        /** How far the ring band reaches past the viewport (dp): room for the last name's descender, inside A. */
        const val RING_BAND_SPILL = 4f
    }
}

/** Remembers a [KitEdgeDockState]; hoist it when the host reads [KitEdgeDockState.body] or its gesture exclusion. */
@Composable
public fun rememberKitEdgeDockState(): KitEdgeDockState = remember { KitEdgeDockState() }
