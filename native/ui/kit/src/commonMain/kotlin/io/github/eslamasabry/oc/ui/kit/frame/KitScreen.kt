package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import io.github.eslamasabry.oc.ui.kit.motion.KitRefresh
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrost
import io.github.eslamasabry.oc.ui.kit.foundation.ChromeVeil
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitChromeVeil
import io.github.eslamasabry.oc.ui.kit.foundation.kitBackdropVeil
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitChromeVeil
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBarPane
import io.github.eslamasabry.oc.ui.kit.effects.LiquidBarShape
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidChrome
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidEdgeFade
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidGlassOn
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidSource
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidWordsHost
import io.github.eslamasabry.oc.ui.kit.effects.kitRunUnderBar
import io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidSource
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.zIndex
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitSheetHost
import io.github.eslamasabry.oc.ui.kit.state.KitLoadingBar
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusContribution
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLineFold
import io.github.eslamasabry.oc.ui.kit.status.KitStatusSlot
import io.github.eslamasabry.oc.ui.kit.status.kitStatusSlotAbove
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow

/** The most of the room under the bar the status line may take (Flutter `KitStatusLineSlot`: 0.4). */
internal const val STATUS_ROOM_SHARE: Float = 0.4f

/** The status line's height cap in px for [room] px under the bar (P-106); unbounded room leaves it uncapped. */
internal fun statusRoomCap(room: Int): Int =
    if (room == Constraints.Infinity) Constraints.Infinity else (room * STATUS_ROOM_SHARE).toInt()

/** Holds the status line to [STATUS_ROOM_SHARE] of the room it is offered (P-106). */
private fun Modifier.kitStatusRoom(): Modifier =
    layout { measurable, constraints ->
        val cap = statusRoomCap(constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = maxOf(cap, 0)))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/** Optional host inset source; null uses the platform's safe drawing area and keyboard. */
internal val LocalKitScreenInsets = compositionLocalOf<WindowInsets?> { null }

private val LocalChromePadding = compositionLocalOf { PaddingValues() }

/** Space kept inside a scrolling body for the overlaid chat header and composer. */
@Composable public fun kitScreenChromePadding(): PaddingValues = LocalChromePadding.current

/** How wide a single-pane body may grow on medium and wider windows (Dart `KitScreenWidth`). */
public enum class KitScreenWidth(internal val cap: CapRole) {
    /** The whole width (the default). */
    Full(CapRole.Fill),

    /** Forms, settings, prose: centred at 720. */
    Reading(CapRole.Reading),

    /** A list on its own: centred at 960. */
    List(CapRole.List),
}

/**
 * Where a screen's body sits across the window (audit C02, 2026-11-09). The frame owns the page gutter: a body that
 * forgot its own padding drew buttons, labels and fields against the window edge on 14 screens.
 */
public enum class KitScreenEdges {
    /**
     * The default: the body sits between the page gutters (16 dp each side) and is on the rail, so kit rows, groups and
     * section labels line up with it instead of stepping in a second gutter (as on a sheet).
     */
    Gutter,

    /**
     * The body runs to the window edges and keeps its own gutters: lists of kit rows whose touch area and selected fill
     * span the window, a transcript, edge-to-edge output. Opt in only when the body really draws to the edge.
     */
    Bleed,
}

/** True inside a body that already sits between the page gutters: a nested [KitScreen] adds none of its own. */
internal val LocalKitScreenGutter = compositionLocalOf { false }

/**
 * The page gutter a kit part keeps from its box's edge on a page: the tokens' gutter, or 0 inside a
 * [KitScreenEdges.Gutter] body, where the frame already keeps it. A part that pads itself by the page gutter reads
 * this, so it never steps in twice.
 */
@Composable
public fun kitPageGutter(): androidx.compose.ui.unit.Dp =
    if (LocalKitScreenGutter.current) 0.dp else LocalKitTokens.current.gutter.dp

/**
 * How far a band (a key bar's ground, a full-width surface) reaches past its box to meet the window edges: the page
 * gutter inside a [KitScreenEdges.Gutter] body, else 0. Pair [kitPageBleed] with this much padding inside, so the
 * band's content stays on the gutter while its ground runs edge to edge.
 */
@Composable
public fun kitPageBleedWidth(): androidx.compose.ui.unit.Dp =
    if (LocalKitScreenGutter.current) LocalKitTokens.current.gutter.dp else 0.dp

/** Widens the box by [kitPageBleedWidth] on both sides without taking more room: its ground meets the window edges. */
@Composable
public fun Modifier.kitPageBleed(): Modifier {
    val bleed = kitPageBleedWidth()
    if (bleed <= 0.dp) return this
    return layout { measurable, constraints ->
        val b = bleed.roundToPx()
        val wide =
            if (constraints.hasBoundedWidth)
                constraints.copy(minWidth = constraints.minWidth + 2 * b, maxWidth = constraints.maxWidth + 2 * b)
            else constraints
        val placeable = measurable.measure(wide)
        val width = (placeable.width - 2 * b).coerceAtLeast(0)
        layout(width, placeable.height) { placeable.placeRelative(-b, 0) }
    }
}

/**
 * Lays [content] between the page gutters for [KitScreenEdges.Gutter], once: a body already between gutters or on a
 * sheet's rail adds none. [KitScreenEdges.Bleed] leaves it alone.
 */
@Composable
internal fun KitScreenBody(edges: KitScreenEdges, content: @Composable () -> Unit) {
    val placed = LocalKitScreenGutter.current || io.github.eslamasabry.oc.ui.kit.row.LocalKitOnRail.current
    if (edges == KitScreenEdges.Bleed || placed) {
        content()
        return
    }
    Box(
        Modifier.fillMaxSize()
            .padding(horizontal = LocalKitTokens.current.gutter.dp)
            .testTag("kit-screen-body"),
    ) {
        CompositionLocalProvider(
            LocalKitScreenGutter provides true,
            io.github.eslamasabry.oc.ui.kit.row.LocalKitOnRail provides true,
        ) {
            content()
        }
    }
}

/** A screen's pull to refresh, set on [KitScreen] so it wraps the scrolling header as well as the body. */
@OptIn(ExperimentalMaterial3Api::class)
public class KitScreenRefresh(
    public val refreshing: Boolean,
    public val onRefresh: () -> Unit,
    public val enabled: Boolean = true,
    public val state: PullToRefreshState? = null,
)

/**
 * The one screen frame (Dart `KitScreen` v2). With [topBar] (or [page]) it is a page: the ground,
 * the bar in the top safe area, and the bottom block lifted above the keyboard; without, it is a
 * body inside a host and builds no frame. Top to bottom: bar → the one status line → [search] →
 * [header] → the one loading line → [body] → [bottom]. The [header] scrolls with the body (P-006):
 * it slides up under the bar as the body scrolls and comes back at the body's top, the cut fading
 * over 12 dp. There is no other slot (R9: no new bar levels). A screen with no status slot above
 * it owns the window's slot; one under a slot (a tab body, a pane) contributes its [status] instead
 * of drawing a second line. It hosts the screen's sheets.
 *
 * The body sits between the page gutters unless the screen passes [KitScreenEdges.Bleed] (C02).
 *
 * States: default, loading, status, search, keyboard Text: none (its slots bring their own) Cap:
 * Fill (the body is capped by [width] from medium up) Motion: none of its own (the status line
 * folds in through the slot) Hidden: none Replaces: KitScreen (lib/ui/kit/kit_screen.dart)
 */
@Composable
public fun KitScreen(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    status: KitStatus? = null,
    search: (@Composable () -> Unit)? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    loading: Boolean = false,
    loadingLabel: String? = null,
    bottom: (@Composable () -> Unit)? = null,
    width: KitScreenWidth = KitScreenWidth.Full,
    page: Boolean = false,
    overlayChrome: Boolean = false,
    hideStatusWhenTyping: Boolean = false,
    /**
     * N-52/N-57: what [body] records for the overlaid bar and bottom block ([kitFrostSource]). The two always lie
     * on the page's ground, edge to edge, so the body never draws through them; under Glass they frost what scrolls
     * behind instead.
     */
    frost: KitFrostState? = null,
    /** Pull to refresh for the whole scrolling part (header and body); see [KitScreenRefresh]. */
    refresh: KitScreenRefresh? = null,
    /** Where the body sits across the window: between the page gutters (the default) or edge to edge. */
    edges: KitScreenEdges = KitScreenEdges.Gutter,
    body: @Composable () -> Unit,
) {
    val framed = topBar != null || page
    val window = LocalKitWindow.current
    val cap = if (window.cls == WindowClass.Compact) CapRole.Fill else width.cap
    // Overlaid chrome swaps between the overlay Box and the plain Column (a status line, Find,
    // loading): the bar, body and bottom move between them instead of being rebuilt, so the
    // composer keeps its field, focus and keyboard and the body keeps its scroll position.
    val latestTop = rememberUpdatedState(topBar)
    val latestBottom = rememberUpdatedState(bottom)
    val latestBody = rememberUpdatedState(body)
    val movableTop = remember { movableContentOf { latestTop.value?.invoke() } }
    val movableBottom = remember { movableContentOf { latestBottom.value?.invoke() } }
    val movableBody = remember { movableContentOf { latestBody.value() } }
    // Both layouts must invoke the same movable content. Switching from a raw slot to a
    // movable slot disposes it: the first optimistic chat bubble used to cancel its own send.
    val topPart: (@Composable () -> Unit)? = topBar?.let { movableTop }
    val bottomPart: (@Composable () -> Unit)? = bottom?.let { movableBottom }
    val bodyPart: @Composable () -> Unit = movableBody
    val inner: @Composable (line: KitStatus?, ownsLine: Boolean) -> Unit = { line, ownsLine ->
        val insets =
            LocalKitScreenInsets.current ?: WindowInsets.safeDrawing.union(WindowInsets.ime)
        if (overlayChrome && line == null && search == null && header == null && !loading) {
            var topPx by remember { mutableIntStateOf(0) }
            var bottomPx by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            // B-102: the body's room includes the chrome's ground fade, so at rest the last line sits above the
            // composer's 16 dp fade (and the first under the bar's 12 dp one) instead of being cut by it.
            // E20-S30: a veiled bottom block (every style but Glass, when the body records itself) lets the body scroll
            // under it as Glass does, so it adds no fade room either: the newest line rests on the list's own padding.
            val veil = if (frost != null && !kitChromeIsGlass()) rememberKitChromeVeil() else null
            val faded = !kitChromeIsGlass() && veil == null
            // Liquid glass (Glass only, where FrostPolicy does not hold it): the bar and the composer float as glass
            // over what scrolls behind them; a null face keeps today's ground, veil or frost.
            val liquid = frost != null && kitLiquidGlassOn()
            val barFace = if (topPart != null) kitLiquidFace(LiquidRole.Bar, frost, LiquidBarShape) else null
            val padding =
                with(density) {
                    PaddingValues(
                        // E20-S13: the header always lies on its opaque ground with its fade, Glass included.
                        top = topPx.toDp() + if (topPart != null) HEADER_FADE_DP.dp else 0.dp,
                        // Liquid: the newest line rests above the scroll edge fade, never inside it.
                        bottom = bottomPx.toDp() + if ((faded || liquid) && bottomPart != null) COMPOSER_FADE_DP.dp else 0.dp,
                    )
                }
            Box(
                Modifier.fillMaxSize()
                    .then(if (framed) Modifier.windowInsetsPadding(insets) else Modifier)
                    .testTag("kit-screen")
            ) {
                CompositionLocalProvider(LocalChromePadding provides padding) {
                    Box(Modifier.fillMaxSize().kitMaxWidth(cap)) { KitScreenBody(edges) { bodyPart() } }
                }
                topPart?.let {
                    Box(
                        Modifier.align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .onSizeChanged { topPx = it.height }
                            .then(if (barFace != null) Modifier.kitLiquidEdgeFade(top = true).then(kitLiquidWordsHost(LiquidRole.Bar, frost)) else Modifier.kitChromeGround(FrostRole.ChatHeader, frost))
                    ) {
                        if (barFace != null) KitLiquidBarPane(barFace)
                        it()
                    }
                }
                bottomPart?.let {
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { bottomPx = it.height }
                            .then(
                                when {
                                    // Glass: no band behind the chips over the message box (owner 2026-11-08): the
                                    // transcript scrolls clean behind the glass chips and the glass message box.
                                    liquid -> Modifier
                                    veil != null && frost != null -> Modifier.kitChromeVeil(veil, frost)
                                    else -> Modifier.kitChromeGround(FrostRole.Composer, frost)
                                }
                            )
                            .padding(
                                horizontal = LocalKitTokens.current.gutter.dp,
                                vertical = Space.S2.dp,
                            )
                            .testTag("kit-screen-bottom")
                    ) {
                        CompositionLocalProvider(LocalKitChromeVeil provides veil, LocalKitLiquidChrome provides frost) { it() }
                    }
                }
            }
        } else {
            // Liquid glass. A chat (it records its transcript) keeps its liquid bar and composer here too, so a status
            // line, the loading line or Find never flips them to frost. A page with a scrolling header runs its body up
            // under the bar (the header keeps its place below it) and the bar floats as glass over what scrolls there;
            // while a status line or Find sits between them the body eases back below the bar, never jumping.
            val under = rememberKitLiquidSource()
            val runs = frost == null && topPart != null && header != null && kitLiquidGlassOn()
            val barFace =
                when {
                    topPart == null -> null
                    frost != null -> kitLiquidFace(LiquidRole.Bar, frost, LiquidBarShape)
                    runs -> kitLiquidFace(LiquidRole.Bar, under, LiquidBarShape)
                    else -> null
                }
            var barPx by remember { mutableIntStateOf(0) }
            val runTarget = if (runs && line == null && search == null) barPx else 0
            val runPx by androidx.compose.animation.core.animateIntAsState(
                runTarget,
                if (KitMotion.state.reduced) androidx.compose.animation.core.snap<Int>() else KitMotion.specs.enter<Int>(),
                label = "kit.screen.under-bar",
            )
            Column(
                Modifier.fillMaxSize()
                    .then(if (framed) Modifier.windowInsetsPadding(insets) else Modifier)
                    .testTag("kit-screen")
            ) {
                if (topPart != null) {
                    Box(
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        ).then(
                            when {
                                runs -> Modifier.zIndex(1f).onSizeChanged { barPx = it.height }
                                    .then(if (barFace != null) Modifier.kitLiquidEdgeFade(top = true).then(kitLiquidWordsHost(LiquidRole.Bar, under)) else Modifier.background(LocalKitColors.current.ground.copy(alpha = 1f)))
                                barFace != null -> kitLiquidWordsHost(LiquidRole.Bar, frost)
                                else -> Modifier
                            }
                        )
                    ) {
                        if (barFace != null) KitLiquidBarPane(barFace)
                        topPart()
                    }
                }
                // P-106: a long condition (Android stopped the app, with its action) stood 200 dp tall and pinned,
                // so a short window kept little room for the list. As Flutter's slot, the line takes at most
                // 40 % of the room under the bar and scrolls its own words beyond that.
                if (ownsLine)
                    KitStatusLineFold(
                        line,
                        Modifier.kitStatusRoom().verticalScroll(rememberScrollState()).testTag("kit-screen-status"),
                    )
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .then(if (runs) Modifier.kitRunUnderBar { runPx }.kitLiquidSource(under) else Modifier)
                        .kitMaxWidth(cap).fillMaxWidth()
                ) {
                    search?.let {
                        // E20-S08: a step under the field, so the page's first part (an empty state's heading)
                        // never sits against it.
                        val gutter = LocalKitTokens.current.gutter.dp
                        Box(Modifier.padding(start = gutter, end = gutter, bottom = Space.S2.dp)) {
                            it()
                        }
                    }
                    // P-006: the header (a page heading, its chips) is the body's first part and scrolls away with it;
                    // the loading line and the body follow it. Without one, the body starts here.
                    // Refresh is the outermost scroll node, around the header frame: list, then header, then refresh.
                    // The header returns before a pull starts, and an upward drag retracts the pull before the header
                    // hides (Flutter's RefreshIndicator wraps the whole CustomScrollView, header sliver included).
                    // Liquid glass: a body that runs up under the bar keeps its header below the bar with a spacer
                    // that eases with the run; refresh still wraps the whole header frame.
                    val scrolling: @Composable (Modifier) -> Unit = { m ->
                        if (header != null) {
                            val runDp = with(LocalDensity.current) { runPx.toDp() }
                            val shownHeader: @Composable ColumnScope.() -> Unit =
                                if (!runs) header else ({ Spacer(Modifier.height(runDp)); header() })
                            KitScrollingHeaderFrame(shownHeader, m) {
                                KitLoadingBar(loading, loadingLabel.orEmpty(), if (loading) Modifier.testTag("kit-screen-loading") else Modifier)
                                Box(Modifier.fillMaxWidth().weight(1f)) { KitScreenBody(edges) { bodyPart() } }
                            }
                        } else {
                            // Dart: KitLoadingBar(loading:, label:) always takes its 2 dp, so the page never jumps.
                            Column(m) {
                                KitLoadingBar(loading, loadingLabel.orEmpty(), if (loading) Modifier.testTag("kit-screen-loading") else Modifier)
                                Box(Modifier.fillMaxWidth().weight(1f)) { KitScreenBody(edges) { bodyPart() } }
                            }
                        }
                    }
                    if (refresh != null) {
                        KitRefresh(
                            refreshing = refresh.refreshing,
                            onRefresh = refresh.onRefresh,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            enabled = refresh.enabled,
                            hostState = refresh.state,
                        ) {
                            scrolling(Modifier.fillMaxSize())
                        }
                    } else {
                        scrolling(Modifier.fillMaxWidth().weight(1f))
                    }
                    bottomPart?.let {
                        Box(
                            Modifier.fillMaxWidth()
                                .windowInsetsPadding(
                                    WindowInsets.ime
                                        .union(WindowInsets.navigationBars)
                                        .only(WindowInsetsSides.Bottom)
                                )
                                .padding(
                                    horizontal = LocalKitTokens.current.gutter.dp,
                                    vertical = Space.S2.dp,
                                )
                                .testTag("kit-screen-bottom"),
                            // Dart's Column(crossAxisAlignment: stretch): the bottom block's child takes the full
                            // width between the gutters (the page's primary is full width).
                            propagateMinConstraints = true,
                        ) {
                            CompositionLocalProvider(LocalKitLiquidChrome provides frost) { it() }
                        }
                    }
                }
            }
        }
    }
    val content: @Composable () -> Unit = {
        if (kitStatusSlotAbove()) {
            KitStatusContribution(status)
            inner(null, false)
        } else {
            KitStatusSlot(status) { shown ->
                // Fold optional information while typing; connection, recovery and failures stay
                // visible.
                val optional =
                    shown?.kind in setOf(KitStatusKind.Update, KitStatusKind.Info) &&
                        shown?.tone != StatusTone.Failure &&
                        shown?.tone != StatusTone.Attention
                inner(
                    if (hideStatusWhenTyping && kitKeyboardVisible() && optional) null else shown,
                    true,
                )
            }
        }
    }
    if (framed) {
        // The page's ground: flat, or the person's live background (Glass panes blur only that picture).
        KitLiveBackground(modifier.fillMaxSize()) {
            ScreenSheetHost(Modifier.fillMaxSize()) {
                content()
            }
        }
    } else {
        Box(modifier) { content() }
    }
}

/** Pane and nested page frames use the encompassing window's modal layer. */
@Composable
private fun ScreenSheetHost(modifier: Modifier, content: @Composable () -> Unit) {
    if (LocalKitSheetHost.current == null) {
        KitSheetHost(modifier, content)
    } else {
        Box(modifier) { content() }
    }
}

/**
 * The adaptive panes of a screen (Dart `KitScreen.twoPane` / `.threePane`): from expanded (not
 * short) [list] at 296 dp at the start, a hairline, and [detail] filling the rest with its content
 * capped at 700 and centred; [emptyDetail] when nothing is selected. On large, [side] at 340 dp at
 * the end. Panes follow the body, not the window: the detail shows only while it keeps its 360 dp
 * minimum ([Layout.showsDetail], [Layout.showsSide]). Below expanded only the list shows; opening a
 * row pushes the detail as a page (the screen's navigator). Each pane is its own traversal group:
 * list → detail → side.
 *
 * States: list, detail empty, detail selected, side Text: none Cap: Fill Motion: none (panes swap
 * at once, MOT-5) Hidden: none Replaces: KitScreen.twoPane, KitScreen.threePane
 * (lib/ui/kit/kit_screen.dart)
 */
@Composable
public fun KitPaneScreen(
    list: @Composable () -> Unit,
    detail: (@Composable () -> Unit)?,
    emptyDetail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    side: (@Composable () -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    status: KitStatus? = null,
    search: (@Composable () -> Unit)? = null,
    loading: Boolean = false,
    loadingLabel: String? = null,
    bottom: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val window = LocalKitWindow.current
        val body = maxWidth.value
        val showsDetail = Layout.showsDetail(window.cls, window.short, body)
        val panes: @Composable (KitStatus?, Boolean) -> Unit = { line, ownsLine ->
            Panes(
                list,
                detail,
                emptyDetail,
                side,
                topBar,
                status,
                search,
                loading,
                loadingLabel,
                bottom,
                showsDetail = showsDetail,
                showsSide = side != null && Layout.showsSide(window.cls, window.short, body),
                line = line,
                ownsLine = ownsLine,
            )
        }
        if (showsDetail && !kitStatusSlotAbove()) {
            KitStatusSlot(status) { panes(it, true) }
        } else {
            panes(null, false)
        }
    }
}

@Composable
private fun Panes(
    list: @Composable () -> Unit,
    detail: (@Composable () -> Unit)?,
    emptyDetail: @Composable () -> Unit,
    side: (@Composable () -> Unit)?,
    topBar: (@Composable () -> Unit)?,
    status: KitStatus?,
    search: (@Composable () -> Unit)?,
    loading: Boolean,
    loadingLabel: String?,
    bottom: (@Composable () -> Unit)?,
    showsDetail: Boolean,
    showsSide: Boolean,
    line: KitStatus?,
    ownsLine: Boolean,
) {
    val modifier = Modifier
    val hairline = LocalKitColors.current.hairline
    val rule = kitHairline()
    val listPane: @Composable () -> Unit = {
        KitScreen(
            topBar =
                if (ownsLine)
                    ({
                        Column {
                            topBar?.invoke()
                            KitStatusLineFold(line)
                        }
                    })
                else topBar,
            status = status,
            search = search,
            loading = loading,
            loadingLabel = loadingLabel,
            bottom = bottom,
            page = showsDetail && topBar == null,
            // The list pane's rows keep their own gutters; the pane's hairline meets their touch area.
            edges = KitScreenEdges.Bleed,
            body = list,
        )
    }
    if (!showsDetail) {
        Box(
            modifier
                .fillMaxSize()
                .semantics { isTraversalGroup = true }
                .testTag("kit-screen-pane-list")
        ) {
            listPane()
        }
        return
    }
    fun Modifier.endRule() = drawBehind {
        val x =
            if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) 0.5f
            else size.width - 0.5f
        drawPaneRule(hairline, x, rule)
    }
    KitLiveBackground(modifier.fillMaxSize()) {
        ScreenSheetHost(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier.width(Layout.PANE_LIST_WIDTH.dp)
                        .fillMaxHeight()
                        .endRule()
                        .semantics { isTraversalGroup = true }
                        .testTag("kit-screen-pane-list")
                ) {
                    listPane()
                }
                Box(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .semantics { isTraversalGroup = true }
                        .testTag("kit-screen-pane-detail"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // Dart _Centred(paneDetailMaxWidth, fill: true): the pane's view takes the whole pane, so an
                    // empty state centres in it.
                    Box(Modifier.widthIn(max = Layout.PANE_DETAIL_MAX_WIDTH.dp).fillMaxSize(), propagateMinConstraints = true) {
                        (detail ?: emptyDetail)()
                    }
                }
                if (showsSide && side != null) {
                    Box(
                        Modifier.width(Layout.PANE_SIDE_WIDTH.dp)
                            .fillMaxHeight()
                            .drawBehind {
                                val x =
                                    if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl)
                                        size.width - 0.5f
                                    else 0.5f
                                drawPaneRule(hairline, x, rule)
                            }
                            .semantics { isTraversalGroup = true }
                            .testTag("kit-screen-pane-side")
                    ) {
                        side()
                    }
                }
            }
        }
    }
}

/** How far the composer's ground fades up into the body (B-102: the body keeps this much room too). */
internal const val COMPOSER_FADE_DP = 16

/** How far the chat header's ground fades down into the body. */
internal const val HEADER_FADE_DP = 12

/**
 * E20-S30: the bottom block as a translucent veil over the body that scrolls under it: the body blurred (API 31+, at
 * half resolution) under the page's ground at the veil's alpha, with a hairline along its top. No ground band, no fade.
 */
@Composable
private fun Modifier.kitChromeVeil(veil: ChromeVeil, frost: KitFrostState): Modifier {
    val colors = LocalKitColors.current
    return kitBackdropVeil(frost, veil, colors.ground, topEdge = colors.hairline)
}

/** Glass frosts what scrolls behind the chrome instead of fading it, so it adds no fade room. */
@Composable
private fun kitChromeIsGlass(): Boolean =
    io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle.current.style ==
        io.github.eslamasabry.oc.ui.design.style.Style.Glass

/**
 * The overlaid bar's or bottom block's ground: frost through [frost] when given, else the page's own ground. Above the
 * bottom block the body fades into that ground over 16 dp, and below the bar over 12 dp, so a line scrolling under
 * either is never cut in half.
 */
@Composable
private fun Modifier.kitChromeGround(role: FrostRole, frost: KitFrostState?): Modifier {
    val ground = LocalKitColors.current.ground
    // A short ground fade where the transcript meets the chrome: above the composer and, so text never slides under
    // the header with a hard cut, below the header. Glass frosts what scrolls behind instead.
    val glass = kitChromeIsGlass()
    // E20-S13: the chat header is an opaque block of the page's ground in every style (nothing in the transcript
    // shows through any part of it, Glass included); its fade below stays so text never meets it with a hard cut.
    val header = role == FrostRole.ChatHeader
    val fade =
        when {
            glass && !header -> Modifier
            role == FrostRole.Composer ->
                Modifier.drawBehind {
                    val reach = COMPOSER_FADE_DP.dp.toPx()
                    drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to ground.copy(alpha = 0f),
                            1f to ground,
                            startY = -reach,
                            endY = 0f,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, -reach),
                        size = androidx.compose.ui.geometry.Size(size.width, reach),
                    )
                }
            role == FrostRole.ChatHeader ->
                Modifier.drawBehind {
                    val reach = HEADER_FADE_DP.dp.toPx()
                    drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to ground,
                            1f to ground.copy(alpha = 0f),
                            startY = size.height,
                            endY = size.height + reach,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height),
                        size = androidx.compose.ui.geometry.Size(size.width, reach),
                    )
                }
            else -> Modifier
        }
    return then(fade)
        .then(
            if (frost != null && !header) Modifier.kitFrost(role, frost)
            else Modifier.background(ground.copy(alpha = 1f))
        )
}

/**
 * The panes' rule (Dart `ColoredBox(hairline)` of `KitTokens.hairlineWidth`): one physical pixel. Drawn as the pixel
 * column at [x] (a pane's first or last) at the hairline's coverage, so a third-of-a-pixel rule reads as Flutter's.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPaneRule(color: androidx.compose.ui.graphics.Color, x: Float, rule: androidx.compose.ui.unit.Dp) {
    val coverage = rule.toPx().coerceAtMost(1f)
    val column = if (x < size.width / 2) 0f else kotlin.math.ceil(size.width) - 1f // NV: the start or the end column
    drawRect(color, Offset(column, 0f), androidx.compose.ui.geometry.Size(1f, size.height), alpha = coverage)
}
