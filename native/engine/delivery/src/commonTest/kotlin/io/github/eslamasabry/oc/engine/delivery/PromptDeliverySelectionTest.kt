package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.PromptAttachment
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Online dispatch of a rich request: the model/agent choice is applied before the prompt (plan 09 §4.2, full-composer contract). */
class PromptDeliverySelectionTest {
    private val choice = ModelSelection(ModelRef("anthropic", "claude-sonnet-4-6"), "Think")
    private val files =
        listOf(
            PromptAttachment("text/plain", "notes.txt", "file:///cache/notes.txt"),
            PromptAttachment("image/png", "shot.png", "file:///cache/shot.png"),
        )

    private fun rich(text: String) =
        SendRequest(REF, text, attachments = files.toPersistentList(), model = choice, agent = "build", typedText = text)

    @Test
    fun `B-STATE-042 DLV-11 the choice is applied before the wire and the prompt carries the exact files, once`() =
        runTest {
            val w = World(Disk(), this)
            val gate = CompletableDeferred<Unit>()
            w.link.onSelection = { gate.await() }
            val d = w.delivery()
            assertIs<Outcome.Ok<*>>(d.send(rich("read these")))
            testScheduler.runCurrent()
            assertEquals<List<Pair<ModelSelection?, String?>>>(listOf(choice to "build"), w.link.selections, "the mutation began")
            assertTrue(w.link.attempts.isEmpty(), "nothing is admitted to the wire while the selection is in flight")
            assertEquals(files, d.pending.value.single().attachments, "the bubble holds the exact file snapshot")
            gate.complete(Unit)
            testScheduler.runCurrent()
            assertEquals(1, w.link.selections.size, "one mutation, no retry")
            val sent = w.link.delivered.single()
            assertEquals("read these", sent.prompt.text)
            assertEquals(files, sent.prompt.attachments)
            assertEquals(choice, sent.prompt.model)
            assertEquals("build", sent.prompt.agent)
            assertIs<SendOutcome.Accepted>(d.outcomes.value.single())
        }

    @Test
    fun `B-STATE-042 DLV-11 a failed selection sends no prompt, keeps the words and files, and leaves no unknown receipt`() =
        runTest {
            val w = World(Disk(), this)
            w.link.selectionAnswer = { Failure.Server(status = 500).failed() }
            val d = w.delivery()
            assertIs<Outcome.Ok<*>>(d.send(rich("must not leave")))
            testScheduler.runCurrent()
            assertTrue(w.link.attempts.isEmpty(), "a refused selection never admits a prompt")
            assertEquals(1, w.link.selections.size, "the mutation is not retried")
            val back = assertIs<SendOutcome.PutBack>(d.outcomes.value.single())
            assertEquals("must not leave", back.text)
            assertEquals(files, back.attachments)
            assertTrue(w.queue.entries().isEmpty(), "the marker finished not-dispatched: never a review entry")
            assertTrue(d.pending.value.isEmpty())
            // The retained draft sends exactly once when the person tries again.
            w.link.selectionAnswer = { Outcome.Ok(Unit) }
            assertIs<Outcome.Ok<*>>(d.send(rich("must not leave")))
            testScheduler.runCurrent()
            assertEquals(1, w.link.delivered.size, "still exactly one delivery in total")
            assertEquals(2, w.link.selections.size)
        }

    @Test
    fun `B-STATE-119 DLV-11 a target replaced while the selection ran gets no prompt from either server`() =
        runTest {
            val w = World(Disk(), this)
            val gate = CompletableDeferred<Unit>()
            w.link.onSelection = { gate.await() }
            val d = w.delivery()
            assertIs<Outcome.Ok<*>>(d.send(rich("stale target")))
            testScheduler.runCurrent()
            val other = FakeLink()
            w.connection.link.value = other
            gate.complete(Unit)
            testScheduler.runCurrent()
            assertTrue(w.link.attempts.isEmpty() && other.attempts.isEmpty(), "a changed connection dispatches to neither server")
            assertTrue(other.selections.isEmpty())
            val back = assertIs<SendOutcome.PutBack>(d.outcomes.value.single())
            val reason = assertIs<Failure.Refused>(back.reason)
            assertEquals(RefusalReason.StaleScope, reason.reason)
            assertTrue(w.queue.entries().isEmpty())
        }

    @Test
    fun `B-AGENT-243 DLV-11 Stop during the selection sends no prompt and restores the words and files once`() =
        runTest {
            val w = World(Disk(), this)
            val gate = CompletableDeferred<Unit>()
            w.link.onSelection = { gate.await() }
            val d = w.delivery()
            assertIs<Outcome.Ok<*>>(d.send(rich("wait for me")))
            testScheduler.runCurrent()
            w.clock.advance(PromptDelivery.STOP_IGNORE_AFTER_SEND)
            assertIs<Outcome.Ok<*>>(d.stop(REF))
            gate.complete(Unit)
            testScheduler.runCurrent()
            assertTrue(w.link.attempts.isEmpty(), "no call after Stop")
            val back = assertIs<SendOutcome.PutBack>(d.outcomes.value.single())
            assertEquals("wait for me", back.text)
            assertEquals(files, back.attachments)
            assertTrue(w.queue.entries().isEmpty())
            assertTrue(d.pending.value.isEmpty())
        }

    @Test
    fun `B-STATE-033 DLV-11 an offline rich send captures its exact files and choice in the queue`() =
        runTest {
            val w = World(Disk(), this)
            w.link.connected = false
            val d = w.delivery()
            val sent = d.send(rich("offline words"))
            assertIs<Outcome.Ok<*>>(sent)
            assertIs<SendOutcome.Queued>(d.outcomes.value.single())
            val entry = w.queue.entries().single()
            assertEquals(files.toPersistentList(), entry.attachments, "the immutable snapshot is stored as given")
            assertEquals("anthropic", entry.modelProviderId)
            assertEquals("claude-sonnet-4-6", entry.modelId)
            assertEquals("Think", entry.variant)
            assertEquals("build", entry.agent)
            assertEquals("offline words", entry.text)
        }
}
