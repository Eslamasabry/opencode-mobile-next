package io.github.eslamasabry.oc.ui.kit.rb.o33

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import io.github.eslamasabry.oc.ui.kit.live.KitLiveFrame
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.KitLiveOscilloscope
import io.github.eslamasabry.oc.ui.kit.live.KitLiveRadar
import io.github.eslamasabry.oc.ui.kit.live.KitLiveSilk
import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import io.github.eslamasabry.oc.ui.kit.live.KitLiveTopo
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Frame cost of each look on a 1080 x 2400 phone frame (density 2.625: 411 x 914 dp), the clock moving at its 12
 * ticks a second. Two numbers per look, medians over 120 frames after 30 warm-up frames:
 * - record: the look's `paint` into a display list (what the UI thread does on a tick on Android: build the layer's
 *   picture; the GPU then draws it on the render thread);
 * - raster: the same frame drawn by Skia on the CPU into a 1080 x 2400 bitmap (an upper bound; the phone's GPU is faster).
 * Plus the JVM heap allocated per frame once warm (paths, paints and shaders are reused). `topo-held` is topo with
 * its clock held (the field is not recomputed): what recording the mesh alone costs. `empty` draws nothing: the
 * harness's own time and allocation, to subtract. Numbers go to
 * `build/reports/live-frame-cost.tsv` and the test log; the assertion is a loose ceiling so a slow CI machine passes.
 */
class KitLiveFrameCostTest {
    private val looks: List<KitLiveStyle> = listOf(KitLiveOscilloscope, KitLiveRadar, KitLiveSilk, KitLiveTopo)
    private val density = 2.625f
    private val px = Pair(1080, 2400)
    private val size = Size(px.first / density, px.second / density)

    private fun allocated(): Long =
        (ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean).getThreadAllocatedBytes(Thread.currentThread().id)

    @Test fun eachLookRecordsAPhoneFrameWellUnderFourMilliseconds() {
        graphicsReady()
        val out = StringBuilder("look\trecord_median_ms\trecord_p90_ms\traster_median_ms\tbytes_per_frame\n")
        val bitmap = Bitmap().apply { allocN32Pixels(px.first, px.second) }
        val raster = SkCanvas(bitmap)
        for ((name, look, moves) in looks.map { Triple(it.id, it, true) } + Triple("topo-held", KitLiveTopo as KitLiveStyle, false) +
            Triple("empty", Empty, true)) {
            val memory = KitLiveMemory()
            val frame = KitLiveFrame(GOLDEN_T, 0.0, 1.0, darkPalette, memory)
            val recorder = PictureRecorder()
            val record = ArrayList<Double>()
            val draw = ArrayList<Double>()
            var bytes = 0L
            for (i in 0 until WARM + FRAMES) {
                frame.t = if (moves) GOLDEN_T + i / 12.0 else GOLDEN_T
                val before = allocated()
                val t0 = System.nanoTime()
                val sk = recorder.beginRecording(Rect.makeWH(px.first.toFloat(), px.second.toFloat()))
                val canvas = sk.asComposeCanvas()
                canvas.scale(density, density)
                look.paint(canvas, size, frame)
                val picture = recorder.finishRecordingAsPicture()
                val t1 = System.nanoTime()
                val after = allocated()
                raster.clear(0xFF0B0C0E.toInt())
                raster.drawPicture(picture)
                val t2 = System.nanoTime()
                picture.close()
                if (i >= WARM) {
                    record += (t1 - t0) / 1e6
                    draw += (t2 - t1) / 1e6
                    bytes += after - before
                }
            }
            record.sort()
            draw.sort()
            val line = String.format(
                java.util.Locale.ROOT, "%s\t%.3f\t%.3f\t%.3f\t%d",
                name, record[FRAMES / 2], record[FRAMES * 9 / 10], draw[FRAMES / 2], bytes / FRAMES,
            )
            println("live frame cost: $line")
            out.append(line).append('\n')
            assertTrue(record[FRAMES / 2] < CEILING_MS, "$name records in ${record[FRAMES / 2]} ms")
        }
        File("build/reports").mkdirs()
        File("build/reports/live-frame-cost.tsv").writeText(out.toString())
    }

    /** A look that draws nothing: the harness's own cost and allocation (recorder, canvas wrapper). */
    private object Empty : KitLiveStyle() {
        override val id: String = "empty"
        override fun paint(canvas: androidx.compose.ui.graphics.Canvas, size: Size, frame: KitLiveFrame) {}
    }

    private companion object {
        const val WARM = 30
        const val FRAMES = 120
        const val CEILING_MS = 4.0
    }
}
