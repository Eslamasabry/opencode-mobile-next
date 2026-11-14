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
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.tool.KitCommandBox
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitBlockKind
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyBlock
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

    /** The sideways scrollers on screen that have more to show: their viewports in root pixels. */
    private fun overflowingViewports(root: androidx.compose.ui.semantics.SemanticsNode): List<Rect> {
        val out = mutableListOf<Rect>()
        fun visit(n: androidx.compose.ui.semantics.SemanticsNode) {
            val range = n.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
            if (range != null && range.maxValue() > 0f) out += n.boundsInRoot
            n.children.forEach(::visit)
        }
        visit(root)
        return out
    }

    /** Strongest ink in columns [x0, x1) of rows [y0, y1), against the strip under the last line ([bgY]). */
    private fun ink(img: androidx.compose.ui.graphics.ImageBitmap, x0: Int, x1: Int, y0: Int, y1: Int, bgY: Int): Float {
        val px = img.toPixelMap()
        var best = 0f
        for (x in x0 until x1) {
            val bg = px[x, bgY]
            for (y in y0 until y1)
                best = maxOf(best, kotlin.math.abs(px[x, y].red - bg.red), kotlin.math.abs(px[x, y].green - bg.green))
        }
        return best
    }

    /**
     * Review 2026-11-13 #5, measured on screen: [content] shows one listing wider than its box. Its viewport can
     * scroll sideways, and the glyphs in the viewport's last 3 px are at most half as strong as mid-line ones: the
     * listing thins out at the edge instead of stopping inside a letter.
     */
    private fun assertFadesAtEnd(what: String, content: @Composable () -> Unit) {
        var viewports = emptyList<Rect>()
        var edge = 1f
        var mid = 0f
        runDesktopComposeUiTest(400, 300) {
            setContent { ChatTestTheme { Box(Modifier.width(300.dp)) { content() } } }
            waitForIdle()
            viewports = overflowingViewports(onRoot().fetchSemanticsNode())
            if (viewports.isNotEmpty()) {
                val v = viewports.first()
                val img = onRoot().captureToImage()
                val top = v.top.toInt()
                val bottom = v.bottom.toInt()
                val right = v.right.toInt()
                val centre = v.center.x.toInt()
                edge = ink(img, right - 3, right, top, bottom - 4, bottom - 2)
                mid = ink(img, centre, centre + 3, top, bottom - 4, bottom - 2)
            }
        }
        assertTrue(viewports.isNotEmpty(), "$what: the listing scrolls sideways")
        assertTrue(mid > 0.2f, "$what: mid-line glyphs are drawn ($mid)")
        assertTrue(edge < mid * 0.5f, "$what: the last glyph column thins out at the edge ($edge vs $mid)")
    }

    // Dense glyphs so the edge columns always hold ink: before the fix they were as strong as mid-line ones.
    private val listing = "W".repeat(120)

    @Test
    fun `a fence still arriving fades at its right edge and scrolls sideways`() =
        assertFadesAtEnd("reply row") {
            KitReplyBlock(
                listing, KitBlockKind.Code, live = false, streamed = false, revealKey = "r", revealGroup = "g",
                registry = RevealRegistry(), motion = KitMotionLevel.Off,
            )
        }

    @Test
    fun `a chunk of a long fence fades at its right edge and scrolls sideways`() =
        assertFadesAtEnd("code chunk") { KitCodeChunk(listing, continued = false, continues = false) }

    @Test
    fun `command output wider than its box fades at the right edge and scrolls sideways`() =
        assertFadesAtEnd("command box") {
            KitCommandBox(command = "ls", output = listing, moreLabel = "Show all", onMore = {}, exitLabel = null)
        }

    /**
     * Audit 1, item 3: with Copy and Wrap in the block, the lines stopped about 160 px before the block's right edge and
     * the whole side down the block stayed empty. The lines now run to the inner edge (ending in the short fade); only
     * the first lines, beside the buttons, are cleared under them.
     */
    @Test
    fun `lines run to the inner edge under the buttons' corner, not to an empty band`() {
        var blockHeight = 0
        var beside = 1f
        var below = 0f
        var edge = 1f
        runDesktopComposeUiTest(400, 400) {
            setContent {
                ChatTestTheme {
                    Box(Modifier.width(300.dp).testTag("blk")) {
                        KitCodeBlock(
                            source = List(14) { "W".repeat(120) }.joinToString("\n"),
                            kind = KitCodeKind.Code,
                            onCopy = {},
                        )
                    }
                }
            }
            waitForIdle()
            val img = onNodeWithTag("blk").captureToImage()
            blockHeight = img.height
            // Columns 255-258 dp: past where the old layout stopped the lines (the block's width minus 77 dp).
            below = ink(img, 255, 258, blockHeight - 60, blockHeight - 30, blockHeight - 2)
            beside = ink(img, 255, 258, 12, 40, blockHeight - 2)
            edge = ink(img, 281, 284, blockHeight - 60, blockHeight - 30, blockHeight - 2)
        }
        assertTrue(below > 0.2f, "a line below the buttons is drawn out to the inner edge ($below)")
        assertTrue(beside < below * 0.2f, "the lines beside the buttons are cleared under them ($beside vs $below)")
        assertTrue(edge < below * 0.5f, "and the lines thin out in the short fade at the inner edge ($edge vs $below)")
    }
}
