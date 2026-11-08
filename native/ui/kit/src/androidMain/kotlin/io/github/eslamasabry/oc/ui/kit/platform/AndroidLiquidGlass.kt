package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.effects.drawLiquidTint

/**
 * Glass's liquid pane through Kyant0/AndroidLiquidGlass (`io.github.kyant0:backdrop` 2.0.1, Apache-2.0), Android 13+.
 * The backdrop is pressed together in contrast and blurred a little, then bent by the lens only inside a rim of
 * min(12 dp, 20 % of the shorter side), with no depth effect and no dispersion; today's Glass tint lies over it, and a
 * soft rim light tinted from the accent runs round it. While a transform the library cannot follow runs ([transform]
 * rising to 1), the lens and the rim light let go and the blur grows to Glass's float frost: plain frost, never a
 * misaligned picture.
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
            },
            highlight = {
                Highlight(
                    width = spec.rimDp.dp,
                    blurRadius = spec.rimDp.dp,
                    alpha = 1f - transform().coerceIn(0f, 1f),
                    style = HighlightStyle.Default(color = spec.rim, blendMode = if (spec.rimAdds) BlendMode.Plus else BlendMode.SrcOver),
                )
            },
            shadow = if (spec.shadow.alpha > 0f) ({ Shadow(radius = 24.dp, color = spec.shadow) }) else null,
            onDrawSurface = { drawLiquidTint(spec) },
        )
    }

    private class LibraryBackdrop(val kit: KitLiquidBackdrop) : Backdrop {
        override val isCoordinatesDependent: Boolean = true

        override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
            val c = coordinates?.takeIf { it.isAttached } ?: return
            kit.draw(this, c)
        }
    }
}
