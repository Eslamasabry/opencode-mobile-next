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
