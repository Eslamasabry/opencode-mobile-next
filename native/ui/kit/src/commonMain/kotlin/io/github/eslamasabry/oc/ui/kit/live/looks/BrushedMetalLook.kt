package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Brushed metal: fine horizontal grain like an aluminium case (long stretched noise in two scales plus a hairline
 * speckle), lit from above, with a broad soft highlight gliding slowly across it that picks up a hint of the accent.
 */
public object BrushedMetalLook : LiveLook {
    override val id: String = "brushedMetal"
    override val shader: String = LiveShaderLib.program(BODY)

    override fun uniforms(u: LiveUniforms) {
        u.float("uGrain", GRAIN_DP)
    }

    private const val GRAIN_DP = 0.8f
}

private const val BODY = """
uniform float uGrain;

half4 main(float2 fc) {
    float d = max(uDensity, 1.0);
    float2 p = lvDp(fc);
    float2 sz = uRes / d;

    float coarse = lvNoise(float2(p.x * 0.005, p.y / uGrain * 0.6));
    float fineN = lvNoise(float2(p.x * 0.028 + 5.0, p.y / uGrain * 0.9 + 3.0));
    float hair = lvHash(float2(floor(p.x * 0.02), floor(fc.y)));
    float grain = coarse * 0.5 + fineN * 0.35 + hair * 0.15;

    float top = 1.0 - clamp(p.y / max(sz.y, 1.0), 0.0, 1.0);

    float2 dir = float2(0.8, 0.6);
    float period = 1500.0;
    float pos = fract(dot(p, dir) / period - uTime * 0.012) - 0.5;
    float w = 240.0 / period;
    float hl = exp(-pos * pos / (2.0 * w * w));

    float streak = clamp(0.5 + (grain - 0.5) * 1.4, 0.0, 1.0);
    float k = lvAmt(0.06) * streak + lvAmt(0.025) * top + lvAmt(0.05) * hl * (0.6 + 0.8 * grain);
    float g = lvAmt(0.035) * hl;
    return lvOut(lvLayer(k, g), fc);
}
"""
