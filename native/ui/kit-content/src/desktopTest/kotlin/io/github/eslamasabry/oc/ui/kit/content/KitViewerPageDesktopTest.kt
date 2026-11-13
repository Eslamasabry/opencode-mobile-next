package io.github.eslamasabry.oc.ui.kit.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerAction
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerDocument
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerFrame
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The file viewer sits in the app's page frame: the top bar with Back and the file name, its tools in the bar (Find as
 * an icon, the rest in "More actions" in a fixed order), the text on the page's side margin and one filled button
 * pinned at the bottom.
 */
@OptIn(ExperimentalTestApi::class)
class KitViewerPageDesktopTest {
    private val scale = 2f

    private class Press : NavigationEventInput() {
        fun press() {
            dispatchOnBackStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f))
            dispatchOnBackCompleted()
        }
    }

    private val press = Press()
    private var leftPage = 0
    private val owner =
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher =
                NavigationEventDispatcher(onBackCompletedFallback = { leftPage++ }).also { it.addInput(press) }
        }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f), LocalNavigationEventDispatcherOwner provides owner) {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = ContentFakePlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.size(411.dp, 380.dp)) { content() }
            }
        }
    }

    private val text = (1..12).joinToString("\n") { "Line number $it of the file" }

    private fun near(expected: Float, actual: Float, tolerance: Float) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected within $tolerance of $actual")

    private fun SemanticsNodeInteraction.left(): Float = fetchSemanticsNode().boundsInRoot.left
    private fun SemanticsNodeInteraction.top(): Float = fetchSemanticsNode().boundsInRoot.top
    private fun SemanticsNodeInteraction.bottom(): Float = fetchSemanticsNode().boundsInRoot.bottom

    @Test
    fun `the viewer leaves by the bar's Back arrow, not by a Close word`() = runComposeUiTest {
        var closed = 0
        setContent {
            Themed {
                KitViewerFrame("b4.txt", Modifier.fillMaxSize(), path = "b4.txt", onClose = { closed++ }) { KitViewerDocument(text) }
            }
        }
        onNodeWithText("Close").assertDoesNotExist()
        onNodeWithContentDescription("Back").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun `the text starts on the page's side margin`() = runComposeUiTest {
        setContent {
            Themed {
                KitViewerFrame("b4.txt", Modifier.fillMaxSize(), path = "b4.txt", onClose = {}) { KitViewerDocument(text) }
            }
        }
        val gutter = 16 * scale
        near(gutter, onNodeWithText("Line number 1 of the file", substring = true).left(), 1f)
    }

    @Test
    fun `Ask about this file starts and ends on the page margin, like the text above it`() = runComposeUiTest {
        askFrame {}
        val node = ask().fetchSemanticsNode().boundsInRoot
        near(16 * scale, node.left, 1f)
        near((411 - 16) * scale, node.right, 1f)
    }

    @Test
    fun `Find is an action of the bar and opens the find field`() = runComposeUiTest {
        setContent {
            Themed {
                KitViewerFrame("b4.txt", Modifier.fillMaxSize(), path = "b4.txt", onClose = {}) { KitViewerDocument(text) }
            }
        }
        // Not a loose row above the text: it sits beside the bar's other controls, above the body.
        val find = onNode(hasContentDescription("Find in file") or hasText("Find in file"))
        val back = onNodeWithContentDescription("Back")
        near(back.top(), find.top(), 40f)
        assertTrue(find.top() < onNodeWithText("Line number 1 of the file", substring = true).top())
        find.performClick()
        onNodeWithContentDescription("Next match").assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.openFind() {
        setContent {
            Themed {
                KitViewerFrame("b4.txt", Modifier.fillMaxSize(), path = "b4.txt", onClose = {}) { KitViewerDocument(text) }
            }
        }
        onNode(hasContentDescription("Find in file") and hasClickAction()).performClick()
    }

    @Test
    fun `Find takes focus as it opens, so the keyboard comes up with it`() = runComposeUiTest {
        openFind()
        waitForIdle()
        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `Previous match, Next match and Close find are buttons by those names, not loose words`() = runComposeUiTest {
        openFind()
        onNode(hasSetTextAction()).performTextInput("Line")
        waitForIdle()
        listOf("Previous match", "Next match", "Close find").forEach {
            onNodeWithContentDescription(it).assertHasClickAction()
            onAllNodesWithText(it).assertCountEquals(0)
        }
    }

    @Test
    fun `the system Back closes Find first, then leaves the page`() = runComposeUiTest {
        openFind()
        waitForIdle()
        onNode(hasSetTextAction()).assertExists()
        runOnIdle { press.press() }
        waitForIdle()
        onNode(hasSetTextAction()).assertDoesNotExist()
        assertEquals(0, leftPage, "the first Back only closed Find")
        runOnIdle { press.press() }
        assertEquals(1, leftPage, "the second Back is the page's")
    }

    @Test
    fun `with text typed, one Back closes Find`() = runComposeUiTest {
        openFind()
        waitForIdle()
        onNode(hasSetTextAction()).performTextInput("Line")
        waitForIdle()
        runOnIdle { press.press() }
        waitForIdle()
        onNode(hasSetTextAction()).assertDoesNotExist()
        assertEquals(0, leftPage, "Back only closed Find")
    }

    @Test
    fun `a code file shows one Wrap lines row, with the host's saved choice as its state, and tapping it reports the change`() =
        runComposeUiTest {
            var saved = false
            val changes = mutableListOf<Boolean>()
            setContent {
                Themed {
                    KitViewerFrame("a.kt", Modifier.fillMaxSize(), path = "src/a.kt", onClose = {}) {
                        KitViewerDocument(text, code = true, wrap = saved, onWrapChanged = { changes += it; saved = it })
                    }
                }
            }
            onNodeWithContentDescription("More actions").performClick()
            onAllNodesWithText("Wrap lines").assertCountEquals(1)
            onAllNodesWithText("Wrap code lines").assertCountEquals(0)
            onNodeWithText("Wrap lines").performClick()
            assertEquals(listOf(true), changes)
        }

    @Test
    fun `the bar's menu lists Copy contents, Wrap lines, Open with, Save, Share in that order and each acts`() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            setContent {
                Themed {
                    KitViewerFrame(
                        "b4.txt", Modifier.fillMaxSize(), path = "b4.txt", onClose = {},
                        menu = listOf(
                            KitViewerMenuItem("Open with", { calls += "open" }),
                            KitViewerMenuItem("Save", { calls += "save" }),
                        ),
                        secondary = KitViewerAction("Share on Wi-Fi", { calls += "share" }),
                    ) { KitViewerDocument(text) }
                }
            }
            // Closed menu: none of them is a row on the page.
            listOf("Copy contents", "Wrap lines", "Open with", "Save", "Share on Wi-Fi").forEach {
                onNodeWithText(it).assertDoesNotExist()
            }
            onNodeWithContentDescription("More actions").performClick()
            val order = listOf("Copy contents", "Wrap lines", "Open with", "Save", "Share on Wi-Fi").map { onNodeWithText(it).top() }
            assertEquals(order.sorted(), order, "menu order")
            onNodeWithText("Open with").performClick()
            onNodeWithContentDescription("More actions").performClick()
            onNodeWithText("Save").performClick()
            onNodeWithContentDescription("More actions").performClick()
            onNodeWithText("Share on Wi-Fi").performClick()
            assertEquals(listOf("open", "save", "share"), calls)
        }

    private fun androidx.compose.ui.test.ComposeUiTest.askFrame(onAsk: () -> Unit) =
        setContent {
            Themed {
                KitViewerFrame(
                    "b4.txt", Modifier.fillMaxSize(), path = "b4.txt", onClose = {},
                    primary = KitViewerAction("Ask about this file", onAsk),
                ) { KitViewerDocument(text) }
            }
        }

    private fun androidx.compose.ui.test.ComposeUiTest.ask() =
        onNode(hasText("Ask about this file") and hasClickAction())

    @Test
    fun `Ask about this file sits inside the side margins at full width`() = runComposeUiTest {
        askFrame {}
        // Inside the margins on both sides; the kit's button cap may centre it, never push it past them.
        val node = ask().fetchSemanticsNode().boundsInRoot
        assertTrue(node.left >= 16 * scale - 1f && node.right <= (411 - 16) * scale + 1f, "button $node")
        assertTrue(node.width >= 250 * scale, "the button is full width up to its cap: ${node.width}")
    }

    @Test
    fun `Ask about this file sits below the text`() = runComposeUiTest {
        askFrame {}
        assertTrue(ask().top() > onNodeWithText("Line number 1 of the file", substring = true).bottom())
    }

    @Test
    fun `Ask about this file is pinned at the bottom of the window`() = runComposeUiTest {
        askFrame {}
        assertTrue(ask().bottom() > 380 * scale - 100 * scale)
    }

    @Test
    fun `Ask about this file calls its callback`() = runComposeUiTest {
        var asked = 0
        askFrame { asked++ }
        ask().performClick()
        assertEquals(1, asked)
    }

    @Test
    fun `a viewer with no actions shows no menu and no button`() = runComposeUiTest {
        setContent { Themed { KitViewerFrame("LICENSE", Modifier.fillMaxSize(), onClose = {}) { Box(Modifier.fillMaxSize()) } } }
        onNodeWithContentDescription("More actions").assertDoesNotExist()
        onNodeWithText("Ask about this file").assertDoesNotExist()
    }
}
