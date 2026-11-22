@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * B-KIT-MODAL-206: where a sheet is drawn in the first frame of the screen that holds it. A rotation rebuilds the page
 * around the sheet it had open (the open state is saved with the screen): the sheet must register inside the
 * composition that rebuilds the page, the page under it must be cleared from that same frame, and the sheet must not
 * rise again. One frame later and the page shows through where the sheet sits (a conversation's message box hint
 * under it). A sheet that opens on a settled page still rises into place from below.
 */
class KitSheetFirstFrameTest {
    /** The top of the sheet's box in the window; not a number while no sheet is drawn. */
    private fun ComposeUiTest.sheetTop(): Float =
        onAllNodesWithTag("kit-sheet").fetchSemanticsNodes().firstOrNull()?.let { it.boundsInRoot.top } ?: Float.NaN

    @Composable
    private fun ThemedWindow(content: @Composable () -> Unit) {
        KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en", stillFrame = true) {
            InWindow(412, 915) { content() }
        }
    }

    /** A page with one sheet over it. */
    @Composable
    private fun SheetPage(sheetOpen: Boolean) {
        KitSheetHost(Modifier.fillMaxSize()) {
            KitText("the page under the sheet")
            KitSheet(
                visible = sheetOpen,
                onDismissRequest = {},
                title = "Commands",
            ) {
                KitText("the sheet's body")
            }
        }
    }

    @Test
    fun `B-KIT-MODAL-206 a sheet that comes back with a rebuilt screen is drawn in that screen's first frame`() =
        runDesktopComposeUiTest(412, 915) {
            var placed by mutableStateOf(true)
            var sheetOpen by mutableStateOf(false)
            setContent {
                ThemedWindow {
                    if (placed) SheetPage(sheetOpen)
                }
            }
            waitForIdle()
            // The sheet is open, as it was when the screen was turned.
            runOnUiThread { sheetOpen = true }
            waitForIdle()
            // A rebuild drops the whole screen and composes it again with the sheet still open.
            runOnIdle { placed = false }
            waitForIdle()
            // The clock stops, the screen comes back with its sheet open, and exactly one frame is drawn.
            mainClock.autoAdvance = false
            runOnUiThread { placed = true }
            mainClock.advanceTimeByFrame()
            onNodeWithTag("kit-sheet")
                .assertExists("the sheet that came back with the rebuilt screen is drawn in its first frame")
            val firstTop = sheetTop()
            assertTrue(
                onAllNodesWithText("the page under the sheet").fetchSemanticsNodes().isEmpty(),
                "the page under the sheet is cleared from the rebuilt screen's first frame",
            )
            mainClock.autoAdvance = true
            waitForIdle()
            onNodeWithTag("kit-sheet").assertExists("the sheet that came back is still shown once settled")
            assertTrue(
                abs(sheetTop() - firstTop) <= 1f,
                "the sheet that came back does not rise again: its top is at $firstTop in the first frame and at " +
                    "${sheetTop()} once settled",
            )
        }

    @Test
    fun `B-KIT-MODAL-206 a sheet that opens on a settled page still rises into place`() =
        runDesktopComposeUiTest(412, 915) {
            var sheetOpen by mutableStateOf(false)
            setContent {
                ThemedWindow { SheetPage(sheetOpen) }
            }
            waitForIdle()
            // The clock stops and the sheet is asked to open: frame by frame until it has come into the window.
            mainClock.autoAdvance = false
            runOnUiThread { sheetOpen = true }
            var risingTop = Float.NaN
            var frames = 0
            while (risingTop.isNaN() && frames < 60) {
                mainClock.advanceTimeByFrame()
                risingTop = sheetTop()
                frames++
            }
            assertTrue(!risingTop.isNaN(), "the sheet that opens on a settled page is drawn")
            mainClock.autoAdvance = true
            waitForIdle()
            val restingTop = sheetTop()
            assertTrue(!restingTop.isNaN(), "the sheet that opened is still shown once settled")
            assertTrue(
                risingTop > restingTop + 24f,
                "a sheet that opens on a settled page rises into place: its top is at $risingTop as it comes in " +
                    "and rests at $restingTop",
            )
        }
}
