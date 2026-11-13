package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.RimLight
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram
import kotlin.math.ceil

/*
 * The plain liquid pane's rim light (owner 2026-11-13: a pane whose edge disappears at a corner reads as a broken
 * shape). Android draws it through the vendored library's highlight (a stroke of the pane's own outline, twice the rim's
 * width, clipped to that outline, so it follows the pane's real corners and can never be cut by a clip of another
 * radius) with this shader as its style (`AndroidLiquidGlass`); the desktop stand-ins draw the same stroke with the same
 * shader text, so the kit's tests see the rim Android draws. The joined body's shader (KitLiquidJoin.kt) uses the same
 * strength ([RimLight.SHADER_LIT]) along the body's outline.
 */
internal object KitRimShader {
    /**
     * The library's directional highlight (`DefaultHighlightShaderString`, Apache-2.0, Kyant), its direction field line
     * for line, with [RimLight.lit]'s floor. One fix: the library picks the corner radius at the raw coordinate, so every
     * corner took the bottom-end one; here it is picked at the coordinate from the pane's centre, so each corner's light
     * turns over its own radius (no change for a pane with four equal corners). `size` the pane in px, `cornerRadii` its corners (top-left, top-right,
     * bottom-right, bottom-left, px), `rimColor` the rim's colour (opaque; the stroke's alpha carries its strength).
     */
    val SOURCE: String =
        """
uniform float2 size;
uniform float4 cornerRadii;
uniform float4 rimColor;
uniform float falloff;
uniform float rimFloor;
""" + RimLight.SHADER_LIT + """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float d = dot(grad, float2(0.70710678, 0.70710678));
    float intensity = rimLit(d, falloff, rimFloor);
    return half4(float4(rimColor.rgb * intensity, intensity));
}
"""

    /** The corners of [shape] on a pane of [size] as the shader takes them: top-left, top-right, bottom-right, bottom-left. */
    fun cornerRadii(shape: CornerBasedShape, size: Size, density: Density, direction: LayoutDirection): FloatArray {
        val max = size.minDimension / 2f
        val ltr = direction == LayoutDirection.Ltr
        val ts = shape.topStart.toPx(size, density)
        val te = shape.topEnd.toPx(size, density)
        val be = shape.bottomEnd.toPx(size, density)
        val bs = shape.bottomStart.toPx(size, density)
        return floatArrayOf(
            (if (ltr) ts else te).coerceAtMost(max),
            (if (ltr) te else ts).coerceAtMost(max),
            (if (ltr) be else bs).coerceAtMost(max),
            (if (ltr) bs else be).coerceAtMost(max),
        )
    }

    /** Every uniform, set before every draw. */
    fun set(p: LiveShaderProgram, size: Size, radii: FloatArray, color: Color) {
        p.float2("size", size.width, size.height)
        p.float4("cornerRadii", radii[0], radii[1], radii[2], radii[3])
        p.float4("rimColor", color.red, color.green, color.blue, 1f)
        p.float("falloff", RimLight.FALLOFF)
        p.float("rimFloor", RimLight.FLOOR)
    }

    private val compiled = mutableMapOf<LiveShaderCompiler, LiveShaderProgram?>()

    /** The shader compiled once per compiler (desktop's in the kit's tests); null where none compiles it. */
    fun programFor(compiler: LiveShaderCompiler?): LiveShaderProgram? = compiler?.let { compiled.getOrPut(it) { it.compile(SOURCE) } }
}

/**
 * A stand-in pane's rim light, as the library draws it on Android: a stroke of [shape]'s outline twice the rim's width,
 * clipped to the outline, through [KitRimShader]; at [spec]'s rim alpha, added in a dark theme. Nothing without a
 * [program] (a platform with no shader compiler) or a rim.
 */
internal fun DrawScope.drawKitRim(spec: KitLiquidPaneSpec, shape: CornerBasedShape, program: LiveShaderProgram?) {
    if (program == null || spec.rim.alpha <= 0f || spec.rimDp <= 0f || size.width <= 0f || size.height <= 0f) return
    val outline = shape.createOutline(size, layoutDirection, this)
    val w = ceil(spec.rimDp.dp.toPx().coerceAtMost(size.minDimension / 2f)) * 2f
    KitRimShader.set(program, size, KitRimShader.cornerRadii(shape, size, this, layoutDirection), spec.rim.copy(alpha = 1f))
    clipPath(Path().apply { addOutline(outline) }) {
        drawOutline(
            outline,
            program.brush,
            alpha = spec.rim.alpha,
            style = Stroke(w),
            blendMode = if (spec.rimAdds) BlendMode.Plus else BlendMode.SrcOver,
        )
    }
}
