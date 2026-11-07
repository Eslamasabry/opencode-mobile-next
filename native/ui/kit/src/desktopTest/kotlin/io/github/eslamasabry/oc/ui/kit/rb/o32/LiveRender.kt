package io.github.eslamasabry.oc.ui.kit.rb.o32

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.kit.live.KitLiveFrame
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.KitLivePalette
import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

/** Paints looks off screen, as the Flutter tests do (`test/kit_live_styles_industrial_test.dart` `_render`). */
internal object LiveRender {
    val phone = Size(412f, 915f)
    const val T = 7.3

    fun palette(dark: Boolean): KitLivePalette = KitLivePalette.of(Graphite.of(dark).toKitColors())

    val measurer: TextMeasurer by lazy { TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr) }

    fun frame(
        p: KitLivePalette,
        t: Double = T,
        running: Double = 0.0,
        strength: Double = 1.0,
        memory: KitLiveMemory = KitLiveMemory(),
    ): KitLiveFrame = KitLiveFrame(t, running, strength, p, memory, measurer, FontFamily.Monospace)

    /** Ground then [style] at [size] dp drawn at [density], as ARGB pixels. */
    fun pixels(
        style: KitLiveStyle,
        p: KitLivePalette,
        size: Size = phone,
        density: Float = 1f,
        t: Double = T,
        running: Double = 0.0,
        strength: Double = 1.0,
        memory: KitLiveMemory = KitLiveMemory(),
    ): IntArray {
        val w = Math.round(size.width * density)
        val h = Math.round(size.height * density)
        val image = ImageBitmap(w, h)
        draw(Canvas(image), style, size, density, frame(p, t, running, strength, memory))
        val out = IntArray(w * h)
        image.readPixels(out)
        return out
    }

    fun draw(canvas: Canvas, style: KitLiveStyle, size: Size, density: Float, frame: KitLiveFrame) {
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(size.width * density, size.height * density)) {
            drawRect(frame.palette.ground)
            scale(density, density, pivot = Offset.Zero) { style.paint(this, size, frame) }
        }
    }

    /** Records one frame into a picture (what a hardware canvas does on the UI thread); returns nanoseconds. */
    fun recordNanos(style: KitLiveStyle, size: Size, density: Float, frame: KitLiveFrame): Long {
        val rec = PictureRecorder()
        val sk = rec.beginRecording(Rect.makeWH(size.width * density, size.height * density))
        val start = System.nanoTime()
        draw(sk.asComposeCanvas(), style, size, density, frame)
        val took = System.nanoTime() - start
        rec.finishRecordingAsPicture().close()
        return took
    }

    /** The largest channel difference from the ground (Dart `_maxDelta`). */
    fun maxDelta(px: IntArray, p: KitLivePalette): Int {
        val g = intArrayOf(
            Math.round(p.ground.red * 255),
            Math.round(p.ground.green * 255),
            Math.round(p.ground.blue * 255),
        )
        var worst = 0
        for (c in px) {
            val r = (c shr 16) and 0xFF
            val gr = (c shr 8) and 0xFF
            val b = c and 0xFF
            worst = maxOf(worst, kotlin.math.abs(r - g[0]), kotlin.math.abs(gr - g[1]), kotlin.math.abs(b - g[2]))
        }
        return worst
    }
}
