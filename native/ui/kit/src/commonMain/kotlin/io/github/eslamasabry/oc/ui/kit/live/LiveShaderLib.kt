package io.github.eslamasabry.oc.ui.kit.live

/**
 * The tiny shared AGSL library every look starts with: the shared uniforms, hashing, value noise, a cheap fbm and the
 * one way a look lays its tint on the page ground (capped, dithered). AGSL is Skia's shading language, so the same text
 * also compiles in Skia's runtime effects on desktop (where the stills in docs/design/live-backgrounds-native/ come from).
 *
 * Shared uniforms (set by the host every frame from the one frame clock; never per look):
 * - `uResolution` the rect in pixels; `uDensity` pixels per dp (looks work in dp so they read the same on every screen)
 * - `uTime` seconds of the shared clock (stands still when the clock rests)
 * - `uGround`, `uAccent`, `uInk` the kit colour roles (sRGB, alpha 1): page ground, accent and body text
 * - `uIntensity` the strength after the style's cap: Subtle .7, Normal 1, Bold 1.6
 *
 * No derivatives in AGSL: anti-aliasing widths come from `uDensity` (one pixel is `1 / uDensity` dp).
 */
public object LiveShaderLib {
    /** The most a look may move the ground towards its tint at Bold; keeps body text above 4.5:1 on every look. */
    public const val MAX_MIX: Float = .2f

    public const val HEADER: String =
        """
uniform float2 uResolution;
uniform float uDensity;
uniform float uTime;
uniform half4 uGround;
uniform half4 uAccent;
uniform half4 uInk;
uniform float uIntensity;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float2 hash22(float2 p) {
    float n = hash21(p);
    return float2(n, hash21(p + n + 17.17));
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm2(float2 p) {
    return 0.65 * vnoise(p) + 0.35 * vnoise(p * 2.03 + 11.7);
}

float luma(half3 c) {
    return dot(float3(c), float3(0.2126, 0.7152, 0.0722));
}

float isDark() {
    return step(luma(uGround.rgb), 0.5);
}

// The look's glow colour: the accent, lifted towards the text colour on a dark ground so it reads as light.
half3 glowTint() {
    return mix(uAccent.rgb, uInk.rgb, half(0.2 * isDark()));
}

// One pixel in dp, for anti-aliased edges without derivatives.
float px() {
    return 1.0 / max(uDensity, 0.5);
}

// Lays [tint] on the ground by [a] (0..1 of the look's own range), scaled by intensity, capped, then dithered so
// the long soft gradients never band.
half4 liveOut(float2 fragCoord, half3 tint, float a) {
    float m = clamp(a * uIntensity * 0.125, 0.0, 0.2);
    half3 c = mix(uGround.rgb, tint, half(m));
    float d = (hash21(fragCoord) + hash21(fragCoord + 71.3) - 1.0) / 255.0;
    return half4(clamp(c + half(d), 0.0, 1.0), 1.0);
}
"""
}
