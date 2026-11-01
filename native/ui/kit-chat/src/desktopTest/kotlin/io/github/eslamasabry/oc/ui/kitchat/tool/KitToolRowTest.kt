package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `KitToolRow` and `KitCommandBox` (plan 09 §9.5, plan 06 §7.8): one step of the agent's work, drawn from the
 * kit-local [KitToolView]. The words are the caller's; the row only draws them.
 */
@OptIn(ExperimentalTestApi::class)
class KitToolRowTest {
    private fun view(
        kind: KitToolKind = KitToolKind.Read,
        title: String = "Read file",
        subject: String? = "lib/main.dart",
        detail: String? = null,
        status: KitToolStatus = KitToolStatus.Done,
        durationLabel: String? = null,
        added: Int? = null,
        removed: Int? = null,
        statusLabel: String = "Done",
    ) = KitToolView(
        id = "call-1", kind = kind, title = title, subject = subject, detail = detail, status = status,
        durationLabel = durationLabel, added = added, removed = removed, statusLabel = statusLabel,
    )

    @Test
    fun `B-CHAT-062 a step row draws its title, subject, detail, status and duration as given`() = runComposeUiTest {
        setContent {
            ChatTestTheme {
                KitToolRow(
                    view(
                        kind = KitToolKind.Search, title = "Searched", subject = "src/**/*.kt", detail = "3 matches",
                        status = KitToolStatus.Failed, statusLabel = "Didn't finish", durationLabel = "2 s",
                    ),
                    opened = false,
                    onToggle = {},
                )
            }
        }
        onNodeWithText("Searched", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("src/**/*.kt", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("3 matches", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Didn't finish", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("2 s", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `B-CHAT-062 the body appears only when opened, and a tap on the header asks to toggle`() = runComposeUiTest {
        var opened by mutableStateOf(false)
        var toggles = 0
        setContent {
            ChatTestTheme {
                KitToolRow(
                    view(),
                    opened = opened,
                    onToggle = {
                        toggles++
                        opened = !opened
                    },
                    body = { KitText("the step's output") },
                )
            }
        }
        onAllNodesWithText("the step's output", useUnmergedTree = true).assertCountEquals(0)
        onNodeWithText("Read file").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))

        onNodeWithText("Read file").performClick()
        assertEquals(1, toggles)
        onNodeWithText("the step's output", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Read file").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))

        onNodeWithText("Read file").performClick()
        assertEquals(2, toggles)
        onAllNodesWithText("the step's output", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `B-CHAT-062 a stopped step reads the given label, and its counts show as text`() = runComposeUiTest {
        setContent {
            ChatTestTheme {
                KitToolRow(
                    view(
                        kind = KitToolKind.Edit, title = "Edited", subject = "lib/app.dart",
                        status = KitToolStatus.Stopped, statusLabel = "Stopped", added = 4, removed = 1,
                    ),
                    opened = false,
                    onToggle = {},
                )
            }
        }
        onNodeWithText("Stopped", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("+4 −1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `B-CHAT-062 a step's subject reads left to right in an Arabic interface`() = runComposeUiTest {
        setContent {
            ChatTestTheme(localeTag = "ar") {
                KitToolRow(view(title = "قرأ الملف", subject = "lib/main.dart"), opened = false, onToggle = {})
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("lib/main.dart", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action!!
            .invoke(layouts)
        assertEquals(ResolvedTextDirection.Ltr, layouts.single().getParagraphDirection(0))
    }

    @Test
    fun `B-SEC-094 a registered secret in a tool subject or command never reaches the semantics tree`() =
        runComposeUiTest {
            val canary = "sk-canary-123456"
            setContent {
                ChatTestTheme(redactor = Redactor().apply { registerSecret(canary) }) {
                    KitToolRow(
                        view(
                            kind = KitToolKind.Shell, title = "Ran a command", subject = "echo $canary",
                            detail = "printed $canary", status = KitToolStatus.Done, statusLabel = "Done",
                        ),
                        opened = true,
                        onToggle = {},
                        body = {
                            KitCommandBox(
                                command = "export KEY=$canary && printenv",
                                output = "KEY=$canary\nHOME=/root",
                                moreLabel = "Show all",
                                onMore = {},
                                exitLabel = "Exit code 0",
                            )
                        },
                    )
                }
            }
            val words = semanticsWords()
            assertFalse(canary in words, "the canary reached the semantics tree:\n$words")
            assertTrue(Redactor.MASK in words, "the mask is drawn in its place:\n$words")
            assertTrue("HOME=/root" in words, "the rest of the output still shows")
        }

    @Test
    fun `B-CHAT-062 the command box keeps the prompt sign out of the command and caps output at twelve lines`() =
        runComposeUiTest {
            var more = 0
            setContent {
                ChatTestTheme {
                    KitCommandBox(
                        command = "ls -la",
                        output = (1..20).joinToString("\n") { "line $it" },
                        moreLabel = "Show all 20 lines",
                        onMore = { more++ },
                        exitLabel = "Exit code 0",
                    )
                }
            }
            // The command is its own text, without "$ " (copying it copies the command only).
            onNodeWithText("ls -la", useUnmergedTree = true).assertIsDisplayed()
            val words = semanticsWords()
            assertFalse("$ ls" in words, "the prompt sign is not part of the command's text")
            onNodeWithText("line 12", useUnmergedTree = true).assertIsDisplayed()
            onAllNodesWithText("line 13", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithText("Exit code 0", useUnmergedTree = true).assertIsDisplayed()

            onNodeWithText("Show all 20 lines").performClick()
            assertEquals(1, more)
        }

    @Test
    fun `B-CHAT-062 output that fits shows whole, with no more button`() = runComposeUiTest {
        setContent {
            ChatTestTheme {
                KitCommandBox(command = "pwd", output = "/root\n", moreLabel = "Show all", onMore = {}, exitLabel = null)
            }
        }
        onNodeWithText("/root", useUnmergedTree = true).assertIsDisplayed()
        onAllNodesWithText("Show all").assertCountEquals(0)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach { node -> assertFalse(node.config[SemanticsProperties.Text].any { it.text.isBlank() }) }
    }
}
