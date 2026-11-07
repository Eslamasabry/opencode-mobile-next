package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Glow spill: a warm pool of accent light spills up from the bottom edge and breathes very slowly; its edge wavers like
 * light through haze, and a fine still grain keeps it from looking flat.
 */
public object GlowSpillLook : LiveLook {
    override val id: String = "glowSpill"

    override val shader: String =
        """
half4 main(float2 fragCoord) {
    float2 p = fragCoord / uRes.y;
    float aspect = uRes.x / uRes.y;
    float t = uTime;
    float2 c = float2(aspect * 0.5, 1.04);
    float2 d = p - c;
    float ang = atan(d.x, -d.y);
    float breathe = 0.5 + 0.5 * sin(t * 0.35);
    float waver = 0.05 * (liveFbm(float2(ang * 2.2, t * 0.06)) - 0.45);
    float r = length(float2(d.x * 0.9, d.y)) / (0.62 + 0.04 * breathe + waver);
    float glow = exp(-r * r * 2.6);
    float core = exp(-r * r * 9.0);

    float3 ground = float3(uGround.rgb);
    float3 a = float3(uAccent.rgb);
    float3 g = float3(uGlow.rgb);
    float3 col = liveGlow(ground, a, glow * 0.24 * uIntensity);
    col = liveGlow(col, g, core * 0.12 * uIntensity);
    // Still grain at a fixed physical size, strongest inside the glow.
    float grain = liveHash(floor(fragCoord / max(uDensity, 1.0) * 1.5)) - 0.5;
    col += grain * 0.03 * glow * uIntensity;
    return liveOut(col, fragCoord);
}
"""

    override fun uniforms(u: LiveUniforms): Unit = Unit
}
