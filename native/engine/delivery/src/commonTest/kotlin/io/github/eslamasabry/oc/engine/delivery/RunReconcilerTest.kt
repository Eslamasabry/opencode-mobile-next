package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.domain.RunRecoveryGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEnvelope
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.GatewayEvents
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StreamStatus
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RunReconcilerTest {
    private val sid = SessionId("run")
    private class Events : GatewayEvents {
        override val status = MutableStateFlow<StreamStatus>(StreamStatus.Connected)
        override val connects = MutableStateFlow(1)
        override val all = MutableSharedFlow<GatewayEnvelope>(extraBufferCapacity = 20)
        override val project = all
        override fun retryNow() = Unit
        suspend fun emit(e: GatewayEvent) { all.emit(GatewayEnvelope(e, null, 0)) }
    }
    private inner class Fixture(test: TestScope) {
        val events = Events()
        val visibility = MutableStateFlow(Visibility.Foreground)
        var reads = 0
        var reconnects = 0
        var hang = false
        var answer: Outcome<ImmutableMap<SessionId, SessionStatus>> = Outcome.Ok(persistentMapOf(sid to SessionStatus.Busy))
        val recovery = object : RunRecoveryGateway {
            override suspend fun reconcileRuns(sessions: Set<SessionId>): Outcome<ImmutableMap<SessionId, SessionStatus>> {
                reads++
                if (hang) delay(60_000)
                if (answer is Outcome.Ok && (answer as Outcome.Ok).value[sid] != SessionStatus.Busy)
                    events.emit(GatewayEvent.RunEnded(sid, RunOutcome.Idle))
                return answer
            }
            override fun reconnectAfterRunCheck() { reconnects++ }
        }
        init {
            RunReconciler(recovery, events, visibility, { test.testScheduler.currentTime }, test.backgroundScope,
                StandardTestDispatcher(test.testScheduler))
            test.runCurrent()
        }
    }
    @Test fun `B-435 reconnect settles an idle run whose end was lost without replaying a prompt`() = runTest {
        val f = Fixture(this)
        f.events.emit(GatewayEvent.RunStarted(sid)); runCurrent()
        f.answer = Outcome.Ok(persistentMapOf())
        f.events.status.value = StreamStatus.Reconnecting(1, null); runCurrent()
        f.events.connects.value++; f.events.status.value = StreamStatus.Connected; runCurrent()
        assertEquals(1, f.reads)
        advanceTimeBy(900_001); runCurrent()
        assertEquals(1, f.reads, "idle ends the tier waits")
    }
    @Test fun `B-435 each quiet tier checks once and a busy answer keeps waiting`() = runTest {
        val f = Fixture(this)
        f.events.emit(GatewayEvent.RunStarted(sid)); runCurrent()
        for ((at, count) in listOf(60_001L to 1, 300_001L to 2, 900_001L to 3)) {
            advanceTimeBy(at - testScheduler.currentTime); runCurrent()
            assertEquals(count, f.reads)
        }
        advanceTimeBy(3_600_000); runCurrent()
        assertEquals(3, f.reads); assertEquals(0, f.reconnects)
    }
    @Test fun `B-435 a failed or timed out tier check asks the reconnect policy once`() = runTest {
        for (timeout in listOf(false, true)) {
            val f = Fixture(this)
            f.hang = timeout
            f.answer = Outcome.Failed(Failure.TimedOut())
            f.events.emit(GatewayEvent.RunStarted(sid)); runCurrent()
            advanceTimeBy(70_001); runCurrent()
            assertEquals(1, f.reads); assertEquals(1, f.reconnects)
            f.events.emit(GatewayEvent.RunEnded(sid, RunOutcome.Idle)); runCurrent()
        }
    }
    @Test fun `B-435 hidden app makes no checks and cancels an in flight read`() = runTest {
        val f = Fixture(this)
        f.visibility.value = Visibility.HiddenWatching; runCurrent()
        f.events.emit(GatewayEvent.RunStarted(sid)); runCurrent()
        advanceTimeBy(900_001); runCurrent()
        assertEquals(0, f.reads)
        f.hang = true
        f.visibility.value = Visibility.Foreground; runCurrent()
        assertEquals(1, f.reads, "return coalesces missed tiers with reconnect")
        f.visibility.value = Visibility.HiddenIdle; runCurrent()
        advanceTimeBy(20_000); runCurrent()
        assertEquals(0, f.reconnects, "hiding cancels the request rather than treating it as failure")
    }
}
