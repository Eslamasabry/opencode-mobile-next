package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.cards.CardFixtures.assistant
import io.github.eslamasabry.oc.engine.cards.CardFixtures.scope
import io.github.eslamasabry.oc.engine.cards.CardFixtures.user
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** P12 review (chat-cards #3, #4): the send-once guard survives a reopen, and the last check before sending is fresh. */
class CardDispatchTest {
    private class Sender(var result: Outcome<Unit> = Outcome.Ok(Unit)) : CardAnswerSender {
        val sent = ArrayList<String>()

        override suspend fun send(sessionId: String, text: String, photos: List<PromptAttachment>): Outcome<Unit> {
            sent += text
            return result
        }
    }

    private class MemoryStore(var text: String? = null) : CardJournalStore {
        override fun read(profileId: String): String? = text

        override suspend fun write(profileId: String, json: String?): Outcome<Unit> {
            text = json
            return Outcome.Ok(Unit)
        }
    }

    private fun TestScope.board(
        sender: Sender,
        journal: CardJournal = MemoryCardJournal(),
        check: CardDispatchCheck = CardDispatchCheck { null },
    ): CardBoard =
        CardBoard(backgroundScope, sender, check = check, journal = journal).also {
            it.setAvailable(true)
            it.observe(scope, "session", listOf(assistant()), tailComplete = true)
        }

    private fun CardBoard.only(): CardEntry = state.value.cards.single()

    private fun TestScope.afterHold() {
        advanceTimeBy(CardBoard.HOLD_MILLIS + 1)
        runCurrent()
    }

    @Test
    fun `B-CHAT-051 an answer that may have left blocks a second send after the chat is reopened`() = runTest {
        val store = MemoryStore()
        val sender = Sender()
        val first = board(sender, StoredCardJournal(store))
        val card = first.only().card!!
        first.answer(card, CardAnswer.Confirm(true))
        afterHold()
        assertEquals(1, sender.sent.size)
        first.close()
        val saved = store.text!!
        assertFalse("Confirmed" in saved, "no answer content is kept")
        assertFalse(card.title in saved, "no card content is kept")

        // Reopening builds a new board over the same saved journal; history still shows the ask.
        val again = board(sender, StoredCardJournal(store))
        assertEquals(CardState.Waiting, again.only().state)
        assertEquals(CardDelivery.DeliveryUnknown, again.only().delivery)
        assertIs<Outcome.Failed>(again.answer(card, CardAnswer.Confirm(true)))
        assertEquals(CardRefusal.CheckFirst, again.only().refusal)
        afterHold()
        assertEquals(1, sender.sent.size, "never sent twice")

        // The authoritative echo settles the card and the journal lets go.
        again.observe(scope, "session", listOf(assistant(), user(sender.sent.single())), true)
        runCurrent()
        assertEquals(CardState.Answered, again.only().state)
        assertNull(store.text)
    }

    @Test
    fun `B-CHAT-051 a corrupt saved journal fails closed`() = runTest {
        val sender = Sender()
        val board = board(sender, StoredCardJournal(MemoryStore("{not json")))
        assertEquals(CardDelivery.DeliveryUnknown, board.only().delivery)
        assertIs<Outcome.Failed>(board.answer(board.only().card!!, CardAnswer.Confirm(true)))
        afterHold()
        assertTrue(sender.sent.isEmpty())
    }

    @Test
    fun `B-CHAT-051 a definite failure lets go of the journal so the person may try again`() = runTest {
        val store = MemoryStore()
        val sender = Sender(Outcome.Failed(Failure.Network(NetworkWhy.Refused)))
        val board = board(sender, StoredCardJournal(store))
        val card = board.only().card!!
        board.answer(card, CardAnswer.Confirm(true))
        afterHold()
        assertEquals(CardDelivery.Failed, board.only().delivery)
        assertNull(store.text)
        sender.result = Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
        assertIs<Outcome.Ok<Unit>>(board.answer(card, CardAnswer.Confirm(true)))
        afterHold()
        assertEquals(2, sender.sent.size)
        assertEquals(CardDelivery.DeliveryUnknown, board.only().delivery)
        assertTrue(store.text!!.contains("\"dispatch\""))
    }

    @Test
    fun `B-CHAT-051 the journal reads the Flutter record of a migrated install`() = runTest {
        val identity = CardFixtures.card().identity
        val flutter =
            """{"v":1,"entries":[{"profile":"profile","source":"source","directory":"/project","session":"session",""" +
                """"message":"assistant","call":"call","revision":"r","endpoint":"https://h","dispatch":"msg_1"}]}"""
        assertEquals(setOf(identity), StoredCardJournal(MemoryStore(flutter)).dispatched("profile"))
        val waitingOnly = flutter.replace(""","dispatch":"msg_1"""", "")
        assertEquals(emptySet(), StoredCardJournal(MemoryStore(waitingOnly)).dispatched("profile"))
        assertNull(StoredCardJournal(MemoryStore(flutter)).dispatched("other"), "another profile's record is corrupt here")
    }

    @Test
    fun `B-CHAT-050 a fresh read showing another client answered sends nothing`() = runTest {
        val sender = Sender()
        lateinit var board: CardBoard
        board =
            board(sender, check = {
                // The last-moment history read finds a typed reply from another client.
                board.observe(scope, "session", listOf(assistant(), user("answered elsewhere")), true)
                null
            })
        board.answer(board.only().card!!, CardAnswer.Confirm(true))
        afterHold()
        assertTrue(sender.sent.isEmpty())
        assertEquals(CardState.PassedOver, board.only().state)
        assertEquals(CardDelivery.Idle, board.only().delivery)
    }

    @Test
    fun `B-CHAT-039 a session found running at the last check refuses and sends nothing`() = runTest {
        val sender = Sender()
        val board = board(sender, check = { CardRefusal.Busy })
        val card = board.only().card!!
        board.answer(card, CardAnswer.Confirm(true))
        afterHold()
        assertTrue(sender.sent.isEmpty())
        assertEquals(CardRefusal.Busy, board.only().refusal)
        assertEquals(CardDelivery.Idle, board.only().delivery)
        assertEquals(CardState.Waiting, board.only().state)
    }
}
