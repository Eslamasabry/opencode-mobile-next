package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Dot ripple: a quiet grid of dots, every 22 dp, that swell and warm towards the accent as two slow rings pass through
 * them from points drifting around the page, like rain far away on still water.
 */
public object DotRippleLook : LiveLook {
    override val id: String = "dotRipple"

    override val shader: String =
        LiveShaderLib.TINT +
            """
float ring(float2 p, float2 c, float t) {
    float d = length(p - c);
    // Rings move outwards at 40 dp/s, 260 dp apart, and fade with distance.
    float w = 0.5 + 0.5 * sin(d * 0.0242 - t * 0.97);
    return w * w * w * exp(-d * 0.0014);
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / uDensity;
    float2 size = uRes / uDensity;
    float t = uTime;
    float2 c1 = size * float2(0.30 + 0.12 * sin(t * 0.05), 0.28 + 0.08 * cos(t * 0.04));
    float2 c2 = size * float2(0.74 + 0.10 * cos(t * 0.035), 0.72 + 0.10 * sin(t * 0.045));

    const float cell = 22.0;
    float2 g = (floor(p / cell) + 0.5) * cell;
    float wave = max(ring(g, c1, t), ring(g, c2, t + 2.1));
    float r = 1.0 + 1.8 * wave;
    float d = length(p - g);
    float disc = 1.0 - smoothstep(r - px(), r + px(), d);
    half3 tint = mix(uInk.rgb, glowTint(), half(wave));
    return liveOut(fragCoord, tint, disc * (0.45 + 1.0 * wave));
}
"""

    override fun uniforms(u: LiveUniforms) {
        // Only the shared uniforms.
    }
}
