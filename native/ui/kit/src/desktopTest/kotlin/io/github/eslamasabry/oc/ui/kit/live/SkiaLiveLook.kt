package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** The theme colours a look reads (the kit colour roles), for the desktop renders. */
internal data class LiveColors(val ground: Color, val accent: Color, val ink: Color)

/** One frame's inputs: the rect in pixels, pixels per dp, clock seconds, theme colours and intensity. */
internal data class LiveFrame(
    val w: Float,
    val h: Float,
    val density: Float = 1f,
    val time: Float = 0f,
    val colors: LiveColors,
    val intensity: Float = 1f,
)

/**
 * A look's AGSL compiled by Skia's runtime effects (the same shading language Android's RuntimeShader runs), with the
 * shared uniforms set the way the host sets them. Test-only: the stills and the contrast check draw through it.
 */
internal class SkiaLiveLook(val look: LiveLook) {
    val effect: RuntimeEffect = RuntimeEffect.makeForShader(look.shader)
    private val builder = RuntimeShaderBuilder(effect)
    private val sink =
        object : LiveUniforms {
            override fun float(name: String, value: Float) = builder.uniform(name, value)

            override fun float2(name: String, x: Float, y: Float) = builder.uniform(name, x, y)

            override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) =
                builder.uniform(name, x, y, z, w)
        }

    fun shader(f: LiveFrame): Shader {
        sink.float2("uResolution", f.w, f.h)
        sink.float("uDensity", f.density)
        sink.float("uTime", f.time)
        sink.color("uGround", f.colors.ground)
        sink.color("uAccent", f.colors.accent)
        sink.color("uInk", f.colors.ink)
        sink.float("uIntensity", f.intensity)
        look.uniforms(sink)
        return builder.makeShader()
    }

    /** The look alone, the frame's size in pixels. */
    fun render(f: LiveFrame): BufferedImage {
        val surface = Surface.makeRasterN32Premul(f.w.toInt(), f.h.toInt())
        val paint = Paint().apply { shader = shader(f) }
        surface.canvas.drawPaint(paint)
        val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
        return ImageIO.read(ByteArrayInputStream(png))
    }

    private fun LiveUniforms.color(name: String, c: Color) = float4(name, c.red, c.green, c.blue, 1f)
}
