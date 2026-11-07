package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.toArgb
import io.github.eslamasabry.oc.ui.kit.live.looks.BlueprintLook
import io.github.eslamasabry.oc.ui.kit.live.looks.BrushedMetalLook
import io.github.eslamasabry.oc.ui.kit.live.looks.CircuitLook
import io.github.eslamasabry.oc.ui.kit.live.looks.HoneycombLook
import io.github.eslamasabry.oc.ui.kit.live.looks.MatrixRainLook
import io.github.eslamasabry.oc.ui.kit.live.looks.OscilloscopeLook
import io.github.eslamasabry.oc.ui.kit.live.looks.RadarLook
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Builder C's looks (E20-S28) on builder A's host: ids, that each program compiles on the shared prelude plus
 * [LiveShaderLib.LAYER] and draws, and the quality bar: secondary text stays readable over every pixel at every
 * intensity in both themes, the look stays in the low-contrast range, it moves, and a frame is deterministic.
 */
class LiveLooksCTest {
    private val mine =
        listOf(MatrixRainLook, BlueprintLook, CircuitLook, OscilloscopeLook, RadarLook, HoneycombLook, BrushedMetalLook)

    @Test
    fun `B-LIVE-C01 C ids are the stored Dart KitLiveStyle ids and resolve through the one registry`() {
        assertEquals(
            listOf("matrixRain", "blueprint", "circuit", "oscilloscope", "radar", "honeycomb", "brushedMetal"),
            mine.map { it.id },
        )
        mine.forEach {
            assertSame(it, LiveLooks.shader(it.id))
            assertTrue(it.id in LiveLooks.offered, "${it.id} is offered in the Look picker")
        }
    }

    @Test
    fun `B-LIVE-C02 every look compiles on the shared prelude and sets only uniforms its program declares`() {
        mine.forEach { look ->
            assertTrue(look.shader.startsWith(LiveShaderLib.LAYER), "${look.id} starts from the layer set")
            val skia = SkiaLiveLook(look)
            skia.pixels(8, 8, SkiaLiveLook.palette(true), SkiaLiveLook.Moment(1f))
            val own = skia.set - shared
            assertTrue(own.isNotEmpty(), "${look.id} sets its own knob")
            own.forEach { assertTrue(look.shader.contains("uniform float $it;"), "${look.id} declares $it") }
        }
    }

    @Test
    fun `B-LIVE-C03 text stays readable over every pixel at Subtle Normal and Bold in dark and light`() {
        for (dark in listOf(true, false)) {
            for (strength in listOf(.7f, 1f, 1.6f)) {
                mine.forEach { look -> checkReadable(look, dark, strength) }
            }
        }
    }

    private fun checkReadable(look: LiveLook, dark: Boolean, strength: Float) {
        val colors = SkiaLiveLook.kitColors(dark)
        val ground = colors.ground.toArgb() and 0xFFFFFF
        val text2 = colors.text2.toArgb() and 0xFFFFFF
        val at = SkiaLiveLook.Moment(12f, strength, DENSITY)
        val px = SkiaLiveLook(look).pixels(W, H, SkiaLiveLook.palette(dark), at)
        var worst = Double.MAX_VALUE
        var drift = 0
        for (rgb in px) {
            worst = min(worst, contrast(text2, rgb))
            drift = max(drift, channelDelta(ground, rgb))
        }
        assertTrue(worst >= 4.5, "${look.id} dark=$dark x$strength: secondary text contrast $worst")
        assertTrue(drift <= 60, "${look.id} dark=$dark x$strength: strays $drift/255 from the ground")
    }

    @Test
    fun `B-LIVE-C04 each look moves over time and draws the same frame for the same time`() {
        val palette = SkiaLiveLook.palette(true)
        mine.forEach { look ->
            val skia = SkiaLiveLook(look)
            val a = skia.pixels(W, H, palette, SkiaLiveLook.Moment(20f, density = DENSITY))
            val again = skia.pixels(W, H, palette, SkiaLiveLook.Moment(20f, density = DENSITY))
            val later = skia.pixels(W, H, palette, SkiaLiveLook.Moment(24f, density = DENSITY))
            assertTrue(a.contentEquals(again), "${look.id} is deterministic")
            assertFalse(a.contentEquals(later), "${look.id} moves")
        }
    }

    private companion object {
        const val W = 270
        const val H = 600
        const val DENSITY = 2.625f / 4f

        val shared =
            setOf(
                LiveShaderLib.RES, LiveShaderLib.TIME, LiveShaderLib.INTENSITY, LiveShaderLib.DENSITY,
                LiveShaderLib.DARK, LiveShaderLib.GROUND, LiveShaderLib.ACCENT, LiveShaderLib.PARTNER,
                LiveShaderLib.GLOW, LiveShaderLib.INK,
            )

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
