package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.code.codeChunkFrame
import io.github.eslamasabry.oc.ui.kitchat.code.codeChunkStyle
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.stream.drive
import io.github.eslamasabry.oc.ui.kitchat.stream.growOnly
import io.github.eslamasabry.oc.ui.kitchat.stream.kitFreshFade

/** Block kinds the kit draws (the feature maps the engine's row kinds onto these). */
public enum class KitBlockKind {
    Prose,
    Heading,
    Quote,
    ListItem,
    Code,
}

/** Plan 09 R13: the conversation column is capped at 700 dp. */
private val Column =
    Modifier.fillMaxWidth()
        .widthIn(max = 700.dp)
        .padding(horizontal = Space.S4.dp, vertical = Space.S1.dp)

/** The same column inside a [KitTurn]: the turn gives every gap (C-001), so the row keeps no padding of its own. */
private val TurnColumn = Modifier.fillMaxWidth().widthIn(max = 700.dp).padding(horizontal = Space.S4.dp)

/** The column for a part: its own 4 dp row padding only outside a [KitTurn]. */
@Composable
internal fun column(): Modifier = if (LocalKitTurn.current) TurnColumn else Column

/** A quiet line inside an opened step's body: the step's own edge, no page gutter. */
private val StepLine = Modifier.fillMaxWidth().padding(vertical = Space.S1.dp / 2)

internal class LayoutHolder {
    var layout: TextLayoutResult? = null
}

/**
 * One Markdown block of a reply. A streamed block reveals word by word with the draw-phase fade;
 * its box grows smoothly while it is live. Settled and loaded blocks draw at full length.
 *
 * A long block (a 400-line fence, a paragraph of many lines) arrives as chunks of a few lines, each
 * its own row (STREAM-FIX): a word landing then re-lays out one chunk instead of the whole growing
 * block, and the fade's offscreen layer is one chunk tall. [continued] (a chunk after the first)
 * and [continues] (a chunk before the last) drop the row gap, the code box's rounding and its inner
 * padding on that side, so the chunks of one block read as one.
 *
 * @param revealKey the row key; @param revealGroup the part it belongs to (blocks of one part
 *   reveal in order);
 * @param streamed false for history rows (never animate).
 */
@Composable
public fun KitReplyBlock(
    text: String,
    kind: KitBlockKind,
    live: Boolean,
    streamed: Boolean,
    revealKey: String,
    revealGroup: String,
    registry: RevealRegistry,
    motion: KitMotionLevel,
    modifier: Modifier = Modifier,
    continued: Boolean = false,
    continues: Boolean = false,
) {
    val reveal =
        if (streamed) registry.stateFor(revealKey, revealGroup, live, text.length) else null
    val holder = remember { LayoutHolder() }
    val style = styleOf(kind)
    if (reveal != null) {
        val latest by rememberUpdatedState(text)
        val motionNow by rememberUpdatedState(motion)
        SideEffect { reveal.targetLength = text.length }
        LaunchedEffect(reveal) { reveal.drive({ latest }, { motionNow }) }
    }
    val shown = reveal?.shown?.coerceAtMost(text.length) ?: text.length
    val visible = if (shown >= text.length) text else text.substring(0, shown)
    val revealing = reveal != null && (live || reveal.active)
    var m = modifier.then(if (LocalKitTurn.current) TurnColumn else columnOf(continued, continues))
    if (revealing) {
        m = m.clipToBounds().growOnly(enabled = live && motion == KitMotionLevel.Full)
        if (motion == KitMotionLevel.Full)
            m = m.preferredFrameRate(FrameRateCategory.High) // §8.5, only while live
    }
    val textModifier = if (revealing) Modifier.kitFreshFade({ holder.layout }, reveal) else Modifier
    val onLayout = { it: TextLayoutResult ->
        holder.layout = it
        registry.laidOut(it.layoutInput.text.length)
    }
    if (kind == KitBlockKind.Code) {
        // P-002: a fence's rows draw on the settled code chunk's own surface, in its text style and without
        // wrapping (lines scroll sideways), so a row still revealing and a settled one beside it read as one box.
        val fill = LocalKitColors.current.surface2
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(m.codeChunkFrame(fill, continued, continues)) {
                BasicText(
                    text = visible,
                    style = codeChunkStyle().copy(textMotion = TextMotion.Static),
                    softWrap = false,
                    onTextLayout = onLayout,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).then(textModifier),
                )
            }
        }
        return
    }
    Box(m) {
        BasicText(text = visible, style = style, onTextLayout = onLayout, modifier = textModifier)
    }
}

/**
 * The column with the row gap only on the sides where the block does not continue into another
 * chunk.
 */
private fun columnOf(continued: Boolean, continues: Boolean): Modifier =
    if (!continued && !continues) {
        Column
    } else {
        Modifier.fillMaxWidth()
            .widthIn(max = 700.dp)
            .padding(
                start = Space.S4.dp,
                end = Space.S4.dp,
                top = if (continued) 0.dp else Space.S1.dp,
                bottom = if (continues) 0.dp else Space.S1.dp,
            )
    }

@Composable
private fun styleOf(kind: KitBlockKind): TextStyle {
    val t = MaterialTheme.typography
    val c = MaterialTheme.colorScheme.onBackground
    val base =
        when (kind) {
            KitBlockKind.Heading -> t.titleMedium
            KitBlockKind.Quote ->
                t.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> t.bodyLarge
        }
    // §8.3: rows move by whole pixels; the default LineBreak.Simple stays (never switched when a
    // block settles).
    return base.copy(
        color = if (kind == KitBlockKind.Quote) base.color else c,
        textMotion = TextMotion.Static,
    )
}

/**
 * A turn's next steps under its quiet lines (a failed reply's "Choose another model", "Continue", Details): the same
 * column and side inset as [KitQuietLine], so the buttons line up with the words above them and never touch the
 * screen edge (B-099). Buttons stack with a small gap.
 */
@Composable
public fun KitTranscriptActions(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier.then(column()).testTag("kit-transcript-actions"),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        content = content,
    )
}

/** One quiet line: a folded thought, the work line, a notice or the turn footer. */
@Composable
public fun KitQuietLine(text: String, modifier: Modifier = Modifier) {
    // E20-S12: inside an opened step the line is already on the step's text edge; the page gutter would push it
    // past the code under it.
    val inStep = io.github.eslamasabry.oc.ui.kitchat.tool.LocalKitStepBody.current
    Box(modifier.then(if (inStep) StepLine else column())) {
        BasicText(
            text,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDirection = TextDirection.Content, // B12: a thought in the reply's language
                ),
        )
    }
}
