package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrameText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** Plan 09 R13: a step sits in the conversation column, capped at 700 dp, on the page gutter. */
private val ConversationColumn =
    Modifier.fillMaxWidth().widthIn(max = 700.dp).padding(horizontal = Space.S4.dp)

/** From this text scale the title, subject and detail may take two lines (Dart `_kWrapTextScale`, A11Y-8). */
private const val WRAP_TEXT_SCALE = 1.3f

/** The status words never take more than this share of the line (Dart `KitToolRow`). */
private const val END_SHARE = 0.45f

/**
 * One step of the agent's work as a line of the reply (plan 09 §9.5; Dart `KitToolRow`): no frame, no fill. The kind
 * glyph, the plain title, the subject in mono reading left to right, the detail, the status in the caller's words,
 * the duration and the "+n −n" counts. A failed step is drawn neutral (no danger colour, R8); a stopped one reads as
 * the quiet status it is.
 *
 * With a [body], the whole header is one tappable row that asks to open or fold the step ([onToggle]; the caller owns
 * [opened]); the body (a command box, a diff) is drawn only while [opened]. Without one the row is plain text.
 * [KitToolView.subject] and [KitToolView.detail] display redacted (R18).
 *
 * Text: frame (≤ 1.3×) for title, detail and status; the subject is mono, left to right.
 */
@Composable
public fun KitToolRow(
    view: KitToolView,
    opened: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    body: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    Column(modifier.then(ConversationColumn)) {
        if (body != null) {
            KitTappable(
                onClick = onToggle,
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        if (opened) {
                            collapse {
                                onToggle()
                                true
                            }
                        } else {
                            expand {
                                onToggle()
                                true
                            }
                        }
                    },
                role = KitTappableRole.Row,
                shape = KitShapeId.Tile,
            ) {
                StepLine(view, opened = opened)
            }
        } else {
            Box(Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).semantics(mergeDescendants = true) {}) {
                StepLine(view, opened = null)
            }
        }
        if (opened && body != null) {
            Box(Modifier.fillMaxWidth().padding(top = Space.S1.dp, bottom = Space.S2.dp)) { body() }
        }
    }
}

/** The header line. [opened] is null for a row that does not open (no chevron). */
@Composable
private fun StepLine(view: KitToolView, opened: Boolean?) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    // The person's text size (system × in-app), before any frame cap.
    val fontScale = (LocalKitAppDensity.current ?: LocalDensity.current).fontScale
    val wide = fontScale >= WRAP_TEXT_SCALE
    val lines = if (wide) 2 else 1
    // Glyphs grow with the text, up to the kit's icon cap (plan 06 §9).
    val glyph = (tokens.smallIcon * fontScale.coerceIn(1f, tokens.maxIconScale)).dp
    val subject = view.subject?.takeIf { it.isNotEmpty() }
    val detail = view.detail?.takeIf { it.isNotEmpty() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.S1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KitToolGlyph(view.kind, colors.text2, glyph, Modifier.padding(top = Space.S1.dp / 2))
        Spacer(Modifier.width(Space.S3.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S1.dp / 2)) {
            KitText(
                view.title,
                role = TypeRole.Secondary,
                tone = TextTone.Primary,
                kind = KitTextKind.Frame,
                maxLines = lines,
            )
            if (subject != null) {
                KitFrameText { KitMonoLtr(subject, Modifier.fillMaxWidth(), TextTone.Secondary, maxLines = lines) }
            }
            if (detail != null) {
                KitText(
                    redacted(detail),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    kind = KitTextKind.Frame,
                    maxLines = lines,
                )
            }
        }
        Spacer(Modifier.width(Space.S2.dp))
        StepEnd(view, lines, Modifier.layout { m, c ->
            val p = m.measure(c.copy(minWidth = 0, maxWidth = (c.maxWidth * END_SHARE).toInt()))
            layout(p.width, p.height) { p.place(0, 0) }
        })
        if (opened != null) {
            Spacer(Modifier.width(Space.S1.dp))
            KitFoldChevron(opened, colors.text2, tokens.smallIcon.dp, Modifier.padding(top = Space.S1.dp / 2))
        }
    }
}

/** The end of the line: the counts, then the status in words and how long it took. */
@Composable
private fun StepEnd(view: KitToolView, lines: Int, modifier: Modifier) {
    val done = view.status == KitToolStatus.Done
    val duration = view.durationLabel?.takeIf { it.isNotEmpty() }
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.S1.dp / 2)) {
        if (view.added != null || view.removed != null) Counts(view.added, view.removed)
        if (done && duration != null) {
            // A finished step shows how long it took; its status is still read out.
            KitText(
                duration,
                Modifier.semantics { stateDescription = view.statusLabel },
                role = TypeRole.Caption,
                tone = TextTone.Tertiary,
                kind = KitTextKind.Frame,
                maxLines = lines,
            )
        } else {
            KitText(
                view.statusLabel,
                role = TypeRole.Secondary,
                // LOOK-5: a failed step's word is plain text1, never a danger colour; the rest are quiet.
                tone = if (view.status == KitToolStatus.Failed) TextTone.Primary else TextTone.Secondary,
                kind = KitTextKind.Frame,
                maxLines = lines,
            )
            if (duration != null) {
                KitText(duration, role = TypeRole.Caption, tone = TextTone.Tertiary, kind = KitTextKind.Frame, maxLines = 1)
            }
        }
    }
}

/**
 * "+4 −1": the signs carry the meaning, the colours only repeat it (never colour alone, STATE-9); left to right in any
 * interface language.
 */
@Composable
private fun Counts(added: Int?, removed: Int?) {
    val colors = LocalKitColors.current
    val text =
        buildAnnotatedString {
            if (added != null) withStyle(SpanStyle(color = colors.success)) { append("+$added") }
            if (added != null && removed != null) append(' ')
            if (removed != null) withStyle(SpanStyle(color = colors.text2)) { append("−$removed") }
        }
    val style =
        LocalKitTypography.current.style(TypeRole.Secondary).copy(
            color = colors.text2,
            textDirection = TextDirection.Ltr,
            fontFeatureSettings = "tnum",
        )
    KitFrameText {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicText(text, style = style, maxLines = 1)
        }
    }
}
