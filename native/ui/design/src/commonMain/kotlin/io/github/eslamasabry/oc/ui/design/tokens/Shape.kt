package io.github.eslamasabry.oc.ui.design.tokens

import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens

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
            KitShapeId.Tile -> Corner.Radius(FlutterKitTokens.ICON_TILE_RADIUS)
            KitShapeId.Code -> Corner.Radius(FlutterKitTokens.CODE_RADIUS)
            KitShapeId.Button -> Corner.Radius(FlutterKitTokens.BUTTON_RADIUS)
            KitShapeId.Panel -> Corner.Radius(FlutterKitTokens.PANEL_CORNER_RADIUS)
            KitShapeId.Card -> Corner.Radius(FlutterKitTokens.CARD_RADIUS)
            KitShapeId.Dialog -> Corner.Radius(FlutterKitTokens.PANEL_RADIUS)
            KitShapeId.Sheet -> Corner.Radius(FlutterKitTokens.SHEET_RADIUS) // top corners only
            KitShapeId.Pill, KitShapeId.Circle -> Corner.Full
        }

    /** Modal side rails. */
    public const val RAIL_RADIUS: Int = FlutterKitTokens.RAIL
}
