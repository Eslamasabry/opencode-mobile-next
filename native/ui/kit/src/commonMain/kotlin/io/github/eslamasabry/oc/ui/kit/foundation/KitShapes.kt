package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.design.tokens.Corner
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Shapes
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/** A named shape as a Compose shape. The sheet rounds its top corners only. */
@Composable
public fun KitShapeId.toShape(): Shape =
    when (this) {
        KitShapeId.Circle -> CircleShape
        KitShapeId.Pill -> RoundedCornerShape(percent = 50)
        KitShapeId.Square -> RectangleShape
        KitShapeId.Sheet -> LocalKitStyle.current.corner((Shapes.of(this) as Corner.Radius).dp).dp.let { RoundedCornerShape(topStart = it, topEnd = it) }
        else -> RoundedCornerShape(LocalKitStyle.current.corner((Shapes.of(this) as Corner.Radius).dp).dp)
    }
