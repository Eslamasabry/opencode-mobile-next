package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid blobs: four soft drops of coloured liquid drift slowly and melt into each other when they meet (metaballs),
 * each with a faint lit rim. The drops move on the CPU once a frame; the shader only sums their fields.
 */
public object LiquidBlobsLook : LiveLook {
    override val id: String = "liquidBlobs"

    override val shader: String =
        """
uniform float3 uB0;
uniform float3 uB1;
uniform float3 uB2;
uniform float3 uB3;

float field(float2 p, float3 b) {
    float2 d = p - b.xy;
    return b.z * b.z / (dot(d, d) + 0.0004);
}

half4 main(float2 fragCoord) {
    float aspect = uRes.x / uRes.y;
    float2 p = float2(fragCoord.x / uRes.x, fragCoord.y / uRes.y);
    p.x *= aspect;
    float3 b0 = float3(uB0.x * aspect, uB0.yz);
    float3 b1 = float3(uB1.x * aspect, uB1.yz);
    float3 b2 = float3(uB2.x * aspect, uB2.yz);
    float3 b3 = float3(uB3.x * aspect, uB3.yz);
    float f0 = field(p, b0);
    float f1 = field(p, b1);
    float f2 = field(p, b2);
    float f3 = field(p, b3);
    float f = f0 + f1 + f2 + f3;

    float3 a = float3(uAccent.rgb);
    float3 b = float3(uPartner.rgb);
    float3 g = float3(uGlow.rgb);
    float3 tint = (a * f0 + b * f1 + mix(a, b, 0.5) * f2 + g * f3) / max(f, 0.0001);

    float inside = smoothstep(0.8, 1.6, f);
    float rim = smoothstep(0.75, 1.0, f) * (1.0 - smoothstep(1.0, 1.6, f));
    float halo = smoothstep(0.15, 0.9, f) * (1.0 - inside);
    float3 ground = float3(uGround.rgb);
    float3 col = liveGlow(ground, tint, (halo * 0.06 + inside * 0.20) * uIntensity);
    col = liveGlow(col, mix(tint, float3(1.0), 0.3), rim * 0.05 * uIntensity);
    return liveOut(col, fragCoord);
}
"""

    override fun uniforms(u: LiveUniforms) {
        val t = u.time * .15f
        u.float3("uB0", .30f + .20f * sin(t * .8f), .22f + .14f * cos(t * .6f), .085f)
        u.float3("uB1", .70f + .20f * cos(t * .7f), .48f + .16f * sin(t * .5f), .10f)
        u.float3("uB2", .50f + .25f * sin(t * .4f + 2f), .78f + .10f * cos(t * .9f), .08f)
        u.float3("uB3", .55f + .22f * cos(t * .33f + 1f), .50f + .30f * sin(t * .27f), .065f)
    }
}
