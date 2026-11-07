package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.continueWithHidePreview
import io.github.eslamasabry.oc.ui.words.res.continueWithOlderLeftOut
import io.github.eslamasabry.oc.ui.words.res.continueWithOmittedCount
import io.github.eslamasabry.oc.ui.words.res.continueWithReadPreview
import io.github.eslamasabry.oc.ui.words.res.continueWithRecordFile
import io.github.eslamasabry.oc.ui.words.res.continueWithRecordSize
import io.github.eslamasabry.oc.ui.words.res.continueWithRedacted
import io.github.eslamasabry.oc.ui.words.res.continueWithShowPreview
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The rules of `KitHandoffRecord` that are not drawing (kit_handoff_record.dart). */
public object KitHandoffRecordRules {
    /** Most characters of the Markdown the preview draws; the record sent is whole. */
    public const val PREVIEW_LIMIT: Int = 12000 // F: lib/ui/kit/kit_handoff_record.dart:38

    /** Lines of the preview before its "more" (the preview code block's cap). */
    public const val PREVIEW_LINES: Int = 14 // F: lib/ui/kit/kit_handoff_record.dart:151

    /** The size in whole KB, rounded up, at least 1 (`(sizeBytes / 1024).ceil().clamp(1, 1 << 20)`). */
    public fun kilobytes(sizeBytes: Int): Int {
        val kb = kotlin.math.ceil(sizeBytes / 1024.0).toInt() // F: lib/ui/kit/kit_handoff_record.dart:68
        return kb.coerceIn(1, 1 shl 20) // F: lib/ui/kit/kit_handoff_record.dart:68
    }

    /** The preview's Markdown: at most [PREVIEW_LIMIT] characters, then a line with "…". */
    public fun preview(markdown: String): String =
        if (markdown.length > PREVIEW_LIMIT) markdown.substring(0, PREVIEW_LIMIT) + "\n…" else markdown
}

/**
 * The conversation record, ready to read before it is sent: its size, how much was left out, whether anything was
 * hidden, the file it will be saved as, and the Markdown itself behind "Show preview", which unfolds in place (Dart
 * `KitHandoffRecord`). A read-only review: it never edits the record.
 *
 * States: none — the preview is folded until asked. Cap: conversation (700). Motion: the fold is [KitReveal], the
 * chevron turns over QUICK; both instant under reduced motion.
 */
@Composable
public fun KitHandoffRecord(
    sizeBytes: Int,
    omittedMessages: Int,
    redacted: Boolean,
    markdown: String,
    path: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val kb = KitHandoffRecordRules.kilobytes(sizeBytes)
    val size = stringResource(Res.string.continueWithRecordSize, kb)
    val omitted =
        if (omittedMessages > 0) pluralStringResource(Res.plurals.continueWithOmittedCount, omittedMessages, omittedMessages)
        else null
    val readPreview = stringResource(Res.string.continueWithReadPreview)
    val caution = if (redacted) stringResource(Res.string.continueWithRedacted) + " " + readPreview else readPreview
    val toggle = stringResource(if (open) Res.string.continueWithHidePreview else Res.string.continueWithShowPreview)
    val turn by animateFloatAsState(if (open) HALF_TURN else 0f, KitMotion.specs.quick(), label = "handoff-chevron")
    Column(
        modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth().testTag("handoff-record"),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitText(listOfNotNull(size, omitted).joinToString(" · "), role = TypeRole.Secondary, tone = TextTone.Secondary)
        if (omittedMessages > 0) {
            KitText(stringResource(Res.string.continueWithOlderLeftOut), role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        KitText(caution, role = TypeRole.Secondary, tone = TextTone.Secondary)
        KitText(stringResource(Res.string.continueWithRecordFile, path), role = TypeRole.Mono, tone = TextTone.Secondary)
        // The fold's reveal adds its own space2 above the preview, so it sits outside the spaced column's gaps.
        Column(Modifier.fillMaxWidth()) {
            KitTappable(
                onClick = { open = !open },
                role = KitTappableRole.Row,
                // The fold's line is a row across the record, not a button (LAYOUT-RULES.md §2).
                modifier = Modifier.fillMaxWidth().kitEdge(KitEdge.Bleed).testTag("handoff-record-toggle").semantics {
                    contentDescription = toggle
                    selected = open
                },
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp), verticalAlignment = Alignment.CenterVertically) {
                    KitText(toggle, Modifier.weight(1f), role = TypeRole.Label, tone = TextTone.Accent)
                    // At rest a half-turned caret is the up caret (the glyph is symmetric), drawn upright; it turns only
                    // while the fold moves.
                    if (turn == HALF_TURN) KitIcon(KitIcons.chevronUp, size = KitIconSize.Small)
                    else KitIcon(KitIcons.chevronDown, Modifier.rotate(turn * DEGREES_PER_TURN), size = KitIconSize.Small)
                }
            }
            KitReveal(visible = open) {
                KitCodeBlock(
                    source = KitHandoffRecordRules.preview(markdown),
                    kind = KitCodeKind.Code,
                    modifier = Modifier.padding(top = Space.S2.dp).testTag("handoff-record-preview"),
                    maxLines = KitHandoffRecordRules.PREVIEW_LINES,
                    wrap = true,
                )
            }
        }
    }
}

private const val HALF_TURN = 0.5f // F: lib/ui/kit/kit_handoff_record.dart:131
private const val DEGREES_PER_TURN = 360f // NV: degrees in one turn (Flutter's AnimatedRotation counts turns)
