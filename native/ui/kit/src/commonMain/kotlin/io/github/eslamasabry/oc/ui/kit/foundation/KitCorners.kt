package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Immutable
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId

/**
 * A surface whose four corners each take their own named shape, in reading order (the start corners are on the right
 * in Arabic). Each corner is resolved as its shape is everywhere: the Corners knob, a style's sharp radius and Glass's
 * pane boost apply per corner, so a pane with a sheet's corners on one side and a panel's on the other keeps both
 * under every style. A pill or circle corner is fully round (half the shorter side). The face, its edge, its shadows
 * and the clip all follow the same four corners, so an outline stays whole at each of them.
 */
@Immutable
public data class KitCorners(
    val topStart: KitShapeId,
    val topEnd: KitShapeId,
    val bottomEnd: KitShapeId,
    val bottomStart: KitShapeId,
) {
    public companion object {
        /** The two start corners take [start], the two end corners [end]. */
        public fun sides(start: KitShapeId, end: KitShapeId): KitCorners = KitCorners(start, end, end, start)
    }
}
