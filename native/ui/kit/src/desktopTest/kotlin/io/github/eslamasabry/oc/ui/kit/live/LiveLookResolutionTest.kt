package io.github.eslamasabry.oc.ui.kit.live

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * bg-looks 2026-11-10 (owner: "will the looks be raised to the maximum possible resolution?"): each look draws at its own
 * resolution, fine marks at every screen pixel, smooth fields reduced and finished at full resolution, where the dither
 * is added after the upscale.
 */
class LiveLookResolutionTest {
    private val marks =
        listOf(
            "constellation", "dotRipple", "flowThreads", "lightRays", "radar", "oscilloscope", "halftone", "honeycomb",
            "topo", "blueprint", "circuit", "matrixRain", "brushedMetal",
        )
    private val fields = listOf("aurora", "mesh", "silk", "caustics", "liquidBlobs", "glowSpill", "bokeh")

    @Test
    fun `B-LIVE-R01 fine-mark looks draw at full resolution, smooth fields reduced, and the platform cap lowers both`() {
        assertEquals((marks + fields).toSet(), LiveLooks.ids.filter { LiveLooks.shader(it) != null }.toSet(), "every look is placed")
        for (id in marks) assertEquals(LiveResolution.FULL, LiveLooks.shader(id)!!.resolution, id)
        for (id in fields) assertEquals(LiveResolution.FIELD, LiveLooks.shader(id)!!.resolution, id)
        assertFalse(LiveResolution.FORCE_FULL, "the debug override ships off")
        val topo = LiveLooks.shader("topo")!!
        assertEquals(LiveResolution.FULL, LiveResolution.of(topo))
        assertEquals(.5f, LiveResolution.of(topo, cap = .5f))
        assertEquals(LiveResolution.FIELD, LiveResolution.of(LiveLooks.shader("mesh")!!, cap = 1f))
    }

    /** The reduced pass's encoding decodes (as LiveShaderLib.FINISH does) to the look's own colour within the dither. */
    @Test
    fun `B-LIVE-R02 a reduced pass writes what the finish decodes back to the look, undithered`() {
        for (dark in listOf(true, false)) {
            val palette = SkiaLiveLook.palette(dark)
            val g = intArrayOf((palette.ground.red * 255).roundToInt(), (palette.ground.green * 255).roundToInt(), (palette.ground.blue * 255).roundToInt())
            for (id in fields + listOf("topo", "radar")) {
                val skia = SkiaLiveLook(LiveLooks.shader(id)!!)
                val at = SkiaLiveLook.Moment(40f, 1.6f)
                val direct = skia.pixels(90, 200, palette, at)
                val enc = skia.pixels(90, 200, palette, at, encoded = true)
                var worst = 0f
                for (i in direct.indices) for (c in 0..2) {
                    val sh = 16 - 8 * c
                    val e = ((enc[i] shr sh) and 255) / 255f
                    val decoded = g[c] + (e - .5f) * 2f * LiveShaderLib.ENCODE_RANGE * 255f
                    worst = maxOf(worst, abs(decoded - ((direct[i] shr sh) and 255)))
                }
                // The direct frame carries up to 1.5 steps of dither, the encoding 0.64 of a step of rounding.
                assertTrue(worst <= 3f, "$id dark=$dark: decoded differs by $worst steps")
            }
        }
    }
}
