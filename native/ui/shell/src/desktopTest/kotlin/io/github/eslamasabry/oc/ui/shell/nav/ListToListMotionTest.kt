package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-11-09, "Clicking on All conversations has a weird animation": the home's rows and All conversations' rows
 * carry the same grow keys (each opens its chat by growing), so the page push matched them as shared bounds and every
 * conversation visible on both pages flew from its home place to its All place, title and all, while the page slid in.
 * A row grows only into the page it opens; between two lists the rows ride their page.
 */
@OptIn(ExperimentalTestApi::class)
class ListToListMotionTest {
    private fun ComposeUiTest.top(text: String): Dp = onNodeWithText(text).getBoundsInRoot().top

    @Test
    fun `MOT-ALL-1 opening All conversations never flies the home's rows into the new list`() =
        runDesktopComposeUiTest(W, H) {
            val h = Harness()
            setContent { h.Content() }
            waitForIdle()
            onNodeWithText(ALL_LABEL).performClick()
            waitForIdle()
            val rest = top("Every 3")
            onNodeWithContentDescription("Back").performClick()
            waitForIdle()
            assertTrue(top("Row 3") < rest - 100.dp, "the test needs the row lower on the second list")
            mainClock.autoAdvance = false
            onNodeWithText(ALL_LABEL).performClick()
            var seen = 0
            repeat(FRAMES) { f ->
                mainClock.advanceTimeByFrame()
                // The new page is composed a frame after the push.
                if (onAllNodesWithText("Every 3").fetchSemanticsNodes().isEmpty()) return@repeat
                seen++
                // The page only slides sideways (fade-forwards); a row that moves up or down is flying.
                assertEquals(rest, top("Every 3"), "frame ${f + 1}: the row rides its page")
            }
            assertTrue(seen > FRAMES / 2, "the new list was on screen for the push ($seen frames)")
        }

    @Test
    fun `MOT-ALL-2 going back from All conversations never flies its rows into the home`() =
        runDesktopComposeUiTest(W, H) {
            val h = Harness()
            setContent { h.Content() }
            waitForIdle()
            val rest = top("Row 3")
            onNodeWithText(ALL_LABEL).performClick()
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithContentDescription("Back").performClick()
            var seen = 0
            repeat(FRAMES) { f ->
                mainClock.advanceTimeByFrame()
                // The home comes back into composition a frame after the pop starts.
                if (onAllNodesWithText("Row 3").fetchSemanticsNodes().isEmpty()) return@repeat
                seen++
                assertEquals(rest, top("Row 3"), "frame ${f + 1}: the home row stays in its place")
            }
            assertTrue(seen > FRAMES / 2, "the home row was on screen for the pop ($seen frames)")
        }

    @Test
    fun `MOT-ALL-4 the window never shows through while the pages slide`() =
        runDesktopComposeUiTest(W, H) {
            val h = Harness()
            setContent { h.Content() }
            waitForIdle()
            val ground = onRoot().captureToImage().toPixelMap()[2, H / 2]
            mainClock.autoAdvance = false
            onNodeWithText(ALL_LABEL).performClick()
            // Past the covered page's fade (112 ms) only the arriving page, still a quarter width short of the start
            // edge and fading in, and whatever is under both are left at the start edge.
            mainClock.advanceTimeBy(128)
            repeat(12) { f ->
                mainClock.advanceTimeBy(24)
                val px = onRoot().captureToImage().toPixelMap()[2, H / 2]
                assertTrue(
                    abs(px.luminance() - ground.luminance()) < 0.05f,
                    "frame ${f + 1}: the start edge shows the page ground, not the window ($px vs $ground)",
                )
            }
        }

    @Test
    fun `MOT-ALL-3 a row of the second list still grows into its chat`() =
        runDesktopComposeUiTest(W, H) {
            val h = Harness()
            setContent { h.Content() }
            waitForIdle()
            onNodeWithText(ALL_LABEL).performClick()
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithText("Every 3").performClick()
            repeat(2) { mainClock.advanceTimeByFrame() }
            mainClock.advanceTimeBy(60)
            val mid = top("transcript line")
            mainClock.autoAdvance = true
            waitForIdle()
            assertTrue(mid != top("transcript line"), "mid-open the chat is still growing out of the row")
        }

    private companion object {
        const val W = 412
        const val H = 860
        const val FRAMES = 20
    }
}
