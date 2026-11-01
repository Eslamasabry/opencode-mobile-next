package io.github.eslamasabry.oc.ui.kitchat.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlin.test.Test
import kotlin.test.assertEquals

/** The one form renderer and its sheet and card (plan 09 §5.5, map CHAT-GENUI-47/48). */
@OptIn(ExperimentalTestApi::class)
class KitFormTest {
    private val fields =
        persistentListOf(
            KitFormField("name", "Project name", KitFormFieldKind.Text, maxLength = 40),
            KitFormField(
                "kind", "Where it runs", KitFormFieldKind.Select,
                options = persistentListOf(KitFormOption("cloud", "Cloud"), KitFormOption("local", "This computer")),
                ownLabel = "Something else",
            ),
            KitFormField("region", "Region", KitFormFieldKind.Text, active = false),
            KitFormField(
                "tags", "Tags", KitFormFieldKind.MultiSelect,
                options = persistentListOf(KitFormOption("db", "Database"), KitFormOption("web", "Website")),
            ),
            KitFormField("notify", "Tell me when done", KitFormFieldKind.Toggle),
            KitFormField("signin", "Sign in", KitFormFieldKind.ExternalLink, linkLabel = "Open example.com"),
        )

    @Test
    fun `CHAT-GENUI-48 every active field is drawn with its kit part and edits report the whole value`() =
        runComposeUiTest {
            var values: ImmutableMap<String, KitFormValue> by mutableStateOf(persistentMapOf())
            val links = mutableListOf<String>()
            setContent {
                ChatTestTheme {
                    KitForm(
                        fields = fields,
                        values = values,
                        onChange = { k, v -> values = (values + (k to v)).toImmutableMap() },
                        onOpenLink = { links += it },
                    )
                }
            }
            onNodeWithText("Project name", useUnmergedTree = true).assertIsDisplayed()
            onAllNodesWithText("Region", useUnmergedTree = true).assertCountEquals(0) // inactive: folded away

            onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("kit-form-name"))).performTextInput("neon")
            waitForIdle()
            assertEquals(KitFormValue.Text("neon"), values["name"])

            onNodeWithText("This computer").performClick()
            assertEquals(KitFormValue.Text("local"), values["kind"])

            onNodeWithText("Website").performClick()
            onNodeWithText("Database").performClick()
            assertEquals(KitFormValue.Choices(persistentListOf("db", "web")), values["tags"], "declaration order")

            onNodeWithText("Tell me when done").performClick()
            assertEquals(KitFormValue.Toggle(true), values["notify"])

            onNodeWithText("Open example.com").performClick()
            assertEquals(listOf("signin"), links, "the link opens only through the caller, after a tap")
        }

    @Test
    fun `CHAT-GENUI-47 Something else opens a field whose words become the answer`() = runComposeUiTest {
        var values: ImmutableMap<String, KitFormValue> by mutableStateOf(persistentMapOf())
        setContent {
            ChatTestTheme {
                KitForm(fields = fields, values = values, onChange = { k, v -> values = (values + (k to v)).toImmutableMap() })
            }
        }
        onNodeWithTag("kit-form-kind-own").assertDoesNotExistCompat()
        onNodeWithText("Something else").performClick()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("kit-form-kind-own"))).performTextInput("A VPS")
        waitForIdle()
        assertEquals(KitFormValue.Text("A VPS"), values["kind"])
        onNodeWithText("Cloud").performClick()
        assertEquals(KitFormValue.Text("cloud"), values["kind"], "choosing an option replaces own words")
    }

    @Test
    fun `STATE-8 the sheet says why it cannot send yet and Send does nothing until it can`() = runComposeUiTest {
        var sends = 0
        var declines = 0
        setContent {
            ChatTestTheme {
                KitFormSheetBody(
                    title = "Deploy settings",
                    description = "The agent needs these to continue.",
                    sendLabel = "Send answers",
                    declineLabel = "Decline this request",
                    onSend = { sends++ },
                    onDecline = { declines++ },
                    blockedReason = "Answer Project name first",
                ) {
                    KitForm(fields = fields, values = persistentMapOf(), onChange = { _, _ -> })
                }
            }
        }
        onNodeWithText("Answer Project name first", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Send answers").assertIsNotEnabled().performClick()
        assertEquals(0, sends)
        onNodeWithText("Decline this request").performClick()
        assertEquals(1, declines)
    }

    @Test
    fun `B-PROTO-044 a refused answer shows its reason and the form stays answerable`() = runComposeUiTest {
        var sends = 0
        setContent {
            ChatTestTheme {
                KitFormSheetBody(
                    title = "Deploy settings", description = null,
                    sendLabel = "Send answers", declineLabel = "Decline this request",
                    onSend = { sends++ }, onDecline = {},
                    error = "The server did not accept these answers.",
                ) {}
            }
        }
        onNodeWithText("The server did not accept these answers.", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Send answers").performClick()
        assertEquals(1, sends, "Send answers is the retry")
    }

    @Test
    fun `CHAT-GENUI-48 the chat card opens the sheet with Answer and says where an answer is`() = runComposeUiTest {
        var opens = 0
        var status: String? by mutableStateOf(null)
        setContent {
            ChatTestTheme {
                KitFormCard(
                    title = "Deploy settings", summary = "3 questions", answerLabel = "Answer",
                    onAnswer = { opens++ }, status = status,
                )
            }
        }
        onNodeWithText("3 questions", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Answer").performClick()
        assertEquals(1, opens)
        status = "Sending your answers"
        waitForIdle()
        onNodeWithText("Sending your answers", useUnmergedTree = true).assertIsDisplayed()
        onAllNodesWithText("Answer").assertCountEquals(0)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
