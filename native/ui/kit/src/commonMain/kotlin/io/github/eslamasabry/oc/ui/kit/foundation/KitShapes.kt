package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.tokens.Corner
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Shapes
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/** A named shape as a Compose shape at the baseline radii. The sheet rounds its top corners only. */
public fun KitShapeId.toShape(): Shape = toShape(Corners.Round, 2f)

/**
 * A named shape under the Corners knob (plan 06 §4.3, B-KIT-005): Round keeps the token, Soft takes about 60 %, Sharp
 * takes the style's `sharpRadius`. Flutter's pillBorder becomes a sharp box under Sharp; circles stay round.
 */
public fun KitShapeId.toShape(corners: Corners, sharpDp: Float): Shape =
    when (this) {
        KitShapeId.Circle -> CircleShape
        KitShapeId.Pill -> if (corners == Corners.Sharp) RoundedCornerShape(sharpDp.dp) else CircleShape
        KitShapeId.Square -> RectangleShape
        KitShapeId.Sheet -> {
            val sheetSharp = minOf(4f, sharpDp * 2f) // F: lib/ui/kit/kit_tokens.dart:672,688
            radius(corners, sheetSharp).let { RoundedCornerShape(topStart = it, topEnd = it) }
        }
        else -> RoundedCornerShape(radius(corners, sharpDp))
    }

private fun KitShapeId.radius(corners: Corners, sharpDp: Float): Dp =
    StyleResolver.corner((Shapes.of(this) as Corner.Radius).dp.toFloat(), corners, sharpDp).dp

/** The shape a kit part draws: the named shape under the Corners knob in force. */
@Composable
public fun KitShapeId.shape(): Shape {
    val style = LocalKitStyle.current
    return toShape(style.knobs.corners, style.spec.tokens.sharpRadius)
}
