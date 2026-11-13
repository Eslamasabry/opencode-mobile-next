package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.design.effects.JoinBox
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidHold
import io.github.eslamasabry.oc.ui.design.effects.LiquidJoin
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint

/**
 * Words on glass 3: the hold (the luminance knee and the colour held back with it) in the two shaders that draw it, the
 * join shader (`LiquidJoinShader`, joined glass) and the plain pane's (`LiquidHoldShader`), both compiled by desktop's
 * Skia from the text Android compiles as AGSL, against the reference `LiquidEdge.heldRgb`; and that every declared
 * uniform is set before every draw.
 */
class KitLiquidHoldShaderTest {
    private val knee = floatArrayOf(LiquidJoin.KNEE, LiquidJoin.KNEE_SLOPE, 1f)
    private val kneeDark = floatArrayOf(LiquidJoin.KNEE, LiquidJoin.KNEE_SLOPE, 0f)

    /** A recording program: remembers which uniforms were set. */
    private class Recorder : LiveShaderProgram() {
        val names = mutableSetOf<String>()
        override val brush: Brush get() = error("not drawn")
        override fun float(name: String, v: Float) { names += name }
        override fun float2(name: String, x: Float, y: Float) { names += name }
        override fun float3(name: String, x: Float, y: Float, z: Float) { names += name }
        override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) { names += name }
    }

    private fun declared(source: String): Set<String> =
        Regex("""uniform\s+(?!shader)\w+\s+(\w+)\s*;""").findAll(source).map { it.groupValues[1] }.toSet()

    @Test
    fun `every uniform a shader declares is set before every draw`() {
        val plain = Recorder()
        LiquidHoldUniforms(knee, LiquidEdge.HOLD_CHROMA, 0f, 0f, 20f, 20f, 8f, 20f, 1f, 1f, 0f).set(plain)
        assertEquals(declared(LiquidHoldShader.SOURCE), plain.names, "the plain pane's hold")
        for (effect in listOf(true, false)) {
            val join = Recorder()
            val box = JoinBox(20f, 20f, 20f, 20f, 8f)
            JoinUniforms(JoinGeometry(box, box, box, box), 0f, 1f, 4f, 4f, 0f, 0f, 1f, 1f, 0f, knee, Color.Transparent, Color.Transparent, 1f, LiquidEdge.HOLD_CHROMA).set(join, 0f, 1f)
            assertEquals(declared(LiquidJoinShader.source(effect)), join.names, "the join shader (effect=$effect)")
        }
    }

    private fun renderEffect(program: LiveShaderProgram, rgb: IntArray, at: Pair<Int, Int>): IntArray {
        val filter = (program as DesktopLiveShader).filter(0f)
        assertNotNull(filter, "the shader makes an image filter")
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(40, 40, ColorAlphaType.PREMUL))
        val canvas = Canvas(bitmap)
        canvas.saveLayer(null, Paint().apply { imageFilter = filter })
        canvas.drawPaint(Paint().apply { color = org.jetbrains.skia.Color.makeRGB(rgb[0], rgb[1], rgb[2]) })
        canvas.restore()
        val c = bitmap.getColor(at.first, at.second)
        return intArrayOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
    }

    private fun joinPixel(rgb: IntArray, knee: FloatArray, hold: Float, at: Pair<Int, Int>): IntArray {
        val program = assertNotNull(DesktopLiveShader.compile(LiquidJoinShader.source(effect = true)), "the join shader compiles as SkSL")
        val box = JoinBox(20f, 20f, 20f, 20f, 8f)
        val far = JoinBox(5000f, 5000f, 10f, 10f, 10f)
        JoinUniforms(JoinGeometry(box, far, box, far), 0f, LiquidJoin.BAND_DP, 20f, 20f, 0f, 0f, 1f, 1f, 0f, knee, Color.Transparent, Color.Transparent, 1f, hold).set(program, 0f, 1f)
        return renderEffect(program, rgb, at)
    }

    private fun plainPixel(rgb: IntArray, knee: FloatArray, hold: Float, at: Pair<Int, Int>): IntArray {
        val program = assertNotNull(DesktopLiveShader.compile(LiquidHoldShader.SOURCE), "the plain pane's hold compiles as SkSL")
        LiquidHoldUniforms(knee, hold, 0f, 0f, 20f, 20f, 8f, 20f, 1f, 1f, 0f).set(program)
        return renderEffect(program, rgb, at)
    }

    private fun close(got: IntArray, want: FloatArray, what: String) {
        for (i in 0..2) assertTrue(abs(got[i] - want[i] * 255f) <= 3f, "$what: channel $i is ${got[i]}, wanted ${want[i] * 255f}")
    }

    private val samples = listOf(intArrayOf(0, 255, 0), intArrayOf(255, 128, 0), intArrayOf(230, 230, 230), intArrayOf(52, 199, 89), intArrayOf(40, 40, 40), intArrayOf(20, 60, 200))
    private val centre = 20 to 20
    private val edge = 2 to 20

    @Test
    fun `both shaders hold light and colour deep in the pane as LiquidEdge heldRgb does, under light and under dark ink`() {
        for (rgb in samples) for (ink in listOf(true, false)) {
            val k = if (ink) knee else kneeDark
            val want = LiquidEdge.heldRgb(floatArrayOf(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f), ink, LiquidHold(true, LiquidEdge.HOLD_CHROMA))
            close(joinPixel(rgb, k, LiquidEdge.HOLD_CHROMA, centre), want, "join ${rgb.toList()} lightInk=$ink")
            close(plainPixel(rgb, k, LiquidEdge.HOLD_CHROMA, centre), want, "plain ${rgb.toList()} lightInk=$ink")
        }
    }

    @Test
    fun `no colour hold is the knee alone, in both shaders`() {
        for (rgb in samples) {
            val want = LiquidEdge.heldRgb(floatArrayOf(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f), true, LiquidHold(true, 0f))
            close(joinPixel(rgb, knee, 0f, centre), want, "join ${rgb.toList()}")
            close(plainPixel(rgb, knee, 0f, centre), want, "plain ${rgb.toList()}")
        }
    }

    @Test
    fun `the rim keeps its colour - at the edge of the pane both shaders leave the knee's colour`() {
        for (rgb in listOf(intArrayOf(0, 255, 0), intArrayOf(255, 128, 0))) {
            val want = LiquidEdge.heldRgb(floatArrayOf(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f), true, LiquidHold(true, 0f))
            close(joinPixel(rgb, knee, LiquidEdge.HOLD_CHROMA, edge), want, "join rim ${rgb.toList()}")
            close(plainPixel(rgb, knee, LiquidEdge.HOLD_CHROMA, edge), want, "plain rim ${rgb.toList()}")
        }
    }

    @Test
    fun `a pure green and a pure orange keep at most a third of their chroma in the middle of the pane, in both shaders`() {
        for (rgb in listOf(intArrayOf(0, 255, 0), intArrayOf(255, 128, 0))) {
            // Today's: the colour controls at 1.6 make green (0,255,0) and orange (255,117,0) the glass's own colour.
            for (got in listOf(joinPixel(rgb, knee, LiquidEdge.HOLD_CHROMA, centre), plainPixel(rgb, knee, LiquidEdge.HOLD_CHROMA, centre))) {
                assertTrue((got.max() - got.min()) <= 255 / 3, "${rgb.toList()} -> ${got.toList()}")
            }
        }
    }

    /**
     * The plain pane's hold over a white page with two 3 px black strokes (a letter's stem: one in the rim at x 2-4, one
     * in the middle at x 19-21), every pixel of row 20 (a 40 px pane, rim 20 px).
     */
    private fun plainStrokes(knee: FloatArray, local: FloatArray): IntArray {
        val program = assertNotNull(DesktopLiveShader.compile(LiquidHoldShader.SOURCE))
        LiquidHoldUniforms(knee, 0f, 0f, 0f, 20f, 20f, 8f, 20f, 1f, 1f, 0f, local).set(program)
        val filter = assertNotNull((program as DesktopLiveShader).filter(0f))
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(40, 40, ColorAlphaType.PREMUL))
        val canvas = Canvas(bitmap)
        canvas.saveLayer(null, Paint().apply { imageFilter = filter })
        canvas.drawPaint(Paint().apply { color = org.jetbrains.skia.Color.makeRGB(255, 255, 255) })
        for (x in listOf(2f, 19f)) canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(x, 0f, 3f, 40f), Paint().apply { color = org.jetbrains.skia.Color.makeRGB(0, 0, 0) })
        canvas.restore()
        return IntArray(40) { x -> (bitmap.getColor(x, 20) shr 8) and 0xFF }
    }

    @Test
    fun `B-GLASS-LIQ-002 the detail hold softens fine detail in the middle and leaves the rim's bent content whole`() {
        val row = plainStrokes(KNEE_OFF, floatArrayOf(13f, 0f, .15f))
        val middle = row.slice(14..26).let { it.max() - it.min() }
        val rim = row.slice(0..8).let { it.max() - it.min() }
        println("stripes under the detail hold: middle contrast $middle, rim contrast $rim")
        assertTrue(rim >= 240, "the rim's outer half keeps its detail: $rim")
        // The stroke keeps .15 of its depth below the local mean; the page round it lifts a little less: at most a third.
        assertTrue(middle <= 255 / 3, "the middle holds back most of the stroke: $middle")
    }

    @Test
    fun `B-GLASS-LIQ-003 the detail hold runs with the knee and the colour limit, not instead of them, in both shaders`() {
        // A flat colour has no detail: the detail hold passes it, and the knee and the colour limit hold it as without one.
        val local = floatArrayOf(6f, .03f, .3f)
        for (rgb in samples) {
            val want = LiquidEdge.heldRgb(floatArrayOf(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f), true, LiquidHold(true, LiquidEdge.HOLD_CHROMA))
            val plain = assertNotNull(DesktopLiveShader.compile(LiquidHoldShader.SOURCE))
            LiquidHoldUniforms(knee, LiquidEdge.HOLD_CHROMA, 0f, 0f, 20f, 20f, 8f, 20f, 1f, 1f, 0f, local).set(plain)
            close(renderEffect(plain, rgb, centre), want, "plain with the detail hold ${rgb.toList()}")
            val join = assertNotNull(DesktopLiveShader.compile(LiquidJoinShader.source(effect = true)))
            val box = JoinBox(20f, 20f, 20f, 20f, 8f)
            val far = JoinBox(5000f, 5000f, 10f, 10f, 10f)
            JoinUniforms(JoinGeometry(box, far, box, far), 0f, LiquidJoin.BAND_DP, 20f, 20f, 0f, 0f, 1f, 1f, 0f, knee, Color.Transparent, Color.Transparent, 1f, LiquidEdge.HOLD_CHROMA, local = local).set(join, 0f, 1f)
            close(renderEffect(join, rgb, centre), want, "join with the detail hold ${rgb.toList()}")
        }
    }

    /**
     * The plain pane's hold over an [n] px square drawn by [draw] (the pane is the whole square, its rim 20 px), every
     * pixel's RGB of row [y].
     */
    private fun plainRow(knee: FloatArray, local: FloatArray, y: Int, n: Int = 80, transparentPast: Int = n, draw: Canvas.() -> Unit): List<IntArray> {
        val program = assertNotNull(DesktopLiveShader.compile(LiquidHoldShader.SOURCE))
        LiquidHoldUniforms(knee, 0f, 0f, 0f, n / 2f, n / 2f, 8f, 20f, 1f, 1f, 0f, local).set(program)
        val filter = assertNotNull((program as DesktopLiveShader).filter(0f))
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(n, n, ColorAlphaType.PREMUL))
        val canvas = Canvas(bitmap)
        canvas.saveLayer(null, Paint().apply { imageFilter = filter })
        canvas.save()
        canvas.clipRect(org.jetbrains.skia.Rect.makeWH(transparentPast.toFloat(), n.toFloat()))
        canvas.draw()
        canvas.restore()
        canvas.restore()
        return List(n) { x -> bitmap.getColor(x, y).let { intArrayOf((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF) } }
    }

    private fun Canvas.fill(r: Int, g: Int, b: Int) = drawPaint(Paint().apply { color = org.jetbrains.skia.Color.makeRGB(r, g, b) })

    private fun Canvas.box(x: Float, y: Float, w: Float, h: Float, r: Int, g: Int, b: Int) =
        drawRect(org.jetbrains.skia.Rect.makeXYWH(x, y, w, h), Paint().apply { color = org.jetbrains.skia.Color.makeRGB(r, g, b) })

    /** Lines of text, as a blurred page shows them: 3 px dark stems 6 px apart in bands 10 px tall every 24 px. */
    private fun Canvas.textLines(r: Int, g: Int, b: Int) {
        for (top in 0 until 80 step 24) for (x in 0 until 80 step 6) box(x.toFloat(), top + 7f, 3f, 10f, r, g, b)
    }

    private val darkInk = floatArrayOf(1f, 1f, 0f)
    private val lightInk = floatArrayOf(1f, 1f, 1f)
    private val modal = LiquidEdge.MODAL_HOLD.let { floatArrayOf(it.localDp, it.localFree, it.localSlope, it.inkKeep) }

    private fun spread(row: List<IntArray>, from: Int, to: Int, channel: Int = 1): Int = row.subList(from, to).map { it[channel] }.let { it.max() - it.min() }

    @Test
    fun `B-GLASS-LIQ-004 under dark words lines of text behind a pane become one even field, the page's own ground`() {
        // A white page with lines of dark text: across the pane's middle (row through a line and between lines) the
        // shipped hold leaves no bar and no word: at most 8/255 from end to end, and within 15/255 of the white ground.
        for (y in listOf(12, 24, 36)) {
            val row = plainRow(darkInk, modal, y) { fill(255, 255, 255); textLines(40, 40, 40) }
            val mid = row.subList(28, 52)
            println("dark ink, text lines, row $y: ${mid.map { it[1] }}")
            assertTrue(spread(row, 28, 52) <= 8, "row $y: the middle is even: ${mid.map { it[1] }}")
            assertTrue(mid.all { it[1] >= 240 }, "row $y: lifted to the page's ground: ${mid.map { it[1] }}")
        }
        // The same lines under light words (a dark page, light text): pressed down to the dark ground.
        val row = plainRow(lightInk, modal, 36) { fill(18, 18, 22); textLines(230, 230, 230) }
        println("light ink, text lines: ${row.subList(28, 52).map { it[1] }}")
        assertTrue(spread(row, 28, 52) <= 8 && row.subList(28, 52).all { it[1] <= 33 }, "light ink: ${row.subList(28, 52).map { it[1] }}")
    }

    @Test
    fun `B-GLASS-LIQ-005 the ink-side hold keeps colour and never lifts a dark picture to fog`() {
        // A green field with text on it, under dark words: the ink-side hold lifts the text's grey and nothing else, so
        // every pixel keeps the chroma it has without that hold (within 4/255) and none is darker than it was.
        val noInk = modal.copyOf().also { it[3] = 1f }
        val with = plainRow(darkInk, modal, 12) { fill(0, 200, 60); textLines(20, 20, 20) }
        val without = plainRow(darkInk, noInk, 12) { fill(0, 200, 60); textLines(20, 20, 20) }
        for (x in 30 until 50) {
            val a = with[x]
            val b = without[x]
            assertTrue(abs((a.max() - a.min()) - (b.max() - b.min())) <= 4, "x $x keeps its chroma: ${a.toList()} vs ${b.toList()}")
            assertTrue(a[1] >= b[1], "x $x is lifted, never darkened: ${a.toList()} vs ${b.toList()}")
        }
        // A dark picture (most of the disc ink-like) with a few light dots, under dark words: left dark, not lifted.
        val dark = plainRow(darkInk, modal, 40) { fill(25, 25, 30); for (x in 0 until 80 step 20) box(x.toFloat(), 38f, 3f, 3f, 250, 250, 250) }
        assertTrue(dark.subList(30, 50).all { it[1] <= 70 }, "a dark picture stays dark: ${dark.subList(30, 50).map { it[1] }}")
        // Under light words, a white page (most of the disc ink-like) is not pressed toward the dark lines on it.
        val white = plainRow(lightInk, modal, 36) { fill(255, 255, 255); textLines(40, 40, 40) }
        assertTrue(white.subList(28, 52).all { it[1] >= 150 }, "a white page stays light: ${white.subList(28, 52).map { it[1] }}")
    }

    @Test
    fun `B-GLASS-LIQ-006 what lies past the recorded page is not read as black - no dark frame inside the rim`() {
        // A flat white page that ends at x 60 (the layer is transparent past it, as past a screen's edge or a layer's
        // padding) and past the bitmap's own edges: the pane stays white up to the page's end, within 3/255.
        val row = plainRow(darkInk, modal, 40, transparentPast = 60) { fill(255, 255, 255) }
        assertTrue(row.subList(4, 58).all { it[1] >= 252 }, "no frame: ${row.subList(4, 58).map { it[1] }}")
    }

    /**
     * A picture to look at (printed as PNGB64 lines, as GlassWordsMenuSheetTest's renders): a light page at a phone's
     * density (2.75 px per dp) with paragraphs of dark text, a green chip and a white card over a mint-to-lilac wash,
     * through the plain pane's 4 dp blur and the hold, round one's numbers on the left half, today's on the right.
     */
    @Test
    fun `renders - lines of text behind a light pane, round one's numbers and today's`() {
        val d = 2.75f
        val w = 1000
        val h = 640
        fun render(local: FloatArray, darkPage: Boolean): Bitmap {
            val program = assertNotNull(DesktopLiveShader.compile(LiquidHoldShader.SOURCE))
            LiquidHoldUniforms(if (darkPage) knee else kneeDark, LiquidEdge.HOLD_CHROMA, 0f, 0f, w / 2f, h / 2f, 26f * d, 26f * d, 1f, 1f, 0f, local).set(program)
            val filter = assertNotNull((program as DesktopLiveShader).filter(4f * d))
            val bitmap = Bitmap()
            bitmap.allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.PREMUL))
            val canvas = Canvas(bitmap)
            canvas.saveLayer(null, Paint().apply { imageFilter = filter })
            // A wash from mint to lilac (dark: deep green to deep violet), column by column.
            val from = if (darkPage) intArrayOf(14, 42, 34) else intArrayOf(212, 245, 228)
            val to = if (darkPage) intArrayOf(30, 24, 56) else intArrayOf(230, 221, 247)
            for (x in 0 until w step 4) {
                val f = x / w.toFloat()
                val c = IntArray(3) { (from[it] + (to[it] - from[it]) * f).toInt() }
                canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), 0f, 4f, h.toFloat()), Paint().apply { color = org.jetbrains.skia.Color.makeRGB(c[0], c[1], c[2]) })
            }
            val ink = if (darkPage) org.jetbrains.skia.Color.makeARGB(235, 236, 236, 240) else org.jetbrains.skia.Color.makeARGB(235, 24, 24, 28)
            var y = 30f
            var line = 0
            while (y < h - 20f) {
                if (line % 6 != 5) {
                    var x = 40f
                    var word = 0
                    while (x < w - 40f - (line % 3) * 90f) {
                        val len = (3 + (word * 7 + line * 3) % 6) * 5.5f * d / 2f
                        var sx = x
                        while (sx < x + len) {
                            canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(sx, y + 2f * d, .9f * d, 7f * d), Paint().apply { color = ink })
                            sx += 2.2f * d
                        }
                        x += len + 4f * d
                        word++
                    }
                }
                y += 24f * d
                line++
            }
            canvas.drawRRect(org.jetbrains.skia.RRect.makeXYWH(620f, 300f, 260f, 120f, 30f), Paint().apply { color = if (darkPage) 0xFF2A2A30.toInt() else 0xFFFFFFFF.toInt() })
            canvas.drawRRect(org.jetbrains.skia.RRect.makeXYWH(120f, 420f, 220f, 90f, 45f), Paint().apply { color = 0xFF1DB954.toInt() })
            canvas.restore()
            return bitmap
        }
        for (dark in listOf(false, true)) {
            val before = render(floatArrayOf(16f * d, .03f, .15f, 1f), dark)
            val after = render(LiquidEdge.MODAL_HOLD.let { floatArrayOf(it.localDp * d, it.localFree, it.localSlope, it.inkKeep) }, dark)
            val out = java.awt.image.BufferedImage(w, h * 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
            for (yy in 0 until h) for (xx in 0 until w) {
                out.setRGB(xx, yy, before.getColor(xx, yy))
                out.setRGB(xx, yy + h, after.getColor(xx, yy))
            }
            val bytes = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(out, "png", it) }.toByteArray()
            val name = "render-hold2-${if (dark) "dark" else "light"}.png"
            java.util.Base64.getEncoder().encodeToString(bytes).chunked(1500).forEachIndexed { i, part -> println("PNGB64 $name $i $part") }
        }
    }

    @Test
    fun `knee off holds nothing - a pure green passes whole`() {
        val got = plainPixel(intArrayOf(0, 255, 0), KNEE_OFF, LiquidEdge.HOLD_CHROMA, centre)
        assertEquals(listOf(0, 255, 0), got.toList())
        val join = joinPixel(intArrayOf(0, 255, 0), KNEE_OFF, LiquidEdge.HOLD_CHROMA, centre)
        assertEquals(listOf(0, 255, 0), join.toList())
    }
}
