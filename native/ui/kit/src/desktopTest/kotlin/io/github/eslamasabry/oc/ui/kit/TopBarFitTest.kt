package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * B-KIT-029 outside the phone: a text action's words are never cut. The actions stay beside the title while their whole
 * labels fit in the room the title keeps, and the rest wait in the overflow. A Back bar keeps its earlier 16 dp step
 * between the title and the actions wherever everything fits, and gives it back only where the title needs the room.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarFitTest {
    private val labels =
        listOf("Copy patch", "Copy updated file", "Show surrounding lines", "Add UploadViewModel.kt to the prompt")

    @Test
    fun `B-KIT-029 at 840 dp whole labels take the room beside the whole title and the rest wait in the overflow`() =
        runComposeUiTest {
            setContent { Themed(840) { fileBar() } }
            assertTitleKeepsItsLine()
            // Three labels fit whole beside the whole title. The fourth is too wide, so it waits in the overflow.
            labels.take(3).forEach { label ->
                onNodeWithText(label).assertExists()
                assertWhole(label, "in the bar")
            }
            val fourth = labels.last()
            onNodeWithText(fourth).assertDoesNotExist()
            onNodeWithTag("kit-top-bar-more").performClick()
            waitForIdle()
            assertWhole(fourth, "in the overflow", oneLine = false)
        }

    @Test
    fun `B-KIT-029 at 600 dp the labels that fit beside the whole title stay whole and the rest overflow`() =
        runComposeUiTest {
            setContent { Themed(600) { fileBar() } }
            assertTitleKeepsItsLine()
            // A medium window keeps three actions at most, and only two fit whole beside the whole title.
            labels.take(2).forEach { label ->
                onNodeWithText(label).assertExists()
                assertWhole(label, "in the bar")
            }
            val waiting = labels.drop(2)
            waiting.forEach { label -> onNodeWithText(label).assertDoesNotExist() }
            onNodeWithTag("kit-top-bar-more").performClick()
            waitForIdle()
            waiting.forEach { label -> assertWhole(label, "in the overflow", oneLine = false) }
        }

    @Test
    fun `B-KIT-029 a Back bar with one short action keeps the earlier step between the title block and the action`() =
        runComposeUiTest {
            // One long word: cut, the subtitle fills the title block's room, so its end shows where that room ends.
            val line = "ill".repeat(31)
            setContent {
                Themed(412) {
                    KitTopBar(
                        "Saved prompts",
                        subtitle = line,
                        exit = KitTopBarExit.Back,
                        exitLabel = "Back",
                        onExit = {},
                        actions = listOf(KitLineAction("Turn on", {})),
                    )
                }
            }
            val block = onNodeWithText(line, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val action = onNodeWithText("Turn on").fetchSemanticsNode().boundsInRoot
            val step = action.left - block.right
            assertTrue(step >= 15f, "the title block ends $step px before the action's box; the earlier step is 16")
        }

    @Test
    fun `B-KIT-029 a Back bar whose title is still cut without the step keeps the step shrunk at 360 dp`() =
        runComposeUiTest {
            setContent { Themed(360) { fileBar() } }
            // The file name's last word is cut even with the step gone, so the title keeps all the room there is.
            val title = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val action = onNodeWithText("Copy patch").fetchSemanticsNode().boundsInRoot
            assertTrue(title.width >= 90f, "the title keeps at least 90 dp: ${title.width}")
            val step = action.left - title.right
            assertTrue(step < 15f, "the step gives way where the title's words are still cut: $step")
        }

    @Test
    fun `B-KIT-029 a Back bar with no actions keeps its title's whole earlier room`() =
        runComposeUiTest {
            // One long word: cut, so the subtitle fills the title block's room and its end shows where that room ends.
            val line = "ill".repeat(60)
            setContent {
                Themed(412) {
                    KitTopBar(
                        "Saved prompts",
                        subtitle = line,
                        exit = KitTopBarExit.Back,
                        exitLabel = "Back",
                        onExit = {},
                    )
                }
            }
            val right = onNodeWithText(line, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right
            assertTrue(right >= 399f, "the subtitle ends at $right; the whole earlier room ends at 400")
        }

    @Test
    fun `B-KIT-029 at 840 dp a glyph action that does not fit as words stays in the bar as its icon`() =
        runComposeUiTest {
            // One unbreakable word fills every wide room, so "Find" cannot stay as its words.
            setContent {
                Themed(840) {
                    KitTopBar(
                        "n".repeat(120),
                        exit = KitTopBarExit.Back,
                        exitLabel = "Back",
                        onExit = {},
                        actions = listOf(KitLineAction("Find", {}, icon = KitIcons.search, testTag = "find")),
                        more = { _ -> KitIconButton(KitIcons.more, "More", {}) },
                    )
                }
            }
            onNodeWithTag("find").assertExists()
        }

    @Test
    fun `B-KIT-029 at 840 dp glyphs that fall back to their icons are counted, so the title keeps its whole line`() =
        runComposeUiTest {
            // Eight glyph actions whose words are too wide to all stay. Those that fall back to their 48 dp icons
            // still take the bar's room: uncounted, they push the title's words out of its whole line.
            val words =
                listOf(
                    "Collapse all steps",
                    "Expand all steps",
                    "Copy updated file",
                    "Show surrounding lines",
                    "Open in the editor",
                    "Add to the prompt",
                    "Rename the session",
                    "Move to another project",
                )
            setContent {
                Themed(840) {
                    KitTopBar(
                        "n".repeat(28),
                        exit = KitTopBarExit.Back,
                        exitLabel = "Back",
                        onExit = {},
                        actions =
                            words.mapIndexed { index, label ->
                                KitLineAction(label, {}, icon = KitIcons.search, testTag = "glyph-$index")
                            },
                    )
                }
            }
            assertTitleKeepsItsLine()
            // Every action is in the bar: its words, or its icon where the words did not fit.
            words.indices.forEach { onNodeWithTag("glyph-$it").assertExists() }
        }

    @Test
    fun `B-KIT-029 a bar in a narrow pane of a wide window splits by the pane's own width and keeps its title room`() =
        runComposeUiTest {
            // The window is wide, but this bar only has a pane's 400 dp: what does not fit there waits in the overflow.
            setContent {
                Themed(840) {
                    Box(Modifier.width(400.dp)) { fileBar() }
                }
            }
            assertTitleKeepsItsLine()
            onNodeWithTag("kit-top-bar-more").assertExists()
        }

    /** The review file page's bar: Back, a file name that ends in one unbreakable word, and its four text actions. */
    @Composable
    private fun fileBar() =
        KitTopBar(
            "src/UploadViewModel.kt",
            exit = KitTopBarExit.Back,
            exitLabel = "Back",
            onExit = {},
            actions = labels.map { KitLineAction(it, {}) },
        )

    /** Density 1, so 1 dp is 1 px. The window fits the test host's root: a taller one is centred and loses its top. */
    @Composable
    private fun Themed(widthDp: Int, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(widthDp, 760) { content() }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.assertTitleKeepsItsLine() {
        val results = mutableListOf<TextLayoutResult>()
        val title = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).fetchSemanticsNode()
        title.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        val layout = results.first()
        val whole = !layout.hasVisualOverflow && !cut(layout) && layout.lineCount == 1
        val seen = "${layout.lineCount} line(s), cut ${cut(layout)}, overflow ${layout.hasVisualOverflow}"
        assertTrue(whole, "the title keeps its whole single line at this width: $seen")
    }

    /**
     * [label]'s words are laid out whole, no line ending in an ellipsis: on one line in the bar, on as many lines as
     * the overflow's narrower menu needs for them.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.assertWhole(
        label: String,
        where: String,
        oneLine: Boolean = true,
    ) {
        val layout = textLayout(label)
        val whole = !layout.hasVisualOverflow && !cut(layout) && (!oneLine || layout.lineCount == 1)
        val seen = "${layout.lineCount} line(s), cut ${cut(layout)}, overflow ${layout.hasVisualOverflow}"
        assertTrue(whole, "$label is drawn in full $where: $seen")
    }

    /**
     * A line ends in an ellipsis, or runs past the layout's width: on desktop a word too long for the last line is
     * simply cut off there, with no ellipsis and no overflow flag.
     */
    private fun cut(layout: TextLayoutResult): Boolean =
        (0 until layout.lineCount).any {
            layout.isLineEllipsized(it) || layout.getLineRight(it) - layout.getLineLeft(it) > layout.size.width
        }

    private fun androidx.compose.ui.test.ComposeUiTest.textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }
}
