package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import io.github.eslamasabry.oc.ui.design.effects.LiquidRecipe

/*
 * The seam between the kit's liquid glass and the platform that draws it (`KitPlatform.liquidPane`). Android 13+ draws
 * it through Kyant0/AndroidLiquidGlass (`AndroidLiquidGlass`, androidMain only: the library's desktop build targets
 * Java 21 and the desktop app and its tests run on 17). Desktop never asks (its platform has no lens); a fake platform
 * that says it has one gets [kitLiquidStandIn], so the kit's choices stay testable there.
 */

/** What lies behind a liquid pane: draws it at the pane's place ([LayoutCoordinates] of the pane), ground first. */
public class KitLiquidBackdrop(internal val draw: DrawScope.(LayoutCoordinates) -> Unit) {
    /** The platform's own wrapper for this backdrop, made once (Android: the library's `Backdrop`). */
    internal var platformHandle: Any? = null
}

/** One liquid pane's look, resolved by the kit from `LiquidEdge` and handed to the platform to draw. */
@Immutable
public data class KitLiquidPaneSpec(
    val recipe: LiquidRecipe,
    /** The tint (today's Glass tint, thickened), and its lit top-start corner. */
    val fill: Color,
    val fillStart: Color,
    val rtl: Boolean,
    /** The rim light, tinted from the accent; [rimAdds] adds it as light (a dark theme), else lays it over. */
    val rim: Color,
    val rimAdds: Boolean,
    val rimDp: Float,
    /** The soft shadow under the pane (transparent: none). */
    val shadow: Color,
    /** The blur a pane eases into while a transform runs (today's float frost, sigma dp). */
    val frostBlurDp: Float,
)

/** The pane's tint as a brush: lit at its top start, plain from 70 % of the way across (today's Glass face). */
internal fun DrawScope.drawLiquidTint(spec: KitLiquidPaneSpec) {
    if (spec.fillStart == spec.fill) {
        drawRect(spec.fill)
        return
    }
    val start = Offset(if (spec.rtl) size.width else 0f, 0f)
    val end = Offset(if (spec.rtl) 0f else size.width, size.height)
    drawRect(Brush.linearGradient(0f to spec.fillStart, .7f to spec.fill, start = start, end = end))
}

/** Where the platform cannot bend light: the pane's tint alone, clipped to its shape. Never drawn by the desktop app. */
public fun kitLiquidStandIn(spec: KitLiquidPaneSpec, shape: CornerBasedShape): Modifier =
    Modifier.clip(shape).drawBehind { drawLiquidTint(spec) }

