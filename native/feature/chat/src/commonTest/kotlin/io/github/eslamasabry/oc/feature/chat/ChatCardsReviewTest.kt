package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.cards.CardAgent
import io.github.eslamasabry.oc.engine.cards.CardAnswer
import io.github.eslamasabry.oc.engine.cards.CardAsk
import io.github.eslamasabry.oc.engine.cards.CardCodec
import io.github.eslamasabry.oc.engine.cards.CardNode
import io.github.eslamasabry.oc.engine.cards.CardParse
import io.github.eslamasabry.oc.engine.cards.CardScope
import io.github.eslamasabry.oc.engine.cards.CardTail
import io.github.eslamasabry.oc.engine.cards.CardsProblem
import io.github.eslamasabry.oc.engine.delivery.LocalSendId
import io.github.eslamasabry.oc.engine.delivery.PendingSend
import io.github.eslamasabry.oc.engine.delivery.SendOutcome
import io.github.eslamasabry.oc.engine.delivery.SendOutcomeId
import io.github.eslamasabry.oc.engine.delivery.SendPhase
import io.github.eslamasabry.oc.engine.delivery.SendRoute
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.cards.CardsSetup
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.cards.CardCharts
import io.github.eslamasabry.oc.feature.chat.cards.ChatCardsModel
import io.github.eslamasabry.oc.feature.chat.cards.GraphCardsQualifier
import io.github.eslamasabry.oc.feature.chat.cards.MemoryCardsPrefs
import io.github.eslamasabry.oc.feature.chat.cards.ModelCardSlotHost
import io.github.eslamasabry.oc.feature.chat.presentation.ChatConfirm
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.assertIs
import io.github.eslamasabry.oc.feature.chat.cards.CardForms
import io.github.eslamasabry.oc.feature.chat.cards.CardTranscript
import io.github.eslamasabry.oc.feature.chat.cards.CardsQualification
import io.github.eslamasabry.oc.feature.chat.cards.awaitCardSend
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P12 review (chat-cards #5, #6, #7, #10, #11): the card slot's pure pieces, from their catalog rows. */
class ChatCardsReviewTest {
    private val ref = ConversationRef(ProfileId("p"), SessionId("s"))

    private fun text(id: String, words: String, role: Role = Role.User): MessageWithParts =
        MessageWithParts(
            MessageInfo(id, "s", role),
            persistentListOf(Part(id = "p-$id", messageId = id, sessionId = "s", type = PartType.Text, rawType = "text", text = words)),
        )

    // ---- #2: card links ----

    private class Outside : ChatOutside {
        val calls = ArrayList<String>()

        override fun copy(text: String): Boolean = false

        override fun share(text: String): Boolean = false

        override fun checkLink(url: String): LinkCheck = LinkCheck.Ask("example.com").also { calls += "check $url" }

        override fun openLink(url: String): Boolean = true.also { calls += "open $url" }
    }

    @Test
    fun `B-SEC-065 a card link asks first naming its host and opens only through the chat's safe opener`() = runTest {
        val registry =
            ConnectionRegistry(
                emptySet(),
                { _, _, _ -> error("no server in a link test") },
                AppCoroutineScope(backgroundScope.coroutineContext),
                TestDispatchers(StandardTestDispatcher(testScheduler)),
            )
        val model = ChatCardsModel(ref, registry, CardsSetup(MemoryCardsPrefs(), GraphCardsQualifier(registry)))
        val outside = Outside()
        val chat = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate, outside = outside)
        val host = ModelCardSlotHost(model, chat::openLink)
        host.openLink("https://example.com/report")
        val ask = assertIs<ChatConfirm.OpenLink>(chat.current().confirm)
        assertEquals("example.com", ask.host)
        assertEquals(listOf("check https://example.com/report"), outside.calls, "nothing opens before yes")
        chat.confirmOpenLink()
        assertEquals("open https://example.com/report", outside.calls.last())
    }

    // ---- #6: history reconciliation ----

    @Test
    fun `B-CHAT-052 a read that started before a live update never replaces the newer parts`() {
        val t = CardTranscript()
        t.applyRead(t.mark(), CardTail(listOf(text("a1", "old", Role.Assistant)), true))
        val mark = t.mark()
        // While the read is out, the live stream brings the newer words of a1.
        t.upsert(Part(id = "p-a1", messageId = "a1", sessionId = "s", type = PartType.Text, rawType = "text", text = "newer"))
        assertFalse(t.applyRead(mark, CardTail(listOf(text("a1", "old", Role.Assistant)), true)), "an overtaken read is dropped")
        assertEquals("newer", t.list().single().parts.single().text)
        // A read that saw nothing newer is applied whole.
        assertTrue(t.applyRead(t.mark(), CardTail(listOf(text("a1", "newest", Role.Assistant)), true)))
        assertEquals("newest", t.list().single().parts.single().text)
    }

    @Test
    fun `B-CHAT-031 a read keeps the server's order and never appends an older cached prompt after the newest ask`() {
        val t = CardTranscript()
        t.applyRead(t.mark(), CardTail(listOf(text("u0", "an old prompt")), true))
        // The newest page no longer holds u0; it must not reappear after the ask (it would pass the card over).
        t.applyRead(t.mark(), CardTail(listOf(text("u1", "build it"), text("a1", "asking", Role.Assistant)), true))
        assertEquals(listOf("u1", "a1"), t.list().map { it.info.id })
    }

    // ---- #7: qualification ----

    @Test
    fun `B-CHAT-034 a connected oc-ui helper alone never qualifies OpenCode 2`() {
        val here = listOf(CardAgent.OpenCode2)
        val connected = Outcome.Ok(listOf(McpServerInfo("oc-ui", "connected", null)))
        val verdict = CardsQualification.verdict(connected, here)
        assertTrue(verdict.ready.isEmpty())
        assertEquals(CardsProblem.NotQualified, verdict.problem)
        assertEquals(here, verdict.affected, "B-CHAT-035: the reason names OpenCode 2")
        // Once qualified on the device, the helper's self-check is still required.
        assertEquals(here, CardsQualification.verdict(connected, here, qualified = true).ready)
        val failed = Outcome.Ok(listOf(McpServerInfo("oc-ui", "failed", null)))
        assertTrue(CardsQualification.verdict(failed, here, qualified = true).ready.isEmpty())
    }

    // ---- #5: the real delivery result ----

    private fun bubble(id: String, phase: SendPhase) =
        PendingSend(LocalSendId(id), ref, SendRoute.Plain, "answer", persistentListOf(), 0, phase)

    @Test
    fun `B-CHAT-050 a definite failure after the send id reaches the card so it may be answered again`() = runTest {
        val outcomes = MutableStateFlow<ImmutableList<SendOutcome>>(persistentListOf())
        val pending = MutableStateFlow<ImmutableList<PendingSend>>(persistentListOf(bubble("l1", SendPhase.Waiting)))
        val result = async { awaitCardSend(LocalSendId("l1"), outcomes, pending) }
        runCurrent()
        assertFalse(result.isCompleted, "still on its way")
        pending.value = persistentListOf()
        outcomes.value =
            persistentListOf(
                SendOutcome.PutBack(SendOutcomeId("o"), ref, "answer", persistentListOf(), Failure.Network(NetworkWhy.Refused), LocalSendId("l1"))
            )
        assertEquals(Outcome.Failed(Failure.Network(NetworkWhy.Refused)), result.await())
    }

    @Test
    fun `B-CHAT-050 accepted is not a receipt, unconfirmed or unseen stays may-have-been-sent`() = runTest {
        val uncertain = Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
        val none = MutableStateFlow<ImmutableList<SendOutcome>>(persistentListOf())
        assertEquals(
            Outcome.Ok(Unit),
            awaitCardSend(LocalSendId("l1"), none, MutableStateFlow(persistentListOf(bubble("l1", SendPhase.Accepted)))),
        )
        val unconfirmed =
            MutableStateFlow<ImmutableList<SendOutcome>>(persistentListOf(SendOutcome.Unconfirmed(SendOutcomeId("o"), ref, null, null, LocalSendId("l1"))))
        assertEquals(uncertain, awaitCardSend(LocalSendId("l1"), unconfirmed, MutableStateFlow(persistentListOf())))
        // Gone without a result the card saw: never treated as "nothing was sent".
        assertEquals(uncertain, awaitCardSend(LocalSendId("l1"), none, MutableStateFlow(persistentListOf())))
        // Another send's result is not this card's.
        val other =
            MutableStateFlow<ImmutableList<SendOutcome>>(
                persistentListOf(SendOutcome.PutBack(SendOutcomeId("o"), ref, "x", persistentListOf(), Failure.Unknown(), LocalSendId("l2")))
            )
        assertEquals(uncertain, awaitCardSend(LocalSendId("l1"), other, MutableStateFlow(persistentListOf())))
    }

    // ---- #11: Undo keeps a cleared field empty ----

    @Test
    fun `B-CHAT-004 Undo after clearing an optional field with a default keeps it empty`() {
        val input =
            mapOf(
                "v" to 1L, "id" to "f", "title" to "Name it", "body" to emptyList<Any?>(),
                "ask" to
                    mapOf(
                        "kind" to "form",
                        "fields" to
                            listOf(
                                mapOf("id" to "name", "label" to "Name", "type" to "text", "default" to "draft"),
                                mapOf("id" to "note", "label" to "Note", "type" to "text"),
                            ),
                    ),
            )
        val card = (CardCodec.fromInput(input, CardScope("p", "opencode", "/x"), "s", "m", "c") as CardParse.Parsed).card
        val ask = card.ask as CardAsk.Form
        assertEquals(KitFormValue.Text("draft"), CardForms.initial(ask, null)["name"], "a fresh card starts at the default")
        val values = CardForms.initial(ask, null).toMutableMap()
        values["name"] = KitFormValue.Text("")
        values["note"] = KitFormValue.Text("hi")
        val sent = CardForms.answer(ask, values)!!
        assertEquals(CardAnswer.Form(mapOf("note" to "hi")), sent)
        // Undo gives the draft back: the cleared field stays cleared.
        assertEquals(KitFormValue.Text(""), CardForms.initial(ask, sent)["name"])
        assertEquals(KitFormValue.Text("hi"), CardForms.initial(ask, sent)["note"])
    }

    // ---- #10: charts ----

    @Test
    fun `B-CHAT-054 the chart summary counts points and names the highest value across every series`() {
        val chart =
            CardNode.Chart(
                CardNode.ChartKind.Line,
                "ms",
                persistentListOf("Jan", "Feb", "Mar"),
                persistentListOf(
                    CardNode.Series("api", persistentListOf(10.0, 12.0, 9.0)),
                    CardNode.Series("web", persistentListOf(8.0, 42.5, 7.0, 3.0)),
                ),
            )
        val facts = CardCharts.facts(chart)
        assertEquals(4, facts.points)
        assertEquals("web, Feb", facts.where)
        assertEquals("42.5 ms", CardCharts.shown(facts.highest!!, chart.unit))
        // One series: the series name is left out.
        val single = chart.copy(series = persistentListOf(CardNode.Series("api", listOf(1.0, 3.0).toImmutableList())))
        assertEquals("Feb", CardCharts.facts(single).where)
        assertEquals("12%", CardCharts.shown(12.0, "%"))
        assertEquals("3.14", CardCharts.plain(3.14159))
    }
}
