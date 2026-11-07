package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokenRoles
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.action.rememberKitCopyAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.motion.KitChevron
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopied
import io.github.eslamasabry.oc.ui.words.res.kitCopyAll
import io.github.eslamasabry.oc.ui.words.res.kitCopyValue
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetailsHide
import io.github.eslamasabry.oc.ui.words.res.kitDetailsShowAll
import io.github.eslamasabry.oc.ui.words.res.kitDetailsValueSpoken
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One technical value a person may need but never reads first (Dart `KitTechnicalValue`): what it is in words, and the
 * value exactly, already redacted. [spoken] is how a screen reader says it ("laptop dot local, port 4096").
 */
@Immutable
public data class KitTechnicalValue(
    val label: String,
    val value: Redacted,
    val copyable: Boolean = true,
    val spoken: String? = null,
)

/** Lines of the raw text shown before "Show all" unfolds the rest in place (Dart `textPreviewLines`). */
public const val KIT_DETAILS_PREVIEW_LINES: Int = 12 // F: lib/ui/kit/kit_details_fold.dart:94

/** The fold's rules, apart from the drawing, so they are tested on their own (Dart `KitDetailsFold`). */
public object KitDetailsRules {
    /** Nothing to fold (no values, notes, text or child): the fold draws nothing (Dart `isEmpty`). */
    public fun isEmpty(values: List<KitTechnicalValue>, notes: List<String>, text: String?, hasChild: Boolean): Boolean =
        values.isEmpty() && notes.isEmpty() && (text == null || text.isBlank()) && !hasChild

    /** A value shows once, under the first label that carried it. */
    public fun unique(values: List<KitTechnicalValue>): List<KitTechnicalValue> = values.distinctBy { it.value.text }

    /** "Copy all" shows for two or more copyable values, or any raw text. */
    public fun showsCopyAll(values: List<KitTechnicalValue>, text: String?): Boolean =
        unique(values).count { it.copyable } >= 2 || text != null // F: lib/ui/kit/kit_details_fold.dart:338

    /** What "Copy all" copies: `label: value` lines, a blank line, then the raw text; redacted again as a whole. */
    public fun copyAll(values: List<KitTechnicalValue>, text: String?): String {
        val lines = unique(values).filter { it.copyable }.map { "${it.label}: ${KitRedact.text(it.value.text)}" }
        return KitRedact.text(listOfNotNull(lines.takeIf { it.isNotEmpty() }?.joinToString("\n"), text).joinToString("\n\n"))
    }

    /** The raw text split for the preview: the first [KIT_DETAILS_PREVIEW_LINES] lines and the rest (null when uncut). */
    public fun preview(text: String, whole: Boolean): Pair<String, String?> {
        val lines = text.split('\n')
        if (whole || lines.size <= KIT_DETAILS_PREVIEW_LINES) return text to null
        return lines.take(KIT_DETAILS_PREVIEW_LINES).joinToString("\n") to lines.drop(KIT_DETAILS_PREVIEW_LINES).joinToString("\n")
    }

    /** "Address" → "address" for "Copy address"; an acronym ("URL", "PID") keeps its case (Dart `_lowerFirst`). */
    public fun lowerFirst(s: String): String {
        if (s.isEmpty()) return s
        if (s.length > 1 && s[1] != s[1].lowercaseChar()) return s
        return s.substring(0, 1).lowercase() + s.substring(1)
    }
}

/**
 * The one technical fold (Dart `KitDetailsFold`; K2 §1.8, §4.3; KIT-33): a 48 dp "Details" row that unfolds, in order,
 * plain [notes], the [values], the raw [text] and a richer [child] (a log panel or a code block) on the details
 * surface, then "Copy all". It holds the technical truth a person may need but never reads first, sits last on its
 * page or sheet, and shows each value once, mono, left to right in every language, selectable and copyable. Every
 * value and the raw text pass through [KitRedact] before they are shown or copied. Nothing to show draws nothing.
 *
 * [expanded] makes it controlled: the host owns the open state and changes it on [onExpansionChanged].
 *
 * States: collapsed, open, empty
 * Text: reading
 * Cap: Reading
 * Motion: unfolds with KitReveal (STANDARD in, QUICK out); the chevron turns; Off = instant
 * Hidden: none
 * Replaces: KitDetailsFold, KitTechnicalValue (lib/ui/kit/kit_details_fold.dart)
 */
@Composable
public fun KitDetailsFold(
    label: String,
    copyLabel: String,
    modifier: Modifier = Modifier,
    values: List<KitTechnicalValue> = emptyList(),
    notes: List<String> = emptyList(),
    text: Redacted? = null,
    showAllLabel: ((lines: Int) -> String)? = null,
    initiallyExpanded: Boolean = false,
    child: (@Composable () -> Unit)? = null,
    expanded: Boolean? = null,
    onExpansionChanged: ((Boolean) -> Unit)? = null,
) {
    if (KitDetailsRules.isEmpty(values, notes, text?.text, child != null)) return
    var own by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val open = expanded ?: own
    val toggle: () -> Unit = {
        val next = !open
        if (expanded == null) own = next
        onExpansionChanged?.invoke(next)
    }
    Column(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().testTag("kit-details-fold")) { // F: lib/ui/kit/kit_details_fold.dart:121
        FoldToggle(label = label.ifBlank { stringResource(Res.string.kitDetails) }, expanded = open, onTap = toggle)
        KitReveal(open) {
            DetailsContent(values, notes, text?.text, wholeText = false, copyLabel, showAllLabel, child)
        }
    }
}

/**
 * A standalone raw error (Dart `showKitTechnicalDetails`): a [KitSheet] titled [title] ("Couldn't send") with the
 * fold's content already open, the [text] whole (no preview cap), the [values], and "Copy all". It replaces every
 * raw-error dialog (§4.3). Shown while [visible]; the host sets it false on [onDismissRequest].
 *
 * States: closed, open
 * Text: frame (title), reading (body)
 * Cap: Sheet
 * Motion: the sheet's own
 * Hidden: none
 * Replaces: showKitTechnicalDetails (lib/ui/kit/kit_details_fold.dart)
 */
@Composable
public fun KitTechnicalDetailsSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    text: Redacted,
    values: List<KitTechnicalValue> = emptyList(),
    notes: List<String> = emptyList(),
    closeLabel: String? = null,
) {
    KitSheet(visible = visible, onDismissRequest = onDismissRequest, title = title, closeLabel = closeLabel) {
        DetailsContent(values, notes, text.text, wholeText = true, stringResource(Res.string.kitCopyAll), null, null)
    }
}

/**
 * The fold's 48 dp tertiary row: the words in `text2` at the start, the kit's one disclosure chevron at the end; the
 * press fills it to the details radius.
 */
@Composable
private fun FoldToggle(label: String, expanded: Boolean, onTap: () -> Unit) {
    val tokens = LocalKitTokens.current
    val hint = if (expanded) stringResource(Res.string.kitDetailsHide) else stringResource(Res.string.kitDetails)
    KitTappable(
        onClick = onTap,
        modifier =
            Modifier.fillMaxWidth()
                .semantics { if (expanded) collapse { onTap(); true } else expand { onTap(); true } }
                .testTag("kit-details-toggle"),
        onClickLabel = hint,
        role = KitTappableRole.Row,
        shape = KitShapeId.Code, // the details radius, 14 dp (FlutterKitTokens.DETAILS_RADIUS = CODE_RADIUS)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp), // F: lib/ui/kit/kit_details_fold.dart:271
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitText(label, Modifier.weight(1f), role = TypeRole.Secondary, tone = TextTone.Secondary) // F: lib/ui/kit/kit_details_fold.dart:276-278
            Spacer(Modifier.width(Space.S2.dp)) // F: lib/ui/kit/kit_details_fold.dart:281
            KitChevron(expanded)
        }
    }
}

/** What the fold unfolds (and what the sheet shows open): notes, values, text and child on the details surface. */
@Composable
private fun DetailsContent(
    values: List<KitTechnicalValue>,
    notes: List<String>,
    raw: String?,
    wholeText: Boolean,
    copyLabel: String,
    showAllLabel: ((lines: Int) -> String)?,
    child: (@Composable () -> Unit)?,
) {
    val roles = LocalKitRoles.current
    val unique = KitDetailsRules.unique(values)
    val text = raw?.takeIf { it.isNotBlank() }?.let { KitRedact.text(it) }
    val window = LocalKitWindow.current
    val wide = window.cls.isWide && !window.short // Dart KitLayout.windowOf(context).isWide
    val sections = buildList<@Composable () -> Unit> {
        if (notes.isNotEmpty()) {
            add {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) { // F: lib/ui/kit/kit_details_fold.dart:356
                    notes.forEach { KitText(KitRedact.text(it), role = TypeRole.Secondary) }
                }
            }
        }
        if (unique.isNotEmpty()) {
            add {
                Column {
                    unique.forEachIndexed { index, value ->
                        if (index > 0) KitDivider()
                        ValueRow(index, value, wide)
                    }
                }
            }
        }
        if (text != null) add { TextBlock(text, wholeText, showAllLabel) }
        if (child != null) add(child)
    }
    Column(Modifier.fillMaxWidth()) {
        if (sections.isNotEmpty()) {
            val fill = FlutterKitTokenRoles.detailsSurface(roles) // Dart tokens.detailsSurface
            val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Code, KitShapeId.Code, ownFill = androidx.compose.ui.graphics.Color(fill.v)))
            Column(
                Modifier.fillMaxWidth().then(look.modifier).padding(Space.S3.dp).testTag("kit-details-body"), // F: lib/ui/kit/kit_details_fold.dart:386-391
                verticalArrangement = Arrangement.spacedBy(Space.S3.dp), // F: lib/ui/kit/kit_details_fold.dart:397
            ) {
                sections.forEach { it() }
            }
        }
        if (KitDetailsRules.showsCopyAll(values, text)) {
            Spacer(Modifier.height(Space.S1.dp)) // F: lib/ui/kit/kit_details_fold.dart:405
            TertiaryInset {
                KitButton(
                    rememberKitCopyAction(copyLabel, stringResource(Res.string.kitCopied), { KitDetailsRules.copyAll(values, text) }),
                    Modifier.testTag("kit-details-copy-all"),
                    role = KitButtonRole.Tertiary,
                )
            }
        }
    }
}

/**
 * The raw text: its first lines, then "Show all {count} lines" unfolding the rest in place (unless [whole]). The text
 * stays one node (the whole text once shown), growing to its new height over the kit's reveal pace; Off = at once.
 */
@Composable
private fun TextBlock(text: String, whole: Boolean, showAllLabel: ((lines: Int) -> String)?) {
    var all by rememberSaveable { mutableStateOf(false) }
    val (head, tail) = KitDetailsRules.preview(text, whole)
    val grow = if (KitMotion.state.reduced) Modifier else Modifier.animateContentSize(KitMotion.specs.pace(KitPace.Standard))
    Column(Modifier.fillMaxWidth().then(grow)) {
        LtrValue(if (tail == null || all) text else head, Modifier.testTag("kit-details-text"))
        if (tail != null && !all) {
            val count = text.split('\n').size
            Spacer(Modifier.height(Space.S1.dp)) // F: lib/ui/kit/kit_details_fold.dart:442
            TertiaryInset {
                KitButton(
                    showAllLabel?.invoke(count) ?: pluralStringResource(Res.plurals.kitDetailsShowAll, count, count),
                    { all = true },
                    Modifier.testTag("kit-details-show-all"),
                    role = KitButtonRole.Tertiary,
                )
            }
        }
    }
}

/**
 * One value: its label and its mono value as one spoken node ("Address: 100.64.0.3, port 4096"), its copy button as its
 * own. Compact and medium windows stack the label over the value; expanded and large put the label in a 160 dp column.
 */
@Composable
private fun ValueRow(index: Int, value: KitTechnicalValue, wide: Boolean) {
    val tokens = LocalKitTokens.current
    val shown = KitRedact.text(value.value.text)
    val spoken = KitBidi.ltr(KitRedact.text(value.spoken ?: value.value.text))
    val sentence = stringResource(Res.string.kitDetailsValueSpoken, value.label, spoken)
    Row(
        Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).padding(vertical = Space.S1.dp) // F: lib/ui/kit/kit_details_fold.dart:501-503
            .testTag("kit-details-value"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One spoken node, label and value ("Address: 100.64.0.3, port 4096"); the copy button is its own.
        Box(Modifier.weight(1f).clearAndSetSemantics { contentDescription = sentence }) {
            if (wide) {
                Row {
                    Box(Modifier.width(FlutterKitTokens.DETAILS_LABEL_COLUMN.dp)) { KitText(value.label, role = TypeRole.Secondary) }
                    Spacer(Modifier.width(Space.S3.dp)) // F: lib/ui/kit/kit_details_fold.dart:490
                    Box(Modifier.weight(1f)) { LtrValue(shown) }
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    KitText(value.label, role = TypeRole.Secondary)
                    LtrValue(shown)
                }
            }
        }
        if (value.copyable) {
            Spacer(Modifier.width(Space.S2.dp)) // F: lib/ui/kit/kit_details_fold.dart:508
            CopyValueButton(
                text = shown,
                label = stringResource(Res.string.kitCopyValue, KitDetailsRules.lowerFirst(value.label)),
                modifier = Modifier.testTag("kit-details-copy-$index"),
            )
        }
    }
}

/** Dart `KitIconButton.copy(size: tokens.smallIconSize)`: the copy glyph at 20 dp that turns to a check for the hold. */
@Composable
private fun CopyValueButton(text: String, label: String, modifier: Modifier) {
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(MotionTokens.COPIED_HOLD.toLong())
            copied = false
        }
    }
    KitIconButton(
        icon = if (copied) KitIcons.check else KitIcons.copy,
        label = label,
        onClick = {
            clipboard.setText(AnnotatedString(text))
            copied = true
        },
        modifier = modifier,
        haptic = KitHaptic.Send,
        size = LocalKitTokens.current.smallIcon,
    )
}

/** A technical value laid out left to right whatever the reading direction, at the start of its row, selectable. */
@Composable
private fun LtrValue(text: String, modifier: Modifier = Modifier) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SelectionContainer { KitText(text, modifier, role = TypeRole.Mono) }
        }
    }
}

/**
 * Dart `KitInset`: a tertiary button's words line up with the text above. The kit's tertiary [KitButton] already
 * reaches its side padding into the column's edge, so this only sets it at the start.
 */
@Composable
private fun TertiaryInset(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { content() }
}
