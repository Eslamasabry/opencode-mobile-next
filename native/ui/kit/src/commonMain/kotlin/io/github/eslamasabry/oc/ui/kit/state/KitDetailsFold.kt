package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitInlineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.frame.ExitGlyph
import io.github.eslamasabry.oc.ui.kit.frame.KitExitGlyph
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

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
public const val KIT_DETAILS_PREVIEW_LINES: Int = 12

/**
 * The one technical fold (Dart `KitDetailsFold`; plan 06 §7.4): a 48 dp "Details" row that unfolds, in order, plain
 * [notes], the [values] and the raw [text] — the only place technical text appears, and only redacted ([Redacted]
 * cannot be built outside the Redactor). Values and text are mono, laid out left to right in every language,
 * selectable and copyable (Copy all). It sits last and starts collapsed. Nothing to show → nothing drawn.
 *
 * States: collapsed, open, empty
 * Text: reading
 * Cap: Reading
 * Motion: unfolds STANDARD on Enter, folds QUICK; the chevron turns; Off = instant
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
) {
    val unique = values.distinctBy { it.value.text }
    if (unique.isEmpty() && notes.isEmpty() && text == null) return
    var open by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var all by rememberSaveable { mutableStateOf(false) }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val reduced = specs.state.reduced
    @Suppress("DEPRECATION") // KitCopy (K1) replaces this with the platform clip entry and the "Copied" note.
    val clipboard = LocalClipboardManager.current
    Column(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().testTag("kit-details-fold")) {
        KitTappable(
            onClick = { open = !open },
            modifier =
                Modifier.fillMaxWidth().semantics {
                    if (open) collapse { open = false; true } else expand { open = true; true }
                }.testTag("kit-details-toggle"),
            shape = KitShapeId.Button,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).padding(horizontal = Space.S1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitText(label, Modifier.weight(1f), role = TypeRole.Secondary, tone = TextTone.Secondary)
                // The chevron points to the end (mirrored in RTL), so the turn that makes it point up or down mirrors too.
                val toEnd = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
                KitExitGlyph(ExitGlyph.Chevron, colors.text2, Modifier.rotate(if (open) -90f * toEnd else 90f * toEnd))
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = if (reduced) EnterTransition.None else expandVertically(specs.enter()) + fadeIn(specs.enter()),
            exit = if (reduced) ExitTransition.None else shrinkVertically(specs.quick()) + fadeOut(specs.quick()),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface1, KitShapeId.Code.toShape())
                    .padding(Space.S3.dp)
                    .testTag("kit-details-body"),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                notes.forEach { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
                unique.forEach { v ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        KitText(v.label, role = TypeRole.Secondary, tone = TextTone.Secondary)
                        Ltr {
                            SelectionContainer {
                                KitText(
                                    v.value.text,
                                    Modifier.semantics { v.spoken?.let { contentDescription = it } }.testTag("kit-details-value"),
                                    role = TypeRole.Mono,
                                )
                            }
                        }
                    }
                }
                text?.let { t ->
                    val lines = t.text.lines()
                    val cut = !all && lines.size > KIT_DETAILS_PREVIEW_LINES
                    Ltr {
                        SelectionContainer {
                            KitText(
                                if (cut) lines.take(KIT_DETAILS_PREVIEW_LINES).joinToString("\n") else t.text,
                                Modifier.testTag("kit-details-text"),
                                role = TypeRole.Mono,
                            )
                        }
                    }
                    if (cut && showAllLabel != null) {
                        KitInlineAction(KitLineAction(showAllLabel(lines.size), { all = true }, testTag = "kit-details-show-all"))
                    }
                }
                val copyText =
                    (unique.filter { it.copyable }.map { "${it.label}: ${it.value.text}" } + listOfNotNull(text?.text))
                        .joinToString("\n")
                if (copyText.isNotEmpty()) {
                    KitInlineAction(
                        KitLineAction(copyLabel, { clipboard.setText(AnnotatedString(copyText)) }, testTag = "kit-details-copy-all"),
                    )
                }
            }
        }
    }
}

/** Technical values read left to right in every language (Dart `KitLtr`). */
@Composable
private fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) { content() }
    }
}
