package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole

/**
 * The hard maximum width of a part (plan 06 §8.2; a port of Dart `_RenderKitMaxWidth.performLayout`). The box wraps its
 * child and grows only when the parent forces a minimum width; then the child sits at [align] (start/end mirror in RTL
 * through `placeRelative`). Fill, Wrapper and Scope roles add no layout node. A part applies it as its outermost
 * modifier, so a caller cannot widen it.
 */
public fun Modifier.kitMaxWidth(role: CapRole, align: CapAlign = role.defaultAlign): Modifier {
    val capDp = role.capDp ?: return this
    return this.layout { measurable, constraints ->
        val cap = capDp.dp.roundToPx()
        val childMax = minOf(constraints.maxWidth, cap)
        val placeable =
            measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, childMax), maxWidth = childMax))
        val width = constraints.constrainWidth(placeable.width)
        layout(width, placeable.height) {
            val x =
                when (align) {
                    CapAlign.Start -> 0
                    CapAlign.End -> width - placeable.width
                    CapAlign.Center -> (width - placeable.width) / 2
                }
            placeable.placeRelative(x, 0)
        }
    }
}
