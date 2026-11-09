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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import io.github.eslamasabry.oc.ui.kit.foundation.drawGlassSpecular
import io.github.eslamasabry.oc.ui.kit.foundation.sheenDepthPx
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
    /** Frost beyond the window's budget: the rim light stays while the transform holds the lens at frost. */
    val keepRim: Boolean = false,
    /** The specular's glint line as a share of [specular] (the glass look's). */
    val glintShare: Float = io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration.GLINT_SHARE,
    /**
     * The share of the pane's pixels (per side) its backdrop, blur and lens are drawn at, then scaled up; 1 = full. The
     * rim light, the specular and the tint are always drawn at full resolution ([liquidResolutionOf]).
     */
    val resolution: Float = 1f,
)

/**
 * The resolution a pane of [recipe] draws its backdrop at on a platform offering [platform] (smoothness 2026-11-09):
 * panes frosted at least [LIQUID_REDUCED_MIN_BLUR_DP] (the bars, the message box, sheets and menus) take it, since their
 * blur hides the lower resolution and they are the large panes; the lightly frosted chips and the + stay at full
 * resolution, where text behind them would read softer, and they are small.
 */
internal fun liquidResolutionOf(recipe: io.github.eslamasabry.oc.ui.design.effects.LiquidRecipe, platform: Float): Float =
    if (recipe.blurDp >= LIQUID_REDUCED_MIN_BLUR_DP) platform.coerceIn(.25f, 1f) else 1f

/** The least frost (dp) at which a liquid pane draws its backdrop at the platform's reduced resolution. */
internal const val LIQUID_REDUCED_MIN_BLUR_DP: Float = 4f

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
        drawRect(Brush.verticalGradient(0f to sheen, 1f to sheen.copy(alpha = 0f), startY = 0f, endY = sheenDepthPx()))
    }
    if (shape != null && spec.specular.alpha > 0f) {
        // The same crisp specular as Glass's in-flow panes: a bright rim segment at the top start and a thin glint line
        // just inside the top edge (owner 2026-11-09, "crisper glossy glass").
        val top = (shape as? CornerBasedShape)?.let { it.topStart.toPx(size, this) } ?: (size.minDimension / 2f)
        drawGlassSpecular(shape, spec.specular, spec.rtl, minOf(top, size.minDimension / 2f), glintShare = spec.glintShare)
    }
}

/** Where the platform cannot bend light: the pane's tint alone, clipped to its shape. Never drawn by the desktop app. */
public fun kitLiquidStandIn(spec: KitLiquidPaneSpec, shape: CornerBasedShape): Modifier =
    Modifier.clip(shape).drawBehind { drawLiquidTint(spec, shape) }

