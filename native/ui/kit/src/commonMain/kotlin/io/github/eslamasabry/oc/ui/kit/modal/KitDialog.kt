package io.github.eslamasabry.oc.ui.kit.modal
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import org.jetbrains.compose.resources.stringResource

/** Plan 06 §7.4: a dialog is an in-window centred panel at most 480 dp wide. */
public const val DIALOG_MAX_DP: Int = 480

internal class DialogEntry {
    var title: String by mutableStateOf("")
    var sheetOnCompact: Boolean by mutableStateOf(false)
    var dismissible: Boolean by mutableStateOf(true)
    var onDismissRequest: () -> Unit by mutableStateOf({})
    var content: @Composable ColumnScope.() -> Unit by mutableStateOf({})
}

/** Registers a dialog with the nearest host while [visible] (shared by [KitAlert], [KitInputDialog], [KitConfirm]). */
@Composable
internal fun KitDialogFrame(
    visible: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    sheetOnCompact: Boolean = false,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalKitSheetHost.current
    kitAssert(host != null) { "Kit dialogs need a KitSheetHost (KitScreen places one)" }
    host ?: return
    val entry = remember { DialogEntry() }
    SideEffect {
        entry.title = title
        entry.sheetOnCompact = sheetOnCompact
        entry.dismissible = dismissible
        entry.onDismissRequest = onDismissRequest
        entry.content = content
    }
    DisposableEffect(host, visible) {
        if (visible) host.showDialog(entry) else host.hideDialog(entry)
        onDispose { host.hideDialog(entry) }
    }
}

@Composable
internal fun DialogLayer(state: KitSheetHostState) {
    val entry = state.dialog ?: return
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val still = ModalMotion(KitMotion.state.reduced)
    val dismiss = { if (entry.dismissible) entry.onDismissRequest() }
    // Keep ownership through exit, including a nondismissible operation, so Back never reaches the page behind it.
    val back = KitPredictiveBack(enabled = state.isDialogShowing) {
        if (state.dialogShown.targetState) dismiss()
    }
    if (state.dialogShown.isIdle && !state.isDialogShowing) {
        SideEffect { state.releaseDialog(entry) }
    }
    AnimatedVisibility(state.dialogShown, enter = still.enter { fadeIn(specs.enter()) }, exit = still.exit { fadeOut(specs.exit()) }) {
        Box(
            Modifier.fillMaxSize().background(colors.scrim).testTag("kit-dialog-scrim").pointerInput(entry) { detectTapGestures { dismiss() } },
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bottom = entry.sheetOnCompact && maxWidth.value < SheetLayout.EXPANDED_FROM_DP
        val cap = maxHeight * SheetLayout.CONTENT_MAX_SHARE
        val panel =
            Modifier.pointerInput(Unit) { detectTapGestures { } }
                .semantics {
                    paneTitle = entry.title
                    isTraversalGroup = true
                }
                .testTag("kit-dialog")
        val body: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(tokens.gutter.dp + Space.S1.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
                content = entry.content,
            )
        }
        if (bottom) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                AnimatedVisibility(
                    state.dialogShown,
                    enter = still.enter { slideInVertically(specs.spatial<IntOffset>()) { it } },
                    exit = still.exit { slideOutVertically(specs.exit<IntOffset>()) { it } },
                ) {
                    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Sheet, KitShapeId.Sheet, SurfaceLevel.Surface2, ownFill = colors.surface2))
                    Box(
                        panel.widthIn(max = SheetLayout.BOTTOM_MAX_DP.dp).fillMaxWidth().heightIn(max = cap)
                            .predictiveShrink(back, TransformOrigin(0.5f, 1f))
                            .then(look.modifier)
                            .clip(KitShapeId.Sheet.shape())
                            .navigationBarsPadding()
                            .imePadding(),
                    ) { body() }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(Space.S6.dp).imePadding(), contentAlignment = Alignment.Center) {
                AnimatedVisibility(state.dialogShown, enter = still.enter { fadeIn(specs.enter()) + scaleIn(specs.enter(), initialScale = 0.96f) }, exit = still.exit { fadeOut(specs.exit()) }) {
                    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Floating, KitShapeId.Dialog, SurfaceLevel.Surface2, ownFill = colors.surface2))
                    Box(
                        panel.widthIn(max = DIALOG_MAX_DP.dp).fillMaxWidth().heightIn(max = cap)
                            .predictiveShrink(back, TransformOrigin.Center)
                            .then(look.modifier)
                            .clip(KitShapeId.Dialog.shape()),
                    ) { body() }
                }
            }
        }
    }
}

/**
 * A short message that needs an answer (Dart `showKitAlert`; plan 06 §7.4): an in-window centred panel, title, body,
 * then its actions. Dismissing (scrim, Back, the predictive back gesture) calls [onDismissRequest]. With no [primary]
 * the alert offers one Close.
 *
 * States: closed, open
 * Text: frame (title), reading (body)
 * Cap: Dialog (480)
 * Motion: fades and settles from 96 %; back gesture shrinks it; Off = instant
 * Replaces: showKitAlert (lib/ui/kit/kit_dialog.dart)
 */
@Composable
public fun KitAlert(
    visible: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    body: String? = null,
    primary: KitAction? = null,
    secondary: KitAction? = null,
) {
    val close = stringResource(Res.string.kitSheetClose)
    KitDialogFrame(visible, title, onDismissRequest) {
        KitText(title, role = TypeRole.Title, kind = KitTextKind.Frame)
        body?.let { KitText(it, role = TypeRole.Body, tone = TextTone.Secondary) }
        KitActionBlock(
            primary = primary,
            secondary = secondary,
            tertiary = if (primary == null && secondary == null) listOf(KitAction(close, onDismissRequest)) else emptyList(),
        )
    }
}

/** Pure rule of [KitInputDialog]: an unchanged or cancelled dialog answers null (B-KIT-016). */
public object InputDialogRules {
    public fun result(initial: String, typed: String, submitted: Boolean): String? = if (!submitted || typed == initial) null else typed
}

/**
 * One value in a dialog (Dart `showKitInputDialog`; plan 06 §7.4): the field opens focused with its words selected;
 * the keyboard's action submits; nonsecret text survives rotation within [operationKey]. Closing removes the input
 * state; changing [operationKey] starts a new operation even when two targets have the same initial text.
 * Secret input starts empty and is never saved or restored; a nonempty secret [initial] is refused.
 * [onResult] is called once: with the new text, or with null
 * when the person cancels or submits it unchanged (B-KIT-016). [validate] returns the error line, or null.
 *
 * States: closed, open, invalid
 * Text: frame (title), reading (value)
 * Cap: Dialog (480)
 * Motion: as [KitAlert]
 * Replaces: showKitInputDialog (lib/ui/kit/kit_dialog.dart)
 */
@Composable
public fun KitInputDialog(
    visible: Boolean,
    title: String,
    fieldLabel: String,
    confirmLabel: String,
    initial: String = "",
    kind: KitFieldKind = KitFieldKind.Text,
    validate: (String) -> String? = { null },
    operationKey: Any? = initial,
    onResult: (String?) -> Unit,
) {
    require(kind != KitFieldKind.Secret || initial.isEmpty()) { "Secret dialogs cannot be prefilled; use Saved / Replace for stored values" }
    if (!visible) return
    key(operationKey, kind) {
        InputDialogContent(title, fieldLabel, confirmLabel, initial, kind, validate, onResult)
    }
}

@Composable
private fun InputDialogContent(
    title: String,
    fieldLabel: String,
    confirmLabel: String,
    initial: String,
    kind: KitFieldKind,
    validate: (String) -> String?,
    onResult: (String?) -> Unit,
) {
    val state: TextFieldState =
        if (kind == KitFieldKind.Secret) remember { TextFieldState() } else rememberTextFieldState(initial)
    val focus = remember { FocusRequester() }
    val cancel = stringResource(Res.string.kitConfirmCancel)
    val typed = state.text.toString()
    val error = validate(typed)
    val submit = {
        val current = state.text.toString()
        if (validate(current) == null) onResult(InputDialogRules.result(initial, current, submitted = true))
    }
    KitDialogFrame(true, title, onDismissRequest = { onResult(null) }) {
        KitText(title, role = TypeRole.Title, kind = KitTextKind.Frame)
        KitField(
            state = state,
            label = fieldLabel,
            kind = kind,
            error = error,
            imeAction = ImeAction.Done,
            focusRequester = focus,
            onSubmit = submit,
        )
        KitActionBlock(
            primary =
                KitAction(
                    confirmLabel,
                    onClick = submit,
                    enabled = if (error == null) KitEnabled.Enabled else KitEnabled.Disabled(error),
                ),
            tertiary = listOf(KitAction(cancel, onClick = { onResult(null) })),
        )
        LaunchedEffect(Unit) {
            state.edit { selectAll() }
            runCatchingFocus { focus.requestFocus() }
        }
    }
}

/** Focus can fail when the field is not yet attached (first frame); the next open tries again. Not suspend code. */
private inline fun runCatchingFocus(block: () -> Unit) {
    try {
        block()
    } catch (_: IllegalStateException) {
        // not attached yet
    }
}
