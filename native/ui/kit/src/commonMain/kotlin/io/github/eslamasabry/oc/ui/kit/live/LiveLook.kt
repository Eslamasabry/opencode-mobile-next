package io.github.eslamasabry.oc.ui.kit.live

/**
 * One live background look: an AGSL program drawn as one full-screen rect behind the page (E20-S28). The host compiles
 * [shader] once, sets the shared uniforms of [LiveShaderLib.HEADER] from the one shared frame clock and the theme, then
 * calls [uniforms] so the look can set any of its own. Contract shared by builders A, B and C: keep it exactly as is.
 */
public interface LiveLook {
    /** The stored id (`oc.effectsLiveStyle`), the same as the Dart `KitLiveStyle` id. */
    public val id: String

    /** The whole AGSL program: [LiveShaderLib.HEADER] plus the look's own functions and `half4 main(float2)`. */
    public val shader: String

    /** Sets the look's own uniforms, if any, after the shared ones. Called every frame: never allocate in here. */
    public fun uniforms(u: LiveUniforms)
}

/**
 * Where a look writes its own uniforms. The host backs it with `RuntimeShader.setFloatUniform` on Android 13+ and with
 * Skia's `RuntimeShaderBuilder` on desktop; a name the program does not declare is a programming error there.
 */
public interface LiveUniforms {
    public fun float(name: String, value: Float)

    public fun float2(name: String, x: Float, y: Float)

    public fun float4(name: String, x: Float, y: Float, z: Float, w: Float)
}
