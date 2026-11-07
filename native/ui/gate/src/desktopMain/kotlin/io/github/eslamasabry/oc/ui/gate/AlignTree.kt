package io.github.eslamasabry.oc.ui.gate

import io.github.eslamasabry.oc.ui.gate.AlignRules.Box
import io.github.eslamasabry.oc.ui.gate.AlignRules.Kind
import io.github.eslamasabry.oc.ui.gate.AlignRules.Node
import io.github.eslamasabry.oc.ui.gate.AlignRules.Spec
import kotlin.math.round

private const val SUBJECT = 40 // NV: characters of a node's words kept in a finding's subject

/** One window's nodes with the family lookups the alignment rules share. */
internal class AlignTree(val nodes: List<Node>, val w: Float, val h: Float, val spec: Spec) {
    val s: Float = spec.slack
    private val byId = nodes.associateBy { it.id }
    val kids: Map<Int, List<Node>> =
        nodes.mapNotNull { n -> n.parent?.let { it to n } }.groupBy({ it.first }, { it.second })
    private val ups = HashMap<Int, List<Node>>()
    private val downs = HashMap<Int, List<Node>>()

    fun parentOf(n: Node): Node? = n.parent?.let { byId[it] }

    /** Parent first, root last. */
    fun up(n: Node): List<Node> = ups.getOrPut(n.id) { parentOf(n)?.let { listOf(it) + up(it) } ?: emptyList() }

    fun descendants(n: Node): List<Node> =
        downs.getOrPut(n.id) { kids[n.id].orEmpty().flatMap { listOf(it) + descendants(it) } }

    fun visible(n: Node): Boolean {
        val v = n.shown ?: return false
        // a 1 dp live region or other screen-reader-only box draws nothing
        return v.w > 1f + s && v.h > 1f + s && v.r > 0f && v.l < w && v.b > 0f && v.t < h
    }

    /** Inside window chrome, a sideways scroller or a floating menu: parts that own their own placement. */
    fun ownPlacement(n: Node): Boolean =
        (listOf(n) + up(n)).any { it.chrome || it.floating } || up(n).any { it.sideScroller }

    fun rowOf(n: Node): Node? = up(n).firstOrNull { it.row }

    /** The nearest control that is not a list row (a button, chip, card or field): it places its own words. */
    fun controlOf(n: Node): Node? = up(n).firstOrNull { (it.tap && !it.bleed) || it.field }

    /** Words and icons that share [n]'s line: the rest of its row, else its siblings. */
    fun lineScope(n: Node): List<Node> =
        rowOf(n)?.let { descendants(it) } ?: n.parent?.let { kids[it] }.orEmpty()

    /** The words an icon labels: the nearest words after it on the same line, within [Spec.labelReach]. */
    fun labelOf(g: Node): Node? {
        val scope = (rowOf(g) ?: controlOf(g))?.let { descendants(it) } ?: lineScope(g)
        fun labels(words: Box) =
            words.l >= g.box.r - s && words.l - g.box.r <= spec.labelReach && overlapY(words, g.box) > 0f
        return scope.filter { it.kind == Kind.Text && visible(it) && labels(it.box) }.minByOrNull { it.box.l - g.box.r }
    }

    fun subject(n: Node): String {
        val words = n.words.takeIf { it.isNotBlank() }
            ?: descendants(n).firstOrNull { it.kind == Kind.Text && it.words.isNotBlank() }?.words
        val label = if (n.kind != Kind.Glyph) null else labelOf(n)?.words
            ?: rowOf(n)?.let { r -> descendants(r).firstOrNull { it.kind == Kind.Text }?.words }
        val base = when {
            n.kind == Kind.Glyph -> if (label != null) "icon by \"${clipWords(label)}\"" else "icon"
            words != null -> "\"${clipWords(words)}\""
            else -> "(unnamed)"
        }
        return if (n.tag != null) "$base [${n.tag}]" else base
    }
}

internal fun overlapX(a: Box, b: Box): Float = minOf(a.r, b.r) - maxOf(a.l, b.l)

internal fun overlapY(a: Box, b: Box): Float = minOf(a.b, b.b) - maxOf(a.t, b.t)

/** One box inside the other: a part drawn within a control, not a neighbour. */
internal fun nested(one: Box, two: Box, s: Float): Boolean {
    fun Box.holds(o: Box) = o.l >= l - s && o.r <= r + s && o.t >= t - s && o.b <= this.b + s
    return one.holds(two) || two.holds(one)
}

/** The most common value (to the half dp); ties go to the smallest. */
internal fun mode(values: List<Float>): Float =
    values.groupingBy { half(it) }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key })).first().key

internal fun half(v: Float): Float = round(v * 2) / 2

internal fun clipWords(words: String): String =
    words.replace(Regex("\\d+"), "#").replace(Regex("\\s+"), " ").trim().take(SUBJECT)
