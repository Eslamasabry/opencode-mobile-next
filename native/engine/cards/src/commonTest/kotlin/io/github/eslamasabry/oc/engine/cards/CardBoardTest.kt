package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.cards.CardFixtures.assistant
import io.github.eslamasabry.oc.engine.cards.CardFixtures.input
import io.github.eslamasabry.oc.engine.cards.CardFixtures.scope
import io.github.eslamasabry.oc.engine.cards.CardFixtures.user
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardBoardTest {
    private class Sender(var result: Outcome<Unit> = Outcome.Ok(Unit)) : CardAnswerSender {
        val sent = ArrayList<Pair<String, List<PromptAttachment>>>()

        override suspend fun send(sessionId: String, text: String, photos: List<PromptAttachment>): Outcome<Unit> {
            sent += text to photos
            return result
        }
    }

    private fun TestScope.board(sender: Sender, photos: Boolean = false): CardBoard =
        CardBoard(backgroundScope, sender, photosAllowed = { photos }).also {
            it.setAvailable(true)
            it.observe(scope, "session", listOf(assistant()), tailComplete = true)
        }

    private fun CardBoard.only(): CardEntry = state.value.cards.single()

    @Test
    fun `B-CHAT-004 an answer is held 3 s with Undo, then sent once`() = runTest {
        val sender = Sender()
        val board = board(sender)
        val card = board.only().card!!
        assertEquals(CardState.Waiting, board.only().state)
        assertIs<Outcome.Ok<Unit>>(board.answer(card, CardAnswer.Confirm(true)))
        assertEquals(CardDelivery.Held, board.only().delivery)
        assertEquals("Confirmed", board.only().summary)
        board.answer(card, CardAnswer.Confirm(true)) // a duplicate tap
        advanceTimeBy(2_999)
        runCurrent()
        assertTrue(sender.sent.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, sender.sent.size)
        assertEquals(CardCodec.answerText(card, CardAnswer.Confirm(true)), sender.sent.single().first)
        // Accepted is not a receipt: the card waits for the echo and refuses a resend.
        assertEquals(CardDelivery.DeliveryUnknown, board.only().delivery)
        assertIs<Outcome.Failed>(board.answer(card, CardAnswer.Confirm(true)))
        assertEquals(CardRefusal.CheckFirst, board.only().refusal)
        // The authoritative echo settles it.
        board.observe(scope, "session", listOf(assistant(), user(sender.sent.single().first)), true)
        assertEquals(CardState.Answered, board.only().state)
        assertEquals(CardDelivery.Idle, board.only().delivery)
        assertEquals("Confirmed", board.only().summary)
    }

    @Test
    fun `B-CHAT-004 Undo sends nothing and gives back what the person had`() = runTest {
        val sender = Sender()
        val ask =
            mapOf(
                "kind" to "choice",
                "multi" to true,
                "options" to listOf(mapOf("id" to "a", "label" to "Alpha"), mapOf("id" to "b", "label" to "Beta")),
            )
        val board = CardBoard(backgroundScope, sender).also {
            it.setAvailable(true)
            it.observe(scope, "session", listOf(assistant(input = input(ask = ask))), true)
        }
        val card = board.only().card!!
        board.answer(card, CardAnswer.Choice(listOf("a", "b")))
        assertEquals("Alpha, Beta", board.only().summary)
        board.undo(card)
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(sender.sent.isEmpty())
        assertEquals(CardDelivery.Idle, board.only().delivery)
        assertEquals(CardState.Waiting, board.only().state)
        assertEquals(CardAnswer.Choice(listOf("a", "b")), board.only().draft)
    }

    @Test
    fun `B-CHAT-052 a typed reply passes the card over and a held answer is re-checked before sending`() = runTest {
        val sender = Sender()
        val board = board(sender)
        val card = board.only().card!!
        board.answer(card, CardAnswer.Confirm(true))
        board.observe(scope, "session", listOf(assistant(), user("I typed instead")), true)
        advanceTimeBy(3_001)
        runCurrent()
        assertTrue(sender.sent.isEmpty(), "stale answer never sent")
        assertEquals(CardState.PassedOver, board.only().state)
    }

    @Test
    fun `B-CHAT-052 cards are read-only without availability or with an incomplete tail`() = runTest {
        val sender = Sender()
        val board = CardBoard(backgroundScope, sender)
        board.observe(scope, "session", listOf(assistant()), true)
        assertEquals(CardState.Unknown, board.only().state)
        assertIs<Outcome.Failed>(board.answer(board.only().card!!, CardAnswer.Confirm(true)))
        board.setAvailable(true)
        board.observe(scope, "session", listOf(assistant()), tailComplete = false)
        assertEquals(CardState.Unknown, board.only().state)
        assertTrue(board.state.value.waiting.isEmpty())
    }

    @Test
    fun `B-CHAT-036 disabling drops a held answer before anything is sent`() = runTest {
        val sender = Sender()
        val board = board(sender)
        board.answer(board.only().card!!, CardAnswer.Confirm(true))
        board.setAvailable(false)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(sender.sent.isEmpty())
        assertEquals(CardState.Unknown, board.only().state)
    }

    @Test
    fun `B-CHAT-004 a busy session refuses and a definite failure keeps the answer for a retry`() = runTest {
        val sender = Sender(Outcome.Failed(Failure.Network(NetworkWhy.Offline)))
        val board = board(sender)
        val card = board.only().card!!
        board.setBusy(true)
        assertIs<Outcome.Failed>(board.answer(card, CardAnswer.Confirm(true)))
        assertEquals(CardRefusal.Busy, board.only().refusal)
        board.setBusy(false)
        board.answer(card, CardAnswer.Confirm(true))
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(CardDelivery.Failed, board.only().delivery)
        sender.result = Outcome.Ok(Unit)
        board.answer(card, CardAnswer.Confirm(true))
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(2, sender.sent.size)
    }

    @Test
    fun `B-CHAT-051 an uncertain dispatch is never resent automatically`() = runTest {
        val sender = Sender(Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain)))
        val board = board(sender)
        val card = board.only().card!!
        board.answer(card, CardAnswer.Confirm(true))
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(CardDelivery.DeliveryUnknown, board.only().delivery)
        assertIs<Outcome.Failed>(board.answer(card, CardAnswer.Confirm(false)))
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun `B-CHAT-053 photo answers validate matching attachments and no photos ride other answers`() = runTest {
        val photo = PromptAttachment("image/png", "a.png", "data:image/png;base64,iVBORw0K")
        val sender = Sender()
        val confirm = board(sender, photos = true)
        assertIs<Outcome.Failed>(confirm.answer(confirm.only().card!!, CardAnswer.Confirm(true), listOf(photo)))
        assertEquals(CardRefusal.PhotosRefused, confirm.only().refusal)

        val photoAsk = input(ask = mapOf("kind" to "photo", "purpose" to "Show it", "max" to 2L))
        val board = CardBoard(backgroundScope, sender, photosAllowed = { true }).also {
            it.setAvailable(true)
            it.observe(scope, "session", listOf(assistant(input = photoAsk)), true)
        }
        val card = board.only().card!!
        assertIs<Outcome.Failed>(board.answer(card, CardAnswer.Photo(2), listOf(photo)))
        assertIs<Outcome.Failed>(board.answer(card, CardAnswer.Photo(1), listOf(photo.copy(mime = "application/pdf"))))
        assertIs<Outcome.Failed>(board.answer(card, CardAnswer.Photo(1), listOf(photo.copy(url = "https://example.com/a.png"))))
        assertIs<Outcome.Ok<Unit>>(board.answer(card, CardAnswer.Photo(1), listOf(photo)))
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(listOf(photo), sender.sent.single().second)
        val none = CardBoard(backgroundScope, sender, photosAllowed = { false }).also {
            it.setAvailable(true)
            it.observe(scope, "session", listOf(assistant(input = photoAsk)), true)
        }
        assertIs<Outcome.Failed>(none.answer(none.only().card!!, CardAnswer.Photo(1), listOf(photo)))
    }

    @Test
    fun `B-CHAT-037 waiting cards are listed for the feed and a typed reply clears them`() = runTest {
        val board = board(Sender())
        assertEquals(1, board.state.value.waiting.size)
        board.observe(scope, "session", listOf(assistant(), user("typed")), true)
        assertTrue(board.state.value.waiting.isEmpty())
    }

    @Test
    fun `B-CHAT-030 an unreadable card is an entry with its reason and no card`() = runTest {
        val board = CardBoard(backgroundScope, Sender())
        board.setAvailable(true)
        board.observe(scope, "session", listOf(assistant(input = input() + ("v" to 2L))), true)
        assertEquals(CardParse.Unreadable(CardProblem.Version), board.only().parse)
        assertNull(board.only().card)
    }

    @Test
    fun `B-CHAT-004 the card belongs to the turn of the prompt before it`() = runTest {
        val board = CardBoard(backgroundScope, Sender())
        board.observe(scope, "session", listOf(user("hi", id = "u1"), assistant()), true)
        assertEquals("u1", board.only().turnId)
    }
}
