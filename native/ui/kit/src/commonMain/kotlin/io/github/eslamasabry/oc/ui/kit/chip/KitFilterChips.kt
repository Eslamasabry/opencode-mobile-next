package io.github.eslamasabry.oc.ui.kit.chip

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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowReach
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowRoom
import io.github.eslamasabry.oc.ui.kit.frame.FloatingPaneRow
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitOnFloatingPane
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.roundToInt

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
     * The gap between chips at rest, in px. [sizes] are the chips' widths in reading order, [gap] the line's own gap
     * and [room] how much of the line shows from its first chip's start to its end edge. Where the end edge cuts a
     * chip and less than [minPeek] of it would show, the gaps before it grow just enough to carry it wholly past the
     * edge, so the last chip shown is whole; a chip cut with [minPeek] or more showing, an edge falling between chips
     * and a line that fits keep [gap]. The first chip is never pushed.
     */
    public fun restGap(sizes: IntArray, gap: Float, room: Float, minPeek: Float): Float {
        // The first chip the end edge cuts, and how much of it shows; none when the edge falls between chips.
        var x = 0f
        var i = 0
        while (i < sizes.size && x + sizes[i] <= room) {
            x += sizes[i] + gap
            i++
        }
        val cut = if (i < sizes.size && x < room) i else -1
        val shown = room - x
        return if (room <= 0f || cut <= 0 || shown >= minPeek) gap else gap + (shown + 1f) / cut
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
}

/** Where a chip line keeps its chips' widths and its resting gap, between laying the chips out and drawing the fade. */
private class ChipLine(private val baseGap: Dp, private val minPeek: Dp) : Arrangement.Horizontal {
    var gap by mutableStateOf(baseGap)
    /** The window's room from the first chip's start to the end edge, set while the line measures. */
    var roomPx = 0
    var sizes = IntArray(0)
    var gapPx = 0f

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
    val line = remember { ChipLine(Space.S2.dp, FilterChipsRules.MIN_PEEK_DP.dp) }
    Box(
        modifier.kitMaxWidth(CapRole.Reading)
            .then(if (onPane) Modifier.padding(start = if (bleed) 0.dp else paneEdge, end = paneEdge) else Modifier)
            .kitShadowRoom(room, start = growStart, end = growEnd)
            .layout { measurable, constraints ->
                line.roomPx = if (constraints.hasBoundedWidth) constraints.maxWidth - startPad.roundToPx() else 0
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (scroll.maxValue <= 0 || scroll.maxValue == Int.MAX_VALUE) return@drawWithContent
                val spans = FilterChipsRules.spans(line.sizes, line.gapPx, startPad.toPx(), scroll.value.toFloat())
                val fade = FilterChipsRules.CHIP_FADE_DP.dp.toPx()
                val (startFade, endFade) = FilterChipsRules.edgeFades(spans, size.width, fade)
                val rtl = layoutDirection == LayoutDirection.Rtl
                val left = if (rtl) endFade else startFade
                val right = if (rtl) startFade else endFade
                // The third of a full fade nearest the edge is always clear, even over a short piece.
                val clear = fade * FilterChipsRules.CLEAR_PART
                if (left > 0f)
                    drawRect(
                        edgeMask(0f, left, clear, clearAtStart = true),
                        Offset.Zero,
                        Size(left, size.height),
                        blendMode = BlendMode.DstIn,
                    )
                if (right > 0f)
                    drawRect(
                        edgeMask(size.width - right, size.width, clear, clearAtStart = false),
                        Offset(size.width - right, 0f),
                        Size(right, size.height),
                        blendMode = BlendMode.DstIn,
                    )
            }
            .testTag("kit-filter-chips"),
    ) {
        Row(
            Modifier.horizontalScroll(scroll).padding(start = startPad, end = endPad, top = room, bottom = room),
            horizontalArrangement = line,
            verticalAlignment = Alignment.CenterVertically,
            content = chips,
        )
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
