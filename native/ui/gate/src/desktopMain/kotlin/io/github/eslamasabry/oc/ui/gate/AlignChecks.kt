package io.github.eslamasabry.oc.ui.gate

import io.github.eslamasabry.oc.ui.gate.AlignRules.Align
import io.github.eslamasabry.oc.ui.gate.AlignRules.Finding
import io.github.eslamasabry.oc.ui.gate.AlignRules.Kind
import io.github.eslamasabry.oc.ui.gate.AlignRules.Node
import io.github.eslamasabry.oc.ui.gate.AlignRules.fmt
import kotlin.math.abs
import kotlin.math.round

/** Rules read on one node: target, ellipsis-frame, clip, and the icon rules (size, squeeze, label line). */
internal class NodeRules(private val t: AlignTree, private val found: MutableList<Finding>) {
    private val s = t.s

    private fun add(n: Node, rule: String, detail: String) {
        found += Finding(rule, t.subject(n), detail)
    }

    fun check(n: Node) {
        if (n.tap && minOf(n.box.w, n.box.h) < t.spec.target - s) {
            add(n, "target", "${fmt(n.box.w)} x ${fmt(n.box.h)} dp, needs ${fmt(t.spec.target)}")
        }
        ellipsisFrame(n)
        clip(n)
        if (n.kind == Kind.Glyph) icon(n)
    }

    /** Frame words (chrome, a bar's title, a button or chip) never end in "…" (screen standard §5). */
    private fun ellipsisFrame(n: Node) {
        if (!n.ellipsized) return
        val where = when {
            n.chrome || t.up(n).any { it.chrome } -> "window chrome"
            n.contentEdge || t.up(n).any { it.contentEdge } -> "the bar's title"
            t.controlOf(n) != null -> "a control"
            else -> return
        }
        add(n, "ellipsis-frame", "ends in … in $where; frame words wrap or the part grows (screen standard §5)")
    }

    /** Words or an icon laid out past their parent's box: drawn cut off (a clipping parent) or hanging out of it. */
    private fun clip(n: Node) {
        val p = t.parentOf(n)?.takeIf { n.kind != Kind.Other } ?: return
        val free = p.scroller || p.sideScroller || p.list
        if (p.parent == null || free || t.up(n).any { it.sideScroller }) return
        val b = n.box
        val pb = p.box
        val out = maxOf(pb.l - b.l, b.r - pb.r, pb.t - b.t, b.b - pb.b)
        if (out > s) {
            add(
                n, "clip",
                "box ${fmt(b.l)},${fmt(b.t)}..${fmt(b.r)},${fmt(b.b)} runs ${fmt(out)} dp past its parent " +
                    "${t.subject(p)} (${fmt(pb.l)},${fmt(pb.t)}..${fmt(pb.r)},${fmt(pb.b)})",
            )
        }
    }

    private fun icon(g: Node) {
        val size = g.glyph
        if (size != null && t.spec.iconSizes.none { abs(it - size) <= s }) {
            val sizes = t.spec.iconSizes.joinToString(", ") { fmt(it) }
            add(g, "icon-size", "drawn at ${fmt(size)} dp; KitIconSize is $sizes")
        }
        if (size != null && (g.box.w < size - s || g.box.h < size - s)) {
            add(
                g, "icon-squeezed",
                "a ${fmt(size)} dp glyph in a ${fmt(g.box.w)} x ${fmt(g.box.h)} dp box: its edges are cut " +
                    "(pass size =, never Modifier.size)",
            )
        }
        val label = t.labelOf(g) ?: return
        val whole = label.box.cy
        val first = label.line0?.let { (it.first + it.second) / 2 } ?: whole
        val off = minOf(abs(g.box.cy - whole), abs(g.box.cy - first))
        if (off > s) {
            add(
                g, "icon-label",
                "icon centre y ${fmt(g.box.cy)}, label \"${clipWords(label.words)}\" centre " +
                    "${fmt(first)} (first line) / ${fmt(whole)}: ${fmt(off)} dp off",
            )
        }
    }
}

/** Rules read on a screen: start edges on the page's columns, gaps between blocks on the spacing steps. */
internal class PageRules(private val t: AlignTree, private val found: MutableList<Finding>) {
    private val s = t.s

    /** The nodes whose start is a column of the page, with that start. */
    fun leading(): List<Pair<Node, Float>> = t.nodes.mapNotNull { n ->
        val x = if (n.kind == Kind.Text) n.inkL ?: n.box.l else n.box.l
        if (startsColumn(n) && x <= t.w / 2 && !followsWords(n, x)) n to x else null
    }

    /** Leading words and icons outside chrome, scrollers, menus and controls; centred words start no column. */
    private fun startsColumn(n: Node): Boolean =
        t.visible(n) && n.kind != Kind.Other && !(n.kind == Kind.Text && n.align != Align.Start) &&
            !t.ownPlacement(n) && t.controlOf(n) == null

    /** Words before [n] on its line: [n] continues the line, it starts no column. */
    private fun followsWords(n: Node, x: Float): Boolean = t.lineScope(n).any { o ->
        o.id != n.id && o.kind == Kind.Text && t.visible(o) && (o.inkR ?: o.box.r) <= x + s &&
            overlapY(o.box, n.box) > minOf(o.box.h, n.box.h) / 2
    }

    private fun onEdge(n: Node, x: Float): Boolean =
        t.spec.textEdges.any { abs(it - x) <= s } ||
            (n.kind == Kind.Glyph && t.spec.iconCentres.any { abs(it - n.box.cx) <= s })

    fun startEdges() {
        val columns = t.spec.textEdges.joinToString(", ") { fmt(it) }
        for ((n, x) in leading()) {
            if (onEdge(n, x)) continue
            val what = if (n.kind == Kind.Glyph) "icon starts" else "words start"
            val tile = if (n.kind == Kind.Glyph) {
                " (icon centre ${fmt(n.box.cx)}, tile centres ${t.spec.iconCentres.joinToString(", ") { fmt(it) }})"
            } else {
                ""
            }
            val detail = "$what at x ${fmt(x)} dp; the page's columns are $columns$tile"
            found += Finding("start-edge", t.subject(n), detail)
        }
    }

    fun edges(): Map<Float, Int> = leading().groupingBy { half(it.second) }.eachCount().toSortedMap()

    /** Each block and the nearest sibling block below it in the same column: the gap is a spacing step. */
    fun gaps() {
        for (members in t.kids.values) {
            val blocks = members.filter { t.visible(it) && !t.ownPlacement(it) && it.kind != Kind.Glyph }
                .sortedWith(compareBy({ it.box.t }, { it.box.l }))
            blocks.forEachIndexed { i, a -> below(a, blocks.subList(i + 1, blocks.size))?.let { gap(a, it) } }
        }
    }

    private fun below(a: Node, rest: List<Node>): Node? = rest.firstOrNull {
        overlapX(a.box, it.box) > s && it.box.t >= a.box.cy && !nested(a.box, it.box, s)
    }

    private fun gap(a: Node, c: Node) {
        val gap = c.box.t - a.box.b
        val measured = gap > s && gap <= t.spec.stepMax + s && !(a.row && c.row)
        if (!measured || onStep(gap)) return
        found += Finding(
            "gap-step", t.subject(c),
            "${fmt(gap)} dp under ${t.subject(a)}; spacing steps are multiples of ${fmt(t.spec.grid)} " +
                "(or the ${fmt(t.spec.lineGap)} dp line gap)",
        )
    }

    private fun onStep(gap: Float): Boolean =
        abs(gap - round(gap / t.spec.grid) * t.spec.grid) <= s || abs(gap - t.spec.lineGap) <= s
}

/** Rules read on a list's rows: one end edge for the trailing parts, one height per kind of row. */
internal class ListRules(private val t: AlignTree, private val found: MutableList<Finding>) {
    private val s = t.s

    /** Rows of one list column: the same list (or scroller) and the same row box. */
    private val lists: Collection<List<Node>> by lazy {
        t.nodes.filter { it.row && t.visible(it) && !t.ownPlacement(it) }.groupBy { r ->
            val list = t.up(r).firstOrNull { it.list || it.scroller }
            Triple(list?.id ?: -1, round(r.box.l).toInt(), round(r.box.r).toInt())
        }.values
    }

    /** The end of a row's trailing part (chevron, ⋯, value): the end-most words or icon in the row's end half. */
    private fun trailingEnd(r: Node): Float? =
        t.descendants(r).filter { t.visible(it) && it.kind != Kind.Other && it.box.l > r.box.cx }
            .maxOfOrNull { if (it.kind == Kind.Text && it.align == Align.Start) it.inkR ?: it.box.r else it.box.r }

    fun endEdges() {
        for (rows in lists) {
            val ends = rows.mapNotNull { r -> trailingEnd(r)?.let { r to it } }
            if (ends.size < 2) continue
            val common = mode(ends.map { it.second })
            ends.filter { abs(it.second - common) > s }.forEach { (r, x) ->
                val detail = "trailing part ends at x ${fmt(x)} dp, the list's rows at ${fmt(common)}"
                found += Finding("end-edge", t.subject(r), detail)
            }
        }
    }

    fun rowHeights() {
        for (rows in lists) {
            val kinds = rows.groupBy { r ->
                val parts = t.descendants(r).filter { t.visible(it) }
                Pair(parts.filter { it.kind == Kind.Text }.sumOf { it.lines }, parts.any { it.kind == Kind.Glyph })
            }
            for (same in kinds.values.filter { it.size >= 2 }) {
                val common = mode(same.map { it.box.h })
                same.filter { abs(it.box.h - common) > s }.forEach {
                    val detail = "${fmt(it.box.h)} dp tall, the list's rows of this kind ${fmt(common)}"
                    found += Finding("row-height", t.subject(it), detail)
                }
            }
        }
    }
}
