package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.delivery.queue.QueueErrors
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The flush (plan 04 §13.4). Every test counts what the server holds: a prompt reaches it at most once. */
class OfflineQueueTest {
    private fun World.entry(id: String): QueuedPrompt? = queue.entries().firstOrNull { it.id == id }

    @Test
    fun `B-STATE-039 flush sends the active server's prompts oldest first and counts the others`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("late", createdAt = NOW_MS - 10))
        w.queue.queuePrompt(prompt("other", profile = P2, createdAt = NOW_MS - 30))
        w.queue.queuePrompt(prompt("early", createdAt = NOW_MS - 20))
        w.queue.flush()
        assertEquals(listOf("prompt early", "prompt late"), w.link.deliveredTexts())
        assertEquals(listOf("other"), w.queue.entries().map { it.id }, "another server's draft stays queued")
        val state = w.queue.state.value
        assertEquals(2, state.lastFlushedCount)
        assertEquals(1, state.lastFlushSkippedForOtherProfiles)
        assertEquals(1, state.flushRevision)
        w.queue.flush()
        assertEquals(2, w.link.delivered.size, "a second flush sends nothing twice")
    }

    @Test
    fun `B-STATE-038 the auto-send policy gates the flush, and an explicit retry still sends`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a"))
        w.policy.allows = false
        w.queue.flush()
        assertTrue(w.link.attempts.isEmpty(), "policy off: nothing automatic")

        w.policy.allows = true
        w.link.transport = {
            w.policy.allows = false // switched off while the connection wakes
            Unit.let { Outcome.Ok(it) }
        }
        w.queue.flush()
        assertTrue(w.link.attempts.isEmpty(), "turned off while waiting: nothing sent")

        w.link.transport = { Outcome.Ok(Unit) }
        assertTrue(w.queue.retry("a"), "the person's explicit retry")
        assertEquals(listOf("prompt a"), w.link.deliveredTexts())
    }

    @Test
    fun `B-STATE-037 a queued send rechecks its location after the wake, and a draft discarded meanwhile is never sent`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a"))
        w.link.transport = {
            w.link.scope = DispatchScope(P1, "http://127.0.0.1:4097", "/another-project", null)
            Outcome.Ok(Unit)
        }
        w.queue.flush()
        assertTrue(w.link.attempts.isEmpty(), "location changed: nothing sent")
        assertNull(w.entry("a")!!.dispatchedAt, "and nothing marked")

        val w2 = World(Disk(), this)
        w2.queue.queuePrompt(prompt("b"))
        w2.link.transport = {
            assertEquals(Outcome.Ok(true), w2.queue.remove("b"))
            Outcome.Ok(Unit)
        }
        w2.queue.flush()
        assertTrue(w2.link.attempts.isEmpty(), "discarded while waking: never sent")
    }

    @Test
    fun `B-STATE-119 a queued prompt cancelled during selection is never delivered, and a retired transport sends to neither`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a").let {
            QueuedPrompt(it.id, it.profileId, it.sessionId, it.text, modelProviderId = "p", modelId = "m", createdAt = it.createdAt)
        })
        w.link.onSelection = { w.queue.remove("a") }
        w.queue.flush()
        assertEquals(1, w.link.selections.size, "the saved model choice was applied first")
        assertEquals(ModelRef("p", "m"), w.link.selections.single().first?.model)
        assertTrue(w.link.attempts.isEmpty())

        val w2 = World(Disk(), this)
        w2.queue.queuePrompt(prompt("b"))
        val replacement = FakeLink()
        w2.link.transport = {
            w2.connection.link.value = replacement // reconnect: a new gateway epoch
            Outcome.Ok(Unit)
        }
        w2.queue.flush()
        assertTrue(w2.link.attempts.isEmpty() && replacement.attempts.isEmpty(), "dispatches to neither server")
        assertNull(w2.entry("b")!!.dispatchedAt)
    }

    @Test
    fun `B-STATE-040 a failure after dispatch parks the entry for review and never retries it`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a"))
        w.link.answer = { FakeLink.Answer.DeliveredThenFailed(Failure.Network(NetworkWhy.Reset)) }
        w.queue.flush()
        val parked = assertNotNull(w.entry("a"))
        assertNotNull(parked.dispatchedAt)
        assertEquals("Network", parked.error)
        assertEquals(1, w.queue.reviewCount(P1))
        w.link.answer = { FakeLink.Answer.Confirmed }
        w.queue.flush()
        w.restart().queue.flush()
        assertEquals(1, w.link.delivered.size, "the server holds it once, across flushes and a restart")
    }

    @Test
    fun `B-STATE-042 the marker is on disk before the send, a refused marker sends nothing, and the marker survives a restart`() = runTest {
        val disk = Disk()
        val w = World(disk, this)
        w.queue.queuePrompt(prompt("a"))
        disk.refuseQueueWrites(times = 1)
        w.queue.flush()
        assertTrue(w.link.attempts.isEmpty(), "storage refused the marker: the prompt stays unsent")
        assertNull(w.entry("a")!!.dispatchedAt)

        disk.allowAll()
        w.link.answer = {
            assertTrue(disk.queueBlob()!!.contains("\"dispatchedAt\":$NOW_MS"), "the marker reached the disk first")
            FakeLink.Answer.LostResponse
        }
        w.queue.flush()
        val after = w.restart()
        assertEquals(NOW_MS, after.entry("a")!!.dispatchedAt, "the marker survives the restart")
        after.link.answer = { FakeLink.Answer.Confirmed }
        after.queue.flush()
        assertEquals(1, after.link.delivered.size, "never auto-sent again")
    }

    @Test
    fun `B-STATE-043 accepted but not recorded, or a death in between, stays possibly-sent and asks instead of resending`() = runTest {
        val disk = Disk()
        val w = World(disk, this)
        w.queue.queuePrompt(prompt("a"))
        disk.refuse = { changes -> w.link.delivered.isNotEmpty() && changes.keys.any { it.endsWith("oc.offlineQueue") } }
        w.queue.flush()
        assertTrue(w.queue.acceptedUnrecorded("a"))
        assertNotNull(w.entry("a")!!.dispatchedAt)
        assertEquals(Outcome.Ok(false), w.queue.resend("a"), "a resend would be a certain duplicate")
        disk.allowAll()
        w.restart().queue.flush()
        assertEquals(1, w.link.delivered.size)

        // The process dies between the server's acceptance and the queue commit.
        val disk2 = Disk()
        val doomed = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
        val w2 = World(disk2, this, engineScope = doomed)
        w2.queue.queuePrompt(prompt("b"))
        val delivered = CompletableDeferred<Unit>()
        w2.link.answer = {
            w2.link.delivered += it
            delivered.complete(Unit)
            awaitCancellation()
        }
        val flush = doomed.launch { w2.queue.flush() }
        delivered.await()
        doomed.cancel() // process death
        flush.join()
        val reborn = w2.restart()
        reborn.link.answer = { FakeLink.Answer.Confirmed }
        assertNotNull(reborn.entry("b")!!.dispatchedAt, "marked: possibly sent")
        reborn.queue.flush()
        assertEquals(1, reborn.link.delivered.size, "never sent twice")
        assertEquals(1, reborn.queue.reviewCount(P1))
    }

    @Test
    fun `B-STATE-044 a status-bearing uncertain failure lets the batch go on, a pre-dispatch failure stays retryable, a refused resend clear sends nothing`() = runTest {
        val disk = Disk()
        val w = World(disk, this)
        w.queue.queuePrompt(prompt("a", createdAt = NOW_MS - 20))
        w.queue.queuePrompt(prompt("b", createdAt = NOW_MS - 10))
        w.link.answer = { if (it.prompt.text == "prompt a") FakeLink.Answer.DeliveredThenFailed(Failure.Server(502)) else FakeLink.Answer.Confirmed }
        w.queue.flush()
        assertEquals("Server:502", w.entry("a")!!.error)
        assertNotNull(w.entry("a")!!.dispatchedAt, "uncertain: parked")
        assertNull(w.entry("b"), "the rest of the batch went on")

        val w2 = World(Disk(), this, link = FakeLink(capabilities = ServerCapabilities(sessionRevert = true)))
        w2.queue.queuePrompt(prompt("c"))
        w2.link.revert = { Failure.Server(503).failed() }
        w2.queue.flush()
        assertNull(w2.entry("c")!!.dispatchedAt, "failed before dispatch: unmarked")
        assertEquals("Server:503", w2.entry("c")!!.error)
        w2.link.revert = { Outcome.Ok(false) }
        w2.queue.flush()
        assertEquals(listOf("prompt c"), w2.link.deliveredTexts(), "and retried by the next flush")

        disk.refuseQueueWrites()
        assertIs<Outcome.Failed>(w.queue.resend("a"))
        testScheduler.runCurrent()
        assertEquals(2, w.link.attempts.size, "the refused clear sent nothing")
        assertNotNull(w.entry("a")!!.dispatchedAt)
    }

    @Test
    fun `B-STATE-044 a person's resend clears the marker durably first and sends exactly once more`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a"))
        w.link.answer = { FakeLink.Answer.Refused(Failure.Network(NetworkWhy.Reset)) }
        w.queue.flush()
        assertNotNull(w.entry("a")!!.dispatchedAt)
        w.link.answer = { FakeLink.Answer.Confirmed }
        assertEquals(Outcome.Ok(true), w.queue.resend("a"))
        testScheduler.runCurrent()
        assertEquals(1, w.link.delivered.size)
        assertNull(w.entry("a"))
    }

    @Test
    fun `B-STATE-045 an entry on the wire cannot be removed or resent, and another server's removal never resurrects entries`() = runTest {
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a"))
        w.queue.queuePrompt(prompt("x", profile = P2))
        w.link.transport = {
            assertEquals(Outcome.Ok(1), w.queue.sweepProfile(P2)) // removal of another server's entries mid-flush
            Outcome.Ok(Unit)
        }
        w.link.answer = {
            assertTrue(w.queue.sending("a"))
            assertEquals(Outcome.Ok(false), w.queue.remove("a"), "on the wire: cannot be removed")
            assertEquals(Outcome.Ok(false), w.queue.resend("a"), "on the wire: cannot be resent")
            FakeLink.Answer.Confirmed
        }
        w.queue.flush()
        assertEquals(1, w.link.delivered.size)
        assertTrue(w.queue.entries().isEmpty(), "x stays removed, a is delivered and removed")
        assertTrue(w.restart().queue.entries().isEmpty())
        assertFalse(w.queue.sending("a"), "the guard is released once the outcome is persisted")
    }

    @Test
    fun `B-STATE-041 an online marker cannot be discarded, resent or cleared while active, deletion fences it, and an accepted unrecorded send cannot be resent`() = runTest {
        val disk = Disk()
        val w = World(disk, this)
        val online = prompt("online-1", dispatchedAt = NOW_MS)
        assertTrue(w.queue.holdOnline(online))
        assertEquals(Outcome.Ok(false), w.queue.remove("online-1"))
        assertEquals(Outcome.Ok(false), w.queue.resend("online-1"))
        assertFalse(w.queue.clearAll())
        assertTrue(w.queue.canDispatchOnline("online-1"))
        w.queue.flush()
        assertTrue(w.link.attempts.isEmpty(), "a held online send never flushes")

        w.queue.closeProfile(P1)
        assertFalse(w.queue.canDispatchOnline("online-1"), "profile deletion fences the online send")
        assertFalse(w.queue.holdOnline(prompt("online-2", dispatchedAt = NOW_MS)), "and admits no new hold")

        val w2 = World(Disk(), this)
        assertTrue(w2.queue.holdOnline(prompt("online-3", dispatchedAt = NOW_MS)))
        w2.disk.refuseQueueWrites()
        w2.queue.finishOnline("online-3", dispatched = true, accepted = true, error = null)
        assertTrue(w2.queue.acceptedUnrecorded("online-3"))
        assertEquals(Outcome.Ok(false), w2.queue.resend("online-3"), "accepted with failed cleanup: never resent")
        w2.disk.allowAll()
        assertNotNull(w2.restart().entry("online-3")!!.dispatchedAt, "after a restart only the marker remains: a review")
    }

    @Test
    fun `B-STATE-041 a confirmed or pre-flight-refused online send removes its marker, an unknown outcome keeps it with its reason`() = runTest {
        val w = World(Disk(), this)
        w.queue.holdOnline(prompt("online-a", dispatchedAt = NOW_MS))
        w.queue.holdOnline(prompt("online-b", dispatchedAt = NOW_MS))
        w.queue.holdOnline(prompt("online-c", dispatchedAt = NOW_MS))
        w.queue.finishOnline("online-a", dispatched = true, accepted = true, error = null)
        w.queue.finishOnline("online-b", dispatched = false, accepted = false, error = null)
        w.queue.finishOnline("online-c", dispatched = true, accepted = false, error = QueueErrors.UNCONFIRMED)
        assertEquals(listOf("online-c"), w.restart().queue.entries().map { it.id })
        assertEquals(QueueErrors.UNCONFIRMED, w.entry("online-c")!!.error)
        assertEquals(setOf("prompt online-c"), w.queue.unconfirmedTexts(prompt("x").ref), "the draft never restores it")
    }

    @Test
    fun `B-STATE-062 queued prompts are counted and swept with the profile, or kept with their markers when refused`() = runTest {
        val disk = Disk()
        val w = World(disk, this)
        w.queue.queuePrompt(prompt("a", profile = P2))
        w.queue.holdOnline(prompt("online-x", profile = P1, dispatchedAt = NOW_MS))
        w.queue.queuePrompt(prompt("b", profile = P2).withDispatchedAt(NOW_MS))
        assertEquals(2, w.queue.inspectForRemoval(P2))
        disk.refuseQueueWrites()
        assertIs<Outcome.Failed>(w.queue.sweepProfile(P2))
        assertEquals(NOW_MS, w.restart().entry("b")!!.dispatchedAt, "refused: the marker stays on the device")
        disk.allowAll()
        assertEquals(Outcome.Ok(2), w.queue.sweepProfile(P2))
        assertEquals(listOf("online-x"), w.restart().queue.entries().map { it.id })

        val corrupt = World(Disk.withQueue("{oops"), this)
        assertNull(corrupt.queue.inspectForRemoval(P2), "an unreadable queue is never counted as zero")
        assertIs<Outcome.Failed>(corrupt.queue.sweepProfile(P2))
    }
}
