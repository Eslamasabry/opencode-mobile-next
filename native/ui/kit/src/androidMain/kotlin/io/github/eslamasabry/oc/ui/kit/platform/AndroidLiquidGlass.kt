package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.RuntimeShaderCache
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.Shadow
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.RimLight
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.effects.KitRimShader
import io.github.eslamasabry.oc.ui.kit.effects.LiquidHoldShader
import io.github.eslamasabry.oc.ui.kit.effects.LiquidHoldUniforms
import io.github.eslamasabry.oc.ui.kit.effects.drawLiquidTint

/**
 * Glass's liquid pane through Kyant0/AndroidLiquidGlass (`io.github.kyant0:backdrop` 2.0.1, Apache-2.0), Android 13+.
 * The backdrop is pressed together in contrast and blurred a little, then bent by the lens only inside a rim of
 * min(12 dp, 20 % of the shorter side), with no depth effect and no dispersion; today's Glass tint lies over it, and a
 * crisp white rim light runs round it, brightest at its top-start and bottom-end arcs and never below RimLight's floor. While a transform the library cannot follow runs ([transform]
 * rising to 1), the lens and the rim light let go and the blur grows to Glass's float frost: plain frost, never a
 * misaligned picture. A frosted pane draws its backdrop at [KitLiquidPaneSpec.resolution] (half on Android) and the
 * library scales it up; the rim light, specular and tint are drawn at full resolution.
 */
internal object AndroidLiquidGlass {
    fun pane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
        val library = (backdrop.platformHandle as? Backdrop) ?: LibraryBackdrop(backdrop).also { backdrop.platformHandle = it }
        val recipe = spec.recipe
        return Modifier.drawBackdrop(
            backdrop = library,
            shape = { shape },
            effects = {
                val t = transform().coerceIn(0f, 1f)
                colorControls(brightness = recipe.brightness, contrast = recipe.contrast, saturation = recipe.saturation)
                blur((recipe.blurDp + (spec.frostBlurDp - recipe.blurDp) * t).dp.toPx())
                if (size.isSpecified && t < 1f) {
                    lens(
                        LiquidEdge.rimPx(size.minDimension, density, recipe),
                        recipe.refractionDp.dp.toPx() * (1f - t),
                        depthEffect = recipe.depth,
                    )
                }
                // The sheet's and the menu's glass hold back the light and the colour behind their words, after the blur
                // and the bend (the join shader's knee for the plain pane; LiquidHold). Every uniform, every draw.
                if (recipe.hold.knee && size.isSpecified) {
                    val u =
                        LiquidHoldUniforms.of(
                            spec = spec,
                            width = size.width,
                            height = size.height,
                            left = padding,
                            top = padding,
                            radius = cornerRadiusPx(shape, size, this),
                            density = density,
                            colour = LiquidHoldUniforms.IDENTITY_CC,
                        )
                    runtimeShaderEffect("OcHold", LiquidHoldShader.SOURCE, "content") {
                        setFloatUniform("knee", u.knee[0], u.knee[1], u.knee[2])
                        setFloatUniform("hold", u.hold)
                        setFloatUniform("box", u.left, u.top, u.halfWidth, u.halfHeight)
                        setFloatUniform("corner", u.radius, u.rimPx)
                        setFloatUniform("cc", u.saturation, u.contrast, u.brightness, 0f)
                    }
                }
            },
            highlight = {
                // A crisp rim (owner 2026-11-09): 1 dp, no blur; the directional light makes its top-start (and
                // bottom-end) arc the brightest, and its floor keeps a hairline at the two other corners (owner
                // 2026-11-13: a pane whose edge disappears at a corner reads as a broken shape).
                Highlight(
                    width = spec.rimDp.dp,
                    blurRadius = 0.dp,
                    alpha = if (spec.keepRim) 1f else 1f - transform().coerceIn(0f, 1f),
                    style = RimStyle(color = spec.rim, blendMode = if (spec.rimAdds) BlendMode.Plus else BlendMode.SrcOver),
                )
            },
            shadow = if (spec.shadow.alpha > 0f) ({ Shadow(radius = 24.dp, color = spec.shadow) }) else null,
            onDrawSurface = { drawLiquidTint(spec, shape) },
            // The backdrop layer (colour controls, blur, lens) at the pane's resolution; rim light, specular and tint
            // above stay at full resolution (third_party/backdrop/README.md).
            resolutionScale = spec.resolution,
        )
    }

    /** The largest corner of [shape] on a pane of [size], px (the hold's inside-the-rim test treats the pane as one rounded box). */
    private fun cornerRadiusPx(shape: CornerBasedShape, size: Size, density: Density): Float =
        maxOf(
            shape.topStart.toPx(size, density),
            shape.topEnd.toPx(size, density),
            shape.bottomStart.toPx(size, density),
            shape.bottomEnd.toPx(size, density),
        ).coerceAtMost(size.minDimension / 2f)

    /**
     * The library's highlight style with the kit's rim shader ([KitRimShader]: the library's directional light with
     * RimLight's floor). The library strokes the pane's own outline with it, clipped to that outline, and lays it on
     * with [blendMode]; the paint's [color] alpha is the rim's strength at its peak.
     */
    private data class RimStyle(override val color: Color, override val blendMode: BlendMode) : HighlightStyle {
        override fun DrawScope.createShader(shape: Shape, runtimeShaderCache: RuntimeShaderCache): RuntimeShader? {
            if (!isRuntimeShaderSupported()) return null
            val corners = shape as? CornerBasedShape
            val radii =
                corners?.let { KitRimShader.cornerRadii(it, size, this, layoutDirection) }
                    ?: FloatArray(4) { size.minDimension / 2f }
            return runtimeShaderCache.obtainRuntimeShader("OcRim", KitRimShader.SOURCE).apply {
                setFloatUniform("size", size.width, size.height)
                setFloatUniform("cornerRadii", radii[0], radii[1], radii[2], radii[3])
                setFloatUniform("rimColor", color.red, color.green, color.blue, 1f)
                setFloatUniform("falloff", RimLight.FALLOFF)
                setFloatUniform("rimFloor", RimLight.FLOOR)
            }
        }
    }

    private class LibraryBackdrop(val kit: KitLiquidBackdrop) : Backdrop {
        override val isCoordinatesDependent: Boolean = true

        override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
            val c = coordinates?.takeIf { it.isAttached } ?: return
            kit.draw(this, c)
        }
    }
}
