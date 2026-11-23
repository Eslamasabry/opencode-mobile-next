package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolOutputView
import io.github.eslamasabry.oc.feature.chat.ui.ReplyActions
import io.github.eslamasabry.oc.feature.chat.ui.ToolStepBody
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * B-CHAT-311..313: an opened edit step's preview never becomes a scroll box inside the conversation. A drag
 * scrolls the page, the preview keeps 12 lines, and a cut one offers Changes, which opens the Changes view.
 */
@OptIn(ExperimentalTestApi::class)
class EditPreviewScrollTest {
    private val path = "/home/owner/projects/cart/Cart.kt"

    private fun writeStep(lines: Int = 40): TurnStep {
        val content = (1..lines).joinToString("\n") { "val line$it = \"content of line $it\"" }
        val state =
            ToolState(
                ToolStatus.Completed,
                input = JsonObject(mapOf("filePath" to JsonPrimitive(path), "content" to JsonPrimitive(content))),
                output = "Wrote file successfully.",
                startedAtMs = 1_000,
                completedAtMs = 1_040,
            )
        return TurnStep("w1", toolFacts("write", state), StepState.Done, 40, 1_000, toolOutputView(state, "write"))
    }

    private fun reply(onOpenChanges: (() -> Unit)? = null) =
        ReplyActions(KitMarkdownActions(onLink = {}), copyLabel = "Copy", copy = {}, onOpenChanges = onOpenChanges)

    /** The page the conversation draws: the step sits 300 dp down, so only the page can take a drag. */
    @Composable
    private fun Page(step: TurnStep, reply: ReplyActions?, onPage: (ScrollState) -> Unit) {
        val page = rememberScrollState()
        onPage(page)
        Column(Modifier.verticalScroll(page)) {
            Spacer(Modifier.height(300.dp))
            WriteRow(step, reply)
            Spacer(Modifier.height(900.dp))
        }
    }

    @Composable
    private fun WriteRow(step: TurnStep, reply: ReplyActions?) {
        KitToolRow(
            KitToolView(
                id = step.partId,
                kind = KitToolKind.Write,
                title = "Write",
                subject = step.facts.subject,
                detail = null,
                status = KitToolStatus.Done,
                durationLabel = null,
                added = 40,
                removed = null,
                statusLabel = "Done",
            ),
            opened = true,
            onToggle = {},
            body = { ToolStepBody(step, reply) },
        )
    }

    /**
     * Today's way to see the rest of a long preview: open every "Show all output" button there is. The merged tree
     * holds the words and the click on one node, and TalkBack's activation needs no room on screen (the button sits
     * past the fold of the 760 dp window).
     */
    private fun ComposeUiTest.showAllOutput() {
        repeat(8) {
            val buttons = onAllNodes(hasText("Show all output") and hasClickAction())
            if (buttons.fetchSemanticsNodes().isEmpty()) return
            buttons[0].performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
        }
    }

    /** One tap on the button whose words are [text], as TalkBack does: the click needs no room on screen. */
    private fun ComposeUiTest.tapButton(text: String) {
        onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    @Test
    fun `B-CHAT-311 a drag that starts on a long edit preview scrolls the page`() {
        val step = writeStep()
        kitUiTest(360, 760) {
            var page: ScrollState? = null
            setContent { E20Scene { Page(step, reply()) { page = it } } }
            waitForIdle()
            showAllOutput()
            val scroll = page ?: error("the page never composed")
            // A slow finger-up drag of 300 px, as a person scrolling the page would make it.
            onNodeWithText("val line5 =", substring = true, useUnmergedTree = true).performTouchInput {
                down(center)
                repeat(10) { moveBy(Offset(0f, -30f), delayMillis = 20) }
                moveBy(Offset.Zero, delayMillis = 100)
                up()
            }
            waitForIdle()
            assertTrue(
                scroll.value > 150,
                "a drag on the preview must scroll the page, not a box inside it; the page sits at ${scroll.value}",
            )
        }
    }

    @Test
    fun `B-CHAT-312 an opened edit preview has no vertical scroll of its own`() {
        val step = writeStep()
        kitUiTest(360, 760) {
            setContent { E20Scene { Page(step, reply()) {} } }
            waitForIdle()
            showAllOutput()
            val under =
                onAllNodes(
                    hasTestTag("kit-tool-body") or hasAnyAncestor(hasTestTag("kit-tool-body")),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
            assertTrue(under.isNotEmpty(), "the opened step draws its body")
            assertTrue(
                under.none { SemanticsProperties.VerticalScrollAxisRange in it.config },
                "a node under the opened step's body scrolls vertically on its own",
            )
        }
    }

    @Test
    fun `B-CHAT-313 a cut edit preview offers Changes under line 12 and it opens the Changes view`() {
        val step = writeStep()
        var opened = 0
        for ((i, scale) in listOf(1f, 1.3f).withIndex()) {
            kitUiTest(360, 760) {
                setContent { E20Scene(scale) { Page(step, reply { opened++ }) {} } }
                waitForIdle()
                val where = "360 dp at ${scale}x"
                assertTrue(
                    onAllNodesWithText("val line13 =", substring = true, useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isEmpty(),
                    "$where: the preview cuts at 12 lines",
                )
                assertTrue(
                    onAllNodesWithText("Changes", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                    "$where: a preview cut at 12 lines offers Changes",
                )
                val changes = boundsOfText("Changes")
                val twelfth = boundsOfText("val line12 =", substring = true)
                val body = boundsOfTag("kit-tool-body")
                assertTrue(changes.top >= twelfth.bottom - 0.5f, "$where: Changes $changes sits under line 12 $twelfth")
                assertTrue(
                    changes.right <= body.right + 0.5f,
                    "$where: Changes $changes keeps inside the card's text edge $body",
                )
                tapButton("Changes")
                assertTrue(opened == i + 1, "$where: a tap opens the conversation's Changes view once (at $opened)")
            }
        }
    }
}
