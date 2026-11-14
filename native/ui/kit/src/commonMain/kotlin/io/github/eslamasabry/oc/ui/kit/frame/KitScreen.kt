package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.offset
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.derivedStateOf
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
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassStackRoot
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBarPane
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidJoin
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidJoin
import io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidJoinBody
import io.github.eslamasabry.oc.ui.kit.effects.LIQUID_BAR_INSET_X
import io.github.eslamasabry.oc.ui.kit.effects.LIQUID_BAR_INSET_Y
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidTouchLight
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidTouchWatch
import io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidTouch
import io.github.eslamasabry.oc.ui.kit.effects.LiquidBarShape
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidChrome
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
import io.github.eslamasabry.oc.ui.kit.modal.KitToastHost
import io.github.eslamasabry.oc.ui.kit.modal.KitToastState
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitToasts
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

/**
 * Optional host inset source; null uses the platform's safe drawing area and keyboard. A scene or preview off the phone
 * (desktop renders, tests) provides the system bars it stands in for.
 */
public val LocalKitScreenInsets: androidx.compose.runtime.ProvidableCompositionLocal<WindowInsets?> =
    compositionLocalOf { null }

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

/**
 * The width of a [KitScreenEdges.Bleed] body, set by the frame (null elsewhere; 0 until measured). A part that is
 * meant to sit between the gutters (a notice) uses it to tell a bare placement, as wide as the body, from one its
 * screen already padded, so a bare one keeps the gutter and a padded one never gets a second.
 */
internal val LocalKitBleedBodyWidth = compositionLocalOf<androidx.compose.runtime.State<Int>?> { null }

/**
 * Keeps the page gutter around a part that [KitScreenEdges.Bleed] left against the window edge: when the part is
 * offered the whole width of the Bleed body, and sits on no rail, it is inset by the gutter on each side. A screen that
 * already padded it is offered less, so it is left alone.
 */
@Composable
internal fun Modifier.kitBleedGutter(): Modifier {
    val body = LocalKitBleedBodyWidth.current ?: return this
    if (io.github.eslamasabry.oc.ui.kit.row.LocalKitOnRail.current) return this
    val gutter = LocalKitTokens.current.gutter.dp
    return layout { measurable, constraints ->
        val whole = body.value > 0 && constraints.hasBoundedWidth && constraints.maxWidth >= body.value
        val side = if (whole) gutter.roundToPx() else 0
        val placeable = measurable.measure(constraints.offset(horizontal = -2 * side))
        layout(placeable.width + 2 * side, placeable.height) { placeable.place(side, 0) }
    }
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
    if (edges == KitScreenEdges.Bleed && !placed) {
        val width = remember { mutableIntStateOf(0) }
        Box(Modifier.fillMaxWidth().onSizeChanged { width.intValue = it.width }) {
            CompositionLocalProvider(LocalKitBleedBodyWidth provides width) { content() }
        }
        return
    }
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
 *
 * Glass, step 4: with [floatingBar] (opt-in) the body runs under [topBar] as a floating liquid bar where the lens is
 * drawn; see [KitFloatingBar]. Its second row is the one exception to R9, and only with that bar.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    /**
     * Glass, step 4: the body runs under [topBar] as a floating liquid bar where the lens is drawn ([KitFloatingBar]);
     * null (the default) keeps the frame as it was. Not read by the overlaid layout (a chat).
     */
    floatingBar: KitFloatingBar? = null,
    body: @Composable () -> Unit,
) {
    val framed = topBar != null || page
    // What the bottom block says it is (one pane of glass: KitReportBottomSlab), kept across the frame's two layouts.
    val bottomState = remember { KitScreenBottom() }
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
    val movableBody = remember { movableContentOf { io.github.eslamasabry.oc.ui.kit.row.KitStackScope { latestBody.value() } } }
    // Both layouts must invoke the same movable content. Switching from a raw slot to a
    // movable slot disposes it: the first optimistic chat bubble used to cancel its own send.
    val topPart: (@Composable () -> Unit)? = topBar?.let { movableTop }
    val bottomPart: (@Composable () -> Unit)? = bottom?.let { movableBottom }
    val bodyPart: @Composable () -> Unit = movableBody
    // The floating bar's second row moves between the bar's pane (lens) and the scrolling header (no lens) without being
    // rebuilt, so a chip row keeps its scroll and press state when the lens comes or goes (battery saver, reduced motion).
    val latestBelow = rememberUpdatedState(floatingBar?.below)
    val movableBelow = remember { movableContentOf { latestBelow.value?.invoke() } }
    // The Glass lab's "Page behind the words" (shipped 1: what passes under a pane's words is cleared).
    val tuning = io.github.eslamasabry.oc.ui.kit.effects.LocalKitGlassTuning.current
    val tuneDark = LocalKitColors.current.dark
    val barClear by rememberUpdatedState(tuning?.knobs(io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind.Bar, tuneDark)?.wordsClear ?: 1f)
    val boxClear by rememberUpdatedState(tuning?.knobs(io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind.MessageBox, tuneDark)?.wordsClear ?: 1f)
    // A status line never decides the layout. It used to: a line arriving, leaving or folding away for typing moved the
    // bar, body and bottom block to the other layout, which made the transcript jump and dropped the message box's
    // focus, so the keyboard closed (emulator 2026-11-10, the demo chat; owner, a new install's first chat: "steals
    // focus from composer whenever there's a tool tip below the top bar"). The overlaid layout now draws the line
    // itself, under the bar; only Find's own slot, a scrolling header or the loading line use the stacked layout.
    val inner: @Composable (line: KitStatus?, ownsLine: Boolean) -> Unit = { line, ownsLine ->
        // Review item 8: the keyboard's room is the kit's settled reading (kitKeyboardInsets), never the raw animated
        // inset, so a keyboard whose closing animation never ends here cannot hold the page up.
        val keyboard = kitKeyboardInsets()
        val insets = (LocalKitScreenInsets.current ?: kitSystemInsets()).union(keyboard)
        // Audit 2 (2026-11-14, a tall sheet's top edge sliced the bar's subtitle): the page tells its sheets where its top
        // chrome ends, so a tall sheet rests below the bar's words (KitSheet's detents).
        val sheetHost = LocalKitSheetHost.current
        val chromeMark = remember { Any() }
        androidx.compose.runtime.DisposableEffect(sheetHost) { onDispose { sheetHost?.releaseChrome(chromeMark) } }
        if (overlayChrome && search == null && header == null && !loading) {
            var topPx by remember { mutableIntStateOf(0) }
            var bottomPx by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            // Owner 2026-11-09 ("a rough line just below Jump to latest", "that dark gradient below the notifications
            // bar", "an opaque slab under the composer"): a page that records its body (a chat) lays no band of ground
            // behind its bottom block in any style. Its body's content fades out above it instead (kitScrollEdges), so
            // the page's ground or live picture shows on, unbroken, down to the navigation bar. The glass faces see the
            // recording cleared the same way (topFade, wordsFade below).
            val edgesFade = frost != null
            // B-102: a page that records nothing keeps its ground and the fade above its bottom block; the body's room
            // includes that fade, so at rest the last line sits above it (and the first under the bar's 12 dp one).
            val faded = !edgesFade && !kitChromeIsGlass()
            // Liquid glass (Glass only, where FrostPolicy does not hold it): the bar and the composer float as glass
            // over what scrolls behind them; a null face keeps today's ground.
            // Joined glass (KitLiquidJoin.kt): a menu from the bar's controls, or the jump pill on the message box, is one
            // body of glass with the bar's capsule or the box while both are liquid. Only where the body is recorded.
            val joinState = remember { KitLiquidJoin() }
            val join = joinState.takeIf { edgesFade && kitChromeIsGlass() && !KitMotion.state.reduced }
            val barBody = rememberKitLiquidJoinBody(join, "bar")
            // Owner 2026-11-14 ("Wtfff": transcript text bent inside the bar's lower edge): the bar's glass, the words on it
            // and the status edge see the body exactly as the page shows it, cleared above the bar (and a control under
            // it) and fading in over the same 12 dp, in both Glass looks.
            val topFade = if (edgesFade && topPart != null && kitChromeIsGlass()) rememberKitWordsFade(frost!!, KitWordsClear.Above) else null
            val topFrost = topFade?.faded ?: frost
            val barFace = if (topPart != null) kitLiquidFace(LiquidRole.Bar, topFrost, LiquidBarShape, join = barBody) else null
            // The capsule answers a finger on its controls: a soft light under it, a brighter edge (KitLiquidTouch.kt).
            val barTouch = rememberKitLiquidTouch(on = barFace != null)
            val barInset = with(LocalDensity.current) { { Offset(LIQUID_BAR_INSET_X.dp.toPx(), LIQUID_BAR_INSET_Y.dp.toPx()) } }
            // Owner's pick 2026-11-09 (chat edge, option A): a page that records its body runs it edge to edge, under
            // the status bar and the bar; at rest its content starts below them (KitTopEdge.kt).
            val frostedFace = if (topPart != null && edgesFade && barFace == null) kitFrostedBarFace(topFrost) else null
            val look = topEdgeLook(liquid = barFace != null, frosted = frostedFace != null)
            val statusBlur = if (edgesFade && look != KitTopEdgeLook.Clear) kitStatusEdgeBlur() else null
            // A bottom block that is one pane of glass (KitReportBottomSlab): it sits in from the sides as the bar does.
            val slab = bottomState.slab && bottomPart != null
            // Owner 2026-11-14 ("Wtfff": a smear of bent transcript text along the message box's top edge, and words
            // mangled under "Jump to latest"): the block's glass, and the pill's, see the body exactly as the page shows
            // it: gone above the block (and above the pill), fading out over the same 16 dp. A clearing hides everything
            // it covers or nothing. Every style: a pane that frosts what is behind it frosts the same.
            val wordsFade = if (edgesFade && bottomPart != null) rememberKitWordsFade(frost!!) else null
            SideEffect { bottomState.glass = wordsFade?.faded }
            // A control floating just above the block (the jump pill): the body ends, by its fade, before it too, so it
            // never stands on a line of text. The page eases its edge to the new place; the glass sees the new place at
            // once (it must never bend the pill's own picture). Nothing floats under the bar (audit 1, 2026-11-14: the
            // chat's "N earlier messages" is the transcript's first row now), so the top edge is the bar's alone.
            var topBottom by remember { mutableFloatStateOf(Float.NaN) }
            val fadeBottomPx = with(density) { COMPOSER_FADE_DP.dp.toPx() }
            val floatGapPx = with(density) { FLOAT_GAP_DP.dp.toPx() }
            val bottomExtraTarget by remember(floatGapPx) {
                derivedStateOf {
                    val f = bottomState.floatTop
                    val b = bottomState.blockTop
                    if (f.isNaN() || b.isNaN()) 0f else (b - f + floatGapPx).coerceAtLeast(0f)
                }
            }
            val bottomExtra = remember { androidx.compose.animation.core.Animatable(0f) }
            val zoneSpec by rememberUpdatedState(KitMotion.specs.quick<Float>())
            LaunchedEffect(bottomExtra) {
                snapshotFlow { bottomExtraTarget }.collectLatest { bottomExtra.animateTo(it, zoneSpec) }
            }
            val blockShown by rememberUpdatedState(bottomPart != null)
            val zones =
                remember(bottomState) {
                    KitScreenZones(bottomGone = { if (!blockShown) Float.NaN else bottomState.blockTop - bottomExtra.value })
                }
            val scroll = remember { KitScreenScroll() }
            val under = kitUnderTop(scroll)
            // Audit 1 (2026-11-14: a bubble's top cut flat under the bar, a copy icon cut by a line just below the bar's
            // rim): what scrolls under the bar dissolves over TOP_DISSOLVE_DP, in every look, never a short ramp that
            // reads as a cut. It grows with what lies under the bar (none at rest, so a page at rest looks as it did:
            // its first row whole right below the bar). The bar's glass sees the same dissolve.
            val dissolvePx = with(density) { TOP_DISSOLVE_DP.dp.toPx() }
            val topDissolve: () -> Float = { dissolvePx * under() }
            // The bar's copy: at least a pixel, as its copy clears nothing without a rim (KitWordsFade.record).
            val barDissolve: () -> Float = { topDissolve().coerceAtLeast(1f) }
            // The window's insets include the keyboard, which moves every frame while it opens: each side is read
            // through a derived value, so the page is composed again only when that side's own size changes (the top
            // never does while typing), and the bottom is read only by a page that draws a ground under its bottom block.
            val insetTop by remember(insets, density, framed) { derivedStateOf { if (framed) insets.getTop(density) else 0 } }
            val insetBottom =
                if (framed && !edgesFade) remember(insets, density) { derivedStateOf { insets.getBottom(density) } }.value else 0
            // The status bar the body runs under (0 for a page that keeps the old frame).
            val statusPx = if (edgesFade) insetTop else 0
            // The room the chrome takes from the body. Its sizes are read where the padding is used (a list measures
            // with it), not while the page is composed: the message box growing a line, or a status line folding in,
            // then lays the body out again without composing the whole page again.
            val hasTop = topPart != null
            val hasBottom = bottomPart != null
            val padding =
                remember(density, edgesFade, faded, hasTop, hasBottom, statusPx) {
                    object : PaddingValues {
                        // A: the body's content rests below the status bar and the bar. E20-S13: a page that records
                        // nothing keeps its content clear of the bar, fading in over 12 dp under it.
                        override fun calculateTopPadding(): Dp =
                            with(density) {
                                if (edgesFade) (statusPx + topPx).toDp()
                                else topPx.toDp() + if (hasTop) HEADER_FADE_DP.dp else 0.dp
                            }

                        // E20-S30: a chat's newest line rests on the list's own padding, right above the bottom block;
                        // the content fades out from 8 dp above the block over its top 16 dp.
                        // 2026-11-14: a chat's transcript is gone at the block's top, fading out over the 16 dp above
                        // it; the list keeps the part of that fade its own 8 dp padding does not cover, so its newest
                        // line rests whole.
                        override fun calculateBottomPadding(): Dp =
                            with(density) { bottomPx.toDp() } +
                                when {
                                    !hasBottom -> 0.dp
                                    faded -> COMPOSER_FADE_DP.dp
                                    edgesFade -> (COMPOSER_FADE_DP - EDGE_LEAD_DP).dp
                                    else -> 0.dp
                                }

                        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

                        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
                    }
                }
            Box(
                Modifier.fillMaxSize()
                    .then(
                        when {
                            !framed -> Modifier
                            edgesFade -> Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                            else -> Modifier.windowInsetsPadding(insets)
                        }
                    )
                    .testTag("kit-screen")
            ) {
                CompositionLocalProvider(
                    LocalChromePadding provides padding,
                    LocalKitScreenScroll provides scroll,
                    LocalKitScreenBottom provides bottomState,
                    LocalKitScreenZones provides (if (edgesFade) zones else null),
                    LocalKitLiquidJoin provides join,
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            // The bar's copy of the recording is made once the body has drawn (the status edge, drawn next,
                            // reads it).
                            .then(
                                if (topFade == null) Modifier
                                else Modifier.kitWordsFadeRecorder(topFade, { topBottom + barDissolve() }, barDissolve, after = true, clear = { barClear })
                            )
                            .then(
                                if (!edgesFade) Modifier
                                else
                                    Modifier.kitScrollEdges(
                                        // Owner 2026-11-14 ("Wtfff"): floating things own their ground. Nothing of the
                                        // body is drawn under the status bar, the bar or its status line; it dissolves
                                        // in below them over TOP_DISSOLVE_DP.
                                        clearAbove = if (topPart == null) null else ({ (statusPx + topPx).toFloat() }),
                                        topReach = TOP_DISSOLVE_DP.dp,
                                        // Owner 2026-11-14 ("solids ... on the header"): the clear look lays nothing
                                        // behind the chrome, so the body's dissolve alone ends the content before the
                                        // bar; Glass's the same. It grows with what lies under the chrome (none at
                                        // rest: a page at rest looks as it did; at once under reduced motion).
                                        topReachPx = topDissolve,
                                        // And nothing in or under the bottom block (its chips row, the message box): the
                                        // body fades out over the 16 dp above it. A list that ends itself (above the jump
                                        // pill too) takes this over, so the pill, drawn beside the list, stays whole.
                                        fadeFromBottom = if (bottomPart == null) null else ({ bottomPx + fadeBottomPx }),
                                        bottomReach = COMPOSER_FADE_DP.dp,
                                        bottomStrength = { if (zones.claims > 0) 0f else 1f },
                                    )
                            )
                            .kitMaxWidth(cap)
                    ) { KitScreenBody(edges) { bodyPart() } }
                }
                // Glass's status edge; the clear look draws nothing behind the chrome (KitTopEdge.kt).
                if (edgesFade && topPart != null && frost != null && look != KitTopEdgeLook.Clear)
                    KitStatusEdge(topFrost ?: frost, statusPx, under = under, blur = statusBlur)
                // The bar, and under it the one status line: one block whose height is the body's top room, so a line
                // folding in or out eases the transcript down or up and nothing else moves.
                if (topPart != null || ownsLine) {
                    Column(
                        Modifier.align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .then(if (statusPx > 0) Modifier.padding(top = with(density) { statusPx.toDp() }) else Modifier)
                            .onSizeChanged { topPx = it.height }
                            .onGloballyPositioned {
                                topBottom = it.positionInRoot().y + it.size.height
                                if (topPart != null) sheetHost?.holdChrome(chromeMark, topBottom)
                            }
                            .then(if (edgesFade || barFace != null) Modifier else Modifier.kitChromeGround(FrostRole.ChatHeader, frost, reach = insetTop))
                    ) {
                        topPart?.let {
                            Box(
                                Modifier.fillMaxWidth()
                                    .kitLiquidTouchWatch(barTouch)
                                    .then(if (barFace != null) kitLiquidWordsHost(LiquidRole.Bar, topFrost) else Modifier)
                            ) {
                                CompositionLocalProvider(LocalKitLiquidJoin provides join, io.github.eslamasabry.oc.ui.kit.effects.LocalKitOnLiquidGlass provides (barFace != null)) {
                                    if (barFace != null) KitLiquidBarPane(barFace.kitLiquidTouchLight(barTouch, LiquidBarShape, barInset, barBody), barBody)
                                    else if (frostedFace != null) KitLiquidBarPane(frostedFace)
                                    it()
                                }
                            }
                        }
                        if (ownsLine)
                            KitStatusLineFold(
                                line,
                                Modifier.kitStatusRoom()
                                    .verticalScroll(rememberScrollState())
                                    .testTag("kit-screen-status"),
                            )
                    }
                }
                bottomPart?.let {
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { bottomPx = it.height }
                            .onGloballyPositioned {
                                bottomState.blockTop = it.positionInRoot().y
                                bottomState.restGap = with(density) { (Space.S2 * 3).dp.toPx() }
                            }
                            .then(
                                if (wordsFade == null) Modifier
                                else Modifier.kitWordsFadeRecorder(wordsFade, { bottomState.blockTop - bottomExtraTarget - fadeBottomPx }, { fadeBottomPx }, clear = { boxClear })
                            )
                            .then(
                                if (edgesFade) Modifier
                                else Modifier.kitChromeGround(FrostRole.Composer, frost, reach = insetBottom)
                            )
                            .padding(
                                // A glass slab sits as far in from the sides as the bar's capsule does.
                                horizontal = if (slab) LIQUID_BAR_INSET_X.dp else LocalKitTokens.current.gutter.dp,
                                vertical = Space.S2.dp,
                            )
                            .testTag("kit-screen-bottom")
                    ) {
                        CompositionLocalProvider(LocalKitLiquidChrome provides (wordsFade?.faded ?: frost), LocalKitScreenBottom provides bottomState, LocalKitLiquidJoin provides join) { it() }
                    }
                }
            }
        } else {
            // Liquid glass. A chat (it records its transcript) keeps its liquid bar and composer here too, so a status
            // line, the loading line or Find never flips them to frost. A page with a scrolling header runs its body up
            // under the bar (the header keeps its place below it) and the bar floats as glass over what scrolls there;
            // while a status line or Find sits between them the body eases back below the bar, never jumping.
            val under = rememberKitLiquidSource()
            val density = LocalDensity.current
            val lens = kitLiquidGlassOn()
            // Glass, step 4 (KitFloatingBar.kt): the bar floats over the body only where the lens is drawn and the
            // window is tall enough; everywhere else (every other style, no lens, a short window) this frame is exactly
            // what it was without the option.
            val floats = floatingBar != null && topPart != null && frost == null && lens && !window.short
            // The feature's own recording of its body, which the floating bar bends instead of a kit recording.
            val lifted = if (floats) floatingBar?.source else null
            // Words on the floating header's glass (KitWordsFade.kt): its glass and the words on it see a copy of the page's
            // recording cleared above the pane's bottom edge less its lens rim; the original keeps recording.
            val floatPane = remember { KitTopPane() }
            val liftedFade = if (lifted != null) rememberKitWordsFade(lifted, KitWordsClear.Above) else null
            // No lens (or a short window) with scrollsElsewhere: no pinned bar; the bar and its second row are the
            // scrolling header's first parts, as the page laid them out before it floated.
            val barInHeader = floatingBar != null && floatingBar.scrollsElsewhere && !floats && topPart != null
            val stackTop = if (barInHeader) null else topPart
            val belowPart: (@Composable () -> Unit)? = floatingBar?.below?.let { movableBelow }
            // A page's tab row (KitFloatingBar.belowPinned) stays under a bar that does not float, instead of scrolling away.
            val belowPinned = floatingBar?.belowPinned == true && belowPart != null && !floats && !barInHeader
            val pageHeader: (@Composable ColumnScope.() -> Unit)? =
                when {
                    floatingBar == null -> header
                    barInHeader -> ({ topPart?.invoke(); belowPart?.invoke(); header?.invoke(this) })
                    // A pinned bar without the lens: its second row leads the scrolling header.
                    !floats && belowPart != null && !belowPinned -> ({ belowPart(); header?.invoke(this) })
                    // A floating page without a header gets one that holds only the spacer below the bar.
                    floats && header == null -> ({})
                    else -> header
                }
            val runs = (frost == null && stackTop != null && pageHeader != null && lens) || floats
            // What the bar's glass (and the status bar's edge) sees of a body that runs under it: the kit's recording,
            // cleared as the page itself is (audit 2, 2026-11-14: Settings' glass bar and status bar showed blurred copies
            // of the page's words). The floating pane and a pinned glass bar both read it.
            val underFade = if (runs && lifted == null) rememberKitWordsFade(under, KitWordsClear.Above) else null
            val floatFade = liftedFade ?: underFade
            val barFace =
                when {
                    stackTop == null -> null
                    frost != null -> kitLiquidFace(LiquidRole.Bar, frost, LiquidBarShape)
                    lifted != null -> kitLiquidFace(LiquidRole.Bar, liftedFade?.faded ?: lifted, LiquidBarShape)
                    runs -> kitLiquidFace(LiquidRole.Bar, underFade?.fadedSource ?: under, LiquidBarShape)
                    else -> null
                }
            var barPx by remember { mutableIntStateOf(0) }
            // Where the run-up body lies in the window, to draw a feature's recording of it under the status bar.
            val bodyOrigin = remember { arrayOf(Offset.Zero) }
            // Chat edge A, kept consistent on every page that scrolls under its bar (owner 2026-11-09): the body runs
            // on up under the status bar too, where it dissolves into a blur of itself (KitTopEdge.kt).
            val statusRun by remember(insets, density, runs, framed) { derivedStateOf { if (runs && framed) insets.getTop(density) else 0 } }
            val runTarget = if (runs && line == null && search == null) barPx + statusRun else 0
            val headerState = remember { KitScrollingHeaderState() }
            val topScroll = remember(headerState) { KitScreenScroll().also { it.reporter = { headerState.offset < 0f } } }
            val headerUnder = kitUnderTop(topScroll)
            val edgeLayers = rememberStatusEdgeLayers()
            // How far the floating pane's bottom edge sits above the bottom of the bar's box (KitLiquidBarPane).
            val paneInsetPx = with(density) { LIQUID_BAR_INSET_Y.dp.roundToPx() }
            val runPx by androidx.compose.animation.core.animateIntAsState(
                runTarget,
                if (KitMotion.state.reduced) androidx.compose.animation.core.snap<Int>() else KitMotion.specs.enter<Int>(),
                label = "kit.screen.under-bar",
            )
            // Audit 2 (2026-11-14: a hard clip sliced the last row above the navigation bar, headings were cut at the bar's
            // lower edge, a heading ran into a page's footer): one page edge for every page, every style. Content never
            // ends in a hard line. Under the top chrome it is gone, and it dissolves in below it over TOP_DISSOLVE_DP once
            // anything lies under the chrome (at rest nothing is faded); above the bottom edge (the navigation bar, a
            // fixed button, a pinned foot) it dissolves out over the same length while more of it lies below, gone
            // FOOT_GAP_DP before a footer. The page's scroller says where its content lies (kitPageScrollState,
            // kitPageListState, KitPinnedFootBody); a body that says nothing is watched for the person's own scrolling.
            val pageScroll = remember { KitScreenScroll().also { it.guessing = true } }
            val edgeWatch = remember(pageScroll) { KitScreenEdgeWatch(pageScroll) }
            val bodyUnder = kitUnderTop(pageScroll)
            val moreBelow = kitMoreBelow(pageScroll)
            val dissolvePx = with(density) { TOP_DISSOLVE_DP.dp.toPx() }
            val footGapPx = with(density) { FOOT_GAP_DP.dp.toPx() }
            val topUnder: () -> Float = { maxOf(headerUnder(), bodyUnder()) }
            // Where the chrome ends inside the scrolling box: a body that runs under the bar is gone above the floating
            // pane's bottom edge (the pinned glass bar's); every other body starts below its chrome.
            val topClear: () -> Float = {
                when {
                    floats -> (runPx - paneInsetPx).coerceAtLeast(0).toFloat()
                    runs -> runPx.toFloat()
                    else -> 0f
                }
            }
            val topDissolve: () -> Float = { dissolvePx * topUnder() }
            // The bar's copy of the recording: at least a pixel, as its copy clears nothing without a rim.
            val barDissolve: () -> Float = { topDissolve().coerceAtLeast(1f) }
            Column(
                Modifier.fillMaxSize()
                    .then(if (framed) Modifier.windowInsetsPadding(insets) else Modifier)
                    .testTag("kit-screen")
            ) {
                if (stackTop != null) {
                    Box(
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        ).then(
                            when {
                                // The floating bar: one pane over the body holding the bar and its second row. A bar
                                // that is the page's own heading keeps the body's width.
                                floats -> Modifier.zIndex(1f).onSizeChanged { barPx = it.height }
                                    .then(if (barFace == null) Modifier.background(LocalKitColors.current.ground.copy(alpha = 1f)) else Modifier)
                                    .then(if (floatingBar?.scrollsElsewhere == true) Modifier.kitMaxWidth(cap) else Modifier)
                                    // A touch on the floating chrome (its title, the gaps between its controls) is the chrome's:
                                    // it never reaches a row that has scrolled under it.
                                    .kitTouchSink()
                                runs -> Modifier.zIndex(1f).onSizeChanged { barPx = it.height }
                                    .then(if (barFace != null) kitLiquidWordsHost(LiquidRole.Bar, underFade?.fadedSource ?: under) else Modifier.background(LocalKitColors.current.ground.copy(alpha = 1f)))
                                    .then(
                                        if (underFade == null || barFace == null) Modifier
                                        else
                                            Modifier.kitTopPaneReport(floatPane, 0, 0)
                                                .kitWordsFadeRecorder(underFade, { floatPane.bottom + barDissolve() }, barDissolve, clear = { barClear })
                                    )
                                barFace != null -> kitLiquidWordsHost(LiquidRole.Bar, frost)
                                else -> Modifier
                            }
                        )
                    ) {
                        if (floats) {
                            // One body of glass (coordinator 2026-11-11, after the scrolled render): the bar and its
                            // second row lie on one pane, with one bottom edge, so nothing of the page shows between
                            // them. The second row's controls are controls on that pane, not panes: the pane is the
                            // root of their glass stack (they take no frost of their own) and they ask for no lens.
                            // The words on it read the same recording the pane bends.
                            Box(
                                Modifier.fillMaxWidth()
                                    .then(if (barFace != null) Modifier.kitGlassStackRoot() else Modifier)
                                    .then(
                                        when {
                                            barFace == null -> Modifier
                                            lifted != null -> kitLiquidWordsHost(LiquidRole.Bar, liftedFade?.faded ?: lifted)
                                            else -> kitLiquidWordsHost(LiquidRole.Bar, underFade?.fadedSource ?: under)
                                        }
                                    )
                                    .then(
                                        if (floatFade == null || barFace == null) Modifier
                                        else
                                            Modifier.kitTopPaneReport(floatPane, LIQUID_BAR_INSET_X, LIQUID_BAR_INSET_Y)
                                                // Owner 2026-11-14 (nothing readable under a floating control): the pane's
                                                // glass sees the page as the page shows it, gone above the pane's bottom
                                                // edge and dissolving in below it, as the conversation's bar does.
                                                .kitWordsFadeRecorder(floatFade, { floatPane.bottom + barDissolve() }, barDissolve, clear = { barClear })
                                    )
                            ) {
                                CompositionLocalProvider(io.github.eslamasabry.oc.ui.kit.effects.LocalKitOnLiquidGlass provides (barFace != null)) {
                                    if (barFace != null) KitLiquidBarPane(barFace)
                                    Column(Modifier.fillMaxWidth()) {
                                        stackTop()
                                        // The second row keeps the room under it the title has above it, and its
                                        // sideways lines stay inside the pane's sides (FloatingPaneRow).
                                        belowPart?.let { below ->
                                            Box(Modifier.fillMaxWidth().padding(bottom = FloatingPaneRow.ROOM_BELOW_DP.dp).testTag("kit-floating-below")) {
                                                CompositionLocalProvider(LocalKitOnFloatingPane provides true) { below() }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (barFace != null) KitLiquidBarPane(barFace)
                            if (belowPinned && belowPart != null) {
                                // Pinned under the bar, one block with it (whatever lies behind the bar lies behind the row),
                                // on the page margin.
                                Column(Modifier.fillMaxWidth()) {
                                    stackTop()
                                    Box(Modifier.fillMaxWidth().padding(horizontal = kitPageGutter()).testTag("kit-pinned-below")) { belowPart() }
                                }
                            } else stackTop()
                        }
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
                        .then(
                            if (!runs) Modifier
                            else
                                Modifier.kitRunUnderBar { runPx }
                                    .then(if (lifted != null) Modifier.onGloballyPositioned { bodyOrigin[0] = it.positionInRoot() } else Modifier)
                                    // Chat edge A, as the conversation now has it (owner 2026-11-14: nothing readable
                                    // under a floating control): the body is gone under the status bar and the bar (the
                                    // page edges below clear it); the status bar keeps only the dim of the page's ground
                                    // while the header has scrolled under, easing with the run. No blurred copy of the
                                    // page is drawn there.
                                    .then(
                                        if (barFace == null || statusRun <= 0) Modifier
                                        else
                                            Modifier.drawWithContent {
                                                drawContent()
                                                val k = runShare(runPx, barPx + statusRun)
                                                if (k > 0f) drawStatusEdge(edgeLayers, statusRun.toFloat(), null, headerUnder() * k, alpha = k) {}
                                            }
                                    )
                                    // The kit records the body only when no feature recording stands in for it.
                                    .then(if (lifted != null) Modifier else Modifier.kitLiquidSource(under))
                        )
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
                    // A chat stacked under a status line, the loading line or Find: its transcript fades in under
                    // what sits above it and out above the bottom block, never cut across.
                    val stackedEdges =
                        if (frost == null) Modifier
                        else
                            Modifier.kitScrollEdges(
                                clearAbove = { 0f },
                                topReach = HEADER_FADE_DP.dp,
                                fadeFromBottom =
                                    if (bottomPart == null) null
                                    else ({ with(density) { COMPOSER_FADE_DP.dp.toPx() } }),
                                bottomReach = COMPOSER_FADE_DP.dp,
                            )
                    // Glass with the lens (the bar floats): the bottom block is a pane like the bar. The body runs on
                    // under it, so rows pass behind its glass instead of being cut at its top; the frame tells the body
                    // the block's height (kitScreenChromePadding) so the last row rests above it.
                    val footFloats = floats && bottomPart != null
                    var footPx by remember { mutableIntStateOf(0) }
                    // Every other page: the one page edge (above). One offscreen layer for the page's scrolling part. A
                    // floating foot pane is the body's to clear under: the content is gone FOOT_GAP_DP above it. A foot
                    // pinned inside the body (KitPinnedFootBody) is kept whole; the content is gone the same gap above it.
                    val pageEdges =
                        if (frost != null) stackedEdges
                        else
                            Modifier.onGloballyPositioned { c ->
                                // Only a page with chrome pinned at its top tells its sheets where that chrome ends.
                                if (stackTop != null || search != null) sheetHost?.holdChrome(chromeMark, c.positionInRoot().y + topClear())
                            }
                                .kitScrollEdges(
                                    clearAbove = topClear,
                                    topReach = 0.dp,
                                    topReachPx = topDissolve,
                                    fadeFromBottom = {
                                        val foot = pageScroll.footRoom.toFloat()
                                        val floating = if (footFloats) footPx + footGapPx else 0f
                                        foot + (if (foot > 0f) footGapPx else 0f) + floating + dissolvePx
                                    },
                                    bottomReach = 0.dp,
                                    bottomReachPx = { dissolvePx },
                                    bottomStrength = moreBelow,
                                    keepBottomPx = { pageScroll.footRoom.toFloat() },
                                )
                    // The body: its scroller reports to the page's edges; a body that reports nothing is watched.
                    val pageBody: @Composable () -> Unit = {
                        CompositionLocalProvider(LocalKitScreenScroll provides pageScroll) { KitScreenBody(edges) { bodyPart() } }
                    }
                    // A floating bar: a part brought into view (a focused field, a keyboard move) that would rest under
                    // the bar brings the spacer back, so it lies below the bar (KitFloatingBar.kt).
                    val reducedMotion = KitMotion.state.reduced
                    val intoSpec = KitMotion.specs.enter<Float>()
                    val intoView =
                        if (!floats) Modifier
                        else
                            Modifier.bringIntoViewResponder(
                                remember(headerState) {
                                    FloatingBarIntoView(headerState, { runPx }, { if (reducedMotion) null else intoSpec })
                                }
                            )
                    val scrolling: @Composable (Modifier) -> Unit = { m ->
                        if (pageHeader != null) {
                            val runDp = with(LocalDensity.current) { runPx.toDp() }
                            val shownHeader: @Composable ColumnScope.() -> Unit =
                                if (!runs) pageHeader else ({ Spacer(Modifier.height(runDp)); pageHeader() })
                            KitScrollingHeaderFrame(shownHeader, m.then(intoView).then(pageEdges), headerState) {
                                KitLoadingBar(loading, loadingLabel.orEmpty(), if (loading) Modifier.testTag("kit-screen-loading") else Modifier)
                                Box(Modifier.fillMaxWidth().weight(1f).nestedScroll(edgeWatch)) { pageBody() }
                            }
                        } else {
                            // Dart: KitLoadingBar(loading:, label:) always takes its 2 dp, so the page never jumps.
                            Column(m) {
                                KitLoadingBar(loading, loadingLabel.orEmpty(), if (loading) Modifier.testTag("kit-screen-loading") else Modifier)
                                Box(Modifier.fillMaxWidth().weight(1f).nestedScroll(edgeWatch).then(pageEdges)) { pageBody() }
                            }
                        }
                    }
                    val scrollArea: @Composable (Modifier) -> Unit = { area ->
                        if (refresh != null) {
                            KitRefresh(
                                refreshing = refresh.refreshing,
                                onRefresh = refresh.onRefresh,
                                modifier = area,
                                enabled = refresh.enabled,
                                hostState = refresh.state,
                                // Under a floating bar the disc comes out below the bar, not under it.
                                indicatorTopPx = if (floats) ({ runPx }) else null,
                            ) {
                                scrolling(Modifier.fillMaxSize())
                            }
                        } else {
                            scrolling(area)
                        }
                    }
                    val bottomBlock: @Composable (Modifier) -> Unit = { place ->
                        bottomPart?.let {
                            Box(
                                place.fillMaxWidth()
                                    .windowInsetsPadding(
                                        keyboard
                                            .union(WindowInsets.navigationBars)
                                            .only(WindowInsetsSides.Bottom)
                                    )
                                    .padding(
                                        horizontal = if (bottomState.slab) LIQUID_BAR_INSET_X.dp else LocalKitTokens.current.gutter.dp,
                                        vertical = Space.S2.dp,
                                    )
                                    .testTag("kit-screen-bottom"),
                                // Dart's Column(crossAxisAlignment: stretch): the bottom block's child takes the full
                                // width between the gutters (the page's primary is full width).
                                propagateMinConstraints = true,
                            ) {
                                CompositionLocalProvider(LocalKitLiquidChrome provides frost, LocalKitScreenBottom provides bottomState) { it() }
                            }
                        }
                    }
                    if (!footFloats) {
                        scrollArea(Modifier.fillMaxWidth().weight(1f))
                        bottomBlock(Modifier)
                    } else {
                        val footDp = with(density) { footPx.toDp() }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            CompositionLocalProvider(LocalChromePadding provides PaddingValues(bottom = footDp)) {
                                scrollArea(Modifier.fillMaxSize())
                            }
                            // A touch on the pane (the gaps between its parts) is the pane's, never a row's under it.
                            bottomBlock(
                                Modifier.align(Alignment.BottomCenter)
                                    .zIndex(1f)
                                    .onSizeChanged { footPx = it.height }
                                    .kitTouchSink()
                            )
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
                inner(if (hideStatusWhenTyping && kitKeyboardVisible() && optional) null else shown, true)
            }
        }
    }
    if (framed) {
        // The page's ground: flat, or the person's live background (Glass panes blur only that picture).
        // One live background for the whole app (smoothness round 2): every page shows the theme root's one picture.
        KitLiveBackground(modifier.fillMaxSize(), shared = true) {
            ScreenToastHost {
                ScreenSheetHost(Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    } else {
        Box(modifier) { content() }
    }
}

/**
 * The window's toast slot, part of the page frame: a page with no slot above it (a window other than the app's shell:
 * the widget panels, Android's Manage space page) provides one and draws its toast over everything it hosts, so no
 * window can forget it and leave every `show` silent. Under the shell's slot (or an outer page's) it adds nothing.
 */
@Composable
private fun ScreenToastHost(content: @Composable () -> Unit) {
    if (LocalKitToasts.current != null) {
        content()
        return
    }
    val toasts = remember { KitToastState() }
    CompositionLocalProvider(LocalKitToasts provides toasts) {
        Box(Modifier.fillMaxSize()) {
            content()
            KitToastHost(toasts, Modifier.zIndex(4f))
        }
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
    KitLiveBackground(modifier.fillMaxSize(), shared = true) {
        ScreenToastHost {
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
}

/**
 * Takes every touch inside this box, so nothing that lies under it (a row scrolled under the floating bar) is hit; the
 * box's own controls still get theirs. Hit testing stops at the first sibling that has pointer input under the finger.
 */
internal fun Modifier.kitTouchSink(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
        }
    }

/** How far a body has run up under its chrome, 0–1 (eases the status edge in and out with the run). */
private fun runShare(runPx: Int, fullPx: Int): Float = if (fullPx > 0) (runPx.toFloat() / fullPx).coerceIn(0f, 1f) else 0f

/** How far the composer's ground fades up into the body (B-102: the body keeps this much room too). */
internal const val COMPOSER_FADE_DP = 16

/** The list's own padding under a chat's newest line (the frame adds the rest of its 16 dp fade to the room). */
internal const val EDGE_LEAD_DP = 8

/** How far the chat header's ground fades down into the body. */
internal const val HEADER_FADE_DP = 12

/**
 * How far what scrolls under an edge-to-edge page's top chrome dissolves below the bar, dp (audit 1, 2026-11-14: over
 * [HEADER_FADE_DP] a bubble's top read as cut flat and an icon as cut by a line). Long enough that no edge of the
 * content reads as a cut: every dp of it changes the content's strength by at most a sixteenth.
 */
internal const val TOP_DISSOLVE_DP = 24

/**
 * How far above a footer (a pinned foot's words, a floating foot pane) a page's content is already gone, dp (audit 2,
 * 2026-11-14: Report a problem's "Performance" heading ran straight into the footer's words).
 */
internal const val FOOT_GAP_DP = Space.S2

/** How far above a control floating over the body (the jump pill) the body is already gone, dp. */
internal const val FLOAT_GAP_DP = 8

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
private fun Modifier.kitChromeGround(role: FrostRole, frost: KitFrostState?, reach: Int = 0): Modifier {
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
    // The ground runs on through the system bar beside it ([reach] px: the status bar above the header, the navigation
    // bar under the bottom block), so its only edge is the screen's own, never a line under the status bar.
    val opaque = ground.copy(alpha = 1f)
    val bar =
        if (reach <= 0 || (frost != null && !header)) Modifier
        else
            Modifier.drawBehind {
                val y = if (header) -reach.toFloat() else size.height
                drawRect(opaque, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, reach.toFloat()))
            }
    return then(fade)
        .then(bar)
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
