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

/**
 * A named shape as a Compose shape under the Corners knob in force (the same answer as [shape]). The sheet rounds its
 * top corners only. Outside composition use [toShape] with explicit corners.
 */
@Composable
public fun KitShapeId.toShape(): Shape = shape()

/**
 * A named shape under the Corners knob (plan 06 §4.3, B-KIT-005): Round keeps the token, Soft takes about 60 %, Sharp
 * takes the style's `sharpRadius` (Paper 0, Signal 4, default 2). Stadiums and circles never change.
 */
public fun KitShapeId.toShape(corners: Corners, sharpDp: Float): Shape =
    when (this) {
        KitShapeId.Circle -> CircleShape
        KitShapeId.Pill -> RoundedCornerShape(percent = 50)
        KitShapeId.Square -> RectangleShape
        KitShapeId.Sheet -> radius(corners, sharpDp).let { RoundedCornerShape(topStart = it, topEnd = it) }
        else -> RoundedCornerShape(radius(corners, sharpDp))
    }

/** A named shape at the baseline radii (Corners Round), for code outside composition. */
public fun KitShapeId.baselineShape(): Shape = toShape(Corners.Round, 2f)

private fun KitShapeId.radius(corners: Corners, sharpDp: Float): Dp =
    StyleResolver.corner((Shapes.of(this) as Corner.Radius).dp.toFloat(), corners, sharpDp).dp

/** The shape a kit part draws: the named shape under the Corners knob in force. */
@Composable
public fun KitShapeId.shape(): Shape {
    val style = LocalKitStyle.current
    return toShape(style.knobs.corners, style.spec.tokens.sharpRadius)
}
