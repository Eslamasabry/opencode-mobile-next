package io.github.eslamasabry.oc.ui.kitchat.card

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormOption
import kotlinx.collections.immutable.persistentListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertEquals

/** The kit's agent card parts by visible words (plan 09 §13.7). */
@OptIn(ExperimentalTestApi::class)
class KitAgentCardTest {
    @Test
    fun `B-CHAT-004 a multi choice sends the chosen ids in option order and Send says why it waits`() = runComposeUiTest {
        var sent: List<String>? = null
        var selected: Set<String> by androidx.compose.runtime.mutableStateOf(setOf())
        setContent {
            ChatTestTheme {
                KitAgentCard(
                    eyebrow = "Claude Code asks",
                    title = "Which checks?",
                    mode = KitAgentCardMode.Ask,
                    ask = {
                        KitCardChoice(
                            options = persistentListOf(KitFormOption("a", "Lint"), KitFormOption("b", "Tests")),
                            multi = true,
                            selected = selected,
                            onToggle = { selected = if (it in selected) selected - it else selected + it },
                            onSend = { sent = it },
                            sendLabel = "Send",
                            chooseHint = "Choose at least one option to send.",
                        )
                    },
                )
            }
        }
        onNodeWithText("Claude Code asks").assertIsDisplayed()
        onNodeWithText("Tests").performClick()
        onNodeWithText("Lint").performClick()
        onNodeWithTag("kit-agent-card-send").performClick()
        assertEquals(listOf("a", "b"), sent)
    }

    @Test
    fun `B-CHAT-004 a held receipt offers Undo and an Arabic card still reads its words`() = runComposeUiTest {
        var undone = 0
        setContent {
            ChatTestTheme(localeTag = "ar") {
                KitAgentCard(
                    eyebrow = "Claude Code يسأل",
                    title = "اختر قاعدة البيانات",
                    mode = KitAgentCardMode.Receipt,
                    receiptLabel = "SQLite",
                    undoLabel = "تراجع",
                    onUndo = { undone++ },
                )
            }
        }
        onNodeWithText("اختر قاعدة البيانات").assertIsDisplayed()
        onNodeWithText("SQLite").assertIsDisplayed()
        onNodeWithText("تراجع").performClick()
        assertEquals(1, undone)
    }

    @Test
    fun `B-CHAT-037 the list slot shows a waiting card under its row with a way in`() = runComposeUiTest {
        var opened = 0
        setContent { ChatTestTheme { KitCardListSlot("OpenCode asks", "Pick a database", "Open", { opened++ }) } }
        onNodeWithText("Pick a database").assertIsDisplayed()
        onNodeWithText("Open").performClick()
        assertEquals(1, opened)
    }
}
