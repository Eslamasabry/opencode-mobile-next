package io.github.eslamasabry.oc.ui.gate

import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import java.util.Locale

/**
 * The alignment gate's rules (native/docs/kit/ALIGN-RULES.md) over a neutral model of one rendered window: the JVM
 * layer builds it from the Compose semantics tree ([AlignRead]), and `native/tool/design/align_device.py` applies the
 * same rules to a uiautomator dump. Every position is in dp from the window's top-left corner.
 */
object AlignRules {
    /** A box in dp. */
    data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
        val w: Float get() = r - l
        val h: Float get() = b - t
        val cx: Float get() = (l + r) / 2
        val cy: Float get() = (t + b) / 2
    }

    /** What a node draws: words, one KitIcon glyph, or anything else (a row, a control, a container). */
    enum class Kind { Text, Glyph, Other }

    /** How a text node places its lines in its box. */
    enum class Align { Start, Centre, End }

    /**
     * One semantics node. [box] is its laid-out box (not clipped), [shown] the part inside its ancestors and the
     * window (null when nothing of it shows). Text nodes carry their ink: [inkL]/[inkR] the first and last ink
     * column over all lines, [line0] the first line's top and bottom. A glyph ([Kind.Glyph], one Phosphor code
     * point) carries the size its kit part asked for in [glyph].
     */
    data class Node(
        val id: Int,
        val parent: Int?,
        val box: Box,
        val shown: Box?,
        val kind: Kind = Kind.Other,
        val words: String = "",
        val tag: String? = null,
        val tap: Boolean = false,
        val bleed: Boolean = false,
        val chrome: Boolean = false,
        val scroller: Boolean = false,
        val sideScroller: Boolean = false,
        val list: Boolean = false,
        val floating: Boolean = false,
        val field: Boolean = false,
        val contentEdge: Boolean = false,
        val align: Align = Align.Start,
        val inkL: Float? = null,
        val inkR: Float? = null,
        val line0: Pair<Float, Float>? = null,
        val lines: Int = 1,
        val ellipsized: Boolean = false,
        val glyph: Float? = null,
    ) {
        /** A list row: a tappable whose box bleeds to the window's edges. */
        val row: Boolean get() = tap && bleed
    }

    /** The numbers the rules hold, from the kit's tokens (the device script reads the same from spec.json). */
    data class Spec(
        val gutter: Float = FlutterKitTokens.GUTTER.toFloat(),
        val rail: Float = FlutterKitTokens.RAIL.toFloat(),
        val leadColumns: List<Float> =
            listOf(FlutterKitTokens.LEAD_COLUMN.toFloat(), FlutterKitTokens.LEAD_COLUMN_COMPACT.toFloat()),
        val iconTile: Float = FlutterKitTokens.ICON_TILE_SIZE.toFloat(),
        val iconSizes: List<Float> = KitIconSize.entries.map { it.dp.toFloat() },
        val grid: Float = Space.S1.toFloat(),
        val lineGap: Float = FlutterKitTokens.ROW_LINE_GAP.toFloat(),
        // NV: past two S6 steps a gap is page layout, as LayoutWalk reads it
        val stepMax: Float = (Space.S6 * 2).toFloat(),
        val target: Float = FlutterKitTokens.MIN_TARGET.toFloat(),
        val slack: Float = 0.5f, // NV: the gate's tolerance in dp (owner brief 2026-11-07)
        val labelReach: Float = Space.S6.toFloat(), // NV: a label starts within one S6 step of its icon
    ) {
        /** Where words may start: the page edge, the sheet rail, and the text column after each lead column. */
        val textEdges: List<Float>
            get() = (listOf(gutter, rail) + leadColumns.flatMap { listOf(gutter + it, rail + it) }).distinct().sorted()

        /** Where an icon's centre may sit: the middle of the leading tile on the page edge or the sheet rail. */
        val iconCentres: List<Float> get() = listOf(gutter + iconTile / 2, rail + iconTile / 2)
    }

    /** One broken rule; [key] (without coordinates) is what the baseline stores. */
    data class Finding(val rule: String, val subject: String, val detail: String) {
        val key: String get() = "$rule\t$subject"
    }

    /** The rule ids, in the order of ALIGN-RULES.md. */
    val RULES: List<String> = listOf(
        "start-edge", "gap-step", "icon-label", "end-edge", "target", "ellipsis-frame",
        "clip", "icon-size", "icon-squeezed", "row-height",
    )

    /**
     * Every finding for one window [w] x [h] dp. [screen] adds the page rules (start edges, gaps between blocks) that
     * a lone kit part in a gallery cannot meet.
     */
    fun check(nodes: List<Node>, w: Float, h: Float, screen: Boolean, spec: Spec = Spec()): List<Finding> {
        val tree = AlignTree(nodes, w, h, spec)
        val found = ArrayList<Finding>()
        val shown = nodes.filter { tree.visible(it) }
        val each = NodeRules(tree, found)
        shown.forEach { each.check(it) }
        val lists = ListRules(tree, found)
        lists.endEdges()
        lists.rowHeights()
        if (screen) {
            val page = PageRules(tree, found)
            page.startEdges()
            page.gaps()
        }
        return found.distinctBy { it.key + it.detail }
    }

    /** The distinct start edges of a screen's words and icons, with how many nodes start there (for the report). */
    fun startEdges(nodes: List<Node>, w: Float, h: Float, spec: Spec = Spec()): Map<Float, Int> =
        PageRules(AlignTree(nodes, w, h, spec), ArrayList()).edges()

    /** One decimal, as every finding prints a dp value. */
    fun fmt(v: Float): String = String.format(Locale.ROOT, "%.1f", v) // NV: one decimal in a finding
}
