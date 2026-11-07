package io.github.eslamasabry.oc.ui.kit.live

import io.github.eslamasabry.oc.ui.kit.live.looks.BlueprintLook
import io.github.eslamasabry.oc.ui.kit.live.looks.BrushedMetalLook
import io.github.eslamasabry.oc.ui.kit.live.looks.CircuitLook
import io.github.eslamasabry.oc.ui.kit.live.looks.HoneycombLook
import io.github.eslamasabry.oc.ui.kit.live.looks.MatrixRainLook
import io.github.eslamasabry.oc.ui.kit.live.looks.OscilloscopeLook
import io.github.eslamasabry.oc.ui.kit.live.looks.RadarLook
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Builder C's live looks (E20-S28): ids, that each AGSL program compiles and draws through Skia's runtime effects (the
 * desktop twin of Android's RuntimeShader), and the quality bar the brief sets: text stays readable over every pixel at
 * every intensity in both themes, the look stays in the low-contrast range, it moves, and a frame is deterministic.
 */
class LiveLooksCTest {
    private val mine =
        listOf(MatrixRainLook, BlueprintLook, CircuitLook, OscilloscopeLook, RadarLook, HoneycombLook, BrushedMetalLook)

    @Test
    fun `E20-S28 C ids are the stored Dart KitLiveStyle ids and resolve through the registry`() {
        assertEquals(
            listOf("matrixRain", "blueprint", "circuit", "oscilloscope", "radar", "honeycomb", "brushedMetal"),
            mine.map { it.id },
        )
        mine.forEach { assertSame(it, LiveLooks.byId(it.id)) }
        assertEquals(LiveLooks.all.size, LiveLooks.all.map { it.id }.toSet().size, "one entry per id")
        assertNull(LiveLooks.byId("notALook"))
    }

    @Test
    fun `E20-S28 C every look compiles and sets only uniforms its program declares`() {
        mine.forEach { look ->
            assertNotNull(RuntimeEffect.makeForShader(look.shader), "${look.id} compiles")
            val rec = Recorder(DARK, 1f)
            look.uniforms(rec)
            assertTrue(rec.names.isNotEmpty(), "${look.id} sets its own knob")
            rec.names.forEach { assertTrue(look.shader.contains("uniform float $it;"), "${look.id} declares $it") }
        }
    }

    @Test
    fun `E20-S28 C text stays readable over every pixel at Subtle Normal and Bold in dark and light`() {
        for (theme in listOf(DARK, LIGHT)) {
            for (strength in listOf(.7f, 1f, 1.6f)) {
                mine.forEach { look -> checkReadable(look, theme, strength) }
            }
        }
    }

    private fun checkReadable(look: LiveLook, theme: Theme, strength: Float) {
        val px = render(look, theme, strength, t = 12f)
        var worst = Double.MAX_VALUE
        var drift = 0
        for (i in px.indices step 4) {
            val rgb = (px[i].u() shl 16) or (px[i + 1].u() shl 8) or px[i + 2].u()
            worst = min(worst, contrast(theme.text2, rgb))
            drift = max(drift, channelDelta(theme.ground, rgb))
        }
        assertTrue(worst >= 4.5, "${look.id} ${theme.name} x$strength: secondary text contrast $worst")
        assertTrue(drift <= 60, "${look.id} ${theme.name} x$strength: strays $drift/255 from the ground")
    }

    @Test
    fun `E20-S28 C each look moves over time and draws the same frame for the same time`() {
        mine.forEach { look ->
            val a = render(look, DARK, 1f, t = 20f)
            val again = render(look, DARK, 1f, t = 20f)
            val later = render(look, DARK, 1f, t = 24f)
            assertTrue(a.contentEquals(again), "${look.id} is deterministic")
            assertFalse(a.contentEquals(later), "${look.id} moves")
        }
    }

    private class Theme(
        val name: String,
        val ground: Int,
        val accent: Int,
        val ink: Int,
        val text2: Int,
        val dark: Boolean,
    )

    /** A LiveUniforms over a Skia builder; [names] records the look's own uniforms. */
    private class Recorder(theme: Theme, override val intensity: Float, val builder: RuntimeShaderBuilder? = null) :
        LiveUniforms {
        override val dark: Boolean = theme.dark
        override val density: Float = DENSITY
        override val width: Float = W.toFloat()
        override val height: Float = H.toFloat()
        override val time: Float = 0f
        val names = mutableListOf<String>()

        override fun float(name: String, value: Float) {
            names += name
            builder?.uniform(name, value)
        }

        override fun float2(name: String, x: Float, y: Float) {
            names += name
            builder?.uniform(name, x, y)
        }

        override fun color(name: String, argb: Int) {
            names += name
            builder?.uniform(name, ch(argb, 16), ch(argb, 8), ch(argb, 0), ch(argb, 24))
        }
    }

    private fun render(look: LiveLook, theme: Theme, strength: Float, t: Float): ByteArray {
        val w = W
        val h = H
        val density = DENSITY
        val b = RuntimeShaderBuilder(RuntimeEffect.makeForShader(look.shader))
        b.uniform(LiveShaderLib.RES, w.toFloat(), h.toFloat())
        b.uniform(LiveShaderLib.TIME, t)
        b.uniform(LiveShaderLib.INTENSITY, strength)
        b.uniform(LiveShaderLib.DENSITY, density)
        b.uniform(LiveShaderLib.DARK, if (theme.dark) 1f else 0f)
        val colors =
            listOf(
                LiveShaderLib.GROUND to theme.ground,
                LiveShaderLib.ACCENT to theme.accent,
                LiveShaderLib.INK to theme.ink,
            )
        colors.forEach { (n, c) -> b.uniform(n, ch(c, 16), ch(c, 8), ch(c, 0), 1f) }
        look.uniforms(Recorder(theme, strength, b))
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawRect(Rect.makeWH(w.toFloat(), h.toFloat()), Paint().apply { shader = b.makeShader() })
        val bmp = Bitmap()
        bmp.allocPixels(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
        surface.readPixels(bmp, 0, 0)
        return requireNotNull(bmp.readPixels())
    }

    private companion object {
        const val W = 270
        const val H = 600
        const val DENSITY = 2.625f / 4f
        val DARK = Theme("dark", 0x0B0C0E, 0x3DDC8A, 0xF3F3F1, 0xA3A5AB, dark = true)
        val LIGHT = Theme("light", 0xF3F3F1, 0x087F43, 0x111214, 0x50535A, dark = false)

        fun ch(argb: Int, shift: Int): Float = ((argb ushr shift) and 0xFF) / 255f

        fun Byte.u(): Int = toInt() and 0xFF

        fun lum(rgb: Int): Double {
            fun lin(c: Int): Double =
                (c / 255.0).let { if (it <= .03928) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
            return .2126 * lin((rgb shr 16) and 0xFF) + .7152 * lin((rgb shr 8) and 0xFF) + .0722 * lin(rgb and 0xFF)
        }

        fun contrast(a: Int, b: Int): Double {
            val x = lum(a)
            val y = lum(b)
            return (max(x, y) + .05) / (min(x, y) + .05)
        }

        fun channelDelta(a: Int, b: Int): Int =
            maxOf(
                abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
                abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
                abs((a and 0xFF) - (b and 0xFF)),
            )
    }
}
