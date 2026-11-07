package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Honeycomb: a fine hexagon lattice like a carbon panel, each cell faintly shaded from its top edge; a wide soft band
 * of accent light slides diagonally across the panel and lifts the cell edges it passes over.
 */
public object HoneycombLook : LiveLook {
    override val id: String = "honeycomb"
    override val shader: String = LiveShaderLib.LAYER + BODY

    override fun uniforms(u: LiveUniforms) {
        u.float("uHex", HEX_DP)
    }

    private const val HEX_DP = 18f
}

private const val BODY = """
uniform float uHex;

// Offset from the nearest hexagon centre (xy) and that cell's id (zw), pointy-top lattice with unit spacing.
float4 hcCell(float2 p) {
    float2 s = float2(1.0, 1.7320508);
    float4 c = floor(float4(p, p - float2(0.5, 1.0)) / s.xyxy) + 0.5;
    float4 h = float4(p - c.xy * s, p - (c.zw + 0.5) * s);
    return dot(h.xy, h.xy) < dot(h.zw, h.zw) ? float4(h.xy, c.xy) : float4(h.zw, c.zw + 0.5);
}

half4 main(float2 fc) {
    float d = max(uDensity, 1.0);
    float2 p = lvDp(fc);
    float4 h = hcCell(p / uHex);
    float2 a = abs(h.xy);
    float hd = max(dot(a, float2(0.5, 0.8660254)), a.x);
    float edgePx = (0.5 - hd) * uHex * d;
    float edge = 1.0 - smoothstep(0.35, 1.25, edgePx);

    float shade = 0.5 + 0.5 * (h.y * 1.6) + (lvHash(h.zw * 3.7) - 0.5) * 0.5;

    float2 dir = float2(0.94, 0.34);
    float along = dot(p, dir);
    float period = 1100.0;
    float pos = fract(along / period - uTime * 0.02) - 0.5;
    float bandW = 150.0 / period;
    float band = exp(-pos * pos / (2.0 * bandW * bandW));

    float k = lvAmt(0.025) * clamp(shade, 0.0, 1.0) + lvAmt(0.06) * edge * (0.45 + 0.55 * band);
    float g = lvAmt(0.035) * band + lvAmt(0.14) * edge * band;
    return lvOut(lvLayer(k, g), fc);
}
"""
