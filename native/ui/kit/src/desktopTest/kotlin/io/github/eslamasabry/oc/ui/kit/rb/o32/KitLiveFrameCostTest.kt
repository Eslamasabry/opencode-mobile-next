package io.github.eslamasabry.oc.ui.kit.rb.o32

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMatrixRain
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyles
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Frame cost of every order-32 look on a 1080×2400 phone (412×915 dp at 2.625). Two numbers per look:
 * - record: the look's own work on the UI thread, what a hardware canvas costs per tick (the GPU rasterises it);
 * - raster: Skia's CPU rasteriser drawing every pixel, a pessimistic bound with no GPU at all.
 * Both are medians of 40 frames after 20 warm-up frames, with the clock moving one tick (1/12 s) per frame. The
 * owner's budget is "well under 4 ms on a mid phone"; the test holds the recorded cost under 4 ms here and prints both.
 */
class KitLiveFrameCostTest {
    private val looks: List<KitLiveStyle> =
        kitLiveStyles.map { if (it is KitLiveMatrixRain) KitLiveMatrixRain(KitLiveMatrixRain.ASCII_GLYPHS) else it }

    @Test fun everyLookRecordsAPhoneFrameUnderFourMilliseconds() {
        LiveRender.ensureGraphics()
        val density = 2.625f
        val size = LiveRender.phone
        val p = LiveRender.palette(true)
        val report = StringBuilder("look\trecord_ms\traster_ms\n")
        val slow = mutableListOf<String>()
        for (style in looks) {
            val memory = KitLiveMemory()
            var t = LiveRender.T
            val record = LongArray(40)
            repeat(60) { k ->
                val took = LiveRender.recordNanos(style, size, density, LiveRender.frame(p, t = t, memory = memory))
                if (k >= 20) record[k - 20] = took
                t += 1.0 / 12
            }
            val image = ImageBitmap(Math.round(size.width * density), Math.round(size.height * density))
            val canvas = Canvas(image)
            val raster = LongArray(40)
            repeat(60) { k ->
                val start = System.nanoTime()
                LiveRender.draw(canvas, style, size, density, LiveRender.frame(p, t = t, memory = memory))
                image.readPixels(IntArray(1), 0, 0, 1, 1) // forces the raster to finish
                val took = System.nanoTime() - start
                if (k >= 20) raster[k - 20] = took
                t += 1.0 / 12
            }
            val rec = record.sorted()[20] / 1e6
            val ras = raster.sorted()[20] / 1e6
            report.append("${style.id}\t${"%.2f".format(rec)}\t${"%.2f".format(ras)}\n")
            if (rec >= 4.0) slow += "${style.id} ${"%.2f".format(rec)} ms"
        }
        println(report)
        assertTrue(slow.isEmpty(), "over 4 ms to record a frame: $slow\n$report")
    }
}
