package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Radar: faint range rings and a crosshair around a point a little above the middle; a soft accent beam sweeps round
 * once every 19 seconds, and a few contacts light up as it passes and fade before it comes back (new ones each turn).
 */
public object RadarLook : LiveLook {
    override val id: String = "radar"
    override val shader: String = LiveShaderLib.program(BODY)

    override fun uniforms(u: LiveUniforms) {
        u.float("uRing", RING_DP)
    }

    private const val RING_DP = 64f
}

private const val BODY = """
uniform float uRing;

half4 main(float2 fc) {
    float d = max(uDensity, 1.0);
    float2 sz = uRes / d;
    float2 q = lvDp(fc) - float2(sz.x * 0.5, sz.y * 0.42);
    float r = length(q);

    float ringD = (fract(r / uRing + 0.5) - 0.5) * uRing * d;
    float rings = lvLine(ringD, 0.55) * smoothstep(8.0, 30.0, r);
    float cross = max(lvLine(q.x * d, 0.5), lvLine(q.y * d, 0.5));
    float fade = 1.0 - smoothstep(0.35, 1.0, r / max(sz.y * 0.6, 1.0)) * 0.6;
    float scope = max(rings, cross * 0.6) * fade;

    float sweep = uTime * 0.33;
    float ang = atan(q.y, q.x);
    float behind = fract((sweep - ang) / LV_TAU);
    // Soft on both sides: the tail fades behind the beam and a short lead ramps in ahead of it.
    float beam = max(exp(-behind * 5.0), smoothstep(0.985, 1.0, behind)) * smoothstep(6.0, 40.0, r);
    float edgePx = min(behind, 1.0 - behind) * LV_TAU * r * d;
    float edge = lvLine(edgePx, 0.6) * smoothstep(6.0, 40.0, r) * fade;

    float cs = 36.0;
    float2 cc = floor(q / cs);
    float2 jit = (float2(lvHash(cc + 1.3), lvHash(cc + 2.9)) - 0.5) * cs * 0.6;
    float2 cp = (cc + 0.5) * cs + jit;
    float cBehindRaw = (sweep - atan(cp.y, cp.x)) / LV_TAU;
    float turn = floor(cBehindRaw);
    float cBehind = cBehindRaw - turn;
    float present = step(0.84, lvHash(cc + turn * 13.1)) * smoothstep(30.0, 50.0, length(cp));
    float cd = length(q - cp);
    float dotC = 1.0 - smoothstep(1.7 * d - 0.5, 1.7 * d + 0.5, cd * d);
    float halo = exp(-cd * cd / 20.0);
    float blip = present * exp(-cBehind * 3.5) * max(dotC, halo * 0.45);

    float k = lvAmt(0.05) * scope;
    float g = lvAmt(0.015) + lvAmt(0.045) * beam + lvAmt(0.07) * edge + lvAmt(0.22) * blip;
    return lvOut(lvLayer(k, g), fc);
}
"""
