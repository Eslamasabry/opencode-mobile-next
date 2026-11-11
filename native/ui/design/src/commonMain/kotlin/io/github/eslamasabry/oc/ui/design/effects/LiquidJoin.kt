package io.github.eslamasabry.oc.ui.design.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/*
 * Joined glass (docs/designs/liquid-merge-one-body.md, owner 2026-11-11 "Build it quickly"): while a menu is open from a
 * control on the top bar, or the jump pill stands on the message box, the two panes are one body of glass. Each pane
 * draws its own side of one signed-distance field from its own node (`kitLiquidJoinedPane` in `:ui:kit`, whose shader
 * is built from these numbers); this file is the field as plain maths, the reference the shader is tested against.
 *
 * Two directions per box (the note asks for the library's softened one; the prototype's curvature check needs the exact
 * one): the outline, the cover and the kernel's `facing` use the exact outward direction of each rounded box, so the
 * crafted fillet keeps its curvature (shape.py: the softened direction in `facing` multiplies the menu's largest
 * curvature step by about 50); the bend and the rim light use the library's softened direction (corner radius x 1.5),
 * so a pane bends and is lit exactly as the library draws it.
 */

/** A rounded box in px: its centre, its half size and its corner radius (clamped to the half size where used). */
public data class JoinBox(val cx: Float, val cy: Float, val hx: Float, val hy: Float, val r: Float) {
    val left: Float get() = cx - hx
    val top: Float get() = cy - hy
    val right: Float get() = cx + hx
    val bottom: Float get() = cy + hy

    public companion object {
        public fun of(left: Float, top: Float, right: Float, bottom: Float, r: Float): JoinBox =
            JoinBox((left + right) / 2f, (top + bottom) / 2f, (right - left) / 2f, (bottom - top) / 2f, r)
    }
}

/** One pixel of a joined body, as [LiquidJoin.sample] finds it. Mutable so a caller can sample a grid without garbage. */
public class JoinSample {
    /** The joined body's signed distance (inside where negative), not normalised. */
    public var d: Float = 0f

    /** The body's direction for its outline and cover: the exact directions mixed, NOT normalised. */
    public var gx: Float = 0f
    public var gy: Float = 0f

    /** The direction the bend and the rim light use: the library's softened directions, mixed the same way. */
    public var bx: Float = 0f
    public var by: Float = 0f

    /** The partner's share of the direction at this pixel for pane a (0 far away, 0.5 on the line da = db). */
    public var w: Float = 0f

    /** Each box's own signed distance (as the field sees them). */
    public var da: Float = 0f
    public var db: Float = 0f

    /** The partner's share of the material (rim, bend, tint): `smoothstep(-band, band, da - db)` on the real boxes. */
    public var t: Float = 0f

    /** Whether this pixel is pane a's to draw: `da <= db` on the real boxes. */
    public var mine: Boolean = true

    /** The distance divided by the length of the direction: what the cover and the rim light measure. */
    public val dist: Float get() = d / max(sqrt(gx * gx + gy * gy), LiquidJoin.MIN_DIRECTION)

    /** How much of this pixel the body covers (anti-aliased over one pixel), on either side. */
    public val cover: Float get() = (0.5f - dist).coerceIn(0f, 1f)
}

public object LiquidJoin {
    /** The reach of the menu's join with the bar, solved for a 24 dp fillet (shape.py; `facing` and `inside` scale it). */
    public const val REACH_MENU_DP: Float = 134f

    /** The reach of the pill's join with the message box, solved for a 16 dp fillet. */
    public const val REACH_PILL_DP: Float = 56f

    /** Half the band over which rim, bend and tint ease from one pane's to the other's (owner: no line at the join). */
    public const val BAND_DP: Float = 24f

    /** How far a menu's top edge overlaps its bar's bottom edge at rest (shape rule 3). */
    public const val MENU_OVERLAP_DP: Float = 4f

    /** How far the pill is sunk into the message box at rest: a quarter of its 32 dp (shape rule 4). */
    public const val PILL_SINK_DP: Float = 8f

    /** The library's softened direction: corner radius times this. */
    public const val SOFTEN: Float = 1.5f

    /** The shortest a mixed direction is taken to be where cover and rim light divide by it. */
    public const val MIN_DIRECTION: Float = 0.05f

    /** The material ramp `t` (partner's share): 0 deep on this pane's side, 0.5 on the line, 1 deep on the partner's. */
    public fun ramp(da: Float, db: Float, bandPx: Float): Float = smoothstep(-bandPx, bandPx, da - db)

    /** A rounded box's signed distance at (px, py). */
    public fun sdBox(px: Float, py: Float, b: JoinBox): Float {
        val r = min(b.r, min(b.hx, b.hy)).coerceAtLeast(0f)
        val qx = abs(px - b.cx) - b.hx + r
        val qy = abs(py - b.cy) - b.hy + r
        val ox = max(qx, 0f)
        val oy = max(qy, 0f)
        return sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - r
    }

    /**
     * A rounded box's outward direction at (px, py), with its corner rounded by `radius x soften` (1: the exact one,
     * [SOFTEN]: the library's `gradSdRoundedRect`), written to [out] (x, y). Zero on the exact centre lines, as the
     * library's `sign` gives.
     */
    public fun direction(px: Float, py: Float, b: JoinBox, soften: Float, out: FloatArray) {
        val gr = min(min(b.r, min(b.hx, b.hy)).coerceAtLeast(0f) * soften, min(b.hx, b.hy))
        val rx = px - b.cx
        val ry = py - b.cy
        val qx = abs(rx) - (b.hx - gr)
        val qy = abs(ry) - (b.hy - gr)
        val sx = sign(rx)
        val sy = sign(ry)
        if (qx >= 0f || qy >= 0f) {
            val mx = max(qx, 0f)
            val my = max(qy, 0f)
            val l = max(sqrt(mx * mx + my * my), 1e-6f)
            out[0] = sx * mx / l
            out[1] = sy * my / l
        } else {
            val g = if (qx >= qy) 1f else 0f
            out[0] = sx * g
            out[1] = sy * (1f - g)
        }
    }

    /**
     * The joined body of pane [a] and its partner [b] at (px, py), seen from [a]: reach [reachPx], material band
     * [bandPx]. [aSide] and [bSide] are the panes' real boxes, which decide whose side a pixel is and the material ramp;
     * [a] and [b] are the boxes as the field sees them (a menu reaches up to its bar's centre line: [fieldBox]).
     * With reach 0 the body is `min(da, db)`: two separate panes.
     */
    public fun sample(
        px: Float,
        py: Float,
        a: JoinBox,
        b: JoinBox,
        reachPx: Float,
        bandPx: Float,
        aSide: JoinBox = a,
        bSide: JoinBox = b,
        out: JoinSample = JoinSample(),
    ): JoinSample {
        val ga = FloatArray(2)
        val gb = FloatArray(2)
        val da = sdBox(px, py, a)
        val db = sdBox(px, py, b)
        direction(px, py, a, 1f, ga)
        direction(px, py, b, 1f, gb)
        val facing = (0.5f - 0.5f * (ga[0] * gb[0] + ga[1] * gb[1])).coerceIn(0f, 1f)
        val inside = (1f - smoothstep(0.5f * depthOf(a), depthOf(a), -da)) * (1f - smoothstep(0.5f * depthOf(b), depthOf(b), -db))
        val k = max(reachPx * facing * inside, 0.001f)
        val h = max(k - abs(da - db), 0f) / k
        val m = h * h * 0.5f
        val w = if (da <= db) m else 1f - m
        out.d = min(da, db) - h * h * h * k / 6f
        out.gx = ga[0] + (gb[0] - ga[0]) * w
        out.gy = ga[1] + (gb[1] - ga[1]) * w
        direction(px, py, a, SOFTEN, ga)
        direction(px, py, b, SOFTEN, gb)
        out.bx = ga[0] + (gb[0] - ga[0]) * w
        out.by = ga[1] + (gb[1] - ga[1]) * w
        out.w = w
        out.da = da
        out.db = db
        val sa = if (aSide === a) da else sdBox(px, py, aSide)
        val sb = if (bSide === b) db else sdBox(px, py, bSide)
        out.t = ramp(sa, sb, bandPx)
        out.mine = sa <= sb
        return out
    }

    /** How deep the `inside` fade reaches into a box: its corner radius, at most half its shorter side (never 0). */
    internal fun depthOf(b: JoinBox): Float = max(min(b.r, min(b.hx, b.hy)), 0.001f)

    /**
     * [drop] as the field sees it next to its [body] (shape rule 3): it reaches to the body's centre line (up into a bar
     * above it, down into a message box below it), inside the body, so only an aligned flank changes: it becomes one
     * straight line from the body's end cap into the drop's side.
     */
    public fun fieldBox(drop: JoinBox, body: JoinBox): JoinBox =
        if (drop.cy >= body.cy) JoinBox.of(drop.left, min(drop.top, body.cy), drop.right, drop.bottom, drop.r)
        else JoinBox.of(drop.left, drop.top, drop.right, max(drop.bottom, body.cy), drop.r)

    /** The library's lens profile: how far a pixel [depth] px inside a rim [rim] px wide is bent, of [refraction] px. */
    public fun bendPx(depth: Float, rim: Float, refraction: Float): Float {
        if (rim <= 0f || depth >= rim) return 0f
        val x = 1f - max(depth, 0f) / rim
        return (1f - sqrt(max(1f - x * x, 0f))) * refraction
    }

    public fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /*
     * Motion (the note's "Drop, rebound, fill"; prototype motion2.py). Progress values run 0 to 1; a spring is given by
     * its damping ratio and stiffness (mass 1, the same as Compose's spring).
     *
     * Owner 2026-11-11, on the phone: "can we slow things down a little bit". Every spring here is about a third
     * slower than the first build (stiffness / 1.8, the damping ratios unchanged so the 8 % rebound is the same), and
     * the waits between the steps are longer by the same share.
     */

    /** The menu's column falls on this spring and passes its resting line by about 8 %. */
    public const val DROP_ZETA: Float = .62f
    public const val DROP_K: Float = 500f

    /** The width fills on this calm spring, [FILL_AFTER_TURN_MS] after the drop turns. */
    public const val FILL_ZETA: Float = .92f
    public const val FILL_K: Float = 500f
    public const val FILL_AFTER_TURN_MS: Int = 27

    /** Closing: the width empties first (critically damped), then the column is drawn up [DRAW_UP_AFTER_MS] later. */
    public const val EMPTY_K: Float = 1300f
    public const val DRAW_UP_K: Float = 1100f
    public const val DRAW_UP_AFTER_MS: Int = 95

    /** The column a menu falls as: the width of a bar control. */
    public const val COLUMN_DP: Float = 56f

    /** The reach is up to this share more than at rest while the column falls (menu), easing to rest. */
    public const val MENU_REACH_SWELL: Float = .5f

    /** The pill rises this far from inside the box, narrow, on (DROP_ZETA, PILL_RISE_K). */
    public const val PILL_RISE_DP: Float = 44f
    public const val PILL_RISE_K: Float = 440f
    public const val PILL_DRAW_UP_AFTER_MS: Int = 80

    /** The pill's half width while it rises, before it widens. */
    public const val PILL_HALF_DP: Float = 24f

    /** The pill's reach swell while it rises. */
    public const val PILL_REACH_SWELL: Float = .4f

    /** The pill's label shows from this share of its width (fully by 0.25 later). */
    public const val LABEL_FROM: Float = .7f

    /** A menu's rows ease in over this many dp behind the glass's moving edge; no word is drawn outside the glass. */
    public const val ROW_EASE_DP: Float = 14f

    /**
     * A menu's rows show only once the glass starts to fill: over the first [ROWS_FROM_FILL] of the fill opening, and
     * until [ROWS_UNTIL_FILL] of it is left closing. While the column is still falling no word shows in it (on the
     * phone, 2026-11-11, the ends of the rows stood in the bare column: "ion", "ript").
     */
    public const val ROWS_FROM_FILL: Float = .08f
    public const val ROWS_UNTIL_FILL: Float = .5f

    /** How long a closing join takes at most (the width empties, then the column is drawn up), in ms. */
    public const val CLOSE_MS: Int = 400

    /** When an under-damped spring of [zeta] and [k] first reaches its peak, in ms. */
    public fun turnMs(zeta: Float, k: Float): Float = (PI / (sqrt(k.toDouble()) * sqrt(1.0 - zeta * zeta))).toFloat() * 1000f

    /**
     * The glass of a dropping menu (or a rising pill) now, from its box at rest: [column] is the column's left and right
     * edges (a control's width under the control, or at the outer edge), [length] the drop's progress (passing 1 once),
     * [fill] the fill's progress. Upwards ([up]) the column grows from the rest box's bottom edge.
     */
    public fun dropBox(rest: JoinBox, columnLeft: Float, columnRight: Float, length: Float, fill: Float, up: Boolean = false): JoinBox {
        val f = fill.coerceIn(0f, 1f)
        // While the column is past its resting line it is narrower by the same share.
        val slim = 1f - max(length - 1f, 0f)
        var l = columnLeft + (rest.left - columnLeft) * f
        var r = columnRight + (rest.right - columnRight) * f
        val c = (l + r) / 2f
        l = c + (l - c) * slim
        r = c + (r - c) * slim
        val h = rest.hy * 2f * max(length, 0f)
        val top = if (up) rest.bottom - h else rest.top
        val bottom = if (up) rest.bottom else rest.top + h
        val radius = min(rest.r, min((r - l) / 2f, h / 2f)).coerceAtLeast(0f)
        return JoinBox.of(l, top, r, bottom, radius)
    }

    /**
     * Where a menu opened from a control in [anchorLeft]..[anchorRight] on [body] falls (shape rule 3): its column's left
     * and right edges for a panel [width] wide. A control near an end of the body puts the column, and the panel, flush
     * with that end; otherwise the column stands under the control.
     */
    public fun menuColumn(body: JoinBox, anchorLeft: Float, anchorRight: Float, columnPx: Float): Pair<Float, Float> {
        val centre = (anchorLeft + anchorRight) / 2f
        val end = body.hx * 2f * END_SHARE
        return when {
            body.right - anchorRight <= end -> (body.right - columnPx) to body.right
            anchorLeft - body.left <= end -> body.left to (body.left + columnPx)
            else -> (centre - columnPx / 2f) to (centre + columnPx / 2f)
        }
    }

    /**
     * The menu panel's box at rest next to its [body] (shape rules 3): its top overlaps the body's bottom by
     * [overlapPx], and a column flush with one end of the body puts the panel's outer edge on that end; otherwise the
     * panel keeps its [placed] left edge (kept inside the column's reach). Returns the panel's new left and top.
     */
    public fun menuRest(body: JoinBox, placed: JoinBox, columnLeft: Float, columnRight: Float, overlapPx: Float): Pair<Float, Float> {
        val width = placed.hx * 2f
        val left =
            when {
                columnRight >= body.right - 0.5f -> body.right - width
                columnLeft <= body.left + 0.5f -> body.left
                else -> placed.left.coerceIn(columnRight - width, columnLeft)
            }
        return left to (body.bottom - overlapPx)
    }

    /** A control within this share of the body's width from one of its ends counts as at that end. */
    public const val END_SHARE: Float = .25f

    /*
     * The material (owner 2026-11-11, "Yeah bright content held back is readable"): inside a joined pane's lens the
     * glass keeps only a share of the light of content brighter than a knee, so words on the glass read over a bright
     * photo without more blur, a thicker tint or a plate. Under light words (a dark theme) it holds the bright end; under
     * dark words (a light theme, or words flipped to dark ink) it lifts the dark end the same way.
     */

    /** The knee (luminance) past which the glass holds back light. */
    public const val KNEE: Float = .36f

    /** The share of the light past the knee the glass keeps. */
    public const val KNEE_SLOPE: Float = .22f

    /**
     * [lum] (0-1) through the knee: under [lightInk] light above [KNEE] is kept at [KNEE_SLOPE] of itself; under dark
     * ink dark below `1 - KNEE` is lifted the same way. The shader applies it per pixel as a scale of the colour.
     */
    public fun held(lum: Float, lightInk: Boolean): Float =
        if (lightInk) {
            if (lum > KNEE) KNEE + (lum - KNEE) * KNEE_SLOPE else lum
        } else {
            val inv = 1f - lum
            1f - if (inv > KNEE) KNEE + (inv - KNEE) * KNEE_SLOPE else inv
        }

    /** How much brighter a pane's second line is on glass than on the page: this share of the way to its first line. */
    public const val SECOND_INK_ON_GLASS: Float = .6f
}
