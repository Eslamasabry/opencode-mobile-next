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
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.Spacing
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.stream.drive
import io.github.eslamasabry.oc.ui.kitchat.stream.growOnly
import io.github.eslamasabry.oc.ui.kitchat.stream.kitFreshFade

/** Block kinds the kit draws (the feature maps the engine's row kinds onto these). */
public enum class KitBlockKind { Prose, Heading, Quote, ListItem, Code }

/** Plan 09 R13: the conversation column is capped at 700 dp. */
private val Column = Modifier.fillMaxWidth().widthIn(max = 700.dp).padding(horizontal = Spacing.M.dp, vertical = Spacing.XS.dp)

private class LayoutHolder {
    var layout: TextLayoutResult? = null
}

/**
 * One Markdown block of a reply. A streamed block reveals word by word with the draw-phase fade; its box grows
 * smoothly while it is live. Settled and loaded blocks draw at full length.
 *
 * @param revealKey the row key; @param revealGroup the part it belongs to (blocks of one part reveal in order);
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
) {
    val reveal = if (streamed) registry.stateFor(revealKey, revealGroup, live, text.length) else null
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
    var m = modifier.then(Column)
    if (revealing) {
        m = m.clipToBounds().growOnly(enabled = live && motion == KitMotionLevel.Full)
        if (motion == KitMotionLevel.Full) m = m.preferredFrameRate(FrameRateCategory.High) // §8.5, only while live
    }
    val textModifier = if (revealing) Modifier.kitFreshFade({ holder.layout }, reveal) else Modifier
    val boxed =
        if (kind == KitBlockKind.Code) {
            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(Spacing.S.dp)
        } else {
            Modifier
        }
    Box(m) {
        BasicText(
            text = visible,
            style = style,
            onTextLayout = { holder.layout = it },
            modifier = boxed.then(textModifier),
        )
    }
}

@Composable
private fun styleOf(kind: KitBlockKind): TextStyle {
    val t = MaterialTheme.typography
    val c = MaterialTheme.colorScheme.onBackground
    val base =
        when (kind) {
            KitBlockKind.Heading -> t.titleMedium
            KitBlockKind.Code -> t.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            KitBlockKind.Quote -> t.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> t.bodyLarge
        }
    // §8.3: rows move by whole pixels; the default LineBreak.Simple stays (never switched when a block settles).
    return base.copy(color = if (kind == KitBlockKind.Quote) base.color else c, textMotion = TextMotion.Static)
}

/** The person's prompt: a bubble at the end side, ≤ 85 % wide (R13). */
@Composable
public fun KitPromptBubble(text: String, modifier: Modifier = Modifier) {
    Box(modifier.then(Column), contentAlignment = Alignment.CenterEnd) {
        BasicText(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onPrimaryContainer),
            modifier =
                Modifier.fillMaxWidth(0.85f)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                    .padding(horizontal = Spacing.M.dp, vertical = Spacing.S.dp),
        )
    }
}

/** One quiet line: a folded thought, the work line, a notice or the turn footer. */
@Composable
public fun KitQuietLine(text: String, modifier: Modifier = Modifier) {
    Box(modifier.then(Column)) {
        BasicText(text, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
    }
}
