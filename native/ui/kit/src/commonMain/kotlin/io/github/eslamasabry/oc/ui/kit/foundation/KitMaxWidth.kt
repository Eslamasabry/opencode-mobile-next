package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole

/**
 * The hard maximum width of a part (plan 06 §8.2; a port of Dart `KitMaxWidth` and `_RenderKitMaxWidth`,
 * `lib/ui/kit/kit_max_width.dart`). Where the slot is no wider than the cap the child is laid out exactly as without
 * it. On a wider slot the child is held to the cap and sits at [align] (start/end mirror in RTL through
 * `placeRelative`). Height is never limited. Intrinsic widths are capped too, so an `IntrinsicSize` parent never sizes
 * itself past the cap (Dart `computeMin/MaxIntrinsicWidth`). Fill, Wrapper and Scope roles add no layout node. A part
 * applies it as its outermost modifier, so a caller cannot widen it.
 */
public fun Modifier.kitMaxWidth(role: CapRole, align: CapAlign = role.defaultAlign): Modifier {
    val capDp = role.capDp ?: return this
    return kitMaxWidth(capDp.dp, align)
}

/** Any width cap, at [align] in a wider slot (Dart `KitMaxWidth.cap`; default alignment: the middle). */
public fun Modifier.kitMaxWidth(maxWidth: Dp, align: CapAlign = CapAlign.Center): Modifier =
    this then KitMaxWidthElement(maxWidth, align)

public object KitMaxWidth {
    /**
     * The padding on each side that holds a lazy list or sliver to [maxWidth] (Dart `KitMaxWidth.sliver`): half of
     * whatever the scroll area has beyond the cap, on both sides; zero where the area is no wider than the cap.
     */
    public fun sidePadding(available: Dp, maxWidth: Dp): Dp {
        val spare = maxOf(0.dp, available - maxWidth)
        return spare / 2 // F: lib/ui/kit/kit_max_width.dart:119,120
    }

    /** [sidePadding] for a role; zero for roles without a cap. */
    public fun sidePadding(available: Dp, role: CapRole): Dp = role.capDp?.let { sidePadding(available, it.dp) } ?: 0.dp

    /** Dart `_inner`: the constraints the child gets; unchanged where the slot is no wider than [cap]. */
    internal fun inner(c: Constraints, cap: Int): Constraints {
        if (c.maxWidth <= cap) return c
        return c.copy(minWidth = minOf(c.minWidth, cap), maxWidth = cap)
    }

    /** Dart `_dx`, start-relative: where a child [inner] wide sits in [outer] at [align]; 0 when nothing is spare. */
    internal fun offset(outer: Int, inner: Int, align: CapAlign): Int {
        val spare = outer - inner
        if (spare <= 0) return 0
        return when (align) {
            CapAlign.Start -> 0
            CapAlign.End -> spare
            CapAlign.Center -> spare / 2 // F: lib/ui/kit/kit_max_width.dart:195
        }
    }
}

private data class KitMaxWidthElement(val maxWidth: Dp, val align: CapAlign) : ModifierNodeElement<KitMaxWidthNode>() {
    override fun create(): KitMaxWidthNode = KitMaxWidthNode(maxWidth, align)

    override fun update(node: KitMaxWidthNode) {
        node.maxWidth = maxWidth
        node.align = align
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitMaxWidth"
        properties["maxWidth"] = maxWidth
        properties["align"] = align
    }
}

private class KitMaxWidthNode(var maxWidth: Dp, var align: CapAlign) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(KitMaxWidth.inner(constraints, maxWidth.roundToPx()))
        val width = constraints.constrainWidth(placeable.width)
        return layout(width, placeable.height) {
            placeable.placeRelative(KitMaxWidth.offset(width, placeable.width, align), 0)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        minOf(measurable.minIntrinsicWidth(height), maxWidth.roundToPx())

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        minOf(measurable.maxIntrinsicWidth(height), maxWidth.roundToPx())

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.minIntrinsicHeight(minOf(width, maxWidth.roundToPx()))

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.maxIntrinsicHeight(minOf(width, maxWidth.roundToPx()))
}
