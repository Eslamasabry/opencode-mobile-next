package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/** Builder B's looks (E20-S28): dotRipple, topo, flowThreads, lightRays, constellation, bokeh, halftone. */
@OptIn(ExperimentalTestApi::class)
class LiveLooksBTest {
    private val ids = listOf("dotRipple", "topo", "flowThreads", "lightRays", "constellation", "bokeh", "halftone")

    /** Every id the Dart `KitLiveStyle` stores; a native look must use one of them unchanged. */
    private val dartIds =
        setOf(
            "aurora", "auroraImage", "mesh", "dotRipple", "topo", "flowThreads", "glowSpill", "lightRays",
            "constellation", "halftone", "liquidBlobs", "matrixRain", "blueprint", "circuit", "oscilloscope", "radar",
            "honeycomb", "brushedMetal", "silk", "caustics", "bokeh",
        )

    @Test fun `B-LIVE-B01 each stored id maps to its own look and unknown ids to none`() {
        for (id in ids) {
            val look = LiveLookRegistry.of(id) ?: fail("no look for $id")
            assertEquals(id, look.id)
            assertSame(look, LiveLookRegistry.of(id))
        }
        assertNull(LiveLookRegistry.of("noSuchLook"))
        assertNull(LiveLookRegistry.of(null))
        val all = LiveLookRegistry.looks.map { it.id }
        assertEquals(all.size, all.toSet().size, "ids are unique")
        assertTrue(dartIds.containsAll(all), "every native id is a stored Dart id: ${all - dartIds}")
    }

    @Test fun `B-LIVE-B02 every look compiles as a runtime shader and reads the shared uniforms`() {
        for (id in ids) {
            val look = LiveLookRegistry.of(id)!!
            val compiled = runCatching { SkiaLiveLook(look) }
            assertTrue(compiled.isSuccess, "$id does not compile: ${compiled.exceptionOrNull()?.message}")
            assertTrue(look.shader.startsWith(LiveShaderLib.HEADER), "$id starts from the shared library")
            assertTrue("half4 main(float2" in look.shader, "$id has an AGSL entry point")
        }
    }

    @Test fun `B-LIVE-B03 body text keeps 4,5 to 1 on every look at Bold, light and dark`() {
        for (dark in listOf(false, true)) {
            val colors = kitColors(dark)
            val live = LiveColors(colors.ground, colors.accent, colors.text1)
            for (id in ids) {
                val look = SkiaLiveLook(LiveLookRegistry.of(id)!!)
                for (time in listOf(0f, 7.3f, 41f)) {
                    val img = look.render(LiveFrame(270f, 600f, time = time, colors = live, intensity = 1.6f))
                    val (worst, spread) = contrastOf(img, colors)
                    assertTrue(worst >= 4.5, "$id dark=$dark t=$time: text 1 contrast $worst")
                    assertTrue(spread > 0.002, "$id dark=$dark t=$time: the look draws something ($spread)")
                }
            }
        }
    }

    @Test fun `B-LIVE-B04 a look is still when the clock rests and moves when it runs`() {
        val colors = kitColors(true)
        val live = LiveColors(colors.ground, colors.accent, colors.text1)
        for (id in ids) {
            val look = SkiaLiveLook(LiveLookRegistry.of(id)!!)
            val a = look.render(LiveFrame(160f, 320f, time = 12f, colors = live))
            val b = look.render(LiveFrame(160f, 320f, time = 12f, colors = live))
            val c = look.render(LiveFrame(160f, 320f, time = 22f, colors = live))
            assertEquals(0, diff(a, b), "$id draws the same frame for the same time")
            assertTrue(diff(a, c) > 0, "$id moves over ten seconds")
        }
    }

    /** The worst text 1 contrast over the picture, and how far any pixel moved from the ground (in luminance). */
    private fun contrastOf(img: java.awt.image.BufferedImage, colors: KitColors): Pair<Double, Double> {
        var worst = Double.MAX_VALUE
        var spread = 0.0
        val g = lum(colors.ground.toArgb())
        val ink = lum(colors.text1.toArgb())
        for (y in 0 until img.height) for (x in 0 until img.width) {
            val l = lum(img.getRGB(x, y))
            worst = min(worst, ratio(l, ink))
            spread = max(spread, kotlin.math.abs(l - g))
        }
        return worst to spread
    }

    private fun diff(a: java.awt.image.BufferedImage, b: java.awt.image.BufferedImage): Int {
        var n = 0
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) n++
        return n
    }

    companion object {
        /** The real kit colours for a brightness, read from the theme. */
        internal fun kitColors(dark: Boolean): KitColors {
            var colors: KitColors? = null
            runDesktopComposeUiTest(10, 10) {
                setContent {
                    KitTheme(
                        KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) { colors = LocalKitColors.current }
                }
                waitForIdle()
            }
            return colors!!
        }

        private fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= .04045) s / 12.92 else ((s + .055) / 1.055).pow(2.4)
        }

        internal fun lum(argb: Int): Double =
            .2126 * channel(argb shr 16 and 255) + .7152 * channel(argb shr 8 and 255) + .0722 * channel(argb and 255)

        internal fun ratio(a: Double, b: Double): Double = (max(a, b) + .05) / (min(a, b) + .05)
    }
}
