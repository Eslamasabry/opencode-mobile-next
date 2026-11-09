package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Smoothness round 2 (docs/native/qa/smoothness-2026-11-09): opening a menu made a popup window and composed it, 11-13 %
 * late frames on every menu open in every style on a Galaxy S24. A menu on a page now opens in the window's own menu
 * layer (KitSheetHost): no window is made, and it still opens beside its anchor, closes on an outside press (which the
 * page under it never gets), and runs the item chosen.
 */
@OptIn(ExperimentalTestApi::class)
class KitMenuInWindowTest {
    @Test
    fun `B-KIT-022 a menu on a page opens in the page's window, never in a popup window`() =
        runDesktopComposeUiTest(400, 800) {
            var open by mutableStateOf(false)
            var chosen = 0
            var pageTaps = 0
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitScreen(topBar = { KitTopBar("Chat") }) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { pageTaps++ } }) { KitText("Behind") }
                            KitMenu(
                                expanded = open,
                                items = listOf(KitMenuItem("Copy", onSelect = { chosen++ }), KitMenuItem("Share", onSelect = {})),
                                onDismissRequest = { open = false },
                                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).testTag("anchor"),
                            ) { KitText("More") }
                        }
                    }
                }
            }
            open = true
            waitForIdle()
            onNodeWithTag("kit-menu").assertExists()
            onAllNodes(isPopup()).assertCountEquals(0)
            // Beside its anchor, never over it.
            val anchor = onNodeWithTag("anchor").fetchSemanticsNode().boundsInWindow
            val panel = onNodeWithTag("kit-menu").fetchSemanticsNode().boundsInWindow
            assertTrue(panel.top >= anchor.bottom - 1f, "the panel opens below its anchor (panel $panel, anchor $anchor)")

            // An outside press closes it; the page under it does not get the press.
            onRoot().performTouchInput { click(Offset(width / 2f, height - 10f)) }
            waitForIdle()
            assertFalse(open, "an outside press closes the menu")
            assertEquals(0, pageTaps)
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            onAllNodesWithTag("kit-menu").assertCountEquals(0)

            // A row runs its item.
            open = true
            waitForIdle()
            onAllNodes(isPopup()).assertCountEquals(0)
            onNodeWithText("Copy").performClick()
            waitForIdle()
            assertEquals(1, chosen)
            assertFalse(open, "choosing closes it")
        }

    @Test
    fun `B-KIT-022 a press on the menu's own padding does not close it`() =
        runDesktopComposeUiTest(400, 800) {
            var open by mutableStateOf(true)
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitScreen(topBar = { KitTopBar("Chat") }) {
                        Box(Modifier.fillMaxSize()) {
                            KitMenu(
                                expanded = open,
                                items = listOf(KitMenuItem("Copy", onSelect = {})),
                                onDismissRequest = { open = false },
                                modifier = Modifier.padding(16.dp),
                            ) { KitText("More") }
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("kit-menu").performTouchInput { click(Offset(2f, 2f)) }
            waitForIdle()
            assertTrue(open, "a press on the panel's edge keeps it open")
        }
}
