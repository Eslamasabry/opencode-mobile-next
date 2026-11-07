package io.github.eslamasabry.oc.ui.kit.live

/**
 * The shared head of every look's shader: the uniforms the host sets on every frame and a tiny library (hash, value
 * noise, fbm, a glow blend and the dithered, contrast-capped output). Written in the common subset of AGSL (Android 13+)
 * and SkSL (desktop), so the same text compiles on both: no derivatives, constant loop bounds, no `%` on floats.
 *
 * Coordinates: `fragCoord` is in render pixels; `uRes` is the drawn size in the same pixels; `uDensity` is render
 * pixels per dp. Colours are the theme's sRGB roles, 0..1.
 */
public object LiveShaderLib {
    /** Uniform names the host sets every frame. */
    internal const val RES = "uRes"
    internal const val TIME = "uTime"
    internal const val INTENSITY = "uIntensity"
    internal const val DENSITY = "uDensity"
    internal const val DARK = "uDark"
    internal const val GROUND = "uGround"
    internal const val ACCENT = "uAccent"
    internal const val PARTNER = "uPartner"
    internal const val GLOW = "uGlow"

    /** The smallest shader: compiles wherever runtime shaders exist, so the host can choose the fallback once. */
    internal const val PROBE: String = "half4 main(float2 c) { return half4(0.0); }"

    public val PRELUDE: String =
        """
uniform float2 uRes;
uniform float uTime;
uniform float uIntensity;
uniform float uDensity;
uniform float uDark;
uniform half4 uGround;
uniform half4 uAccent;
uniform half4 uPartner;
uniform half4 uGlow;

// Hash without sine: stable on mobile GPUs at the coordinates we use (a few hundred at most).
float liveHash(float2 p) {
    float3 q = fract(float3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float2 liveHash2(float2 p) {
    float3 q = fract(float3(p.xyx) * float3(0.1031, 0.1030, 0.0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.xx + q.yz) * q.zy);
}

// Smooth value noise, 0..1.
float liveNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = liveHash(i);
    float b = liveHash(i + float2(1.0, 0.0));
    float c = liveHash(i + float2(0.0, 1.0));
    float d = liveHash(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Four octaves, each turned so the grid never shows. 0..~0.94.
float liveFbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * liveNoise(p);
        p = float2(1.6 * p.x + 1.2 * p.y, -1.2 * p.x + 1.6 * p.y) + 7.31;
        a *= 0.5;
    }
    return v;
}

// Light added to the ground: a screen blend on dark themes, a tint on light ones, so a glow never greys a light page.
float3 liveGlow(float3 base, float3 tint, float a) {
    a = clamp(a, 0.0, 1.0);
    float3 lit = 1.0 - (1.0 - base) * (1.0 - tint * a);
    float3 dye = mix(base, tint, a);
    return mix(dye, lit, uDark);
}

// Every look ends here: the colour is kept within a small distance of the ground (text stays readable at Bold), then
// a triangular dither of one 8-bit step hides banding in the soft gradients.
half4 liveOut(float3 col, float2 fragCoord) {
    float3 g = float3(uGround.rgb);
    float3 d = col - g;
    float m = length(d);
    float cap = mix(0.20, 0.30, uDark);
    if (m > cap) { d *= cap / m; }
    float n = liveHash(fragCoord) + liveHash(fragCoord + 71.3) - 1.0;
    return half4(clamp(g + d + n / 255.0, 0.0, 1.0), 1.0);
}
"""
}
