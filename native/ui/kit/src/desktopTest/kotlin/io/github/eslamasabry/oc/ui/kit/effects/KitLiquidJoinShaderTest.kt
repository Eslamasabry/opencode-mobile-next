package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.ui.graphics.Color
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
    private fun alphas(p: LiveShaderProgram, u: JoinUniforms, mode: Float, w: Int, h: Int, light: FloatArray? = null): IntArray {
        if (light == null) u.set(p, mode, res = 1f) else u.set(p, mode, res = 1f, light = light)
        val shader = (p as DesktopLiveShader).skiaShader()
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

    /**
     * Owner 2026-11-11, on the phone: a press on the bar's control lit the bar in its own capsule, which drew the bar's
     * old bottom edge and end cap inside the joined glass while the menu dropped. The finger's light is the body's: the
     * two sides together light every point of the body near the finger (nothing stops at the bar's own edge), nothing
     * outside the body, and the brighter outline lies on the body's outline only.
     */
    @Test
    fun `liquid join shader - the finger's light fills the body's shape, not the touched pane's own`() {
        val brush = program(effect = false)
        val w = 360
        val h = 420
        val field = LiquidJoin.fieldBox(menu, bar)
        val reach = LiquidJoin.REACH_MENU_DP
        // A finger on the control at the bar's end, right above the menu.
        val glow = floatArrayOf(320f, 52f, 72f, 1f)
        val barSide = alphas(brush, uniforms(bar, field, bar, menu, reach), mode = 4f, w = w, h = h, light = glow)
        val menuSide = alphas(brush, uniforms(field, bar, menu, bar, reach), mode = 4f, w = w, h = h, light = glow)
        val line = floatArrayOf(0f, 0f, 1f, 1f)
        val barLine = alphas(brush, uniforms(bar, field, bar, menu, reach), mode = 5f, w = w, h = h, light = line)
        val menuLine = alphas(brush, uniforms(field, bar, menu, bar, reach), mode = 5f, w = w, h = h, light = line)
        var below = 0
        var onOldEdge = 0
        for (y in 0 until h) for (x in 0 until w) {
            val s = LiquidJoin.sample(x + .5f, y + .5f, bar, field, reach, LiquidJoin.BAND_DP, bar, menu)
            val fall = (1f - kotlin.math.hypot(x + .5f - 320f, y + .5f - 52f) / 72f).coerceIn(0f, 1f)
            val i = y * w + x
            assertEquals(s.cover * fall * 255f, (barSide[i] + menuSide[i]).toFloat(), 4f, "the light at ($x, $y), dist ${s.dist}")
            // Inside the body just under the bar's bottom edge (y 80), where the bar's own shape ends.
            if (y in 82..100 && s.dist < -2f && fall > .2f) below++
            val lit = barLine[i] + menuLine[i]
            if (lit > 2) assertTrue(s.dist > -2.5f && s.dist < 1f, "the brighter outline at ($x, $y) is on the body's outline, dist ${s.dist}")
            // The bar's own bottom edge where it runs inside the body (over the menu's column).
            if (y in 79..80 && x in 260..340 && lit > 2) onOldEdge++
        }
        assertTrue(below > 200, "the light carries on below the bar's own edge into the menu ($below points)")
        assertEquals(0, onOldEdge, "no line along the bar's old bottom edge inside the body")
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

    /** The lens pass over a flat [grey] (0-255) page, deep inside a pane (no bend), read back at its centre. */
    private fun throughLens(grey: Int, knee: FloatArray): Int {
        val lens = program(effect = true)
        val big = JoinBox(20f, 20f, 400f, 400f, 24f)
        val far = JoinBox(5000f, 5000f, 10f, 10f, 10f)
        val u = uniforms(big, far, big, far, 0f)
        JoinUniforms(u.geometry, 0f, u.band, 4f, 4f, 0f, 0f, 1f, 1f, 0f, knee, u.tintMine, u.tintTheirs, 1f).set(lens, 0f, 1f)
        val filter = (lens as DesktopLiveShader).filter(0f)!!
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(40, 40, ColorAlphaType.PREMUL))
        val canvas = Canvas(bitmap)
        canvas.saveLayer(null, Paint().apply { imageFilter = filter })
        canvas.drawPaint(Paint().apply { color = org.jetbrains.skia.Color.makeRGB(grey, grey, grey) })
        canvas.restore()
        return (bitmap.getColor(20, 20) shr 8) and 0xFF
    }

    @Test
    fun `liquid join material - the lens pass holds back bright content under light words`() {
        val off = throughLens(230, KNEE_OFF)
        assertEquals(230f, off.toFloat(), 2f, "no knee: the page as it is")
        val held = throughLens(230, floatArrayOf(LiquidJoin.KNEE, LiquidJoin.KNEE_SLOPE, 1f))
        assertEquals(LiquidJoin.held(230 / 255f, lightInk = true) * 255f, held.toFloat(), 3f, "bright content held back")
        val dark = throughLens(40, floatArrayOf(LiquidJoin.KNEE, LiquidJoin.KNEE_SLOPE, 1f))
        assertEquals(40f, dark.toFloat(), 2f, "dark content under light words unchanged")
        val lifted = throughLens(20, floatArrayOf(LiquidJoin.KNEE, LiquidJoin.KNEE_SLOPE, 0f))
        assertEquals(LiquidJoin.held(20 / 255f, lightInk = false) * 255f, lifted.toFloat(), 3f, "dark content lifted under dark words")
    }

    private data class Quad(val a: JoinBox, val b: JoinBox, val sa: JoinBox, val sb: JoinBox)
}
