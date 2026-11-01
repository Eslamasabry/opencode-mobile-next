package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * An icon-only button (Dart `KitIconButton`; plan 06 §7.5). [label] is required and is what a screen reader says (and
 * the tooltip on a fine pointer, once `KitTip` lands with S2-KITB); 48 dp target. A toggled glyph ([icon] changes)
 * cross-fades on QUICK and settles in one frame under Off (B-KIT-019). [stateLabel] is the spoken state of a toggle
 * ("On"). [tone] Accent marks the one active icon in a bar.
 *
 * States: default, pressed, disabled, working
 * Text: none (label is semantics only)
 * Cap: none (48 dp square)
 * Motion: glyph cross-fade QUICK; Off = instant
 * Replaces: KitIconButton (lib/ui/kit/kit_icon_button.dart)
 */
@Composable
public fun KitIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: TextTone = TextTone.Primary,
    stateLabel: String? = null,
    enabled: KitEnabled = KitEnabled.Enabled,
    working: Boolean = false,
    haptic: KitHaptic? = null,
) {
    kitAssert(label.isNotBlank()) { "KitIconButton: an icon button needs a label (B-KIT-019)" }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val on = enabled.isEnabled
    val ink = if (on) colors.tone(tone) else colors.text3
    Box(
        modifier
            .kitTappable(
                onClick = { if (!working) onClick() },
                enabled = on,
                disabledReason = enabled.reason,
                haptic = if (working) null else haptic,
                shape = KitShapeId.Circle,
                ink = tone,
            )
            .semantics {
                contentDescription = label
                if (stateLabel != null) stateDescription = stateLabel
                if (working) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (working) {
            KitWorkingMark(ink, size = tokens.smallIcon.dp)
        } else {
            AnimatedContent(
                targetState = icon,
                transitionSpec = { fadeIn(specs.quick()) togetherWith fadeOut(specs.quick()) },
                label = "kit-icon-swap",
            ) { glyph -> KitGlyph(glyph, ink, (tokens.smallIcon + 2).dp) }
        }
    }
}
