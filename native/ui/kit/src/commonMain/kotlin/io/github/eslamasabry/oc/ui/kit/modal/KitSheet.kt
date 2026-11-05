package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
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
    var secondaryDismisses: Boolean by mutableStateOf(false)
    var asking: Boolean by mutableStateOf(false)
    var onDismissRequest: () -> Unit by mutableStateOf({})
    var body: @Composable ColumnScope.() -> Unit by mutableStateOf({})

    /** Scrim, Close and Back all come here: unsaved input without a draft asks first. */
    fun requestDismiss() {
        if (!dismissible) return
        if (SheetLayout.asksBeforeClosing(dirty, draft)) asking = true else onDismissRequest()
    }
}

public val LocalKitSheetHost: ProvidableCompositionLocal<KitSheetHostState?> =
    staticCompositionLocalOf {
        null
    }

/** Places the host around [content]. */
@Composable
public fun KitSheetHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { KitSheetHostState() }
    Box(modifier) {
        CompositionLocalProvider(LocalKitSheetHost provides state) {
            content()
            SheetLayer(state)
            DialogLayer(state)
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
 * (B-KIT-032).
 *
 * States: closed, open, asking (discard), predictive back Text: frame (header), reading (body) Cap:
 * Sheet (640 / 560 / 440) Motion: rises on the spatial spring, scrim fades STANDARD; back gesture
 * shrinks it; Off = instant Replaces: showKitSheet, KitSheet, KitSheetHeight, showKitFramedSheet
 * (lib/ui/kit/kit_sheet*.dart)
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
        entry.secondaryDismisses = secondaryDismisses
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
    AnimatedVisibility(
        state.shown,
        enter = still.enter { fadeIn(specs.enter()) },
        exit = still.exit { fadeOut(specs.exit()) },
    ) {
        Box(
            Modifier.fillMaxSize().background(colors.scrim).testTag("kit-sheet-scrim").pointerInput(
                entry
            ) {
                detectTapGestures { entry.requestDismiss() }
            }
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val placement = SheetLayout.placement(maxWidth.value, entry.height)
        val maxSheet = maxHeight * SheetLayout.CONTENT_MAX_SHARE
        val half = maxHeight * 0.5f
        when (placement) {
            KitSheetPlacement.Bottom ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    AnimatedVisibility(
                        state.shown,
                        enter =
                            still.enter { slideInVertically(specs.spatial<IntOffset>()) { it } },
                        exit = still.exit { slideOutVertically(specs.exit<IntOffset>()) { it } },
                    ) {
                        val sized =
                            when (entry.height) {
                                KitSheetHeight.Content -> Modifier.heightIn(max = maxSheet)
                                KitSheetHeight.Half -> Modifier.height(half)
                                KitSheetHeight.Full -> Modifier.height(maxSheet)
                            }
                        SheetPanel(
                            entry,
                            Modifier.widthIn(max = SheetLayout.BOTTOM_MAX_DP.dp)
                                .fillMaxWidth()
                                .then(sized)
                                .predictiveShrink(back, TransformOrigin(0.5f, 1f))
                                .clip(KitShapeId.Sheet.shape()),
                            handle = true,
                        )
                    }
                }
            KitSheetPlacement.Centred ->
                Box(
                    Modifier.fillMaxSize().padding(Space.S6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedVisibility(
                        state.shown,
                        enter =
                            still.enter {
                                fadeIn(specs.enter()) + scaleIn(specs.enter(), initialScale = 0.96f)
                            },
                        exit = still.exit { fadeOut(specs.exit()) },
                    ) {
                        SheetPanel(
                            entry,
                            Modifier.widthIn(max = SheetLayout.CENTRED_MAX_DP.dp)
                                .fillMaxWidth()
                                .heightIn(max = maxSheet)
                                .predictiveShrink(back, TransformOrigin.Center)
                                .clip(KitShapeId.Dialog.shape()),
                            handle = false,
                        )
                    }
                }
            KitSheetPlacement.End ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    AnimatedVisibility(
                        state.shown,
                        enter =
                            still.enter { slideInHorizontally(specs.spatial<IntOffset>()) { it } },
                        exit = still.exit { slideOutHorizontally(specs.exit<IntOffset>()) { it } },
                    ) {
                        SheetPanel(
                            entry,
                            Modifier.width(SheetLayout.END_WIDTH_DP.dp)
                                .fillMaxHeight()
                                .predictiveShrink(back, TransformOrigin(1f, 0.5f)),
                            handle = false,
                        )
                    }
                }
        }
    }
    if (entry.asking) DiscardQuestion(entry)
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

@Composable
private fun SheetPanel(entry: SheetEntry, modifier: Modifier, handle: Boolean) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    Column(
        modifier
            .background(colors.surface2)
            .pointerInput(Unit) { detectTapGestures {} } // taps on the sheet never reach the scrim
            .semantics {
                paneTitle = entry.title
                isTraversalGroup = true
            }
            .testTag("kit-sheet")
            .navigationBarsPadding()
            .imePadding()
    ) {
        if (handle) {
            val density = LocalDensity.current
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
                    .pointerInput(entry, entry.dismissible) {
                        var pulled = 0f
                        detectVerticalDragGestures(
                            onDragStart = { pulled = 0f },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                pulled += amount
                            },
                            onDragEnd = {
                                if (pulled > with(density) { 48.dp.toPx() }) entry.requestDismiss()
                            },
                            onDragCancel = { pulled = 0f },
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
        } else {
            Box(Modifier.height(Space.S3.dp))
        }
        BoxWithConstraints(Modifier.weight(1f, fill = false)) {
            // Under a large font or the keyboard, let the header join the scroll. Pinned actions
            // keep their
            // own space and the words always have room instead of sitting under an oversized fixed
            // heading.
            val frameHeight = maxHeight
            val scrollHeader = frameHeight < 400.dp || LocalDensity.current.fontScale >= 2f
            Column {
                if (!scrollHeader) {
                    SheetHeader(entry, handle)
                    KitLoadingBar(entry.loading, stringResource(Res.string.kitSheetLoading))
                }
                Column(
                    Modifier.weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(tokens.labelGap.dp),
                ) {
                    if (scrollHeader) {
                        SheetHeader(entry, handle)
                        KitLoadingBar(entry.loading, stringResource(Res.string.kitSheetLoading))
                    }
                    val hasActions =
                        entry.primary != null || entry.secondary != null || entry.tertiary.isNotEmpty()
                    Column(
                        Modifier.padding(
                            start = FlutterKitTokens.RAIL.dp,
                            top = Space.S2.dp,
                            end = FlutterKitTokens.RAIL.dp,
                            bottom = (if (hasActions) Space.S2 else FlutterKitTokens.RAIL).dp,
                        ),
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
                        } else entry.body(this)
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
    val showClose = entry.dismissible && !(entry.secondaryDismisses && entry.secondary != null) // F: lib/ui/kit/kit_sheet_modal.dart:383-385
    val closeEnd =
        if (showClose) {
            (FlutterKitTokens.RAIL - (tokens.minTarget - 24) / 2f - 24 * 0.1875f).coerceAtLeast(0f).dp // F: lib/ui/kit/kit_sheet.dart:616-627 lib/ui/kit/kit_optical.dart:29,34 lib/ui/app_iconography.dart:20
        } else Space.S2.dp
    Column(
        Modifier.fillMaxWidth()
            .padding(
                start = FlutterKitTokens.RAIL.dp,
                top = if (handle) 0.dp else Space.S3.dp,
                end = closeEnd,
                bottom = Space.S1.dp,
            )
    ) {
        entry.icon?.let { glyph ->
            IconTile(glyph, entry.tone)
            Spacer(Modifier.height(Space.S3.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(top = Space.S3.dp, end = Space.S2.dp)) {
                KitText(entry.title, role = TypeRole.Title, kind = KitTextKind.Frame)
                entry.subtitle?.let {
                    Spacer(Modifier.height((Space.S1 / 2f).dp)) // F: lib/ui/kit/kit_sheet.dart:604
                    KitText(
                        it,
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                        kind = KitTextKind.Frame,
                    )
                }
            }
            if (showClose) {
                // Flutter's sheet: Close is the X glyph at the end; its words are what TalkBack says.
                KitIconButton(
                    icon = KitIcons.close,
                    label = entry.closeLabel ?: stringResource(Res.string.kitSheetClose),
                    onClick = { entry.requestDismiss() },
                    tone = TextTone.Secondary,
                )
            }
        }
    }
}

/** The header mark tile (`_KitIconTile`): the 44 dp square, neutral on `surface3`, attention tinted. */
@Composable
internal fun IconTile(glyph: ImageVector, tone: KitSheetTone = KitSheetTone.Neutral) {
    val colors = LocalKitColors.current
    val fill =
        when (tone) {
            KitSheetTone.Neutral -> colors.surface3
            KitSheetTone.Attention ->
                colors.attention.copy(alpha = FlutterKitTokens.MARK_TINT_ALPHA).compositeOver(colors.surface2)
        }
    val ink = if (tone == KitSheetTone.Neutral) colors.text1 else colors.attention
    Box(
        Modifier.size(FlutterKitTokens.MARK_SIZE.dp)
            .clip(RoundedCornerShape(FlutterKitTokens.MARK_RADIUS.dp))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        KitGlyph(glyph, ink, FlutterKitTokens.MARK_ICON_SIZE.dp)
    }
}

/** The confirm sheet's tile at a given [size] (KitConfirm keeps this call). */
@Composable
internal fun IconTile(glyph: ImageVector, size: Dp) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    Box(
        Modifier.size(size).clip(KitShapeId.Tile.shape()).background(colors.surface3),
        contentAlignment = Alignment.Center,
    ) {
        KitGlyph(glyph, colors.text1, tokens.smallIcon.dp)
    }
}

/** "Discard your changes?" over a dirty sheet without a draft (Dart `showKitDiscardChanges`). */
@Composable
private fun DiscardQuestion(entry: SheetEntry) {
    KitConfirm(
        spec =
            KitConfirmSpec(
                title = stringResource(Res.string.kitDiscardTitle),
                confirmLabel = stringResource(Res.string.kitDiscardConfirm),
                kind = KitConfirmKind.Discard,
                body = stringResource(Res.string.kitDiscardBody),
                cancelLabel = stringResource(Res.string.kitConfirmKeepEditing),
            ),
        onConfirm = {
            entry.asking = false
            entry.onDismissRequest()
        },
        onCancel = { entry.asking = false },
    )
}
