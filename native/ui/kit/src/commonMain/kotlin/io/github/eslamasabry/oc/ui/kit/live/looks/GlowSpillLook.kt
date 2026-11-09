package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Glow spill: a pool of accent light spills up from the bottom edge and breathes very slowly. Finished like light, not
 * paint (owner 2026-11-10, "the glow ends in a very teary way"): one smooth long-tailed falloff that eases to nothing
 * well before it could show an edge, a rim that sways on two slow sines instead of ragged noise, no grain (the
 * prelude's dither alone breaks the 8-bit steps), and the light shading from the partner at its outer skirt through the
 * accent to a brighter core, so the pool has depth.
 *
 * Per pixel: one exp, one pow-free rational falloff, four sines; no noise, no loops.
 */
public object GlowSpillLook : LiveLook {
    override val id: String = "glowSpill"

    override val shader: String =
        """
half4 main(float2 fragCoord) {
    float2 p = fragCoord / uRes.y;
    float aspect = uRes.x / uRes.y;
    float t = uTime;
    // Two incommensurate slow swells: the breath never repeats on a beat the eye can count.
    float breathe = 0.6 * sin(t * 0.21) + 0.4 * sin(t * 0.13 + 1.7);
    float sway = 0.035 * sin(t * 0.07) + 0.02 * sin(t * 0.043 + 2.3);
    float2 c = float2(aspect * (0.5 + sway), 1.06);
    float2 d = p - c;
    // A wide, low ellipse whose rim leans a little as it sways (smooth in angle: no torn edge).
    float lean = 1.0 + 0.06 * sin(d.x * 3.1 + t * 0.05);
    float r = length(float2(d.x * 0.82, d.y * lean)) / (0.66 + 0.025 * breathe);
    // Long soft tail (rational, so it reaches zero gently) times a Gaussian that pulls it to nothing by the top third.
    float body = 1.0 / (1.0 + 3.2 * r * r);
    float tail = exp(-r * r * 1.1);
    float glow = body * tail;
    float core = exp(-r * r * 7.0);

    float3 ground = float3(uGround.rgb);
    float3 a = float3(uAccent.rgb);
    float3 b = float3(uPartner.rgb);
    float3 g = float3(uGlow.rgb);
    // The skirt leans to the partner, the body is the accent, the core lifts towards the glow.
    float3 hue = mix(b, a, smoothstep(0.15, 0.75, glow));
    float3 col = liveGlow(ground, hue, glow * 0.30 * uIntensity);
    col = liveGlow(col, g, core * 0.10 * uIntensity);
    return liveOut(col, fragCoord);
}
"""

    override fun uniforms(u: LiveUniforms): Unit = Unit
}
