package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.drawscope.translate
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
    /**
     * The local hold both shaders run (LiquidHold's localDp, localFree, localSlope, inkKeep), AGSL and SkSL alike. It
     * needs `uniform float4 local` (reach px, free, slope, ink kept; 1 = off), `ss` and `float4 holdSample(float2)` (what
     * is behind at a point, through the colour controls, with its alpha) defined before it.
     *
     * Round two (2026-11-14): the mean is taken over a disc of 36 points laid as many per area everywhere (a golden-angle
     * spiral) instead of three rings of twelve, which put a third of the weight within a third of the reach, so a line of
     * text survived in the mean as a grey bar; it is weighted by alpha, so what lies outside the recorded page (past the
     * screen, past the layer's padding) is not read as black (a dark frame inside a sheet at a long reach). Then the
     * ink-side hold: what looks like the pane's ink (under dark words its blackness, 1 - the largest channel; under light
     * words its whiteness, the smallest channel) past the page's ground round it (the least ink of the disc) keeps
     * local.w; the same amount moves on every channel, so chroma is kept. Where ink-like things cover most of the disc
     * (past 60 %: a dark picture under dark words) it lets go; in the rim's outer half it works at 60 %, so the bend
     * still shows the page's strokes at about half their strength (the bent words there read as grey smudges at full).
     * 37 taps per pixel, as before.
     */
    const val LOCAL_HOLD: String =
        """
float inkOf(float3 c, float lightInk) { return lightInk > 0.5 ? min(c.r, min(c.g, c.b)) : 1.0 - max(c.r, max(c.g, c.b)); }

float3 localHold(float2 at, float3 rgb, float deep, float scale, float lightInk) {
    float3 sum = rgb;
    float wsum = 1.0;
    float ink0 = inkOf(rgb, lightInk);
    float isum = ink0;
    float imin = ink0;
    float imax = ink0;
    for (int i = 0; i < 36; i++) {
        float r = local.x * scale * sqrt((float(i) + 0.5) / 36.0);
        float t = 2.39996323 * float(i);
        float4 s = holdSample(at + float2(cos(t), sin(t)) * r);
        sum += s.rgb * s.a;
        wsum += s.a;
        float k = inkOf(s.rgb, lightInk);
        isum += k * s.a;
        if (s.a > 0.5) {
            imin = min(imin, k);
            imax = max(imax, k);
        }
    }
    float3 mean = sum / wsum;
    float3 dv = rgb - mean;
    float len = length(dv);
    float held = len > local.y ? local.y + (len - local.y) * local.z : len;
    float3 o = len > 0.0001 ? clamp(mean + dv * (held / len), 0.0, 1.0) : rgb;
    o = mix(rgb, o, deep);
    if (local.w < 1.0) {
        // Lines of text cover up to about half the disc; past 60 % ink-like things are the ground itself.
        float share = (isum / wsum - imin) / max(imax - imin, 0.05);
        // In the rim's outer half the ink-side hold is at 60 %: the bend still shows the page's strokes, at half strength.
        float strength = (1.0 - ss(0.6, 0.9, share)) * mix(0.6, 1.0, deep);
        float inkNow = inkOf(o, lightInk);
        if (inkNow > imin) {
            float shift = (inkNow - (imin + (inkNow - imin) * local.w)) * strength;
            o = clamp(lightInk > 0.5 ? o - shift : o + shift, 0.0, 1.0);
        }
    }
    return o;
}
"""

    /** AGSL on Android, SkSL on desktop: the same text. `content` is the pane's blurred, bent backdrop. */
    const val SOURCE: String =
        """
uniform shader content;
uniform float3 knee;
uniform float hold;
uniform float4 box;
uniform float2 corner;
uniform float4 cc;
uniform float4 local;

float sat01(float x) { return clamp(x, 0.0, 1.0); }
float ss(float e0, float e1, float x) { float t = sat01((x - e0) / (e1 - e0)); return t * t * (3.0 - 2.0 * t); }

float3 controlled(float3 rgb) {
    float l = dot(rgb, float3(0.213, 0.715, 0.072));
    return clamp(cc.y * (l + cc.x * (rgb - l)) + (0.5 - 0.5 * cc.y + cc.z), 0.0, 1.0);
}

float3 controls(float2 p) {
    float4 c = float4(content.eval(p));
    return controlled(c.rgb / max(c.a, 0.0001));
}

float4 holdSample(float2 p) {
    float4 c = float4(content.eval(p));
    return float4(controlled(c.rgb / max(c.a, 0.0001)), c.a);
}
""" + LOCAL_HOLD + """
half4 main(float2 q) {
    float4 c = float4(content.eval(q));
    float3 rgb = controls(q);
    // How deep inside the lens's rim this point lies: 0 in the rim's outer half, 1 from its inner end on. The rim, where
    // the bent content must show, keeps its detail and its colour.
    float deep = 1.0;
    if (corner.y > 0.0) {
        float k = clamp(corner.x, 0.0, min(box.z, box.w));
        float2 d = abs(q - (box.xy + box.zw)) - box.zw + k;
        float depth = -(length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - k);
        deep = ss(0.5 * corner.y, corner.y, depth);
    }
    // The local hold (LOCAL_HOLD): the page's detail pressed to its local mean, what looks like the pane's ink lifted to
    // the page's ground; then the knee below, as without it.
    if (local.x > 0.0) rgb = localHold(q, rgb, deep, 1.0, knee.z);
    if (knee.y < 1.0) {
        float lum = dot(rgb, float3(0.213, 0.715, 0.072));
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
    /** The local hold: (reach px, free, slope, ink kept); reach 0 = off, ink kept 1 = off. A 3-long array has no ink hold. */
    val local: FloatArray = floatArrayOf(0f, 0f, 1f, 1f),
) {
    fun set(p: LiveShaderProgram) {
        p.float4("local", local[0], local[1], local[2], local.getOrElse(3) { 1f })
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
                // Off, the knee still says the words' ink: the ink-side hold reads it.
                knee = if (hold.knee) kneeOf(spec) else floatArrayOf(1f, 1f, kneeOf(spec)[2]),
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
                local = localOf(hold, hold.localDp * density),
            )
        }
    }
}

/** [hold]'s local hold as the shaders' `local` uniform, its reach [reachPx]; off (reach 0) when it has none. */
internal fun localOf(hold: io.github.eslamasabry.oc.ui.design.effects.LiquidHold, reachPx: Float): FloatArray =
    if (hold.local && reachPx > 0f) floatArrayOf(reachPx, hold.localFree, hold.localSlope, hold.inkKeep) else floatArrayOf(0f, 0f, 1f, 1f)

/**
 * The plain liquid pane where the platform has no lens but the kit's tests want to see what the pane keeps: its
 * backdrop pressed together by the recipe's colour controls, blurred by the recipe's blur, then through the hold
 * ([LiquidHoldShader]) under the pane's tint, and the rim light Android's library draws ([drawKitRim]). No bend. Used
 * by tests through a fake platform that says it has a lens; Android draws its own (`AndroidLiquidGlass`).
 */
public fun kitLiquidPlainStandIn(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, platform: KitPlatform): Modifier {
    val at = arrayOfNulls<LayoutCoordinates>(1)
    val program = platform.liveShaders?.compile(LiquidHoldShader.SOURCE)
    val rim = KitRimShader.programFor(platform.liveShaders)
    return Modifier.onGloballyPositioned { at[0] = it }
        .clip(shape)
        .drawWithCache {
            val layer = obtainGraphicsLayer()
            val r = spec.recipe.blurDp.dp.toPx()
            // With a local hold, the backdrop on a layer padded by the blur and the mean's reach, as the library pads its
            // own, so neither clamps at the pane's own edge; without one, as before (no padding).
            val pad = if (spec.recipe.hold.local) kotlin.math.ceil(r + spec.recipe.hold.localDp.dp.toPx() + 2f) else 0f
            onDrawBehind {
                val c = at[0] ?: return@onDrawBehind
                val radius = maxOf(shape.topStart.toPx(size, this), shape.topEnd.toPx(size, this), shape.bottomStart.toPx(size, this), shape.bottomEnd.toPx(size, this))
                val colour = floatArrayOf(spec.recipe.saturation, spec.recipe.contrast, spec.recipe.brightness)
                layer.renderEffect =
                    program?.let {
                        LiquidHoldUniforms.of(spec, size.width, size.height, pad, pad, radius, density, colour).set(it)
                        platform.liquidJoinEffect(it, r)
                    } ?: BlurEffect(r, r)
                if (pad <= 0f) {
                    layer.record { backdrop.draw(this, c) }
                    drawLayer(layer)
                } else {
                    val w = size.width.toInt() + 2 * pad.toInt()
                    val h = size.height.toInt() + 2 * pad.toInt()
                    layer.record(androidx.compose.ui.unit.Density(density, fontScale), layoutDirection, androidx.compose.ui.unit.IntSize(w, h)) {
                        translate(pad, pad) { backdrop.draw(this, c) }
                    }
                    translate(-pad, -pad) { drawLayer(layer) }
                }
                drawLiquidTint(spec, shape)
                drawKitRim(spec, shape, rim)
            }
        }
}
