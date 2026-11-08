package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.LayoutCoordinates
import io.github.eslamasabry.oc.ui.design.effects.LiquidRecipe

/*
 * The seam between the kit's liquid glass and the platform that draws it (`KitPlatform.liquidPane`). Android 13+ draws
 * it through Kyant0/AndroidLiquidGlass (`AndroidLiquidGlass`, androidMain only: the library's desktop build targets
 * Java 21 and the desktop app and its tests run on 17). Desktop never asks (its platform has no lens); a fake platform
 * that says it has one gets [kitLiquidStandIn], so the kit's choices stay testable there.
 */

/** What lies behind a liquid pane: draws it at the pane's place ([LayoutCoordinates] of the pane), ground first. */
public class KitLiquidBackdrop(
    /** How many times the source has recorded: a sampler reads the content again only when it moved. */
    internal val version: () -> Int = { 0 },
    internal val draw: DrawScope.(LayoutCoordinates) -> Unit,
) {
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
    /** The top sheen: this colour at the top edge, fading out over the first 30 % of the pane (transparent: none). */
    val sheen: Color = Color.Transparent,
    /** The specular highlight along the top-start edge, fading along the edge from that corner (transparent: none). */
    val specular: Color = Color.Transparent,
    /**
     * The other theme's face ([flipFill], [flipFillStart], [flipSheen]) the pane cross-fades to while its words flip to
     * the other theme's ink over content of the other brightness ([flip]): no plate behind the words, so the face flips
     * with them (owner 2026-11-08).
     */
    val flipFill: Color = fill,
    val flipFillStart: Color = fillStart,
    val flipSheen: Color = sheen,
    val flip: LiquidFlip? = null,
)

/** How far a liquid pane has flipped to the other theme's ink and face (0: its own; 1: the other). Snapshot state. */
@androidx.compose.runtime.Stable
public class LiquidFlip internal constructor() {
    internal val progress: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> =
        androidx.compose.animation.core.Animatable(0f)
}

/**
 * The pane's tint as a brush: lit at its top start, plain from 70 % of the way across (today's Glass face); then the top
 * sheen, and, given its [shape], the specular highlight along the top-start edge.
 */
internal fun DrawScope.drawLiquidTint(spec: KitLiquidPaneSpec, shape: Shape? = null) {
    val start = Offset(if (spec.rtl) size.width else 0f, 0f)
    val end = Offset(if (spec.rtl) 0f else size.width, size.height)
    // Read in the draw: the face redraws as the flip runs.
    val f = spec.flip?.progress?.value ?: 0f
    val fill = if (f > 0f) lerp(spec.fill, spec.flipFill, f) else spec.fill
    val fillStart = if (f > 0f) lerp(spec.fillStart, spec.flipFillStart, f) else spec.fillStart
    val sheen = if (f > 0f) lerp(spec.sheen, spec.flipSheen, f) else spec.sheen
    if (fillStart == fill) {
        drawRect(fill)
    } else {
        drawRect(Brush.linearGradient(0f to fillStart, .7f to fill, start = start, end = end))
    }
    if (sheen.alpha > 0f) {
        drawRect(Brush.verticalGradient(0f to sheen, 1f to sheen.copy(alpha = 0f), startY = 0f, endY = size.height * SHEEN_SHARE))
    }
    if (shape != null && spec.specular.alpha > 0f) drawSpecular(shape, spec.specular, spec.rtl)
}

/**
 * The specular highlight along a pane's top-start edge: a soft line inside the edge, brightest at the top-start corner
 * and gone [SPECULAR_REACH] of the way to the bottom-end one (Apple's light from above, never a white hairline).
 */
internal fun DrawScope.drawSpecular(shape: Shape, color: Color, rtl: Boolean) {
    val w = SPECULAR_DP.dp.toPx()
    if (size.width <= w || size.height <= w) return
    val start = Offset(if (rtl) size.width else 0f, 0f)
    val end = Offset(if (rtl) 0f else size.width, size.height)
    val brush = Brush.linearGradient(0f to color, SPECULAR_REACH to color.copy(alpha = 0f), start = start, end = end)
    inset(w / 2f) { drawOutline(shape.createOutline(size, layoutDirection, this), brush, style = Stroke(w)) }
}

/** The share of a pane's height its sheen fades over. */
internal const val SHEEN_SHARE: Float = .30f

/** The specular line's width in dp, and how far along the diagonal it fades out. */
internal const val SPECULAR_DP: Float = 1.25f
internal const val SPECULAR_REACH: Float = .45f

/** Where the platform cannot bend light: the pane's tint alone, clipped to its shape. Never drawn by the desktop app. */
public fun kitLiquidStandIn(spec: KitLiquidPaneSpec, shape: CornerBasedShape): Modifier =
    Modifier.clip(shape).drawBehind { drawLiquidTint(spec, shape) }

