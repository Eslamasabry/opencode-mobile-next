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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = ContentFakePlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.size(411.dp, 800.dp)) { content() }
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
                KitViewerFrame("b4.txt", path = "b4.txt", onClose = { closed++ }) { KitViewerDocument(text) }
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
                KitViewerFrame("b4.txt", path = "b4.txt", onClose = {}) { KitViewerDocument(text) }
            }
        }
        val gutter = 16 * scale
        near(gutter, onNodeWithText("Line number 1 of the file", substring = true).left(), 1f)
    }

    @Test
    fun `Find is an action of the bar and opens the find field`() = runComposeUiTest {
        setContent {
            Themed {
                KitViewerFrame("b4.txt", path = "b4.txt", onClose = {}) { KitViewerDocument(text) }
            }
        }
        // Not a loose row above the text: it sits beside the bar's other controls, above the body.
        val find = onNode(hasContentDescription("Find in file") or hasText("Find in file"))
        val back = onNodeWithContentDescription("Back")
        near(back.top(), find.top(), 40f)
        assertTrue(find.top() < onNodeWithText("Line number 1 of the file", substring = true).top())
        find.performClick()
        onNodeWithText("Next match").assertExists()
    }

    @Test
    fun `the bar's menu lists Copy contents, Wrap lines, Open with, Save, Share in that order and each acts`() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            setContent {
                Themed {
                    KitViewerFrame(
                        "b4.txt", path = "b4.txt", onClose = {},
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

    @Test
    fun `Ask about this file is pinned at the bottom on the side margin`() = runComposeUiTest {
        var asked = 0
        setContent {
            Themed {
                KitViewerFrame(
                    "b4.txt", path = "b4.txt", onClose = {},
                    primary = KitViewerAction("Ask about this file", { asked++ }),
                ) { KitViewerDocument(text) }
            }
        }
        val ask = onNodeWithText("Ask about this file")
        near(16 * scale, ask.left(), 40f)
        // Under every line of the text, at the bottom of the window.
        assertTrue(ask.top() > onNodeWithText("Line number 12 of the file", substring = true).bottom() - 1f)
        assertTrue(ask.bottom() > 800 * scale - 120 * scale)
        ask.performClick()
        assertEquals(1, asked)
    }

    @Test
    fun `a viewer with no actions shows no menu and no button`() = runComposeUiTest {
        setContent { Themed { KitViewerFrame("LICENSE", onClose = {}) { Box(Modifier.fillMaxSize()) } } }
        onNodeWithContentDescription("More actions").assertDoesNotExist()
        onNodeWithText("Ask about this file").assertDoesNotExist()
    }
}
