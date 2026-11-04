package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** What a tappable is to a screen reader. */
public enum class KitTappableRole { Button, Row, Tab, Switch }

/**
 * The one tappable (Dart `KitTappable` + `KitInk` + `KitPressTracker`; plan 06 §7.1). Press feedback is the
 * platform's (Android ripple at 12 % of [ink], bounded to [shape]; a flat fill under reduced motion and on desktop).
 * The hit area is at least 48 dp. [haptic] plays on a tap only when the meaning calls for one (plain taps are
 * silent, MOT-11); a long-press always plays `LongPress`, performed by the kit so one path plays every haptic.
 * A disabled tappable must say why: [disabledReason] becomes its state description.
 *
 * Text: none (content decides). Hidden: long-press when [onLongClick] is set — the screen gives it a visible twin.
 */
@Composable
public fun KitTappable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    role: KitTappableRole = KitTappableRole.Button,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    haptic: KitHaptic? = null,
    shape: KitShapeId = KitShapeId.Button,
    ink: TextTone = TextTone.Primary,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier.kitTappable(
                onClick = onClick, enabled = enabled, disabledReason = disabledReason, role = role,
                onClickLabel = onClickLabel, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel,
                haptic = haptic, shape = shape, ink = ink,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The modifier form, for kit parts that draw their own box (`KitButton`, rows). Kit-internal. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.kitTappable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledReason: String? = null,
    role: KitTappableRole = KitTappableRole.Button,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    haptic: KitHaptic? = null,
    shape: KitShapeId = KitShapeId.Button,
    ink: TextTone = TextTone.Primary,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val platform = LocalKitPlatform.current
    val appearance = LocalKitAppearance.current
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val reduced = LocalKitMotion.current.reduced
    if (platform.isDebug) {
        check(enabled || disabledReason != null) { "KitTappable: a disabled tappable must say why (B-KIT-051)" }
    }
    val inkColor = colors.tone(ink)
    val indication =
        remember(platform, inkColor, reduced) {
            if (reduced) KitFillIndication(inkColor) else platform.pressIndication(inkColor)
        }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val hapticsOn = appearance.haptics
    return this
        .sizeIn(minWidth = tokens.minTarget.dp, minHeight = tokens.minTarget.dp)
        .clip(shape.toShape())
        .semantics { if (!enabled && disabledReason != null) stateDescription = disabledReason }
        .combinedClickable(
            interactionSource = interactions,
            indication = indication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role.toSemantics(),
            onLongClickLabel = onLongClickLabel,
            onLongClick =
                onLongClick?.let { long ->
                    {
                        if (hapticsOn) platform.haptic(KitHaptic.LongPress)
                        long()
                    }
                },
            hapticFeedbackEnabled = false,
            onClick = {
                if (hapticsOn && haptic != null) platform.haptic(haptic)
                onClick()
            },
        )
}

private fun KitTappableRole.toSemantics(): Role =
    when (this) {
        KitTappableRole.Button, KitTappableRole.Row -> Role.Button
        KitTappableRole.Tab -> Role.Tab
        KitTappableRole.Switch -> Role.Switch
    }
