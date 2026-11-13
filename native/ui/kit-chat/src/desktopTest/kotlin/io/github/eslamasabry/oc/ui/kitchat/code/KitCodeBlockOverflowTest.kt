package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review 2026-11-13 #5: a listing wider than its block was cut inside a glyph with no fade. */
@OptIn(ExperimentalTestApi::class)
class KitCodeBlockOverflowTest {
    private fun scrollRanges(root: androidx.compose.ui.semantics.SemanticsNode): List<Float> {
        val out = mutableListOf<Float>()
        fun visit(n: androidx.compose.ui.semantics.SemanticsNode) {
            n.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.let { out += it.maxValue() }
            n.children.forEach(::visit)
        }
        visit(root)
        return out
    }

    private fun contrast(img: androidx.compose.ui.graphics.ImageBitmap, x0: Int, x1: Int): Float {
        val px = img.toPixelMap()
        var best = 0f
        for (x in x0 until x1) {
            val bg = px[x, 2].red
            for (y in 0 until img.height) best = maxOf(best, kotlin.math.abs(px[x, y].red - bg))
        }
        return best
    }

    @Test
    fun `a line wider than the block scrolls sideways and fades at its right edge`() {
        var ranges = emptyList<Float>()
        var edge = 1f
        var mid = 0f
        runDesktopComposeUiTest(400, 200) {
            setContent {
                ChatTestTheme {
                    Box(Modifier.width(300.dp).testTag("blk")) {
                        KitCodeBlock(source = "W".repeat(120), kind = KitCodeKind.Output)
                    }
                }
            }
            waitForIdle()
            ranges = scrollRanges(onRoot().fetchSemanticsNode())
            val img = onNodeWithTag("blk").captureToImage()
            edge = contrast(img, 281, 284)
            mid = contrast(img, 200, 203)
        }
        assertTrue(ranges.any { it > 0f }, "the block can scroll sideways ($ranges)")
        assertTrue(edge < mid * 0.5f, "the last glyph column thins out ($edge vs $mid)")
    }

    @Test
    fun `a block that fits can not scroll and has nothing to fade`() {
        var ranges = emptyList<Float>()
        runDesktopComposeUiTest(400, 200) {
            setContent { ChatTestTheme { Box(Modifier.width(300.dp)) { KitCodeBlock(source = "ls", kind = KitCodeKind.Output) } } }
            waitForIdle()
            ranges = scrollRanges(onRoot().fetchSemanticsNode())
        }
        assertFalse(ranges.any { it > 0f }, "nothing to scroll ($ranges)")
    }
}
