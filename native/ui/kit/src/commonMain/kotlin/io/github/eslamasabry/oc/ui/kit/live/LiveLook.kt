package io.github.eslamasabry.oc.ui.kit.live

/**
 * One live background look: an AGSL fragment shader plus the per-frame uniforms only this look needs.
 *
 * The contract every look builder works against (E20-S28). A look is a file under `live/looks/` and a one-line entry in
 * [LiveLooks]. [shader] is the body only: the host prepends [LiveShaderLib.PRELUDE], which declares the shared uniforms
 * (`uRes`, `uTime`, `uIntensity`, `uDensity`, `uDark`, `uGround`, `uAccent`, `uPartner`, `uGlow`) and the small noise,
 * gradient and dither library, so the body only defines `half4 main(float2 fragCoord)` (and its own helpers and extra
 * uniforms). End with `return liveOut(colour, fragCoord);` (or the output of a helper set, [LiveShaderLib.TINT] or
 * [LiveShaderLib.LAYER]) so every look gets dithering and a contrast cap.
 *
 * [uniforms] runs on every drawn frame, after the host has set the shared uniforms: set any look-specific uniform
 * through [LiveUniforms] here. It must not allocate (no lists, no boxing, no string building): it runs on every display
 * frame (up to 120 a second).
 */
public interface LiveLook {
    public val id: String
    public val shader: String /*AGSL*/

    /**
     * Share of the screen's pixels per side the look's shader runs at (bg-looks 2026-11-10): [LiveResolution.FULL] for
     * looks of fine marks (a line or a dot is only crisp at the screen's own pixels), [LiveResolution.FIELD] for smooth
     * colour fields, whose reduced pass is filtered up and finished (decoded and dithered) at full resolution. The
     * platform may lower it ([io.github.eslamasabry.oc.ui.kit.platform.KitPlatform.liveResolutionCap]).
     */
    public val resolution: Float
        get() = LiveResolution.FIELD

    public fun uniforms(u: LiveUniforms)
}

/**
 * The frame values a look can read, and the setters for its own uniforms. One instance per compiled shader, reused for
 * every frame (no allocation per frame). Colours are the theme's sRGB components, 0..1.
 */
public abstract class LiveUniforms {
    /** Seconds on the shared live clock; frozen while paused and at a fixed moment for a still frame. */
    public var time: Float = 0f
        internal set
    public var width: Float = 0f
        internal set
    public var height: Float = 0f
        internal set

    /** The person's Intensity after the style's cap: Subtle .7, Normal 1, Bold 1.6. */
    public var intensity: Float = 1f
        internal set

    /** Render pixels per dp (screen density times the render scale). */
    public var density: Float = 1f
        internal set
    public var dark: Boolean = true
        internal set

    public abstract fun float(name: String, v: Float)

    public abstract fun float2(name: String, x: Float, y: Float)

    public abstract fun float3(name: String, x: Float, y: Float, z: Float)

    public abstract fun float4(name: String, x: Float, y: Float, z: Float, w: Float)
}

/** How many of the screen's pixels a look's shader runs at ([LiveLook.resolution]). */
public object LiveResolution {
    /** Every screen pixel: the line and dot looks. */
    public const val FULL: Float = 1f

    /** Half the pixels per side (a quarter of the fragments): the smooth colour fields. */
    public const val FIELD: Float = .5f

    /**
     * Debug only, never a setting: true draws every look at full resolution whatever its own [LiveLook.resolution] and
     * the platform's cap, to measure frame times on a phone. Flip it here and build.
     */
    public const val FORCE_FULL: Boolean = false

    /** The share [look] draws at under the platform's [cap] (1 = no cap). */
    public fun of(look: LiveLook, cap: Float = 1f): Float =
        if (FORCE_FULL) FULL else minOf(look.resolution, cap).coerceIn(.25f, FULL)
}
