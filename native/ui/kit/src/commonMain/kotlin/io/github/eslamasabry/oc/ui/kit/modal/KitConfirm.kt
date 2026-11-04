package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitConfirmFailed
import io.github.eslamasabry.oc.ui.words.res.kitConfirmKeepEditing
import io.github.eslamasabry.oc.ui.words.res.kitConfirmKeepRunning
import io.github.eslamasabry.oc.ui.words.res.kitConfirmTypeName
import io.github.eslamasabry.oc.ui.words.res.kitConfirmTypeNameReason
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveWorking
import org.jetbrains.compose.resources.stringResource

/**
 * What a confirmation guards (Dart `KitConfirmKind`). None of them is red: the confirm is the
 * strong neutral fill.
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
 * [consequences] lists what happens; [typedName], when set, must be typed exactly before the
 * confirm turns on.
 */
@Immutable
public data class KitConfirmSpec(
    val title: String,
    val confirmLabel: String,
    val kind: KitConfirmKind = KitConfirmKind.Neutral,
    val body: String? = null,
    val consequences: List<String> = emptyList(),
    val typedName: String? = null,
    val cancelLabel: String? = null,
    val consequenceItems: List<KitConsequence> = emptyList(),
    val alternative: KitAction? = null,
    val details: List<KitTechnicalValue> = emptyList(),
)

/** Pure rules of [KitConfirm] (B-KIT-015). */
public object ConfirmRules {
    private val vague = setOf("ok", "yes", "continue", "confirm")

    /** The title is a question, in English or Arabic. */
    public fun titleIsQuestion(title: String): Boolean =
        title.trimEnd().let { it.endsWith("?") || it.endsWith("؟") }

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
 * ones. The header mark tile, the question, its body, the consequences, the typed-name field when
 * asked, then the strong neutral confirm with Cancel (Keep running for a stop, Keep editing for a
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
) {
    if (spec != null) {
        kitAssert(ConfirmRules.titleIsQuestion(spec.title)) {
            "KitConfirm: the title is a question ending in ? or ؟ (B-KIT-015)"
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            IconTile(
                when (shown.kind) {
                    KitConfirmKind.Neutral -> KitPartGlyphs.Ring
                    KitConfirmKind.Stop -> KitPartGlyphs.Close
                    KitConfirmKind.Destructive,
                    KitConfirmKind.Discard -> KitPartGlyphs.Warning
                },
                (LocalKitTokens.current.iconTile + Space.S2).dp,
            )
            KitText(
                shown.title,
                Modifier.weight(1f),
                role = TypeRole.Title,
                kind = KitTextKind.Frame,
            )
        }
        shown.body?.let { KitText(it, role = TypeRole.Body, tone = TextTone.Secondary) }
        // Plain consequence lines keep the neutral bullet: the kit cannot tell a loss from a
        // kept fact, so only callers that say so ([KitConfirmSpec.consequenceItems]) get marks.
        if (shown.consequenceItems.isNotEmpty()) KitMarkedConsequences(shown.consequenceItems)
        else if (shown.consequences.isNotEmpty()) KitConsequences(shown.consequences)
        shown.typedName?.let { name ->
            KitField(
                state = typed,
                label = stringResource(Res.string.kitConfirmTypeName, name),
                kind = KitFieldKind.Mono,
                enabled = KitEnabled.of(!working, stringResource(Res.string.kitTurnLiveWorking)),
                imeAction =
                    if (ConfirmRules.enterConfirms(shown.kind)) ImeAction.Done else ImeAction.None,
                onSubmit = if (ConfirmRules.enterConfirms(shown.kind) && on) confirm else null,
            )
        }
        if (failed) KitNotice(stringResource(Res.string.kitConfirmFailed))
        KitActionBlock(
            modifier =
                Modifier.onKeyEvent { event ->
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
                },
            primary =
                KitAction(
                    label = shown.confirmLabel,
                    onClick = confirm,
                    working = working,
                    enabled = if (on) KitEnabled.Enabled else KitEnabled.Disabled(reason),
                    destructive =
                        true, // the strong neutral fill for every kind (never red, never the
                    // accent)
                    haptic = ConfirmRules.haptic(shown.kind),
                ),
            tertiary =
                listOfNotNull(
                    shown.alternative?.let { alternative ->
                        alternative.copy(
                            onClick =
                                if (working) null
                                else
                                    ({
                                        onCancel()
                                        alternative.onClick?.invoke()
                                    }),
                            enabled =
                                if (working) KitEnabled.Disabled(cancel) else alternative.enabled,
                        )
                    },
                    KitAction(
                        cancel,
                        onClick = cancelAction,
                        enabled = if (working) KitEnabled.Disabled(cancel) else KitEnabled.Enabled,
                    ),
                ),
        )
        if (shown.details.isNotEmpty())
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                values = shown.details,
            )
    }
}

public enum class KitConsequenceMark {
    Neutral,
    Lost,
    Kept,
    Info,
}

@Immutable
public data class KitConsequence(
    val text: String,
    val mark: KitConsequenceMark = KitConsequenceMark.Neutral,
)

/** Counted facts distinguish losses from retained data without using colour alone. */
@Composable
public fun KitMarkedConsequences(items: List<KitConsequence>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().testTag("kit-consequences"),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitText(
                    when (item.mark) {
                        KitConsequenceMark.Lost -> "−"
                        KitConsequenceMark.Kept -> "✓"
                        KitConsequenceMark.Info -> "i"
                        KitConsequenceMark.Neutral -> "•"
                    },
                    role = TypeRole.Body,
                )
                KitText(item.text, Modifier.weight(1f), role = TypeRole.Body)
            }
        }
    }
}

/** What will happen, one line each, with a neutral mark (Dart `KitConsequences`). */
@Composable
public fun KitConsequences(lines: List<String>, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    Column(
        modifier.fillMaxWidth().testTag("kit-consequences").semantics(mergeDescendants = false) {},
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        lines.forEach { line ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                Box(
                    Modifier.padding(top = 9.dp)
                        .size(6.dp)
                        .clip(KitShapeId.Circle.toShape())
                        .background(colors.text2)
                )
                KitText(line, Modifier.weight(1f), role = TypeRole.Body)
            }
        }
    }
}
