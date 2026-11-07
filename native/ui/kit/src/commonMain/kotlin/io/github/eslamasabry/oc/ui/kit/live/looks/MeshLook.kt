package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms
import kotlin.math.cos
import kotlin.math.sin

/**
 * Breathing mesh: four soft colour points wander slowly and blend into one smooth gradient that gently brightens and
 * dims, like a mesh gradient breathing. The points move on the CPU once a frame (four uniforms), not per pixel.
 */
public object MeshLook : LiveLook {
    override val id: String = "mesh"

    override val shader: String =
        """
uniform float2 uP0;
uniform float2 uP1;
uniform float2 uP2;
uniform float2 uP3;
uniform float uBreath;

float w(float2 uv, float2 c) {
    float2 d = uv - c;
    float r = dot(d, d) + 0.02;
    return 1.0 / (r * r);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uRes;
    // A gentle warp so the blends are not perfect circles.
    uv += 0.04 * (float2(liveNoise(uv * 3.0 + uTime * 0.02), liveNoise(uv * 3.0 + 9.0 - uTime * 0.02)) - 0.5);
    float3 a = float3(uAccent.rgb);
    float3 b = float3(uPartner.rgb);
    float3 g = float3(uGlow.rgb);
    float3 ground = float3(uGround.rgb);
    float w0 = w(uv, uP0);
    float w1 = w(uv, uP1);
    float w2 = w(uv, uP2);
    float w3 = w(uv, uP3);
    float3 tint = (a * w0 + b * w1 + mix(a, g, 0.6) * w2 + g * w3) / (w0 + w1 + w2 + w3);
    // Coverage falls off away from every point, so plain ground shows between them.
    float near = max(max(w0, w1), max(w2, w3));
    float cover = smoothstep(2.0, 60.0, near);
    float3 col = liveGlow(ground, tint, cover * (0.22 + 0.04 * uBreath) * uIntensity);
    return liveOut(col, fragCoord);
}
"""

    override fun uniforms(u: LiveUniforms) {
        val t = u.time * .12f
        u.float2("uP0", .20f + .15f * sin(t * .7f), .10f + .06f * cos(t * .5f))
        u.float2("uP1", .85f + .10f * cos(t * .6f), .32f + .08f * sin(t * .4f))
        u.float2("uP2", .30f + .20f * sin(t * .3f), .62f + .06f * cos(t * .8f))
        u.float2("uP3", .70f + .15f * sin(t * .5f + 1f), .95f + .05f * cos(t * .9f))
        u.float("uBreath", sin(t))
    }
}
