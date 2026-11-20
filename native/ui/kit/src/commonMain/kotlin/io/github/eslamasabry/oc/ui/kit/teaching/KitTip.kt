package io.github.eslamasabry.oc.ui.kit.teaching

import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitStyledFace
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.MenuLayerEntry
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitMenuLayer
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuLayerState
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.motion.tipFade
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlinx.coroutines.delay

/** Dart `KitTip` numbers and placement (plan 06 §7.2; plan 08 §5.15). */
public object TipRules {
    /** The bubble's widest (Cap Popover, bubble 280). */
    public const val MAX_WIDTH_DP: Int = 280

    /** How long a tip stays by itself (`KitTip.defaultHold`, 4 s). */
    public const val HOLD_MS: Long = MotionTokens.TIP_HOLD.toLong()

    /** Above the anchor when it sits below the top quarter of the window, else below. */
    public fun above(anchorTopPx: Float, windowHeightPx: Float): Boolean = anchorTopPx > windowHeightPx / 4f

    /**
     * An anchor taller than half the window is one the page is made of (a terminal, a canvas): its far side is where
     * its own controls are, so the tip sits inside it, at its top edge, never past its bottom.
     */
    public fun inside(anchorHeightPx: Float, windowHeightPx: Float): Boolean = anchorHeightPx > windowHeightPx / 2f

    /**
     * Beside an edge part ([KitTipPlacement.BesideEdge]): the tip stands on the page side of the anchor, [gapPx] away,
     * centred on it vertically and kept in the window, so it points at a 16 dp light and never lies over it. Returns
     * the tip's top-left (px).
     */
    public fun besideEdge(anchorLeft: Int, anchorTop: Int, anchorRight: Int, anchorBottom: Int, windowWidth: Int, windowHeight: Int, tipWidth: Int, tipHeight: Int, gapPx: Int): Pair<Int, Int> {
        val onRight = (anchorLeft + anchorRight) / 2 > windowWidth / 2
        val x = (if (onRight) anchorLeft - gapPx - tipWidth else anchorRight + gapPx).coerceIn(0, (windowWidth - tipWidth).coerceAtLeast(0))
        val y = ((anchorTop + anchorBottom) / 2 - tipHeight / 2).coerceIn(0, (windowHeight - tipHeight).coerceAtLeast(0))
        return x to y
    }

    /** Red-proof stub: the line has no yield rule, so another tip on screen changes nothing. */
    public fun mayShow(visible: Boolean, modalOpen: Boolean, yieldsToOthers: Boolean, othersShowing: Int): Boolean = visible && !modalOpen

    /** How far the tip rises toward its anchor as it appears: Full only. */
    public fun riseDp(state: KitMotionState): Int = if (state.travels) MotionTokens.TIP_RISE_DP else 0
}

/**
 * A part whose words a tip must never cover (owner, look 10100: "Hold Send…" lay over a shared link in the message
 * box). The part writes its window box here as it is placed; a [KitTip] anchored inside it stands above or below the
 * whole part instead of over it. A plain holder, not snapshot state: it is read only when the tip is placed.
 */
public class KitTipClearance {
    public var boundsInWindow: Rect = Rect.Zero
}

/** The [KitTipClearance] of the part around this point, if any (the message box provides its own). */
public val LocalKitTipClearance: ProvidableCompositionLocal<KitTipClearance?> = staticCompositionLocalOf { null }

/** Where a tip stands against its anchor. */
public enum class KitTipPlacement {
    /** Above the anchor (below near the top of the window), aligned with its nearer edge. */
    Auto,

    /**
     * Beside a thin part on the screen edge (the edge light, 16 dp): on the page side of it, centred on it, moving
     * sideways toward it as it appears. Above or below would cover the reply's buttons that line up with the light.
     */
    BesideEdge,
}

/**
 * One sentence anchored to a control (Dart `KitTip`; plan 06 §7.2, owner rule "teach what is hidden"), shown while
 * [visible]. It never blocks: it takes no focus, any touch fades it, and it fades by itself after [holdMs]; each end
 * calls [onDismissed] once. Above the anchor when the anchor is below the top quarter, else below (inside its top edge when the anchor is taller than half the window); aligned with the
 * anchor's nearer edge so it never leaves the window; at most 280 dp wide. Announced politely when it appears. Never
 * amber, never red. Which tip shows, and how often, is the teaching controller's (S2-TEACH); this is the drawing.
 *
 * States: shown, hidden
 * Text: frame
 * Cap: Popover (bubble 280)
 * Motion: fade TIP_FADE + 4 dp rise toward the anchor; Calm = fade; Off = instant (class Travel)
 * Replaces: KitTip (lib/ui/kit/kit_tip.dart)
 */
@Composable
public fun KitTip(
    message: String,
    visible: Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    holdMs: Long = TipRules.HOLD_MS,
    placement: KitTipPlacement = KitTipPlacement.Auto,
    anchor: @Composable () -> Unit,
) {
    kitAssert(message.isNotBlank()) { "KitTip: a tip is one sentence" }
    val dismiss by rememberUpdatedState(onDismissed)
    // Where the anchor sits is kept outside the snapshot state and read when the tip is about to show: an anchor in a
    // scrolling list moves every frame, and its tip part was composed again each time (jank pass 2026-11-10). [placed]
    // is written once, so a tip asked for before its anchor's first layout still finds its side.
    val bounds = remember { arrayOf(Rect.Zero) }
    var placed by remember { mutableStateOf(false) }
    val clearance = LocalKitTipClearance.current
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val above = remember(visible, placed, windowHeight) { TipRules.above(bounds[0].top, windowHeight) }
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    val anchorOnRight = remember(visible, placed, windowWidth) { bounds[0].center.x > windowWidth / 2f }
    val beside = placement == KitTipPlacement.BesideEdge
    val motion = KitMotion.state
    val specs = KitMotion.specs
    val shown = remember { MutableTransitionState(false) }
    // One rule for every tip (F-005): a tip is never drawn over an open sheet or dialog. It waits, and shows once the
    // modal has gone (its hold starts then, so the person gets the whole of it).
    val host = LocalKitSheetHost.current
    val modalOpen = host != null && (host.isShowing || host.isDialogShowing)
    val show = visible && !modalOpen
    shown.targetState = show
    LaunchedEffect(show, holdMs) {
        if (show) {
            delay(holdMs)
            dismiss()
        }
    }
    // The anchor's place while the tip shows, for the layer that draws it (read only when the tip is placed).
    var shownAt by remember { mutableStateOf(Rect.Zero) }
    val showing = shown.currentState || shown.targetState
    Box(
        modifier.onGloballyPositioned {
            val at = it.boundsInWindow()
            bounds[0] = at
            if (!placed) placed = true
            if (showing && shownAt != at) shownAt = at
        }
    ) {
        anchor()
        if (showing) {
            val gap = with(LocalDensity.current) { Space.S2.dp.roundToPx() }
            val rise = with(LocalDensity.current) { TipRules.riseDp(motion).dp.roundToPx() }
            // Never over the words of the part around the anchor (the message box): above or below all of it.
            val place = rememberTipPosition(above, gap, beside, clearance)
            val bubble: @Composable () -> Unit = {
                val enter: EnterTransition =
                    if (motion.reduced) EnterTransition.None
                    else fadeIn(specs.tipFade()) +
                        (when {
                            rise == 0 -> EnterTransition.None
                            // Toward the light: from the page side, whichever physical edge it sits on.
                            beside -> slideInHorizontally(specs.pace<IntOffset>(KitPace.Standard)) { if (anchorOnRight) -rise else rise }
                            else -> slideInVertically(specs.pace<IntOffset>(KitPace.Standard)) { if (above) -rise else rise }
                        })
                val exit: ExitTransition = if (motion.reduced) ExitTransition.None else fadeOut(specs.tipFade())
                AnimatedVisibility(shown, enter = enter, exit = exit, label = "kit.tip") {
                    KitTipBubble(message, Modifier.pointerInput(Unit) { detectTapGestures { dismiss() } })
                }
            }
            // In the window's own layer where there is one (every app screen): a tip that opened a window of its own as
            // the person began to type took the window focus from the message box and closed the keyboard (owner
            // 2026-11-10: "steals focus from composer whenever there's a tool tip"), and that window crashed the
            // emulator's renderer. Drawn in the page's window it can do neither. Outside a host, the popup as before.
            val layer = LocalKitMenuLayer.current
            if (layer != null) TipInLayer(layer, place, { shownAt.takeIf { it != Rect.Zero } ?: bounds[0] }, bubble)
            else Popup(popupPositionProvider = place, onDismissRequest = { dismiss() }, properties = PopupProperties(focusable = false, dismissOnClickOutside = true)) { bubble() }
        }
    }
}

/** Where the tip stands; the system gesture area at the window's foot is not its room, so it stands above it. */
@Composable
private fun rememberTipPosition(above: Boolean, gap: Int, beside: Boolean, clearance: KitTipClearance?): TipPosition {
    val footInset = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    return remember(above, gap, beside, clearance, footInset) { TipPosition(above, gap, beside, clearance, footInset) }
}

/**
 * The tip's bubble in the window's menu layer: placed by [place] against the anchor's bounds, in the layer's own
 * pixels, following the anchor while it moves (the keyboard opening). The layer takes no touch for it: only the bubble
 * answers a tap; a touch elsewhere goes to the page, and the tip leaves by itself or when its moment ends.
 */
@Composable
private fun TipInLayer(layer: KitMenuLayerState, place: TipPosition, anchorInWindow: () -> Rect, bubble: @Composable () -> Unit) {
    val entry = remember { MenuLayerEntry() }
    val locals = currentCompositionLocalContext
    SideEffect {
        entry.locals = locals
        entry.content = {
            Layout(content = bubble, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val placeables = measurables.map { it.measure(loose) }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val at = anchorInWindow()
                    val origin = layer.origin
                    val anchor = IntRect(at.left.toInt() - origin.x, at.top.toInt() - origin.y, at.right.toInt() - origin.x, at.bottom.toInt() - origin.y)
                    val window = IntSize(constraints.maxWidth, constraints.maxHeight)
                    val clear =
                        place.clearInWindow()?.let { c ->
                            IntRect(
                                c.left.toInt() - origin.x, c.top.toInt() - origin.y,
                                c.right.toInt() - origin.x, c.bottom.toInt() - origin.y,
                            )
                        }
                    placeables.forEach { p -> p.place(place.at(anchor, clear, window, IntSize(p.width, p.height))) }
                }
            }
        }
    }
    DisposableEffect(layer, entry) {
        layer.entries += entry
        onDispose { layer.entries -= entry }
    }
}

/**
 * The tip's look: the sentence on the panel surface with a hairline edge, at most 280 dp, wrapping at large text. A
 * polite live region.
 *
 * States: one
 * Text: frame
 * Cap: Popover (280)
 * Motion: none of its own
 * Replaces: KitTipBubble (lib/ui/kit/kit_tip.dart)
 */
@Composable
public fun KitTipBubble(message: String, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val shape = RoundedCornerShape(FlutterKitTokens.POPOVER_CORNER.dp) // F: lib/ui/kit/kit_tip.dart:239 lib/ui/kit/kit_tokens.dart:137
    // glass-truth 2026-11-16: the tip was surface2 with a hairline in every style. Under Glass it is Glass's floating
    // frost through the resolver. Not liquid: it holds a sentence on a pane about 50 dp tall, where a lens's rim (a
    // menu's reaches 26 dp) would reach its middle and lay the page's bright content under the words (seen in the desktop
    // pictures). Brutal and Paper dress it; Solid, Soft and Signal keep its own face.
    val styled = kitStyledFace(KitSurfaceInput(SurfaceRole.Floating, KitShapeId.Panel, SurfaceLevel.Surface2, ownFill = colors.surface2, radius = FlutterKitTokens.POPOVER_CORNER.dp))
    Box(
        modifier
            .testTag("kit-tip")
            .widthIn(max = TipRules.MAX_WIDTH_DP.dp)
            .then(
                styled?.let { Modifier.kitSurface(it) } ?: Modifier.background(colors.surface2, shape).border(kitHairline(), colors.hairline, shape)
            )
            .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        KitText(message, role = TypeRole.Secondary, tone = TextTone.Primary, kind = KitTextKind.Frame)
    }
}

/**
 * Above or below the anchor, aligned with the anchor's nearer edge, kept inside the window; inside a tall anchor
 * ([TipRules.inside]) at its top edge plus the gap, never on its far side.
 */
private class TipPosition(
    private val above: Boolean,
    private val gap: Int,
    private val beside: Boolean = false,
    /** The part the tip stands clear of ([KitTipClearance], window pixels); null: none. */
    private val clearance: KitTipClearance? = null,
    /** The system navigation or gesture area at the window's foot, px: the tip never reaches into it. */
    private val footInset: Int = 0,
) : PopupPositionProvider {
    fun clearInWindow(): Rect? = clearance?.boundsInWindow

    // The popup's anchor bounds are window pixels, as the clearance is.
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        at(anchorBounds, clearInWindow()?.roundToIntRect(), windowSize, popupContentSize)

    /** The tip's top-left for [anchorBounds], standing above or below all of [clear] when it is given (same space). */
    fun at(anchorBounds: IntRect, clear: IntRect?, windowSize: IntSize, popupContentSize: IntSize): IntOffset {
        if (beside) {
            val (bx, by) =
                TipRules.besideEdge(
                    anchorBounds.left, anchorBounds.top, anchorBounds.right, anchorBounds.bottom,
                    windowSize.width, windowSize.height, popupContentSize.width, popupContentSize.height, gap,
                )
            return IntOffset(bx, by)
        }
        val around = clear?.takeIf { it.width > 0 && it.height > 0 }
        val top = around?.let { minOf(it.top, anchorBounds.top) } ?: anchorBounds.top
        val bottom = around?.let { maxOf(it.bottom, anchorBounds.bottom) } ?: anchorBounds.bottom
        val nearStart = anchorBounds.center.x < windowSize.width / 2
        val x =
            (if (nearStart) anchorBounds.left else anchorBounds.right - popupContentSize.width)
                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        // Below a part kept clear there may be no room (a message box at the foot of the window, the gesture area under
        // it): clamped into the window the tip would lie back over that part, so it stands above it instead.
        val usableBottom = windowSize.height - footInset
        val noRoomBelow = around != null && bottom + gap + popupContentSize.height > usableBottom
        val y =
            // Inside a tall anchor only when no part is to be kept clear: then it stands outside that part.
            when {
                around == null && TipRules.inside(anchorBounds.height.toFloat(), windowSize.height.toFloat()) ->
                    anchorBounds.top + gap
                above || noRoomBelow -> top - gap - popupContentSize.height
                else -> bottom + gap
            }.coerceIn(0, (usableBottom - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}
