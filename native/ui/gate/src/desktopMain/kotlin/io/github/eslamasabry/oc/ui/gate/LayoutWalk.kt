package io.github.eslamasabry.oc.ui.gate

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.gate.LayoutGuard.Finding
import io.github.eslamasabry.oc.ui.gate.LayoutGuard.Window
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.KitLayoutMarks
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

private const val SLACK = 0.5f // NV: sub-pixel slack in dp for rounding of layout positions
private const val GRID = 4f // NV: the spacing scale steps by 4 dp (Space.S1..S6 = 4..24)
private const val TARGET = 48f // F: kit-v2.md §8.3 "Touch targets stay 48 dp everywhere"
private const val SUBJECT = 48 // NV: characters of a node's words kept in a finding
private const val TITLE_EDGE = 1f // NV: titles within 1 dp share an edge
private const val CUT = 1f // NV: words lost when the laid-out paragraph exceeds its box by more than 1 dp
private val stepGap: Float = (Space.S6 * 2).toFloat() // NV: gaps past two S6 steps are page layout, not a spacing step
private val gutter: Float = FlutterKitTokens.GUTTER.toFloat()
private val controlEdge: Float = Space.S1.toFloat()

/** One semantics node with what the rules need to know about it. */
private class Item(val node: SemanticsNode, val ancestors: List<SemanticsNode>) {
    val config = node.config
    val bounds: Rect = node.boundsInWindow
    val text: Boolean = SemanticsProperties.Text in config && SemanticsProperties.EditableText !in config
    val tap: Boolean = SemanticsActions.OnClick in config
    val control: Boolean = tap || SemanticsProperties.EditableText in config || SemanticsActions.SetText in config
    val bleed: Boolean = config.getOrNull(KitLayoutMarks.Edge) == KitEdge.Bleed
    val chrome: Boolean = (ancestors + node).any { it.config.getOrNull(KitLayoutMarks.Edge) == KitEdge.Chrome }
    val sideScroll: Boolean = ancestors.any { SemanticsProperties.HorizontalScrollAxisRange in it.config }
    val visible: Boolean = bounds.width > SLACK && bounds.height > SLACK
    val button: Boolean = config.getOrNull(SemanticsProperties.Role) == Role.Button
    val pageRules: Boolean = !chrome && !sideScroll
}

/** One walk of [LayoutGuard]'s rules (native/docs/kit/LAYOUT-RULES.md) over one rendered tree at one window. */
internal class LayoutWalk(private val window: Window, roots: List<SemanticsNode>) {
    private val w: Float = window.w.toFloat()
    private val items = ArrayList<Item>()
    private val found = ArrayList<Finding>()

    init {
        fun collect(n: SemanticsNode, up: List<SemanticsNode>) {
            items += Item(n, up)
            n.children.forEach { collect(it, up + n) }
        }
        roots.forEach { collect(it, emptyList()) }
    }

    /** Every finding; [screen] adds the page rules (gutter, title edge) that a lone part cannot meet. */
    fun run(screen: Boolean): List<Finding> {
        for (n in items.filter { it.visible && it.bounds.right > 0f && it.bounds.left < w }) {
            targets(n)
            truncation(n)
            if (window == Window.Tablet && !n.chrome) caps(n)
            if (screen && n.pageRules) edges(n)
        }
        spacing()
        if (screen) titleEdges()
        return found
    }

    private fun add(item: Item, rule: String, detail: String) {
        found += Finding(window, rule, subjectOf(item.node), detail)
    }

    private fun targets(n: Item) {
        val size = n.node.size
        if (n.tap && minOf(size.width, size.height) < TARGET - SLACK) {
            add(n, "small-target", "${size.width} x ${size.height} dp, needs 48")
        }
    }

    private fun truncation(n: Item) {
        val layout = if (n.text) layoutOf(n.node) else null
        if (layout == null) return
        val cut = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        val allowed = n.config.getOrNull(KitLayoutMarks.MayTruncate) != null
        when {
            cut && !allowed -> add(n, "ellipsis", "ends in … and its kit part does not allow a cut")
            !cut && lost(layout) && !n.sideScroll -> add(n, "clipped", "words cut off with no ellipsis")
        }
    }

    private fun caps(n: Item) {
        val width = n.bounds.width
        val list = Layout.LIST_WIDTH + SLACK
        val rowTap = n.tap && n.bleed
        val buttonTap = n.tap && !n.bleed && n.button
        when {
            n.text && width > list -> add(n, "wide-text", "${fmt(width)} dp wide, list cap ${Layout.LIST_WIDTH}")
            rowTap && width > list -> add(n, "wide-row", "${fmt(width)} dp wide, list cap ${Layout.LIST_WIDTH}")
            buttonTap && width > Layout.CONTROL_MAX_WIDTH + SLACK ->
                add(n, "wide-control", "${fmt(width)} dp wide, control cap ${Layout.CONTROL_MAX_WIDTH}")
        }
    }

    private fun edges(n: Item) {
        val b = n.bounds
        val textOut = b.left < gutter - SLACK || b.right > w - gutter + SLACK
        val boxOut = b.left < controlEdge - SLACK || b.right > w - controlEdge + SLACK
        val box = n.control && !n.text && !n.bleed
        val at = "x ${fmt(b.left)}..${fmt(b.right)}"
        when {
            n.text && textOut -> add(n, "edge-text", "$at, gutter ${fmt(gutter)}..${fmt(w - gutter)}")
            box && boxOut -> add(n, "edge-tap", "$at, needs ${fmt(controlEdge)} dp from the edge")
        }
    }

    /** Each words-or-control node and the nearest sibling below it that shares columns with it. */
    private fun spacing() {
        val byId = items.associateBy { it.node.id }
        for (parent in items) {
            val sorted = parent.node.children.mapNotNull { byId[it.id] }
                .filter { it.visible && (it.text || it.control) }
                .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            for (i in sorted.indices) {
                val a = sorted[i]
                val below = sorted.subList(i + 1, sorted.size).firstOrNull {
                    sharesColumns(a.bounds, it.bounds) && stacked(a.bounds, it.bounds) && !nested(a.bounds, it.bounds)
                }
                if (below != null) pair(a, below)
            }
        }
    }

    private fun pair(a: Item, c: Item) {
        val involvesControl = a.control || c.control
        val bothRows = a.bleed && c.bleed
        if (!involvesControl || bothRows) return
        val gap = c.bounds.top - a.bounds.bottom
        val above = subjectOf(a.node)
        val bothControls = a.control && c.control
        val noRow = !a.bleed && !c.bleed
        when {
            gap < -SLACK -> add(c, "overlap", "overlaps \"$above\" by ${fmt(-gap)} dp")
            gap < controlEdge - SLACK -> add(c, "tight", "${fmt(gap)} dp under \"$above\", needs ${Space.S1}")
            bothControls && noRow && gap <= stepGap && offGrid(gap) ->
                add(c, "off-scale", "${fmt(gap)} dp under \"$above\", not a spacing step")
        }
    }

    /** Rows of one list column (same list, same row box) start their titles on one edge. */
    private fun titleEdges() {
        val rows = items.filter { it.tap && it.bleed && it.visible && it.pageRules }
        val groups = rows.groupBy { r ->
            val list = r.ancestors.lastOrNull { isList(it) }
            Triple(list?.id ?: -1, round(r.bounds.left).toInt(), round(r.bounds.right).toInt())
        }
        for (members in groups.values) {
            val starts = members.mapNotNull { r -> titleStart(r)?.let { r to it } }
            if (starts.size < 2) continue
            val common = starts.groupingBy { round(it.second).toInt() }.eachCount().maxBy { it.value }.key
            starts.filter { abs(it.second - common) > TITLE_EDGE }.forEach { (r, x) ->
                add(r, "title-edge", "title starts at x ${fmt(x)}, the list's rows at x $common")
            }
        }
    }

    /** The start (left, LTR) of the first line of words inside a row: the top-most text node, then the start-most. */
    private fun titleStart(row: Item): Float? =
        items.filter { it.text && it.visible && it.ancestors.any { a -> a.id == row.node.id } }
            .minWithOrNull(compareBy({ round(it.bounds.top) }, { it.bounds.left }))?.bounds?.left
}

private fun isList(n: SemanticsNode): Boolean =
    KitLayoutMarks.List in n.config || SemanticsProperties.CollectionInfo in n.config ||
        SemanticsProperties.VerticalScrollAxisRange in n.config

private fun sharesColumns(a: Rect, b: Rect): Boolean = minOf(a.right, b.right) - maxOf(a.left, b.left) > SLACK

/** [b] starts at or below [a]'s middle: under it, not beside it (a clear button centred on a field's words). */
private fun stacked(a: Rect, b: Rect): Boolean = b.top >= a.center.y

/** One box inside the other: a part drawn within a control (a clear button in a field), not a neighbour. */
private fun nested(a: Rect, b: Rect): Boolean {
    fun Rect.holds(o: Rect) =
        o.left >= left - SLACK && o.right <= right + SLACK && o.top >= top - SLACK && o.bottom <= bottom + SLACK
    return a.holds(b) || b.holds(a)
}

/**
 * Words lost without a sign: lines past maxLines dropped, a line wider than its box, or the lines taller than the
 * box, each by more than [CUT]. (`hasVisualOverflow` is not used: it compares the paragraph's constraint
 * width, not its words, with the box.)
 */
private fun lost(layout: TextLayoutResult): Boolean {
    val width = layout.size.width
    // A line's width, not its position: aligned words are placed across the constraint width, not the box.
    val wide = (0 until layout.lineCount).any { layout.getLineRight(it) - layout.getLineLeft(it) - width > CUT }
    return layout.multiParagraph.didExceedMaxLines || wide || layout.multiParagraph.height - layout.size.height > CUT
}

private fun offGrid(gap: Float): Boolean = abs(gap - round(gap / GRID) * GRID) > SLACK

private fun layoutOf(node: SemanticsNode): TextLayoutResult? {
    val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
    val results = ArrayList<TextLayoutResult>()
    return if (action(results)) results.firstOrNull() else null
}

/** A node's words (its own or its first descendant's), else its description or test tag; digits folded to `#`. */
private fun subjectOf(node: SemanticsNode): String {
    fun words(n: SemanticsNode): String? {
        val own = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        return own?.takeIf { it.isNotBlank() } ?: n.children.firstNotNullOfOrNull { words(it) }
    }
    val raw = words(node) ?: node.config.getOrNull(SemanticsProperties.TestTag) ?: "(unnamed)"
    return raw.replace(Regex("\\d+"), "#").replace(Regex("\\s+"), " ").trim().take(SUBJECT)
}

private fun fmt(v: Float): String = String.format(Locale.ROOT, "%.1f", v) // NV: one decimal in a finding
