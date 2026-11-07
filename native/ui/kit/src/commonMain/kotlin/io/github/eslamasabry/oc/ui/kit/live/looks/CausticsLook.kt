package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Caustics: the net of light on the floor of a pool. Two layers of slowly moving cells, bent by a gentle noise, light
 * up along the edges where cells meet; where both layers' lines cross the light is brightest.
 */
public object CausticsLook : LiveLook {
    override val id: String = "caustics"

    override val shader: String =
        """
// Distance to the nearest cell edge (second-nearest minus nearest point), points circling slowly.
float edge(float2 p, float t) {
    float2 i = floor(p);
    float2 f = fract(p);
    float d1 = 8.0;
    float d2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float2 o = float2(float(x), float(y));
            float2 h = liveHash2(i + o);
            float2 c = o + 0.5 + 0.38 * sin(t + 6.2831 * h) - f;
            float d = dot(c, c);
            if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
        }
    }
    return sqrt(d2) - sqrt(d1);
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / uRes.y * 7.0;
    float t = uTime * 0.22;
    float2 warp = float2(liveNoise(p * 0.35 + t * 0.15), liveNoise(p * 0.35 + 5.2 - t * 0.12)) - 0.5;
    float e1 = edge(p + warp * 1.1, t);
    float e2 = edge(p * 1.37 + 3.1 - warp * 0.8, t * 1.21 + 2.0);
    float l1 = 1.0 - smoothstep(0.0, 0.22, e1);
    float l2 = 1.0 - smoothstep(0.0, 0.18, e2);
    float light = l1 * l1 * l1 * 0.35 + l2 * l2 * l2 * 0.2 + l1 * l2 * 0.55;
    light *= 0.55 + 0.45 * liveNoise(fragCoord / uRes.y * 2.0 - t * 0.1);

    float3 ground = float3(uGround.rgb);
    float3 a = float3(uAccent.rgb);
    float3 g = float3(uGlow.rgb);
    float depth = liveNoise(fragCoord / uRes.y * 1.4 + t * 0.05);
    float3 col = liveGlow(ground, float3(uPartner.rgb), depth * 0.06 * uIntensity);
    col = liveGlow(col, mix(a, g, 0.55), light * 0.16 * uIntensity);
    return liveOut(col, fragCoord);
}
"""

    override fun uniforms(u: LiveUniforms) {}
}
