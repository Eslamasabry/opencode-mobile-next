package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.ShaderBrush
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * A live look's shader on desktop: Skia's runtime effect compiles the same text as SkSL. Skia shaders are immutable, so
 * [brush] builds a new one from the current uniforms; desktop only, where a frame's small allocation does not matter.
 */
internal class DesktopLiveShader(effect: RuntimeEffect) : LiveShaderProgram() {
    private val builder = RuntimeShaderBuilder(effect)

    override val brush: Brush
        get() = ShaderBrush(builder.makeShader().asComposeShader())

    override fun float(name: String, v: Float) = builder.uniform(name, v)

    override fun float2(name: String, x: Float, y: Float) = builder.uniform(name, x, y)

    override fun float3(name: String, x: Float, y: Float, z: Float) = builder.uniform(name, x, y, z)

    override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) = builder.uniform(name, x, y, z, w)

    companion object {
        /** Null when the text does not compile. */
        fun compile(source: String): LiveShaderProgram? =
            try {
                DesktopLiveShader(RuntimeEffect.makeForShader(source))
            } catch (_: RuntimeException) {
                null
            }
    }
}
