@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.ModalBand
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The one mechanism behind every modal pane's view of the page ([kitModalView], cut audits 1 and 2), as the joined menu
 * and the dock's list use it: a closed edge (the one a joined menu hangs from) shows none of the page, the pane's box may
 * be a part of the node that draws it (the dock's list inside the dock's area), and [base] eases the page from whole (the
 * dock at rest) to its band (the list out).
 */
class KitModalViewTest {
    /** Blocks of the page, in window dp: (left, top, right, bottom). */
    private fun page(blocks: List<FloatArray>): KitLiquidBackdrop {
        val pageDraw: androidx.compose.ui.graphics.drawscope.DrawScope.(LayoutCoordinates) -> Unit = { c ->
            val at = c.positionInRoot()
            for (b in blocks) drawRect(Color.White, Offset(b[0] * density - at.x, b[1] * density - at.y), Size((b[2] - b[0]) * density, (b[3] - b[1]) * density))
        }
        val under: androidx.compose.ui.graphics.drawscope.DrawScope.(LayoutCoordinates) -> Unit = { drawRect(PICTURE) }
        return KitLiquidBackdrop { c ->
            under(this, c)
            pageDraw(this, c)
        }.also { it.layers = KitBackdropLayers(under, pageDraw, pictured = { true }) }
    }

    /**
     * A node at [node] (window dp: left, top, width, height) drawing [blocks] through the modal view; [box] its pane inside
     * the node (node dp), null: the whole node.
     */
    private fun shot(blocks: List<FloatArray>, node: FloatArray, closed: Int = 0, base: Float = 0f, box: FloatArray? = null): PixelMap {
        lateinit var out: PixelMap
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    val view = remember {
                        kitModalView(
                            page(blocks),
                            { BAND },
                            closed = { closed },
                            base = { base },
                            box = box?.let { b -> { _: LayoutCoordinates -> Rect(b[0] * D, b[1] * D, (b[0] + b[2]) * D, (b[1] + b[3]) * D) } },
                        )
                    }
                    val at = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    Box(Modifier.size(W.dp, H.dp).background(Color.Black).testTag("scene")) {
                        Box(
                            Modifier.offset(node[0].dp, node[1].dp).size(node[2].dp, node[3].dp)
                                .onGloballyPositioned { at[0] = it }
                                .drawBehind { at[0]?.let { view.draw(this, it) } }
                        )
                    }
                }
            }
            waitForIdle()
            out = onNodeWithTag("scene").captureToImage().toPixelMap()
        }
        return out
    }

    /** The largest per-channel difference from the picture over a region (window dp), of 255. */
    private fun shown(img: PixelMap, l: Float, t: Float, r: Float, b: Float): Float {
        var m = 0f
        for (y in (t * D).roundToInt() until (b * D).roundToInt()) for (x in (l * D).roundToInt() until (r * D).roundToInt()) {
            val c = img[x, y]
            m = max(m, max(abs(c.red - PICTURE.red), max(abs(c.green - PICTURE.green), abs(c.blue - PICTURE.blue))) * 255f)
        }
        return m
    }

    @Test
    fun `B-GLASS-JOIN-002 a joined menu shows none of the page along the edge it hangs from, its band along its free edges`() {
        // A menu at (100, 100), 160 x 200 dp; blocks across its top edge, its bottom edge, and in its middle.
        val node = floatArrayOf(100f, 100f, 160f, 200f)
        val blocks = listOf(floatArrayOf(140f, 90f, 180f, 110f), floatArrayOf(140f, 290f, 180f, 310f), floatArrayOf(160f, 180f, 200f, 220f))
        val img = shot(blocks, node, closed = ModalSides.TOP)
        val top = shown(img, 140f, 101f, 180f, 109f)
        val bottom = shown(img, 140f, 291f, 180f, 299f)
        val middle = shown(img, 160f, 180f, 200f, 220f)
        println("joined menu: top edge $top, bottom edge $bottom, middle $middle")
        assertTrue(top <= 1f, "the edge it hangs from shows the page: $top")
        assertTrue(bottom >= 200f, "a free edge shows the page in its band: $bottom")
        assertTrue(middle <= 1f, "the middle shows the page: $middle")
    }

    @Test
    fun `B-GLASS-DOCK-001 the dock's list keeps the page only in its band, eased from whole as it comes out`() {
        // The dock's area spans the window's left part; its list is a box inside it, on the window's left edge.
        val node = floatArrayOf(0f, 60f, 200f, 360f)
        val box = floatArrayOf(0f, 40f, 120f, 280f)
        // In window dp the list spans x 0-120, y 100-380. Blocks: under its middle, across its right edge, across its left
        // (the window's) edge.
        val blocks = listOf(floatArrayOf(40f, 200f, 80f, 240f), floatArrayOf(110f, 300f, 130f, 330f), floatArrayOf(0f, 150f, 10f, 180f))
        val out = shot(blocks, node, base = 0f, box = box)
        val rest = shot(blocks, node, base = 1f, box = box)
        val half = shot(blocks, node, base = .5f, box = box)
        val middleOut = shown(out, 40f, 200f, 80f, 240f)
        val rightEdge = shown(out, 111f, 301f, 119f, 329f)
        val windowEdge = shown(out, 1f, 151f, 9f, 179f)
        val middleRest = shown(rest, 40f, 200f, 80f, 240f)
        val middleHalf = shown(half, 40f, 200f, 80f, 240f)
        println("dock list: out middle $middleOut, right edge $rightEdge, window edge $windowEdge; at rest $middleRest, half $middleHalf")
        assertTrue(middleOut <= 1f, "the page reads through the list's middle: $middleOut")
        assertTrue(rightEdge >= 200f, "the list's open edge shows the page in its band: $rightEdge")
        assertTrue(windowEdge <= 1f, "the list's edge on the window's edge shows the page: $windowEdge")
        assertTrue(middleRest >= 200f, "at rest the dock sees the page whole, as before: $middleRest")
        assertTrue(middleHalf in 60f..190f, "half out, the page is half gone (no step): $middleHalf")
    }

    private companion object {
        const val W = 360f
        const val H = 480f
        const val D = 2f
        val PICTURE = Color(0xFF3B2E5C)
        val BAND: ModalBand = LiquidEdge.MENU_BAND
    }
}
