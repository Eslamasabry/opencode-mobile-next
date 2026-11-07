package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Aurora drift: two curtains of light hang in the upper part of the page, each with a bright lower hem fading upward
 * through faint vertical rays, over a slow drifting haze. Accent and partner colours; the lower half stays calm.
 */
public object AuroraLook : LiveLook {
    override val id: String = "aurora"

    override val shader: String =
        """
float curtain(float2 p, float t, float k) {
    float wave = 0.05 * sin(p.x * 2.6 + t * 1.3 + k * 2.1) + 0.11 * (liveFbm(float2(p.x * 1.1 + t * 0.45 + k * 4.0, k + t * 0.2)) - 0.45);
    float y0 = 0.26 + 0.14 * k + wave;
    float d = y0 - p.y;                       // > 0 above the hem
    float above = exp(-max(d, 0.0) * mix(5.0, 8.0, k));
    float below = exp(-max(-d, 0.0) * 22.0);
    float rays = 0.72 + 0.28 * liveNoise(float2(p.x * 14.0 + k * 9.0 + t * 0.6, t * 0.35));
    return above * below * rays;
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / uRes.y;
    float t = uTime * 0.05;
    float3 ground = float3(uGround.rgb);
    float3 a = float3(uAccent.rgb);
    float3 b = float3(uPartner.rgb);
    float3 g = float3(uGlow.rgb);

    float haze = liveFbm(p * 1.5 + float2(t * 0.6, -t * 0.4));
    float3 col = liveGlow(ground, mix(a, b, haze), smoothstep(0.35, 0.85, haze) * 0.10 * uIntensity);

    float aspect = uRes.x / uRes.y;
    float2 q = float2(p.x / max(aspect, 0.001) * 1.2, p.y);
    float c0 = curtain(q, t, 0.0);
    float c1 = curtain(q + float2(0.37, 0.0), t * 0.8, 1.0);
    float fade = smoothstep(0.95, 0.15, p.y);
    float3 tint0 = mix(a, g, 0.5 + 0.5 * sin(q.x * 2.0 + t));
    col = liveGlow(col, tint0, c0 * fade * 0.42 * uIntensity);
    col = liveGlow(col, mix(b, a, 0.35), c1 * fade * 0.30 * uIntensity);
    return liveOut(col, fragCoord);
}
"""

    override fun uniforms(u: LiveUniforms): Unit = Unit
}
