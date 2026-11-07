package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Constellation: scattered small stars that wander a little and twinkle slowly; neighbours close enough to each other
 * are joined by hairlines that fade with their length, so the figures form and dissolve on their own.
 */
public object LiveConstellation : LiveLook {
    override val id: String = "constellation"

    override val shader: String =
        LiveShaderLib.HEADER +
            """
const float CELL = 90.0;

float2 star(float2 cell, float t) {
    float2 h = hash22(cell);
    float2 wander = float2(sin(t * (0.05 + 0.04 * h.x) + h.y * 6.28), cos(t * (0.04 + 0.05 * h.y) + h.x * 6.28));
    return (cell + 0.2 + 0.6 * h + 0.12 * wander) * CELL;
}

float segment(float2 p, float2 a, float2 b) {
    float2 pa = p - a;
    float2 ba = b - a;
    float k = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * k);
}

// The hairline between the stars of two touching cells, if this pair is lit now. Order-free, so every pixel agrees.
float link(float2 p, float2 ca, float2 cb, float t) {
    float pair = hash21(ca + cb + abs(ca - cb) * 7.7);
    if (pair < 0.35) {
        return 0.0;
    }
    float on = smoothstep(0.35, 0.85, 0.5 + 0.5 * sin(t * 0.07 + pair * 31.0));
    if (on <= 0.0) {
        return 0.0;
    }
    float2 a = star(ca, t);
    float2 b = star(cb, t);
    float near = (1.0 - smoothstep(60.0, 110.0, length(a - b))) * on;
    if (near <= 0.0) {
        return 0.0;
    }
    return near * (1.0 - smoothstep(0.25, 0.25 + px() * 1.3, segment(p, a, b)));
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / uDensity;
    float t = uTime;
    float2 home = floor(p / CELL);
    float stars = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            float2 c = home + float2(float(i), float(j));
            float2 s = star(c, t);
            float h = hash21(c + 5.3);
            float tw = 0.6 + 0.4 * sin(t * (0.35 + 0.3 * h) + h * 40.0);
            float r = 0.8 + 1.1 * h * h;
            float d = length(p - s);
            float core = 1.0 - smoothstep(r, r + px() * 1.5, d);
            stars = max(stars, tw * (core + 0.35 * exp(-d * d / (r * r * 9.0))));
        }
    }
    // Only stars of touching cells are joined, and stars stay inside their cells, so a hairline lies inside the 2 x 2
    // block of its two cells. The ones that can cross this cell: home to each of its 8 neighbours, and the 4
    // diagonals between its side neighbours.
    float lines = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            if (i != 0 || j != 0) {
                lines = max(lines, link(p, home, home + float2(float(i), float(j)), t));
            }
        }
    }
    float2 l = home + float2(-1.0, 0.0);
    float2 u = home + float2(0.0, -1.0);
    float2 r = home + float2(1.0, 0.0);
    float2 d = home + float2(0.0, 1.0);
    lines = max(lines, link(p, l, u, t));
    lines = max(lines, link(p, u, r, t));
    lines = max(lines, link(p, r, d, t));
    lines = max(lines, link(p, d, l, t));
    half3 tint = mix(uInk.rgb, glowTint(), half(0.45));
    return liveOut(fragCoord, tint, max(stars * 1.5, lines * 0.7));
}
"""

    override fun uniforms(u: LiveUniforms) {
        // Only the shared uniforms.
    }
}
