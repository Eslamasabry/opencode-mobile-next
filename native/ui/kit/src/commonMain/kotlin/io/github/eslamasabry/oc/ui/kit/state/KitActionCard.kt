package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairlineBorder
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/**
 * The one thing to do on an empty part of a page, as a card ("Add a computer"): a large bare glyph that pictures it
 * ([picture], no box behind it), one line of what it gives ([line]) and its one ordinary primary button ([action]),
 * not a tinted slab and not full width. The card is the style's own content card (Solid's matte face and hairline,
 * every other style its own) on the corners knob's radius. It sits at the top of what it stands in for.
 *
 * States: default; the action may be disabled (it says why).
 */
@Composable
public fun KitActionCard(picture: ImageVector, line: String, action: KitAction, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, level = SurfaceLevel.Surface2, ownFill = colors.surface1))
    val solid = LocalKitStyle.current.style == Style.Solid
    val edge = if (solid) Modifier.kitHairlineBorder(kitHairline(), colors.hairline, look.shape) else Modifier
    Column(
        modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().then(look.modifier).then(edge).padding(Space.S5.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        KitIcon(picture, Modifier.size(FlutterKitTokens.MARK_SIZE.dp).clearAndSetSemantics {}, tone = TextTone.Secondary)
        Spacer(Modifier.height(Space.S4.dp))
        KitText(line, role = TypeRole.Body, tone = TextTone.Primary)
        Spacer(Modifier.height(Space.S4.dp))
        KitButton(
            action.label,
            { action.onClick?.invoke() },
            enabled = action.enabled,
            working = action.working,
            icon = action.icon,
            expand = false,
        )
    }
}
