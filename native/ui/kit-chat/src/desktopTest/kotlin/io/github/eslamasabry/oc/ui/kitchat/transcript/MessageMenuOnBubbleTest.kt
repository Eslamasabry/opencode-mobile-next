package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F4 (owner screenshot, 2026-11-20): a long-press on a prompt bubble opened the message menu from the FULL-WIDTH row,
 * at the finger's x and flush with the row: it covered the footer of the reply above, touched the held row, and the
 * highlight was a full-width band behind a bubble that sits at the end. Option C: a message menu opens on the
 * message's own face (the bubble), its end edge on the bubble's end edge, a step (8 dp) clear of it, above when it
 * fits and below near the top, never over it; the page dims and only the held bubble stays bright.
 */
@OptIn(ExperimentalTestApi::class)
class MessageMenuOnBubbleTest {
    private val prompt = "Reply with exactly the word: base-three"

    private val items =
        listOf("Copy message text", "Send again", "Fork from this prompt", "Undo from here").map { KitMenuItem(it, onSelect = {}) } +
            KitMenuItem("Delete message", onSelect = {}, destructive = true)

    /** A page with a prompt [above] dp from the top, held for its menu through [KitMessageActions], in a sheet host. */
    private fun ComposeUiTest.promptPage(above: Int, rtl: Boolean = false) {
        setContent {
            ChatTestTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        var open by remember { mutableStateOf(false) }
                        Box(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize()) {
                                Spacer(Modifier.height(above.dp))
                                KitMessageActions("Message actions", onOpen = { open = true }) {
                                    KitPromptBubble(prompt, Modifier.padding(horizontal = 16.dp))
                                }
                            }
                            // The chat's message menu: a KitMenu with an empty anchor, opened from the recorded press.
                            KitMenu(
                                expanded = open,
                                items = items,
                                onDismissRequest = { open = false },
                                modifier = Modifier.align(Alignment.BottomEnd),
                            ) {}
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.bubble(): Rect = onNodeWithTag(PROMPT_BUBBLE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow

    private fun ComposeUiTest.panel(): Rect = onNodeWithTag("kit-menu").fetchSemanticsNode().boundsInWindow

    private fun ComposeUiTest.holdPrompt() {
        onNodeWithText(prompt, useUnmergedTree = true).performTouchInput { longClick(center) }
        waitForIdle()
    }

    private fun Rect.meets(other: Rect): Boolean = left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun ComposeUiTest.step(): Float = with(density) { 8.dp.toPx() }

    @Test
    fun `B-KIT-MENU-001 a held prompt's menu opens above its bubble, a step clear of it, its end on the bubble's end`() =
        runDesktopComposeUiTest(400, 900) {
            promptPage(above = 560)
            val bubble = bubble()
            holdPrompt()
            val panel = panel()
            assertTrue(!panel.meets(bubble), "the menu never lies over the bubble (menu $panel, bubble $bubble)")
            assertTrue(panel.bottom <= bubble.top, "with room above, the menu opens above the bubble (menu $panel, bubble $bubble)")
            assertTrue(abs(bubble.top - panel.bottom - step()) <= 1f, "a step (8 dp) between the menu and the bubble, not ${bubble.top - panel.bottom} px")
            assertTrue(abs(panel.right - bubble.right) <= 1f, "the menu's end edge on the bubble's end edge (menu ${panel.right}, bubble ${bubble.right})")
        }

    @Test
    fun `B-KIT-MENU-002 a prompt near the top opens its menu below the bubble, a step clear of it`() =
        runDesktopComposeUiTest(400, 900) {
            promptPage(above = 24)
            val bubble = bubble()
            holdPrompt()
            val panel = panel()
            assertTrue(!panel.meets(bubble), "the menu never lies over the bubble (menu $panel, bubble $bubble)")
            assertTrue(panel.top >= bubble.bottom, "no room above: the menu opens below the bubble (menu $panel, bubble $bubble)")
            assertTrue(abs(panel.top - bubble.bottom - step()) <= 1f, "a step (8 dp) between the bubble and the menu, not ${panel.top - bubble.bottom} px")
        }

    @Test
    fun `B-KIT-MENU-003 a message menu dims the page and keeps the held bubble bright`() =
        runDesktopComposeUiTest(400, 900) {
            promptPage(above = 560)
            val bubble = bubble()
            val inBubble = bubble.left.toInt() + 6 to bubble.center.y.toInt()
            val onPage = 8 to (bubble.bottom + 120f).toInt()
            val before = onRoot().captureToImage().toPixelMap()
            holdPrompt()
            assertEquals(1, onAllNodesWithTag(MENU_DIM_TAG, useUnmergedTree = true).fetchSemanticsNodes().size, "a message menu dims the page")
            val after = onRoot().captureToImage().toPixelMap()
            assertEquals(
                before[inBubble.first, inBubble.second],
                after[inBubble.first, inBubble.second],
                "the held bubble stays bright: the dim has a hole in its shape",
            )
            assertTrue(
                after[onPage.first, onPage.second].luma() < before[onPage.first, onPage.second].luma() - 0.05f,
                "the page around it dims (${before[onPage.first, onPage.second]} -> ${after[onPage.first, onPage.second]})",
            )
        }

    @Test
    fun `B-KIT-MENU-005 an ordinary menu opened from a button draws no dim`() =
        runDesktopComposeUiTest(400, 900) {
            setContent {
                ChatTestTheme {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().padding(top = 300.dp)) {
                            OrdinaryMenu()
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Rename").performClick()
            waitForIdle()
            onNodeWithTag("kit-menu").fetchSemanticsNode()
            assertEquals(0, onAllNodesWithTag(MENU_DIM_TAG, useUnmergedTree = true).fetchSemanticsNodes().size, "an ordinary menu keeps the page as it is")
        }

    @Test
    fun `B-KIT-MENU-004 right to left, the menu's start edge sits on the bubble's start edge`() =
        runDesktopComposeUiTest(400, 900) {
            promptPage(above = 560, rtl = true)
            val bubble = bubble()
            holdPrompt()
            val panel = panel()
            assertTrue(!panel.meets(bubble), "the menu never lies over the bubble (menu $panel, bubble $bubble)")
            assertTrue(abs(panel.left - bubble.left) <= 1f, "right to left the bubble's end is its left edge, and the menu's too (menu ${panel.left}, bubble ${bubble.left})")
            assertTrue(abs(bubble.top - panel.bottom - step()) <= 1f, "a step (8 dp) between the menu and the bubble, not ${bubble.top - panel.bottom} px")
        }

    @Composable
    private fun OrdinaryMenu() {
        var open by remember { mutableStateOf(false) }
        KitMenu(expanded = open, items = listOf(KitMenuItem("Pin conversation", {})), onDismissRequest = { open = false }) {
            io.github.eslamasabry.oc.ui.kit.KitButton("Rename", onClick = { open = true })
        }
    }

    private fun Color.luma(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    private companion object {
        const val MENU_DIM_TAG = "kit-menu-dim"
    }
}
