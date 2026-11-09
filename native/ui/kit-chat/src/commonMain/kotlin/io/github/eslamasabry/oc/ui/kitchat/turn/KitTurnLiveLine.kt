package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** How a turn's live line reads (Dart `_KitTurnLiveLine` tones): working, waiting for the person, or nothing answering. */
public enum class KitTurnLiveTone { Working, WaitingForYou, Quiet }

/**
 * The running turn's live line (Dart `_KitTurnLiveLine`, lib/ui/kit/chat/kit_turn.dart:872-1147): the live dot
 * ([FlutterKitTokens.LIVE_DOT], accent while it works, attention while it waits for the person, text3 while nothing
 * answers) on the first line's middle, space2, the words ("Running npm test · 12 s") in the secondary role and the
 * matching ink, with the light sweeping over them while it works. [spoken] is what a screen reader hears (the words
 * without the seconds). [step] is the muted second line under the words, not under the dot ("Step 3 of 5", or the
 * newest line a running command printed); it unfolds in and out. [onTap] makes the line a button ([tapLabel], "Show
 * steps"): the host shows the turn's steps.
 *
 * Motion: the sweep (a progress loop, still under Off and while it waits); the step line reveals (size and fade).
 */
@Composable
public fun KitTurnLiveLine(
    words: String,
    modifier: Modifier = Modifier,
    tone: KitTurnLiveTone = KitTurnLiveTone.Working,
    spoken: String = words,
    step: String? = null,
    onTap: (() -> Unit)? = null,
    tapLabel: String? = null,
    /** A way out of a long wait (Stop), drawn quietly under the words; null draws none. */
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** A one-time ask under the words (see [KitLiveAsk]); null draws none. It unfolds in and folds away. */
    ask: KitLiveAsk? = null,
) {
    val colors = LocalKitColors.current
    val style = LocalKitTypography.current.style(TypeRole.Secondary)
    val firstLine = with(LocalDensity.current) { style.lineHeight.toDp() }
    val dot = FlutterKitTokens.LIVE_DOT.dp
    val dotColor =
        when (tone) {
            KitTurnLiveTone.Working -> colors.accent
            KitTurnLiveTone.WaitingForYou -> colors.attention
            KitTurnLiveTone.Quiet -> colors.text3
        }
    val ink =
        when (tone) {
            KitTurnLiveTone.Working -> TextTone.Accent
            KitTurnLiveTone.WaitingForYou -> TextTone.Attention
            KitTurnLiveTone.Quiet -> TextTone.Secondary
        }
    val line: @Composable (Modifier) -> Unit = { m ->
        Row(
            // One node: a polite live region named by the phase ([spoken]); the seconds are never announced. The words
            // stay its text, so they can still be found by what they say.
            m.testTag(LIVE_LINE_TAG).semantics(mergeDescendants = true) {
                if (spoken != words) contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.padding(top = maxOf(0.dp, (firstLine - dot) / 2))
                    .size(dot)
                    .background(dotColor, CircleShape)
                    .clearAndSetSemantics {}
            )
            Spacer(Modifier.width(Space.S2.dp))
            // F: kit_turn.dart:1063 waiting for the person, nothing sweeps: nothing works meanwhile
            KitLiveSweep(working = tone == KitTurnLiveTone.Working) {
                KitText(words, role = TypeRole.Secondary, tone = ink, tabular = true)
            }
        }
    }
    Column(modifier) {
        if (onTap == null) line(Modifier)
        else
            // F: kit_turn.dart:1100-1109 a tap on the line shows the turn's steps; one step of padding keeps it a target
            KitTappable(onClick = onTap, onClickLabel = tapLabel, shape = KitShapeId.Button) {
                line(Modifier.padding(vertical = Space.S1.dp))
            }
        if (actionLabel != null && onAction != null)
            KitButton(
                actionLabel,
                onAction,
                role = KitButtonRole.Tertiary,
                modifier = Modifier.padding(start = dot + Space.S2.dp).testTag(LIVE_ACTION_TAG),
            )
        // The leaving line keeps its words while it folds away.
        val held = remember { arrayOfNulls<String>(1) }
        if (!step.isNullOrEmpty()) held[0] = step
        KitReveal(visible = !step.isNullOrEmpty()) {
            // F: kit_turn.dart:1127-1139 under the words, not under the dot; two lines at most
            KitText(
                step?.takeIf { it.isNotEmpty() } ?: held[0].orEmpty(),
                Modifier.padding(start = dot + Space.S2.dp).testTag(LIVE_STEP_TAG),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                maxLines = 2,
            )
        }
        // The leaving ask keeps its words while it folds away.
        val heldAsk = remember { arrayOfNulls<KitLiveAsk>(1) }
        if (ask != null) heldAsk[0] = ask
        KitReveal(visible = ask != null) {
            val shown = ask ?: heldAsk[0]
            if (shown != null) KitLiveAskView(shown, Modifier.padding(top = Space.S2.dp).testTag(LIVE_ASK_TAG))
        }
    }
}

/** Test handle of the live line's row. */
public const val LIVE_LINE_TAG: String = "kit-turn-live-line"

/** Test handle of the live line's Stop. */
public const val LIVE_ACTION_TAG: String = "kit-turn-live-action"

/** Test handle of the live line's muted step line. */
public const val LIVE_STEP_TAG: String = "kit-turn-live-step"
