package io.github.eslamasabry.oc.ui.kit.dock

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The edge dock's geometry, pure and in dp. Everything is computed in the "edge frame": the dock sits on the RIGHT
 * screen edge (x grows toward the edge, which is at x = width). A dock on the physical left is the same geometry
 * mirrored ([mirrorX]); text is never mirrored, only placed.
 *
 * The body is two rounded boxes (the glass owner's joined-glass model, `liquid-glass/references/joined-glass.md`):
 * box A is the strip, box B the swell (or the peek card, or the pulled-out list at full size). They are joined by the
 * cubic smooth-minimum field ([DockJoinField]), whose fillet's curvature runs out to zero where it meets each straight
 * edge. The non-lens looks draw that field's zero line ([buildOutline]); under a lens the host reads the same boxes and
 * the same reach ([KitDockBody]) and joins them itself. One field, one outline, for both.
 */

/** The dock's numbers, in dp. */
internal object DockDims {
    /** The strip's width at rest. */
    const val REST_W = 68f
    /** The tucked nub's visible width; its touch target is [NUB_TOUCH]. */
    const val NUB_W = 8f
    const val NUB_TOUCH = 48f
    /** The strip's convex corners (A's radius). */
    const val CORNER = 28f
    /**
     * The concave shoulders into the screen edge: the effective blend width of a right-angle cubic blend (tightest
     * radius = this x [FILLET_RADIUS_PER_BLEND]). Shoulders are a third shape the two-box join does not draw.
     */
    const val SHOULDER_REACH = 38f
    /** Boxes run this far past the screen edge so their outer corners never show. */
    const val OVERHANG = 40f
    /** The grip band at the dock's head: drag it to move the dock. */
    const val HEAD = 24f
    /** The new-conversation button's band at the dock's foot (a 48 dp round target inside it). */
    const val FOOT = 60f
    const val PAD_BOTTOM = 10f
    const val PITCH = 64f
    /** Never closer: below this the column scrolls instead (each ring keeps at least a 48 dp target). */
    const val PITCH_MIN = 56f
    const val MAX_VISIBLE = 7
    /** The gap that holds the pin line between pinned and other rings. */
    const val PIN_GAP = 12f
    /** The ring's stroke radius and width (a 48 dp ring box, as the prototype). */
    const val RING_R = 18f
    const val RING_STROKE = 3f
    /** The ring sits this much above its slot's centre; its name sits below. */
    const val RING_LIFT = 8f
    const val MIN_TARGET = 48f
    /** Kept between the body and the safe area's edges (and around keep-clear spots). */
    const val EDGE_MARGIN = 12f
    /**
     * The page gutter (the tokens' 16 dp): kept free between the swell, the peek or the list and the far screen edge,
     * and between the list's words and the dock's own screen edge (device QA 2026-11-11, m2: names to the edge).
     */
    const val GUTTER = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.GUTTER.toFloat()
    /** A ring's name keeps this much in from each side of the strip, so it never runs to the screen's edge. */
    const val LABEL_INSET = 6f
    /** A ring's name's widest: the strip at rest less [LABEL_INSET] on each side (longer names end in an ellipsis). */
    const val LABEL_MAX = REST_W - 2f * LABEL_INSET

    /**
     * A ring's name's widest with the page gutter kept from the screen's own edge (device QA 2026-11-11, run 4, 4a and 4c:
     * names started about 5 dp from the edge): from [LABEL_INSET] in from the strip's inner side to [GUTTER] short of the
     * screen edge, which lies [edgeInset] beyond the strip's visible edge (a cutout).
     */
    fun labelMax(edgeInset: Float): Float = min(LABEL_MAX, REST_W - LABEL_INSET - GUTTER + edgeInset)

    /** How far the name's box moves from the ring's centre toward the screen edge (edge frame; negative: toward the middle). */
    fun labelShift(edgeInset: Float): Float = ((REST_W / 2f + edgeInset - GUTTER) - (REST_W / 2f - LABEL_INSET)) / 2f

    // The swell (box B while scrubbing).
    const val SW_TIP_PAD = 14f
    const val SW_BASE_PAD = 10f
    /** One target cell's width in the swell (>= 48 dp). */
    const val SW_CELL = 50f
    /** Cells per row while scrubbing: four action targets or four mini rings. */
    const val SW_COLS = 4
    /** The swell's end corner: well under half its height (the owner rejected the capsule). */
    const val SW_END_R = 24f
    /**
     * The root fillet's EFFECTIVE BLEND WIDTH: the `k` of the cubic smooth minimum `min(da, db) - h^3 k / 6` where the
     * two edges meet at a right angle. Its tightest radius is blend x 0.354 = 22.7 dp (taller boxes widen it for the
     * base rule, [filletFor]: the 288 dp peek to 94.4, radius 33.4 dp). The join field's reach is twice this ([JOIN_REACH_PER_BLEND]).
     */
    const val SW_FILLET_BLEND = 64f
    /**
     * B reaches this far into A so the two boxes overlap (no gap, no seam, even without the join). At least B's end
     * corner (24 dp): B's corner arcs then lie inside A, its top and bottom edges run straight up to A's side, the
     * field's `facing` is exactly 0.5 all along the fillet, and the fillet ends one blend width from B as the strip's
     * extension budgets for (at 20 dp the arc started 4 dp outside A: a 0.3 dp notch and a 4 % wider blend).
     */
    const val SW_OVERLAP = 28f
    const val SW_PAD_T = 12f
    const val SW_PAD_B = 10f
    const val SW_GAP = 8f
    /** The swell always keeps the ring's own band (ring centre +- this) beside it. */
    const val SW_RING_BAND = 32f
    const val PEEK_MAX_LEN = 274f
    const val PEEK_LINES = 6
    /** Rename: B as long as the peek (the name gets room), the field up to this many lines, in B's own part. */
    const val RENAME_LEN = PEEK_MAX_LEN
    const val RENAME_H = 80f
    const val RENAME_LINES = 4
    /** The rename field's line height per unit of text scale (the row title's, 16/22). */
    const val RENAME_LINE = 22f
    const val RENAME_PAD = 12f

    // The pulled-out list (box B at full size).
    const val PANEL_MAX = 320f
    /** Kept free between the list and the far screen edge. */
    const val PANEL_MIN_GAP = 48f
    const val PANEL_PAD = 12f
    const val CHILD_ROW = 48f
    const val HIDDEN_ROW = 48f

    /** Tightest radius of a right-angle cubic smooth-minimum fillet per unit of effective blend width (measured: 0.3541). */
    const val FILLET_RADIUS_PER_BLEND = 0.3541525f

    /**
     * The join's reach per unit of effective blend width. The joined-glass field blends with `k = reach x facing x
     * inside`, and `facing = 0.5 - 0.5 dot(ga, gb)` is 0.5 all along a right-angle fillet (the two edges' outward
     * directions are perpendicular; `inside` is 1 there). So a fillet of blend width k needs a join reach of 2 k.
     */
    const val JOIN_REACH_PER_BLEND = 2f

    /**
     * The join's reach rises from nothing over the first part of B's growth (0 to this), so the fillet arrives with
     * the swell instead of showing as a small bump on the strip's side in the first frame.
     */
    const val JOIN_FADE_IN = 0.2f

    /** The smallest join reach drawn while B is out (a near-sharp root); keeps the traced field well defined. */
    const val JOIN_MIN_REACH = 1f
}

/** A rounded box in the edge frame, dp. */
internal data class DockBox(val left: Float, val top: Float, val right: Float, val bottom: Float, val radius: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
}

/** A plain rectangle in the edge frame, dp. */
internal data class DockRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom

    fun offset(dx: Float, dy: Float): DockRect = DockRect(left + dx, top + dy, right + dx, bottom + dy)
}

/** Edge-frame x for a dock on the physical left (and back): the same geometry, mirrored. */
internal fun mirrorX(x: Float, width: Float): Float = width - x

internal fun mirrorRect(r: DockRect, width: Float): DockRect = DockRect(width - r.right, r.top, width - r.left, r.bottom)

/**
 * Whether a ring whose slot is centred at [centerY] stands whole inside the strip's ring viewport [viewTop]..[viewBottom]
 * (dp). A ring only part in view is not in the strip at all (it stays in the list): a half ring peeping out above the
 * "+" when the keyboard left room for one (device QA 2026-11-11, run 4, 1b).
 */
internal fun ringStandsWhole(centerY: Float, pitch: Float, viewTop: Float, viewBottom: Float): Boolean =
    centerY - pitch / 2f >= viewTop - WHOLE_SLACK && centerY + pitch / 2f <= viewBottom + WHOLE_SLACK

private const val WHOLE_SLACK = 0.5f

/** True when [side] is on the physical right for the layout direction. */
internal fun physicallyRight(side: KitDockSide, rtl: Boolean): Boolean = (side == KitDockSide.End) != rtl

/** The side a dock dropped at physical [x] snaps to: the nearer edge. */
internal fun sideAt(x: Float, width: Float, rtl: Boolean): KitDockSide {
    val right = x >= width / 2f
    return if (right != rtl) KitDockSide.End else KitDockSide.Start
}

internal fun smoothstep(a: Float, b: Float, v: Float): Float {
    val t = ((v - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// ---------------------------------------------------------------------------------------------------------------
// The ring column and where it sits.

/** How the rings fill the strip: [visible] rings at [pitch], the rest scroll. [pinGap] holds the pin line. */
internal data class DockColumn(
    val count: Int,
    val pinned: Int,
    val visible: Int,
    val pitch: Float,
    val pinGap: Float,
    /** The foot's band under the rings: [DockDims.FOOT] for the "+", 0 when the dock offers no new conversation. */
    val foot: Float = DockDims.FOOT,
) {
    /** The strip's height (A at rest). */
    val height: Float get() = DockDims.HEAD + visible * pitch + pinGap + foot + DockDims.PAD_BOTTOM

    /** The whole list's height when all rings are laid out (for scrolling). */
    val contentHeight: Float get() = count * pitch + pinGap

    val viewportHeight: Float get() = visible * pitch + pinGap

    val maxScroll: Float get() = max(0f, contentHeight - viewportHeight)

    /** Slot [i]'s centre for a strip whose top is [top], scrolled by [scroll]. */
    fun slotCenter(i: Int, top: Float, scroll: Float): Float = slotCenterAt(i.toFloat(), top, scroll)

    /** The same for a fractional slot (rings moving between places). */
    fun slotCenterAt(slot: Float, top: Float, scroll: Float): Float {
        val gap =
            when {
                pinGap <= 0f -> 0f
                pinned == 0 -> pinGap
                else -> pinGap * (slot - (pinned - 1)).coerceIn(0f, 1f)
            }
        return top + DockDims.HEAD + (slot + 0.5f) * pitch + gap - scroll
    }

    /** The pin line's y: between the pinned rings and the rest (at the top of the list when none is pinned). */
    fun pinLineY(top: Float, scroll: Float): Float = top + DockDims.HEAD + pinned * pitch + pinGap / 2f - scroll

    /** The index a ring dragged to [y] lands on (0 until [count]). */
    fun reorderIndex(y: Float, top: Float, scroll: Float): Int {
        if (count <= 0) return 0
        val line = pinLineY(top, scroll)
        val local = y - (top + DockDims.HEAD) + scroll - (if (y >= line) pinGap else 0f)
        return floor(local / pitch).toInt().coerceIn(0, count - 1)
    }

    /** Dropping at [y] pins the ring when it lands above the pin line. */
    fun pinsAt(y: Float, top: Float, scroll: Float): Boolean = y < pinLineY(top, scroll)

    /** The ring under [y] at rest, or -1 above or below the list. */
    fun ringAt(y: Float, top: Float, scroll: Float): Int {
        if (count <= 0) return -1
        val viewTop = top + DockDims.HEAD
        if (y < viewTop || y >= viewTop + viewportHeight) return -1
        return reorderIndex(y, top, scroll)
    }

    /** The scroll that keeps ring [i] in view (for alerts and keyboard focus). */
    fun scrollToShow(i: Int, scroll: Float): Float {
        val top = DockDims.HEAD + i * pitch + (if (pinned > 0 && i >= pinned) pinGap else 0f)
        val view = viewportHeight
        return when {
            top < scroll + DockDims.HEAD -> (top - DockDims.HEAD).coerceIn(0f, maxScroll)
            top + pitch > scroll + DockDims.HEAD + view -> (top + pitch - DockDims.HEAD - view).coerceIn(0f, maxScroll)
            // In view: stay, but never past what this column can scroll (a scroll kept from a smaller column).
            else -> scroll.coerceIn(0f, maxScroll)
        }
    }

    companion object {
        /**
         * The column for [count] rings in a safe span of [span] dp. Up to seven rings shrink from 64 toward 56 dp to
         * fit; more than seven, or too little room, keep 64 dp and scroll. The pin line's gap shows when a ring is
         * pinned or while one is being reordered.
         */
        fun of(count: Int, pinned: Int, span: Float, reordering: Boolean = false, offersNew: Boolean = true): DockColumn {
            val gap = if (pinned > 0 || reordering) DockDims.PIN_GAP else 0f
            val foot = if (offersNew) DockDims.FOOT else 0f
            val fixed = DockDims.HEAD + gap + foot + DockDims.PAD_BOTTOM
            val room = span - 2f * (DockDims.EDGE_MARGIN + DockDims.SHOULDER_REACH) - fixed
            if (count <= 0) return DockColumn(0, 0, 0, DockDims.PITCH, gap, foot)
            val fit = room / count
            return if (count <= DockDims.MAX_VISIBLE && fit >= DockDims.PITCH_MIN) {
                DockColumn(count, pinned, count, min(DockDims.PITCH, fit), gap, foot)
            } else {
                val v = floor(room / DockDims.PITCH).toInt().coerceIn(1, min(count, DockDims.MAX_VISIBLE))
                DockColumn(count, pinned, v, DockDims.PITCH, gap, foot)
            }
        }
    }
}

/**
 * Where a body of [height] stands between [safeTop] and [safeBottom] for a [bias] (0 = highest, 1 = lowest), kept
 * clear of the [keepClear] spots that lie in the dock's band [bandLeft]..[bandRight] (edge frame). Room is kept for the
 * shoulders and a margin. Returns the body's top.
 */
internal fun placeTop(
    height: Float,
    bias: Float,
    safeTop: Float,
    safeBottom: Float,
    keepClear: List<DockRect> = emptyList(),
    bandLeft: Float = Float.NEGATIVE_INFINITY,
    bandRight: Float = Float.POSITIVE_INFINITY,
): Float {
    val room = DockDims.EDGE_MARGIN + DockDims.SHOULDER_REACH
    val minTop = safeTop + room
    val maxTop = safeBottom - room - height
    if (maxTop <= minTop) return (safeTop + safeBottom) / 2f - height / 2f
    var top = minTop + bias.coerceIn(0f, 1f) * (maxTop - minTop)
    val spots = keepClear.filter { it.right > bandLeft && it.left < bandRight }
    if (spots.isEmpty()) return top
    // Move off a spot to the nearer side that is free; two passes settle a move that lands on another spot.
    repeat(2) {
        for (s in spots) {
            val bodyTop = top - room
            val bodyBottom = top + height + room
            if (bodyBottom <= s.top || bodyTop >= s.bottom) continue
            val above = s.top - room - height
            val below = s.bottom + room
            val aboveOk = above >= minTop
            val belowOk = below <= maxTop
            top = when {
                aboveOk && belowOk -> if (abs(above - top) <= abs(below - top)) above else below
                aboveOk -> above
                belowOk -> below
                else -> top
            }
        }
    }
    return top.coerceIn(minTop, maxTop)
}

/** The bias that puts a body of [height] with its centre at [centerY] (the inverse of [placeTop] without spots). */
internal fun biasFor(centerY: Float, height: Float, safeTop: Float, safeBottom: Float): Float {
    val room = DockDims.EDGE_MARGIN + DockDims.SHOULDER_REACH
    val minTop = safeTop + room
    val maxTop = safeBottom - room - height
    if (maxTop <= minTop) return 0.5f
    return ((centerY - height / 2f - minTop) / (maxTop - minTop)).coerceIn(0f, 1f)
}

/** The strip (box A): its inner edge at the screen edge minus [edgeInset] and [out], running past the screen edge. */
internal fun stripBox(width: Float, edgeInset: Float, out: Float, top: Float, bottom: Float): DockBox {
    val left = width - edgeInset - out
    val visible = width - left
    return DockBox(left, top, width + DockDims.OVERHANG, bottom, min(DockDims.CORNER, min(visible / 2f, (bottom - top) / 2f)).coerceAtLeast(0f))
}

// ---------------------------------------------------------------------------------------------------------------
// The swell (box B) and what sits in it.

/** What box B holds while it is out of the strip. */
internal enum class SwellKind { Scrub, Peek, Rename }

/** A target in the swell; [child] indexes the shown children for [Child]. */
internal enum class DockTargetKind { Header, Child, More, Open, Allow, Deny, Stop, Rename, Remove }

/** One target (or a block of words) in the swell, relative to B's top-left corner at full growth. */
internal data class DockTarget(val kind: DockTargetKind, val child: Int, val rect: DockRect)

/**
 * The targets a ring offers in the swell, in order (owner 2026-11-11: no shortened action words). The scrub swell shows
 * only what belongs to the ring's state, with room for its full words: Open, then Allow and Deny for a request or Stop
 * while it runs. Rename and Remove live in the held ring's card (the peek) and in the list, where there is room.
 */
internal fun swellActions(state: KitDockRingState, kind: SwellKind): List<DockTargetKind> {
    if (kind == SwellKind.Rename) return emptyList()
    val list = ArrayList<DockTargetKind>(5)
    list += DockTargetKind.Open
    if (state == KitDockRingState.NeedsYou) {
        list += DockTargetKind.Allow
        list += DockTargetKind.Deny
    } else if (state.running) {
        list += DockTargetKind.Stop
    }
    if (kind == SwellKind.Peek) {
        list += DockTargetKind.Rename
        list += DockTargetKind.Remove
    }
    return list
}

/** Children in the order the swell and the list show them: needs-you, then failed, then the model's order. */
internal fun childOrder(children: List<KitDockChild>): List<Int> =
    children.indices.sortedWith(
        compareBy<Int> {
            when (children[it].state) {
                KitDockRingState.NeedsYou -> 0
                KitDockRingState.Failed -> 1
                else -> 2
            }
        }.thenBy { it },
    )

/**
 * The mini-ring cells of a row [cols] wide for [total] sub-agents: all of them up to [cols], otherwise one fewer and a
 * "+n" cell. Returns (shown, more).
 */
internal fun childCells(total: Int, cols: Int): Pair<Int, Int> =
    if (total <= cols) total to 0 else (cols - 1) to (total - (cols - 1))

/** Heights of the swell's rows for a frame text scale [s] (already capped at 1.3). */
internal object SwellRows {
    fun header(s: Float, server: Boolean, stateLine: Boolean = true): Float =
        20f * s + (if (stateLine) 18f * s * (if (server) 2f else 1f) else 0f)
    fun children(s: Float): Float = 38f + 26f * s
    fun action(s: Float): Float = max(DockDims.MIN_TARGET, 36f + 14f * s)
    fun line(s: Float): Float = 18f * s
}

/**
 * Box B's size and its contents, relative to B's top-left (x from the tip, y from the top), before it is placed.
 * [length] is how far B stands out of the strip; B reaches [DockDims.SW_OVERLAP] further into it.
 */
internal data class SwellLayout(
    val kind: SwellKind,
    val length: Float,
    val height: Float,
    val radius: Float,
    /** The root fillet's effective blend width (see [DockDims.SW_FILLET_BLEND]). */
    val filletBlend: Float,
    val targets: List<DockTarget>,
    /** The peek's lines block, or null. */
    val lines: DockRect?,
    /** Rename: where the ring rides (centre, relative) and the field's rectangle (relative). */
    val rideX: Float = 0f,
    val field: DockRect? = null,
    /** Rename: how many lines the field shows at once (it scrolls inside itself beyond them). */
    val fieldLines: Int = 0,
) {
    /** The target at a point relative to B's top-left, or null. */
    fun targetAt(rx: Float, ry: Float): DockTarget? = targets.firstOrNull { it.kind != DockTargetKind.Header && it.rect.contains(rx, ry) }
        ?: targets.firstOrNull { it.kind == DockTargetKind.Header && it.rect.contains(rx, ry) }
}

/**
 * Lays out box B for [kind]. [maxLength] is the room between the strip and the far safe edge. [s] is the frame text
 * scale. The length is the same for every conversation and every number of sub-agents: sub-agents and actions make the
 * swell taller, never longer.
 */
internal fun swellLayout(
    kind: SwellKind,
    state: KitDockRingState,
    childTotal: Int,
    peekLines: Int,
    server: Boolean,
    s: Float,
    maxLength: Float,
    stateLine: Boolean = true,
    renameLines: Int = DockDims.RENAME_LINES,
    maxHeight: Float = Float.POSITIVE_INFINITY,
): SwellLayout {
    val pad = DockDims.SW_TIP_PAD + DockDims.SW_BASE_PAD
    if (kind == SwellKind.Rename) {
        val len = min(DockDims.RENAME_LEN, maxLength).coerceAtLeast(pad + 2 * DockDims.SW_CELL)
        // The field shows the whole name being edited: it grows with the name up to four lines (it wraps, never scrolls
        // sideways out of sight), then scrolls inside itself with the caret kept in view (device QA 2026-11-11, M3: "o.txt
        // with one line", the rest scrolled out; run 4, 3a: only two lines of a long name). A short area (the keyboard up)
        // takes lines off the top of that, so the swell, its fillets and the strip's corners stay inside the area.
        val lineH = DockDims.RENAME_LINE * s
        val roomH = maxHeight - 2f * (DockDims.EDGE_MARGIN + DockDims.CORNER + DockDims.SW_FILLET_BLEND) - 2f * DockDims.RENAME_PAD
        val cap = if (roomH.isFinite()) floor(roomH / lineH).toInt().coerceAtLeast(1) else DockDims.RENAME_LINES
        val lines = renameLines.coerceIn(1, DockDims.RENAME_LINES).coerceAtMost(cap)
        val fieldH = lines * lineH
        val h = max(DockDims.RENAME_H, fieldH + 2f * DockDims.RENAME_PAD)
        val rideX = DockDims.SW_TIP_PAD + 24f
        // The field: right of the ridden-out ring, in B's own part (never over the strip and its rings).
        val field = DockRect(rideX + 24f + 10f, (h - fieldH) / 2f, len - DockDims.SW_BASE_PAD, (h + fieldH) / 2f)
        return SwellLayout(kind, len, h, min(DockDims.SW_END_R, h * 0.3f), filletFor(len, h), emptyList(), null, rideX, field, lines)
    }
    val want = if (kind == SwellKind.Peek) DockDims.PEEK_MAX_LEN else pad + DockDims.SW_COLS * DockDims.SW_CELL
    val len = min(want, maxLength).coerceAtLeast(pad + 2 * DockDims.SW_CELL)
    val cw = len - pad
    val cols = max(1, floor(cw / DockDims.SW_CELL).toInt()).let { if (kind == SwellKind.Scrub) min(it, DockDims.SW_COLS) else it }
    val cell = cw / cols
    val targets = ArrayList<DockTarget>(12)
    val x0 = DockDims.SW_TIP_PAD
    var y = DockDims.SW_PAD_T
    val headerH = SwellRows.header(s, server, stateLine)
    targets += DockTarget(DockTargetKind.Header, -1, DockRect(x0, y, x0 + cw, y + headerH))
    y += headerH
    if (childTotal > 0) {
        y += DockDims.SW_GAP
        val (shown, more) = childCells(childTotal, cols)
        val h = SwellRows.children(s)
        for (i in 0 until shown) targets += DockTarget(DockTargetKind.Child, i, DockRect(x0 + i * cell, y, x0 + (i + 1) * cell, y + h))
        if (more > 0) targets += DockTarget(DockTargetKind.More, more, DockRect(x0 + shown * cell, y, x0 + (shown + 1) * cell, y + h))
        y += h
    }
    var lines: DockRect? = null
    val nLines = if (kind == SwellKind.Peek) min(peekLines, DockDims.PEEK_LINES) else 0
    if (nLines > 0) {
        y += DockDims.SW_GAP
        val h = nLines * SwellRows.line(s)
        lines = DockRect(x0, y, x0 + cw, y + h)
        y += h
    }
    val actions = swellActions(state, kind)
    if (actions.isNotEmpty()) {
        y += DockDims.SW_GAP
        val h = SwellRows.action(s)
        actions.forEachIndexed { j, a ->
            val c = j % cols
            val r = j / cols
            targets += DockTarget(a, -1, DockRect(x0 + c * cell, y + r * h, x0 + (c + 1) * cell, y + (r + 1) * h))
        }
        y += ceil(actions.size / cols.toFloat()) * h
    }
    val h = max(64f, y + DockDims.SW_PAD_B)
    return SwellLayout(kind, len, h, min(DockDims.SW_END_R, h * 0.3f), filletFor(len, h), targets, lines)
}

/**
 * The fillet's effective blend width for a swell [length] by [height]: the design blend, widened on tall swells so the
 * base stays at least [BASE_PER_HEIGHT] x the height, and never more than the swell's straight top edge can take.
 *
 * The base is measured where the fillet stands [BASE_FOOT] dp off the strip. A cubic fillet of blend k is that far off
 * only [filletFoot] (about 0.46 k at 64 dp) beyond B, not k, so "blend >= 0.3 x height" left 190 and 213 dp tall swells
 * (sub-agents) with a base of 1.27 and 1.25 x their height: the narrow pill the owner rejected.
 */
internal fun filletFor(length: Float, height: Float): Float {
    val want = (BASE_PER_HEIGHT - 1f) / 2f * height
    var k = DockDims.SW_FILLET_BLEND
    if (filletFoot(k) < want) {
        // filletFoot rises with k (from k > 4 dp): bisect between a blend that falls short and one that is ample.
        var lo = k
        var hi = k + 4f * want
        repeat(20) {
            val m = (lo + hi) / 2f
            if (filletFoot(m) >= want) hi = m else lo = m
        }
        k = hi
    }
    return min(k, length - min(DockDims.SW_END_R, height * 0.3f) - 2f).coerceAtLeast(0f)
}

/** The base's least width per unit of the swell's height (the owner's limit is 1.3; this keeps the drawn outline above it). */
internal const val BASE_PER_HEIGHT = 1.34f

/** The base is measured this far off the strip, where the fillet first visibly leaves it. */
internal const val BASE_FOOT = 2f

/**
 * How far beyond B's side the right-angle cubic fillet of blend [k] still stands [BASE_FOOT] dp off the strip: on edge
 * A's side d = da - h^3 k / 6 with h = (k - |da - db|) / k, so da = e at db = x where (k - x + e)^3 = 6 k^2 e.
 */
internal fun filletFoot(k: Float): Float = k + BASE_FOOT - (6f * k * k * BASE_FOOT).pow(1f / 3f)

/**
 * Box B placed for a ring at [centerY] (at full growth), with the strip's ends extended as far as the join needs.
 * [blend] is the fillet's effective blend width once fitted.
 */
internal data class PlacedSwell(val b: DockBox, val stripTop: Float, val stripBottom: Float, val blend: Float)

/**
 * Places B (of [layout]) beside the ring at [centerY] (the finger's height, clamped to the first and last ring by the
 * caller). B keeps the ring's band beside it, stays inside the strip's rest span as far as that allows, and keeps room
 * in the safe area for its fillets. Near the first and last ring the strip's END EXTENDS (instead of the swell pooling
 * into the end, the prototype's flaw), so the swell keeps the same symmetric shape everywhere.
 */
internal fun placeSwell(
    layout: SwellLayout,
    centerY: Float,
    strip: DockBox,
    safeTop: Float,
    safeBottom: Float,
): PlacedSwell {
    val h = layout.height
    val k = layout.filletBlend
    val top = swellTop(h, k, centerY, strip.top, strip.bottom, safeTop, safeBottom)
    val b = DockBox(strip.left - layout.length, top, strip.left + DockDims.SW_OVERLAP, top + h, layout.radius)
    val sTop = extendedStripTop(top, k, strip.top, safeTop)
    val sBottom = extendedStripBottom(top + h, k, strip.bottom, safeBottom)
    return PlacedSwell(b, sTop, sBottom, fittedFillet(k, top, top + h, sTop, sBottom))
}

/** B's top for a box of height [h] with fillet blend [k] beside a ring at [centerY] (see [placeSwell]). */
internal fun swellTop(h: Float, k: Float, centerY: Float, stripTop: Float, stripBottom: Float, safeTop: Float, safeBottom: Float): Float {
    val r = DockDims.CORNER
    var top = centerY - h / 2f
    // Prefer the strip's own straight span (less extension); a box taller than that span centres on the strip, so
    // both ends extend evenly.
    val lo = stripTop + r + k
    val hi = stripBottom - r - k - h
    top = if (hi >= lo) top.coerceIn(lo, hi) else (stripTop + stripBottom) / 2f - h / 2f
    // Always keep the ring's band beside the swell.
    top = top.coerceIn(centerY + DockDims.SW_RING_BAND - h, centerY - DockDims.SW_RING_BAND)
    // And room in the safe area for the fillets and the strip's corners.
    val minTop = safeTop + DockDims.EDGE_MARGIN + r + k
    val maxTop = safeBottom - DockDims.EDGE_MARGIN - r - k - h
    return if (maxTop >= minTop) top.coerceIn(minTop, maxTop) else (minTop + maxTop) / 2f
}

/** The strip's top once it extends to hold a swell whose top is [swellTop]. */
internal fun extendedStripTop(swellTop: Float, k: Float, stripTop: Float, safeTop: Float): Float =
    min(stripTop, swellTop - k - DockDims.CORNER - 2f).coerceAtLeast(safeTop + DockDims.EDGE_MARGIN)

internal fun extendedStripBottom(swellBottom: Float, k: Float, stripBottom: Float, safeBottom: Float): Float =
    max(stripBottom, swellBottom + k + DockDims.CORNER + 2f).coerceAtMost(safeBottom - DockDims.EDGE_MARGIN)

/** If the safe area cut the extension, the fillet shrinks to what fits (never a kink into the corner arc). */
internal fun fittedFillet(k: Float, swellTop: Float, swellBottom: Float, stripTop: Float, stripBottom: Float): Float =
    min(k, min(swellTop - stripTop - DockDims.CORNER, stripBottom - swellBottom - DockDims.CORNER)).coerceAtLeast(0f)

// ---------------------------------------------------------------------------------------------------------------
// The pulled-out list (box B at full size).

/** Height of one conversation row in the list, and of a sub-agent row, for frame text scale [s]. */
internal object PanelRows {
    fun ring(s: Float, server: Boolean, stateLine: Boolean = true): Float =
        max(64f, 14f + (20f + (if (stateLine) 18f else 0f) + 18f + (if (server) 18f else 0f)) * s)
    fun child(s: Float): Float = max(DockDims.CHILD_ROW, 14f + 18f * s)
    fun hidden(s: Float): Float = max(DockDims.HIDDEN_ROW, 14f + 18f * s)
}

/** Sub-agent rows shown under a conversation in the list: all up to three, else its alerts and one "N sub-agents" row. */
internal fun panelChildRows(children: List<KitDockChild>, expanded: Boolean): Int {
    if (children.size <= 3 || expanded) return children.size
    val alerts = children.count { it.state == KitDockRingState.NeedsYou || it.state == KitDockRingState.Failed }
    return min(alerts, 2) + 1
}

/** The list's full content height. */
internal fun panelContentHeight(
    rings: List<KitDockRing>,
    hidden: List<KitDockRing>,
    hiddenOpen: Boolean,
    expanded: Set<String>,
    s: Float,
    stateLine: (KitDockRing) -> Boolean = { true },
): Float {
    var h = 0f
    for (r in rings) h += PanelRows.ring(s, r.server != null, stateLine(r)) + panelChildRows(r.children, r.key in expanded) * PanelRows.child(s)
    if (rings.any { it.pinned }) h += DockDims.PIN_GAP * 2f
    if (hidden.isNotEmpty()) {
        h += PanelRows.hidden(s)
        if (hiddenOpen) for (r in hidden) h += PanelRows.ring(s, r.server != null, stateLine(r))
    }
    return h
}

/**
 * The list's box: as wide as the screen allows up to [DockDims.PANEL_MAX], as tall as its content within the safe
 * area, centred on the strip and never smaller than it (so the list's box holds the strip and the union is one box).
 */
internal fun panelBox(width: Float, edgeInset: Float, farInset: Float, strip: DockBox, contentHeight: Float, safeTop: Float, safeBottom: Float, foot: Float = DockDims.FOOT): DockBox {
    val w = min(DockDims.PANEL_MAX, width - edgeInset - farInset - DockDims.PANEL_MIN_GAP).coerceAtLeast(strip.width - DockDims.OVERHANG)
    val left = width - edgeInset - w
    val room = DockDims.EDGE_MARGIN + DockDims.SHOULDER_REACH
    val h = min(contentHeight + 2f * DockDims.PANEL_PAD + DockDims.HEAD + foot, safeBottom - safeTop - 2f * room)
    var top = strip.centerY - h / 2f
    top = top.coerceIn(safeTop + room, max(safeTop + room, safeBottom - room - h))
    val t = min(top, strip.top)
    val b = max(top + h, strip.bottom)
    return DockBox(left, t, width + DockDims.OVERHANG, b, DockDims.CORNER)
}

/**
 * Box B on its way to the list: the width follows [gx] (it may pass 1 once, the overshoot along the pull), the height
 * follows [gy] (a calm fill). B always holds A, so the body is one box at every step.
 */
internal fun panelAt(strip: DockBox, panel: DockBox, gx: Float, gy: Float): DockBox {
    val y = gy.coerceIn(0f, 1f)
    return DockBox(
        left = min(strip.left, lerp(strip.left, panel.left, gx)),
        top = lerp(strip.top, panel.top, y),
        right = panel.right,
        bottom = lerp(strip.bottom, panel.bottom, y),
        radius = lerp(strip.radius, panel.radius, y),
    )
}

// ---------------------------------------------------------------------------------------------------------------
// The join field: two rounded boxes joined by a cubic smooth minimum.

/** One sample of a rounded box's exact signed distance [d] (negative inside) and its outward unit direction. */
internal class DockSd {
    var d = 0f
    var gx = 0f
    var gy = 0f
}

/**
 * The exact signed distance from ([px], [py]) to the rounded box [left]..[right] x [top]..[bottom] with corner
 * [radius] (clamped to the half sides, as the field does), and its outward direction, into [out].
 */
internal fun roundedBoxSd(px: Float, py: Float, left: Float, top: Float, right: Float, bottom: Float, radius: Float, out: DockSd) {
    val hx = max((right - left) / 2f, 0f)
    val hy = max((bottom - top) / 2f, 0f)
    val r = min(radius, min(hx, hy)).coerceAtLeast(0f)
    val qx = px - (left + right) / 2f
    val qy = py - (top + bottom) / 2f
    val wx = abs(qx) - (hx - r)
    val wy = abs(qy) - (hy - r)
    val sx = if (qx < 0f) -1f else 1f
    val sy = if (qy < 0f) -1f else 1f
    val g = max(wx, wy)
    if (g > 0f) {
        val ox = max(wx, 0f)
        val oy = max(wy, 0f)
        val l = sqrt(ox * ox + oy * oy).coerceAtLeast(1e-6f)
        out.d = l - r
        out.gx = sx * ox / l
        out.gy = sy * oy / l
    } else {
        out.d = g - r
        out.gx = if (wx > wy) sx else 0f
        out.gy = if (wx > wy) 0f else sy
    }
}

/**
 * The joined body's field: box A (the strip) and box B (the swell) as one body, the same field as the glass owner's
 * `LiquidJoin.sample` (that lives on the glass branch; this is the same definition, from
 * `liquid-glass/references/joined-glass.md` and its `scripts/join_shape.py`):
 *
 * ```
 * facing = clamp(0.5 - 0.5 dot(ga, gb), 0, 1)
 * inside = (1 - smoothstep(m/2, m, -da)) (1 - smoothstep(m/2, m, -db))      m = each box's corner radius
 * k      = max(reach x facing x inside, 0.001)
 * h      = max(k - |da - db|, 0) / k
 * d      = min(da, db) - h^3 k / 6
 * ```
 *
 * Its zero line is the body's outline. Reused: [set] the boxes, then sample [d] or [project] points onto the line.
 */
internal class DockJoinField {
    private var aL = 0f
    private var aT = 0f
    private var aR = 0f
    private var aB = 0f
    private var aRad = 0f
    private var bL = 0f
    private var bT = 0f
    private var bR = 0f
    private var bB = 0f
    private var bRad = 0f
    private var reach = 0f
    private val sa = DockSd()
    private val sb = DockSd()

    /** The last [project]ed point and the outline's unit tangent there (walking down the inner side). */
    var px = 0f
        private set
    var py = 0f
        private set
    var tx = 0f
        private set
    var ty = 1f
        private set

    fun set(
        aLeft: Float, aTop: Float, aRight: Float, aBottom: Float, aRadius: Float,
        bLeft: Float, bTop: Float, bRight: Float, bBottom: Float, bRadius: Float,
        joinReach: Float,
    ): DockJoinField {
        aL = aLeft; aT = aTop; aR = aRight; aB = aBottom; aRad = aRadius
        bL = bLeft; bT = bTop; bR = bRight; bB = bBottom; bRad = bRadius
        reach = joinReach
        return this
    }

    /** The field's signed distance at ([x], [y]): negative inside the body, zero on its outline. */
    fun d(x: Float, y: Float): Float {
        roundedBoxSd(x, y, aL, aT, aR, aB, aRad, sa)
        roundedBoxSd(x, y, bL, bT, bR, bB, bRad, sb)
        val da = sa.d
        val db = sb.d
        val facing = (0.5f - 0.5f * (sa.gx * sb.gx + sa.gy * sb.gy)).coerceIn(0f, 1f)
        val ma = min(aRad, min((aR - aL) / 2f, (aB - aT) / 2f)).coerceAtLeast(1e-3f)
        val mb = min(bRad, min((bR - bL) / 2f, (bB - bT) / 2f)).coerceAtLeast(1e-3f)
        val inside = (1f - smoothstep(0.5f * ma, ma, -da)) * (1f - smoothstep(0.5f * mb, mb, -db))
        val k = max(reach * facing * inside, 1e-3f)
        val h = max(k - abs(da - db), 0f) / k
        return min(da, db) - h * h * h * k / 6f
    }

    /**
     * Moves ([x0], [y0]) onto the zero line along the field's gradient (Newton steps, gradient by central differences)
     * and leaves the point in [px]/[py] and the outline's unit tangent in [tx]/[ty]: the direction that walks down the
     * dock's inner side with the body on the right (the gradient turned a quarter: (gy, -gx)).
     */
    fun project(x0: Float, y0: Float) {
        var x = x0
        var y = y0
        var gx = -1f
        var gy = 0f
        for (i in 0 until PROJECT_STEPS) {
            val dv = d(x, y)
            gx = (d(x + GRAD_EPS, y) - d(x - GRAD_EPS, y)) / (2f * GRAD_EPS)
            gy = (d(x, y + GRAD_EPS) - d(x, y - GRAD_EPS)) / (2f * GRAD_EPS)
            val g2 = gx * gx + gy * gy
            if (g2 < 1e-8f || abs(dv) < PROJECT_TOLERANCE) break
            x -= dv * gx / g2
            y -= dv * gy / g2
        }
        val gl = sqrt(gx * gx + gy * gy).coerceAtLeast(1e-6f)
        px = x
        py = y
        tx = gy / gl
        ty = -gx / gl
    }

    private companion object {
        const val PROJECT_STEPS = 10
        const val PROJECT_TOLERANCE = 2e-4f
        const val GRAD_EPS = 0.02f
    }
}

/** The field of the two boxes [a] and [b] joined with [joinReach] at ([x], [y]), for checks (allocates). */
internal fun dockJoinDistance(x: Float, y: Float, a: DockBox, b: DockBox, joinReach: Float): Float =
    DockJoinField().set(a.left, a.top, a.right, a.bottom, a.radius, b.left, b.top, b.right, b.bottom, b.radius, joinReach).d(x, y)

/** B's left edge now: it stands [growth] x [length] out of the strip (growth may pass 1 once, the drop's overshoot). */
internal fun grownLeft(aLeft: Float, length: Float, growth: Float): Float = aLeft - growth * length

/** B's corner radius: fixed (the end corner), only clamped to B's half sides as the field clamps it. */
internal fun swellRadius(height: Float, width: Float): Float = min(min(DockDims.SW_END_R, height * 0.3f), max(width, 0f) / 2f)

/** The join's reach now, for a fitted fillet [blend] and B's [growth] (see [DockDims.JOIN_FADE_IN]). */
internal fun joinReachFor(blend: Float, growth: Float): Float =
    max(DockDims.JOIN_MIN_REACH, DockDims.JOIN_REACH_PER_BLEND * blend * smoothstep(0f, DockDims.JOIN_FADE_IN, growth))

/** Box B at [growth] for the full-grown box [full] beside a strip whose inner edge is [aLeft]: the box as it IS now. */
internal fun grownSwell(full: DockBox, aLeft: Float, growth: Float): DockBox {
    val left = grownLeft(aLeft, aLeft - full.left, growth)
    return DockBox(left, full.top, full.right, full.bottom, swellRadius(full.height, full.right - left))
}

// ---------------------------------------------------------------------------------------------------------------
// The outline: the field's zero line, plus the shoulders into the screen edge.

/**
 * One point of a right-angle cubic smooth-minimum fillet of effective blend width [blend], at parameter [t] in -1..1:
 * returns how far the point lies outside each of the two edges, (da, db). t = -1 is on edge A at [blend] from the
 * corner; t = +1 is on edge B. Tangent and curvature both run out to zero at the ends (the glass skill's cubic kernel,
 * d = min - h^3 k / 6). Used for the shoulders, which are not part of the two-box join.
 */
internal inline fun filletPoint(t: Float, blend: Float, out: (da: Float, db: Float) -> Unit) {
    val h = 1f - abs(t)
    val m = h * h * h * blend / 6f
    val u = t * blend
    if (u >= 0f) out(m + u, m) else out(m, m - u)
}

/** A body outline in reusable arrays (no allocation per frame): points in the edge frame, dp, top to bottom. */
internal class DockOutline(capacity: Int = 1024) {
    /** The join field the swell's part is traced from (reused). */
    val field = DockJoinField()
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    /** Arc length from the first point. */
    val s = FloatArray(capacity)
    var n = 0
        private set
    var length = 0f
        private set
    /** The leftmost x (the tip). */
    var minX = 0f
        private set
    /** Index range of the points that belong to the swell (fillets and B), for tests; -1 when none. */
    var swellFrom = -1
        private set
    var swellTo = -1
        private set

    fun clear() {
        n = 0
        swellFrom = -1
        swellTo = -1
    }

    fun add(px: Float, py: Float) {
        if (n >= x.size) return
        if (n > 0 && abs(px - x[n - 1]) < 1e-3f && abs(py - y[n - 1]) < 1e-3f) return
        x[n] = px
        y[n] = py
        n++
    }

    fun line(px: Float, py: Float, step: Float = 8f) {
        if (n == 0) {
            add(px, py)
            return
        }
        val x0 = x[n - 1]
        val y0 = y[n - 1]
        val d = sqrt((px - x0) * (px - x0) + (py - y0) * (py - y0))
        if (d < 0.05f) return
        val k = max(1, ceil(d / step).toInt())
        for (i in 1..k) add(x0 + (px - x0) * i / k, y0 + (py - y0) * i / k)
    }

    fun arc(cx: Float, cy: Float, r: Float, a0: Float, a1: Float, seg: Int = 16) {
        for (i in 1..seg) {
            val a = a0 + (a1 - a0) * i / seg
            add(cx + r * cos(a), cy + r * sin(a))
        }
    }

    fun markSwell(from: Int, to: Int) {
        swellFrom = from
        swellTo = to
    }

    fun finish() {
        var acc = 0f
        var mn = Float.POSITIVE_INFINITY
        for (i in 0 until n) {
            if (i > 0) acc += sqrt((x[i] - x[i - 1]) * (x[i] - x[i - 1]) + (y[i] - y[i - 1]) * (y[i] - y[i - 1]))
            s[i] = acc
            if (x[i] < mn) mn = x[i]
        }
        length = acc
        minX = if (n == 0) 0f else mn
    }

    /** The index of the point on the strip's side nearest to height [py] (for the failure pulse's start). */
    fun indexNearY(py: Float, maxX: Float): Int {
        var best = 0
        var bd = Float.POSITIVE_INFINITY
        for (i in 0 until n) {
            if (x[i] > maxX) continue
            val d = abs(y[i] - py)
            if (d < bd) {
                bd = d
                best = i
            }
        }
        return best
    }
}

private const val FILLET_STEPS = 24
private const val HALF_PI = (PI / 2).toFloat()
private const val PI_F = PI.toFloat()

/** The walk along the field's zero line: a step of at most this many dp, halved while the line turns more than 8 deg. */
private const val TRACE_STEP = 4f
private const val TRACE_MIN_STEP = 0.25f
private const val TRACE_TURN_COS = 0.9902681f
private const val TRACE_MAX_STEPS = 700

/**
 * Builds the body's outline into [o], from the screen edge above the body, down the inner side, to the screen edge
 * below it (the caller closes it beyond the screen edge at [edgeX]).
 *
 * - With [b] null: the strip [a] alone.
 * - With [b] holding [a] (the list): [b] alone.
 * - Otherwise [b] is the swell AS IT IS NOW (see [grownSwell]) beside [a]: the zero line of the two boxes joined by the
 *   field ([DockJoinField]) with [joinReach], traced from A's inner edge above B round B and back. There is no other
 *   construction: the glass owner's join of the same two boxes with the same reach draws the same outline.
 * - [shoulders]: concave cubic blends of [shoulderReach] from the body's top and bottom edges into the screen edge.
 */
internal fun buildOutline(
    o: DockOutline,
    a: DockBox,
    b: DockBox?,
    joinReach: Float,
    edgeX: Float,
    shoulders: Boolean,
    shoulderReach: Float = DockDims.SHOULDER_REACH,
) {
    val panel = b != null && b.top <= a.top + 0.5f && b.bottom >= a.bottom - 0.5f && b.left <= a.left + 0.5f
    buildOutline(
        o, a.left, a.top, a.right, a.bottom, a.radius,
        b != null, panel, b?.left ?: 0f, b?.top ?: 0f, b?.right ?: 0f, b?.bottom ?: 0f, b?.radius ?: 0f,
        joinReach, edgeX, shoulders, shoulderReach,
    )
}

/** [buildOutline] on plain numbers: the frame loop's allocation-free form. */
internal fun buildOutline(
    o: DockOutline,
    aLeft: Float,
    aTop: Float,
    aRight: Float,
    aBottom: Float,
    aRadius: Float,
    hasB: Boolean,
    panel: Boolean,
    bLeft: Float,
    bTop: Float,
    bRight: Float,
    bBottom: Float,
    bRadius: Float,
    joinReach: Float,
    edgeX: Float,
    shoulders: Boolean,
    shoulderReach: Float = DockDims.SHOULDER_REACH,
) {
    o.clear()
    val swell = hasB && !panel && bLeft < aLeft - 0.01f
    val lo = if (panel) bLeft else aLeft
    val top = if (panel) bTop else aTop
    val bottom = if (panel) bBottom else aBottom
    val radius = if (panel) bRadius else aRadius
    val visible = edgeX - lo
    val r = min(radius, min(max(visible, 0f) / 2f, (bottom - top) / 2f)).coerceAtLeast(0f)
    val ks = if (shoulders) min(shoulderReach, max(0f, visible - r)) else 0f

    // The top shoulder: from the screen edge (above) into the top edge.
    if (ks > 0.5f) {
        for (i in 0..FILLET_STEPS) {
            val t = -1f + 2f * i / FILLET_STEPS
            filletPoint(t, ks) { da, db -> o.add(edgeX - da, top - db) }
        }
    } else {
        o.add(edgeX, top)
    }
    o.line(lo + r, top)
    o.arc(lo + r, top + r, r, -HALF_PI, -PI_F)

    if (swell) {
        val reach = max(joinReach, DockDims.JOIN_MIN_REACH)
        val field = o.field.set(aLeft, aTop, aRight, aBottom, aRadius, bLeft, bTop, bRight, bBottom, bRadius, reach)
        // Beyond one reach from B the field is A's own distance, so A's straight side is exact up to there.
        val yStart = max(top + r, bTop - reach - 2f)
        val yEnd = min(bottom - r, bBottom + reach + 2f)
        o.line(lo, yStart)
        val from = o.n - 1
        traceZeroLine(o, field, lo, yStart, yEnd)
        o.markSwell(from, o.n - 1)
    }
    o.line(lo, bottom - r)
    o.arc(lo + r, bottom - r, r, PI_F, HALF_PI)
    if (ks > 0.5f) {
        o.line(edgeX - ks, bottom)
        for (i in 0..FILLET_STEPS) {
            val t = 1f - 2f * i / FILLET_STEPS
            filletPoint(t, ks) { da, db -> o.add(edgeX - da, bottom + db) }
        }
    } else {
        o.line(edgeX, bottom)
    }
    o.finish()
}

/**
 * Walks the field's zero line from A's inner side at ([x0], [yStart]) down and round B until it is back on A's side at
 * [yEnd] (y never decreases along the way: B is a bump on a vertical side). Predictor-corrector: a step along the
 * tangent, then Newton back onto the line; the step halves while the line turns more than 8 deg per step.
 */
private fun traceZeroLine(o: DockOutline, field: DockJoinField, x0: Float, yStart: Float, yEnd: Float) {
    field.project(x0, yStart)
    var x = field.px
    var y = field.py
    var tx = field.tx
    var ty = field.ty
    o.add(x, y)
    var steps = 0
    while (y < yEnd && steps < TRACE_MAX_STEPS) {
        var step = TRACE_STEP
        while (true) {
            field.project(x + tx * step, y + ty * step)
            if (tx * field.tx + ty * field.ty >= TRACE_TURN_COS || step <= TRACE_MIN_STEP) break
            step *= 0.5f
        }
        // Never past the end (A's corner is drawn as its arc), never into the strip, never back up: the walk stops and
        // A's straight side is drawn on from here.
        if (field.py > yEnd || field.px > x0 + 0.5f || field.py < y - 1f) break
        x = field.px
        y = field.py
        tx = field.tx
        ty = field.ty
        o.add(x, y)
        steps++
    }
}

/**
 * The failure pulse: a small wave that runs along the outline both ways from point [start], [travelled] dp so far,
 * [amp] dp high. Writes the displaced points into [dx]/[dy] (the outline's normal, pointing out of the body).
 */
internal fun pulseOffsets(o: DockOutline, start: Int, travelled: Float, amp: Float, dx: FloatArray, dy: FloatArray) {
    val n = o.n
    if (n < 3) return
    val s0 = o.s[start.coerceIn(0, n - 1)]
    val h1 = s0 + travelled
    val h2 = s0 - travelled
    for (i in 0 until n) {
        dx[i] = 0f
        dy[i] = 0f
        if (i == 0 || i == n - 1) continue
        val d1 = (o.s[i] - h1) / 16f
        val d2 = (o.s[i] - h2) / 16f
        val a = amp * (kotlin.math.exp(-d1 * d1) + kotlin.math.exp(-d2 * d2))
        if (a < 0.02f) continue
        val tx = o.x[i + 1] - o.x[i - 1]
        val ty = o.y[i + 1] - o.y[i - 1]
        val tl = sqrt(tx * tx + ty * ty).coerceAtLeast(1e-4f)
        // Travelling down the inner side, (-ty, tx) points away from the body (out toward the screen's middle).
        dx[i] = -ty / tl * a
        dy[i] = tx / tl * a
    }
}

/** Radius of the circle through three outline points (for tests and the shape checks); huge when collinear. */
internal fun threePointRadius(o: DockOutline, i: Int, k: Int): Float {
    if (i - k < 0 || i + k >= o.n) return Float.POSITIVE_INFINITY
    val ax = o.x[i - k]; val ay = o.y[i - k]
    val bx = o.x[i]; val by = o.y[i]
    val cx = o.x[i + k]; val cy = o.y[i + k]
    val cr = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
    if (abs(cr) < 1e-6f) return Float.POSITIVE_INFINITY
    val a = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
    val bb = sqrt((cx - bx) * (cx - bx) + (cy - by) * (cy - by))
    val c = sqrt((cx - ax) * (cx - ax) + (cy - ay) * (cy - ay))
    return a * bb * c / (2f * abs(cr))
}

/** Rounds to the nearest whole dp (for keys that should only change when something moved visibly). */
internal fun Float.q(): Int = (this * 4f).roundToInt()
