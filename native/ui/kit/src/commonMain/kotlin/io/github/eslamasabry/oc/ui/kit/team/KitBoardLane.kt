package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitAnimatedRows
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitRefresh
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabStrip
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitBoardLane
import io.github.eslamasabry.oc.ui.words.res.kitBoardLaneLoading
import io.github.eslamasabry.oc.ui.words.res.kitBoardLane__eq0
import io.github.eslamasabry.oc.ui.words.res.kitBoardLane__eq1
import kotlin.math.floor
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One column's tab in the strip (Dart `KitBoardColumn`). [count] is null while the first answer comes: no number. */
@Immutable
public data class KitBoardColumn(
    /** The host's word ("Working"), shown as written. */
    val label: String,
    /** Null while the first answer comes; once known it equals the lane's card count. */
    val count: Int? = null,
    /** Cards in it that need the person: the needs-you badge beside the count. */
    val needsYou: Int = 0,
)

/** One card of a lane: its own stable [key] (the arrival animation follows it) and the card itself. */
@Immutable
public class KitBoardCard(public val key: Any, public val content: @Composable () -> Unit)

/** The pure layout of [KitBoardLanes] (Dart `_KitBoardLanesState`), for the part and its parity test. */
public object KitBoardLaneLayout {
    /** The narrowest phone window: its lane runs edge to edge, no peek. */
    public const val NARROWEST_WINDOW: Float = 320f // F: lib/ui/kit/kit_board_lane.dart:110

    /** How much of each neighbour peeks on a phone (Dart `KitLayout.lanePeek`). */
    public const val LANE_PEEK: Float = 20f // F: lib/ui/kit/kit_layout.dart:124

    /** The narrowest lane side by side (Dart `KitLayout.laneMinWidth`). */
    public const val LANE_MIN_WIDTH: Float = 296f // F: lib/ui/kit/kit_layout.dart:125

    /** Skeleton cards in a loading lane (Dart `KitBoardLane.skeletonCards`). */
    public const val SKELETON_CARDS: Int = 4 // F: lib/ui/kit/kit_board_lane.dart:609

    /** The fewest and most columns a board shows. */
    public const val MIN_COLUMNS: Int = 2 // F: lib/ui/kit/kit_board_lane.dart:77
    public const val MAX_COLUMNS: Int = 8 // F: lib/ui/kit/kit_board_lane.dart:77

    /** A lane's width when one shows at a time in a view [width] wide (Dart `_pagedLaneWidth`). */
    public fun pagedLaneWidth(width: Float): Float {
        val peek = ((width - NARROWEST_WINDOW) / 2).coerceIn(0f, LANE_PEEK) // F: lib/ui/kit/kit_board_lane.dart:365
        val lane = width - 2 * peek // F: lib/ui/kit/kit_board_lane.dart:369
        return if (lane < Layout.LANE_MAX_WIDTH) lane else Layout.LANE_MAX_WIDTH.toFloat()
    }

    /** How many lanes fit side by side in [room] with [gap] between them (Dart `_sideBySide`). */
    public fun fit(room: Float, gap: Float, columns: Int): Int =
        floor((room + gap) / (LANE_MIN_WIDTH + gap)).toInt().coerceIn(1, columns)

    /** Each side-by-side lane's width: whole logical pixels. */
    public fun sideBySideLane(room: Float, gap: Float, fit: Int): Float = floor((room - (fit - 1) * gap) / fit)
}

/**
 * The board (Dart `KitBoardLanes`, lib/ui/kit/kit_board_lane.dart): the column strip and the lanes, kept in step.
 * [selected] drives both: the strip shows it selected, and the lanes page to it (compact, medium, short) or scroll it
 * into view (expanded, large). A swipe or scroll that settles on another lane, a strip tap and Ctrl+→ / Ctrl+← each
 * call [onSelected]; the host recomposes with the new [selected]. The part never picks the opening column.
 *
 * Compact, medium and short windows show one lane at a time, at most `Layout.LANE_MAX_WIDTH` wide, with the neighbours
 * peeking (20 dp on a phone, nothing at 320 dp). Wide windows show as many lanes side by side as fit at 296 dp or more,
 * in a horizontal list. Stale is not dimming: last-known cards stay at full strength.
 *
 * States: loading, loaded, empty (per lane)
 * Text: frame (the strip)
 * Cap: Fill
 * Motion: paging and the reveal scroll on STANDARD; Off jumps
 * Hidden: swipe between lanes (visible twin: the strip); Ctrl+→ / Ctrl+←
 * Replaces: KitBoardLanes (lib/ui/kit/kit_board_lane.dart)
 */
@Composable
public fun KitBoardLanes(
    columns: List<KitBoardColumn>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    lane: @Composable (index: Int) -> Unit,
) {
    kitAssert(columns.size in KitBoardLaneLayout.MIN_COLUMNS..KitBoardLaneLayout.MAX_COLUMNS) { "KitBoardLanes shows ${KitBoardLaneLayout.MIN_COLUMNS} to ${KitBoardLaneLayout.MAX_COLUMNS} columns." }
    val tokens = LocalKitTokens.current
    val window = LocalKitWindow.current
    // A phone in landscape keeps the paged board (the short row of §8.2).
    val wide = WindowClass.modal(window.widthDp, window.heightDp).isWide
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val current by rememberUpdatedState(selected)
    val report by rememberUpdatedState(onSelected)
    // A strip tap or a keyboard move: the host hears of it and recomposes with [index] selected; the lanes follow.
    val select: (Int) -> Unit = { index -> if (index != current) report(index) }
    Column(
        modifier.onPreviewKeyEvent { e ->
            if (loading || e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@onPreviewKeyEvent false
            val forward = if (rtl) -1 else 1
            val step =
                when (e.key) {
                    Key.DirectionRight -> forward
                    Key.DirectionLeft -> -forward
                    else -> return@onPreviewKeyEvent false
                }
            val target = current + step
            if (target in columns.indices) select(target)
            true
        },
    ) {
        KitTabStrip(
            tabs = columns.map { c -> KitTab(c.label, count = if (loading) null else c.count, needsYou = if (loading) 0 else c.needsYou) },
            selected = selected,
            onSelected = select,
        )
        Spacer(Modifier.height(tokens.labelGap.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val width = maxWidth.value
            when {
                loading -> LoadingLane(columns[selected], width, wide)
                wide -> SideBySide(columns, selected, width, select, lane)
                else -> Paged(columns, selected, width, select, lane)
            }
        }
    }
}

/** Lane [index] as the host built it, labelled for screen readers ("Working, 3 tasks"). */
@Composable
private fun LabelledLane(column: KitBoardColumn, index: Int, modifier: Modifier, lane: @Composable (Int) -> Unit) {
    val count = column.count
    val label =
        when (count) {
            null -> column.label
            0 -> stringResource(Res.string.kitBoardLane__eq0, column.label)
            1 -> stringResource(Res.string.kitBoardLane__eq1, column.label)
            else -> pluralStringResource(Res.plurals.kitBoardLane, count, column.label, count)
        }
    Box(modifier.semantics { contentDescription = label }) { lane(index) }
}

/** One lane at a time, the neighbours peeking; a settled swipe selects (Dart `_paged`). */
@Composable
private fun Paged(columns: List<KitBoardColumn>, selected: Int, width: Float, select: (Int) -> Unit, lane: @Composable (Int) -> Unit) {
    val laneWidth = KitBoardLaneLayout.pagedLaneWidth(width)
    val laneHalfGap = Space.S1.dp / 2 // F: lib/ui/kit/kit_board_lane.dart:389
    val pages = rememberPagerState(initialPage = selected) { columns.size }
    val specs = KitMotion.specs
    val reduced = KitMotion.state.reduced
    var driving by remember { mutableIntStateOf(0) }
    LaunchedEffect(selected) {
        if (pages.currentPage == selected && !pages.isScrollInProgress) return@LaunchedEffect
        driving++
        try {
            if (reduced) pages.scrollToPage(selected) else pages.animateScrollToPage(selected, animationSpec = specs.enter())
        } finally {
            driving--
        }
    }
    val chosen by rememberUpdatedState(select)
    LaunchedEffect(pages) {
        snapshotFlow { pages.settledPage }.collect { settled -> if (driving == 0) chosen(settled.coerceIn(0, columns.size - 1)) }
    }
    val side = ((width - laneWidth) / 2).coerceAtLeast(0f) // F: lib/ui/kit/kit_board_lane.dart:365
    HorizontalPager(
        state = pages,
        pageSize = PageSize.Fixed(laneWidth.dp),
        contentPadding = PaddingValues(horizontal = side.dp),
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { index ->
        // Half of space1 on each side: space1 between two paged lanes.
        LabelledLane(
            columns[index],
            index,
            Modifier.fillMaxSize().padding(start = laneHalfGap, end = laneHalfGap, bottom = Space.S2.dp),
            lane,
        )
    }
}

/** As many lanes side by side as fit, in a horizontal list (Dart `_sideBySide`). */
@Composable
private fun SideBySide(columns: List<KitBoardColumn>, selected: Int, width: Float, select: (Int) -> Unit, lane: @Composable (Int) -> Unit) {
    val tokens = LocalKitTokens.current
    val gap = Space.S3.toFloat()
    val room = width - 2 * tokens.gutter // F: lib/ui/kit/kit_board_lane.dart:429
    val fit = KitBoardLaneLayout.fit(room, gap, columns.size)
    val laneWidth = KitBoardLaneLayout.sideBySideLane(room, gap, fit)
    val step = laneWidth + gap
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val specs = KitMotion.specs
    val reduced = KitMotion.state.reduced
    var driving by remember { mutableIntStateOf(0) }
    fun fullyShown(index: Int): Boolean {
        val px = with(density) { 1.dp.toPx() }
        val pixels = scroll.value / px
        val viewport = scroll.viewportSize / px
        // Lane [index] starts at gutter + index * step in the list's content.
        val start = tokens.gutter + index * step
        return start >= pixels - .5f && start + laneWidth <= pixels + viewport + .5f // F: lib/ui/kit/kit_board_lane.dart:250-251
    }
    LaunchedEffect(selected, step) {
        if (fullyShown(selected)) return@LaunchedEffect
        val px = with(density) { 1.dp.toPx() }
        val target = (selected * step * px).roundToInt().coerceIn(0, scroll.maxValue)
        driving++
        try {
            if (reduced) scroll.scrollTo(target) else scroll.animateScrollBy((target - scroll.value).toFloat(), specs.enter())
        } finally {
            driving--
        }
    }
    val chosen by rememberUpdatedState(select)
    val shownSelected by rememberUpdatedState(selected)
    LaunchedEffect(scroll, step) {
        snapshotFlow { scroll.isScrollInProgress }.collect { moving ->
            if (moving || driving > 0 || fullyShown(shownSelected)) return@collect
            val px = with(density) { 1.dp.toPx() }
            chosen((scroll.value / px / step).roundToInt().coerceIn(0, columns.size - 1))
        }
    }
    Row(
        Modifier.fillMaxSize()
            .horizontalScroll(scroll)
            .padding(start = tokens.gutter.dp, end = tokens.gutter.dp, bottom = Space.S2.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        columns.forEachIndexed { i, column -> LabelledLane(column, i, Modifier.width(laneWidth.dp).fillMaxHeight(), lane) }
    }
}

/** The first answer is on its way: one skeleton lane where the selected column will be, nothing to swipe. */
@Composable
private fun LoadingLane(column: KitBoardColumn, width: Float, wide: Boolean) {
    val tokens = LocalKitTokens.current
    val laneWidth =
        if (wide) minOf(width - 2 * tokens.gutter, Layout.LANE_MAX_WIDTH.toFloat()) // F: lib/ui/kit/kit_board_lane.dart:490
        else KitBoardLaneLayout.pagedLaneWidth(width) - Space.S1
    val label = stringResource(Res.string.kitBoardLaneLoading, column.label)
    Box(
        Modifier.fillMaxSize().padding(start = if (wide) tokens.gutter.dp else 0.dp, bottom = Space.S2.dp),
        contentAlignment = if (wide) Alignment.TopStart else Alignment.TopCenter,
    ) {
        Box(Modifier.width(laneWidth.dp).fillMaxHeight().semantics { contentDescription = label }) { KitBoardLaneLoading() }
    }
}

/** The lane bed: a track in the style, `ground` with a one-physical-pixel hairline under Solid, the panel radius. */
@Composable
private fun LaneBed(modifier: Modifier, content: @Composable () -> Unit) {
    val colors = LocalKitColors.current
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Track, KitShapeId.Panel, SurfaceLevel.Surface1, ownFill = colors.ground))
    val edge = if (LocalKitStyle.current.style == Style.Solid) Modifier.teamEdge(kitHairline(), colors.hairline, look.shape) else Modifier
    Box(modifier.kitMaxWidth(CapRole.Lane).fillMaxSize().then(look.modifier).then(edge).clip(look.shape)) { content() }
}

/**
 * One column's cards (Dart `KitBoardLane`): a vertical list on the lane surface (`ground` with a one-physical-pixel
 * hairline, so a peeking neighbour reads as another column), the cards space2 apart, arriving and leaving with
 * [KitAnimatedRows]. With no cards it shows its inline [empty] state, never a blank lane. Pull to refresh with
 * [onRefresh].
 *
 * States: loading ([KitBoardLaneLoading]), loaded, empty
 * Text: none (the cards decide)
 * Cap: Lane (400)
 * Motion: cards unfold in and fold out (KitAnimatedRows); Off = instant
 * Hidden: pull to refresh (visible twin: the screen's Refresh action, KitRefresh's own semantics action)
 * Replaces: KitBoardLane (lib/ui/kit/kit_board_lane.dart)
 */
@Composable
public fun KitBoardLane(
    cards: List<KitBoardCard>,
    modifier: Modifier = Modifier,
    empty: (@Composable () -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
) {
    kitAssert(cards.isNotEmpty() || empty != null) { "KitBoardLane: a lane with no cards needs its empty state." }
    LaneBed(modifier) {
        val list: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Space.S2.dp, top = Space.S2.dp, end = Space.S2.dp),
            ) {
                if (cards.isEmpty()) {
                    empty!!()
                } else {
                    KitAnimatedRows(cards, key = { it.key }) { card ->
                        Box(Modifier.padding(bottom = Space.S2.dp)) { card.content() }
                    }
                }
            }
        }
        if (onRefresh == null) list() else KitRefresh(refreshing = refreshing, onRefresh = onRefresh) { list() }
    }
}

/** The skeleton lane while the first answer comes (Dart `KitBoardLane.loading`): four content cards in the style. */
@Composable
public fun KitBoardLaneLoading(modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    LaneBed(modifier) {
        Column(Modifier.fillMaxSize().padding(Space.S2.dp)) {
            repeat(KitBoardLaneLayout.SKELETON_CARDS) {
                // A skeleton card: a content card in the style.
                val card = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, SurfaceLevel.Surface1, ownFill = colors.surface1))
                Box(Modifier.padding(bottom = Space.S2.dp)) {
                    Box(Modifier.fillMaxWidth().then(card.modifier).clip(card.shape).padding(vertical = Space.S1.dp)) {
                        KitSkeletonRows(count = 1)
                    }
                }
            }
        }
    }
}

/**
 * A border drawn inside the box at its exact width, like Flutter's `Border.all` (a one-physical-pixel hairline is a
 * third of a picture pixel in the DPR-3 gallery). `Modifier.border` rounds its width up to a whole pixel, which draws
 * the hairline three times as strong.
 */
internal fun Modifier.teamEdge(width: Dp, color: Color, shape: Shape, over: Boolean = false): Modifier =
    if (over) drawWithContent { drawContent(); drawEdge(width, color, shape) } else drawBehind { drawEdge(width, color, shape) }

private fun DrawScope.drawEdge(width: Dp, color: Color, shape: Shape) {
    val w = width.toPx()
    if (w <= 0f || size.width <= w || size.height <= w) return
    val inner = Size(size.width - w, size.height - w)
    translate(w / 2, w / 2) { drawOutline(shape.createOutline(inner, layoutDirection, this), color, style = Stroke(w)) } // NV: half the stroke, so it lies inside the edge as Flutter's Border does
}
