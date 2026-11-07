package io.github.eslamasabry.oc.ui.kit.live

/**
 * One live background look (E20-S28): an AGSL program drawn as one full-screen rect behind the page.
 *
 * [id] is the stored look id (`oc.effectsLiveStyle`, Dart `KitLiveStyle` ids). [shader] is the whole AGSL source, built
 * from [LiveShaderLib.program] so every look shares the same uniform header, noise helpers and dithered output.
 * [uniforms] sets only the look's own extra uniforms; the host sets the shared ones ([LiveShaderLib] names) from the one
 * frame clock and the theme before it calls this. It runs on every drawn frame, so it must not allocate.
 */
public interface LiveLook {
    public val id: String
    public val shader: String /*AGSL*/
    public fun uniforms(u: LiveUniforms)
}

/**
 * What a look reads and writes for one frame. The host owns the instance (one per drawn background, reused every frame)
 * and has already set the shared uniforms; [time] is the shared clock in seconds, [width]/[height] the drawn size in
 * pixels, [intensity] the capped strength (Subtle .7, Normal 1, Bold 1.6), [density] pixels per dp, [dark] the theme.
 */
public interface LiveUniforms {
    public val time: Float
    public val width: Float
    public val height: Float
    public val intensity: Float
    public val density: Float
    public val dark: Boolean

    public fun float(name: String, value: Float)

    public fun float2(name: String, x: Float, y: Float)

    /** A `layout(color) uniform half4`, from a packed ARGB colour. */
    public fun color(name: String, argb: Int)
}
