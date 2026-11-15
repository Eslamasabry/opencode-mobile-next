@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.diff

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * glass-truth 2026-11-16, item 4: a removed line was a quiet "−" in secondary ink on a 10 % neutral wash. Never red, it
 * now carries a neutral mark at its start edge (secondary ink, full strength) in the diff view and the preview card, in
 * light and dark; added and unchanged lines carry none.
 */
class KitDiffRemovedMarkTest {
    private fun near(a: Color, b: Color) = abs(a.red - b.red) < .03f && abs(a.green - b.green) < .03f && abs(a.blue - b.blue) < .03f

    @Test
    fun `GLASS-TRUTH-4 the preview card marks a removed line at its start edge, and only it`() {
        for (dark in listOf(false, true)) runDesktopComposeUiTest(420, 400) {
            var mark = Color.Unspecified
            setContent {
                ChatTestTheme(dark = dark) {
                    mark = LocalKitColors.current.text2
                    KitDiffPreviewCard(KitDiffPreview.fromPatch("@@ -1,3 +1,3 @@\n a\n-b\n+B\n c"), "Edit", {})
                }
            }
            waitForIdle()
            val rows = onAllNodesWithTag("kit-diff-preview-row", useUnmergedTree = true).fetchSemanticsNodes().size
            assertEquals(4, rows)
            val marked =
                (0 until rows).map { i ->
                    val img = onAllNodesWithTag("kit-diff-preview-row", useUnmergedTree = true)[i].captureToImage().toPixelMap()
                    near(img[1, img.height / 2], mark)
                }
            // a (context), -b (removed), +B (added), c (context)
            assertEquals(listOf(false, true, false, false), marked, "dark=$dark")
        }
    }

    @Test
    fun `GLASS-TRUTH-4 the diff view marks removed lines at their start edge`() {
        for (dark in listOf(false, true)) runDesktopComposeUiTest(420, 400) {
            var mark = Color.Unspecified
            setContent {
                ChatTestTheme(dark = dark) {
                    mark = LocalKitColors.current.text2
                    Box(Modifier.width(400.dp).testTag("diff")) {
                        KitDiffView(DiffModel.fromBeforeAfter("a.kt", "one\ntwo\nthree", "one\nTWO\nthree"), hunkLabel = { _, _, _, _ -> "hunk" }, gapLabel = { "$it" })
                    }
                }
            }
            waitForIdle()
            val img = onNodeWithTag("diff").captureToImage().toPixelMap()
            // Along the start of a removed line the mark runs down unbroken for the line's height (no glyph is that tall).
            val best =
                (0 until img.width / 3).maxOf { x ->
                    var run = 0
                    var longest = 0
                    for (y in 0 until img.height) {
                        run = if (near(img[x, y], mark)) run + 1 else 0
                        longest = maxOf(longest, run)
                    }
                    longest
                }
            assertTrue(best >= 14, "a removed line's start mark (dark=$dark): longest run $best px")
        }
    }
}
