package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** 09 §4.4. */
class StopTest {
    @Test
    fun `StopByPersonTest a stop notes this app before the call and records stoppedByPerson`() =
        runTest {
            val w = World(Disk(), this)
            val log = mutableListOf<String>()
            w.link.stopLog = log
            val d = w.delivery(stops = Stops(log))
            assertEquals(Outcome.Ok(Unit), d.stop(REF))
            assertEquals(listOf("note", "mark", "link.stop"), log)
        }

    @Test
    fun `B10 Codex chat-bugs 2 an ignored Stop says it asked nothing, a heard one says it asked`() = runTest {
        val w = World(Disk(), this)
        val d = w.delivery()
        d.send(req("hello"))
        testScheduler.runCurrent()
        assertEquals(Outcome.Ok(StopAnswer.Ignored), d.stopTurn(REF))
        assertTrue(w.link.stops.isEmpty())
        w.clock.advance(PromptDelivery.STOP_IGNORE_AFTER_SEND)
        assertEquals(Outcome.Ok(StopAnswer.Asked), d.stopTurn(REF))
        assertEquals(1, w.link.stops.size)
    }

    @Test
    fun `map CHAT-GENUI-40 Stop ignores taps for 800 ms after a send`() = runTest {
        val w = World(Disk(), this)
        val d = w.delivery()
        d.send(req("hello"))
        testScheduler.runCurrent()
        w.clock.advance(PromptDelivery.STOP_IGNORE_AFTER_SEND - 1.milliseconds)
        d.stop(REF)
        assertTrue(w.link.stops.isEmpty(), "a double tap on Send never stops the turn")
        w.clock.advance(2.milliseconds)
        d.stop(REF)
        assertEquals(1, w.link.stops.size)
    }

    @Test
    fun `map CHAT-GENUI-40 Stop calls off a send still waiting, and its words come back`() =
        runTest {
            val w = World(Disk(), this)
            val gate = CompletableDeferred<Unit>()
            w.link.transport = {
                gate.await()
                Outcome.Ok(Unit)
            }
            val d = w.delivery()
            val id = (d.send(req("never mind")) as Outcome.Ok).value
            w.clock.advance(PromptDelivery.STOP_IGNORE_AFTER_SEND)
            d.stop(REF)
            gate.complete(Unit)
            testScheduler.runCurrent()
            assertTrue(w.link.attempts.isEmpty(), "called off before the wire")
            assertTrue(d.pending.value.isEmpty())
            assertTrue(
                w.queue.entries().isEmpty(),
                "its marker is removed: nothing left the device",
            )
            val restored = assertIs<SendOutcome.PutBack>(d.outcomes.value.single())
            assertEquals("never mind", restored.text)
            assertEquals(
                id,
                restored.localId,
                "chat must match Stop restoration to the admitted send",
            )
            assertNull(d.localTurns.value[REF])
        }

    @Test
    fun `map CHAT-GENUI-40 Stop on the wire stops now and again once the server took it`() =
        runTest {
            val w = World(Disk(), this)
            val answer = CompletableDeferred<Unit>()
            val onWire = CompletableDeferred<Unit>()
            w.link.answer = {
                onWire.complete(Unit)
                answer.await()
                FakeLink.Answer.Confirmed
            }
            val d = w.delivery()
            d.send(req("hello"))
            onWire.await()
            w.clock.advance(PromptDelivery.STOP_IGNORE_AFTER_SEND)
            d.stop(REF)
            assertEquals(1, w.link.stops.size, "stopped now")
            answer.complete(Unit)
            testScheduler.runCurrent()
            assertEquals(2, w.link.stops.size, "and again once the server took it")
            assertEquals(1, w.link.delivered.size)
        }

    @Test
    fun `map CHAT-GENUI-40 Stop after this step is not offered yet`() = runTest {
        val w = World(Disk(), this)
        assertIs<Outcome.Failed>(w.delivery().stop(REF, StopMode.AfterThisStep))
        assertTrue(w.link.stops.isEmpty())
    }
}
