package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Bokeh: out-of-focus lights, like city lights through a lens, rising very slowly in three depths; the near ones are
 * larger, softer and slower, each disc with the faint bright rim a real lens gives it.
 */
public object BokehLook : LiveLook {
    override val id: String = "bokeh"

    override val shader: String =
        LiveShaderLib.TINT +
            """
// One depth of lights: cells of [cell] dp, about half hold a light of radius up to [maxR] dp. A light stays inside its
// own cell (blur included), so each pixel looks at one cell per depth, not nine.
float layer(float2 p, float cell, float maxR, float blur, float speed, float seed, float t) {
    float2 q = p + float2(sin(t * 0.03 + seed) * 12.0, t * speed);
    float2 c = floor(q / cell);
    float2 h = hash22(c + seed);
    if (h.x >= 0.5) {
        return 0.0;
    }
    float r = maxR * (0.45 + 0.55 * h.y);
    float soft = max(r * blur, px() * 1.5);
    float reach = r + soft;
    float2 centre = c * cell + reach + (cell - 2.0 * reach) * hash22(c * 1.7 + seed + 3.1);
    float d = length(q - centre);
    float disc = 1.0 - smoothstep(r - soft, r + soft, d);
    float e = (d - r * 0.9) / (r * 0.12 + 0.5);
    float rim = exp(-e * e) * 0.35;
    float fade = 0.5 + 0.5 * sin(t * 0.2 + h.y * 30.0);
    return (disc * (0.4 + 0.25 * h.x) + rim * disc) * (0.35 + 0.65 * fade);
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / uDensity;
    float t = uTime;
    float far = layer(p, 70.0, 7.0, 0.5, 3.0, 1.3, t);
    float mid = layer(p, 130.0, 24.0, 0.18, 2.0, 7.9, t);
    float near = layer(p, 240.0, 52.0, 0.3, 1.2, 19.4, t);
    float a = far * 0.6 + mid * 0.7 + near * 0.75;
    float hue = vnoise(p * 0.004 + t * 0.01);
    half3 tint = mix(glowTint(), mix(uInk.rgb, uAccent.rgb, half(0.3)), half(hue));
    return liveOut(fragCoord, tint, min(a, 1.0));
}
"""

    override fun uniforms(u: LiveUniforms) {
        // Only the shared uniforms.
    }
}
