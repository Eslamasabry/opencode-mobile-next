package io.github.eslamasabry.oc.ui.kit
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitSignalSlip
import io.github.eslamasabry.oc.ui.design.color.SignalPalette

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Dart `KitButtonRole`: primary = accent fill, secondary = surface3, tertiary = words only. */
public enum class KitButtonRole { Primary, Secondary, Tertiary }

/**
 * One action a block or menu can offer (Dart `KitAction`). [onClick] null means the action cannot run now, and then
 * [enabled] must carry the reason. [working] marks the tap in flight: the action stays its enabled colour but ignores
 * taps (STATE-7). [destructive] is never red: a strong neutral, and it is offered apart (B-KIT-011).
 */
@Immutable
public data class KitAction(
    val label: String,
    val onClick: (() -> Unit)?,
    val enabled: KitEnabled = KitEnabled.Enabled,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val working: Boolean = false,
    val haptic: KitHaptic? = null,
    /** What a screen reader says instead of [label] (e.g. the label plus its object). */
    val semanticsLabel: String? = null,
) {
    /** True when a tap would run the action now. */
    val runnable: Boolean get() = onClick != null && enabled.isEnabled
}

/**
 * A button (Dart `KitButton`; plan 06 §7.5). Primary = accent fill with `onAccent` words; a destructive primary is the
 * strong neutral (text1 fill, ground words), never red; secondary = `surface3`; tertiary = `text1` words. Disabled =
 * `surface3` with `text3` words (tertiary: `text3` words) and its reason as the state a screen reader hears. [working]
 * shows the working mark before the words and makes taps no-ops while the button keeps its colour. Labels wrap to two
 * lines. Plain presses are silent; pass [haptic] only when the action's meaning calls for one (plan 06 §4.9).
 *
 * States: default, pressed, disabled, working
 * Text: frame
 * Cap: Control
 * Motion: fill eases between enabled and disabled (QUICK); Off = instant; the working mark is still under Off
 * Replaces: KitButton, KitButton.primary/secondary/tertiary (lib/ui/kit/kit_buttons.dart)
 */
@Composable
public fun KitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: KitButtonRole = KitButtonRole.Primary,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    maxLines: Int = 2,
    semanticsLabel: String? = null,
    enabled: KitEnabled = KitEnabled.Enabled,
    working: Boolean = false,
    haptic: KitHaptic? = null,
) {
    kitAssert(label.isNotBlank()) { "KitButton: a button needs words (B-KIT-011)" }
    kitAssert(enabled !is KitEnabled.Disabled || enabled.reason.isNotBlank()) {
        "KitButton: a disabled button must say why (B-KIT-011)"
    }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val on = enabled.isEnabled
    val fill: Color =
        when {
            role == KitButtonRole.Tertiary -> Color.Transparent
            !on -> colors.surface3
            role == KitButtonRole.Secondary && LocalKitSignalSlip.current -> Color(SignalPalette.of(colors.dark).key.v)
            role == KitButtonRole.Secondary -> colors.surface3
            destructive -> colors.text1
            else -> colors.accent
        }
    val words: Color =
        when {
            !on -> colors.text3
            role == KitButtonRole.Primary && destructive -> colors.ground
            role == KitButtonRole.Primary -> colors.onAccent
            else -> colors.text1
        }
    val easedFill by animateColorAsState(fill, specs.quick(), label = "kit-button-fill")
    val easedWords by animateColorAsState(words, specs.quick(), label = "kit-button-words")
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val look = kitSurfaceLook(
        SurfaceRequest(SurfaceRole.Control, fill = if (role == KitButtonRole.Primary || !on || (role == KitButtonRole.Secondary && LocalKitSignalSlip.current)) Argb(fill.toArgb()) else null,
            pressed = pressed, disabled = !on, action = true),
        easedFill,
    )
    val surfaceWords = look.words?.let { Color(it.v) } ?: easedWords
    val ink = if (role == KitButtonRole.Primary && !destructive) TextTone.OnAccent else TextTone.Primary
    val minHeight = if (role == KitButtonRole.Tertiary) tokens.minTarget else tokens.buttonHeight
    val pad = if (role == KitButtonRole.Tertiary) Space.S2 else Space.S6
    Row(
        modifier =
            modifier
                .heightIn(min = minHeight.dp)
                .then(if (role == KitButtonRole.Tertiary) Modifier.background(easedFill)
                    else Modifier.kitSurface(look, KitShapeId.Button.toShape(), clip = false))
                .kitTappable(
                    onClick = { if (!working) onClick() },
                    enabled = on,
                    disabledReason = enabled.reason,
                    haptic = if (working) null else haptic,
                    shape = KitShapeId.Button,
                    ink = ink,
                    interactionSource = interactions,
                )
                .semantics {
                    if (working) disabled()
                    if (semanticsLabel != null) contentDescription = semanticsLabel
                }
                .padding(horizontal = pad.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            working -> KitWorkingMark(surfaceWords, size = tokens.smallIcon.dp)
            icon != null -> KitGlyph(icon, surfaceWords, tokens.smallIcon.dp)
        }
        KitButtonWords(label, surfaceWords, maxLines)
    }
}

/**
 * The slice-0 bridge: `enabled` + `disabledReason` as two values. New code passes [KitEnabled]; this overload keeps the
 * P1/P7 call sites (`:feature:chats`, `:ui:shell`, the P7 scenes) compiling unchanged.
 */
@Composable
public fun KitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    disabledReason: String? = null,
    haptic: KitHaptic? = null,
) {
    KitButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = KitEnabled.of(enabled, disabledReason),
        haptic = haptic,
    )
}

/** Runs a [KitAction] as a button of [role]. */
@Composable
public fun KitButton(action: KitAction, modifier: Modifier = Modifier, role: KitButtonRole = KitButtonRole.Primary) {
    val run = action.onClick
    kitAssert(run != null || !action.enabled.isEnabled) {
        "KitAction: an action without a callback must say why it cannot run (B-KIT-011)"
    }
    KitButton(
        label = action.label,
        onClick = { run?.invoke() },
        modifier = modifier,
        role = role,
        icon = action.icon,
        destructive = action.destructive,
        semanticsLabel = action.semanticsLabel,
        enabled = if (run == null && action.enabled.isEnabled) KitEnabled.Disabled(action.label) else action.enabled,
        working = action.working,
        haptic = action.haptic,
    )
}

@Composable
private fun KitButtonWords(label: String, color: Color, maxLines: Int) {
    // Tone is resolved by the caller's eased colour; KitText takes a tone, so the words draw through a tone-free path.
    KitTextColored(label, color, maxLines)
}

@Composable
private fun KitTextColored(text: String, color: Color, maxLines: Int) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalKitColors provides LocalKitColors.current.copy(text1 = color),
    ) {
        KitText(
            text,
            role = TypeRole.Button,
            tone = TextTone.Primary,
            kind = KitTextKind.Frame,
            maxLines = maxLines,
            textAlign = TextAlign.Center,
        )
    }
}
