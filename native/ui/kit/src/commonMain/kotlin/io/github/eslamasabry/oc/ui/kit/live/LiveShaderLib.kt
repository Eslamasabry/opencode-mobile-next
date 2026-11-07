package io.github.eslamasabry.oc.ui.kit.live

/**
 * The shared head of every look's shader: the uniforms the host sets on every frame and a tiny library (hash, value
 * noise, fbm, a glow blend and the dithered, contrast-capped output). Written in the common subset of AGSL
 * (Android 13+) and SkSL (desktop), so the same text compiles on both: no derivatives, constant loop bounds, no `%`
 * on floats.
 *
 * Coordinates: `fragCoord` is in render pixels; `uRes` is the drawn size in the same pixels; `uDensity` is render
 * pixels per dp. Colours are the theme's sRGB roles, 0..1; `uInk` is the body text colour.
 *
 * Two optional helper sets sit beside the prelude, for looks that lay a tint on the ground in their own way. A look
 * puts one in front of its body (`shader = LiveShaderLib.TINT + "..."`); the host still prepends [PRELUDE]. Their names
 * never clash with the prelude's or with each other's, so either may be used by any look.
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
    internal const val INK = "uInk"

    /** The smallest shader: compiles wherever runtime shaders exist, so the host can choose the fallback once. */
    internal const val PROBE: String = "half4 main(float2 c) { return half4(0.0); }"

    public const val PRELUDE: String =
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
uniform half4 uInk;

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

    /**
     * The tint set (the dot, line and light looks: dot ripple, topo, flow threads, light rays, constellation, bokeh,
     * halftone): hash and value noise, a one-pixel width in dp for edges without derivatives, the glow colour (accent
     * lifted towards the text on dark grounds) and `liveOut(fragCoord, tint, amount)`, which mixes the ground towards
     * the tint by at most [TINT_MAX_MIX] at Bold, then dithers.
     */
    public const val TINT: String =
        """
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

    /** The most a [TINT] look moves the ground to its tint at Bold: body text stays above 4.5:1 on every look. */
    public const val TINT_MAX_MIX: Float = .2f

    /**
     * The layer set (the instrument looks: matrix rain, blueprint, circuit, oscilloscope, radar, honeycomb, brushed
     * metal): hash and noise, anti-aliased line coverage in pixels, the page point in dp, `lvLayer(k, g)` (the ground
     * towards the text by k, then towards the accent by g) and `lvOut(colour, fragCoord)`, which keeps the colour
     * within [LAYER_MAX_LUMA] of the ground's luma with a soft knee, then dithers. A look ends with
     * `return lvOut(lvLayer(..), fc);`.
     */
    public const val LAYER: String =
        """
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

// The most a look may move the ground's luma (gamma-encoded). Over ground at most this far away, secondary text keeps
// at least 4.5:1 on the kit's dark and light grounds, so every look stays readable at every intensity.
const float LV_MAX_LUMA = 0.15;

// Keeps c within LV_MAX_LUMA of the ground's luma: untouched below 60% of it, a soft knee above, so peaks (a glyph
// head, a trace core) flatten out instead of clipping.
half3 lvKeepContrast(half3 c) {
    float3 w = float3(0.2126, 0.7152, 0.0722);
    float dl = dot(float3(c) - float3(uGround.rgb), w);
    float a = abs(dl);
    float knee = LV_MAX_LUMA * 0.6;
    float room = LV_MAX_LUMA - knee;
    float b = a <= knee ? a : knee + room * (1.0 - exp(-(a - knee) / room));
    float s = a > 1.0e-5 ? b / a : 1.0;
    return half3(mix(float3(uGround.rgb), float3(c), s));
}

// Opaque output, contrast-kept, with triangular dither (one 8-bit step) so slow gradients never band.
half4 lvOut(half3 c, float2 fc) {
    float n = lvHash(fc) + lvHash(fc + float2(19.19, 47.47)) - 1.0;
    return half4(clamp(lvKeepContrast(c) + half(n / 255.0), 0.0, 1.0), 1.0);
}
"""

    /** The most a [LAYER] look moves the ground's luma (`LV_MAX_LUMA` in the shader). */
    public const val LAYER_MAX_LUMA: Float = .15f
}
