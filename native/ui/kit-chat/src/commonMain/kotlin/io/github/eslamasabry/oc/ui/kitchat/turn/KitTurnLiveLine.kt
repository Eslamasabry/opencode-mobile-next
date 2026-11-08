package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** How a turn's live line reads (Dart `_KitTurnLiveLine` tones): working, waiting for the person, or nothing answering. */
public enum class KitTurnLiveTone { Working, WaitingForYou, Quiet }

/**
 * The running turn's live line (Dart `_KitTurnLiveLine`, lib/ui/kit/chat/kit_turn.dart:872-1090): the live dot
 * ([FlutterKitTokens.LIVE_DOT], accent while it works, attention while it waits for the person, text3 while nothing
 * answers) on the first line's middle, space2, the words ("Writing · 6 s") in the secondary role and the matching ink,
 * with the sweep while it works. [spoken] is what a screen reader hears (the words without the seconds).
 */
@Composable
public fun KitTurnLiveLine(
    words: String,
    modifier: Modifier = Modifier,
    tone: KitTurnLiveTone = KitTurnLiveTone.Working,
    spoken: String = words,
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
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = maxOf(0.dp, (firstLine - dot) / 2)).size(dot).background(dotColor, CircleShape))
        Spacer(Modifier.width(Space.S2.dp))
        KitLiveSweep(working = tone == KitTurnLiveTone.Working) {
            KitText(words, role = TypeRole.Secondary, tone = ink, tabular = true)
        }
    }
}
