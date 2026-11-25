package io.github.eslamasabry.oc.ui.kit.chip

import io.github.eslamasabry.oc.ui.kit.foundation.kitScrollCue
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitLayoutMarks
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowReach
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowRoom
import io.github.eslamasabry.oc.ui.kit.frame.FloatingPaneRow
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitOnFloatingPane
import io.github.eslamasabry.oc.ui.kit.frame.ScrollFadeRules
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitChipsFirst
import io.github.eslamasabry.oc.ui.words.res.kitChipsMore
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/** Dart `KitFilterChips` numbers. */
public object FilterChipsRules {
    /** An edge's fade: the gutter plus 32 dp, enough to show a word fading rather than cut. */
    public fun fadeWidthDp(gutter: Int): Int = gutter + 32 // F: lib/ui/kit/kit_filter_chips.dart:28

    /**
     * Review 2026-11-13 #18: how far a chip line fades at an edge with more past it, on the page and on the floating
     * pane alike. It lies over the cut chip only, never over a whole chip beside it.
     */
    public const val CHIP_FADE_DP: Int = Space.S6 + Space.S2

    /**
     * The least of a cut chip a line shows at rest: its start padding and a few whole letters before [CHIP_FADE_DP]
     * thins the rest out, so the piece names the chip and reads as "more this way" (never a 1-3 letter stump: "Al").
     */
    public const val MIN_PEEK_DP: Int = CHIP_FADE_DP + Space.S6 + Space.S2

    /** The part of a fade nearest its edge that is fully clear, so no letter stands half drawn at the edge. */
    public const val CLEAR_PART: Float = 1f / 3f

    /**
     * The gap between chips at rest: always [gap]. A chip the end edge cuts stays where it is and is thinned by the
     * edge fade, which is the cue that the line scrolls on (coordinator decision 2026-11-14: no chip is ever carried out
     * of view to avoid a stump). The other arguments are kept for callers.
     */
    public fun restGap(sizes: IntArray, gap: Float, room: Float, minPeek: Float): Float = gap

    /**
     * Where a line that rests whole settles: the scroll of the nearest chip's start (a chip's start less the line's start
     * padding), so the first chip showing is whole. [scroll] is where it is, [max] the end of its range.
     */
    public fun restStart(sizes: IntArray, gap: Float, startPad: Float, scroll: Float, max: Int): Int {
        if (sizes.isEmpty() || max <= 0) return scroll.toInt()
        var x = 0f
        var best = 0f
        var bestDistance = abs(scroll - best)
        for (w in sizes) {
            val at = x.coerceIn(0f, max.toFloat())
            if (abs(scroll - at) < bestDistance) { best = at; bestDistance = abs(scroll - at) }
            x += w + gap
        }
        return best.roundToInt()
    }

    /**
     * The window's width when it ends after the last whole chip: [width] when every chip it reaches is whole, else the end
     * of the last chip that fits, a gap after it. Chips [sizes] [gap] apart begin [startPad] in, scrolled [scroll] along.
     */
    public fun wholeEnd(sizes: IntArray, gap: Float, startPad: Float, scroll: Float, width: Float): Float {
        var x = startPad - scroll
        var end = 0f
        var past = false
        for (w in sizes) {
            val right = x + w
            if (right <= width + .5f) end = maxOf(end, right) else if (x < width) past = true else past = true
            x += w + gap
        }
        return if (past && end > 0f) minOf(width, end + gap) else width
    }

    /**
     * The chips' spans in the line's window, in reading order: (start, end) from its start edge, for chips of [sizes]
     * [gap] apart that begin [startPad] in and are scrolled [scroll] along.
     */
    public fun spans(sizes: IntArray, gap: Float, startPad: Float, scroll: Float): List<Pair<Float, Float>> {
        var x = startPad - scroll
        return sizes.map { w -> (x to x + w).also { x += w + gap } }
    }

    /**
     * How wide each edge's fade is, in reading order (start, end), for a window [width] wide: zero where no chip runs
     * past that edge, else at most [fade] and never wider than the cut chip's piece in the window, so a whole chip is
     * never thinned out.
     */
    public fun edgeFades(spans: List<Pair<Float, Float>>, width: Float, fade: Float): Pair<Float, Float> {
        val start = spans.firstOrNull { (a, b) -> a < 0f && b > 0f }?.let { (_, b) -> minOf(fade, b) } ?: 0f
        val end = spans.firstOrNull { (a, b) -> a < width && b > width }?.let { (a, _) -> minOf(fade, width - a) } ?: 0f
        return start to end
    }

    /**
     * Single-pass rest-whole layout plan: decides whether the chip line fits at least its first chip beside any
     * needed "+N" cues, how many chips lie before/after the visible window, and the window's clipped inner width.
     * When even the first chip cannot fit beside the cues, returns `whole = false` with zero cues so no "+N" mark
     * is ever drawn for a frame.
     */
    internal fun planWhole(metrics: WholeMetrics, budget: WholeBudget): WholePlan {
        val fullInner = budget.innerBudget(budget.totalWidth)
        if (metrics.sizes.isEmpty()) return WholePlan(true, 0, 0, fullInner, fullInner)
        // Lower-bound pre-pass using minCueWidth (KitChip's minTarget floor) avoids subcomposing throwaway cue
        // probes on narrow widths or intermediate counts before measuring the real KitChip measurables.
        val floorWidths = CueMeasurers(beforeWidth = { budget.minCueWidth }, afterWidth = { budget.minCueWidth })
        val seed = convergeWhole(metrics, budget, fullInner, floorWidths)
        val noCues = !seed.whole || (seed.before == 0 && seed.after == 0)
        return if (noCues) {
            seed
        } else {
            val realWidths = CueMeasurers(budget.beforeCueWidth, budget.afterCueWidth)
            convergeWhole(metrics, budget, fullInner, realWidths, seed.before to seed.after)
        }
    }
}

private const val WHOLE_HALF_PX = 0.5f
private const val MAX_WHOLE_PASSES = 6
private const val HALF_DIVISOR = 2f

private fun convergeWhole(
    metrics: WholeMetrics,
    budget: WholeBudget,
    fullInner: Int,
    widths: CueMeasurers,
    start: Pair<Int, Int> = 0 to 0,
): WholePlan {
    var before = start.first
    var after = start.second
    var converged: WholePlan? = null
    var step = 0
    while (converged == null && step < MAX_WHOLE_PASSES) {
        val plan = resolveWholePass(metrics, budget, fullInner, widths, before to after)
        if (!plan.whole || (plan.before == before && plan.after == after)) {
            converged = plan
        } else {
            before = plan.before
            after = plan.after
        }
        step++
    }
    return converged ?: resolveWholePass(metrics, budget, fullInner, widths, before to after).let { settled ->
        if (settled.whole) settled.copy(before = before, after = after) else settled
    }
}

private fun resolveWholePass(
    metrics: WholeMetrics,
    budget: WholeBudget,
    fullInner: Int,
    widths: CueMeasurers,
    counts: Pair<Int, Int>,
): WholePlan {
    val reserved =
        cueSpace(counts.first, widths.beforeWidth, budget.cueGap) +
            cueSpace(counts.second, widths.afterWidth, budget.cueGap)
    val availInner = budget.innerBudget((budget.totalWidth - reserved).coerceAtLeast(0))
    if (metrics.firstVisibleSize() + metrics.startPad > availInner + WHOLE_HALF_PX) {
        return WholePlan(whole = false, before = 0, after = 0, availInner = fullInner, clippedWidth = fullInner)
    }
    val edge =
        FilterChipsRules.wholeEnd(metrics.sizes, metrics.gap, metrics.startPad, metrics.scroll, availInner.toFloat())
    val clipped = edge.roundToInt().coerceIn(0, availInner)
    val (gone, past) = countHidden(metrics, clipped)
    return WholePlan(whole = true, before = gone, after = past, availInner = availInner, clippedWidth = clipped)
}

private fun cueSpace(count: Int, measureWidth: (Int) -> Int, gap: Int): Int =
    if (count > 0) measureWidth(count) + gap else 0

private fun countHidden(metrics: WholeMetrics, clipped: Int): Pair<Int, Int> {
    var x = metrics.startPad - metrics.scroll
    var gone = 0
    var past = 0
    for (w in metrics.sizes) {
        if (x + w <= metrics.startPad + WHOLE_HALF_PX) gone++ else if (x + w > clipped + WHOLE_HALF_PX) past++
        x += w + metrics.gap
    }
    return gone to past
}

/** Chip geometry for one pass of [FilterChipsRules.planWhole]. */
internal class WholeMetrics(
    val sizes: IntArray,
    val gap: Float,
    val startPad: Float,
    val scroll: Float,
) {
    /** Width of the first chip whose end lies inside the visible window at [scroll]. */
    fun firstVisibleSize(): Int {
        var x = startPad - scroll
        for (w in sizes) {
            if (x + w > startPad + WHOLE_HALF_PX) return w
            x += w + gap
        }
        return sizes.first()
    }
}

/** Available width and real cue-chip measurements for one pass of [FilterChipsRules.planWhole]. */
internal class WholeBudget(
    val totalWidth: Int,
    val cueGap: Int,
    val minCueWidth: Int,
    val innerBudget: (Int) -> Int,
    val beforeCueWidth: (Int) -> Int,
    val afterCueWidth: (Int) -> Int,
)

/** Measures real "+N" cue widths for [FilterChipsRules.planWhole]. */
internal class CueMeasurers(
    val beforeWidth: (Int) -> Int,
    val afterWidth: (Int) -> Int,
)

/** Result of [FilterChipsRules.planWhole]: computed in layout before anything is placed or drawn. */
internal data class WholePlan(
    val whole: Boolean,
    val before: Int,
    val after: Int,
    val availInner: Int,
    val clippedWidth: Int,
)

private sealed interface WholeSlot {
    data object Line : WholeSlot
    data class Before(val count: Int) : WholeSlot
    data class After(val count: Int) : WholeSlot
}

/** Where a chip line keeps its chips' widths and its resting gap, between laying the chips out and drawing the fade. */
private class ChipLine(
    private val baseGap: Dp,
    private val minPeek: Dp,
    private val restWhole: Boolean = false,
) : Arrangement.Horizontal {
    var gap by mutableStateOf(baseGap)

    /** The window's room from the first chip's start to the end edge, set while the line measures. */
    var roomPx = 0

    /** Full bounded width of the chip line box before any whole-chip clipping, set while the line measures. */
    var viewportPx = 0
    var sizes = IntArray(0)
    var gapPx = 0f

    /** `restWhole`: true when at least the first chip fits beside any needed "+N" cues; false falls back to fades. */
    var wholeActive by mutableStateOf(restWhole)

    /** True once the user taps "+N" to expand the row into a sideways-scrolling line with edge fades. */
    var expanded by mutableStateOf(false)

    /** Latest single-pass plan computed during [WholeFilterChips] layout. */
    var lastPlan: WholePlan = WholePlan(whole = restWhole, before = 0, after = 0, availInner = 0, clippedWidth = 0)

    /** Hook invoked inside the chip box's measure pass to plan cues and return the available inner width. */
    var onMeasureWhole: ((innerPlaceableWidth: Int, hasBoundedWidth: Boolean) -> Int)? = null

    /**
     * [KitFilterChips] `restWhole`: room kept after the last chip while resting whole so every chip can come to the
     * line's start. Drops to 0 once expanded to a scrolling line so scrolling to the end stops cleanly at the last
     * chip. Read in the same measure pass that laid the chips out.
     */
    fun tail(): Int = if (wholeActive && sizes.isNotEmpty()) (roomPx - sizes.last()).coerceAtLeast(0) else 0

    /**
     * Scroll offset bringing chip [first] in whole inside the visible window (shadow room left out), keeping an edge
     * cue on both sides when room allows. If the chip is wider than that room, starts it at the visible start so no
     * start fade dims its beginning.
     */
    fun revealTarget(first: Int, insets: RevealInsets, fadePx: Float): Int {
        if (sizes.isEmpty()) return 0
        val viewport = viewportPx.toFloat().coerceAtLeast(1f)
        val contentW =
            sizes.sum().toFloat() + (sizes.size - 1).coerceAtLeast(0) * gapPx + insets.startPadPx + insets.endPadPx
        val maxNoTail = (contentW - viewport).roundToInt().coerceAtLeast(0)
        val clamped = first.coerceIn(0, sizes.lastIndex)
        var chipStart = 0f
        for (i in 0 until clamped) chipStart += sizes[i] + gapPx
        val chipW = sizes[clamped].toFloat()
        val visibleRoom = (viewport - insets.growStartPx - insets.growEndPx).coerceAtLeast(0f)
        val atVisibleStart = (insets.startPadPx - insets.growStartPx) + chipStart
        val target =
            if (chipW > visibleRoom) {
                floor(atVisibleStart).toInt()
            } else {
                val slack = visibleRoom - chipW
                val leftPeek = if (clamped > 0 && clamped < sizes.lastIndex) minOf(fadePx, slack / HALF_DIVISOR) else 0f
                val minScroll = ceil(atVisibleStart - slack).toInt()
                val maxScroll = floor(atVisibleStart).toInt()
                val desired = (atVisibleStart - leftPeek).roundToInt()
                if (minScroll <= maxScroll) desired.coerceIn(minScroll, maxScroll) else maxScroll
            }
        return target.coerceIn(0, maxNoTail)
    }

    override val spacing: Dp get() = gap

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) {
        this@ChipLine.sizes = sizes.copyOf()
        val step = FilterChipsRules.restGap(sizes, baseGap.toPx(), roomPx.toFloat(), minPeek.toPx()).roundToInt()
        gapPx = step.toFloat()
        val dp = step.toDp()
        // Read by the row's measure: a changed gap measures the line again, so its scroll range holds the gaps.
        if (dp != gap) gap = dp
        // As spacedBy: in reading order from the start, the first chip at the right in a right-to-left line.
        var x = 0
        val order = if (layoutDirection == LayoutDirection.Rtl) sizes.indices.reversed() else sizes.indices
        for (i in order) {
            outPositions[i] = x
            x += sizes[i] + step
        }
    }
}

/**
 * The filter chips under a screen's title ("All projects", "Needs you", "Running") (Dart `KitFilterChips`; plan 06
 * §7.5). The chips are `KitChip`s the caller builds (selected = the active filter; only "Needs you" may be amber); this
 * part lays them on one line on the gutter. The line scrolls sideways when they do not fit (large text, Arabic): it
 * never wraps and no chip is hidden.
 *
 * Review 2026-11-13 #18: at rest the last chip shown is whole, or cut with at least [FilterChipsRules.MIN_PEEK_DP] of
 * it showing ([FilterChipsRules.restGap] widens the gaps to carry a would-be stump past the edge). An edge with more
 * past it fades over the cut chip only ([FilterChipsRules.edgeFades]), its outer third clear, so no letter stands half
 * drawn at the edge and no whole chip beside it thins out.
 *
 * States: none (each chip carries its own selected look)
 * Text: frame (the chips)
 * Cap: Reading
 * Motion: the fade follows the scroll (class Follow)
 * Replaces: KitFilterChips, KitFilterChip (lib/ui/kit/kit_filter_chips.dart)
 */
@Composable
public fun KitFilterChips(
    modifier: Modifier = Modifier,
    bleed: Boolean = false,
    /**
     * The line's end is a block's gutter (the chat's phrase row in the bottom block): it ends there, its end fade inside
     * it (audit 2026-11-14, finding 21). Elsewhere (the home's chips beside the server pill) the clip grows past the
     * line's end by the chips' shadow reach, so the last chip's style shadow keeps its room (SoftShadowRoomTest).
     */
    endOnGutter: Boolean = false,
    /**
     * The line never rests with a chip cut by an edge (the home's chips beside the server pill, at 360 dp): it settles with
     * its first chip whole at the start, the window ends after the last whole chip, and a "+N" chip says how many lie beyond.
     * Left to right only; a right-to-left line keeps the plain scroll.
     */
    restWhole: Boolean = false,
    chips: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalKitTokens.current
    val scroll = rememberScrollState()
    // The line clips sideways for its scroll and fade; the chips' style shadows keep their room above and below.
    val room = kitShadowReach()
    // [bleed]: the line sits in a block that already keeps the gutter (a screen's bottom block). E20-S08: it scrolls
    // inside that gutter, its chips starting on it and fading out at the block's end, never cut at the screen's edge
    // (it used to reach out over the gutter, so the last chip ran off the screen).
    val inset = if (bleed) 0.dp else kitPageGutter()
    // On the floating glass pane (Glass with a lens, KitFloatingBar's second row) the line clips at the pane's sides
    // and its chips rest on the gutter inside them, so no chip is ever cut by the pane's outline (FloatingPaneRow).
    val onPane = LocalKitOnFloatingPane.current
    val paneEdge = FloatingPaneRow.EDGE_DP.dp
    val paneInset = FloatingPaneRow.contentInsetDp(tokens.gutter).dp
    val restStart = if (!onPane) inset else if (bleed) 0.dp else paneInset
    val restEnd = if (onPane) paneInset else inset
    // Device pass 2026-11-13 (Soft, home): where the line starts or ends on its clip edge (beside the server pill, on
    // the pane) a chip resting there had its shadow cut into a straight edge. The clip grows past that end by the
    // shadow's reach and the chips keep their place, so their shadows stay whole; the fade's clear part covers it.
    // On the floating glass pane the clip stays at the pane's sides (nothing draws beside the pane).
    val growStart = if (onPane) 0.dp else (room - restStart).coerceAtLeast(0.dp)
    // A line on a block that keeps the gutter ([endOnGutter], the chat's phrase row) ends on that gutter: its end fade,
    // clear third included, lies inside it, so the last chip thins out before the block's edge and is never cut flat
    // beside the screen's edge (audit 2026-11-14, finding 21). Any other line's end keeps its chips' shadow room (audit
    // 2, 2026-11-14: pinned chrome under the bar is never clipped; its style shadows keep their room).
    val growEnd = if (onPane || endOnGutter) 0.dp else (room - restEnd).coerceAtLeast(0.dp)
    val startPad = restStart + growStart
    val endPad = restEnd + growEnd
    val wantWhole = restWhole && LocalLayoutDirection.current == LayoutDirection.Ltr
    val line = remember(wantWhole) { ChipLine(Space.S2.dp, FilterChipsRules.MIN_PEEK_DP.dp, wantWhole) }
    val density = LocalDensity.current
    if (wantWhole) {
        LaunchedEffect(scroll, line) {
            snapshotFlow { if (scroll.isScrollInProgress) null else scroll.value }.filterNotNull().collectLatest { at ->
                delay(REST_SNAP_DELAY_MS)
                if (!line.wholeActive) return@collectLatest
                val padPx = with(density) { startPad.toPx() }
                val goal = FilterChipsRules.restStart(line.sizes, line.gapPx, padPx, at.toFloat(), scroll.maxValue)
                if (goal != at) scroll.animateScrollTo(goal)
            }
        }
    }
    val scope = rememberCoroutineScope()
    val lineBox: @Composable (Modifier) -> Unit = { outer ->
        Box(
            outer.kitMaxWidth(CapRole.Reading)
                .then(if (onPane) Modifier.padding(start = if (bleed) 0.dp else paneEdge, end = paneEdge) else Modifier)
                .kitShadowRoom(room, start = growStart, end = growEnd)
                .layout { measurable, constraints ->
                    line.viewportPx = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
                    line.roomPx = if (constraints.hasBoundedWidth) constraints.maxWidth - startPad.roundToPx() else 0
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .clipToBounds()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (line.wholeActive) return@drawWithContent // cues, not fades, say there is more
                    if (scroll.maxValue <= 0 || scroll.maxValue == Int.MAX_VALUE) return@drawWithContent
                    val fade = FilterChipsRules.CHIP_FADE_DP.dp.toPx()
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    val gStart = if (!wantWhole) 0f else (if (rtl) growEnd else growStart).toPx()
                    val gEnd = if (!wantWhole) 0f else (if (rtl) growStart else growEnd).toPx()
                    val visibleEnd = (size.width - gEnd).coerceAtLeast(gStart)
                    val visibleW = (visibleEnd - gStart).coerceAtLeast(0f)
                    val spans = FilterChipsRules.spans(
                        line.sizes,
                        line.gapPx,
                        if (wantWhole) startPad.toPx() - gStart else startPad.toPx(),
                        scroll.value.toFloat(),
                    )
                    val (startFade, endFade) =
                        FilterChipsRules.edgeFades(spans, if (wantWhole) visibleW else size.width, fade)
                    val left = if (rtl) endFade else startFade
                    val right = if (rtl) startFade else endFade
                    // The third of a full fade nearest the edge is always clear, even over a short piece.
                    val clear = fade * FilterChipsRules.CLEAR_PART
                    val (hasLeft, hasRight) =
                        if (wantWhole) {
                            ScrollFadeRules.leftRight(ScrollFadeRules.edges(scroll.value, scroll.maxValue), rtl)
                        } else {
                            false to false
                        }
                    if (gStart > 0f && (left > 0f || hasLeft)) {
                        drawRect(Color.Transparent, Offset.Zero, Size(gStart, size.height), blendMode = BlendMode.DstIn)
                    }
                    if (left > 0f) {
                        drawRect(
                            edgeMask(gStart, gStart + left, clear, clearAtStart = true),
                            Offset(gStart, 0f),
                            Size(left, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    if (right > 0f) {
                        val startX = (visibleEnd - right).coerceAtLeast(gStart)
                        drawRect(
                            edgeMask(startX, visibleEnd, clear, clearAtStart = false),
                            Offset(startX, 0f),
                            Size(visibleEnd - startX, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    if (gEnd > 0f && (right > 0f || hasRight)) {
                        drawRect(
                            Color.Transparent,
                            Offset(visibleEnd, 0f),
                            Size(gEnd, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                // Inside the clip and the fade: a line that rests whole is cut to the end of its last whole chip in the
                // same measure pass, so the clip, the fade, the guard and any "+N" cue agree on frame 0.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val hook = line.onMeasureWhole
                    val width =
                        if (wantWhole && hook != null) {
                            val availInner = hook(placeable.width, constraints.hasBoundedWidth)
                            if (constraints.hasBoundedWidth && line.wholeActive) {
                                // Read scroll.value here so this box re-measures when scrolled and clips right
                                // after the last visible whole chip within availInner.
                                FilterChipsRules.wholeEnd(
                                    line.sizes,
                                    line.gapPx,
                                    startPad.toPx(),
                                    scroll.value.toFloat(),
                                    availInner.toFloat(),
                                ).roundToInt().coerceIn(0, availInner)
                            } else {
                                availInner
                            }
                        } else {
                            placeable.width
                        }
                    layout(width, placeable.height) { placeable.place(0, 0) }
                }
                .testTag("kit-filter-chips"),
        ) {
            Row(
                Modifier.horizontalScroll(scroll)
                    .kitScrollCue("edge fade")
                    .semantics { if (line.wholeActive) this[KitLayoutMarks.RestsWhole] = "filter chips" }
                    .padding(start = startPad, end = endPad, top = room, bottom = room)
                    // Inside the padding, in the same pass that lays the chips out: the room kept after the last chip.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width + line.tail(), placeable.height) { placeable.place(0, 0) }
                    },
                horizontalArrangement = line,
                verticalAlignment = Alignment.CenterVertically,
                content = chips,
            )
        }
    }
    if (!wantWhole) {
        lineBox(modifier)
    } else {
        val wholeConfig =
            WholeChipsConfig(
                line = line,
                scroll = scroll,
                minCueWidthDp = tokens.minTarget.dp,
                insets =
                    WholeInsets(
                        startPad = startPad,
                        paneStart = if (onPane && !bleed) paneEdge else 0.dp,
                        paneEnd = if (onPane) paneEdge else 0.dp,
                        growStart = growStart,
                        growEnd = growEnd,
                    ),
                onFirst = { scope.launch { scroll.animateScrollTo(0) } },
                onMore = { hidden ->
                    line.expanded = true
                    val first = (line.sizes.size - hidden).coerceAtLeast(0)
                    val revealInsets = with(density) {
                        RevealInsets(
                            startPadPx = startPad.toPx(),
                            endPadPx = endPad.toPx(),
                            growStartPx = growStart.toPx(),
                            growEndPx = growEnd.toPx(),
                        )
                    }
                    val fadePx = with(density) { FilterChipsRules.CHIP_FADE_DP.dp.toPx() }
                    val target = line.revealTarget(first, revealInsets, fadePx)
                    scope.launch { scroll.animateScrollTo(target) }
                },
            )
        WholeFilterChips(modifier = modifier, config = wholeConfig, lineBox = lineBox)
    }
}

private const val REST_SNAP_DELAY_MS = 120L

private class RevealInsets(
    val startPadPx: Float,
    val endPadPx: Float,
    val growStartPx: Float,
    val growEndPx: Float,
)

private class WholeInsets(
    val startPad: Dp,
    val paneStart: Dp,
    val paneEnd: Dp,
    val growStart: Dp,
    val growEnd: Dp,
)

private class WholeChipsConfig(
    val line: ChipLine,
    val scroll: ScrollState,
    val minCueWidthDp: Dp,
    val insets: WholeInsets,
    val onFirst: () -> Unit,
    val onMore: (Int) -> Unit,
)

private class CueSpec(
    val slot: WholeSlot,
    val count: Int,
    val spoken: String,
    val visibility: CueVisibility,
    val onClick: () -> Unit,
)

/** Tracks whether a subcomposed "+N" cue is placed in this pass so unplaced probes expose no semantics. */
private class CueVisibility {
    var placed: Boolean = false
        private set
    private var node: CueSemanticsNode? = null

    fun attach(target: CueSemanticsNode?) {
        node = target
    }

    fun update(isPlaced: Boolean) {
        if (placed != isPlaced) {
            placed = isPlaced
            node?.refresh()
        }
    }
}

private data class CueSemanticsElement(val visibility: CueVisibility) : ModifierNodeElement<CueSemanticsNode>() {
    override fun create(): CueSemanticsNode = CueSemanticsNode(visibility)

    override fun update(node: CueSemanticsNode) {
        node.bind(visibility)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wholeChipCueSemantics"
        properties["placed"] = visibility.placed
    }
}

private class CueSemanticsNode(private var visibility: CueVisibility) : Modifier.Node(), SemanticsModifierNode {
    override val shouldClearDescendantSemantics: Boolean get() = !visibility.placed

    override fun onAttach() {
        visibility.attach(this)
    }

    override fun onDetach() {
        visibility.attach(null)
    }

    fun bind(next: CueVisibility) {
        if (visibility !== next) {
            visibility.attach(null)
            visibility = next
            if (isAttached) visibility.attach(this)
            refresh()
        }
    }

    fun refresh() {
        if (isAttached) invalidateSemantics()
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        // Visibility-only semantics gate: clears unplaced cue probes without adding properties.
    }
}

@Composable
private fun WholeFilterChips(
    modifier: Modifier,
    config: WholeChipsConfig,
    lineBox: @Composable (Modifier) -> Unit,
) {
    val firstSpoken = stringResource(Res.string.kitChipsFirst)
    val moreSpoken = stringResource(Res.string.kitChipsMore)
    val visibilities = remember { mutableMapOf<WholeSlot, CueVisibility>() }

    // Chips past either edge are never cut: measure the chips and real "+N" cues in the same pass before placing.
    SubcomposeLayout(modifier) { constraints ->
        // Scroll offset decides which chips lie before/after the viewport while resting whole; once expanded into a
        // scrolling row, cues stay off and only drawWithContent reads scroll.value for the edge fades.
        val scrollPx = if (config.line.expanded) 0f else config.scroll.value.toFloat()
        val gapPx = Space.S2.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val measured = mutableMapOf<WholeSlot, Placeable>()
        val measureBefore: (Int) -> Placeable = { count ->
            val slot = WholeSlot.Before(count)
            val vis = visibilities.getOrPut(slot) { CueVisibility() }
            measured.getOrPut(slot) {
                measureWholeCue(CueSpec(slot, count, firstSpoken, vis, config.onFirst), loose)
            }
        }
        val measureAfter: (Int) -> Placeable = { count ->
            val slot = WholeSlot.After(count)
            val vis = visibilities.getOrPut(slot) { CueVisibility() }
            measured.getOrPut(slot) {
                measureWholeCue(CueSpec(slot, count, moreSpoken, vis) { config.onMore(count) }, loose)
            }
        }
        val cues = CueMeasurers(beforeWidth = { measureBefore(it).width }, afterWidth = { measureAfter(it).width })
        config.line.onMeasureWhole = buildWholeHook(config, constraints, gapPx, scrollPx, cues)
        val linePlaceable =
            try {
                subcompose(WholeSlot.Line) { lineBox(Modifier) }.single().measure(loose)
            } finally {
                config.line.onMeasureWhole = null
            }
        val plan = config.line.lastPlan
        val activeBefore = if (plan.whole && plan.before > 0) WholeSlot.Before(plan.before) else null
        val activeAfter = if (plan.whole && plan.after > 0) WholeSlot.After(plan.after) else null
        val beforeCue = activeBefore?.let { measureBefore(plan.before) }
        val afterCue = activeAfter?.let { measureAfter(plan.after) }
        for (slot in measured.keys) {
            visibilities[slot]?.update(slot == activeBefore || slot == activeAfter)
        }
        placeWholeRow(constraints, gapPx, beforeCue, linePlaceable, afterCue)
    }
}

private fun SubcomposeMeasureScope.buildWholeHook(
    config: WholeChipsConfig,
    constraints: Constraints,
    gapPx: Int,
    scrollPx: Float,
    cues: CueMeasurers,
): (Int, Boolean) -> Int {
    val totalOuterW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
    val panePadPx = config.insets.paneStart.roundToPx() + config.insets.paneEnd.roundToPx()
    val shadowExtraPx =
        config.insets.growStart.roundToPx().coerceAtLeast(0) + config.insets.growEnd.roundToPx().coerceAtLeast(0)
    val capPx = CapRole.Reading.capDp?.dp?.roundToPx() ?: Int.MAX_VALUE
    val startPadPx = config.insets.startPad.toPx()
    val minCuePx = config.minCueWidthDp.roundToPx()
    return { innerPlaceableW, hasBound ->
        val plan =
            if (config.line.expanded) {
                WholePlan(
                    whole = false,
                    before = 0,
                    after = 0,
                    availInner = innerPlaceableW,
                    clippedWidth = innerPlaceableW,
                )
            } else if (!hasBound) {
                WholePlan(
                    whole = true,
                    before = 0,
                    after = 0,
                    availInner = innerPlaceableW,
                    clippedWidth = innerPlaceableW,
                )
            } else {
                val budget: (Int) -> Int = { outerW ->
                    val innerMax = (minOf(outerW, capPx) - panePadPx).coerceAtLeast(0) + shadowExtraPx
                    minOf(innerPlaceableW, innerMax)
                }
                val metrics = WholeMetrics(config.line.sizes, config.line.gapPx, startPadPx, scrollPx)
                val wholeBudget = WholeBudget(totalOuterW, gapPx, minCuePx, budget, cues.beforeWidth, cues.afterWidth)
                FilterChipsRules.planWhole(metrics, wholeBudget)
            }
        config.line.lastPlan = plan
        if (config.line.wholeActive != plan.whole) config.line.wholeActive = plan.whole
        plan.availInner
    }
}

private fun SubcomposeMeasureScope.measureWholeCue(
    spec: CueSpec,
    loose: Constraints,
): Placeable =
    subcompose(spec.slot) {
        KitChip(
            label = "+${spec.count}",
            modifier = CueSemanticsElement(spec.visibility),
            kind = KitChipKind.Action,
            spoken = spec.spoken,
            onClick = spec.onClick,
        )
    }.single().measure(loose)

private fun SubcomposeMeasureScope.placeWholeRow(
    constraints: Constraints,
    gapPx: Int,
    before: Placeable?,
    line: Placeable,
    after: Placeable?,
): MeasureResult {
    val height = maxOf(constraints.minHeight, line.height, before?.height ?: 0, after?.height ?: 0)
    val contentW =
        (before?.let { it.width + gapPx } ?: 0) +
            line.width +
            (after?.let { gapPx + it.width } ?: 0)
    val width = contentW.coerceIn(constraints.minWidth, constraints.maxWidth)
    return layout(width, height) {
        var x = 0
        before?.let {
            it.placeRelative(x, (height - it.height) / 2)
            x += it.width + gapPx
        }
        line.placeRelative(x, (height - line.height) / 2)
        x += line.width
        after?.let {
            x += gapPx
            it.placeRelative(x, (height - it.height) / 2)
        }
    }
}

/** A fade from [from] to [to]: [clear] px nearest the edge fully clear (all of a shorter piece), then whole. */
private fun edgeMask(from: Float, to: Float, clear: Float, clearAtStart: Boolean): Brush {
    val span = (to - from).coerceAtLeast(1f)
    val c = (clear / span).coerceIn(0f, 1f)
    val clearColor = Color.Transparent
    return if (clearAtStart)
        Brush.horizontalGradient(0f to clearColor, c to clearColor, 1f to Color.Black, startX = from, endX = to)
    else
        Brush.horizontalGradient(0f to Color.Black, 1f - c to clearColor, 1f to clearColor, startX = from, endX = to)
}
