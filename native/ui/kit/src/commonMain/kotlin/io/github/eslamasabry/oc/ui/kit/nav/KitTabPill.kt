package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The selection pill's place as pure numbers. A strip's tabs lie at [lefts] with [widths] (px, physical: the first tab
 * is the rightmost one in Arabic); `position` is where the pages are, in tabs (1.5 = half-way between the second and
 * the third). Everything here is a function of that one number, so the pill is wherever the finger put the pages, in
 * both directions, with nothing to settle afterwards.
 *
 * Plain: both edges move with the fraction (Calm, Off). Elastic (Full): the edge facing the next tab runs ahead of the
 * fraction ([lead]) and the other one stays behind ([trail]), so the pill stretches across the gap and gathers itself
 * on the tab it reaches. At a whole position both give exactly the tab.
 */
public object TabPill {
    /** The trailing edge's share of a step: slow to leave, quick to arrive. */
    public fun trail(f: Float): Float {
        val x = f.coerceIn(0f, 1f)
        return x * sqrt(x)
    }

    /** The leading edge's share of a step: the mirror of [trail], so a swipe back looks like the swipe forward. */
    public fun lead(f: Float): Float = 1f - trail(1f - f)

    /** How far apart the two edges' shares are at [position] (0 on a tab, about .3 half-way): how stretched the pill is. */
    public fun stretch(position: Float, elastic: Boolean): Float {
        if (!elastic) return 0f
        val f = position - floor(position)
        return lead(f) - trail(f)
    }

    /** The pill's left edge at [position]. */
    public fun left(lefts: FloatArray, widths: FloatArray, position: Float, elastic: Boolean): Float =
        edge(lefts, widths, position, elastic, right = false)

    /** The pill's right edge at [position]. */
    public fun right(lefts: FloatArray, widths: FloatArray, position: Float, elastic: Boolean): Float =
        edge(lefts, widths, position, elastic, right = true)

    private fun edge(lefts: FloatArray, widths: FloatArray, position: Float, elastic: Boolean, right: Boolean): Float {
        val n = min(lefts.size, widths.size)
        if (n == 0) return 0f
        val p = position.coerceIn(0f, (n - 1).toFloat())
        val i = min(p.toInt(), n - 2).coerceAtLeast(0)
        val a = lefts[i] + if (right) widths[i] else 0f
        if (n == 1) return a
        val b = lefts[i + 1] + if (right) widths[i + 1] else 0f
        val f = p - i
        if (!elastic) return a + (b - a) * f
        // The next tab lies to the right in English and to the left in Arabic: the edge on that side leads.
        val nextIsRight = lefts[i + 1] >= lefts[i]
        val leads = right == nextIsRight
        return a + (b - a) * if (leads) lead(f) else trail(f)
    }

    /**
     * How far the strip is scrolled from its start (the right in Arabic) so the pill sits in the middle of the
     * [viewport], never past either end of the [content]. A function of the pill alone, so the strip follows a drag
     * frame for frame and returns with it.
     */
    public fun stripScroll(pillLeft: Float, pillRight: Float, viewport: Float, content: Float, rtl: Boolean): Float {
        val max = (content - viewport).coerceAtLeast(0f)
        val centre = (pillLeft + pillRight) / 2f
        val fromStart = if (rtl) content - centre else centre
        return (fromStart - viewport / 2f).coerceIn(0f, max)
    }
}

/** Where a strip's tabs lie, written by its layout; a new one only when a tab moved or changed size. */
internal class TabSlots(val lefts: FloatArray, val widths: FloatArray, val height: Float, val content: Float) {
    fun same(l: FloatArray, w: FloatArray, h: Float, c: Float): Boolean =
        height == h && content == c && lefts.contentEquals(l) && widths.contentEquals(w)

    companion object {
        val Empty: TabSlots = TabSlots(FloatArray(0), FloatArray(0), 0f, 0f)
    }
}

/**
 * One strip's pill, shared by the parts that draw it: the face, the labels outside it and the labels on it. The layout
 * writes [slots]; every reader asks [measure] in its own draw or layout pass and reads the edges back, so a frame of a
 * drag allocates nothing and composes nothing.
 */
@Stable
internal class TabPillModel {
    var slots: TabSlots by mutableStateOf(TabSlots.Empty)

    /** The pill's box in the strip's content, px. */
    var left: Float = 0f
        private set
    var right: Float = 0f
        private set
    var top: Float = 0f
        private set
    var bottom: Float = 0f
        private set
    var radius: Float = 0f
        private set

    /** The clip both label layers share; rebuilt in place. Made on first use, so the numbers above need no canvas. */
    val path: Path by lazy(LazyThreadSafetyMode.NONE) { Path() }

    /**
     * Sets the pill's box for [position]. [inset] is the room it keeps above and below inside the tab's box, [squash]
     * how much thinner it gets when fully stretched, [corner] its corner radius (negative = a full round end), all px.
     * [snap] puts the edges on whole pixels (a face that is a laid-out box can only stand on those).
     */
    fun measure(position: Float, elastic: Boolean, inset: Float, squash: Float, corner: Float, snap: Boolean) {
        val s = slots
        var l = TabPill.left(s.lefts, s.widths, position, elastic)
        var r = TabPill.right(s.lefts, s.widths, position, elastic)
        val thin = squash * TabPill.stretch(position, elastic) / MAX_STRETCH
        var t = inset + thin
        var b = s.height - inset - thin
        if (snap) {
            l = floor(l + .5f)
            r = floor(r + .5f)
            t = floor(t + .5f)
            b = floor(b + .5f)
        }
        left = l
        right = r
        top = t
        bottom = b
        val round = (b - t) / 2f
        radius = if (corner < 0f) round else min(corner, round)
    }

    /** Rebuilds [path] as the pill moved by ([dx], [dy]): the same box seen from a tab's own corner. */
    fun outline(dx: Float, dy: Float): Path {
        pillPath(path, left + dx, top + dy, right + dx, bottom + dy, radius)
        return path
    }

    companion object {
        /** [TabPill.stretch] at its most (half-way between two tabs). */
        val MAX_STRETCH: Float = TabPill.stretch(.5f, elastic = true)
    }
}

/** A rounded box drawn with floats only (no geometry objects), so a frame of a drag allocates nothing. */
internal fun pillPath(path: Path, l: Float, t: Float, r: Float, b: Float, radius: Float) {
    path.rewind()
    if (r <= l || b <= t) return
    val rr = min(radius, min((r - l) / 2f, (b - t) / 2f))
    if (rr <= .5f) {
        path.moveTo(l, t)
        path.lineTo(r, t)
        path.lineTo(r, b)
        path.lineTo(l, b)
        path.close()
        return
    }
    val k = rr * CIRCLE_K
    path.moveTo(l + rr, t)
    path.lineTo(r - rr, t)
    path.cubicTo(r - rr + k, t, r, t + rr - k, r, t + rr)
    path.lineTo(r, b - rr)
    path.cubicTo(r, b - rr + k, r - rr + k, b, r - rr, b)
    path.lineTo(l + rr, b)
    path.cubicTo(l + rr - k, b, l, b - rr + k, l, b - rr)
    path.lineTo(l, t + rr)
    path.cubicTo(l, t + rr - k, l + rr - k, t, l + rr, t)
    path.close()
}

/** The cubic that stands in for a quarter circle. */
private const val CIRCLE_K: Float = 0.5522848f

/**
 * A tap's travel. The pages slide one page at most (a far tab's neighbour is brought next to it first), and the pill
 * crosses every tab on the way in that same slide: it goes from [from] to [to] as the pages go from [via] to [to].
 * Still one number in, one place out, so the pill and the pages cannot drift apart.
 */
internal class TabTravel(val from: Float, val via: Float, val to: Float) {
    fun at(pages: Float): Float {
        val span = to - via
        if (abs(span) < 1e-3f) return to
        val t = ((pages - via) / span).coerceIn(0f, 1f)
        return from + (to - from) * t
    }
}
