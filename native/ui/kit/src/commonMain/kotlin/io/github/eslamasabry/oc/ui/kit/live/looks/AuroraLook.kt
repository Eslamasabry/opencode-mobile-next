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

// A soft round field of colour (0..1), centre c, radius r, in the page's own units.
float blob(float2 p, float2 c, float r) {
    float2 d = (p - c) / r;
    return exp(-dot(d, d));
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / uRes.y;
    float t = uTime * 0.05 * uSpeed;
    float vs = float(uVibe.x);
    float va = float(uVibe.z);
    float z = float(uVibe.w);
    // 0 at Calm, 1 at Playful: how much of the extra hues and fields show.
    float multi = clamp((z - 1.0) * 1.6, 0.0, 1.0);
    float3 ground = float3(uGround.rgb);
    float3 a = liveSat(float3(uAccent.rgb), vs);
    float3 b = liveSat(float3(uPartner.rgb), vs);
    float3 g = float3(uGlow.rgb);
    // Two more hues round the wheel from the accent (Playful only).
    float3 h1 = liveSat(liveHue(a, 0.30), vs);
    float3 h2 = liveSat(liveHue(a, -0.17), vs);

    float haze = liveFbm(p * 1.5 / z + float2(t * 0.6, -t * 0.4));
    float3 col = liveGlow(ground, mix(a, b, haze), smoothstep(0.35, 0.85, haze) * 0.10 * uIntensity * va);

    float aspect = uRes.x / uRes.y;
    float2 q = float2(p.x / max(aspect, 0.001) * 1.2 / z, p.y);
    float c0 = curtain(q, t, 0.0);
    float c1 = curtain(q + float2(0.37, 0.0), t * 0.8, 1.0);
    float fade = smoothstep(0.95, 0.15, p.y);
    float3 tint0 = mix(a, g, 0.5 + 0.5 * sin(q.x * 2.0 + t));
    col = liveGlow(col, tint0, c0 * fade * 0.42 * uIntensity * va);
    col = liveGlow(col, mix(b, a, 0.35), c1 * fade * 0.30 * uIntensity * va);

    if (multi > 0.0) {
        // Three large drifting fields in three different hues, high, middle and low on the page.
        float r = 0.34 * z;
        float2 p0 = float2(p.x, p.y);
        float2 ca = float2(aspect * (0.15 + 0.10 * sin(t * 1.1)), 0.18 + 0.06 * sin(t * 0.9 + 1.0));
        float2 cb = float2(aspect * (0.85 + 0.08 * sin(t * 0.8 + 2.0)), 0.52 + 0.08 * sin(t * 1.0));
        float2 cc = float2(aspect * (0.30 + 0.12 * sin(t * 0.7 + 4.0)), 0.90 + 0.05 * sin(t * 1.2 + 3.0));
        col = liveGlow(col, h1, blob(p0, ca, r) * 0.46 * uIntensity * va * multi);
        col = liveGlow(col, h2, blob(p0, cb, r * 0.9) * 0.40 * uIntensity * va * multi);
        col = liveGlow(col, mix(a, h1, 0.5), blob(p0, cc, r * 0.8) * 0.32 * uIntensity * va * multi);
    }
    return liveOut(col, fragCoord);
}
"""

    override fun uniforms(u: LiveUniforms): Unit = Unit
}
