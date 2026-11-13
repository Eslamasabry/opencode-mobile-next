package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint

/**
 * The rim light's floor in the shaders themselves (owner 2026-11-13: a pane whose edge disappears at a corner reads as
 * a broken shape). The plain pane's rim ([KitRimShader], the text Android's library draws the highlight with) and the
 * joined body's rim (the join shader's rim pass), compiled by desktop's Skia from the same text Android compiles as
 * AGSL, are read back all the way round a rounded pane's outline: the lit arcs reach the rim's peak, and nowhere,
 * corners included, does the rim fall below the floor (RimLight.FLOOR) of it. What this cannot see: the library's own
 * plumbing on a device (its stroke, clip and layer), which only a phone shows.
 */
class RimFloorShaderTest {
    private val w = 360
    private val h = 220
    private val radius = 28f

    /** The least share of the peak the rim may show anywhere: the floor (RimLight.FLOOR, .35) less rounding. */
    private val guard = .32f

    /** Points just inside the outline of a [w] x [h] pane with corners of [radius], all the way round, [inset] px in. */
    private fun outline(left: Float, top: Float, inset: Float): List<Pair<String, Pair<Float, Float>>> {
        val out = mutableListOf<Pair<String, Pair<Float, Float>>>()
        val r = radius - inset
        val l = left + inset
        val t = top + inset
        val ri = left + w - inset
        val b = top + h - inset
        val corners =
            listOf(
                Triple("top-start", Pair(l + r, t + r), 180.0),
                Triple("top-end", Pair(ri - r, t + r), 270.0),
                Triple("bottom-end", Pair(ri - r, b - r), 0.0),
                Triple("bottom-start", Pair(l + r, b - r), 90.0),
            )
        for ((name, c, from) in corners) for (step in 0..18) {
            val a = (from + step * 5.0) * PI / 180.0
            out += "$name corner at ${step * 5}°" to Pair(c.first + r * cos(a).toFloat(), c.second + r * sin(a).toFloat())
        }
        var x = l + radius
        while (x < ri - radius) {
            out += "top side" to Pair(x, t)
            out += "bottom side" to Pair(x, b)
            x += 6f
        }
        var y = t + radius
        while (y < b - radius) {
            out += "start side" to Pair(l, y)
            out += "end side" to Pair(ri, y)
            y += 6f
        }
        return out
    }

    private fun alphas(p: LiveShaderProgram, width: Int, height: Int): IntArray {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL))
        Canvas(bitmap).drawPaint(Paint().apply { shader = (p as DesktopLiveShader).skiaShader() })
        return IntArray(width * height) { i -> (bitmap.getColor(i % width, i / width) ushr 24) and 0xFF }
    }

    private fun check(what: String, got: IntArray, width: Int, points: List<Pair<String, Pair<Float, Float>>>) {
        val at = points.map { (name, p) -> name to got[p.second.toInt() * width + p.first.toInt()] }
        val peak = at.maxOf { it.second }
        assertTrue(peak >= 250, "$what: the lit arcs reach the rim's peak ($peak)")
        val low = at.filter { it.second < guard * 255f }
        assertTrue(low.isEmpty(), "$what: the rim falls below the floor at\n" + low.joinToString("\n") { "${it.first}: ${it.second}" })
    }

    @Test
    fun `rim floor - the plain pane's rim never falls below the floor round its outline`() {
        val p = assertNotNull(DesktopLiveShader.compile(KitRimShader.SOURCE), "the plain pane's rim shader compiles as SkSL")
        KitRimShader.set(p, Size(w.toFloat(), h.toFloat()), floatArrayOf(radius, radius, radius, radius), Color.White)
        check("plain pane", alphas(p, w, h), w, outline(0f, 0f, 1.5f))
    }

    @Test
    fun `rim floor - the joined body's rim never falls below the floor round a lone pane`() {
        val p = assertNotNull(DesktopLiveShader.compile(LiquidJoinShader.source(effect = false)), "the join shader compiles as SkSL")
        val left = 20f
        val top = 20f
        val rimPx = 4f
        val u =
            JoinUniforms(
                geometry = JoinGeometry.alone(Rect(left, top, left + w, top + h), radius),
                reach = 0f,
                band = 1f,
                rimMine = 22f,
                rimTheirs = 0f,
                refractionMine = 0f,
                refractionTheirs = 0f,
                saturation = 1f,
                contrast = 1f,
                brightness = 0f,
                knee = KNEE_OFF,
                tintMine = Color.Transparent,
                tintTheirs = Color.Transparent,
                rimPx = rimPx,
            )
        u.set(p, mode = 2f, res = 1f)
        check("joined body", alphas(p, w + 40, h + 40), w + 40, outline(left, top, rimPx / 2f))
    }
}
