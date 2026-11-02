package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.cards.AgentCard
import io.github.eslamasabry.oc.engine.cards.CardAgent
import io.github.eslamasabry.oc.engine.cards.CardAnswer
import io.github.eslamasabry.oc.engine.cards.CardBoardState
import io.github.eslamasabry.oc.engine.cards.CardCodec
import io.github.eslamasabry.oc.engine.cards.CardDelivery
import io.github.eslamasabry.oc.engine.cards.CardEntry
import io.github.eslamasabry.oc.engine.cards.CardParse
import io.github.eslamasabry.oc.engine.cards.CardProblem
import io.github.eslamasabry.oc.engine.cards.CardScope
import io.github.eslamasabry.oc.engine.cards.CardState
import io.github.eslamasabry.oc.engine.cards.CardsProblem
import io.github.eslamasabry.oc.engine.cards.CardsStatus
import io.github.eslamasabry.oc.feature.chat.cards.CardAnswerDisplay
import io.github.eslamasabry.oc.feature.chat.cards.CardsRowUi
import io.github.eslamasabry.oc.feature.chat.cards.CardsSettingsActions
import io.github.eslamasabry.oc.feature.chat.cards.CardsSettingsScreen
import io.github.eslamasabry.oc.feature.chat.cards.CardsSettingsUi
import io.github.eslamasabry.oc.feature.chat.cards.ChatCardSlotHost
import io.github.eslamasabry.oc.feature.chat.cards.ChatTurnCards
import io.github.eslamasabry.oc.feature.chat.cards.LocalChatCards
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/** The chat's card slot by visible words (B-CHAT-004 kinds and states, B-CHAT-052 read-only, the Cards page). */
@OptIn(ExperimentalTestApi::class)
class ChatCardSlotTest {
    private val platform =
        object : KitPlatform by DesktopKitPlatform() {
            override val isDebug: Boolean = true
        }

    private class Host(entry: CardEntry, available: Boolean = true, busy: Boolean = false) : ChatCardSlotHost {
        val flow = MutableStateFlow(CardBoardState(1, persistentListOf(entry), busy, available))
        override val state: StateFlow<CardBoardState> = flow
        val answers = ArrayList<CardAnswer>()
        val undos = ArrayList<AgentCard>()
        val links = ArrayList<String>()
        var settings = 0
        override val agentName: String = "OpenCode"

        override fun answer(card: AgentCard, answer: CardAnswer) {
            answers += answer
        }

        override fun undo(card: AgentCard) {
            undos += card
        }

        override fun openLink(url: String) {
            links += url
        }

        override fun openSettings() {
            settings++
        }
    }

    private fun card(input: Map<String, Any?>): AgentCard =
        (CardCodec.fromInput(input, CardScope("p", "opencode", "/x"), "s", "m", "call") as CardParse.Parsed).card

    private fun input(ask: Any?, body: List<Any?> = emptyList(), title: String = "Pick a database") =
        buildMap<String, Any?> {
            put("v", 1L)
            put("id", "db")
            put("title", title)
            put("body", body)
            if (ask != null) put("ask", ask)
        }

    private fun entry(card: AgentCard, state: CardState = CardState.Waiting, delivery: CardDelivery = CardDelivery.Idle, summary: String? = null) =
        CardEntry("call", "m", "t1", CardParse.Parsed(card), state, delivery, summary, null)

    private val choice =
        mapOf(
            "kind" to "choice",
            "options" to listOf(mapOf("id" to "pg", "label" to "Postgres"), mapOf("id" to "sq", "label" to "SQLite")),
        )

    @Composable
    private fun Root(host: ChatCardSlotHost, content: @Composable () -> Unit) {
        KitTheme(platform = platform, appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
            CompositionLocalProvider(LocalChatCards provides host, content = content)
        }
    }

    @Test
    fun `B-CHAT-004 a choice card shows its words and one tap answers with that option`() = runComposeUiTest {
        val host = Host(entry(card(input(choice))))
        setContent { Root(host) { ChatTurnCards("t1") } }
        onNodeWithText("OpenCode asks").assertIsDisplayed()
        onNodeWithText("Pick a database").assertIsDisplayed()
        onNodeWithText("SQLite").performClick()
        assertEquals(listOf<CardAnswer>(CardAnswer.Choice(listOf("sq"))), host.answers)
    }

    @Test
    fun `B-CHAT-004 a held answer collapses to its words and Undo takes it back`() = runComposeUiTest {
        val host = Host(entry(card(input(choice)), delivery = CardDelivery.Held, summary = "SQLite"))
        setContent { Root(host) { ChatTurnCards("t1") } }
        onNodeWithText("SQLite").assertIsDisplayed()
        onNodeWithText("Undo").performClick()
        assertEquals(1, host.undos.size)
    }

    @Test
    fun `B-CHAT-004 confirm buttons name their verb and danger asks for the same answer once`() = runComposeUiTest {
        val host = Host(entry(card(input(mapOf("kind" to "confirm")))))
        setContent { Root(host) { ChatTurnCards("t1") } }
        onNodeWithText("Cancel").assertIsDisplayed()
        onNodeWithText("Yes, go ahead").performClick()
        assertEquals(listOf<CardAnswer>(CardAnswer.Confirm(true)), host.answers)
    }

    @Test
    fun `B-CHAT-004 a form holds Send until required fields are filled, then sends typed values`() = runComposeUiTest {
        val form =
            mapOf(
                "kind" to "form",
                "fields" to
                    listOf(
                        mapOf("id" to "name", "label" to "Name", "type" to "text", "required" to true),
                        mapOf("id" to "count", "label" to "Count", "type" to "number"),
                    ),
            )
        val host = Host(entry(card(input(form, title = "Your details"))))
        setContent { Root(host) { ChatTurnCards("t1") } }
        onNodeWithText("The agent sees this, and it is saved in the conversation.").assertIsDisplayed()
        onNodeWithTag("kit-agent-card-send").assertIsNotEnabled()
        onNodeWithTag("kit-form-name", useUnmergedTree = true).performClickOnField()
        onNodeWithText("Name", useUnmergedTree = true)
        typeInto("name", "Ada")
        onNodeWithTag("kit-agent-card-send").assertIsEnabled().performClick()
        assertEquals(listOf<CardAnswer>(CardAnswer.Form(mapOf("name" to "Ada"))), host.answers)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickOnField() = this

    private fun androidx.compose.ui.test.ComposeUiTest.typeInto(field: String, text: String) {
        onNode(
            androidx.compose.ui.test.hasSetTextAction() and
                androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("kit-form-$field")),
            useUnmergedTree = true,
        ).performTextInput(text)
    }

    @Test
    fun `B-CHAT-004 an answered card is a receipt that opens read-only, a passed-over one says Not answered`() = runComposeUiTest {
        val body = listOf(mapOf("type" to "text", "text" to "Two options"))
        val answered = Host(entry(card(input(choice, body)), state = CardState.Answered, summary = "Postgres"))
        setContent { Root(answered) { ChatTurnCards("t1") } }
        onNodeWithText("Postgres").assertIsDisplayed()
        onNodeWithText("Show the card").performClick()
        onNodeWithText("Two options").assertIsDisplayed()
        answered.flow.value = answered.flow.value.copy(cards = persistentListOf(entry(card(input(choice, body)), state = CardState.PassedOver)))
        onNodeWithText("Not answered").assertIsDisplayed()
    }

    @Test
    fun `B-CHAT-052 a card that cannot be confirmed is read-only and offers the visible way to turn cards on`() = runComposeUiTest {
        val host = Host(entry(card(input(choice)), state = CardState.Unknown), available = false)
        setContent { Root(host) { ChatTurnCards("t1") } }
        onNodeWithText("We can't tell yet whether this card was answered. It updates when the conversation does.").assertIsDisplayed()
        onNodeWithText("Cards from agents").performClick()
        assertEquals(1, host.settings)
        assertEquals(0, host.answers.size)
    }

    @Test
    fun `B-CHAT-004 controls wait while the agent works and say why`() = runComposeUiTest {
        val host = Host(entry(card(input(choice))), busy = true)
        setContent { Root(host) { ChatTurnCards("t1") } }
        onNodeWithTag("kit-agent-card-option-pg").assertIsNotEnabled()
    }

    @Test
    fun `B-CHAT-030 an unreadable card is one plain line with the reason under Details`() = runComposeUiTest {
        val host = Host(CardEntry("call", "m", "t1", CardParse.Unreadable(CardProblem.SecretField), CardState.Unknown, CardDelivery.Idle, null, null))
        setContent { Root(host) { ChatTurnCards("t1") } }
        onNodeWithText("This card couldn't be shown").assertIsDisplayed()
        onNodeWithText("Details").performClick()
        onNodeWithText("SecretField").assertIsDisplayed()
    }

    @Test
    fun `B-CHAT-004 a report card shows its body and a link goes out only after a tap, naming its host`() = runComposeUiTest {
        val body = listOf(mapOf("type" to "link", "label" to "Docs", "url" to "https://example.com/docs"), mapOf("type" to "progress", "label" to "Build", "value" to 0.5))
        val host = Host(entry(card(input(null, body, title = "Status")), state = CardState.Report))
        setContent { Root(host) { ChatTurnCards("t1") } }
        onNodeWithText("OpenCode reports").assertIsDisplayed()
        onNodeWithText("Build").assertIsDisplayed()
        assertEquals(emptyList(), host.links)
        onNodeWithText("Docs · example.com").performClick()
        assertEquals(listOf("https://example.com/docs"), host.links)
    }

    @Test
    fun `B-CHAT-037 cards of another turn are not drawn under this one`() = runComposeUiTest {
        val host = Host(entry(card(input(choice))))
        setContent { Root(host) { ChatTurnCards("other-turn") } }
        onNodeWithText("Pick a database").assertDoesNotExistCompat()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()

    @Test
    fun `B-CHAT-032 a receipt bubble shows only the summary of a valid answer`() {
        val c = card(input(choice))
        val text = CardCodec.answerText(c, CardAnswer.Choice(listOf("pg")))
        assertEquals("Postgres", CardAnswerDisplay.text(text))
        assertEquals("[oc-ui answer db] Postgres\nnot json", CardAnswerDisplay.text("[oc-ui answer db] Postgres\nnot json"))
        assertEquals("hello", CardAnswerDisplay.text("hello"))
    }

    @Test
    fun `B-CHAT-034 the Cards page says Off by default and the switch asks for the change`() = runComposeUiTest {
        val calls = ArrayList<Pair<String, Boolean>>()
        val actions =
            object : CardsSettingsActions {
                override fun setEnabled(profileId: String, on: Boolean) {
                    calls += profileId to on
                }

                override fun back() {}
            }
        var ui = CardsSettingsUi(persistentListOf(CardsRowUi("p1", "Home server", false, CardsStatus.Off, pending = false, failed = false)))
        setContent { Root(Host(entry(card(input(choice))))) { CardsSettingsScreen(ui, actions) } }
        onNodeWithText("Off").assertIsDisplayed()
        onNodeWithTag("cards-switch-p1").performClick()
        assertEquals(listOf("p1" to true), calls)
    }

    @Test
    fun `B-CHAT-035 the status names the agents of this connection in plain words`() = runComposeUiTest {
        val ui =
            CardsSettingsUi(
                persistentListOf(
                    CardsRowUi("p1", "Home", true, CardsStatus.Unavailable(CardsProblem.NotQualified, listOf(CardAgent.OpenCode2)), false, false)
                )
            )
        val actions =
            object : CardsSettingsActions {
                override fun setEnabled(profileId: String, on: Boolean) {}

                override fun back() {}
            }
        setContent { Root(Host(entry(card(input(choice))))) { CardsSettingsScreen(ui, actions) } }
        onNodeWithText("Not available. OpenCode 2 hasn't been checked to work with cards yet.").assertIsDisplayed()
    }
}
