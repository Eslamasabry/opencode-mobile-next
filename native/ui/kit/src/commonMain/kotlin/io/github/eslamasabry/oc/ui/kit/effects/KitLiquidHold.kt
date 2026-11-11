package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform

/*
 * What the plain liquid pane holds back (words on glass 3, owner 2026-11-11). The joined shader (KitLiquidJoin.kt) has
 * held the light behind joined glass past a luminance knee since 2026-11-11; a sheet, and a menu that is not joined,
 * draw through the vendored library's pane, which had none: a bright button behind a sheet's "Copy path" was a bright
 * blurry blob. This is the same knee as one more runtime-shader effect after the pane's blur and lens (Android 13+,
 * `AndroidLiquidGlass`), and the colour that is left is limited (LiquidEdge.heldRgb, the reference this
 * shader is tested against). Only the roles whose recipe says so (LiquidHold: the menu and the sheet).
 */
internal object LiquidHoldShader {
    /** AGSL on Android, SkSL on desktop: the same text. `content` is the pane's blurred, bent backdrop. */
    const val SOURCE: String =
        """
uniform shader content;
uniform float3 knee;
uniform float hold;
uniform float4 box;
uniform float2 corner;
uniform float4 cc;

float sat01(float x) { return clamp(x, 0.0, 1.0); }
float ss(float e0, float e1, float x) { float t = sat01((x - e0) / (e1 - e0)); return t * t * (3.0 - 2.0 * t); }

half4 main(float2 q) {
    float4 c = float4(content.eval(q));
    float a = max(c.a, 0.0001);
    float3 rgb = c.rgb / a;
    float l = dot(rgb, float3(0.213, 0.715, 0.072));
    rgb = clamp(cc.y * (l + cc.x * (rgb - l)) + (0.5 - 0.5 * cc.y + cc.z), 0.0, 1.0);
    if (knee.y < 1.0) {
        float lum = dot(rgb, float3(0.213, 0.715, 0.072));
        float deep = 1.0;
        if (corner.y > 0.0) {
            float k = clamp(corner.x, 0.0, min(box.z, box.w));
            float2 d = abs(q - (box.xy + box.zw)) - box.zw + k;
            float depth = -(length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - k);
            deep = ss(0.5 * corner.y, corner.y, depth);
        }
        float3 scaled;
        float grey;
        if (knee.z > 0.5) {
            grey = lum > knee.x ? knee.x + (lum - knee.x) * knee.y : lum;
            scaled = rgb * (grey / max(lum, 0.0001));
        } else {
            float inv = 1.0 - lum;
            float heldInv = inv > knee.x ? knee.x + (inv - knee.x) * knee.y : inv;
            grey = 1.0 - heldInv;
            scaled = 1.0 - (1.0 - rgb) * (heldInv / max(inv, 0.0001));
        }
        float chroma = max(scaled.r, max(scaled.g, scaled.b)) - min(scaled.r, min(scaled.g, scaled.b));
        float easy = 0.4 * hold;
        float limited = (hold > 0.0 && chroma > easy) ? (easy + (hold - easy) * (1.0 - exp(-(chroma - easy) / (hold - easy)))) / chroma : 1.0;
        rgb = mix(float3(grey), scaled, mix(1.0, limited, deep));
        rgb = clamp(rgb, 0.0, 1.0);
    }
    return half4(float4(rgb * c.a, c.a));
}
"""
}

/**
 * Every uniform of [LiquidHoldShader], set before every draw. [knee] is (knee, slope, dark) as [kneeOf]; slope 1 = off.
 * [box] is the pane in the effect layer's pixels (left, top, half width, half height), [corner] its corner radius and
 * the rim's width there, [cc] the colour controls to apply inside the shader (the identity on Android, where the
 * library applies them before the blur; the recipe's on the desktop stand-in).
 */
internal class LiquidHoldUniforms(
    val knee: FloatArray,
    val hold: Float,
    val left: Float,
    val top: Float,
    val halfWidth: Float,
    val halfHeight: Float,
    val radius: Float,
    val rimPx: Float,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
) {
    fun set(p: LiveShaderProgram) {
        p.float3("knee", knee[0], knee[1], knee[2])
        p.float("hold", hold)
        p.float4("box", left, top, halfWidth, halfHeight)
        p.float2("corner", radius, rimPx)
        p.float4("cc", saturation, contrast, brightness, 0f)
    }

    companion object {
        val IDENTITY_CC: FloatArray = floatArrayOf(1f, 1f, 0f)

        /** [spec]'s hold for a pane of [width] x [height] px with its box at ([left], [top]) in the effect layer. */
        fun of(
            spec: KitLiquidPaneSpec,
            width: Float,
            height: Float,
            left: Float,
            top: Float,
            radius: Float,
            density: Float,
            colour: FloatArray,
        ): LiquidHoldUniforms {
            val hold = spec.recipe.hold
            return LiquidHoldUniforms(
                knee = if (hold.knee) kneeOf(spec) else KNEE_OFF,
                hold = hold.chroma,
                left = left,
                top = top,
                halfWidth = width / 2f,
                halfHeight = height / 2f,
                radius = radius,
                rimPx = LiquidEdge.rimPx(minOf(width, height), density, spec.recipe),
                saturation = colour[0],
                contrast = colour[1],
                brightness = colour[2],
            )
        }
    }
}

/**
 * The plain liquid pane where the platform has no lens but the kit's tests want to see what the pane keeps: its
 * backdrop pressed together by the recipe's colour controls, blurred by the recipe's blur, then through the hold
 * ([LiquidHoldShader]) under the pane's tint. No bend and no rim light (a desktop cannot show those). Used by tests
 * through a fake platform that says it has a lens; Android draws its own (`AndroidLiquidGlass`).
 */
public fun kitLiquidPlainStandIn(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, platform: KitPlatform): Modifier {
    val at = arrayOfNulls<LayoutCoordinates>(1)
    val program = platform.liveShaders?.compile(LiquidHoldShader.SOURCE)
    return Modifier.onGloballyPositioned { at[0] = it }
        .clip(shape)
        .drawWithCache {
            val layer = obtainGraphicsLayer()
            val r = spec.recipe.blurDp.dp.toPx()
            onDrawBehind {
                val c = at[0] ?: return@onDrawBehind
                val radius = maxOf(shape.topStart.toPx(size, this), shape.topEnd.toPx(size, this), shape.bottomStart.toPx(size, this), shape.bottomEnd.toPx(size, this))
                val colour = floatArrayOf(spec.recipe.saturation, spec.recipe.contrast, spec.recipe.brightness)
                layer.renderEffect =
                    program?.let {
                        LiquidHoldUniforms.of(spec, size.width, size.height, 0f, 0f, radius, density, colour).set(it)
                        platform.liquidJoinEffect(it, r)
                    } ?: BlurEffect(r, r)
                layer.record { backdrop.draw(this, c) }
                drawLayer(layer)
                drawLiquidTint(spec, shape)
            }
        }
}
