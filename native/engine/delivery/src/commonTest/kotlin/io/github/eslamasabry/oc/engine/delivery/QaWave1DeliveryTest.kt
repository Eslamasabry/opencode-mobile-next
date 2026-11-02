package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay

/**
 * QA wave 1 (docs/native/qa/wave1): Oc2Gateway publishes `status = Connected` and then `connects += 1` as two
 * separate updates, and [ConnectionDelivery] wraps the same gateway in a new link for each. A send or a Stop that
 * suspended across that flicker was fenced as "another server" and nothing reached the wire.
 */
class QaWave1DeliveryTest {
    /** The stream flickers while the server is answering the model choice (a real suspension point). */
    private class FlickerServer(val fake: FakeServerGateway = FakeServerGateway(listOf(Session(S1)))) :
        ServerGateway by fake, SessionSelectionGateway {
        var flickerOnSelect = false
        var flickerOnTransport = false

        private suspend fun flicker() {
            fake.events.status.value = StreamStatus.Reconnecting(1, 0)
            delay(1)
            fake.events.status.value = StreamStatus.Connected
            delay(1)
            fake.events.connects.value += 1
            delay(1)
        }

        override suspend fun awaitActionTransport(): Outcome<Unit> {
            if (flickerOnTransport) flicker()
            return Outcome.Ok(Unit)
        }

        override suspend fun setSessionModel(session: SessionId, model: ModelRef, variant: String): Outcome<Unit> {
            if (flickerOnSelect) flicker()
            fake.sessionList =
                persistentListOf(
                    Session(S1, selection = SessionSelection(model, variant, null, modelKnown = true, agentKnown = true)),
                )
            return Outcome.Ok(Unit)
        }

        override suspend fun setSessionAgent(session: SessionId, agent: String): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun createSelectedSession(defaults: SessionSelection): Outcome<Session> =
            fake.createSession(io.github.eslamasabry.oc.core.model.NewSession())
    }

    private fun TestScope.delivery(server: FlickerServer, clock: TestClock): ConnectionDelivery =
        ConnectionDelivery(
            gateway = server,
            profile =
                ServerProfile(
                    P1,
                    "local",
                    BackendKind.OpenCode2,
                    ServerOrigin("https", "example.invalid", 443),
                    ProfileTrust(null, null),
                    managedOnPhone = false,
                    username = null,
                ),
            scope = ConnectionCoroutineScope(backgroundScope.coroutineContext),
            clock = clock,
            stores = setOf(DeliveryStorage(kv(Disk(), this), DefaultWriterLanes(), ProfileDirectory { true })),
            dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
        )

    @Test
    fun `B2 QA wave1 a stream flicker during the first send still sends it exactly once`() = runTest {
        val server = FlickerServer()
        server.fake.events.connect()
        val d = delivery(server, TestClock(Instant.fromEpochMilliseconds(NOW_MS)))
        testScheduler.runCurrent()
        server.flickerOnSelect = true
        val choice = ModelSelection(ModelRef("zai-coding-plan", "glm-5.3-flash"), null)
        assertIs<Outcome.Ok<*>>(d.send(REF, "hello", model = choice))
        // Background work's delays do not count as busy for advanceUntilIdle: step the clock.
        repeat(10) {
            testScheduler.advanceTimeBy(5)
            testScheduler.runCurrent()
        }
        assertEquals(1, server.fake.sentPrompts.size, "the prompt reached the wire once: ${server.fake.calls}")
        assertIs<SendOutcome.Accepted>(d.engine!!.outcomes.value.single())
    }

    @Test
    fun `B10 QA wave1 Stop during a stream flicker still interrupts the running turn`() = runTest {
        val server = FlickerServer()
        server.fake.events.connect()
        val clock = TestClock(Instant.fromEpochMilliseconds(NOW_MS))
        val d = delivery(server, clock)
        testScheduler.runCurrent()
        d.running(REF, busy = true)
        clock.advance(6.seconds)
        server.flickerOnTransport = true
        assertEquals(Outcome.Ok(Unit), d.engine!!.stop(REF))
        assertEquals(1, server.fake.calls.count { it == "stop ${S1.raw}" }, "one interrupt request: ${server.fake.calls}")
    }
}
