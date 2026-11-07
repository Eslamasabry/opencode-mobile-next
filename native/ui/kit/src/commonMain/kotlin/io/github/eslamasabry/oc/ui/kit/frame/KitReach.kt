package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/**
 * How far a flat list inside may reach out past its own box into the padding around it, on each side (Dart `KitReach`,
 * `lib/ui/kit/kit_reach.dart`; docs/ux-system/kit-api/KitReach.md): a sheet's rails. A row group with no fill of its
 * own (Solid's flat lists, Paper) uses it to run its rows to the sheet's edges, the way it runs them to the screen's
 * edges on a page, so the rows' words and hairlines start on the sheet title's line instead of one row inset in.
 *
 * `KitSheet` sets it for its body; it is zero everywhere else. A part that reaches out ([KitReachExtend]) sets it back
 * to zero for what it holds, so nothing reaches twice.
 *
 * States: none — a scope that only carries a distance.
 */
public val LocalKitReach: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

/** [content] with [reach] in force on each side (Dart `KitReach(reach:)`; `assert(reach >= 0)`). */
@Composable
public fun KitReach(reach: Dp, content: @Composable () -> Unit) {
    require(reach >= 0.dp) { "reach must not be negative" }
    CompositionLocalProvider(LocalKitReach provides reach, content = content)
}

/** The reach in force here: zero outside any [KitReach] (Dart `KitReach.of`). */
@Composable
public fun kitReach(): Dp = LocalKitReach.current

/**
 * [content] laid out [by] wider on each side and drawn that far out past this box, which keeps its own width (Dart
 * `KitReach.extend`). Inside, the reach is zero again. Zero or less returns [content] as it is.
 */
@Composable
public fun KitReachExtend(by: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (by <= 0.dp) {
        content()
        return
    }
    Box(modifier.kitReachExtend(by)) {
        CompositionLocalProvider(LocalKitReach provides 0.dp, content = content)
    }
}

/**
 * The layout half of [KitReachExtend] (Dart `_RenderKitReach`): the child is measured `2 × by` wider, this box takes
 * the child's width less `2 × by`, and the child sits `by` out past the start edge. Intrinsic widths shrink by
 * `2 × by` (never below zero); intrinsic heights are asked at the wider width.
 */
public fun Modifier.kitReachExtend(by: Dp): Modifier = if (by <= 0.dp) this else this then KitReachElement(by)

internal object KitReachMath {
    /** Dart `_wider`: both width bounds grow by `2 × by`; an unbounded maximum stays unbounded. */
    fun wider(c: Constraints, by: Int): Constraints {
        val twice = 2 * by // F: lib/ui/kit/kit_reach.dart:67
        val maxW = if (c.hasBoundedWidth) c.maxWidth + twice else Constraints.Infinity
        return Constraints(minWidth = c.minWidth + twice, maxWidth = maxW, minHeight = c.minHeight, maxHeight = c.maxHeight)
    }

    /** Dart `computeMin/MaxIntrinsicWidth`: the child's intrinsic width less `2 × by`, clamped at zero. */
    fun narrower(childIntrinsic: Int, by: Int): Int = maxOf(0, childIntrinsic - 2 * by) // F: lib/ui/kit/kit_reach.dart:75
}

private data class KitReachElement(val by: Dp) : ModifierNodeElement<KitReachNode>() {
    override fun create(): KitReachNode = KitReachNode(by)

    override fun update(node: KitReachNode) {
        node.by = by
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitReachExtend"
        properties["by"] = by
    }
}

private class KitReachNode(var by: Dp) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val px = by.roundToPx()
        val placeable = measurable.measure(KitReachMath.wider(constraints, px))
        val width = constraints.constrainWidth(placeable.width - 2 * px) // F: lib/ui/kit/kit_reach.dart:112
        val height = constraints.constrainHeight(placeable.height)
        return layout(width, height) { placeable.place(-px, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        KitReachMath.narrower(measurable.minIntrinsicWidth(height), by.roundToPx())

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        KitReachMath.narrower(measurable.maxIntrinsicWidth(height), by.roundToPx())

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.minIntrinsicHeight(width + 2 * by.roundToPx()) // F: lib/ui/kit/kit_reach.dart:89

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.maxIntrinsicHeight(width + 2 * by.roundToPx()) // F: lib/ui/kit/kit_reach.dart:93
}
