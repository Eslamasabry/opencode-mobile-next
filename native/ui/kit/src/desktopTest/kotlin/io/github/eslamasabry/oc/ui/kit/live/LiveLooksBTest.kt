package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Builder B's looks (E20-S28) on builder A's host: dotRipple, topo, flowThreads, lightRays, constellation, bokeh,
 * halftone. They draw through the shared prelude plus [LiveShaderLib.TINT], with the host's own uniforms.
 */
class LiveLooksBTest {
    private val ids = listOf("dotRipple", "topo", "flowThreads", "lightRays", "constellation", "bokeh", "halftone")

    @Test
    fun `B-LIVE-B01 each stored id maps to its own look in the one registry`() {
        for (id in ids) {
            val look = LiveLooks.shader(id) ?: fail("no look for $id")
            assertEquals(id, look.id)
            assertSame(look, LiveLooks.shader(id))
            assertTrue(id in LiveLooks.offered, "$id is offered in the Look picker")
        }
    }

    @Test
    fun `B-LIVE-B02 every look compiles on the shared prelude with the tint set and sets every uniform it reads`() {
        for (id in ids) {
            val look = LiveLooks.shader(id)!!
            val compiled = runCatching { SkiaLiveLook(look) }
            assertTrue(compiled.isSuccess, "$id does not compile: ${compiled.exceptionOrNull()?.message}")
            assertTrue(look.shader.startsWith(LiveShaderLib.TINT), "$id starts from the tint set")
            assertTrue("half4 main(float2" in look.shader, "$id has an AGSL entry point")
            assertTrue("uniform " !in look.shader, "$id reads only the host's shared uniforms")
            val skia = compiled.getOrThrow()
            skia.pixels(8, 8, SkiaLiveLook.palette(true), SkiaLiveLook.Moment(1f))
            assertTrue(LiveShaderLib.INK in skia.set, "$id gets the text colour from the host")
        }
    }

    @Test
    fun `B-LIVE-B03 body text keeps 4,5 to 1 on every look at Bold, light and dark`() {
        for (dark in listOf(false, true)) {
            for (id in ids) {
                for (time in listOf(0f, 7.3f, 41f)) checkReadable(id, dark, time)
            }
        }
    }

    private fun checkReadable(id: String, dark: Boolean, time: Float) {
        val colors = SkiaLiveLook.kitColors(dark)
        val ink = lum(colors.text1.toArgb())
        val ground = lum(colors.ground.toArgb())
        val look = SkiaLiveLook(LiveLooks.shader(id)!!)
        val px = look.pixels(270, 600, SkiaLiveLook.palette(dark), SkiaLiveLook.Moment(time, strength = 1.6f))
        var worst = Double.MAX_VALUE
        var spread = 0.0
        for (c in px) {
            val l = lum(c)
            worst = min(worst, ratio(l, ink))
            spread = max(spread, abs(l - ground))
        }
        assertTrue(worst >= 4.5, "$id dark=$dark t=$time: text 1 contrast $worst")
        assertTrue(spread > 0.002, "$id dark=$dark t=$time: the look draws something ($spread)")
    }

    @Test
    fun `B-LIVE-B04 a look is still when the clock rests and moves when it runs`() {
        val palette = SkiaLiveLook.palette(true)
        for (id in ids) {
            val look = SkiaLiveLook(LiveLooks.shader(id)!!)
            val a = look.pixels(160, 320, palette, SkiaLiveLook.Moment(12f))
            val b = look.pixels(160, 320, palette, SkiaLiveLook.Moment(12f))
            val c = look.pixels(160, 320, palette, SkiaLiveLook.Moment(22f))
            assertTrue(a.contentEquals(b), "$id draws the same frame for the same time")
            assertTrue(!a.contentEquals(c), "$id moves over ten seconds")
        }
    }

    private companion object {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= .04045) s / 12.92 else ((s + .055) / 1.055).pow(2.4)
        }

        fun lum(rgb: Int): Double =
            .2126 * channel(rgb shr 16 and 255) + .7152 * channel(rgb shr 8 and 255) + .0722 * channel(rgb and 255)

        fun ratio(a: Double, b: Double): Double = (max(a, b) + .05) / (min(a, b) + .05)
    }
}
