package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * The selection twin of `KitTappable` for part-B controls (segments, choice rows, switch rows): `selectable` (radio,
 * tab) or `toggleable` (checkbox, switch) with the kit's press feedback, a 48 dp target, and the haptic played through
 * the kit seam ([io.github.eslamasabry.oc.ui.kit.platform.KitPlatform.haptic], plan 06 §4.9). A disabled control states
 * its reason.
 */
@Composable
internal fun Modifier.kitSelectTap(
    selected: Boolean,
    role: Role,
    enabled: KitEnabled,
    haptic: KitHaptic?,
    shape: KitShapeId = KitShapeId.Square,
    onClick: () -> Unit,
): Modifier {
    val platform = LocalKitPlatform.current
    val hapticsOn = LocalKitAppearance.current.haptics
    val ink = LocalKitColors.current.text1
    val reduced = LocalKitMotion.current.reduced
    val tokens = LocalKitTokens.current
    val indication = remember(platform, ink, reduced) { if (reduced) KitFillIndication(ink) else platform.pressIndication(ink) }
    val interactions = remember { MutableInteractionSource() }
    val act = {
        if (hapticsOn && haptic != null) platform.haptic(haptic)
        onClick()
    }
    val base =
        this.sizeIn(minWidth = tokens.minTarget.dp, minHeight = tokens.minTarget.dp)
            .clip(shape.shape())
            .semantics { enabled.reason?.let { stateDescription = it } }
    return if (role == Role.Checkbox || role == Role.Switch) {
        base.toggleable(
            value = selected,
            interactionSource = interactions,
            indication = indication,
            enabled = enabled.isEnabled,
            role = role,
            onValueChange = { act() },
        )
    } else {
        base.selectable(
            selected = selected,
            interactionSource = interactions,
            indication = indication,
            enabled = enabled.isEnabled,
            role = role,
            onClick = act,
        )
    }
}

/** Plays a haptic through the kit seam when the appearance allows it; for parts that decide after the tap. */
@Composable
internal fun rememberKitHaptics(): (KitHaptic) -> Unit {
    val platform = LocalKitPlatform.current
    val on = LocalKitAppearance.current.haptics
    return remember(platform, on) { { kind -> if (on) platform.haptic(kind) } }
}
