package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitMayTruncate
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitPixelRatio
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitInlineStateView
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMarkDone
import io.github.eslamasabry.oc.ui.words.res.kitMarkFailed
import io.github.eslamasabry.oc.ui.words.res.kitMarkPaused
import io.github.eslamasabry.oc.ui.words.res.kitMarkWaiting
import io.github.eslamasabry.oc.ui.words.res.kitMarkWorking
import io.github.eslamasabry.oc.ui.words.res.kitTaskNeedsYou
import io.github.eslamasabry.oc.ui.words.res.kitTaskStopped
import io.github.eslamasabry.oc.ui.words.res.kitWorkGraph
import io.github.eslamasabry.oc.ui.words.res.kitWorkGraphEmpty
import io.github.eslamasabry.oc.ui.words.res.kitWorkGraphNeeds
import io.github.eslamasabry.oc.ui.words.res.kitWorkGraphNeedsMore
import io.github.eslamasabry.oc.ui.words.res.kitWorkGraphNode
import io.github.eslamasabry.oc.ui.words.res.kitZoomAtMax
import io.github.eslamasabry.oc.ui.words.res.kitZoomAtStart
import io.github.eslamasabry.oc.ui.words.res.kitZoomFit
import io.github.eslamasabry.oc.ui.words.res.kitZoomIn
import io.github.eslamasabry.oc.ui.words.res.kitZoomLevel
import io.github.eslamasabry.oc.ui.words.res.kitZoomOut
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where a task stands: Dart `KitTaskState` (lib/ui/kit/kit_task_mark.dart:13), the four states of a step plus a task
 * that waits on the person and one stopped before it finished. Kept here until order 07 lands `KitTaskMark`.
 */
public enum class KitWorkGraphMark { Waiting, Working, Done, Failed, NeedsYou, Stopped }

/** One work item (Dart `KitWorkGraphNode`). The host maps its own model; the kit knows no WorkState. */
@Immutable
public data class KitWorkGraphNode(
    val id: String,
    /** The item's title as written. */
    val title: String,
    /** The leading mark and its tone. */
    val mark: KitWorkGraphMark,
    /** Only meaningful with [KitWorkGraphMark.Waiting] or [KitWorkGraphMark.Working]. */
    val paused: Boolean = false,
    /** The state word; null takes the mark's own word. */
    val word: String? = null,
    /** Ids this item needs; ids outside the graph are ignored. */
    val dependsOn: List<String> = emptyList(),
    /** Blocked, needs input or failed: seeds the blocked chain. */
    val stuck: Boolean = false,
    /** Not finished yet: something stuck may be waiting on it. */
    val open: Boolean = true,
)

/** Where [KitWorkGraph] draws its graph (Dart `KitWorkGraphLayout`). */
public enum class KitWorkGraphLayout {
    /** Rows on compact, layers from medium (the default). */
    Auto,

    /** One item per row, links in a start-side gutter; scrolls with its host. */
    Rows,

    /** The layered graph in a zoomable canvas, fitted on open. */
    Layers,
}

/**
 * One `needs` link (Dart `KitWorkGraphEdge`). In `layers` it is a cubic curve through [control1] and [control2]; in
 * `rows` it is a straight run through its [lane] in the start-side gutter. Positions in dp.
 */
@Immutable
public data class KitWorkGraphEdge(
    /** The needed item (upstream). */
    val from: String,
    /** The item that needs it (downstream). */
    val to: String,
    val start: Offset,
    val control1: Offset,
    val control2: Offset,
    val end: Offset,
    /** On the critical path: drawn thicker. */
    val critical: Boolean,
    /** Both ends in the blocked chain: drawn dashed in `text1` (never the attention tone). */
    val blocked: Boolean,
    /** Rows only; -1 in layers. */
    val lane: Int = -1,
) {
    public fun toPath(): Path =
        Path().apply {
            moveTo(start.x, start.y)
            cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
        }
}

/** The row/layer analysis shared by both forms (Dart `_Analysis`), so their node order always agrees. */
private class Analysis(
    val nodes: List<KitWorkGraphNode>,
    val deps: List<List<Int>>,
    val dependents: List<List<Int>>,
    /** Row (layer) index → node indices, start to end. */
    val rows: List<List<Int>>,
    val criticalPath: List<String>,
    val blockedChain: Set<String>,
) {
    companion object {
        fun of(input: List<KitWorkGraphNode>): Analysis {
            // 1. Unique nodes in input order; edges to known, other nodes only.
            val nodes = ArrayList<KitWorkGraphNode>()
            val index = HashMap<String, Int>()
            for (node in input) {
                if (node.id in index) continue
                index[node.id] = nodes.size
                nodes.add(node)
            }
            val n = nodes.size
            val deps: MutableList<List<Int>> =
                nodes.map { node -> node.dependsOn.distinct().mapNotNull { id -> index[id]?.takeIf { it != index[node.id] } } }.toMutableList()

            // 2. Longest-path layering with cycle breaking: a dependency that closes a cycle is dropped.
            val layer = arrayOfNulls<Int>(n)
            val visiting = BooleanArray(n)
            fun layerOf(i: Int): Int {
                layer[i]?.let { return it }
                visiting[i] = true
                var depth = 0
                val kept = ArrayList<Int>()
                for (dep in deps[i]) {
                    if (visiting[dep]) continue
                    kept.add(dep)
                    depth = max(depth, layerOf(dep) + 1)
                }
                deps[i] = kept
                visiting[i] = false
                layer[i] = depth
                return depth
            }
            for (i in 0 until n) layerOf(i)
            val dependents = List(n) { ArrayList<Int>() }
            for (i in 0 until n) for (dep in deps[i]) dependents[dep].add(i)

            // 3. Rows in input order, then three barycenter sweeps (down, up, down).
            val depth = if (n == 0) 0 else layer.maxOf { it!! } + 1
            val rows = List(depth) { ArrayList<Int>() }
            for (i in 0 until n) rows[layer[i]!!].add(i)
            val column = DoubleArray(n)
            fun place() {
                for (row in rows) row.forEachIndexed { position, i -> column[i] = position - (row.size - 1) / 2.0 } // F: lib/ui/kit/kit_work_graph.dart:221
            }
            fun sweep(neighbours: List<List<Int>>, upward: Boolean = false) {
                for (row in if (upward) rows.reversed() else rows) {
                    val key = HashMap<Int, Double>()
                    for (i in row) {
                        val near = neighbours[i]
                        key[i] = if (near.isEmpty()) column[i] else near.sumOf { column[it] } / near.size
                    }
                    val before = row.withIndex().associate { (p, i) -> i to p }
                    val sorted = row.sortedWith { a, b ->
                        val byKey = key.getValue(a).compareTo(key.getValue(b))
                        if (byKey != 0) byKey else before.getValue(a).compareTo(before.getValue(b))
                    }
                    row.clear()
                    row.addAll(sorted)
                    place()
                }
            }
            place()
            sweep(deps)
            sweep(dependents, upward = true)
            sweep(deps)

            // 4. Critical path: the longest chain by node count; ties go to the earliest input node.
            val chain = IntArray(n) { 1 }
            val via = arrayOfNulls<Int>(n)
            for (r in 0 until depth) {
                for (i in rows[r]) {
                    for (dep in deps[i]) {
                        val length = chain[dep] + 1
                        if (length > chain[i] || (length == chain[i] && dep < via[i]!!)) {
                            chain[i] = length
                            via[i] = dep
                        }
                    }
                }
            }
            val criticalPath = ArrayList<String>()
            if (n > 0) {
                var end = 0
                for (i in 1 until n) if (chain[i] > chain[end]) end = i
                var i: Int? = end
                while (i != null) {
                    criticalPath.add(0, nodes[i].id)
                    i = via[i]
                }
            }

            // 5. Blocked chain: stuck items, everything downstream of them and the open items they wait on.
            val blocked = LinkedHashSet<Int>()
            val queue = ArrayList((0 until n).filter { nodes[it].stuck })
            for (i in queue) for (dep in deps[i]) if (nodes[dep].open) blocked.add(dep)
            while (queue.isNotEmpty()) {
                val i = queue.removeAt(queue.lastIndex)
                if (!blocked.add(i)) continue
                queue.addAll(dependents[i])
            }
            return Analysis(nodes, deps, dependents, rows, criticalPath, blocked.mapTo(LinkedHashSet()) { nodes[it].id })
        }
    }
}

/**
 * The pure, deterministic geometry of [KitWorkGraph] (Dart `KitWorkGraphGeometry`), in dp. No composables; the parity
 * test asserts its positions directly.
 */
public class KitWorkGraphGeometry private constructor(
    /** The nodes in input order, duplicates (by id) dropped. */
    public val nodes: List<KitWorkGraphNode>,
    /** Where each node sits, by id. */
    public val rects: Map<String, Rect>,
    /** Node ids per row, top row first, start to end. */
    public val layerRows: List<List<String>>,
    public val edges: List<KitWorkGraphEdge>,
    /** The longest chain of `needs` links, upstream first. One id when no edge exists; empty when the graph is empty. */
    public val criticalPath: List<String>,
    /** Stuck items, everything downstream of them and the open items they wait on. */
    public val blockedChain: Set<String>,
    /** The painted area, padding included. */
    public val size: Size,
    /** `rows`: the links' gutter width; `layers`: 0. */
    public val gutter: Float,
) {
    /** Consecutive pairs of [criticalPath] as (from, to). */
    public val criticalEdges: Set<Pair<String, String>>
        get() = (0 until (criticalPath.size - 1).coerceAtLeast(0)).mapTo(LinkedHashSet()) { criticalPath[it] to criticalPath[it + 1] }

    public fun nodeAt(point: Offset): KitWorkGraphNode? = nodes.firstOrNull { rects.getValue(it.id).contains(point) }

    public companion object {
        /** The canvas padding around `layers` (KitTokens.space4's value; the geometry is pure). */
        public const val CANVAS_PADDING: Float = 16f // F: lib/ui/kit/kit_work_graph.dart:326

        /** One `rows` lane's width in the pure geometry (KitTokens.space3's value). */
        public const val LANE_WIDTH: Float = 12f // F: lib/ui/kit/kit_work_graph.dart:331

        private val columnGap = FlutterKitTokens.GRAPH_COLUMN_GAP.toFloat()
        private val rowGap = FlutterKitTokens.GRAPH_ROW_GAP.toFloat()

        /** Longest-path layering, then a barycenter sweep, fixed node size, rows centred (Dart `layers`). */
        public fun layers(nodes: List<KitWorkGraphNode>, nodeSize: Size): KitWorkGraphGeometry {
            val a = Analysis.of(nodes)
            val n = a.nodes.size
            val depth = a.rows.size
            val widest = a.rows.maxOfOrNull { it.size } ?: 0
            val width = if (widest == 0) 0f else widest * nodeSize.width + (widest - 1) * columnGap
            val rects = LinkedHashMap<String, Rect>()
            a.rows.forEachIndexed { r, row ->
                val rowWidth = row.size * nodeSize.width + (row.size - 1) * columnGap
                val left = CANVAS_PADDING + (width - rowWidth) / 2 // F: lib/ui/kit/kit_work_graph.dart:396
                val top = CANVAS_PADDING + r * (nodeSize.height + rowGap)
                row.forEachIndexed { p, i ->
                    rects[a.nodes[i].id] = Rect(Offset(left + p * (nodeSize.width + columnGap), top), nodeSize)
                }
            }
            val size =
                Size(
                    width + 2 * CANVAS_PADDING, // F: lib/ui/kit/kit_work_graph.dart:409
                    if (depth == 0) 2 * CANVAS_PADDING // F: lib/ui/kit/kit_work_graph.dart:411
                    else depth * nodeSize.height + (depth - 1) * rowGap + 2 * CANVAS_PADDING, // F: lib/ui/kit/kit_work_graph.dart:414
                )
            val critical = criticalPairs(a)
            val edges = ArrayList<KitWorkGraphEdge>()
            for (i in 0 until n) {
                for (dep in a.deps[i]) {
                    val from = rects.getValue(a.nodes[dep].id)
                    val to = rects.getValue(a.nodes[i].id)
                    val start = Offset(from.center.x, from.bottom)
                    val end = Offset(to.center.x, to.top)
                    edges +=
                        KitWorkGraphEdge(
                            from = a.nodes[dep].id,
                            to = a.nodes[i].id,
                            start = start,
                            control1 = start + Offset(0f, rowGap / 2), // F: lib/ui/kit/kit_work_graph.dart:433
                            control2 = end - Offset(0f, rowGap / 2), // F: lib/ui/kit/kit_work_graph.dart:434
                            end = end,
                            critical = (dep to i) in critical,
                            blocked = a.nodes[dep].id in a.blockedChain && a.nodes[i].id in a.blockedChain,
                        )
                }
            }
            return KitWorkGraphGeometry(a.nodes, rects, a.rows.map { row -> row.map { a.nodes[it].id } }, edges, a.criticalPath, a.blockedChain, size, 0f)
        }

        /**
         * The same order as [layers] read top to bottom, start to end, one node per row; each link gets a lane in the
         * gutter (greedy interval assignment, at most `GRAPH_MAX_LANES` lanes; extra links share the last lane).
         */
        public fun rows(nodes: List<KitWorkGraphNode>, width: Float, rowHeight: (KitWorkGraphNode) -> Float): KitWorkGraphGeometry {
            val a = Analysis.of(nodes)
            val order = a.rows.flatten()
            val position = order.withIndex().associate { (p, i) -> a.nodes[i].id to p }
            // Lane assignment: sort spans by start then length, place each in the first lane whose last use ends at or
            // before this span's start.
            class Span(val from: Int, val to: Int, val start: Int, val end: Int)
            val spans =
                buildList {
                    for (i in a.nodes.indices) for (dep in a.deps[i]) {
                        val p = position.getValue(a.nodes[dep].id)
                        val q = position.getValue(a.nodes[i].id)
                        add(Span(dep, i, min(p, q), max(p, q)))
                    }
                }.sortedWith(compareBy<Span> { it.start }.thenBy { it.end })
            val maxLanes = FlutterKitTokens.GRAPH_MAX_LANES
            val laneEnd = HashMap<Int, Int>()
            val laneOf = LinkedHashMap<Pair<Int, Int>, Int>()
            for (s in spans) {
                var chosen = maxLanes - 1
                for (lane in 0 until maxLanes) {
                    val end = laneEnd[lane]
                    if (end == null || end <= s.start) {
                        chosen = lane
                        break
                    }
                }
                laneEnd[chosen] = max(laneEnd[chosen] ?: 0, s.end)
                laneOf[s.from to s.to] = chosen
            }
            val laneCount = if (laneOf.isEmpty()) 0 else laneOf.values.max() + 1
            val gutter = if (laneCount == 0) 0f else laneCount * LANE_WIDTH
            val rects = LinkedHashMap<String, Rect>()
            var y = 0f
            val rowWidth = max(0f, width - gutter)
            for (i in order) {
                val h = rowHeight(a.nodes[i])
                rects[a.nodes[i].id] = Rect(Offset(gutter, y), Size(rowWidth, h))
                y += h
            }
            val critical = criticalPairs(a)
            val edges = ArrayList<KitWorkGraphEdge>()
            for (i in a.nodes.indices) {
                for (dep in a.deps[i]) {
                    val lane = laneOf.getValue(dep to i)
                    val x = lane * LANE_WIDTH + LANE_WIDTH / 2 // F: lib/ui/kit/kit_work_graph.dart:527
                    val start = Offset(x, rects.getValue(a.nodes[dep].id).center.y)
                    val end = Offset(x, rects.getValue(a.nodes[i].id).center.y)
                    edges +=
                        KitWorkGraphEdge(
                            from = a.nodes[dep].id,
                            to = a.nodes[i].id,
                            start = start,
                            control1 = start,
                            control2 = end,
                            end = end,
                            critical = (dep to i) in critical,
                            blocked = a.nodes[dep].id in a.blockedChain && a.nodes[i].id in a.blockedChain,
                            lane = lane,
                        )
                }
            }
            return KitWorkGraphGeometry(a.nodes, rects, a.rows.map { row -> row.map { a.nodes[it].id } }, edges, a.criticalPath, a.blockedChain, Size(width, y), gutter)
        }

        private fun criticalPairs(a: Analysis): Set<Pair<Int, Int>> =
            a.nodes.indices.mapNotNullTo(HashSet()) { i -> via(a, i)?.takeIf { a.nodes[i].id in a.criticalPath }?.let { it to i } }

        /** The node just upstream of [i] on the critical path, if [i] is on it (Dart `_via`). */
        private fun via(a: Analysis, i: Int): Int? {
            val at = a.criticalPath.indexOf(a.nodes[i].id)
            if (at <= 0) return null
            val fromId = a.criticalPath[at - 1]
            return a.deps[i].firstOrNull { a.nodes[it].id == fromId }
        }
    }
}

/** Arrow head half width and length, the same at every link width. */
private const val ARROW_HALF_WIDTH: Float = 5f // F: lib/ui/kit/kit_work_graph.dart:736
private const val ARROW_LENGTH: Float = 7f // F: lib/ui/kit/kit_work_graph.dart:737

/** `rowTitle` semibold: the blocked chain's titles. */
private val chainWeight = FontWeight.W600 // F: lib/ui/kit/kit_work_graph.dart:731

/** KitZoom's canvas floor and ceiling and its one step (kit_image.dart); the canvas fits between them. */
private const val ZOOM_MIN: Float = 0.2f // F: lib/ui/kit/kit_image.dart:825
private const val ZOOM_MAX: Float = 5f // F: lib/ui/kit/kit_image.dart:824
private const val ZOOM_STEP: Float = 1.5f // F: lib/ui/kit/kit_image.dart:866
private const val ZOOM_EPSILON: Float = 1e-3f // NV: float comparison of two zoom transforms

/** The paint of a link: critical in `text2` at the focus width, others `text3` at a hairline, blocked `text1`. */
private class LinkInk(val color: Color, val width: Dp, val dashed: Boolean)

private fun linkInk(edge: KitWorkGraphEdge, text1: Color, text2: Color, text3: Color, hairline: Dp, focus: Dp): LinkInk =
    LinkInk(
        color = if (edge.blocked) text1 else if (edge.critical) text2 else text3,
        width = if (edge.critical) focus else hairline,
        dashed = edge.blocked,
    )

/** Plain links under critical ones, blocked on top: the stronger reading is never hidden under a hairline. */
private fun rank(edge: KitWorkGraphEdge): Int = if (edge.blocked) 2 else if (edge.critical) 1 else 0 // F: lib/ui/kit/kit_work_graph.dart:802

private fun DrawScope.strokePath(path: Path, ink: LinkInk, dashed: Boolean) {
    val w = ink.width.toPx()
    val dash = FlutterKitTokens.GRAPH_DASH.dp.toPx()
    drawPath(
        path,
        ink.color,
        style =
            Stroke(
                width = w,
                cap = StrokeCap.Butt,
                join = StrokeJoin.Miter,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(dash, dash)) else null,
            ),
    )
}

/** [value] rounded up to the next physical pixel, so stacked boxes start on whole device pixels. */
private fun ceilToPixel(value: Float, dpr: Float): Float = ceil(value * dpr) / dpr

/** A stroke's centre line snapped to the physical grid: a pixel centre for an odd physical width, an edge for even. */
private fun snapStroke(value: Float, strokeWidth: Float, dpr: Float): Float {
    val physical = (strokeWidth * dpr).roundToInt()
    if ((physical and 1) == 1) return (floor(value * dpr) + .5f) / dpr // F: lib/ui/kit/kit_work_graph.dart:699
    return (value * dpr).roundToInt() / dpr
}

/** A node's state word: its own, or the mark's. */
@Composable
private fun wordOf(node: KitWorkGraphNode): String = node.word ?: markWord(node.mark, node.paused)

@Composable
private fun markWord(state: KitWorkGraphMark, paused: Boolean): String =
    if (paused) stringResource(Res.string.kitMarkPaused)
    else
        when (state) {
            KitWorkGraphMark.Waiting -> stringResource(Res.string.kitMarkWaiting)
            KitWorkGraphMark.Working -> stringResource(Res.string.kitMarkWorking)
            KitWorkGraphMark.Done -> stringResource(Res.string.kitMarkDone)
            KitWorkGraphMark.Failed -> stringResource(Res.string.kitMarkFailed)
            KitWorkGraphMark.NeedsYou -> stringResource(Res.string.kitTaskNeedsYou)
            KitWorkGraphMark.Stopped -> stringResource(Res.string.kitTaskStopped)
        }

/**
 * The task's leading mark in its 32 dp slot (Dart `KitTaskMark` over `KitStatusMark`): a hollow ring waiting, a small
 * spinner working (a still dot under reduced motion), a check done, the error glyph failed, the question in the
 * attention tone needing the person, a muted stop mark stopped, a pause when paused. Decorative here: the row or chip
 * carries the word. Stand-in until order 07 lands `KitTaskMark`.
 */
@Composable
internal fun KitWorkGraphTaskMark(state: KitWorkGraphMark, paused: Boolean = false, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val roles = LocalKitRoles.current
    val tokens = LocalKitTokens.current
    val dpr = kitPixelRatio()
    val glyph = (tokens.smallIcon * min(kitTextScale(), tokens.maxIconScale) * dpr).roundToInt() / dpr
    Box(modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        when {
            paused -> KitIcon(KitIcons.pause, size = KitIconSize.Small, tone = TextTone.Secondary, growsWithText = true)
            state == KitWorkGraphMark.Done -> KitIcon(KitIcons.check, size = KitIconSize.Small, tone = TextTone.Success, growsWithText = true)
            state == KitWorkGraphMark.Working ->
                if (KitMotion.state.reduced) {
                    androidx.compose.foundation.Image(
                        KitIcons.statusDot,
                        null,
                        Modifier.size(FlutterKitTokens.MARK_DOT_SIZE.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.accent),
                    )
                } else {
                    KitWorkingMark(colors.accent, glyph.dp, strokeWidth = FlutterKitTokens.SPINNER_STROKE.dp)
                }
            state == KitWorkGraphMark.Failed -> KitIcon(KitIcons.error, size = KitIconSize.Small, tone = TextTone.Primary, growsWithText = true)
            state == KitWorkGraphMark.NeedsYou -> KitIcon(KitIcons.question, size = KitIconSize.Small, tone = TextTone.Attention, growsWithText = true)
            state == KitWorkGraphMark.Stopped -> KitIcon(KitIcons.stopCircle, size = KitIconSize.Small, tone = TextTone.Secondary, growsWithText = true)
            else -> {
                // The waiting ring: `text3`, or `text2` where `text3` misses the 3:1 glyph floor on a ground.
                val grounds = listOf(roles.ground, roles.surface1, roles.surface2, roles.surface3)
                val ring = if (grounds.all { wcagContrast(roles.text3, it) >= GLYPH_CONTRAST_FLOOR }) colors.text3 else colors.text2
                Box(Modifier.size(FlutterKitTokens.MARK_RING_SIZE.dp).border(kitHairline(), ring, KitShapeId.Circle.shape()))
            }
        }
    }
}

/** LOOK-8: a glyph's contrast floor on `ground` and `surface1`–`surface3`. */
private const val GLYPH_CONTRAST_FLOOR: Double = 3.0 // F: lib/ui/kit/kit_status_mark.dart:78

/** A node's title: `rowTitle`, isolated, semibold in the blocked chain. */
@Composable
private fun TitleText(node: KitWorkGraphNode, inChain: Boolean, maxLines: Int) {
    val title = KitBidi.auto(node.title)
    if (!inChain) {
        KitText(title, role = TypeRole.RowTitle, maxLines = maxLines)
        return
    }
    val style =
        LocalKitTypography.current.style(TypeRole.RowTitle).copy(
            color = LocalKitColors.current.text1,
            fontWeight = chainWeight,
            textDirection = TextDirection.Content,
        )
    BasicText(title, Modifier.kitMaxWidth(CapRole.Reading).kitMayTruncate("title"), style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/**
 * The dependency graph of a team task's work items, readable on a phone (Dart `KitWorkGraph`,
 * lib/ui/kit/kit_work_graph.dart). On a compact (or short) window `rows` lists one item per row with the `needs`
 * links drawn as lanes in a start-side gutter and each row saying its state in words. From a medium window up
 * `layers` draws the graph in a zoomable canvas with the critical path and the blocked chain picked out, fitted on
 * open. The part is model-free: the host maps its own items to [KitWorkGraphNode]s.
 *
 * States: laid out, empty, one item (no links), blocked chain, needs you
 * Text: reading (titles, words)
 * Cap: Fill
 * Motion: none of its own; the zoom follows the fingers, the buttons jump
 * Hidden: pinch, drag and double tap in `layers` (visible twin: the zoom buttons)
 * Replaces: KitWorkGraph (lib/ui/kit/kit_work_graph.dart)
 */
@Composable
public fun KitWorkGraph(
    nodes: List<KitWorkGraphNode>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    layout: KitWorkGraphLayout = KitWorkGraphLayout.Auto,
    emptyText: String? = null,
) {
    if (nodes.isEmpty()) {
        KitInlineStateView(emptyText ?: stringResource(Res.string.kitWorkGraphEmpty), KitIcons.checklist, modifier)
        return
    }
    val window = LocalKitWindow.current
    val resolved =
        when {
            layout != KitWorkGraphLayout.Auto -> layout
            WindowClass.of(window.widthDp) == WindowClass.Compact -> KitWorkGraphLayout.Rows
            WindowClass.isShort(window.heightDp) -> KitWorkGraphLayout.Rows
            else -> KitWorkGraphLayout.Layers
        }
    if (resolved == KitWorkGraphLayout.Rows) RowsGraph(nodes, onOpen, modifier) else LayersGraph(nodes, onOpen, modifier)
}

/** Where a row sits on one link's lane in the `rows` gutter. */
private enum class RunPart { From, Through, To }

private class LaneRun(val edge: KitWorkGraphEdge, val part: RunPart)

/** `rows`: one item per row, each row painting its own slice of the start-side gutter (Dart `_RowsGraph`). */
@Composable
private fun RowsGraph(nodes: List<KitWorkGraphNode>, onOpen: (String) -> Unit, modifier: Modifier) {
    val tokens = LocalKitTokens.current
    val label = stringResource(Res.string.kitWorkGraph)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = if (constraints.hasBoundedWidth) maxWidth.value else 2f * FlutterKitTokens.GRAPH_NODE_WIDTH // F: lib/ui/kit/kit_work_graph.dart:868
        val geometry = remember(nodes, width) { KitWorkGraphGeometry.rows(nodes, width) { tokens.rowHeightTwo.toFloat() } }
        val order = geometry.layerRows.flatten()
        val position = order.withIndex().associate { (i, id) -> id to i }
        val runs = order.associateWith { ArrayList<LaneRun>() }
        var lanes = 0
        for (edge in geometry.edges) {
            lanes = max(lanes, edge.lane + 1)
            val from = position.getValue(edge.from)
            val to = position.getValue(edge.to)
            runs.getValue(edge.from).add(LaneRun(edge, RunPart.From))
            for (k in from + 1 until to) runs.getValue(order[k]).add(LaneRun(edge, RunPart.Through))
            runs.getValue(edge.to).add(LaneRun(edge, RunPart.To))
        }
        val gutter = (lanes * Space.S3).dp
        val byId = geometry.nodes.associateBy { it.id }
        Column(Modifier.fillMaxWidth().semantics { contentDescription = label }) {
            for (id in order) {
                val node = byId.getValue(id)
                RowNode(
                    node = node,
                    inChain = id in geometry.blockedChain,
                    dependencyTitles = node.dependsOn.filter { it != id }.mapNotNull { byId[it]?.title },
                    runs = runs.getValue(id),
                    gutter = gutter,
                    onOpen = { onOpen(id) },
                )
            }
        }
    }
}

@Composable
private fun RowNode(node: KitWorkGraphNode, inChain: Boolean, dependencyTitles: List<String>, runs: List<LaneRun>, gutter: Dp, onOpen: () -> Unit) {
    val tokens = LocalKitTokens.current
    val word = wordOf(node)
    val needs = dependencyTitles.firstOrNull()?.let { stringResource(Res.string.kitWorkGraphNeeds, it) }
    val hint =
        when {
            dependencyTitles.isEmpty() -> null
            dependencyTitles.size == 1 -> needs
            else -> pluralStringResource(Res.plurals.kitWorkGraphNeedsMore, dependencyTitles.size - 1, dependencyTitles.first(), dependencyTitles.size - 1)
        }
    val supporting = if (needs == null) word else "$word · $needs"
    val label = stringResource(Res.string.kitWorkGraphNode, node.title, word)
    val colors = LocalKitColors.current
    val hairline = kitHairline()
    val focus = kitFocusRingWidth()
    val dpr = kitPixelRatio()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier.fillMaxWidth().drawBehind {
            if (gutter.value == 0f) return@drawBehind
            val g = gutter.toPx()
            // The gutter sits at the start edge and mirrors inside itself in RTL.
            if (!rtl) {
                drawGutter(runs, Size(g, size.height), colors, hairline, focus, dpr)
            } else {
                translate(left = size.width) {
                    scale(-1f, 1f, pivot = Offset.Zero) { drawGutter(runs, Size(g, size.height), colors, hairline, focus, dpr) }
                }
            }
        },
    ) {
        // No fill and no radius of its own: the row sits on the host's panel.
        KitTappable(
            onOpen,
            Modifier.fillMaxWidth().padding(start = gutter).kitEdge(KitEdge.Bleed).semantics {
                contentDescription = label
                if (hint != null) stateDescription = hint
            },
            shape = KitShapeId.Square,
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = tokens.rowHeightTwo.dp), contentAlignment = Alignment.CenterStart) {
                Row(Modifier.padding(horizontal = Space.S3.dp, vertical = Space.S2.dp).clearAndSetSemantics {}) {
                    KitWorkGraphTaskMark(node.mark, node.paused)
                    Spacer(Modifier.width(Space.S2.dp))
                    Column(Modifier.weight(1f)) {
                        TitleText(node, inChain, maxLines = 2) // F: lib/ui/kit/kit_work_graph.dart:989
                        KitText(supporting, Modifier.kitMayTruncate("supporting"), role = TypeRole.Secondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * One row's slice of the `rows` gutter: each link's lane (snapped to the physical grid) and, at either end, a stub
 * between the lane and the row's start edge; the needed item's stub leaves just below the row's centre, the dependent's
 * arrives just above it with an arrow head into the row.
 */
private fun DrawScope.drawGutter(runs: List<LaneRun>, box: Size, colors: io.github.eslamasabry.oc.ui.kit.theme.KitColors, hairline: Dp, focus: Dp, dpr: Float) {
    val laneWidth = Space.S3.toFloat()
    val stubOffset = Space.S1.toFloat()
    val px = 1.dp.toPx()
    val centre = box.height / px / 2 // F: lib/ui/kit/kit_work_graph.dart:1081
    val right = box.width / px
    val bottom = box.height / px
    for (run in runs.sortedBy { rank(it.edge) }) {
        val edge = run.edge
        val ink = linkInk(edge, colors.text1, colors.text2, colors.text3, hairline, focus)
        val w = ink.width.value
        val x = snapStroke((edge.lane + .5f) * laneWidth, w, dpr) // F: lib/ui/kit/kit_work_graph.dart:1091
        val path = Path()
        when (run.part) {
            RunPart.From -> {
                val y = snapStroke(centre + stubOffset, w, dpr)
                path.moveTo(right * px, y * px)
                path.lineTo(x * px, y * px)
                path.lineTo(x * px, bottom * px)
            }
            RunPart.Through -> {
                path.moveTo(x * px, 0f)
                path.lineTo(x * px, bottom * px)
            }
            RunPart.To -> {
                val y = snapStroke(centre - stubOffset, w, dpr)
                path.moveTo(x * px, 0f)
                path.lineTo(x * px, y * px)
                path.lineTo(right * px, y * px)
            }
        }
        strokePath(path, ink, ink.dashed)
        if (run.part == RunPart.To) {
            val y = snapStroke(centre - stubOffset, w, dpr)
            val head =
                Path().apply {
                    moveTo((right - ARROW_LENGTH) * px, (y - ARROW_HALF_WIDTH) * px)
                    lineTo(right * px, y * px)
                    lineTo((right - ARROW_LENGTH) * px, (y + ARROW_HALF_WIDTH) * px)
                }
            strokePath(head, ink, dashed = false)
        }
    }
}

/** The `layers` links: each curve, then its arrow head pointing down into the node that needs it. */
private fun DrawScope.drawLayerLinks(geometry: KitWorkGraphGeometry, colors: io.github.eslamasabry.oc.ui.kit.theme.KitColors, hairline: Dp, focus: Dp) {
    val px = 1.dp.toPx()
    for (edge in geometry.edges.sortedBy { rank(it) }) {
        val ink = linkInk(edge, colors.text1, colors.text2, colors.text3, hairline, focus)
        val path =
            Path().apply {
                moveTo(edge.start.x * px, edge.start.y * px)
                cubicTo(edge.control1.x * px, edge.control1.y * px, edge.control2.x * px, edge.control2.y * px, edge.end.x * px, edge.end.y * px)
            }
        strokePath(path, ink, ink.dashed)
        val tip = edge.end
        val head =
            Path().apply {
                moveTo((tip.x - ARROW_HALF_WIDTH) * px, (tip.y - ARROW_LENGTH) * px)
                lineTo(tip.x * px, tip.y * px)
                lineTo((tip.x + ARROW_HALF_WIDTH) * px, (tip.y - ARROW_LENGTH) * px)
            }
        strokePath(head, ink, dashed = false)
    }
}

/**
 * Every chip's size: GRAPH_NODE_WIDTH grown with the text, and the chip's insides (space2 above and below) around one
 * `rowTitle` line, plus one `secondary` line for the state word when any item is stuck, never under the touch target,
 * rounded up to the physical pixel.
 */
@Composable
private fun nodeSize(withWord: Boolean): Size {
    val typography = LocalKitTypography.current
    val scale = LocalDensity.current.fontScale
    val dpr = kitPixelRatio()
    val tokens = LocalKitTokens.current
    fun line(role: TypeRole): Float = typography.style(role).lineHeight.value * scale
    val text = line(TypeRole.RowTitle) + (if (withWord) line(TypeRole.Secondary) else 0f)
    val node = FlutterKitTokens.GRAPH_NODE_WIDTH.toFloat()
    return Size(
        ceilToPixel(max(node, node * scale), dpr),
        ceilToPixel(max(tokens.minTarget.toFloat(), 2 * Space.S2 + text), dpr), // F: lib/ui/kit/kit_work_graph.dart:1181
    )
}

/** `layers`: the layered graph in a zoomable canvas, fitted on open (Dart `_LayersGraph`). */
@Composable
private fun LayersGraph(nodes: List<KitWorkGraphNode>, onOpen: (String) -> Unit, modifier: Modifier) {
    val withWord = nodes.any { it.stuck }
    val size = nodeSize(withWord)
    val geometry = remember(nodes, size) { KitWorkGraphGeometry.layers(nodes, size) }
    val colors = LocalKitColors.current
    val hairline = kitHairline()
    val focus = kitFocusRingWidth()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val byId = geometry.nodes.associateBy { it.id }
    val order = geometry.layerRows.flatten()
    CanvasZoom(stringResource(Res.string.kitWorkGraph), geometry.size, modifier) {
        Box(
            Modifier.requiredSize(geometry.size.width.dp, geometry.size.height.dp).drawBehind {
                // Mirrored in RTL (x → width − x), like the chips placed start-relative below.
                scale(if (rtl) -1f else 1f, 1f) { drawLayerLinks(geometry, colors, hairline, focus) }
            },
        ) {
            for (id in order) {
                val rect = geometry.rects[id] ?: continue
                val node = byId[id] ?: continue
                LayerChip(
                    node = node,
                    inChain = id in geometry.blockedChain,
                    onOpen = { onOpen(id) },
                    modifier = Modifier.offset(rect.left.dp, rect.top.dp).requiredSize(rect.width.dp, rect.height.dp),
                )
            }
        }
    }
}

/**
 * A `layers` chip: `surface1` with a one-physical-pixel `hairline` border (`text1` in the blocked chain), the panel
 * radius, the tappable's treatments, the mark and a one-line title; a stuck item also shows its state word.
 */
@Composable
private fun LayerChip(node: KitWorkGraphNode, inChain: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val word = wordOf(node)
    val label = stringResource(Res.string.kitWorkGraphNode, node.title, word)
    val colors = LocalKitColors.current
    val chainEdge = if (inChain) Modifier.border(kitHairline(), colors.text1, KitShapeId.Panel.shape()) else Modifier
    KitSurface(modifier.then(chainEdge), level = SurfaceLevel.Surface1, shape = KitShapeId.Panel, padding = KitSurfacePadding.None, outlined = !inChain) {
        KitTappable(onOpen, Modifier.fillMaxSize().semantics { contentDescription = label }, shape = KitShapeId.Panel) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = Space.S3.dp, vertical = Space.S2.dp).clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitWorkGraphTaskMark(node.mark, node.paused)
                Spacer(Modifier.width(Space.S2.dp))
                Column(Modifier.weight(1f)) {
                    TitleText(node, inChain, maxLines = 1)
                    if (node.stuck) KitText(word, Modifier.kitMayTruncate("word"), role = TypeRole.Secondary, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The canvas viewer of `layers` (Dart `KitZoom(mode: canvas)`, lib/ui/kit/kit_image.dart:792): the child keeps its own
 * size, fitted into the view on open (never above 1×, never below the floor), pinch and drag move it, and a stadium of
 * Zoom out, Fit and Zoom in floats at the bottom-end corner. Kit-content's `KitZoomView` is the fit-mode twin; :ui:kit
 * cannot reach it, so the canvas mode lives here until the lead moves `KitZoom` into :ui:kit.
 */
@Composable
private fun CanvasZoom(label: String, child: Size, modifier: Modifier, content: @Composable () -> Unit) {
    var viewport by remember { mutableStateOf(Size.Zero) }
    var scale by remember { mutableStateOf(Float.NaN) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    fun fitted(): Pair<Float, Offset> {
        val px = density.density
        val vw = viewport.width / px
        val vh = viewport.height / px
        if (child.width <= 0f || child.height <= 0f || vw <= 0f) return 1f to Offset.Zero
        val s = min(vw / child.width, vh / child.height).coerceIn(ZOOM_MIN, 1f)
        return s to Offset((vw - child.width * s) / 2 * px, (vh - child.height * s) / 2 * px) // F: lib/ui/kit/kit_image.dart:1072-1073
    }
    if (scale.isNaN() && viewport != Size.Zero) {
        val (s, o) = fitted()
        scale = s
        offset = o
    }
    val (fitScale, fitOffset) = fitted()
    val atRest = abs(scale - fitScale) < ZOOM_EPSILON && (offset - fitOffset).getDistance() < ZOOM_EPSILON
    val atMax = scale >= ZOOM_MAX - ZOOM_EPSILON
    val canZoomOut = scale > ZOOM_MIN + ZOOM_EPSILON
    fun zoomTo(target: Float) {
        val s = target.coerceIn(ZOOM_MIN, ZOOM_MAX)
        val centre = viewport.center
        offset = centre - (centre - offset) * (s / scale)
        scale = s
    }
    val level = stringResource(Res.string.kitZoomLevel, ((if (scale.isNaN()) 1f else scale) * 100).roundToInt().toString()) // F: lib/ui/kit/kit_image.dart:1339
    Box(modifier.fillMaxSize().semantics { contentDescription = label; stateDescription = level }) {
        Box(
            Modifier.fillMaxSize()
                .clipToBounds()
                .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val s = (scale * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
                        offset = centroid - (centroid - offset) * (s / scale) + pan
                        scale = s
                    }
                },
        ) {
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).graphicsLayer {
                    val s = if (scale.isNaN()) 1f else scale
                    scaleX = s
                    scaleY = s
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            ) { content() }
        }
        val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Floating, KitShapeId.Pill, SurfaceLevel.Surface2, ownFill = colorsSurface2()))
        Row(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = Space.S4.dp, bottom = Space.S4.dp)
                .then(look.modifier)
                .clip(look.shape)
                .padding(horizontal = Space.S2.dp, vertical = Space.S1.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            val atStart = stringResource(Res.string.kitZoomAtStart)
            KitIconButton(
                KitIcons.collapse, stringResource(Res.string.kitZoomOut), { zoomTo(scale / ZOOM_STEP) },
                enabled = KitEnabled.of(canZoomOut, atStart),
            )
            KitIconButton(
                KitIcons.retry, stringResource(Res.string.kitZoomFit), { scale = fitScale; offset = fitOffset },
                enabled = KitEnabled.of(!atRest, atStart),
            )
            KitIconButton(
                KitIcons.expand, stringResource(Res.string.kitZoomIn), { zoomTo(scale * ZOOM_STEP) },
                enabled = KitEnabled.of(!atMax, stringResource(Res.string.kitZoomAtMax)),
            )
        }
    }
}

@Composable
private fun colorsSurface2(): Color = LocalKitColors.current.surface2
