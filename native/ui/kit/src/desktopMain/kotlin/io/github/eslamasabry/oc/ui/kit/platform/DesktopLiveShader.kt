package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
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

    /**
     * This shader as an image filter over its `content` input, blurred first by [blurPx] (a radius, as Compose's
     * `BlurEffect` takes it); null if Skia refuses it. Joined glass's lens pass (`KitPlatform.liquidJoinEffect`).
     */
    fun effect(blurPx: Float): androidx.compose.ui.graphics.RenderEffect? = filter(blurPx)?.asComposeRenderEffect()

    /** The shader with the uniforms set so far, as Skia's own (tests read it back without Compose). */
    fun skiaShader(): org.jetbrains.skia.Shader = builder.makeShader()

    /** [effect] as Skia's image filter. */
    fun filter(blurPx: Float): org.jetbrains.skia.ImageFilter? =
        try {
            val sigma = if (blurPx > 0f) blurPx * 0.57735f + 0.5f else 0f
            val input = if (sigma > 0f) org.jetbrains.skia.ImageFilter.makeBlur(sigma, sigma, org.jetbrains.skia.FilterTileMode.CLAMP, null, null) else null
            org.jetbrains.skia.ImageFilter.makeRuntimeShader(builder, "content", input)
        } catch (_: RuntimeException) {
            null
        }

    companion object {
        private val finishEffect by lazy { RuntimeEffect.makeForShader(io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib.FINISH) }

        /** The full-resolution finish of a reduced look on [ground] (LiveShaderLib.FINISH as an image filter). */
        fun finish(ground: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.RenderEffect? =
            try {
                val b = RuntimeShaderBuilder(finishEffect)
                b.uniform("uGround", ground.red, ground.green, ground.blue, 1f)
                org.jetbrains.skia.ImageFilter.makeRuntimeShader(b, "content", null).asComposeRenderEffect()
            } catch (_: RuntimeException) {
                null
            }

        /** Null when the text does not compile. */
        fun compile(source: String): LiveShaderProgram? =
            try {
                DesktopLiveShader(RuntimeEffect.makeForShader(source))
            } catch (_: RuntimeException) {
                null
            }
    }
}
