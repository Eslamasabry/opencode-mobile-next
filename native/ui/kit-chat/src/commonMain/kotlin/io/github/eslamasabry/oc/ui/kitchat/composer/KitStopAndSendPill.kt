package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/**
 * The "Stop and send" pill (plan 08 §5.2): while a reply runs and the agent must stop it to take the message
 * (`delivery == stopAndSend`), the field's Send disc becomes this amber (`attentionFill`) pill with the stop square and
 * the words, so the stop is plain before the tap (an approved amber meaning, 06 LOOK-4). It plays `KitHaptic.Send`. Its
 * wider width is taken at once (no width animation); the disc ⇄ pill change is a [KitComposerSwap] cross-fade.
 *
 * Text: frame. Motion: none of its own (press feedback from `KitTappable`).
 */
@Composable
public fun KitStopAndSendPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val style = LocalKitTypography.current.style(TypeRole.Label).copy(color = colors.onAttentionFill)
    val shape = KitShapeId.Pill.toShape()
    KitTappable(
        onClick = onClick,
        modifier = modifier.testTag(PILL_TAG).semantics { contentDescription = label },
        haptic = KitHaptic.Send,
        shape = KitShapeId.Pill,
    ) {
        Row(
            Modifier.clearAndSetSemantics {}
                .heightIn(min = PILL_HEIGHT_DP.dp)
                .background(colors.attentionFill, shape)
                .padding(horizontal = Space.S3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            Icon(StopGlyph, contentDescription = null, tint = colors.onAttentionFill, modifier = Modifier.size((tokens.smallIcon - 4).dp))
            BasicText(label, style = style, maxLines = 1)
        }
    }
}

/** The pill is as tall as the Send disc it replaces. */
private const val PILL_HEIGHT_DP = 40

/** Test handle of the pill. */
public const val PILL_TAG: String = "kit-stop-and-send"

/**
 * The field button's face changes (plan 08 §5.2 "Field button changes"): mic ⇄ Send ⇄ "Stop and send", Send → spinner,
 * all on the same 48 dp target, a cross-fade of `QUICK` under Full and Calm, instant under Off. No scale (MOT-2).
 *
 * Motion: fade. Replaces: the field button's `KitSwap` (lib/ui/kit/chat/kit_composer.dart).
 */
@Composable
public fun <T> KitComposerSwap(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    Crossfade(target, modifier, animationSpec = KitMotion.specs.quick(), label = "composer-swap") { content(it) }
}
