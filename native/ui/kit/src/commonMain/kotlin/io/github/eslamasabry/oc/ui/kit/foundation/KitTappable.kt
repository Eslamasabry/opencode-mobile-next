package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.Interaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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
    pressClip: Shape? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier.kitTappable(
                onClick = onClick, enabled = enabled, disabledReason = disabledReason, role = role,
                onClickLabel = onClickLabel, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel,
                haptic = haptic, shape = shape, ink = ink, pressClip = pressClip,
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
    focusFill: Boolean = true,
    pressClip: Shape? = null,
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
    // A part that marks focus itself (the bar's exit ring) keeps the press fill but not a resting focus fill.
    val drawn: InteractionSource =
        if (focusFill) interactions
        else remember(interactions) {
            val all = interactions.interactions
            object : InteractionSource {
                override val interactions: Flow<Interaction> = all.filter { it !is FocusInteraction }
            }
        }
    val hapticsOn = appearance.haptics
    // P-108: the control's box, read only when it is pressed, so a menu it opens can open beside it.
    val origin = LocalKitPressOrigin.current
    val placed = remember { PlacedBox() }
    return this
        .onPlaced { placed.coordinates = it }
        .sizeIn(minWidth = tokens.minTarget.dp, minHeight = tokens.minTarget.dp)
        .clip(pressClip ?: shape.shape())
        .then(role.edgeMark())
        .semantics { if (!enabled && disabledReason != null) stateDescription = disabledReason }
        .then(if (focusFill) Modifier else Modifier.indication(drawn, indication))
        .combinedClickable(
            interactionSource = interactions,
            indication = if (focusFill) indication else null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role.toSemantics(),
            onLongClickLabel = onLongClickLabel,
            onLongClick =
                onLongClick?.let { long ->
                    {
                        placed.pressed()?.let(origin::record)
                        if (hapticsOn) platform.haptic(KitHaptic.LongPress)
                        long()
                    }
                },
            hapticFeedbackEnabled = false,
            onClick = {
                placed.pressed()?.let(origin::record)
                if (hapticsOn && haptic != null) platform.haptic(haptic)
                onClick()
            },
        )
}

/** Where a tappable was last placed; its window box is worked out only when it is pressed. */
private class PlacedBox {
    var coordinates: LayoutCoordinates? = null

    fun pressed(): IntRect? = coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.toIntRect()
}

/** A row's highlight runs edge to edge; its words keep the gutter (native/docs/kit/LAYOUT-RULES.md §2). */
private fun KitTappableRole.edgeMark(): Modifier =
    if (this == KitTappableRole.Row) Modifier.kitEdge(KitEdge.Bleed) else Modifier

private fun KitTappableRole.toSemantics(): Role =
    when (this) {
        KitTappableRole.Button, KitTappableRole.Row -> Role.Button
        KitTappableRole.Tab -> Role.Tab
        KitTappableRole.Switch -> Role.Switch
    }

/**
 * Reading content that opens its actions on a tap or a hold (a message bubble, a reply; M-018): the press shows from
 * the moment the finger lands and stays through the hold, as the platform's bounded ripple clipped to [shape] (the
 * bubble's own outline), so a long-press is never ~600 ms of nothing. Unlike [kitTappable] it adds no minimum size
 * and no resting surface: the content keeps its natural height. The hold plays the kit's `LongPress` haptic, once.
 * Reduced motion shows the flat press fill instead of the ripple.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun Modifier.kitContentPress(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    shape: Shape,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val platform = LocalKitPlatform.current
    val hapticsOn = LocalKitAppearance.current.haptics
    val reduced = LocalKitMotion.current.reduced
    val ink = LocalKitColors.current.tone(TextTone.Primary)
    val indication = remember(platform, ink, reduced) { if (reduced) KitFillIndication(ink) else platform.pressIndication(ink) }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    return this
        .clip(shape)
        .combinedClickable(
            interactionSource = interactions,
            indication = indication,
            onClickLabel = label,
            onLongClickLabel = label,
            hapticFeedbackEnabled = false,
            onLongClick = {
                if (hapticsOn) platform.haptic(KitHaptic.LongPress)
                onLongClick()
            },
            onClick = onClick,
        )
}
