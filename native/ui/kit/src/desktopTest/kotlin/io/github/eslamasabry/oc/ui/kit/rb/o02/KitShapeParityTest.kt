package io.github.eslamasabry.oc.ui.kit.rb.o02

import kotlin.test.*
import io.github.eslamasabry.oc.ui.design.tokens.*
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.CircleShape

class KitShapeParityTest {
    @Test fun identitiesRadiiAndSurfaceLevels() {
        assertEquals(listOf("Square","Tile","Code","Button","Panel","Card","Dialog","Sheet","Pill","Circle"),KitShapeId.entries.map { it.name })
        assertEquals(listOf(0,9,14,14,18,22,24,30),KitShapeId.entries.take(8).map { (Shapes.of(it) as Corner.Radius).dp })
        assertEquals(Corner.Full, Shapes.of(KitShapeId.Pill)); assertEquals(Corner.Full,Shapes.of(KitShapeId.Circle))
        assertEquals(listOf("Ground","Surface1","Surface2","Surface3"),SurfaceLevel.entries.map { it.name })
        for (corners in Corners.entries) {
            assertEquals(RectangleShape,KitShapeId.Square.toShape(corners,2f))
            assertEquals(CircleShape,KitShapeId.Circle.toShape(corners,2f))
            assertEquals(KitShapeId.Pill.toShape(),KitShapeId.Pill.toShape(corners,2f))
        }
    }
}
