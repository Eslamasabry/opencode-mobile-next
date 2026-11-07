package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Silk: wide folds of satin lie diagonally across the page; a soft sheen slides along each fold as they slowly fold
 * and unfold. Shade in the accent and partner, highlights a touch brighter.
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

    // The cloth: a diagonal coordinate bent by two slow, large noises.
    float bend = liveFbm(p * 0.9 + float2(t * 0.5, -t * 0.3));
    float v = p.x * 1.1 + p.y * 0.8 + bend * 1.4;
    float phase = v * 3.2 + t * 1.6;
    float fold = 0.5 + 0.5 * sin(phase);
    float sheen = pow(0.5 + 0.5 * sin(phase + 0.9), 9.0);
    float under = 0.5 + 0.5 * sin(phase * 0.5 - t);

    float3 cloth = mix(a, b, under);
    float3 col = liveGlow(ground, cloth, (0.03 + 0.20 * fold * fold) * uIntensity);
    col = liveGlow(col, mix(g, float3(1.0), 0.25), sheen * 0.22 * uIntensity);
    // Let the folds settle toward the bottom, where the message box sits.
    col = mix(col, ground, smoothstep(0.55, 1.0, p.y) * 0.35);
    return liveOut(col, fragCoord);
}
"""

    override fun uniforms(u: LiveUniforms): Unit = Unit
}
