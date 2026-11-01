package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.engine.delivery.live.ConversationActivity
import io.github.eslamasabry.oc.engine.delivery.live.LivePhase
import io.github.eslamasabry.oc.engine.delivery.live.LiveTurnRules
import io.github.eslamasabry.oc.engine.delivery.live.RetryFacts
import io.github.eslamasabry.oc.engine.delivery.live.StuckFacts
import io.github.eslamasabry.oc.engine.delivery.live.TurnFacts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** 09 §4.1 rules (map CHAT-GENUI-08, 23, 40, 50). */
class ConversationActivityTest {
    private val t0 = NOW_MS

    @Test
    fun `map CHAT-GENUI-08 rule 1 the local guess runs the line from the moment a send is accepted for dispatch`() {
        val sending = LocalTurn(t0, sending = true)
        assertEquals(LivePhase.Sending, LiveTurnRules.derive(TurnFacts(), sending, t0)?.phase)
        val accepted = LocalTurn(t0, sending = false, acceptedAtMs = t0 + 100)
        assertEquals(LivePhase.WaitingServer, LiveTurnRules.derive(TurnFacts(), accepted, t0 + 200)?.phase)
        assertEquals(LivePhase.WaitingFirstWord, LiveTurnRules.derive(TurnFacts(busy = true, busySinceMs = t0 + 300), accepted, t0 + 400)?.phase)
        assertNull(LiveTurnRules.derive(TurnFacts(), null, t0), "no turn: no line")
    }

    @Test
    fun `map CHAT-GENUI-08 rules 2 and 6 the server's own words win, and an idle without a busy clears the guess`() {
        val local = LocalTurn(t0, sending = false, acceptedAtMs = t0)
        assertNull(LiveTurnRules.derive(TurnFacts(idleAtMs = t0 + 50), local, t0 + 60), "rule 6")
        assertNull(LiveTurnRules.derive(TurnFacts(endedAtMs = t0 + 50), local, t0 + 60), "rule 2")
        val stillSending = LocalTurn(t0, sending = true)
        assertEquals(LivePhase.Sending, LiveTurnRules.derive(TurnFacts(idleAtMs = t0 + 50), stillSending, t0 + 60)?.phase, "an idle never ends a send in flight")
        assertEquals(LivePhase.WaitingServer, LiveTurnRules.derive(TurnFacts(idleAtMs = t0 - 50), local, t0 + 60)?.phase, "an older idle does not count")
    }

    @Test
    fun `map CHAT-GENUI-23 rule 3 a waiting request is WaitingForYou, never a spinner or Thinking`() {
        val f = TurnFacts(busy = true, busySinceMs = t0, waitingRequest = true, thinking = true, runningStep = "Reading README.md")
        val turn = LiveTurnRules.derive(f, null, t0 + 1_000)!!
        assertEquals(LivePhase.WaitingForYou, turn.phase)
        assertNull(turn.step, "the step that asks is not repeated above its card")
    }

    @Test
    fun `map CHAT-GENUI-40 rule 4 a retry counts down only toward a future time, then reads plain Retrying`() {
        val f = TurnFacts(busy = true, busySinceMs = t0, retry = RetryFacts(attempt = 2, nextAtMs = t0 + 5_000, rateLimit = true))
        val counting = LiveTurnRules.derive(f, null, t0 + 1_000)!!
        assertEquals(LivePhase.Retrying, counting.phase)
        assertEquals(t0 + 5_000, counting.retry?.nextAtMs)
        assertEquals(1_000L, LiveTurnRules.nextTickMs(counting, f, t0 + 1_000), "a 1 s ticker while the time is ahead")
        val past = LiveTurnRules.derive(f, null, t0 + 6_000)!!
        assertNull(past.retry?.nextAtMs, "no stuck 0:00")
        assertNull(LiveTurnRules.nextTickMs(past, f, t0 + 6_000), "no ticker once it has passed")
    }

    @Test
    fun `map CHAT-GENUI-50 rule 5 stuck, working and writing phases, and the 8 s slow start`() {
        val stuck = TurnFacts(busy = true, busySinceMs = t0, stuck = StuckFacts("npm test", t0), runningStep = "Running npm test")
        assertEquals(LivePhase.Stuck, LiveTurnRules.derive(stuck, null, t0)?.phase)
        val working = LiveTurnRules.derive(TurnFacts(busy = true, busySinceMs = t0, runningStep = "Reading a.kt", secondary = "line"), null, t0)!!
        assertEquals(LivePhase.Working, working.phase)
        assertEquals("Reading a.kt", working.step)
        assertEquals(LivePhase.Writing, LiveTurnRules.derive(TurnFacts(busy = true, writing = true, thinking = true), null, t0)?.phase)
        assertEquals(LivePhase.Thinking, LiveTurnRules.derive(TurnFacts(busy = true, thinking = true), null, t0)?.phase)
        val local = LocalTurn(t0, sending = true)
        assertEquals(false, LiveTurnRules.derive(TurnFacts(), local, t0 + 8_000)?.slow)
        assertEquals(true, LiveTurnRules.derive(TurnFacts(), local, t0 + 8_001)?.slow)
        assertEquals(false, LiveTurnRules.derive(TurnFacts(busy = true, runningStep = "x"), local.copy(sending = false), t0 + 60_000)?.slow, "slow is for starting phases only")
    }

    @Test
    fun `map CHAT-GENUI-08 the live line follows a real send and flips to slow on virtual time without new facts`() = runTest {
        val w = World(Disk(), this)
        val gate = CompletableDeferred<Unit>()
        w.link.transport = { gate.await(); io.github.eslamasabry.oc.core.common.Outcome.Ok(Unit) }
        val d = w.delivery()
        val facts = MutableStateFlow(TurnFacts())
        val activity = ConversationActivity(d, { facts }, w.clock, backgroundScope)
        val line = activity.liveTurn(REF)
        val seen = mutableListOf<LivePhase?>()
        backgroundScope.launch { line.collect { seen += it?.phase } }
        testScheduler.runCurrent()
        assertNull(line.value)

        d.send(req("hello"))
        testScheduler.runCurrent()
        assertEquals(LivePhase.Sending, line.value?.phase)
        assertEquals(false, line.value?.slow)
        w.clock.advance(9.seconds)
        testScheduler.advanceTimeBy(9.seconds)
        testScheduler.runCurrent()
        assertEquals(true, line.value?.slow, "the 8 s flip happens on the clock alone")

        gate.complete(Unit)
        testScheduler.runCurrent()
        facts.value = TurnFacts(busy = true, busySinceMs = w.clock.now().toEpochMilliseconds(), writing = true)
        testScheduler.runCurrent()
        assertEquals(LivePhase.Writing, line.value?.phase)
        facts.value = TurnFacts(busy = false, idleAtMs = w.clock.now().toEpochMilliseconds() + 1)
        testScheduler.runCurrent()
        assertNull(line.value, "idle ends the line")
        assertTrue(seen.containsAll(listOf(LivePhase.Sending, LivePhase.Writing, null)))
    }
}
