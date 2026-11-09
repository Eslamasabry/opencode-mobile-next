package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInset
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitConfirmFailed
import io.github.eslamasabry.oc.ui.words.res.kitConfirmKeepEditing
import io.github.eslamasabry.oc.ui.words.res.kitConfirmKeepRunning
import io.github.eslamasabry.oc.ui.words.res.kitConfirmTypeName
import io.github.eslamasabry.oc.ui.words.res.kitConfirmTypeNameReason
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveWorking
import org.jetbrains.compose.resources.stringResource

/**
 * What a confirmation guards (Dart `KitConfirmKind`). None of them is red: a [Stop], [Destructive] or [Discard]
 * confirm is the strong neutral fill (the kit's destructive style); only a [Neutral] one is the accent primary.
 * [KitConfirmSpec.kind] has no default, so every confirm says which it is (audit 2026-11-09 C10): delete, remove,
 * clear, forget, revoke, reset, disconnect and sign out are [Destructive]; stopping running work is [Stop]; throwing
 * away unsaved edits is [Discard].
 */
public enum class KitConfirmKind {
    Neutral,
    Stop,
    Destructive,
    Discard,
}

/**
 * What to ask (Dart `showKitConfirm` arguments). [title] is a question ("Delete this server?");
 * [confirmLabel] names the act ("Delete server"), never OK / Yes / Continue / Confirm.
 * [kind] is required: it decides the confirm's style (see [KitConfirmKind]). [consequences] lists what happens;
 * [typedName], when set, must be typed exactly before the confirm turns on.
 */
@Immutable
public data class KitConfirmSpec(
    val title: String,
    val confirmLabel: String,
    val kind: KitConfirmKind,
    val body: String? = null,
    val consequences: List<String> = emptyList(),
    val typedName: String? = null,
    val cancelLabel: String? = null,
    val consequenceItems: List<KitConsequence> = emptyList(),
    val alternative: KitAction? = null,
    val details: List<KitTechnicalValue> = emptyList(),
    val icon: ImageVector? = null,
)

/** Pure rules of [KitConfirm] (B-KIT-015). */
public object ConfirmRules {
    private val vague = setOf("ok", "yes", "continue", "confirm")

    /** The title is a question, in the active language. */
    public fun titleIsQuestion(title: String): Boolean =
        title.trimEnd().let { it.endsWith("?") || it.endsWith("؟") || it.endsWith("？") }

    /** The confirm names the act. */
    public fun labelNamesTheAct(label: String): Boolean = label.trim().lowercase() !in vague

    /** With a typed name, the confirm is on only for an exact match. */
    public fun confirmEnabled(typedName: String?, typed: String): Boolean =
        typedName == null || typed == typedName

    /**
     * Enter confirms only a neutral question; a stop, destructive or discard act needs the button.
     */
    public fun enterConfirms(kind: KitConfirmKind): Boolean = kind == KitConfirmKind.Neutral

    /**
     * A confirmed stop, destructive or discard act plays `Commit` (plan 06 §4.9); a neutral one is
     * silent.
     */
    public fun haptic(kind: KitConfirmKind): KitHaptic? =
        if (kind == KitConfirmKind.Neutral) null else KitHaptic.Commit
}

/**
 * A confirmation as screen state (Dart `showKitConfirm`, `KitConfirmSheet`, `KitConsequences`; plan
 * 06 §7.4): shown while [spec] is non-null. A sheet on compact windows, a 480 dp dialog on wide
 * ones. The bare header mark above the question, its body, the consequences, the typed-name field
 * when asked, then the confirm with a secondary Cancel (Keep running for a stop, Keep editing for a
 * discard). Back, the scrim and Cancel call [onCancel]. A stop, destructive or discard confirm
 * plays `Commit` through the kit seam. Works at 200 % text: everything scrolls and the actions
 * stack.
 *
 * States: closed, open, waiting for the typed name Text: frame (title, actions), reading (body,
 * consequences) Cap: Dialog (480) / Sheet Motion: as the sheet or dialog; Off = instant Replaces:
 * showKitConfirm, KitConfirmSheet, KitConsequences, KitConsequenceMark
 * (lib/ui/kit/kit_confirm_sheet.dart)
 */
@Composable
public fun KitConfirm(
    spec: KitConfirmSpec?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    working: Boolean = false,
    failed: Boolean = false,
    /** PORT-SCREENS-2: a third answer offered beside Cancel (it cancels first, then runs). */
    alternative: KitAction? = null,
) {
    if (spec != null) {
        kitAssert(ConfirmRules.titleIsQuestion(spec.title)) {
            "KitConfirm: the title is a question ending in ?, ؟ or ？ (B-KIT-015)"
        }
        kitAssert(ConfirmRules.labelNamesTheAct(spec.confirmLabel)) {
            "KitConfirm: the confirm names the act, not OK/Yes/Continue/Confirm"
        }
    }
    val shown = spec ?: return KitDialogFrame(false, "", onCancel, sheetOnCompact = true) {}
    val typed = rememberTextFieldState()
    val on = ConfirmRules.confirmEnabled(shown.typedName, typed.text.toString())
    val confirm = { if (on && !working) onConfirm() }
    val cancelAction = { if (!working) onCancel() }
    val reason = stringResource(Res.string.kitConfirmTypeNameReason)
    val cancel =
        shown.cancelLabel
            ?: when (shown.kind) {
                KitConfirmKind.Stop -> stringResource(Res.string.kitConfirmKeepRunning)
                KitConfirmKind.Discard -> stringResource(Res.string.kitConfirmKeepEditing)
                else -> stringResource(Res.string.kitConfirmCancel)
            }
    KitDialogFrame(
        visible = true,
        title = shown.title,
        onDismissRequest = cancelAction,
        sheetOnCompact = true,
        dismissible = !working,
    ) {
        Column(
            Modifier.kitMaxWidth(CapRole.Confirm).fillMaxWidth().testTag("kit-confirm-content").onKeyEvent { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.Escape -> {
                        cancelAction()
                        true
                    }
                    event.key == Key.Enter && ConfirmRules.enterConfirms(shown.kind) -> {
                        confirm()
                        true
                    }
                    else -> false
                }
            }
        ) {
            // The bare glyph on the title's rail: no tile, box or circle behind it (audit 2026-11-09 C01).
            Box(Modifier.testTag("kit-confirm-mark")) {
                HeaderMark(
                    shown.icon
                        ?: when (shown.kind) {
                            KitConfirmKind.Neutral -> KitIcons.info
                            KitConfirmKind.Stop -> KitIcons.stop
                            KitConfirmKind.Destructive -> KitIcons.delete
                            KitConfirmKind.Discard -> KitIcons.editOff
                        }
                )
            }
            Spacer(Modifier.height(Space.S3.dp))
            KitText(
                shown.title,
                Modifier.semantics { heading() }.testTag("kit-confirm-title"),
                role = TypeRole.Title,
                kind = KitTextKind.Frame,
            )
            shown.body?.let {
                Spacer(Modifier.height(Space.S2.dp))
                KitText(it, role = TypeRole.Body, tone = TextTone.Secondary)
            }
            val consequences =
                shown.consequenceItems.ifEmpty {
                    shown.consequences.mapIndexed { index, fact ->
                        KitConsequence(
                            fact,
                            if (index == 0 && shown.kind != KitConfirmKind.Neutral)
                                KitConsequenceMark.Lost
                            else KitConsequenceMark.Info,
                        )
                    }
                }
            if (consequences.isNotEmpty()) {
                Spacer(Modifier.height(Space.S4.dp))
                KitMarkedConsequences(consequences)
            }
            shown.typedName?.let { name ->
                Spacer(Modifier.height(Space.S4.dp))
                KitField(
                    state = typed,
                    label = stringResource(Res.string.kitConfirmTypeName, name),
                    kind = KitFieldKind.Mono,
                    // InputDecoration.labelText (kit_confirm_sheet.dart:556-560): the words sit inside the empty field.
                    labelShown = false,
                    enabled =
                        KitEnabled.of(!working, stringResource(Res.string.kitTurnLiveWorking)),
                    imeAction =
                        if (ConfirmRules.enterConfirms(shown.kind)) ImeAction.Done
                        else ImeAction.None,
                    onSubmit = if (ConfirmRules.enterConfirms(shown.kind) && on) confirm else null,
                )
            }
            if (failed) {
                Spacer(Modifier.height(Space.S3.dp))
                KitNotice(
                    stringResource(Res.string.kitConfirmFailed),
                    tone = io.github.eslamasabry.oc.ui.design.color.StatusTone.Failure,
                    actions =
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.kitTryAgain),
                                confirm,
                                enabled = on && !working,
                                disabledReason =
                                    if (!on) reason
                                    else if (working) stringResource(Res.string.kitTurnLiveWorking)
                                    else null,
                            )
                        ),
                )
            }
            Spacer(Modifier.height(Space.S5.dp))
            ConfirmAnswers(
                confirm =
                    KitAction(
                        label = shown.confirmLabel,
                        onClick = confirm,
                        working = working,
                        enabled = if (on) KitEnabled.Enabled else KitEnabled.Disabled(reason),
                        destructive = shown.kind != KitConfirmKind.Neutral,
                        haptic = ConfirmRules.haptic(shown.kind),
                    ),
                cancel =
                    KitAction(
                        cancel,
                        onClick = cancelAction,
                        enabled = if (working) KitEnabled.Disabled(cancel) else KitEnabled.Enabled,
                    ),
                alternatives =
                    listOfNotNull(shown.alternative, alternative).map { action ->
                        action.copy(
                            onClick =
                                if (working) null
                                else
                                    action.onClick?.let { click ->
                                        {
                                            onCancel()
                                            click()
                                        }
                                    },
                            enabled = if (working) KitEnabled.Disabled(cancel) else action.enabled,
                        )
                    },
            )
            if (shown.details.isNotEmpty()) {
                Spacer(Modifier.height(Space.S2.dp))
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    values = shown.details,
                )
            }
        }
    }
}

/** Flutter kit_confirm_sheet.dart: compact answers stack, wide answers wrap at the window's end. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmAnswers(confirm: KitAction, cancel: KitAction, alternatives: List<KitAction>) {
    val wide = LocalKitWindow.current.modal.isWide
    if (wide) {
        Column(Modifier.fillMaxWidth()) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S3.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                KitButton(cancel, role = KitButtonRole.Secondary)
                alternatives.forEach { KitButton(it, role = KitButtonRole.Tertiary) }
                KitButton(confirm)
            }
            confirm.enabled.reason?.let {
                Spacer(Modifier.height(Space.S2.dp))
                KitText(it, role = TypeRole.Secondary)
            }
        }
    } else {
        // Flutter stretches the column and each button holds its control cap (KitMaxWidth.control, 400 dp) centred, so
        // on a medium window the confirm and Cancel sit in the middle of the sheet, not at its start edge.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { KitButton(confirm, Modifier.fillMaxWidth()) }
        confirm.enabled.reason?.let {
            Spacer(Modifier.height(Space.S2.dp))
            KitText(it, role = TypeRole.Secondary)
        }
        alternatives.forEach {
            Spacer(Modifier.height(Space.S2.dp))
            // KitInset (kit_buttons.dart:1270-1290): start-aligned at its own width. Flutter also pulls it back by the
            // tertiary padding (space 2), which would put its box inside the 16 dp page gutter the layout guard holds
            // (edge-text); the box stays on the rail (evidence/12.md).
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                KitButton(it, role = KitButtonRole.Tertiary)
            }
        }
        Spacer(Modifier.height(Space.S3.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            KitButton(cancel, Modifier.fillMaxWidth(), role = KitButtonRole.Secondary)
        }
    }
}

/** What a fact in a [KitMarkedConsequences] panel says about the thing (Dart `KitConsequenceMark`, kit_consequences.dart). */
public enum class KitConsequenceMark {
    /** A plain fact with no verdict: a small `text2` dot. */
    Neutral,

    /** Goes with the act (loses data or ends work): the strong neutral warning glyph (never red). */
    Lost,

    /** Survives the act: a `text2` check. */
    Kept,

    /** A fact: the `text2` info glyph. */
    Info,
}

/** One counted fact ("3 queued prompts will be deleted"); [icon] overrides the mark's glyph (Dart `KitConsequence`). */
@Immutable
public data class KitConsequence(
    val text: String,
    val mark: KitConsequenceMark = KitConsequenceMark.Neutral,
    val icon: ImageVector? = null,
)

/**
 * The consequences panel (Dart `KitConsequences`, lib/ui/kit/kit_consequences.dart): one inset panel (`surface1` in
 * dark, the ground in light) of one-line facts, each a small mark, space 3, then the words as a row title, with
 * hairlines between them inset to the words. The marks are hidden from screen readers; the words say the fact.
 *
 * States: none (a static list)
 * Text: reading (row title)
 * Cap: reading (720)
 * Motion: none
 * Replaces: KitConsequences, KitConsequence, KitConsequenceMark (lib/ui/kit/kit_consequences.dart)
 */
@Composable
public fun KitMarkedConsequences(items: List<KitConsequence>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val colors = LocalKitColors.current
    val small = LocalKitTokens.current.smallIcon.dp
    val hairline = kitHairline()
    KitSurfaceInset(
        modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().testTag("kit-consequences"),
        padding = KitSurfacePadding.None,
    ) {
        Column(Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                // Between two facts a hairline that takes no room (Divider height 0), starting under the words: space 4 +
                // the mark + space 3 (kit_consequences.dart:110-116); it mirrors in RTL.
                val rule =
                    if (index == 0) Modifier
                    else
                        Modifier.drawBehind {
                            val inset = (Space.S4.dp + small + Space.S3.dp).toPx()
                            val x0 = if (layoutDirection == LayoutDirection.Rtl) 0f else inset
                            val x1 = if (layoutDirection == LayoutDirection.Rtl) size.width - inset else size.width
                            drawLine(colors.hairline, Offset(x0, 0f), Offset(x1, 0f), hairline.toPx())
                        }
                Row(
                    Modifier.fillMaxWidth().then(rule).padding(horizontal = Space.S4.dp, vertical = Space.S3.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ConsequenceMark(item) // decoration: no semantics node of its own
                    Spacer(Modifier.width(Space.S3.dp))
                    KitText(item.text, Modifier.weight(1f), role = TypeRole.RowTitle)
                }
            }
        }
    }
}

/** The row's leading mark (Dart `KitConsequences._mark`): the mark's glyph, or the neutral dot drawn small as a bullet. */
@Composable
private fun ConsequenceMark(item: KitConsequence) {
    val colors = LocalKitColors.current
    val small = LocalKitTokens.current.smallIcon.dp
    if (item.icon == null && item.mark == KitConsequenceMark.Neutral) {
        // A space 2 dot (radius space 1) centred on the mark's box (kit_consequences.dart:74-85).
        val dot = colors.text2
        Spacer(Modifier.size(small).drawBehind { drawCircle(dot, radius = Space.S1.dp.toPx()) })
        return
    }
    val glyph =
        item.icon
            ?: when (item.mark) {
                KitConsequenceMark.Info -> KitIcons.info
                KitConsequenceMark.Lost -> KitIcons.warning
                KitConsequenceMark.Kept -> KitIcons.check
                KitConsequenceMark.Neutral -> KitIcons.statusDot
            }
    KitGlyph(glyph, if (item.mark == KitConsequenceMark.Lost) colors.text1 else colors.text2, small)
}

/** What will happen, one plain fact per line, in the consequences panel with the neutral dot (Dart `KitConsequences`). */
@Composable
public fun KitConsequences(lines: List<String>, modifier: Modifier = Modifier) {
    KitMarkedConsequences(lines.map { KitConsequence(it, KitConsequenceMark.Neutral) }, modifier)
}
