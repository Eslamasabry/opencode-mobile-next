package io.github.eslamasabry.oc.ui.design.effects

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Joined glass (docs/designs/liquid-merge-one-body.md section 4): the field one joined body is drawn from. No catalog
 * row yet (the design note names these checks); the layouts are the prototype's (shape.py), in dp with 1 px = 1 dp.
 */
class LiquidJoinTest {
    private val bar = JoinBox(180f, 52f, 168f, 28f, 28f)

    /** The menu at rest: its right edge on the bar's right edge, its top 4 dp into the bar. */
    private val menu = JoinBox(248f, 52f + 28f - 4f + 115f, 100f, 115f, 24f)
    private val box = JoinBox(180f, 320f, 168f, 52f, 28f)

    /** The pill at rest: a capsule sunk a quarter of its height (8 dp of 32) into the box. */
    private val pill = JoinBox(180f, 320f - 52f + 8f - 16f, 76f, 16f, 16f)

    /** A menu opened upwards from a chip on the message box (design phase 3's layout): its left edge on the box's. */
    private val chipMenu = JoinBox(12f + 100f, 320f - 52f + 4f - 115f, 100f, 115f, 24f)

    private val menuField = LiquidJoin.fieldBox(menu, bar)
    private val chipField = LiquidJoin.fieldBox(chipMenu, box)
    private val band = LiquidJoin.BAND_DP

    private fun d(x: Float, y: Float, a: JoinBox, b: JoinBox, reach: Float): Float = LiquidJoin.sample(x, y, a, b, reach, band).d

    @Test
    fun `liquid join - with reach 0 the body is min(da, db)`() {
        val rnd = Random(7)
        repeat(4000) {
            val x = rnd.nextFloat() * 360f
            val y = rnd.nextFloat() * 420f
            val s = LiquidJoin.sample(x, y, box, pill, 0f, band)
            assertEquals(min(LiquidJoin.sdBox(x, y, box), LiquidJoin.sdBox(x, y, pill)), s.d, 1e-3f, "at ($x, $y)")
        }
    }

    @Test
    fun `liquid join - a drop inside its body leaves the body's outline unchanged`() {
        // The pill as it starts to rise: 44 dp below its rest, half its width, inside the box (facing is 0 there).
        val start = JoinBox(pill.cx, pill.cy + LiquidJoin.PILL_RISE_DP, LiquidJoin.PILL_HALF_DP, 16f, 16f)
        assertTrue(start.top > box.top && start.bottom < box.bottom, "the drop starts inside its body")
        for (y in 200..420) for (x in 0..360) {
            val da = LiquidJoin.sdBox(x.toFloat(), y.toFloat(), box)
            if (da < -2f) continue
            val s = LiquidJoin.sample(x.toFloat(), y.toFloat(), box, start, LiquidJoin.REACH_PILL_DP, band)
            assertEquals(da, s.d, 0.01f, "the box's outline and outside at ($x, $y)")
        }
    }

    @Test
    fun `liquid join - d has no step across both boxes of each rest layout`() {
        // A tear (the direction stepping on a centre line with no `inside` fade) jumps by up to 12 px between
        // neighbouring pixels in the menu's layout; the joined field changes by at most its steepest gradient.
        for ((name, a, b, reach) in layouts()) {
            var most = 0f
            for (y in 0..420) {
                var left = d(0f, y.toFloat(), a, b, reach)
                for (x in 1..360) {
                    val here = d(x.toFloat(), y.toFloat(), a, b, reach)
                    val below = d(x.toFloat(), y + 1f, a, b, reach)
                    most = max(most, max(abs(here - left), abs(below - here)))
                    left = here
                }
            }
            assertTrue(most <= MAX_STEP_PER_PX, "$name: the largest change between neighbouring pixels is $most px")
        }
    }

    @Test
    fun `liquid join - the cover is one connected region for the three rest layouts`() {
        for ((name, a, b, reach) in layouts()) {
            val w = 361
            val h = 421
            val inside = BooleanArray(w * h) { i -> LiquidJoin.sample((i % w).toFloat(), (i / w).toFloat(), a, b, reach, band).cover > .5f }
            val seen = BooleanArray(w * h)
            var regions = 0
            for (start in inside.indices) {
                if (!inside[start] || seen[start]) continue
                regions++
                val stack = ArrayDeque<Int>()
                stack.addLast(start)
                seen[start] = true
                while (stack.isNotEmpty()) {
                    val i = stack.removeLast()
                    val x = i % w
                    val y = i / w
                    for ((nx, ny) in listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val j = ny * w + nx
                        if (inside[j] && !seen[j]) {
                            seen[j] = true
                            stack.addLast(j)
                        }
                    }
                }
            }
            assertEquals(1, regions, "$name: one body")
        }
    }

    @Test
    fun `liquid join - every mixed value is the same from both sides`() {
        val rnd = Random(11)
        for ((name, a, b, reach) in layouts()) {
            repeat(3000) {
                val x = rnd.nextFloat() * 360f
                val y = rnd.nextFloat() * 420f
                val fromA = LiquidJoin.sample(x, y, a, b, reach, band)
                val fromB = LiquidJoin.sample(x, y, b, a, reach, band)
                assertEquals(fromA.d, fromB.d, 1e-3f, "$name d at ($x, $y)")
                assertEquals(fromA.gx, fromB.gx, 1e-4f, "$name gx")
                assertEquals(fromA.gy, fromB.gy, 1e-4f, "$name gy")
                assertEquals(fromA.bx, fromB.bx, 1e-4f, "$name bend x")
                assertEquals(fromA.by, fromB.by, 1e-4f, "$name bend y")
                assertEquals(1f, fromA.t + fromB.t, 1e-4f, "$name material ramp")
                if (abs(fromA.da - fromA.db) > 1e-3f) assertTrue(fromA.mine != fromB.mine, "$name: each pixel is one pane's")
            }
        }
    }

    @Test
    fun `liquid join - on the line da = db the material is half each pane's and the direction half each`() {
        // Along the bar's bottom edge, half way into the menu's overlap: the two are equally near.
        for (x in listOf(200f, 250f, 300f)) {
            var y = 60f
            while (LiquidJoin.sdBox(x, y, bar) < LiquidJoin.sdBox(x, y, menu)) y += .01f
            val s = LiquidJoin.sample(x, y, bar, menu, LiquidJoin.REACH_MENU_DP, band)
            assertEquals(.5f, s.t, .01f, "the ramp at ($x, $y)")
        }
    }

    @Test
    fun `liquid join - the crafted fillets have the radius the shape rules ask for and no curvature kink`() {
        // shape.py, cubic kernel: the menu's concave corner 24 dp, its largest curvature step .0011 /dp; the pill's
        // flank 16 dp, .0121 /dp. The quadratic kernel steps 3 and 6 times higher.
        val menuWindow = floatArrayOf(120f, 70f, 175f, 125f)
        val pillWindow = floatArrayOf(70f, 240f, 120f, 285f)
        val (menuRadius, menuStep) = outline(bar, menuField, LiquidJoin.REACH_MENU_DP, menuWindow)
        val (pillRadius, pillStep) = outline(box, pill, LiquidJoin.REACH_PILL_DP, pillWindow)
        assertEquals(24f, menuRadius, 1.5f, "the menu's fillet")
        assertEquals(16f, pillRadius, 1.5f, "the pill's flanks")
        assertTrue(menuStep <= .0022f, "the menu's largest curvature step $menuStep /dp")
        assertTrue(pillStep <= .024f, "the pill's largest curvature step $pillStep /dp")
    }

    @Test
    fun `liquid join - the menu's field reaches the bar's centre line and its aligned flank is one straight line`() {
        assertEquals(bar.cy, menuField.top, 1e-4f)
        assertEquals(menu.bottom, menuField.bottom, 1e-4f)
        // The right flank: from the bar's end cap straight down into the menu's side, no waist anywhere along it (the
        // prototype measures it within 1.2 dp of the bar's right edge, never inside it).
        for (y in 52..140) {
            var x = 300f
            while (d(x, y.toFloat(), bar, menuField, LiquidJoin.REACH_MENU_DP) < 0f) x += .05f
            assertTrue(x >= bar.right - .5f && x <= bar.right + 1.5f, "the flank at y $y is at $x")
        }
        // Upwards from a chip: the field reaches down to the box's centre line.
        assertEquals(box.cy, chipField.bottom, 1e-4f)
    }

    @Test
    fun `liquid join - the drop turns at about 179 ms and its column fills from the outer edge`() {
        assertEquals(179f, LiquidJoin.turnMs(LiquidJoin.DROP_ZETA, LiquidJoin.DROP_K), 1.5f)
        val (l, r) = LiquidJoin.menuColumn(bar, 320f, 344f, LiquidJoin.COLUMN_DP)
        assertEquals(bar.right, r, 1e-4f, "a control at the bar's end drops its column flush with the end")
        assertEquals(bar.right - LiquidJoin.COLUMN_DP, l, 1e-4f)
        val (left, top) = LiquidJoin.menuRest(bar, JoinBox(200f, 200f, 100f, 115f, 24f), l, r, LiquidJoin.MENU_OVERLAP_DP)
        assertEquals(bar.right - 200f, left, 1e-4f, "the menu's outer edge on the bar's")
        assertEquals(bar.bottom - LiquidJoin.MENU_OVERLAP_DP, top, 1e-4f, "its top 4 dp into the bar")
        val column = LiquidJoin.dropBox(menu, l, r, length = 1f, fill = 0f)
        assertEquals(r, column.right, 1e-4f)
        assertEquals(LiquidJoin.COLUMN_DP, column.hx * 2f, 1e-3f)
        val past = LiquidJoin.dropBox(menu, l, r, length = 1.08f, fill = 0f)
        assertEquals(menu.hy * 2f * 1.08f, past.hy * 2f, 1e-3f, "passes its resting line")
        assertEquals(LiquidJoin.COLUMN_DP * .92f, past.hx * 2f, 1e-3f, "narrower by the same share")
        assertEquals(menu, LiquidJoin.dropBox(menu, l, r, length = 1f, fill = 1f).copy(r = menu.r), "at rest: the menu's box")
        val (cl, cr) = LiquidJoin.menuColumn(bar, 170f, 194f, LiquidJoin.COLUMN_DP)
        assertEquals(182f, (cl + cr) / 2f, 1e-4f, "a control in the middle drops its column under itself")
    }

    @Test
    fun `liquid join material - bright content is held back under light words, dark content lifted under dark words`() {
        assertEquals(.2f, LiquidJoin.held(.2f, lightInk = true), 1e-6f, "below the knee: unchanged")
        assertEquals(LiquidJoin.KNEE + (.9f - LiquidJoin.KNEE) * LiquidJoin.KNEE_SLOPE, LiquidJoin.held(.9f, lightInk = true), 1e-6f)
        assertEquals(.50f, LiquidJoin.held(1f, lightInk = true), .01f, "white behind light words keeps about half its light")
        assertEquals(.8f, LiquidJoin.held(.8f, lightInk = false), 1e-6f, "above the mirrored knee: unchanged")
        assertEquals(.50f, LiquidJoin.held(0f, lightInk = false), .01f, "black behind dark words is lifted to about half")
        for (i in 0..100) {
            val l = i / 100f
            assertEquals(1f - LiquidJoin.held(1f - l, lightInk = true), LiquidJoin.held(l, lightInk = false), 1e-5f, "mirrored at $l")
            if (i > 0) assertTrue(LiquidJoin.held(l, true) >= LiquidJoin.held(l - .01f, true), "keeps the order of brightness")
        }
    }

    @Test
    fun `liquid join material - the blur is cut and the saturation kept`() {
        // Owner 2026-11-11: "Cut the blur and leave the saturation as is."
        for (dark in listOf(true, false)) {
            assertEquals(3f, LiquidEdge.recipe(LiquidRole.Bar, dark).blurDp)
            assertEquals(2f, LiquidEdge.recipe(LiquidRole.Composer, dark).blurDp)
            assertEquals(LiquidEdge.MODAL_BLUR_DP, LiquidEdge.recipe(LiquidRole.Menu, dark).blurDp, "menus frost as sheets do, the bar's clear glass (glass liquid)")
            assertEquals(1f, LiquidEdge.recipe(LiquidRole.Pill, dark).blurDp)
            assertEquals(1f, LiquidEdge.recipe(LiquidRole.Status, dark).blurDp)
            assertEquals(LiquidEdge.MODAL_BLUR_DP, LiquidEdge.recipe(LiquidRole.Sheet, dark).blurDp, "sheets as menus")
            assertEquals(if (dark) 6f else 3f, LiquidEdge.swellRecipe(dark).blurDp, "the dock's swell keeps the menu's old glass")
            for (role in LiquidRole.entries) assertEquals(1.6f, LiquidEdge.recipe(role, dark).saturation, "$role saturation")
        }
    }

    private data class Layout(val name: String, val a: JoinBox, val b: JoinBox, val reach: Float)

    private fun layouts(): List<Layout> =
        listOf(
            Layout("menu on the bar", bar, menuField, LiquidJoin.REACH_MENU_DP),
            Layout("pill on the box", box, pill, LiquidJoin.REACH_PILL_DP),
            Layout("menu on the box", box, chipField, LiquidJoin.REACH_MENU_DP),
        )

    /**
     * shape.py's measure of the outline d = 0 inside [window] (x0, y0, x1, y1 in dp), sampled 8 times per dp: its
     * tightest concave radius (the 2nd percentile of the curvature) and its largest curvature step along the outline
     * (neighbouring samples ordered round the window's outline, smoothed over 5).
     */
    private fun outline(a: JoinBox, b: JoinBox, reach: Float, window: FloatArray): Pair<Float, Float> {
        val step = 1f / 8f
        val w = ((window[2] - window[0]) / step).toInt()
        val h = ((window[3] - window[1]) / step).toInt()
        val d = Array(h) { r -> FloatArray(w) { c -> d(window[0] + c * step, window[1] + r * step, a, b, reach) } }
        fun gradient(f: Array<FloatArray>, axis: Int): Array<FloatArray> =
            Array(h) { r ->
                FloatArray(w) { c ->
                    if (axis == 1) {
                        when (c) {
                            0 -> (f[r][1] - f[r][0]) / step
                            w - 1 -> (f[r][w - 1] - f[r][w - 2]) / step
                            else -> (f[r][c + 1] - f[r][c - 1]) / (2 * step)
                        }
                    } else {
                        when (r) {
                            0 -> (f[1][c] - f[0][c]) / step
                            h - 1 -> (f[h - 1][c] - f[h - 2][c]) / step
                            else -> (f[r + 1][c] - f[r - 1][c]) / (2 * step)
                        }
                    }
                }
            }
        val gx = gradient(d, 1)
        val gy = gradient(d, 0)
        val nx = Array(h) { r -> FloatArray(w) { c -> gx[r][c] / max(sqrt(gx[r][c] * gx[r][c] + gy[r][c] * gy[r][c]), 1e-6f) } }
        val ny = Array(h) { r -> FloatArray(w) { c -> gy[r][c] / max(sqrt(gx[r][c] * gx[r][c] + gy[r][c] * gy[r][c]), 1e-6f) } }
        val dnx = gradient(nx, 1)
        val dny = gradient(ny, 0)
        val on = ArrayList<Triple<Int, Int, Float>>()
        for (r in 0 until h) for (c in 0 until w) if (abs(d[r][c]) < step * .75f) on += Triple(r, c, dnx[r][c] + dny[r][c])
        val kappas = on.map { it.third }.sorted()
        val radius = if (kappas.isEmpty() || kappas.first() >= 0f) Float.POSITIVE_INFINITY else 1f / -percentile(kappas, 2f)
        val mr = on.map { it.first }.average()
        val mc = on.map { it.second }.average()
        val ordered = on.sortedBy { atan2(it.first - mr, it.second - mc) }.map { it.third }
        val smooth = (0..ordered.size - 5).map { i -> (i until i + 5).sumOf { ordered[it].toDouble() }.toFloat() / 5f }
        val jump = smooth.zipWithNext { p, q -> abs(q - p) }.maxOrNull() ?: 0f
        return radius to jump
    }

    /** numpy's default (linear) percentile of an ascending list. */
    private fun percentile(sorted: List<Float>, p: Float): Float {
        val at = (sorted.size - 1) * p / 100f
        val lo = at.toInt()
        val hi = min(lo + 1, sorted.size - 1)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (at - lo)
    }

    private companion object {
        /** The steepest the joined field gets between neighbouring pixels (2.52 px in the menu's layout). */
        const val MAX_STEP_PER_PX = 3f
    }
}
