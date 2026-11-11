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
    fun `knee off holds nothing - a pure green passes whole`() {
        val got = plainPixel(intArrayOf(0, 255, 0), KNEE_OFF, LiquidEdge.HOLD_CHROMA, centre)
        assertEquals(listOf(0, 255, 0), got.toList())
        val join = joinPixel(intArrayOf(0, 255, 0), KNEE_OFF, LiquidEdge.HOLD_CHROMA, centre)
        assertEquals(listOf(0, 255, 0), join.toList())
    }
}
