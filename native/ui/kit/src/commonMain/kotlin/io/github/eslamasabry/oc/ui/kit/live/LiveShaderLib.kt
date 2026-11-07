package io.github.eslamasabry.oc.ui.kit.live

/**
 * The tiny AGSL library every live look starts from: the shared uniform header, hash/value noise, anti-aliased line
 * coverage and a dithered, low-contrast output. Only ES2-style float maths (no integer ops, constant loop bounds), so the
 * same source runs as an Android `RuntimeShader` (API 33+) and as a Skia `RuntimeEffect` on desktop for stills and tests.
 *
 * Shared uniforms, set by the host every frame:
 * - [RES] `float2` drawn size in px, [TIME] `float` seconds on the shared clock;
 * - [INTENSITY] capped strength (.7/1/1.6), [DENSITY] px per dp, [DARK] 1 on dark themes, 0 on light;
 * - [GROUND] page ground, [ACCENT] the person's accent, [INK] the neutral text colour (all `layout(color) half4`).
 *
 * A look writes `half4 main(float2 fc)` and ends with `return lvOut(lvLayer(...), fc);`.
 */
public object LiveShaderLib {
    public const val RES: String = "uRes"
    public const val TIME: String = "uTime"
    public const val INTENSITY: String = "uIntensity"
    public const val DENSITY: String = "uDensity"
    public const val DARK: String = "uDark"
    public const val GROUND: String = "uGround"
    public const val ACCENT: String = "uAccent"
    public const val INK: String = "uInk"

    /** Uniforms and helpers; a look's own uniforms and `main` follow it. */
    public val PRELUDE: String =
        """
uniform float2 uRes;
uniform float uTime;
uniform float uIntensity;
uniform float uDensity;
uniform float uDark;
layout(color) uniform half4 uGround;
layout(color) uniform half4 uAccent;
layout(color) uniform half4 uInk;

const float LV_TAU = 6.2831853;

// Cheap stable hash in [0,1).
float lvHash(float2 p) {
    float3 q = fract(float3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

// Smooth value noise in [0,1).
float lvNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = lvHash(i);
    float b = lvHash(i + float2(1.0, 0.0));
    float c = lvHash(i + float2(0.0, 1.0));
    float d = lvHash(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Three octaves of value noise in [0,1).
float lvFbm(float2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        s += a * lvNoise(p);
        p = p * 2.03 + float2(17.1, 9.2);
        a *= 0.5;
    }
    return s / 0.875;
}

// Coverage of a line at signed distance d (px) with half width w (px), one pixel of anti-aliasing.
float lvLine(float d, float w) {
    return 1.0 - smoothstep(w - 0.5, w + 0.5, abs(d));
}

// The page point in dp.
float2 lvDp(float2 fc) {
    return fc / max(uDensity, 1.0);
}

// How strongly a look may tint the ground: intensity, a little less on light grounds where tints read stronger.
float lvAmt(float a) {
    return a * uIntensity * mix(0.8, 1.0, uDark);
}

// The ground tinted towards ink by k, then towards the accent by g (both already scaled by lvAmt).
half3 lvLayer(float k, float g) {
    half3 c = mix(uGround.rgb, uInk.rgb, half(clamp(k, 0.0, 1.0)));
    return mix(c, uAccent.rgb, half(clamp(g, 0.0, 1.0)));
}

// Opaque output with triangular dither (one 8-bit step) so slow gradients never band.
half4 lvOut(half3 c, float2 fc) {
    float n = lvHash(fc) + lvHash(fc + float2(19.19, 47.47)) - 1.0;
    return half4(clamp(c + half(n / 255.0), 0.0, 1.0), 1.0);
}
"""

    /** The whole program for a look: the prelude, then the look's [body] (its uniforms and `main`). */
    public fun program(body: String): String = PRELUDE + body
}
