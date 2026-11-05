package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * The screen's one floating action (Dart `KitFloatingAction`): an extended Material button in the accent, 56 dp tall,
 * a 16 dp corner, the glyph and the words ("New conversation"). The one floating layer of a Solid screen, so it alone
 * carries a soft neutral shadow. Press: the corner rounds and the face settles 2 % (the button press); reduced motion
 * keeps the ripple only. The host places it at the bottom end and pads its list so the last row stays reachable.
 *
 * States: default, pressed
 * Text: frame
 * Cap: 320 dp
 * Motion: press spring; Off = ripple only
 * Replaces: KitFloatingAction (lib/ui/kit/kit_floating_action.dart)
 */
@Composable
public fun KitFab(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed && !KitMotion.state.reduced) 1f else 0f, KitMotion.specs.spatial(), label = "kit-fab-press")
    val shape = RoundedCornerShape((16 + 8 * press).dp)
    Row(
        modifier
            .widthIn(max = 320.dp)
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = 1f - 0.02f * press
                scaleY = 1f - 0.02f * press
            }
            .shadow(6.dp, shape, clip = false, ambientColor = colors.elevationShadow, spotColor = colors.elevationShadow)
            .kitTappable(onClick = onClick, shape = KitShapeId.Button, ink = TextTone.OnAccent, interactionSource = interactions)
            .background(colors.accent, shape)
            .padding(start = Space.S4.dp, end = Space.S5.dp)
            .testTag("kit-fab"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitIcon(icon, size = KitIconSize.Large, tone = TextTone.OnAccent)
        KitText(label, role = TypeRole.Button, tone = TextTone.OnAccent, kind = KitTextKind.Frame, maxLines = 1)
    }
}
