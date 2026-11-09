package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Silk: wide folds of satin lie diagonally across the page and slowly fold and unfold. Each fold is shaded from its
 * slope as cloth is (lit from the upper left: the rising face catches the light, the falling one sinks into the ground)
 * with a narrow sheen along the crest. The cloth's colour drifts between the accent and the partner across the page,
 * each laid as its own light, so the two never average into grey; the folds settle towards the bottom, where the message
 * box sits.
 *
 * Per pixel: one fbm (four value noises), one more noise for the colour drift, three sines, two exps.
 */
public object SilkLook : LiveLook {
    override val id: String = "silk"

    override val shader: String =
        """
half4 main(float2 fragCoord) {
    float2 p = fragCoord / uRes.y;
    float t = uTime * 0.05;
    float3 ground = float3(uGround.rgb);
    float3 a = float3(uAccent.rgb);
    float3 b = float3(uPartner.rgb);
    float3 g = float3(uGlow.rgb);

    // The cloth: a diagonal coordinate bent by a slow, large noise; folds are its sine.
    float bend = liveFbm(p * 0.8 + float2(t * 0.5, -t * 0.3));
    float v = p.x * 1.1 + p.y * 0.8 + bend * 1.5;
    float phase = v * 3.0 + t * 1.4;
    float slope = cos(phase);                     // +1 on the face turned to the light
    float lit = 0.5 + 0.5 * slope;
    float body = lit * lit * (0.75 + 0.25 * sin(phase * 0.37 - t * 0.6));
    // The crest's sheen: narrow, and a little broader where the cloth bends most.
    float sheen = exp(-(1.0 - slope) * mix(16.0, 9.0, bend));

    // Which colour the cloth is here: a slow drift across the page, sharpened so each hue keeps its own region.
    float hueAt = smoothstep(0.3, 0.7, liveNoise(p * 0.9 + float2(-t * 0.4, t * 0.25)));
    float settle = 1.0 - 0.45 * smoothstep(0.55, 1.0, p.y);
    float k = (0.03 + 0.19 * body) * settle * uIntensity;
    float3 col = liveGlow(ground, a, k * (1.0 - hueAt));
    col = liveGlow(col, b, k * hueAt);
    col = liveGlow(col, g, sheen * 0.13 * settle * uIntensity);
    return liveOut(col, fragCoord);
}
"""

    override fun uniforms(u: LiveUniforms): Unit = Unit
}
