package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitMonoCut
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import io.github.eslamasabry.oc.ui.kit.motion.KitChevron
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMessageNoticeFailed
import org.jetbrains.compose.resources.stringResource

/** From this text scale the notice's words and its technical value stack (Flutter `_kWrapTextScale`). */
private const val WRAP_TEXT_SCALE = 1.3f // F: lib/ui/kit/chat/kit_message.dart _kWrapTextScale

/** A marker's words take at most this share of the column (kit_message.dart:1213). */
private const val MARKER_SHARE = 0.75f

/**
 * A line in the conversation about the conversation (Flutter `KitMessage.notice`, kit_message.dart `_Notice` in
 * `_Fold`): "Context added", "Skill loaded flutter-testing", "Conversation compacted". One 48 dp line: the small glyph
 * in text2 ([icon], else info; the error glyph when [failed]), space2, the words in the secondary role (primary ink
 * when failed) and an optional [technical] value in mono cut in the middle after them. With a [detail] the line opens
 * it (the kit's chevron); [action] sits at the end as a tertiary button. [expanded] null keeps its own open state.
 */
@Composable
public fun KitMessageNotice(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    technical: String? = null,
    failed: Boolean = false,
    action: KitAction? = null,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    detail: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    var own by remember { mutableStateOf(false) }
    val open = detail != null && (expanded ?: own)
    val tone = if (failed) TextTone.Primary else TextTone.Secondary
    val wide = LocalDensity.current.fontScale >= WRAP_TEXT_SCALE
    val tech = technical?.trim()?.takeIf { it.isNotEmpty() }
    val failedWord = stringResource(Res.string.kitMessageNoticeFailed)
    val label = listOfNotNull(failedWord.takeIf { failed }, text, tech).joinToString(", ")
    val glyph = if (failed) KitIcons.error else icon ?: KitIcons.info
    val toggle: () -> Unit = {
        val next = !open
        if (expanded == null) own = next
        onExpandedChange?.invoke(next)
    }
    val line: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).padding(vertical = Space.S1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitIcon(glyph, size = KitIconSize.Small, tone = TextTone.Secondary)
            Spacer(Modifier.width(Space.S2.dp))
            Box(Modifier.weight(1f)) {
                if (tech == null) {
                    KitText(text, role = TypeRole.Secondary, tone = tone)
                } else if (wide) {
                    Column {
                        KitText(text, role = TypeRole.Secondary, tone = tone)
                        KitMonoText(tech, tone = tone)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KitText(text, Modifier.weight(1f, fill = false), role = TypeRole.Secondary, tone = tone)
                        Spacer(Modifier.width(Space.S2.dp))
                        KitMonoText(tech, Modifier.weight(1f, fill = false), tone = tone, cut = KitMonoCut.Middle, maxLines = 1)
                    }
                }
            }
            if (detail != null) {
                Spacer(Modifier.width(Space.S1.dp))
                KitChevron(open)
            }
        }
    }
    val header: @Composable (Modifier) -> Unit = { m ->
        if (detail != null) {
            KitTappable(
                toggle,
                m.semantics(mergeDescendants = true) {
                    contentDescription = label
                    if (open) collapse { toggle(); true } else expand { toggle(); true }
                },
                shape = KitShapeId.Tile,
                role = KitTappableRole.Row, // the fold line is a row: full width, the 48 dp line (kit_message.dart `_Fold`)
            ) { line() }
        } else {
            Box(m.clearAndSetSemantics { contentDescription = label }) { line() }
        }
    }
    Column(modifier.then(column())) {
        if (action == null) {
            header(Modifier.fillMaxWidth())
        } else if (wide) {
            header(Modifier.fillMaxWidth())
            KitButton(action.label, { action.onClick?.invoke() }, role = KitButtonRole.Tertiary, enabled = action.enabled)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                header(Modifier.weight(1f))
                Spacer(Modifier.width(Space.S2.dp))
                KitButton(action.label, { action.onClick?.invoke() }, role = KitButtonRole.Tertiary, enabled = action.enabled)
            }
        }
        if (detail != null) {
            KitReveal(visible = open) {
                Box(Modifier.padding(top = Space.S1.dp, bottom = Space.S2.dp)) { detail() }
            }
        }
    }
}

/**
 * A change of state between turns (Flutter `KitMessage.marker`, kit_message.dart `_Marker`): "Switched to Sonnet 4",
 * "Compacting the conversation". A hairline either side of a centred caption in text2, with the small [icon] (or the
 * working mark when [working]) before it, space2 above and below; the words take at most three quarters of the column.
 */
@Composable
public fun KitMessageMarker(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    working: Boolean = false,
) {
    Box(modifier.then(column()).clearAndSetSemantics { contentDescription = text }) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp)) {
            val cap = maxWidth * MARKER_SHARE
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KitDivider(Modifier.weight(1f))
                Spacer(Modifier.width(Space.S2.dp))
                Row(Modifier.widthIn(max = cap), verticalAlignment = Alignment.CenterVertically) {
                    if (working) {
                        // KitStatusMark.working (kit_status_mark.dart:126-140): a small spinner, a still dot of the mark
                        // dot size under reduced motion, in the progress tone.
                        val colors = LocalKitColors.current
                        Box(Modifier.size(LocalKitTokens.current.smallIcon.dp), contentAlignment = Alignment.Center) {
                            if (KitMotion.state.reduced) {
                                Box(Modifier.size(FlutterKitTokens.MARK_DOT_SIZE.dp).background(colors.accent, CircleShape))
                            } else {
                                KitWorkingMark(colors.accent, size = LocalKitTokens.current.smallIcon.dp)
                            }
                        }
                        Spacer(Modifier.width(Space.S1.dp))
                    } else if (icon != null) {
                        KitIcon(icon, size = KitIconSize.Small, tone = TextTone.Secondary)
                        Spacer(Modifier.width(Space.S1.dp))
                    }
                    KitText(text, Modifier.weight(1f, fill = false), role = TypeRole.Caption, tone = TextTone.Secondary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.width(Space.S2.dp))
                KitDivider(Modifier.weight(1f))
            }
        }
    }
}
