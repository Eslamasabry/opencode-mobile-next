package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.currentValueOf
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
    // A centred page column grows with the pane beside a list (LocalKitPaneColumn).
    this then KitMaxWidthElement(maxWidth, align, followsPane = PaneColumn.follows(maxWidth, align))

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

private data class KitMaxWidthElement(val maxWidth: Dp, val align: CapAlign, val followsPane: Boolean = false) :
    ModifierNodeElement<KitMaxWidthNode>() {
    override fun create(): KitMaxWidthNode = KitMaxWidthNode(maxWidth, align, followsPane)

    override fun update(node: KitMaxWidthNode) {
        node.maxWidth = maxWidth
        node.align = align
        node.followsPane = followsPane
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitMaxWidth"
        properties["maxWidth"] = maxWidth
        properties["align"] = align
    }
}

private class KitMaxWidthNode(var maxWidth: Dp, var align: CapAlign, var followsPane: Boolean) :
    Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {
    /** The cap in force: [maxWidth], grown with the pane's column beside a list (LocalKitPaneColumn). */
    private fun cap(): Dp {
        val column = if (followsPane) currentValueOf(LocalKitPaneColumn) else null
        return if (column == null) maxWidth else PaneColumn.widened(maxWidth, column)
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(KitMaxWidth.inner(constraints, cap().roundToPx()))
        val width = constraints.constrainWidth(placeable.width)
        return layout(width, placeable.height) {
            placeable.placeRelative(KitMaxWidth.offset(width, placeable.width, align), 0)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        minOf(measurable.minIntrinsicWidth(height), cap().roundToPx())

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        minOf(measurable.maxIntrinsicWidth(height), cap().roundToPx())

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.minIntrinsicHeight(minOf(width, cap().roundToPx()))

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.maxIntrinsicHeight(minOf(width, cap().roundToPx()))
}
