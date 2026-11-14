@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Device 2026-11-14 (Glass, Back from a conversation just after its "…" menu closed): a StackOverflowError on the main
 * thread, `CanvasDrawScope.getDensity` and `LayoutNodeDrawScope.getDensity` calling each other, entered from the joined
 * menu's side of the glass (`JoinedPaneNode` → `kitRecord` → `kitModalView` reading `density`). The framework's
 * `DrawScope.record` shorthand gives the recording the node's shared draw scope as its density and then runs the block
 * on that same scope with the recording's density, so the scope's density was itself. A desktop node draws through the
 * same `LayoutNodeDrawScope`, so the overflow is reproducible here: a density read directly inside a kit recording made
 * in a node's draw, alone, nested in another recording, or through the layer's own helper given the scope as density.
 */
class KitLayerRecordDensityTest {
    /** Runs [block] in one node's draw with two layers; returns what it threw, if anything, and what it measured. */
    private fun drawOnce(block: DrawScope.(GraphicsLayer, GraphicsLayer, MutableList<Float>) -> Unit): Pair<Throwable?, List<Float>> {
        var thrown: Throwable? = null
        val seen = ArrayList<Float>()
        runComposeUiTest {
            setContent {
                val a = rememberGraphicsLayer()
                val b = rememberGraphicsLayer()
                Canvas(Modifier.size(20.dp)) {
                    seen.clear()
                    seen += density
                    try {
                        block(this, a, b, seen)
                    } catch (e: Throwable) {
                        // A StackOverflowError is an Error: caught here so the test reports it instead of dying with it.
                        thrown = e
                    }
                }
            }
            waitForIdle()
        }
        return thrown to seen
    }

    @Test
    fun `KIT-RECORD-DENSITY-01 a density read inside a kit recording made in a node's draw is the node's density`() {
        val (thrown, seen) = drawOnce { a, _, out ->
            kitRecord(a) { out += 1.dp.toPx() }
            kitDrawLayer(a)
        }
        assertNull(thrown, "a toPx() inside kitRecord: ${thrown?.let { it::class.simpleName }}")
        assertEquals(2, seen.size, "the block ran")
        assertEquals(seen[0], seen[1], "1 dp inside the recording is the node's density")
    }

    @Test
    fun `KIT-RECORD-DENSITY-02 a recording inside a recording inside a node's draw reads its density without recursing`() {
        val (thrown, seen) = drawOnce { a, b, out ->
            kitRecord(a) {
                kitRecord(b) { out += 2.dp.toPx() }
                kitDrawLayer(b)
                // After the inner recording, the outer one's density is still a density, not this scope.
                out += 3.dp.toPx()
            }
            kitDrawLayer(a)
        }
        assertNull(thrown, "nested kitRecord: ${thrown?.let { it::class.simpleName }}")
        assertEquals(listOf(seen[0], seen[0] * 2f, seen[0] * 3f), seen)
    }

    @Test
    fun `KIT-RECORD-DENSITY-03 a layer recorded with this scope as its density inside another recording reads a plain density`() {
        val (thrown, seen) = drawOnce { a, b, out ->
            kitRecord(b) {
                // KitWordsFade and the look picker pass the scope itself as the density.
                a.kitRecord(this, layoutDirection, IntSize(4, 4)) { out += 1.dp.toPx() }
                kitDrawLayer(a)
            }
            kitDrawLayer(b)
        }
        assertNull(thrown, "GraphicsLayer.kitRecord(scope) inside kitRecord: ${thrown?.let { it::class.simpleName }}")
        assertEquals(listOf(seen[0], seen[0]), seen)
    }

    @Test
    fun `KIT-RECORD-DENSITY-04 drawContent inside a kit recording still draws the node's content`() {
        var red = 0
        runComposeUiTest {
            setContent {
                val layer = rememberGraphicsLayer()
                Box(
                    Modifier.size(20.dp)
                        .drawWithContent {
                            kitRecord(layer) { this@drawWithContent.drawContent() }
                            kitDrawLayer(layer)
                        }
                        .background(Color.Red)
                )
            }
            waitForIdle()
            val image = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
            for (x in 0 until image.width) for (y in 0 until image.height) {
                val c = image[x, y]
                if (c.red > .9f && c.green < .1f && c.blue < .1f) red++
            }
        }
        assertTrue(red > 100, "the recorded content shows: $red red pixels")
    }
}
