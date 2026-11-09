package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveResolution
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Topo: the contour lines of a map, hairline thin with every fifth line a little stronger, over a landscape that shifts
 * so slowly you notice it only when you look away and back.
 */
public object TopoLook : LiveLook {
    override val id: String = "topo"
    override val resolution: Float = LiveResolution.FULL // fine marks: crisp only at the screen's own pixels

    override val shader: String =
        LiveShaderLib.TINT +
            """
float height(float2 q, float t) {
    float2 drift = float2(t * 0.011, -t * 0.007);
    return fbm2(q + drift) + 0.35 * vnoise(q * 0.5 - drift * 1.7);
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / uDensity;
    float t = uTime;
    const float scale = 0.0065;   // one hill about 150 dp across
    const float bands = 14.0;
    float2 q = p * scale;
    float e = scale;              // one dp in noise space, for the slope
    float h = height(q, t);
    float hx = height(q + float2(e, 0.0), t);
    float hy = height(q + float2(0.0, e), t);
    float slope = length(float2(hx - h, hy - h)) * bands + 1e-4; // contour units per dp

    float f = h * bands;
    float dist = abs(fract(f + 0.5) - 0.5) / slope;  // distance to the nearest line in dp
    float major = step(abs(fract((floor(f + 0.5)) / 5.0)), 0.01);
    float w = mix(0.5, 0.9, major);
    float line = 1.0 - smoothstep(w, w + px() * 1.2, dist);
    half3 tint = mix(uInk.rgb, glowTint(), half(0.6 + 0.3 * major));
    return liveOut(fragCoord, tint, line * mix(0.42, 0.75, major));
}
"""

    override fun uniforms(u: LiveUniforms) {
        // Only the shared uniforms.
    }
}
