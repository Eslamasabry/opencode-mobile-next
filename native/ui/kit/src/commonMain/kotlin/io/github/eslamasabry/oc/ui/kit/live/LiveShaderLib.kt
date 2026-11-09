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

    /** 1 when the look runs at reduced resolution and the [FINISH] pass decodes and dithers it at full resolution. */
    internal const val ENCODE = "uEncode"

    /**
     * How far from the ground (per channel, 0..1) a reduced pass can write ([FINISH] decodes it): every look stays within
     * 0.3 of its ground, so the 8-bit layer holds the look in 0.64 of an output step, finer than the screen's own.
     */
    public const val ENCODE_RANGE: Float = .32f

    /**
     * The dither's reach either way, in 8-bit steps. It is always laid at the screen's own pixels (in the look at full
     * resolution, in [FINISH] after the upscale otherwise), where one step either way of triangular noise is exactly
     * what removes an 8-bit band; more only adds grain.
     */
    public const val DITHER_STEPS: Float = 1f

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
uniform float uEncode;

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

// Interleaved gradient noise (Jimenez 2014): screen-space noise with almost no low frequencies, so a dither made of it
// never clumps into the mottled patches white noise leaves in a dark gradient. One dot and two fract per pixel.
float liveIgn(float2 p) {
    return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
}

// The dither every look ends with: triangular (gradient noise plus a hash), one 8-bit step either way, at the
// screen's own pixels, so no glow ends in visible bands.
float liveDither(float2 fragCoord) {
    return (liveIgn(fragCoord) + liveHash(fragCoord + 71.3) - 1.0) * ($DITHER_STEPS / 255.0);
}

// A look's final colour. At full resolution: dithered here. In a reduced pass (uEncode): the distance from the ground,
// spread over the 8-bit range and undithered, for the full-resolution finish (LiveShaderLib.FINISH) to decode and
// dither after the upscale, so the dither is always at the screen's own pixels.
half4 liveEmit(float3 col, float2 fragCoord) {
    if (uEncode > 0.5) {
        return half4(clamp(0.5 + 0.5 * (col - float3(uGround.rgb)) / $ENCODE_RANGE, 0.0, 1.0), 1.0);
    }
    return half4(clamp(col + liveDither(fragCoord), 0.0, 1.0), 1.0);
}

// The tint with its colour drawn out (40 % further from its own grey), so a look adds colour, not a grey haze: theme
// partners are often pale, and a pale tint screened onto a dark ground only lifted it towards grey.
float3 liveVivid(float3 c) {
    float l = dot(c, float3(0.2126, 0.7152, 0.0722));
    return clamp(l + (c - l) * 1.4, 0.0, 1.0);
}

// Light added to the ground. Dark themes: a screen blend, so it reads as light. Light themes: coloured light on paper,
// the ground multiplied by the tint brought to full brightness, so colour comes in without greying or darkening the
// page (a light accent is dark for contrast; mixing towards it made light pages muddy).
float3 liveGlow(float3 base, float3 tint, float a) {
    a = clamp(a, 0.0, 1.0);
    tint = liveVivid(tint);
    float3 lit = 1.0 - (1.0 - base) * (1.0 - tint * a);
    float3 hue = tint / max(max(tint.r, tint.g), max(tint.b, 0.02));
    float3 dye = base * mix(float3(1.0), hue, a);
    return mix(dye, lit, uDark);
}

// Every look ends here: the colour is kept within a small distance of the ground (text stays readable at Bold) with a
// soft knee from 60 % of it, so a bright spot rolls off into a gentle plateau instead of a flat clipped patch with a
// hard rim; then dithered.
half4 liveOut(float3 col, float2 fragCoord) {
    float3 g = float3(uGround.rgb);
    float3 d = col - g;
    float m = length(d);
    float cap = mix(0.20, 0.30, uDark);
    float knee = cap * 0.6;
    if (m > knee) {
        float room = cap - knee;
        d *= (knee + room * (1.0 - exp(-(m - knee) / room))) / m;
    }
    return liveEmit(g + d, fragCoord);
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
// the long soft gradients never band. A fine mark on a near-white ground needs a deeper share to be seen at all, so on a
// light ground Subtle and Normal take up to 1.6 times the share; the boost fades out towards Bold, which stays exactly as
// strong as before (the reach Glass panes are measured against, LiveReach, is measured at Bold).
float lightBoost(float k) {
    return mix(k, 1.0, max(isDark(), clamp((uIntensity - 1.0) / 0.6, 0.0, 1.0)));
}

half4 liveOut(float2 fragCoord, half3 tint, float a) {
    float m = clamp(a * uIntensity * 0.125 * lightBoost(1.6), 0.0, 0.2);
    return liveEmit(mix(float3(uGround.rgb), float3(tint), m), fragCoord);
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

// How many render pixels make one of the half-resolution pixels the looks' widths were drawn in (1 up to 1.5 px per dp):
// at full resolution a line keeps its width on the page and gains crisp edges.
float lvK() {
    return max(uDensity / 1.5, 1.0);
}

// Coverage of a line at signed distance d (px) with half width w (half-resolution px), one pixel of anti-aliasing.
float lvLine(float d, float w) {
    float k = lvK();
    return 1.0 - smoothstep(w * k - 0.5, w * k + 0.5, abs(d));
}

// The page point in dp.
float2 lvDp(float2 fc) {
    return fc / max(uDensity, 1.0);
}

// How strongly a look may tint the ground. Light grounds took 0.8 of it, which made the instrument looks vanish there at
// Subtle and Normal; the discount now fades in only towards Bold, which stays as before (the measured reach, LiveReach,
// and the secondary-text guard are taken at Bold).
float lvAmt(float a) {
    float lightK = mix(1.0, 0.8, clamp((uIntensity - 1.0) / 0.6, 0.0, 1.0));
    return a * uIntensity * mix(lightK, 1.0, uDark);
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

// Opaque output, contrast-kept, dithered (liveEmit) so slow gradients never band.
half4 lvOut(half3 c, float2 fc) {
    return liveEmit(float3(lvKeepContrast(c)), fc);
}
"""

    /**
     * The full-resolution finish of a look drawn at reduced resolution (bg-looks 2026-11-10): applied as a render effect
     * to the full-size layer the reduced pass is filtered up into, it decodes the look's distance from the ground
     * ([ENCODE_RANGE]) and adds the dither at the screen's own pixels, after the upscale. Per pixel: one texture read,
     * one gradient noise and one hash (about 20 ALU operations); no time uniform, so the effect is built once per theme.
     */
    public const val FINISH: String =
        """
uniform shader content;
uniform half4 uGround;

float fHash(float2 p) {
    float3 q = fract(float3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

half4 main(float2 fragCoord) {
    float3 e = float3(content.eval(fragCoord).rgb);
    float3 c = float3(uGround.rgb) + (e - 0.5) * (2.0 * $ENCODE_RANGE);
    float ign = fract(52.9829189 * fract(dot(fragCoord, float2(0.06711056, 0.00583715))));
    float n = (ign + fHash(fragCoord + 71.3) - 1.0) * ($DITHER_STEPS / 255.0);
    return half4(clamp(c + n, 0.0, 1.0), 1.0);
}
"""

    /** The most a [LAYER] look moves the ground's luma (`LV_MAX_LUMA` in the shader). */
    public const val LAYER_MAX_LUMA: Float = .15f
}
