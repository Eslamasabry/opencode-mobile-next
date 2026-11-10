package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import io.github.eslamasabry.oc.ui.design.effects.JoinBox
import io.github.eslamasabry.oc.ui.design.effects.LiquidJoin
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import kotlin.random.Random
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
 * Joined glass (docs/designs/liquid-merge-one-body.md section 4): the join shader, compiled by desktop's Skia from the
 * same text Android compiles as AGSL, draws the cover [LiquidJoin] computes, at 200 sample points of each rest layout.
 */
class KitLiquidJoinShaderTest {
    private val bar = JoinBox(180f, 52f, 168f, 28f, 28f)
    private val menu = JoinBox(248f, 52f + 28f - 4f + 115f, 100f, 115f, 24f)
    private val box = JoinBox(180f, 320f, 168f, 52f, 28f)
    private val pill = JoinBox(180f, 320f - 52f + 8f - 16f, 76f, 16f, 16f)

    private fun program(effect: Boolean): LiveShaderProgram =
        assertNotNull(DesktopLiveShader.compile(LiquidJoinShader.source(effect)), "the join shader compiles as SkSL (effect=$effect)")

    private fun uniforms(a: JoinBox, b: JoinBox, sideA: JoinBox, sideB: JoinBox, reach: Float): JoinUniforms =
        JoinUniforms(
            geometry = JoinGeometry(a, b, sideA, sideB),
            reach = reach,
            band = LiquidJoin.BAND_DP,
            rimMine = 22f,
            rimTheirs = 26f,
            refractionMine = 34f,
            refractionTheirs = 48f,
            saturation = 1.6f,
            contrast = 1f,
            brightness = 0f,
            knee = KNEE_OFF,
            tintMine = Color(0x33FFFFFF),
            tintTheirs = Color(0x22000000),
            rimPx = 1f,
        )

    /** The shader's mode-[mode] output alpha (0-255) over a [w] x [h] px area. */
    private fun alphas(p: LiveShaderProgram, u: JoinUniforms, mode: Float, w: Int, h: Int): IntArray {
        u.set(p, mode, res = 1f)
        val shader = (p.brush as ShaderBrush).createShader(Size(w.toFloat(), h.toFloat()))
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.PREMUL))
        Canvas(bitmap).drawPaint(Paint().apply { this.shader = shader })
        return IntArray(w * h) { i -> (bitmap.getColor(i % w, i / w) ushr 24) and 0xFF }
    }

    @Test
    fun `liquid join shader - its cover agrees with LiquidJoin at 200 points of each rest layout`() {
        val brush = program(effect = false)
        program(effect = true)
        val layouts =
            listOf(
                Triple(bar, LiquidJoin.fieldBox(menu, bar) to menu, LiquidJoin.REACH_MENU_DP),
                Triple(box, pill to pill, LiquidJoin.REACH_PILL_DP),
            )
        val rnd = Random(3)
        for ((body, drop, reach) in layouts) {
            val (field, side) = drop
            for ((a, b, sa, sb) in listOf(Quad(body, field, body, side), Quad(field, body, side, body))) {
                val w = 360
                val h = 420
                val got = alphas(brush, uniforms(a, b, sa, sb, reach), mode = 1f, w = w, h = h)
                var near = 0
                repeat(200) {
                    // Half the points near the joined outline, where the cover is neither 0 nor 1.
                    var x: Int
                    var y: Int
                    var s: io.github.eslamasabry.oc.ui.design.effects.JoinSample
                    do {
                        x = rnd.nextInt(w)
                        y = rnd.nextInt(h)
                        s = LiquidJoin.sample(x + .5f, y + .5f, a, b, reach, LiquidJoin.BAND_DP, sa, sb)
                    } while (it % 2 == 0 && kotlin.math.abs(s.dist) > 3f)
                    if (kotlin.math.abs(s.dist) <= 3f) near++
                    val want = (if (s.mine) s.cover else 0f) * 255f
                    assertEquals(want, got[y * w + x].toFloat(), 3f, "cover at ($x, $y), dist ${s.dist}")
                }
                assertTrue(near >= 100, "the points cover the outline")
            }
        }
    }

    @Test
    fun `liquid join shader - its rim light lies on the outline and its tint eases over the band`() {
        val brush = program(effect = false)
        val u = uniforms(box, pill, box, pill, LiquidJoin.REACH_PILL_DP)
        val rim = alphas(brush, u, mode = 2f, w = 360, h = 420)
        val tint = alphas(brush, u, mode = 3f, w = 360, h = 420)
        for (y in 0 until 420) for (x in 0 until 360) {
            val s = LiquidJoin.sample(x + .5f, y + .5f, box, pill, LiquidJoin.REACH_PILL_DP, LiquidJoin.BAND_DP)
            if (rim[y * 360 + x] > 2) assertTrue(s.mine && s.dist > -2.5f && s.dist < 1f, "rim light at ($x, $y) dist ${s.dist}")
            val want = (0x33 * (1f - s.t) + 0x22 * s.t)
            assertEquals(want, tint[y * 360 + x].toFloat(), 2f, "tint at ($x, $y)")
        }
    }

    @Test
    fun `liquid join shader - desktop builds the lens pass as a render effect`() {
        val lens = program(effect = true)
        uniforms(bar, menu, bar, menu, LiquidJoin.REACH_MENU_DP).set(lens, 0f, 1f)
        assertNotNull(DesktopKitPlatform().liquidJoinEffect(lens, 6f))
        assertNotNull(DesktopKitPlatform().liquidJoinEffect(lens, 0f))
    }

    private data class Quad(val a: JoinBox, val b: JoinBox, val sa: JoinBox, val sb: JoinBox)
}
