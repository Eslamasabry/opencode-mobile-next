package io.github.eslamasabry.oc.ui.kit.rb.o31

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.kit.live.KitLiveAurora
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBlueprint
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBokeh
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBrushedMetal
import io.github.eslamasabry.oc.ui.kit.live.KitLiveCaustics
import io.github.eslamasabry.oc.ui.kit.live.KitLiveCircuit
import io.github.eslamasabry.oc.ui.kit.live.KitLiveFrame
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The cost of one frame of each look on a 1080 × 2400 phone at density 2.75 (392.7 × 872.7 logical), measured two ways
 * on this machine's JVM and CPU Skia:
 *
 * - record: the UI-thread work of a frame (the look's own maths and draw calls, recorded into a picture, as Compose
 *   records a layer's display list on Android; the GPU rasters it on another thread);
 * - raster: the same frame drawn by CPU Skia into a 1080 × 2400 surface (an upper bound: a phone's GPU rasters far
 *   faster than one CPU core).
 *
 * The whole table runs twice and the second pass is kept (the JIT settles); each look runs 300 warm-up frames, then
 * 120 timed frames at advancing seconds (a fresh time each frame, so caches that
 * hold per-frame work cannot hide it). The table goes to `build/live-frame-cost.tsv` and stdout; the hard limit here is
 * a generous 16 ms of recording per frame (a dropped frame), so a slow CI machine does not fail on noise. The target,
 * well under 4 ms on a mid phone, is judged from the numbers in docs/design/native-ui-rebuild/evidence/31.md.
 */
class KitLiveFrameCostTest {
    private val looks: List<KitLiveStyle> =
        listOf(KitLiveBlueprint, KitLiveBokeh, KitLiveBrushedMetal, KitLiveCaustics, KitLiveCircuit, KitLiveAurora)
    private val density = 2.75f
    private val px = Size(1080f, 2400f)

    @Test fun eachLookRecordsAFrameWellUnderAFrameBudget() =
        withGraphics {
            measure() // a first pass lets the JIT compile every look's code; the second is the one reported
            measure()
        }

    private fun measure() {
        val rows = StringBuilder("look\trecord_ms_median\trecord_ms_p95\traster_ms_median\n")
        val failures = ArrayList<String>()
        val logical = Size(px.width / density, px.height / density)
        val recorder = PictureRecorder()
        val surface = Surface.makeRasterN32Premul(px.width.toInt(), px.height.toInt())
        for (look in looks) {
            val memory = KitLiveMemory()
            val palette = livePalette(dark = true)
            var t = GOLDEN_T
            val recs = LongArray(120)
            for (i in 0 until 420) {
                t += 1.0 / 12
                val f = KitLiveFrame(t, 0.0, 1.0, palette, memory)
                val start = System.nanoTime()
                val canvas = recorder.beginRecording(Rect.makeWH(px.width, px.height)).asComposeCanvas()
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, px) {
                    scale(density, density, pivot = Offset.Zero) { look.paint(this, logical, f) }
                }
                if (i >= 300) recs[i - 300] = System.nanoTime() - start
                recorder.finishRecordingAsPicture().close()
            }
            recs.sort()
            val rasters = LongArray(40)
            for (i in 0 until 50) {
                t += 1.0 / 12
                val f = KitLiveFrame(t, 0.0, 1.0, palette, memory)
                val start = System.nanoTime()
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, surface.canvas.asComposeCanvas(), px) {
                    drawRect(palette.ground)
                    scale(density, density, pivot = Offset.Zero) { look.paint(this, logical, f) }
                }
                surface.flushAndSubmit(true)
                if (i >= 10) rasters[i - 10] = System.nanoTime() - start
            }
            rasters.sort()
            val med = recs[recs.size / 2] / 1e6
            val p95 = recs[(recs.size * 95) / 100] / 1e6
            val rmed = rasters[rasters.size / 2] / 1e6
            rows.append("${look.id}\t${"%.3f".format(med)}\t${"%.3f".format(p95)}\t${"%.3f".format(rmed)}\n")
            if (med > 16) failures += "${look.id} records a frame in $med ms"
        }
        surface.close()
        println(rows)
        File("build/live-frame-cost.tsv").apply { parentFile.mkdirs() }.writeText(rows.toString())
        assertTrue(failures.isEmpty(), failures.joinToString())
    }
}
