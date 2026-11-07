package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E20-S25: the agent's work in a reply copies Flutter's step timeline (lib/ui/kit/chat/kit_step_timeline.dart,
 * kit_tool_row.dart `_buildTimelineStep`): the group's line has the one chevron; each step is one 32 dp line whose
 * node sits on the rail and whose words start one column in; a tap opens the body at the text's own x.
 */
@OptIn(ExperimentalTestApi::class)
class KitStepTimelineTest {
    private fun step(
        id: String,
        kind: KitToolKind = KitToolKind.Read,
        title: String = "Read",
        subject: String? = "lib/ui/router.dart",
        status: KitToolStatus = KitToolStatus.Done,
        duration: String? = "1 second",
        added: Int? = null,
    ) = KitToolView(id, kind, title, subject, null, status, duration, added, null, "Done")

    @Composable
    private fun Work(expanded: Boolean, rtl: Boolean = false, content: @Composable () -> Unit) {
        ChatTestTheme(localeTag = if (rtl) "ar" else "en") {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KitWorkLine(
                    "Read 1 file · ran 1 command",
                    null,
                    KitWorkState.Done,
                    expanded = expanded,
                    onToggle = {},
                    icon = KitIcons.terminal,
                ) {
                    content()
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.box(): DpRect = getUnclippedBoundsInRoot()

    @Test
    fun `E20-S25 an opened group has one chevron, on its line, and none on its steps`() =
        runDesktopComposeUiTest(412, 600) {
            setContent {
                Work(expanded = true) {
                    KitToolRow(step("a"), opened = false, onToggle = {}, body = { KitText("output") })
                    KitToolRow(
                        step("b", KitToolKind.Shell, "Run", "flutter test test/home_test.dart", duration = "38 seconds"),
                        opened = false,
                        onToggle = {},
                        body = { KitText("output") },
                    )
                }
            }
            waitForIdle()
            onAllNodesWithTag("kit-step-timeline-chevron", useUnmergedTree = true).assertCountEquals(1)
            onAllNodesWithTag("kit-tool-chevron", useUnmergedTree = true).assertCountEquals(0)
            onAllNodesWithTag("kit-tool-forward", useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `E20-S25 each step is one 32 dp line with its node on the rail and its words one column in`() =
        runDesktopComposeUiTest(412, 600) {
            setContent {
                Work(expanded = true) {
                    KitToolRow(step("a"), opened = false, onToggle = {}, body = { KitText("output") })
                }
            }
            waitForIdle()
            val line = onNodeWithTag("kit-step-line", useUnmergedTree = true).box()
            assertEquals(32f, (line.bottom - line.top).value, .5f, "one 32 dp line: $line")
            val node = onNodeWithTag("kit-step-node", useUnmergedTree = true).box()
            // The rail's column: the 20 dp node on the 16 dp edge, the words 12 dp after it (glyph + space3).
            assertEquals(16f, node.left.value, .5f, "the node on the reply's edge: $node")
            assertEquals(20f, (node.right - node.left).value, .5f, "a 20 dp node: $node")
            assertTrue(abs((node.top + node.bottom).value / 2 - (line.top + line.bottom).value / 2) < 1f, "node centred on the line: $node vs $line")
            val title = onNodeWithText("Read", useUnmergedTree = true).box()
            assertEquals(48f, title.left.value, .5f, "the words start one column in (16 + 20 + 12): $title")
            // One line: the path follows the verb and the duration ends the line, all on the same row.
            val path = onNodeWithText("lib/ui/router.dart", useUnmergedTree = true).box()
            val time = onNodeWithTag("kit-tool-duration", useUnmergedTree = true).box()
            assertTrue(path.left > title.right && abs(path.top.value + path.bottom.value - title.top.value - title.bottom.value) < 4f, "verb and path on one line: $title $path")
            assertTrue(time.left > path.right && time.right.value <= 412f - 16f + .5f, "the time ends the line: $time")
        }

    @Test
    fun `E20-S25 the group line puts its glyph and words on the rail's column`() =
        runDesktopComposeUiTest(412, 400) {
            setContent { Work(expanded = false) {} }
            waitForIdle()
            val words = onNodeWithText("Read 1 file · ran 1 command", useUnmergedTree = true).box()
            assertEquals(48f, words.left.value, .5f, "the summary starts where the steps' words do: $words")
            val chevron = onNodeWithTag("kit-step-timeline-chevron", useUnmergedTree = true).box()
            assertTrue(chevron.left >= words.right, "the chevron follows the words: $chevron after $words")
        }

    @Test
    fun `E20-S25 a tap on a step opens its body at the text's own x`() =
        runDesktopComposeUiTest(412, 600) {
            var opened by mutableStateOf(false)
            setContent {
                Work(expanded = true) {
                    KitToolRow(step("a"), opened = opened, onToggle = { opened = !opened }, body = { KitText("the step's output") })
                }
            }
            onAllNodesWithText("the step's output", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithText("Read").performClick()
            waitForIdle()
            val body = onNodeWithText("the step's output", useUnmergedTree = true).box()
            val title = onNodeWithText("Read", useUnmergedTree = true).box()
            assertEquals(title.left.value, body.left.value, .5f, "the body at the text's own x: $body vs $title")
        }

    @Test
    fun `E20-S25 a step that opens another page keeps its forward chevron`() =
        runDesktopComposeUiTest(412, 400) {
            var opens = 0
            setContent {
                Work(expanded = true) {
                    KitToolRow(
                        step("s", KitToolKind.SubAgent, "Delegated to explore", null, duration = null),
                        opened = false,
                        onToggle = {},
                        onOpen = { opens++ },
                    )
                }
            }
            onAllNodesWithTag("kit-tool-forward", useUnmergedTree = true).assertCountEquals(1)
            onNodeWithText("Delegated to explore").performClick()
            assertEquals(1, opens)
        }

    @Test
    fun `E20-S25 the rail mirrors in Arabic - the node on the right, the words to its left`() =
        runDesktopComposeUiTest(412, 400) {
            setContent {
                Work(expanded = true, rtl = true) {
                    KitToolRow(step("a", title = "قراءة"), opened = false, onToggle = {}, body = { KitText("output") })
                }
            }
            waitForIdle()
            val node = onNodeWithTag("kit-step-node", useUnmergedTree = true).box()
            assertEquals(412f - 16f, node.right.value, .5f, "the node on the right edge: $node")
            val title = onNodeWithText("قراءة", useUnmergedTree = true).box()
            assertEquals(412f - 48f, title.right.value, .5f, "the words end one column in: $title")
        }

    @Test
    fun `E20-S25 the group glyph follows what the work mostly did`() {
        assertEquals(KitIcons.tools, kitWorkIcon())
        assertEquals(KitIcons.editNote, kitWorkIcon(read = 1, searched = 1, edited = 1, ran = 1))
        assertEquals(KitIcons.fileText, kitWorkIcon(read = 3, edited = 1, ran = 2))
        assertEquals(KitIcons.terminal, kitWorkIcon(read = 2, ran = 2))
    }
}
