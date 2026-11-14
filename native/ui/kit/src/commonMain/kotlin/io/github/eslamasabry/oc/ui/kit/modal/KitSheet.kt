package io.github.eslamasabry.oc.ui.kit.modal

import io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardPadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitDressedLook
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import io.github.eslamasabry.oc.ui.kit.foundation.kitPaneInset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import io.github.eslamasabry.oc.ui.kit.frame.kitScrollEdges
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassSheet
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBudget
import io.github.eslamasabry.oc.ui.kit.effects.LiquidMotion
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidBudget
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidOverModal
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidPage
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidSheetFace
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidSource
import io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidSource
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.LocalKitGrowTitle
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowMotion
import io.github.eslamasabry.oc.ui.kit.motion.kitMorphTarget
import io.github.eslamasabry.oc.ui.kit.state.KitLoadingBar
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitConfirmKeepEditing
import io.github.eslamasabry.oc.ui.words.res.kitDiscardBody
import io.github.eslamasabry.oc.ui.words.res.kitDiscardConfirm
import io.github.eslamasabry.oc.ui.words.res.kitDiscardTitle
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.kitSheetLoading
import org.jetbrains.compose.resources.stringResource

/** How tall a sheet is (Dart `KitSheetHeight`). */
public enum class KitSheetHeight {
    Content,
    Half,
    Full,
}

/** The tone of a sheet header's icon tile (Dart `KitSheetTone`). */
public enum class KitSheetTone {
    Neutral,
    Attention,
}

/** Where a sheet sits for the window it is in (plan 06 §7.4, §10.1). */
public enum class KitSheetPlacement {
    Bottom,
    Centred,
    End,
}

/** Plan 06 §7.4 and §10.1: sizes that decide where a sheet sits and how wide it is. */
public object SheetLayout {
    /** Compact and medium windows keep the bottom sheet, capped at 640 dp. */
    public const val BOTTOM_MAX_DP: Int = 640

    /**
     * From an expanded window (840 dp) a sheet is a centred 560 dp panel; `Full` is a 440 dp end
     * sheet.
     */
    public const val EXPANDED_FROM_DP: Int = 840
    public const val CENTRED_MAX_DP: Int = 560
    public const val END_WIDTH_DP: Int = 440

    /** A content sheet never covers more than 90 % of the window's height. */
    public const val CONTENT_MAX_SHARE: Float = 0.9f

    public fun placement(windowWidthDp: Float, height: KitSheetHeight): KitSheetPlacement =
        when {
            windowWidthDp < EXPANDED_FROM_DP -> KitSheetPlacement.Bottom
            height == KitSheetHeight.Full -> KitSheetPlacement.End
            else -> KitSheetPlacement.Centred
        }

    /** A dismiss attempt on a sheet with unsaved input and no draft asks first (B-KIT-032). */
    public fun asksBeforeClosing(dirty: Boolean, draft: Boolean): Boolean = dirty && !draft
}

/**
 * Where a screen's sheets and dialogs render: in-window, above the screen's content (plan 06 K9,
 * §7.4). `KitScreen` places one; `:ui:shell` places one root host for app-level overlays. It holds
 * one sheet at a time (a second replaces the first: no sheet on a sheet) and, above it, one dialog
 * (confirm, input, alert, the discard question).
 */
@Stable
public class KitSheetHostState internal constructor() {
    internal var entry: SheetEntry? by mutableStateOf(null)
        private set

    internal val shown: MutableTransitionState<Boolean> = MutableTransitionState(false)

    internal var dialog: DialogEntry? by mutableStateOf(null)
        private set

    internal val dialogShown: MutableTransitionState<Boolean> = MutableTransitionState(false)

    internal fun show(e: SheetEntry) {
        entry = e
        shown.targetState = true
    }

    internal fun hide(e: SheetEntry) {
        if (entry === e) shown.targetState = false
    }

    internal fun showDialog(d: DialogEntry) {
        dialog = d
        dialogShown.targetState = true
    }

    internal fun hideDialog(d: DialogEntry) {
        if (dialog === d) dialogShown.targetState = false
    }

    internal fun releaseDialog(d: DialogEntry) {
        if (dialog === d && dialogShown.isIdle && !isDialogShowing) dialog = null
    }

    /** True while a sheet is up or animating (tests and `KitScreenAudit`). */
    public val isShowing: Boolean
        get() = shown.currentState || shown.targetState

    /** True while a dialog is up or animating. */
    public val isDialogShowing: Boolean
        get() = dialogShown.currentState || dialogShown.targetState
}

internal class SheetEntry {
    var title: String by mutableStateOf("")
    var subtitle: String? by mutableStateOf(null)
    var closeLabel: String? by mutableStateOf(null)
    var icon: ImageVector? by mutableStateOf(null)
    var tone: KitSheetTone by mutableStateOf(KitSheetTone.Neutral)
    var height: KitSheetHeight by mutableStateOf(KitSheetHeight.Content)
    var primary: KitAction? by mutableStateOf(null)
    var secondary: KitAction? by mutableStateOf(null)
    var tertiary: List<KitAction> by mutableStateOf(emptyList())
    var dirty: Boolean by mutableStateOf(false)
    var draft: Boolean by mutableStateOf(false)
    var dismissible: Boolean by mutableStateOf(true)
    var framed: Boolean by mutableStateOf(false)
    var loading: Boolean by mutableStateOf(false)
    var showClose: Boolean by mutableStateOf(true)
    var leading: KitAction? by mutableStateOf(null)
    var secondaryDismisses: Boolean by mutableStateOf(false)
    var grows: Boolean by mutableStateOf(false)
    var morph: Any? by mutableStateOf(null)
    var fills: Boolean by mutableStateOf(false)
    var answerPair: Boolean by mutableStateOf(false)
    var asking: Boolean by mutableStateOf(false)
    var onDismissRequest: () -> Unit by mutableStateOf({})
    var body: @Composable ColumnScope.() -> Unit by mutableStateOf({})

    /** Scrim, Close and Back all come here: unsaved input without a draft asks first. */
    fun requestDismiss() {
        if (!dismissible) return
        if (asking) { // F: lib/ui/kit/kit_sheet_modal.dart:336-339
            asking = false
            return
        }
        if (SheetLayout.asksBeforeClosing(dirty, draft)) asking = true else onDismissRequest()
    }
}

/**
 * The keyboard's height where the platform reports none (the desktop gate sets it); null reads the
 * real IME inset.
 */
internal val LocalSheetKeyboard: ProvidableCompositionLocal<Dp?> = staticCompositionLocalOf { null }

public val LocalKitSheetHost: ProvidableCompositionLocal<KitSheetHostState?> =
    staticCompositionLocalOf {
        null
    }

/** Whether the page holds focus, whether it gave it up to a modal (to be handed back), and the soft keyboard. */
private class PageFocus {
    var has: Boolean = false
    var saved: Boolean = false
    var keyboardUp: Boolean = false
    var keyboardSeen: Boolean = false
}

/** Keeps [held]'s keyboard facts current in a scope of its own, so the host is not composed again when it moves. */
@Composable
private fun PageKeyboard(held: PageFocus) {
    val up = io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardVisible()
    SideEffect {
        held.keyboardUp = up
        if (up) held.keyboardSeen = true
    }
}

/** Places the host around [content]. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
public fun KitSheetHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { KitSheetHostState() }
    // Liquid glass: the page as the sheets and menus over it see it, recorded only while one of them is liquid.
    val page = rememberKitLiquidSource()
    // One budget per window: the floating parts that may be liquid at once, and which drop to frost first.
    val budget = remember { KitLiquidBudget() }
    // Menus open in this window, above the page, its sheet and its dialog (smoothness round 2: no popup window per menu).
    val menus = remember { KitMenuLayerState() }
    // The page's live picture, for the sheets' and menus' liquid faces (they follow its every frame).
    val livePicture = remember { io.github.eslamasabry.oc.ui.kit.foundation.KitLivePicture() }
    Box(modifier) {
        CompositionLocalProvider(
            LocalKitSheetHost provides state,
            LocalKitLiquidPage provides page,
            LocalKitLiquidBudget provides budget,
            LocalKitMenuLayer provides menus,
            io.github.eslamasabry.oc.ui.kit.foundation.LocalKitLivePicture provides livePicture,
        ) {
            // The in-window modal is a sibling of the page, never a descendant of its
            // cleared semantics. Keep the page composed (drafts/scroll survive) but remove
            // its accessibility nodes while a modal owns the window, including its exit.
            // The page does not take focus while a modal is up either (words on glass 3, coordinator 2026-11-11): its
            // content is a focus group that cancels focus entering it, and the focus it held is cleared when the modal
            // is asked to show. From the moment the modal is asked to hide (its exit has only begun) page fields may take
            // focus again: Find opened from a sheet's row asks once, as the sheet leaves. When it has gone, the element
            // that had focus gets it back only if (a) the page had it when the modal showed, (b) nothing on the page has taken it since,
            // and (c) the soft keyboard was up when the modal showed, or none was ever seen (a hardware keyboard, a
            // desktop): focus on a field brings the keyboard back, which the person had put away.
            val modal = state.isShowing || state.isDialogShowing
            // Asked to show (entering or shown), not leaving; read live in the focus callbacks below.
            val holding = { state.shown.targetState || state.dialogShown.targetState }
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val pageFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            val held = remember { PageFocus() }
            PageKeyboard(held)
            // Saved when a modal is asked to show; given back once it has gone (at the end of its exit: the page's
            // own focus handling, such as a conversation's landing spot, sees the page uncovered by then).
            // A modal's own field (a dialog's input) may take focus before this effect runs: then the holder is saved
            // on the way out of the page (onExit below, as Compose's focusRestorer does).
            val save = {
                if (!held.saved) {
                    held.saved = held.keyboardUp || !held.keyboardSeen
                    if (held.saved) pageFocus.saveFocusedChild()
                }
            }
            androidx.compose.runtime.LaunchedEffect(modal) {
                if (modal) {
                    if (held.has) {
                        save()
                        focusManager.clearFocus(force = true)
                    }
                } else if (held.saved) {
                    held.saved = false
                    // The kit yields: a page field that took focus as the modal left (Find opening from a sheet's
                    // row) keeps it; the old holder gets focus back only when nothing on the page has it.
                    if (!held.has) pageFocus.restoreFocusedChild()
                }
            }
            Box(
                (if (modal) Modifier.clearAndSetSemantics {}
                else Modifier)
                    .onFocusChanged {
                        held.has = it.hasFocus
                        // Whatever asked for it (a requestFocus on a page field): the page takes no focus under a modal.
                        if (holding() && it.hasFocus) focusManager.clearFocus(force = true)
                    }
                    .focusRequester(pageFocus)
                    .focusProperties {
                        if (modal) onEnter = { if (holding()) cancelFocusChange() }
                        onExit = { if (holding() && held.has) save() }
                    }
                    .focusGroup()
                    .kitLiquidSource(page)
            ) {
                content()
            }
            Box(
                Modifier.zIndex(1f)
                    .then(if (state.isDialogShowing) Modifier.clearAndSetSemantics {} else Modifier)
            ) {
                // A sheet's own bar never claims the title of the page that grew from a row.
                CompositionLocalProvider(LocalKitGrowTitle provides null, LocalKitLiquidOverModal provides true) { SheetLayer(state) }
            }
            Box(Modifier.zIndex(2f)) {
                CompositionLocalProvider(LocalKitGrowTitle provides null, LocalKitLiquidOverModal provides true) { DialogLayer(state) }
            }
            Box(Modifier.zIndex(3f)) { MenuLayer(menus) }
        }
    }
}

/**
 * A sheet as screen state (Dart `showKitSheet`, `KitSheet`, `showKitFramedSheet`; plan 06 §7.4):
 * shown while [visible]. A handle, a header (an optional [icon] tile, the title ≤ 4 words and
 * [subtitle], frame text) with Close at the end, the scrolling [body] (inside a panel when
 * [framed]), then the pinned actions ([primary], [secondary], [tertiary] in one `KitActionBlock`,
 * stacking on compact). It closes from the scrim, Close and Back — including the predictive back
 * gesture, which shrinks it toward its edge as the gesture moves. With [dirty] input and no [draft]
 * keeping it, a dismiss attempt first asks "Discard your changes?". [dismissible] false keeps it
 * until answered (no Close, scrim or Back). The window decides the shape: a bottom sheet capped at
 * 640 dp; from an expanded window a centred 560 dp panel, or a 440 dp end sheet when [height] is
 * `Full`. "Closes when answered elsewhere" is the owning ViewModel's job: it sets [visible] false
 * (B-KIT-032). [growsInPlace]: one sheet whose second step needs more room changes [height] from
 * `Half` to `Full` and grows taller where it is, on the spatial spring (snap under reduced motion),
 * instead of a second sheet replacing it; from an expanded window it stays the centred panel and
 * grows there, never jumping to the end sheet.
 *
 * States: closed, open, asking (discard), predictive back, grown Text: frame (header), reading
 * (body) Cap: Sheet (640 / 560 / 440) Motion: rises on the spatial spring, scrim fades STANDARD;
 * Half → Full grows on the spatial spring; back gesture shrinks it; Off = instant Replaces:
 * showKitSheet, KitSheet, KitSheetHeight, showKitFramedSheet (lib/ui/kit/kit_sheet*.dart)
 */
@Composable
public fun KitSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    closeLabel: String? = null,
    subtitle: String? = null,
    height: KitSheetHeight = KitSheetHeight.Content,
    icon: ImageVector? = null,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    tertiary: List<KitAction> = emptyList(),
    dirty: Boolean = false,
    draft: Boolean = false,
    dismissible: Boolean = true,
    framed: Boolean = false,
    tone: KitSheetTone = KitSheetTone.Neutral,
    loading: Boolean = false,
    secondaryDismisses: Boolean = false,
    showClose: Boolean = true,
    leading: KitAction? = null,
    growsInPlace: Boolean = false,
    morphFrom: Any? = null,
    /**
     * The body lays itself out in the sheet's own height, edge to edge, instead of being one scrolling column on the
     * rail: parts of it stay put (a search field, tabs) while others scroll on their own (pages of rows). It keeps the
     * rail itself where its parts need it. Use with a `Half` or `Full` [height].
     */
    bodyFills: Boolean = false,
    /** The pinned [primary] and [secondary] as a permission card lays its answers: secondary then primary, side by side. */
    answerPair: Boolean = false,
    body: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalKitSheetHost.current
    kitAssert(host != null) { "KitSheet needs a KitSheetHost (KitScreen places one)" }
    kitAssert(title.isNotBlank()) { "KitSheet: a sheet needs its title" }
    host ?: return
    val entry = remember { SheetEntry() }
    SideEffect {
        entry.title = title
        entry.subtitle = subtitle
        entry.closeLabel = closeLabel
        entry.icon = icon
        entry.tone = tone
        entry.height = height
        entry.primary = primary
        entry.secondary = secondary
        entry.tertiary = tertiary
        entry.dirty = dirty
        entry.draft = draft
        entry.dismissible = dismissible
        entry.framed = framed
        entry.loading = loading
        entry.showClose = showClose
        entry.leading = leading
        entry.secondaryDismisses = secondaryDismisses
        entry.grows = growsInPlace
        entry.morph = morphFrom
        entry.fills = bodyFills
        entry.answerPair = answerPair
        entry.onDismissRequest = onDismissRequest
        entry.body = body
    }
    DisposableEffect(host, visible) {
        if (visible) host.show(entry)
        else {
            entry.asking = false
            host.hide(entry)
        }
        onDispose { host.hide(entry) }
    }
}

@Composable
private fun SheetLayer(state: KitSheetHostState) {
    val entry = state.entry ?: return
    val colors = LocalKitColors.current
    val specs = KitMotion.specs
    val still = ModalMotion(KitMotion.state.reduced)
    val back =
        KitPredictiveBack(enabled = state.isShowing && !state.isDialogShowing) {
            if (state.shown.targetState) entry.requestDismiss()
        }
    // The sheet under the finger (motion lead 2026-11-07): its offset, shared by the panel and the scrim.
    val drag = remember(entry) { SheetDrag() }
    AnimatedVisibility(
        state.shown,
        enter = still.enter { fadeIn(specs.enter()) },
        exit = still.exit { fadeOut(specs.exit()) },
    ) {
        Box(
            Modifier.fillMaxSize()
                // The scrim lightens as the sheet is pulled down (read in the draw phase only).
                .graphicsLayer { alpha = 1f - drag.fraction() }
                .background(colors.scrim)
                .testTag("kit-sheet-scrim")
                // Cut audit 2, finding 11: from the moment the sheet is dismissed (scrim, Back, a choice) its exit is
                // picture only. A scrim kept taking taps while it faded, and on a busy phone that lasted seconds: the
                // first tap on the page after a sheet closed did nothing.
                .then(if (state.shown.targetState) Modifier.pointerInput(entry) { detectTapGestures { entry.requestDismiss() } } else Modifier)
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A growing sheet keeps one place for both of its heights.
        val placement =
            SheetLayout.placement(
                maxWidth.value,
                if (entry.grows) KitSheetHeight.Half else entry.height,
            )
        val maxSheet = maxHeight * SheetLayout.CONTENT_MAX_SHARE
        val half = maxHeight * 0.5f
        val growth = sheetGrowth(entry, half, maxSheet)
        when (placement) {
            KitSheetPlacement.Bottom ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    // A sheet that grows out of its card (kitMorphSource) lets the morph carry it; it only fades.
                    val morphs = entry.morph != null && KitMotion.state.travels
                    AnimatedVisibility(
                        state.shown,
                        enter =
                            still.enter {
                                if (morphs) fadeIn(specs.quick())
                                else slideInVertically(specs.spatial<IntOffset>()) { it }
                            },
                        exit =
                            still.exit {
                                if (morphs) fadeOut(specs.exit())
                                else slideOutVertically(specs.exit<IntOffset>()) { it }
                            },
                    ) {
                        val sized =
                            when {
                                entry.grows && entry.height != KitSheetHeight.Content ->
                                    Modifier.grownHeight(growth)
                                entry.height == KitSheetHeight.Content ->
                                    Modifier.heightIn(max = maxSheet)
                                entry.height == KitSheetHeight.Half -> Modifier.height(half)
                                else -> Modifier.height(maxSheet)
                            }
                        SheetPanel(
                            entry,
                            Modifier.widthIn(max = SheetLayout.BOTTOM_MAX_DP.dp)
                                .fillMaxWidth()
                                .then(sized)
                                .kitMorphTarget(entry.morph, state.shown.targetState, KitGrowMotion.ROW_CORNER)
                                .onSizeChanged { drag.height = it.height }
                                .graphicsLayer { translationY = drag.offset.value }
                                .predictiveShrink(back, TransformOrigin(0.5f, 1f))
                                .clip(KitShapeId.Sheet.shape()),
                            handle = true,
                            shape = KitShapeId.Sheet,
                            open = state.shown.targetState,
                            drag = drag,
                            stillShown = { state.shown.targetState },
                            shrink = { LiquidMotion.shrinkFraction(back.value()) },
                        )
                    }
                }
            KitSheetPlacement.Centred ->
                Box(
                    Modifier.fillMaxSize().padding(Space.S6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // MOT-2: a modal never scales in; it fades and rises 8 dp at Full (KitModalMotion).
                    val rise = KitModalMotion.dialogEnter()
                    AnimatedVisibility(
                        state.shown,
                        enter = still.enter { rise },
                        exit = still.exit { fadeOut(specs.exit()) },
                    ) {
                        SheetPanel(
                            entry,
                            Modifier.widthIn(max = SheetLayout.CENTRED_MAX_DP.dp)
                                .fillMaxWidth()
                                .then(
                                    if (entry.grows && entry.height != KitSheetHeight.Content)
                                        Modifier.grownHeight(growth)
                                    else Modifier.heightIn(max = maxSheet)
                                )
                                .predictiveShrink(back, TransformOrigin.Center)
                                .clip(KitShapeId.Dialog.shape()),
                            handle = false,
                            shape = KitShapeId.Dialog,
                            open = state.shown.targetState,
                            shrink = { LiquidMotion.shrinkFraction(back.value()) },
                        )
                    }
                }
            KitSheetPlacement.End -> {
                // The end edge is the left one in Arabic: the panel comes in from, and leaves by, its own edge.
                val endSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    AnimatedVisibility(
                        state.shown,
                        enter =
                            still.enter { slideInHorizontally(specs.spatial<IntOffset>()) { endSign * it } },
                        exit = still.exit { slideOutHorizontally(specs.exit<IntOffset>()) { endSign * it } },
                    ) {
                        SheetPanel(
                            entry,
                            Modifier.width(SheetLayout.END_WIDTH_DP.dp)
                                .fillMaxHeight()
                                .predictiveShrink(back, TransformOrigin(if (endSign > 0) 1f else 0f, 0.5f)),
                            handle = false,
                            shape = KitShapeId.Square,
                            open = state.shown.targetState,
                            shrink = { LiquidMotion.shrinkFraction(back.value()) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The growing sheet's height. A change of [SheetEntry.height] (Half ↔ Full) springs on `Spatial`; a
 * change of the window (rotation, split screen) snaps, so only the person's step moves.
 */
@Composable
private fun sheetGrowth(entry: SheetEntry, half: Dp, full: Dp): Animatable<Dp, AnimationVector1D> {
    val specs = KitMotion.specs
    val target = if (entry.height == KitSheetHeight.Full) full else half
    val grow = remember(entry) { Animatable(target, Dp.VectorConverter) }
    val shown = remember(entry) { mutableStateOf(entry.height) }
    LaunchedEffect(entry, target) {
        if (shown.value != entry.height) {
            shown.value = entry.height
            grow.animateTo(target, specs.spatial())
        } else grow.snapTo(target)
    }
    return grow
}

/** Reads the growing height in the layout phase: the spring never recomposes the sheet's body. */
private fun Modifier.grownHeight(grow: Animatable<Dp, AnimationVector1D>): Modifier =
    layout { measurable, constraints ->
        val h = grow.value.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
        layout(placeable.width, h) { placeable.place(0, 0) }
    }

/**
 * Reads the back gesture in the draw phase: the modal shrinks toward its edge while the gesture
 * moves.
 */
internal fun Modifier.predictiveShrink(back: KitBackProgress, origin: TransformOrigin): Modifier =
    graphicsLayer {
        val p = back.value()
        val s = 1f - PREDICTIVE_SHRINK * p
        scaleX = s
        scaleY = s
        transformOrigin = origin
    }

/**
 * A modal that is leaving is picture only: its panel takes the presses over itself (so nothing in it acts while it
 * slides or fades away) and its scrim takes none at all, so every touch beside it is the page's at once.
 */
internal fun Modifier.modalInert(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

/**
 * The Glass face every modal panel shares (KitSheet's panels and KitDialogFrame's bottom and centred faces): liquid glass
 * over the window's page source within the window's liquid budget, else the frosted pane; null in every other style, so
 * each caller keeps its own solid face pixel for pixel. [shrink] is the predictive back gesture's way into frost.
 */
@Composable
internal fun kitModalGlassFace(shape: KitShapeId, shrink: () -> Float = { 0f }): Modifier? =
    kitLiquidSheetFace(shape.shape(), shrink, dialog = shape == KitShapeId.Dialog) ?: kitGlassSheet(shape, LocalKitColors.current.surface2)

@Composable
private fun SheetPanel(
    entry: SheetEntry,
    modifier: Modifier,
    handle: Boolean,
    shape: KitShapeId,
    /** False from the moment the sheet is dismissed: its exit is picture only ([modalInert]). */
    open: Boolean = true,
    drag: SheetDrag? = null,
    stillShown: () -> Boolean = { true },
    /** How far the predictive back gesture has shrunk the panel (0–1): liquid glass eases into frost meanwhile. */
    shrink: () -> Float = { 0f },
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    // Glass: the sheet is liquid glass over the page (Android 13+, not held), else a frosted pane (opaque over the page
    // whenever nothing blurs); every other style keeps surface2.
    // Brutal and Paper dress the panel (audit 2026-11-14): the style's edge on the paper face, Brutal's softened and
    // Paper's square corners; Brutal casts no shadow from a sheet (it lies on the scrim).
    val face =
        kitModalGlassFace(shape, shrink)
            ?: kitDressedLook(KitSurfaceInput(role = SurfaceRole.Sheet, shape = shape, level = SurfaceLevel.Surface2))?.modifier
            ?: Modifier.background(colors.surface2)
    Column(
        modifier
            .then(face)
            // Taps on the sheet never reach the scrim; a leaving sheet's controls no longer act.
            .then(if (open) Modifier.pointerInput(Unit) { detectTapGestures {} } else Modifier.modalInert())
            .semantics {
                paneTitle = entry.title
                isTraversalGroup = true
                if (entry.dismissible)
                    dismiss {
                        entry.requestDismiss()
                        true
                    }
            }
            .testTag("kit-sheet")
            .navigationBarsPadding()
            .then(
                LocalSheetKeyboard.current?.let { Modifier.padding(bottom = it) }
                    ?: Modifier.kitKeyboardPadding()
            )
            // Glass keeps its pane inset inside the sheet (owner 2026-11-09): room for the rim, sheen and lens.
            .padding(kitPaneInset())
    ) {
        if (handle) {
            val scope = rememberCoroutineScope()
            val specs = KitMotion.specs
            Box(
                Modifier.fillMaxWidth()
                    .height(tokens.handleBand.dp)
                    .semantics {
                        if (entry.dismissible)
                            dismiss {
                                entry.requestDismiss()
                                true
                            }
                    }
                    .pointerInput(entry, entry.dismissible, drag) {
                        if (drag == null) return@pointerInput
                        val tracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                scope.launch { drag.offset.snapTo(SheetDragRules.follow(drag.offset.value, amount)) }
                            },
                            onDragEnd = {
                                val velocity = tracker.calculateVelocity().y
                                val dismiss =
                                    entry.dismissible &&
                                        SheetDragRules.dismisses(drag.offset.value, drag.height, velocity, this@pointerInput)
                                if (dismiss) entry.requestDismiss()
                                scope.launch {
                                    // Gone: carry the finger's speed off the bottom edge. Kept (or asked to keep
                                    // unsaved words): settle home on the release spring, the one place a spring may
                                    // overshoot slightly.
                                    if (dismiss && !stillShown())
                                        drag.offset.animateTo(drag.height.toFloat(), specs.spatial(), velocity)
                                    else drag.offset.animateTo(0f, specs.release(), velocity)
                                }
                            },
                            onDragCancel = { scope.launch { drag.offset.animateTo(0f, specs.release()) } },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(tokens.handleWidth.dp, tokens.handleThickness.dp)
                        .clip(KitShapeId.Pill.shape())
                        .background(colors.text3)
                )
            }
        }
        // Without a handle the header's own top padding (space 3) is the air above it, once (kit_sheet.dart:574-577).
        if (entry.asking) SheetQuestion(entry)
        BoxWithConstraints(
            Modifier.weight(1f, fill = false)
                .then(if (entry.asking) Modifier.keptOffstage() else Modifier)
        ) {
            // Under a large font or the keyboard, let the header join the scroll. Pinned actions
            // keep their
            // own space and the words always have room instead of sitting under an oversized fixed
            // heading.
            val frameHeight = maxHeight
            val scrollHeader = frameHeight < 400.dp || LocalDensity.current.fontScale >= 2f
            Column {
                // A body that fills keeps the header in place at any size: there is no one scroll for it to join.
                if (!scrollHeader || entry.fills) {
                    SheetHeader(entry, handle)
                    KitLoadingBar(entry.loading, stringResource(Res.string.kitSheetLoading))
                }
                if (entry.fills)
                    Column(Modifier.weight(1f).fillMaxWidth().padding(top = Space.S2.dp).testTag("kit-sheet-fill")) {
                        androidx.compose.runtime.CompositionLocalProvider(io.github.eslamasabry.oc.ui.kit.row.LocalKitOnRail provides true) { entry.body(this) }
                    }
                else {
                val scroll = rememberScrollState()
                val fadeReach = 24.dp
                val fadePx = with(LocalDensity.current) { fadeReach.toPx() }
                val pinned = entry.primary != null || entry.secondary != null || entry.tertiary.isNotEmpty()
                // A field in the body comes to rest above the fade, and again when the keyboard resizes the body.
                val intoView = rememberSheetBodyIntoView { if (pinned) fadePx else 0f }
                androidx.compose.runtime.CompositionLocalProvider(LocalKitSheetTyping provides intoView) {
                Column(
                    Modifier.weight(1f, fill = false)
                        .fillMaxWidth()
                        // A body that runs on below the pinned buttons fades out above them (nothing is hidden: no fade).
                        .kitScrollEdges(
                            clearAbove = null,
                            topReach = 0.dp,
                            fadeFromBottom = if (pinned) ({ if (scroll.canScrollForward) fadePx else 0f }) else null,
                            bottomReach = fadeReach,
                        )
                        .then(intoView.viewport())
                        .verticalScroll(scroll)
                        .then(intoView.content()),
                    verticalArrangement = Arrangement.spacedBy(tokens.labelGap.dp),
                ) {
                    if (scrollHeader) {
                        SheetHeader(entry, handle)
                        KitLoadingBar(entry.loading, stringResource(Res.string.kitSheetLoading))
                    }
                    val hasActions =
                        entry.primary != null ||
                            entry.secondary != null ||
                            entry.tertiary.isNotEmpty()
                    Column(
                        Modifier.padding(
                            start = FlutterKitTokens.RAIL.dp,
                            top = Space.S2.dp,
                            end = FlutterKitTokens.RAIL.dp,
                            bottom = (if (hasActions) Space.S2 else FlutterKitTokens.RAIL).dp,
                        )
                    ) {
                        if (entry.framed) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(KitShapeId.Panel.shape())
                                    .background(colors.surface1)
                                    .padding(tokens.panelPadding.dp),
                                verticalArrangement = Arrangement.spacedBy(tokens.labelGap.dp),
                                content = entry.body,
                            )
                        } else androidx.compose.runtime.CompositionLocalProvider(io.github.eslamasabry.oc.ui.kit.row.LocalKitOnRail provides true) { io.github.eslamasabry.oc.ui.kit.row.KitStackScope { entry.body(this) } }
                    }
                }
                }
                }
                if (
                    entry.primary != null || entry.secondary != null || entry.tertiary.isNotEmpty()
                ) {
                    KitActionBlock(
                        Modifier.heightIn(max = (frameHeight - 64.dp).coerceAtLeast(0.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = FlutterKitTokens.RAIL.dp,
                                top = Space.S2.dp,
                                end = FlutterKitTokens.RAIL.dp,
                                bottom = Space.S4.dp,
                            ),
                        primary = entry.primary,
                        secondary = entry.secondary,
                        tertiary = entry.tertiary,
                        answerPair = entry.answerPair,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(entry: SheetEntry, handle: Boolean) {
    val tokens = LocalKitTokens.current
    // The close glyph's ink ends on the rail: rail - (minTarget - glyph) / 2 - ink inset.
    val lead = entry.leading
    val showClose =
        lead == null &&
            entry.showClose &&
            entry.dismissible &&
            !(entry.secondaryDismisses &&
                entry.secondary != null) // F: lib/ui/kit/kit_sheet_modal.dart:383-385
    val closeEnd =
        if (showClose) {
            (FlutterKitTokens.RAIL - (tokens.minTarget - 24) / 2f - 24 * 0.1875f)
                .coerceAtLeast(0f)
                .dp // F: lib/ui/kit/kit_sheet.dart:616-627 lib/ui/kit/kit_optical.dart:29,34
            // lib/ui/app_iconography.dart:20
        } else Space.S2.dp
    Column(
        Modifier.fillMaxWidth()
            .padding(
                start = if (lead != null) Space.S1.dp else FlutterKitTokens.RAIL.dp,
                top = if (handle) 0.dp else Space.S3.dp,
                end = closeEnd,
                bottom = Space.S1.dp,
            )
    ) {
        entry.icon?.let { glyph ->
            HeaderMark(glyph, entry.tone)
            Spacer(Modifier.height(Space.S3.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = if (lead != null) Alignment.CenterVertically else Alignment.Top,
        ) {
            if (lead != null)
                KitIconButton(
                    icon = lead.icon ?: KitIcons.back,
                    label = lead.semanticsLabel ?: lead.label,
                    onClick = { if (lead.runnable && !lead.working) lead.onClick?.invoke() },
                    enabled = lead.enabled,
                    working = lead.working,
                )
            Column(
                Modifier.weight(1f)
                    .padding(
                        start = if (lead != null) Space.S1.dp else 0.dp,
                        top = if (lead == null) Space.S3.dp else 0.dp,
                        end = Space.S2.dp,
                    )
            ) {
                KitText(entry.title, role = TypeRole.Title)
                entry.subtitle
                    ?.takeIf { lead == null }
                    ?.let {
                        Spacer(
                            Modifier.height((Space.S1 / 2f).dp)
                        ) // F: lib/ui/kit/kit_sheet.dart:604
                        KitText(
                            it,
                            role = TypeRole.Secondary,
                            tone = TextTone.Secondary,
                        )
                    }
            }
            if (showClose) {
                // Flutter's sheet: Close is the X glyph at the end; its words are what TalkBack
                // says.
                KitIconButton(
                    icon = KitIcons.close,
                    label = entry.closeLabel ?: stringResource(Res.string.kitSheetClose),
                    onClick = { entry.requestDismiss() },
                    // Flutter's KitIconButton (not quiet) draws its glyph in text1 (kit_icon_button.dart:295).
                )
            }
        }
        if (lead != null)
            entry.subtitle?.let {
                Column(
                    Modifier.padding(
                        start = tokens.minTarget.dp + Space.S1.dp,
                        end = Space.S2.dp,
                        bottom = Space.S1.dp,
                    )
                ) {
                    Spacer(Modifier.height((Space.S1 / 2f).dp)) // F: lib/ui/kit/kit_sheet.dart:604
                    KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
            }
    }
}

/**
 * The header mark: the bare glyph, no tile, box or circle behind it (owner rule: no icons on plates; audit 2026-11-09
 * C01). Neutral draws in `text1`, attention in the attention colour. The glyph keeps the mark's icon size
 * ([FlutterKitTokens.MARK_ICON_SIZE]) and starts on the title's rail. Tagged `kit-header-mark` for the guard.
 */
@Composable
internal fun HeaderMark(glyph: ImageVector, tone: KitSheetTone = KitSheetTone.Neutral) {
    val colors = LocalKitColors.current
    val ink = if (tone == KitSheetTone.Neutral) colors.text1 else colors.attention
    KitGlyph(glyph, ink, FlutterKitTokens.MARK_ICON_SIZE.dp, Modifier.testTag(HEADER_MARK_TAG))
}

/** A dialog's small mark beside its title ([KitAlert]): the bare glyph at the small icon size. */
@Composable
internal fun HeaderMarkSmall(glyph: ImageVector) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    KitGlyph(glyph, colors.text1, tokens.smallIcon.dp, Modifier.testTag(HEADER_MARK_TAG))
}

/** The test tag every header mark carries ([HeaderMark], [HeaderMarkSmall]). */
internal const val HEADER_MARK_TAG: String = "kit-header-mark"

/**
 * The content kept composed (its typed text stays) but out of sight, out of focus and out of the
 * reading order while the question has the sheet (Flutter's `Visibility(maintainState)` under
 * `ExcludeFocus`, `kit_sheet_modal.dart:441-449`).
 */
private fun Modifier.keptOffstage(): Modifier = layout { measurable, constraints ->
    measurable.measure(constraints)
    layout(0, 0) {}
}
    .focusProperties { canFocus = false }
    .clearAndSetSemantics {}

/**
 * "Discard your changes?" over a dirty sheet without a draft (Dart `showKitDiscardChanges`): the
 * question takes the content's place inside the sheet (`kit_sheet_modal.dart:451-474`), drawn as
 * `KitConfirmSheet` does (`kit_confirm_sheet.dart:520-560`).
 */
@Composable
private fun SheetQuestion(entry: SheetEntry) {
    val tokens = LocalKitTokens.current
    val answer = { yes: Boolean ->
        entry.asking = false
        if (yes) entry.onDismissRequest()
    }
    Column(
        Modifier.fillMaxWidth()
            .testTag("kit-sheet-question")
            .padding(
                start = FlutterKitTokens.RAIL.dp,
                top = Space.S3.dp,
                end = FlutterKitTokens.RAIL.dp,
                bottom = Space.S4.dp,
            )
            .verticalScroll(rememberScrollState())
    ) {
        HeaderMark(KitIcons.editOff)
        Spacer(Modifier.height(Space.S3.dp))
        KitText(stringResource(Res.string.kitDiscardTitle), role = TypeRole.Title)
        Spacer(Modifier.height(Space.S2.dp))
        KitText(
            stringResource(Res.string.kitDiscardBody),
            role = TypeRole.Body,
            tone = TextTone.Secondary,
        )
        Spacer(Modifier.height(Space.S5.dp))
        KitActionBlock(
            primary =
                KitAction(
                    stringResource(Res.string.kitDiscardConfirm),
                    onClick = { answer(true) },
                    destructive = true,
                    haptic = KitHaptic.Commit,
                )
        )
        Spacer(Modifier.height(Space.S3.dp)) // F: lib/ui/kit/kit_confirm_sheet.dart:610
        KitActionBlock(
            secondary =
                KitAction(
                    stringResource(Res.string.kitConfirmKeepEditing),
                    onClick = { answer(false) },
                )
        )
    }
}

/** A bottom sheet's drag: how far the finger pulled it down (px) and the panel's height. */
@Stable
internal class SheetDrag {
    val offset: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var height: Int by mutableIntStateOf(0)

    /** 0 at rest, 1 when pulled its full height: the scrim fades by it. */
    fun fraction(): Float = if (height > 0) (offset.value / height).coerceIn(0f, 1f) else 0f
}

/** Drag-to-dismiss as pure rules (motion lead 2026-11-07). Pixels and px/s; down is positive. */
public object SheetDragRules {
    /** A pull past a quarter of the sheet's height dismisses on release. */
    public const val DISMISS_SHARE: Float = 0.25f

    /** A downward flick faster than this dismisses however short (dp per second). */
    public const val FLING_DP_PER_S: Float = 1000f

    /** The sheet follows the finger 1:1 downward and never rises past its resting place (no gap under it). */
    public fun follow(offset: Float, delta: Float): Float = (offset + delta).coerceAtLeast(0f)

    public fun dismisses(offset: Float, height: Int, velocity: Float, density: Density): Boolean {
        val flingPx = with(density) { FLING_DP_PER_S.dp.toPx() }
        if (velocity > flingPx) return true
        if (velocity < -flingPx) return false
        return height > 0 && offset > height * DISMISS_SHARE
    }
}
