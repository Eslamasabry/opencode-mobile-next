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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitConfirmKeepEditing
import io.github.eslamasabry.oc.ui.words.res.kitConfirmKeepRunning
import io.github.eslamasabry.oc.ui.words.res.kitConfirmTypeName
import io.github.eslamasabry.oc.ui.words.res.kitConfirmTypeNameReason
import org.jetbrains.compose.resources.stringResource

/** What a confirmation guards (Dart `KitConfirmKind`). None of them is red: the confirm is the strong neutral fill. */
public enum class KitConfirmKind { Neutral, Stop, Destructive, Discard }

/**
 * What to ask (Dart `showKitConfirm` arguments). [title] is a question ("Delete this server?"); [confirmLabel] names
 * the act ("Delete server"), never OK / Yes / Continue / Confirm. [consequences] lists what happens; [typedName], when
 * set, must be typed exactly before the confirm turns on.
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
)

/** Pure rules of [KitConfirm] (B-KIT-015). */
public object ConfirmRules {
    private val vague = setOf("ok", "yes", "continue", "confirm")

    /** The title is a question, in English or Arabic. */
    public fun titleIsQuestion(title: String): Boolean = title.trimEnd().let { it.endsWith("?") || it.endsWith("؟") }

    /** The confirm names the act. */
    public fun labelNamesTheAct(label: String): Boolean = label.trim().lowercase() !in vague

    /** With a typed name, the confirm is on only for an exact match. */
    public fun confirmEnabled(typedName: String?, typed: String): Boolean = typedName == null || typed == typedName

    /** Enter confirms only a neutral question; a stop, destructive or discard act needs the button. */
    public fun enterConfirms(kind: KitConfirmKind): Boolean = kind == KitConfirmKind.Neutral

    /** A confirmed stop, destructive or discard act plays `Commit` (plan 06 §4.9); a neutral one is silent. */
    public fun haptic(kind: KitConfirmKind): KitHaptic? = if (kind == KitConfirmKind.Neutral) null else KitHaptic.Commit
}

/**
 * A confirmation as screen state (Dart `showKitConfirm`, `KitConfirmSheet`, `KitConsequences`; plan 06 §7.4): shown
 * while [spec] is non-null. A sheet on compact windows, a 480 dp dialog on wide ones. The header mark tile, the
 * question, its body, the consequences, the typed-name field when asked, then the strong neutral confirm with Cancel
 * (Keep running for a stop, Keep editing for a discard). Back, the scrim and Cancel call [onCancel]. A stop,
 * destructive or discard confirm plays `Commit` through the kit seam. Works at 200 % text: everything scrolls and the
 * actions stack.
 *
 * States: closed, open, waiting for the typed name
 * Text: frame (title, actions), reading (body, consequences)
 * Cap: Dialog (480) / Sheet
 * Motion: as the sheet or dialog; Off = instant
 * Replaces: showKitConfirm, KitConfirmSheet, KitConsequences, KitConsequenceMark (lib/ui/kit/kit_confirm_sheet.dart)
 */
@Composable
public fun KitConfirm(spec: KitConfirmSpec?, onConfirm: () -> Unit, onCancel: () -> Unit) {
    if (spec != null) {
        kitAssert(ConfirmRules.titleIsQuestion(spec.title)) { "KitConfirm: the title is a question ending in ? or ؟ (B-KIT-015)" }
        kitAssert(ConfirmRules.labelNamesTheAct(spec.confirmLabel)) { "KitConfirm: the confirm names the act, not OK/Yes/Continue/Confirm" }
    }
    val shown = spec ?: return KitDialogFrame(false, "", onCancel, sheetOnCompact = true) {}
    val typed = rememberTextFieldState()
    val on = ConfirmRules.confirmEnabled(shown.typedName, typed.text.toString())
    val reason = stringResource(Res.string.kitConfirmTypeNameReason)
    val cancel =
        shown.cancelLabel ?: when (shown.kind) {
            KitConfirmKind.Stop -> stringResource(Res.string.kitConfirmKeepRunning)
            KitConfirmKind.Discard -> stringResource(Res.string.kitConfirmKeepEditing)
            else -> stringResource(Res.string.kitConfirmCancel)
        }
    KitDialogFrame(visible = true, title = shown.title, onDismissRequest = onCancel, sheetOnCompact = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            IconTile(
                when (shown.kind) {
                    KitConfirmKind.Neutral -> KitPartGlyphs.Ring
                    KitConfirmKind.Stop -> KitPartGlyphs.Close
                    KitConfirmKind.Destructive, KitConfirmKind.Discard -> KitPartGlyphs.Warning
                },
                (LocalKitTokens.current.iconTile + Space.S2).dp,
            )
            KitText(shown.title, Modifier.weight(1f), role = TypeRole.Title, kind = KitTextKind.Frame)
        }
        shown.body?.let { KitText(it, role = TypeRole.Body, tone = TextTone.Secondary) }
        if (shown.consequences.isNotEmpty()) KitConsequences(shown.consequences)
        shown.typedName?.let { name ->
            KitField(
                state = typed,
                label = stringResource(Res.string.kitConfirmTypeName, name),
                kind = KitFieldKind.Mono,
                imeAction = if (ConfirmRules.enterConfirms(shown.kind)) ImeAction.Done else ImeAction.None,
                onSubmit = if (ConfirmRules.enterConfirms(shown.kind) && on) onConfirm else null,
            )
        }
        KitActionBlock(
            primary =
                KitAction(
                    label = shown.confirmLabel,
                    onClick = onConfirm,
                    enabled = if (on) KitEnabled.Enabled else KitEnabled.Disabled(reason),
                    destructive = true, // the strong neutral fill for every kind (never red, never the accent)
                    haptic = ConfirmRules.haptic(shown.kind),
                ),
            tertiary = listOf(KitAction(cancel, onClick = onCancel)),
        )
    }
}

/** What will happen, one line each, with a neutral mark (Dart `KitConsequences`). */
@Composable
public fun KitConsequences(lines: List<String>, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    Column(modifier.fillMaxWidth().testTag("kit-consequences").semantics(mergeDescendants = false) {}, verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                Box(Modifier.padding(top = 9.dp).size(6.dp).clip(KitShapeId.Circle.toShape()).background(colors.text2))
                KitText(line, Modifier.weight(1f), role = TypeRole.Body)
            }
        }
    }
}
