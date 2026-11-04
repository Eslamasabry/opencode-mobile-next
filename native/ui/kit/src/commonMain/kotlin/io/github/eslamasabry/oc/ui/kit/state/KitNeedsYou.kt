package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.foundation.KitSignalHead
import io.github.eslamasabry.oc.ui.kit.foundation.KitSignalAspect
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitNeedsYouReasonBlocked
import io.github.eslamasabry.oc.ui.words.res.kitNeedsYouReasonConsent
import io.github.eslamasabry.oc.ui.words.res.kitNeedsYouReasonDecision
import io.github.eslamasabry.oc.ui.words.res.kitNeedsYouWaiting
import org.jetbrains.compose.resources.stringResource

/** Why something needs the person (Dart `KitNeedsYouReason`). */
public enum class KitNeedsYouReason { Decision, Blocked, Consent }

private const val DOT_DP = 8

/**
 * The one attention marker (Dart `KitNeedsYou`; plan 06 §7.6): an amber dot and its word, always together, so amber is
 * never the only signal. Amber is reserved for this meaning (owner rule; `attention_allowlist.txt`). [waiting] is the
 * already-formatted wait ("2 min"), shown as "waiting 2 min".
 *
 * States: one per reason
 * Text: frame
 * Cap: Chip
 * Motion: none (it never pulses in Solid)
 * Replaces: KitNeedsYou, KitNeedsYouReason (lib/ui/kit/kit_needs_you.dart)
 */
@Composable
public fun KitNeedsYou(reason: KitNeedsYouReason, modifier: Modifier = Modifier, waiting: String? = null) {
    val colors = LocalKitColors.current
    val words =
        when (reason) {
            KitNeedsYouReason.Decision -> stringResource(Res.string.kitNeedsYouReasonDecision)
            KitNeedsYouReason.Blocked -> stringResource(Res.string.kitNeedsYouReasonBlocked)
            KitNeedsYouReason.Consent -> stringResource(Res.string.kitNeedsYouReasonConsent)
        }
    val shape = KitShapeId.Pill.toShape()
    if (LocalKitStyle.current.track) {
        Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitSignalHead(KitSignalAspect.Stop)
            KitText(words, role = TypeRole.Caption, kind = KitTextKind.Frame)
            waiting?.let { KitText(stringResource(Res.string.kitNeedsYouWaiting, it), role = TypeRole.Caption, tone = TextTone.Secondary, kind = KitTextKind.Frame) }
        }
        return
    }
    Row(
        modifier
            .heightIn(min = 24.dp)
            .clip(shape)
            .background(colors.attentionSurface)
            .border(1.dp, colors.attentionLine, shape)
            .padding(horizontal = Space.S2.dp, vertical = 2.dp)
            .testTag("kit-needs-you")
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        Box(Modifier.size(DOT_DP.dp).clip(KitShapeId.Circle.toShape()).background(colors.attention))
        KitText(words, role = TypeRole.Label, tone = TextTone.Attention, kind = KitTextKind.Frame, maxLines = 1)
        waiting?.let {
            KitText(stringResource(Res.string.kitNeedsYouWaiting, it), role = TypeRole.Caption, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
        }
    }
}
