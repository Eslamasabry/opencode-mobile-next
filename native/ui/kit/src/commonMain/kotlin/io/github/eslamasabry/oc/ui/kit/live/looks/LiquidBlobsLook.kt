package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Liquid blobs: four soft drops of coloured liquid drift slowly and melt into each other when they meet (metaballs).
 * Drawn as lit drops, not flat discs: the body fades in over a wide edge, a soft light catches only the upper-left rim
 * (from the field's own gradient), and each drop lays its own colour as light, so where an accent drop meets a partner
 * drop they glow together instead of turning grey. The drops move on the CPU once a frame; the shader sums their fields.
 *
 * Per pixel: four inverse-square fields with their gradients, three glow blends; no loops, no noise.
 */
public object LiquidBlobsLook : LiveLook {
    override val id: String = "liquidBlobs"

    override val shader: String =
        """
uniform float3 uB0;
uniform float3 uB1;
uniform float3 uB2;
uniform float3 uB3;

// One drop's field at p, and its gradient (added to grad).
float field(float2 p, float3 b, inout float2 grad) {
    float2 d = p - b.xy;
    float q = dot(d, d) + 0.0004;
    float f = b.z * b.z / q;
    grad += -2.0 * f * d / q;
    return f;
}

half4 main(float2 fragCoord) {
    float aspect = uRes.x / uRes.y;
    float2 p = float2(fragCoord.x / uRes.x * aspect, fragCoord.y / uRes.y);
    float2 grad = float2(0.0);
    float f0 = field(p, float3(uB0.x * aspect, uB0.yz), grad);
    float f1 = field(p, float3(uB1.x * aspect, uB1.yz), grad);
    float f2 = field(p, float3(uB2.x * aspect, uB2.yz), grad);
    float f3 = field(p, float3(uB3.x * aspect, uB3.yz), grad);
    float f = f0 + f1 + f2 + f3;
    float inv = 1.0 / max(f, 0.0001);

    // A wide soft body edge and a faint halo round it: no hard rim anywhere.
    float body = smoothstep(0.45, 1.7, f);
    float halo = smoothstep(0.08, 0.8, f) * (1.0 - body);
    // The rim facing the light (upper left): the field rises inwards, so the gradient points away from that rim.
    float facing = max(dot(grad / max(length(grad), 0.0001), float2(0.6, 0.8)), 0.0);
    float rim = smoothstep(0.5, 1.0, f) * (1.0 - smoothstep(1.0, 2.4, f)) * facing * facing;

    float k = (halo * 0.07 + body * 0.20) * uIntensity;
    float3 col = float3(uGround.rgb);
    col = liveGlow(col, float3(uAccent.rgb), k * (f0 + 0.5 * f2) * inv);
    col = liveGlow(col, float3(uPartner.rgb), k * (f1 + 0.5 * f2) * inv);
    col = liveGlow(col, float3(uGlow.rgb), (k * f3 * inv) + rim * 0.10 * uIntensity);
    return liveOut(col, fragCoord);
}
"""

    private const val SPEED = .15f
    private val names = arrayOf("uB0", "uB1", "uB2", "uB3")
    private val orbits =
        arrayOf(
            LiveOrbit(
                x = LiveWave(mid = .30f, swing = .20f, speed = .8f),
                y = LiveWave(mid = .22f, swing = .14f, speed = .6f, phase = QUARTER_TURN),
                r = .085f,
            ),
            LiveOrbit(
                x = LiveWave(mid = .70f, swing = .20f, speed = .7f, phase = QUARTER_TURN),
                y = LiveWave(mid = .48f, swing = .16f, speed = .5f),
                r = .10f,
            ),
            LiveOrbit(
                x = LiveWave(mid = .50f, swing = .25f, speed = .4f, phase = 2f),
                y = LiveWave(mid = .78f, swing = .10f, speed = .9f, phase = QUARTER_TURN),
                r = .08f,
            ),
            LiveOrbit(
                x = LiveWave(mid = .55f, swing = .22f, speed = .33f, phase = 1f + QUARTER_TURN),
                y = LiveWave(mid = .50f, swing = .30f, speed = .27f),
                r = .065f,
            ),
        )

    override fun uniforms(u: LiveUniforms) {
        val t = u.time * SPEED
        for (i in orbits.indices) u.float3(names[i], orbits[i].x(t), orbits[i].y(t), orbits[i].r)
    }
}
