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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp
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

    /**
     * The backdrop's two parts, when it has them: the picture under the content and the content alone. A modal pane
     * (a sheet, a dialog, a menu, the dock's list) draws its picture whole and its content only in a band along the
     * pane's open edges ([kitModalView]). Null: one picture, drawn whole.
     */
    internal var layers: KitBackdropLayers? = null
}

/**
 * A backdrop in two parts: [under] (the page's ground and its live background) and [page] (the page's content: its
 * controls, cards and words), both drawn at a pane's place; [pictured] says whether [under] holds a picture of the page's
 * background here (false: the content is the only picture there is, and a modal pane shows it whole, as before).
 */
internal class KitBackdropLayers(
    val under: DrawScope.(LayoutCoordinates) -> Unit,
    val page: DrawScope.(LayoutCoordinates) -> Unit,
    val pictured: (LayoutCoordinates) -> Boolean,
)

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
    /** How tightly the rim light gathers at its lit arcs ([io.github.eslamasabry.oc.ui.design.effects.RimLight.FALLOFF]). */
    val rimFalloff: Float = io.github.eslamasabry.oc.ui.design.effects.RimLight.FALLOFF,
    /** The least the rim ever is, as a share of its peak ([io.github.eslamasabry.oc.ui.design.effects.RimLight.FLOOR]). */
    val rimFloor: Float = io.github.eslamasabry.oc.ui.design.effects.RimLight.FLOOR,
    /** The light's direction, degrees from the start edge (45: the top-start / bottom-end diagonal). */
    val lightDeg: Float = io.github.eslamasabry.oc.ui.design.effects.GlassTuning.LIGHT_DEG,
    /** The luminance knee that holds back bright content behind words, and the share of the light past it kept. */
    val knee: Float = io.github.eslamasabry.oc.ui.design.effects.LiquidJoin.KNEE,
    val kneeSlope: Float = io.github.eslamasabry.oc.ui.design.effects.LiquidJoin.KNEE_SLOPE,
    /** The lens's colour split at the edge, 0-1 (0: none, the shipped glass). */
    val dispersion: Float = 0f,
    /** The bend's profile: -1 a soft dome, 0 the shipped circle, 1 a sharp bevel at the rim (B-609). */
    val bendShape: Float = io.github.eslamasabry.oc.ui.design.effects.GlassTuning.SHIPPED_BEND_SHAPE,
    /** How far the specular's bright rim segment reaches from its corner, dp (the shipped glass draws no specular). */
    val specularReachDp: Float = io.github.eslamasabry.oc.ui.kit.foundation.SPECULAR_REACH_DP,
    /** The inner shadow along the edge (alpha 0: none, the shipped glass) and its size in dp. */
    val innerShadow: Float = 0f,
    val innerShadowDp: Float = io.github.eslamasabry.oc.ui.design.effects.GlassTuning.INNER_SHADOW_DP,
    /** Live tilt + scroll specular light source read in draw phase (B-610); null uses static [lightDeg]. */
    val liveLight: KitGlassLight? = null,
) {
    /** The light's direction as a unit vector in the pane's pixels (y down). */
    internal val lightDir: FloatArray
        get() {
            val a = lightDeg * (kotlin.math.PI.toFloat() / 180f)
            return if (lightDeg == io.github.eslamasabry.oc.ui.design.effects.GlassTuning.LIGHT_DEG) floatArrayOf(DIAGONAL, DIAGONAL)
            else floatArrayOf(kotlin.math.cos(a), kotlin.math.sin(a))
        }

    /** Live light direction read strictly in the draw phase (tilts/scrolls with zero recomposition, B-610). */
    internal fun liveLightDir(): FloatArray = liveLight?.lightDir(lightDeg) ?: lightDir
}

/** The shipped light's direction component (1/sqrt 2), as the shaders had it. */
private const val DIAGONAL: Float = 0.70710678f

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
    drawGlassBevelAndLift(spec, shape, f)
    if (shape != null && spec.specular.alpha > 0f) {
        // The same crisp specular as Glass's in-flow panes: a bright rim segment at the top start and a thin glint line
        // just inside the top edge (owner 2026-11-09, "crisper glossy glass").
        val top = (shape as? CornerBasedShape)?.let { it.topStart.toPx(size, this) } ?: (size.minDimension / 2f)
        drawGlassSpecular(shape, spec.specular, spec.rtl, minOf(top, size.minDimension / 2f), glintShare = spec.glintShare, reachDp = spec.specularReachDp)
    }
}

private const val FLIP_MIDPOINT: Float = .5f
private const val LIGHT_LIFT_TOP: Float = .56f
private const val LIGHT_LIFT_MID_STOP: Float = .45f
private const val LIGHT_LIFT_MID: Float = .34f
private const val LIGHT_LIFT_BOT: Float = .24f
private const val LIGHT_BEVEL_DP: Int = 8
private const val LIGHT_BEVEL_SHARE: Float = .32f
private const val LIGHT_BEVEL_ALPHA: Float = .68f
private const val LIGHT_EDGE_START: Float = .07f
private const val LIGHT_EDGE_END: Float = .16f
private const val LIGHT_EDGE_STROKE_DP: Float = 1.25f
private const val DARK_SHADE_START: Float = .66f
private const val DARK_CUSHION_ALPHA: Float = .22f
private const val DARK_SHADE_ALPHA: Float = .28f

/**
 * Physical 3D bevel depth, dark-mode center legibility cushion, and light-mode lifted luminous glass body + subtle
 * outer definition (so light glass reads as sculpted bright glass instead of a flat tinted rectangle).
 */
private fun DrawScope.drawGlassBevelAndLift(spec: KitLiquidPaneSpec, shape: Shape?, flip: Float) {
    if (size.width <= 0f || size.height <= 0f) return
    val lightFace = (spec.rimAdds && flip > FLIP_MIDPOINT) || (!spec.rimAdds && flip <= FLIP_MIDPOINT)
    if (lightFace) {
        // Lifted luminous pure-white glass body (brighter than underlying content, Apple iOS 26 light mode).
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = LIGHT_LIFT_TOP),
                LIGHT_LIFT_MID_STOP to Color.White.copy(alpha = LIGHT_LIFT_MID),
                1f to Color.White.copy(alpha = LIGHT_LIFT_BOT),
                startY = 0f,
                endY = size.height,
            )
        )
        // Soft inner top white bevel highlight + subtle dark-glass outer contour so silhouette pops off bright pages.
        val bevelH = minOf(LIGHT_BEVEL_DP.dp.toPx(), size.height * LIGHT_BEVEL_SHARE)
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = LIGHT_BEVEL_ALPHA),
                1f to Color.Transparent,
                startY = 0f,
                endY = bevelH,
            )
        )
        if (shape != null && spec.rim.alpha > 0f) {
            val edgeBrush = Brush.linearGradient(
                0f to Color.Black.copy(alpha = LIGHT_EDGE_START),
                1f to Color.Black.copy(alpha = LIGHT_EDGE_END),
                start = Offset(if (spec.rtl) size.width else 0f, 0f),
                end = Offset(if (spec.rtl) 0f else size.width, size.height),
            )
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, edgeBrush, style = Stroke(LIGHT_EDGE_STROKE_DP.dp.toPx()))
        }
    } else {
        // Dark mode: subtle adaptive dimming cushion for label clarity + soft bottom-inner bevel shade for thickness.
        val shadeTop = size.height * DARK_SHADE_START
        drawRect(Color.Black.copy(alpha = DARK_CUSHION_ALPHA))
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = DARK_SHADE_ALPHA),
                startY = shadeTop,
                endY = size.height,
            )
        )
    }
}

/**
 * Where the platform cannot bend light: the pane's tint, clipped to its shape, and, given the platform's [shaders], the
 * rim light Android's library draws ([drawKitRim]). Never drawn by the desktop app; the kit's tests draw it.
 */
public fun kitLiquidStandIn(
    spec: KitLiquidPaneSpec,
    shape: CornerBasedShape,
    shaders: io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler? = null,
): Modifier {
    val rim = KitRimShader.programFor(shaders)
    return Modifier.clip(shape).drawBehind {
        drawLiquidTint(spec, shape)
        drawKitRim(spec, shape, rim)
    }
}

