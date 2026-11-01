package io.github.eslamasabry.oc.ui.design.tokens

/** The kit's named shapes (Dart `KitShape`, `lib/ui/kit/kit_shape.dart`): a part names a shape, never a radius. */
public enum class KitShapeId { Square, Tile, Code, Button, Panel, Card, Dialog, Sheet, Pill, Circle }

/** The surface steps a part sits on (Dart `KitSurfaceLevel`). */
public enum class SurfaceLevel { Ground, Surface1, Surface2, Surface3 }

/** A resolved corner: a radius in dp, or fully rounded (stadium / circle). */
public sealed interface Corner {
    public data class Radius(val dp: Int) : Corner
    public data object Full : Corner
}

public object Shapes {
    /** Round corners (Solid at its defaults). Dart `KitTokens.shapeOf` over the radii in `KitTokens.fallback`. */
    public fun of(shape: KitShapeId): Corner =
        when (shape) {
            KitShapeId.Square -> Corner.Radius(0)
            KitShapeId.Tile -> Corner.Radius(9) // iconTileRadius
            KitShapeId.Code -> Corner.Radius(14) // codeRadius
            KitShapeId.Button -> Corner.Radius(14) // buttonRadius (field 14, popover 14)
            KitShapeId.Panel -> Corner.Radius(18) // panelCornerRadius
            KitShapeId.Card -> Corner.Radius(22) // cardRadius
            KitShapeId.Dialog -> Corner.Radius(24) // panelRadius
            KitShapeId.Sheet -> Corner.Radius(30) // sheetRadius, top corners only
            KitShapeId.Pill, KitShapeId.Circle -> Corner.Full
        }

    /** Modal side rails. */
    public const val RAIL_RADIUS: Int = 20
}
