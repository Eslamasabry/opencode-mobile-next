package io.github.eslamasabry.oc.ui.design.effects

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit 2026-11-14 (c26): the message box's running glow ran straight across the foot of the joined "Jump to latest"
 * pill. What is drawn along a joined body follows [JoinOutline], the edge the glass shows.
 */
class JoinOutlineTest {
    // A message box 1000 x 300 px with 70 px corners, and the pill (180 x 42, 21 px corners) sunk 21 px into its top.
    private val box = JoinBox.of(0f, 100f, 1000f, 400f, 70f)
    private val pill = JoinBox.of(410f, 79f, 590f, 121f, 21f)
    private val reach = 147f

    private fun points(a: JoinBox, b: JoinBox, r: Float, offset: Float): List<Pair<Float, Float>> =
        JoinOutline.trace(a, b, r, offset).toList().chunked(2) { (x, y) -> x to y }

    @Test
    fun `B-GLASS-JOIN-OUTLINE-01 the joined outline runs over the pill, never along the box's top under it`() {
        val line = points(box, pill, reach, 0f)
        // The top half only: the box's bottom edge lies under the pill's centre too.
        val overPill = line.filter { (x, y) -> x in 470f..530f && y < 250f }.map { it.second }
        assertTrue(overPill.isNotEmpty() && overPill.all { abs(it - pill.top) < 1.5f }, "over the pill the line is its top edge: $overPill")
        val underFoot = line.filter { (x, y) -> x in 430f..570f && abs(y - box.top) < 2f }
        assertTrue(underFoot.isEmpty(), "no point on the box's top edge under the pill: $underFoot")
    }

    @Test
    fun `B-GLASS-JOIN-OUTLINE-02 beside the pill the outline curves down into the box's top as the fillet`() {
        val line = points(box, pill, reach, 0f)
        // Just outside the pill's side the line stands above the box's top (the fillet), and well away it is on it.
        val nearFoot = line.filter { (x, y) -> x in 395f..405f && y < box.top }.map { it.second }
        assertTrue(nearFoot.isNotEmpty() && nearFoot.all { it < box.top - 2f }, "the fillet beside the pill: $nearFoot")
        val farAway = line.filter { (x, y) -> x in 200f..260f && y < 200f }.map { it.second }
        assertTrue(farAway.isNotEmpty() && farAway.all { abs(it - box.top) < 1.5f }, "the box's top away from the pill: $farAway")
    }

    @Test
    fun `B-GLASS-JOIN-OUTLINE-03 an inside band lies its width inside the outline`() {
        val inner = points(box, pill, reach, 4f).filter { (x, y) -> x in 470f..530f && y < 250f }.map { it.second }
        assertTrue(inner.isNotEmpty() && inner.all { abs(it - (pill.top + 4f)) < 1.5f }, "4 px inside the pill's top: $inner")
    }

    @Test
    fun `B-GLASS-JOIN-OUTLINE-04 a drop still inside the box leaves the box's own outline`() {
        val sunk = JoinBox.of(476f, 120f, 524f, 162f, 21f)
        val line = points(box, sunk, reach * 1.4f, 0f)
        val top = line.filter { (x, y) -> x in 470f..530f && y < 250f }.map { it.second }
        assertTrue(top.isNotEmpty() && top.all { abs(it - box.top) < 1.5f }, "rising, still inside: the box's top $top")
    }

    @Test
    fun `B-GLASS-JOIN-OUTLINE-05 the points follow the outline evenly enough for a smooth line`() {
        val n = JoinOutline.pointsFor(box, pill, 4f)
        assertEquals(n * 2, JoinOutline.trace(box, pill, reach, 0f, n).size)
        assertTrue(n in JoinOutline.MIN_POINTS..JoinOutline.MAX_POINTS)
        val line = points(box, pill, reach, 0f)
        val longest = line.indices.maxOf { i ->
            val (x0, y0) = line[i]
            val (x1, y1) = line[(i + 1) % line.size]
            kotlin.math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
        }
        assertTrue(longest < 16f, "longest step along the outline $longest px")
    }
}
