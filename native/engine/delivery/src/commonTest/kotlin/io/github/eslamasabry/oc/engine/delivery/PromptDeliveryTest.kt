package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** 09 §4.2 steps 1–10. Every test counts what the server holds: one send, at most one delivery. */
class PromptDeliveryTest {
    private fun receiptLink() = FakeLink(capabilities = ServerCapabilities(commandReceipts = true, clientPromptMessageID = true), correlated = true)

    @Test
    fun `B-STATE-042 bubble first, with the durable marker on disk before send returns, then exactly one delivery`() = runTest {
        val w = World(Disk(), this)
        val gate = CompletableDeferred<Unit>()
        w.link.transport = { gate.await(); Outcome.Ok(Unit) }
        val stops = Stops()
        val d = w.delivery(stops = stops)
        val id = (d.send(req("hello")) as Outcome.Ok).value
        val bubble = d.pending.value.single()
        assertEquals(SendPhase.Waiting, bubble.phase, "the bubble shows before the transport answered")
        assertTrue(w.disk.queueBlob()!!.contains("\"id\":\"${bubble.reviewId}\""), "the marker is on disk first")
        assertTrue(w.disk.queueBlob()!!.contains("\"dispatchedAt\":$NOW_MS"))
        assertTrue(w.link.attempts.isEmpty())
        assertNotNull(d.localTurns.value[REF], "the live line starts at once")

        gate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(listOf("hello"), w.link.deliveredTexts())
        assertEquals(SendPhase.Accepted, d.pending.value.single().phase)
        assertTrue(w.queue.entries().isEmpty(), "accepted: the marker is removed")
        val accepted = assertIs<SendOutcome.Accepted>(d.outcomes.value.single())
        assertEquals(id, accepted.localId)
        assertEquals(listOf("clear"), stops.events, "the next prompt clears stoppedByPerson")
    }

    @Test
    fun `B-STATE-042 a refused marker keeps the prompt unsent and the words in the composer`() = runTest {
        val w = World(Disk(), this)
        w.disk.refuseQueueWrites()
        val d = w.delivery()
        assertEquals(Outcome.Failed(Failure.StorageRefused()), d.send(req("hello")))
        testScheduler.runCurrent()
        assertTrue(w.link.attempts.isEmpty() && d.pending.value.isEmpty() && d.outcomes.value.isEmpty())
    }

    @Test
    fun `B-STATE-043 a lost response keeps the marker and asks, Check again only looks, and nothing is ever sent twice`() = runTest {
        val w = World(Disk(), this, link = receiptLink())
        w.link.answer = { FakeLink.Answer.LostResponse }
        val d = w.delivery()
        val id = (d.send(req("hello")) as Outcome.Ok).value
        testScheduler.runCurrent()
        assertTrue(d.pending.value.isEmpty(), "the bubble leaves")
        val unconfirmed = assertIs<SendOutcome.Unconfirmed>(d.outcomes.value.single(), "never PutBack: the words would invite a duplicate")
        assertEquals(setOf("hello"), d.unconfirmedTexts(REF), "the draft never restores it")
        assertNull(d.localTurns.value[REF])

        val review = assertNotNull(unconfirmed.reviewId)
        assertEquals(1, w.queue.reviewCount(P1))
        assertEquals(Outcome.Ok(ReceiptLook.Confirmed), d.checkAgain(LocalSendId(review)), "the lookup finds the admission")
        assertEquals(1, w.link.attempts.size, "Check again only looks")
        assertTrue(w.queue.entries().isEmpty())
        w.queue.flush()
        w.restart().queue.flush()
        assertEquals(1, w.link.attempts.size, "neither a flush nor a restart sends it again")
        assertEquals(id.raw.startsWith("local-"), true)
    }

    @Test
    fun `B-STATE-043 a crash with the request on the wire leaves a review entry, never a second send`() = runTest {
        val disk = Disk()
        val doomed = CoroutineScope(coroutineContext + Job())
        val w = World(disk, this, engineScope = doomed)
        val onWire = CompletableDeferred<Unit>()
        w.link.answer = {
            w.link.delivered += it
            onWire.complete(Unit)
            awaitCancellation()
        }
        val d = w.delivery(scope = doomed)
        d.send(req("hello"))
        onWire.await()
        doomed.cancel() // the process dies before the answer
        val reborn = w.restart()
        reborn.link.answer = { FakeLink.Answer.Confirmed }
        reborn.queue.flush()
        assertEquals(1, reborn.link.delivered.size)
        assertEquals(1, reborn.queue.reviewCount(P1), "it waits for the person")
    }

    @Test
    fun `B-STATE-040 nothing left the device, the words go back once, and a chip send never puts them back`() = runTest {
        val w = World(Disk(), this)
        w.link.transport = { Failure.TimedOut().failed() }
        val d = w.delivery()
        d.send(req("typed words"))
        testScheduler.runCurrent()
        val back = assertIs<SendOutcome.PutBack>(d.outcomes.value.single())
        assertEquals("typed words", back.text)
        assertTrue(w.queue.entries().isEmpty(), "no review entry: nothing left")
        assertTrue(d.pending.value.isEmpty())
        d.acknowledge(back.id)
        assertTrue(d.outcomes.value.isEmpty(), "acknowledged entries leave")

        d.sendInsteadOfDraft(req("chip words"))
        testScheduler.runCurrent()
        assertTrue(d.outcomes.value.isEmpty(), "a failed chip never leaves its words in the box")
        assertTrue(w.link.attempts.isEmpty())
    }

    @Test
    fun `B-STATE-119 a changed connection between the bubble and the dispatch sends to neither server`() = runTest {
        val w = World(Disk(), this)
        val replacement = FakeLink()
        w.link.transport = {
            w.connection.link.value = replacement
            Outcome.Ok(Unit)
        }
        val d = w.delivery()
        d.send(req("hello"))
        testScheduler.runCurrent()
        assertTrue(w.link.attempts.isEmpty() && replacement.attempts.isEmpty())
        assertIs<SendOutcome.PutBack>(d.outcomes.value.single())
        assertTrue(w.queue.entries().isEmpty())
    }

    @Test
    fun `B-STATE-039 offline sends wait up to 10 s for the transport, then queue, and a server without a queue keeps the draft`() = runTest {
        val w = World(Disk(), this)
        w.link.connected = false
        w.connection.link.value = null
        val d = w.delivery()
        val returning = FakeLink()
        val sent = async { d.send(req("after resume")) }
        testScheduler.advanceTimeBy(5.seconds)
        w.connection.link.value = returning // the connection comes back within the wait
        testScheduler.runCurrent()
        assertIs<Outcome.Ok<LocalSendId>>(sent.await())
        testScheduler.runCurrent()
        assertEquals(listOf("after resume"), returning.deliveredTexts(), "sent live, not queued")

        w.connection.link.value = FakeLink(connected = false)
        val queued = async { d.send(req("offline words")) }
        testScheduler.advanceTimeBy(10.seconds + 1.seconds)
        assertIs<Outcome.Ok<LocalSendId>>(queued.await())
        assertEquals(listOf("offline words"), w.queue.entries().map { it.text })
        kotlin.test.assertNull(w.queue.entries().single().dispatchedAt, "queued, not marked")
        assertIs<SendOutcome.Queued>(d.outcomes.value.last())

        w.connection.link.value = FakeLink(connected = false, capabilities = ServerCapabilities(offlinePromptQueue = false))
        val kept = async { d.send(req("no queue here")) }
        testScheduler.advanceTimeBy(11.seconds)
        assertEquals(Outcome.Failed(Failure.Network(NetworkWhy.Offline)), kept.await(), "draft saved: the composer keeps it")
    }

    @Test
    fun `B-CHAT-106 a staged undo is resolved before sending`() = runTest {
        val w = World(Disk(), this)
        val facts = Facts().apply { undo += REF }
        val d = w.delivery(facts = facts)
        assertEquals(Outcome.Failed(Failure.Refused(RefusalReason.UndoUnresolved)), d.send(req("hello")))
        testScheduler.runCurrent()
        assertTrue(w.link.attempts.isEmpty() && w.queue.entries().isEmpty())
    }

    @Test
    fun `map CHAT-GENUI-31 commands and shell route by what was typed, before references are folded in`() = runTest {
        val w = World(Disk(), this)
        val d = w.delivery()
        d.send(req(text = "/new\n\n[ref: src/a.kt]", typed = "/new"))
        d.send(req(text = "!ls -la", typed = "!ls -la"))
        d.send(req(text = "! not a shell", typed = "! not a shell"))
        d.send(req(text = "!!also not", typed = "!!also not"))
        testScheduler.runCurrent()
        assertEquals(listOf("/new ", "!ls -la"), w.link.commands)
        assertEquals(listOf("! not a shell", "!!also not"), w.link.deliveredTexts())
        assertEquals(TypedKind.Command("review", "this file"), TypedKind.of("/review this file"))
    }

    @Test
    fun `SendOutcome the outcome list is bounded and survives until acknowledged`() = runTest {
        val w = World(Disk(), this)
        w.link.transport = { Failure.TimedOut().failed() }
        val d = w.delivery()
        repeat(40) {
            w.clock.advance(1.seconds)
            d.send(req("words $it"))
            testScheduler.runCurrent()
        }
        assertEquals(PromptDelivery.MAX_OUTCOMES, d.outcomes.value.size)
        assertEquals("words 39", (d.outcomes.value.last() as SendOutcome.PutBack).text, "the oldest leave first")
    }

    private fun assertNull(value: Any?) = kotlin.test.assertNull(value)
}
