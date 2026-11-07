package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.Brush

/** Turns a look's shader text into a program; null when the text does not compile. */
public fun interface LiveShaderCompiler {
    public fun compile(source: String): LiveShaderProgram?
}

/**
 * One compiled look shader and its uniforms, made by the platform (`KitPlatform.liveShaders`). The host sets the
 * uniforms, then draws [brush] once over the whole layer. On Android the brush wraps one mutable `RuntimeShader`, so a
 * frame allocates nothing on the heap.
 */
public abstract class LiveShaderProgram : LiveUniforms() {
    /** The shader with the uniforms set so far, as a brush for one `drawRect`. */
    public abstract val brush: Brush
}
