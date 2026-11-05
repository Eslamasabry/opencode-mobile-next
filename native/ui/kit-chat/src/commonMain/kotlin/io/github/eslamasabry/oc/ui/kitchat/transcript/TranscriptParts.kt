package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
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

private class LayoutHolder {
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
    var m = modifier.then(columnOf(continued, continues))
    if (revealing) {
        m = m.clipToBounds().growOnly(enabled = live && motion == KitMotionLevel.Full)
        if (motion == KitMotionLevel.Full)
            m = m.preferredFrameRate(FrameRateCategory.High) // §8.5, only while live
    }
    val textModifier = if (revealing) Modifier.kitFreshFade({ holder.layout }, reveal) else Modifier
    val boxed = if (kind == KitBlockKind.Code) codeBox(continued, continues) else Modifier
    Box(m) {
        BasicText(
            text = visible,
            style = style,
            onTextLayout = {
                holder.layout = it
                registry.laidOut(it.layoutInput.text.length)
            },
            modifier = boxed.then(textModifier),
        )
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

/**
 * The code surface: one rounded box across all chunks of a fence (no seam, no inner padding between
 * chunks).
 */
@Composable
private fun codeBox(continued: Boolean, continues: Boolean): Modifier {
    val top = if (continued) 0.dp else 8.dp
    val bottom = if (continues) 0.dp else 8.dp
    return Modifier.background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(top, top, bottom, bottom),
        )
        .fillMaxWidth()
        .padding(
            start = Space.S2.dp,
            end = Space.S2.dp,
            top = if (continued) 0.dp else Space.S2.dp,
            bottom = if (continues) 0.dp else Space.S2.dp,
        )
}

@Composable
private fun styleOf(kind: KitBlockKind): TextStyle {
    val t = MaterialTheme.typography
    val c = MaterialTheme.colorScheme.onBackground
    val base =
        when (kind) {
            KitBlockKind.Heading -> t.titleMedium
            KitBlockKind.Code -> t.bodyMedium.copy(fontFamily = FontFamily.Monospace)
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
 * The person's prompt: a bubble at the end side, ≤ 85 % wide (R13), wrapped to its words. Graphite `surface2` with
 * the primary ink, as in Flutter: mint is kept for actions and live things, never the person's words.
 */
@Composable
public fun KitPromptBubble(text: String, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    Box(modifier.then(Column), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.fillMaxWidth(0.85f), contentAlignment = Alignment.CenterEnd) {
            BasicText(
                text = text,
                style =
                    // QA wave 4 B12: the person's own words read in their own direction (an English prompt in an
                    // Arabic interface is left to right, its full stop at the end).
                    LocalKitTypography.current.style(TypeRole.Body).copy(
                        color = colors.text1,
                        textDirection = TextDirection.Content,
                    ),
                modifier =
                    Modifier.background(colors.surface2, RoundedCornerShape(tokens.bubbleRadius.dp))
                        .padding(horizontal = Space.S4.dp, vertical = Space.S3.dp),
            )
        }
    }
}

/** One quiet line: a folded thought, the work line, a notice or the turn footer. */
@Composable
public fun KitQuietLine(text: String, modifier: Modifier = Modifier) {
    Box(modifier.then(Column)) {
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
