@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The rim's picture light after the owner's first phone look (2026-11-11: a thick, bright, hard-ended bar on the straight
 * part of a panel's start side while its corners stayed thin and dim). "One light": the light is the rim, as wide as the
 * rim, round the pane's whole outline, corners included, and smooth along it. Drawn alone (no pane, no rim of its own)
 * over a black page, so every lit pixel is the light. Also writes a pane over a bright plate when RIM_LIGHT_RENDER_OUT
 * names a folder (RIM_LIGHT_RENDER_NAME, default render-rim-light.png).
 */
class KitRimLightTest {
    /** The look's own numbers (Glass, the default look) over a black ground. */
    private val light = KitRimLight(groundLuma = 0f, gain = RIM_LIGHT_GAIN, max = RIM_LIGHT_MAX, bandDp = RIM_LIGHT_BAND_DP)

    /**
     * A page whose picture is [plate] (the whole window), black where nothing is drawn, and a pane of [PW]×[PH] dp at
     * [AT] dp: [face] draws the pane's own fill and rim under the light (null: the light alone).
     */
    @Composable
    private fun Scene(plate: Brush, showPlate: Boolean, face: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)?) {
        val holder = remember { arrayOfNulls<GraphicsLayer>(1) }
        val context = LocalGraphicsContext.current
        val rim = remember { BackdropRimLight() }
        val shape = RoundedCornerShape(RADIUS.dp)
        Box(
            Modifier.size(W.dp, H.dp).drawWithCache {
                val picture = obtainGraphicsLayer()
                holder[0] = picture
                onDrawBehind {
                    picture.record { drawRect(plate) }
                    drawRect(Color.Black)
                    if (showPlate) drawRect(plate)
                }
            }
        ) {
            Box(
                Modifier.offset(AT.dp, AT.dp).size(PW.dp, PH.dp).drawWithContent {
                    drawContent()
                    face?.invoke(this)
                    val picture = holder[0] ?: return@drawWithContent
                    val at = AT.dp.toPx()
                    rim.draw(this, context, picture, Offset(-at, -at), shape, light)
                }
            )
        }
    }

    private fun render(plate: Brush, showPlate: Boolean = false, face: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)? = null): PixelMap {
        lateinit var image: PixelMap
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent { CompositionLocalProvider(LocalDensity provides Density(D)) { Scene(plate, showPlate, face) } }
            waitForIdle()
            image = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
        }
        return image
    }

    private fun luma(image: PixelMap, xDp: Float, yDp: Float): Float {
        val c = image[(xDp * D).toInt().coerceIn(0, image.width - 1), (yDp * D).toInt().coerceIn(0, image.height - 1)]
        return (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f
    }

    /**
     * Points [depth] dp inside the pane's outline, in page dp, going down its start side from the middle, round the
     * bottom start corner's arc and along the bottom edge: (label, x, y).
     */
    private fun along(depth: Float): List<Triple<String, Float, Float>> {
        val r = RADIUS.toFloat()
        val left = AT + depth
        val pts = mutableListOf<Triple<String, Float, Float>>()
        // The straight start side, down to the tangent point.
        var y = AT + PH / 2f
        while (y < AT + PH - r) {
            pts += Triple("side", left, y)
            y += 2f
        }
        pts += Triple("tangent", left, AT + PH - r)
        // Round the arc, every 10 degrees.
        val cx = AT + r
        val cy = AT + PH - r
        for (deg in 10..80 step 10) {
            val a = Math.toRadians(deg.toDouble())
            pts += Triple("arc $deg", cx - (r - depth) * cos(a).toFloat(), cy + (r - depth) * sin(a).toFloat())
        }
        pts += Triple("tangent bottom", AT + r, AT + PH - depth)
        var x = AT + r + 2f
        while (x < AT + PW / 2f) {
            pts += Triple("bottom", x, AT + PH - depth)
            x += 2f
        }
        return pts
    }

    @Test
    fun `B-KIT-RIM-001 the picture's light is the rim, as wide as the rim, round the whole outline and smooth along it`() {
        val bright = Brush.linearGradient(listOf(Color.White, Color.White))
        val image = render(bright)
        // The pane's rim is 1 dp (the glass rim and the liquid rim alike): the light lies in it and nowhere past it.
        val rimDp = 1f
        // Lit through the rim's width, unlit just inside it, on every edge and at every corner.
        val half = rimDp / 2f
        val inside = rimDp + .5f
        val r = RADIUS.toFloat()
        val probes =
            listOf(
                "top" to (AT + PW / 2f to AT), "bottom" to (AT + PW / 2f to AT + PH), "start" to (AT to AT + PH / 2f),
                "end" to (AT + PW to AT + PH / 2f),
            )
        for ((edge, p) in probes) {
            val (x, y) = p
            val nx = if (edge == "start") 1f else if (edge == "end") -1f else 0f
            val ny = if (edge == "top") 1f else if (edge == "bottom") -1f else 0f
            val lit = luma(image, x + nx * half, y + ny * half)
            val past = luma(image, x + nx * inside, y + ny * inside)
            assertTrue(lit >= 120f, "$edge edge: lit in the rim, luma $lit")
            assertTrue(past <= 8f, "$edge edge: nothing past the rim's width, luma $past at $inside dp in")
        }
        for ((corner, c) in listOf("top start" to (AT + r to AT + r), "top end" to (AT + PW - r to AT + r), "bottom end" to (AT + PW - r to AT + PH - r), "bottom start" to (AT + r to AT + PH - r))) {
            val (cx, cy) = c
            val sx = if (corner.endsWith("start")) -1f else 1f
            val sy = if (corner.startsWith("top")) -1f else 1f
            val k = (1f / kotlin.math.sqrt(2f))
            val lit = luma(image, cx + sx * k * (r - half), cy + sy * k * (r - half))
            val past = luma(image, cx + sx * k * (r - inside), cy + sy * k * (r - inside))
            assertTrue(lit >= 120f, "$corner corner: the arc is lit as the edges are, luma $lit")
            assertTrue(past <= 8f, "$corner corner: no wider on the arc, luma $past at $inside dp in")
        }
        // Over an even picture the light is even along the edge: side, tangent point and arc alike.
        val even = along(half).map { (what, x, y) -> what to luma(image, x, y) }
        val lo = even.minOf { it.second }
        val hi = even.maxOf { it.second }
        assertTrue(hi - lo <= 24f, "even along the edge over an even picture: $lo to $hi; " + even.joinToString { "${it.first} ${it.second.roundToInt()}" })
        // Over a picture brightening from the bottom up, it rises and falls smoothly: no step between neighbours.
        val glow = Brush.verticalGradient(listOf(Color(0xFFB0FFC8), Color(0xFF103018)), startY = 0f, endY = H * D)
        val graded = render(glow)
        val ramp = along(half).map { (what, x, y) -> what to luma(graded, x, y) }
        val steps = ramp.zipWithNext { a, b -> abs(a.second - b.second) }
        assertTrue(ramp.all { it.second > 8f }, "lit all along the edge: " + ramp.joinToString { "${it.first} ${it.second.roundToInt()}" })
        assertTrue(steps.max() <= 16f, "no hard start or stop along the edge, steepest step ${steps.max()}: " + ramp.joinToString { "${it.first} ${it.second.roundToInt()}" })
        println("rim light: even $lo..$hi; graded " + ramp.joinToString { "${it.first} ${it.second.roundToInt()}" })
    }

    @Test
    fun `rim light render over a bright plate`() {
        val out = System.getenv("RIM_LIGHT_RENDER_OUT")?.let(::File) ?: return
        out.mkdirs()
        val name = System.getenv("RIM_LIGHT_RENDER_NAME") ?: "render-rim-light.png"
        // The page's glow, bright at the pane's start side as on the owner's phone today.
        val plate = Brush.radialGradient(listOf(Color(0xFF7DE3A0), Color(0xFF2C5A44), Color(0xFF14181C)), center = Offset(0f, H * D * .45f), radius = W * D * .9f)
        val shape = RoundedCornerShape(RADIUS.dp)
        lateinit var image: java.awt.image.BufferedImage
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    Scene(plate, showPlate = true) {
                        // A dark glass panel's own fill and its thin 1 dp rim, as the Glass panes draw them.
                        drawOutline(shape.createOutline(size, layoutDirection, this), Color(0xE6181C20))
                        val w = 1.dp.toPx()
                        inset(w / 2f) { drawOutline(shape.createOutline(size, layoutDirection, this), Color.White.copy(alpha = .16f), style = Stroke(w)) }
                    }
                }
            }
            waitForIdle()
            image = onAllNodes(isRoot())[0].captureToImage().toAwtImage()
        }
        ImageIO.write(image, "png", File(out, name))
        println("rim light render: $name")
    }

    private companion object {
        const val W = 240
        const val H = 200
        const val D = 4f
        const val AT = 24f
        const val PW = 192f
        const val PH = 152f
        const val RADIUS = 24
    }
}
