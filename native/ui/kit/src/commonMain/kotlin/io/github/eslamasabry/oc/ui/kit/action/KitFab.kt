package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** The floating action's round face, in dp (Dart `KitFloatingAction.size`). */
public const val KIT_FAB_SIZE: Int = 56

/** Room a list leaves under its last row so the floating action never hides it (Dart `KitFloatingAction.clearance`). */
public const val KIT_FAB_CLEARANCE: Int = 80

/**
 * The screen's one floating action (Dart `KitFloatingAction`): a round 56 dp button in the accent with one glyph (a
 * plus for "New conversation"). Its [label] is not drawn: a screen reader says it (P-107: the 320 dp pill with words
 * covered the rows' times; Flutter floats the round button). The one floating layer of a Solid screen, so it alone
 * carries a soft neutral shadow. Press: the face settles 4 % (the button press); reduced motion keeps the ripple
 * only. When the action cannot run yet the host passes [visible] false and it fades out (and back in, STANDARD on
 * Enter; instant under reduced motion). The host places it on the gutter at the bottom end, and the list under it
 * ends with [KIT_FAB_CLEARANCE] more room so its last row scrolls clear.
 *
 * States: default, pressed, hidden
 * Text: none (the label is heard)
 * Cap: 56 dp
 * Motion: press spring, fade in/out; Off = ripple only, instant
 * Replaces: KitFloatingAction (lib/ui/kit/kit_floating_action.dart)
 */
@Composable
public fun KitFab(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val reduced = KitMotion.state.reduced
    val specs = KitMotion.specs
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else fadeIn(specs.enter()),
        exit = if (reduced) ExitTransition.None else fadeOut(specs.quick()),
    ) {
        val colors = LocalKitColors.current
        val interactions = remember { MutableInteractionSource() }
        val pressed by interactions.collectIsPressedAsState()
        val press by animateFloatAsState(if (pressed && !reduced) 1f else 0f, specs.spatial(), label = "kit-fab-press")
        Box(
            Modifier
                .size(KIT_FAB_SIZE.dp)
                .graphicsLayer {
                    scaleX = 1f - 0.04f * press
                    scaleY = 1f - 0.04f * press
                }
                .shadow(6.dp, CircleShape, clip = false, ambientColor = colors.elevationShadow, spotColor = colors.elevationShadow)
                .kitTappable(onClick = onClick, shape = KitShapeId.Circle, ink = TextTone.OnAccent, interactionSource = interactions)
                .background(colors.accent, CircleShape)
                .semantics { contentDescription = label }
                .testTag("kit-fab"),
            contentAlignment = Alignment.Center,
        ) {
            KitIcon(icon, size = KitIconSize.Fab, tone = TextTone.OnAccent, growsWithText = true)
        }
    }
}
