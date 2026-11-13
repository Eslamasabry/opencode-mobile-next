@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Proof pictures for the glass rim's floor (owner 2026-11-13): each of [PaneRimScenes]' panes (the message box's glass,
 * a context menu, the bar and the menu out of it, a dialog panel, a chosen style card, a chosen segment), in Glass dark
 * and Glass light, on the plain ground and over Aurora, at the phone's 2.625 px/dp, 1080 px wide; and beside each, its
 * four corners cut out and enlarged four times into one 2x2 picture (top-start, top-end / bottom-start, bottom-end).
 * Not a test: it runs only when `PANE_RIM_RENDER_OUT` names a folder, and writes
 * `pane-rim-<pane>-glass-{dark,light}-{ground,aurora}.png`, `...-corners.png` and `...-measured.png` (the measured
 * box outlined) there, and `pane-rim-errors.txt` naming any scene that could not be drawn.
 */
class PaneRimRenders {
    @Test
    fun `pane rim renders`() {
        val out = System.getenv("PANE_RIM_RENDER_OUT")?.let(::File) ?: return
        out.mkdirs()
        val errors = StringBuilder()
        for (case in PaneRimScenes.cases) for (dark in listOf(true, false)) for (aurora in listOf(false, true)) {
            val name = "pane-rim-${case.name.replace(' ', '-')}-glass-${if (dark) "dark" else "light"}-${if (aurora) "aurora" else "ground"}"
            // One scene that cannot be drawn is named in errors.txt; the others are still written.
            try {
                runDesktopComposeUiTest(PaneRimScenes.WIDTH, PaneRimScenes.HEIGHT) {
                    PaneRimScenes.settle(this) { PaneRimScenes.Scene(aurora, dark) { case.content() } }
                    val image = onAllNodes(isRoot())[0].captureToImage().toAwtImage()
                    ImageIO.write(image, "png", File(out, "$name.png"))
                    val box =
                        case.pane { matcher ->
                            val node =
                                onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
                                    ?: onAllNodes(matcher).fetchSemanticsNodes().first()
                            PaneRimScenes.paneBox(node)
                        }
                    ImageIO.write(corners(image, box.left, box.top, box.right, box.bottom), "png", File(out, "$name-corners.png"))
                    ImageIO.write(measured(image, box.left, box.top, box.right, box.bottom), "png", File(out, "$name-measured.png"))
                }
            } catch (e: Throwable) {
                errors.append("$name: ${e::class.simpleName}: ${e.message?.take(400)}\n")
            }
        }
        File(out, "pane-rim-errors.txt").writeText(errors.toString())
    }

    /** The picture with the box the test measures outlined in magenta: which pane a scene samples. */
    private fun measured(image: BufferedImage, left: Float, top: Float, right: Float, bottom: Float): BufferedImage {
        val copy = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val g = copy.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.color = Color.MAGENTA
        g.drawRect(left.roundToInt(), top.roundToInt(), (right - left).roundToInt(), (bottom - top).roundToInt())
        g.dispose()
        return copy
    }

    /** The four corners of the pane at [left]..[bottom] (px), each a [CUT_DP] dp square enlarged [ZOOM] times, 2x2. */
    private fun corners(image: BufferedImage, left: Float, top: Float, right: Float, bottom: Float): BufferedImage {
        val cut = (CUT_DP * PaneRimScenes.DENSITY).roundToInt()
        val pad = (PAD_DP * PaneRimScenes.DENSITY).roundToInt()
        val tile = cut * ZOOM
        val gap = 4
        val sheet = BufferedImage(tile * 2 + gap, tile * 2 + gap, BufferedImage.TYPE_INT_ARGB)
        val g = sheet.createGraphics()
        g.color = Color.MAGENTA
        g.fillRect(0, 0, sheet.width, sheet.height)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        val xs = listOf(left.roundToInt() - pad, right.roundToInt() + pad - cut)
        val ys = listOf(top.roundToInt() - pad, bottom.roundToInt() + pad - cut)
        for ((row, y) in ys.withIndex()) for ((col, x) in xs.withIndex()) {
            val sx = x.coerceIn(0, image.width - cut)
            val sy = y.coerceIn(0, image.height - cut)
            val dx = col * (tile + gap)
            val dy = row * (tile + gap)
            g.drawImage(image, dx, dy, dx + tile, dy + tile, sx, sy, sx + cut, sy + cut, null)
        }
        g.dispose()
        return sheet
    }

    private companion object {
        /** The square cut at each corner (dp), reaching [PAD_DP] beyond the pane, and how much it is enlarged. */
        const val CUT_DP = 40f
        const val PAD_DP = 6f
        const val ZOOM = 4
    }
}
