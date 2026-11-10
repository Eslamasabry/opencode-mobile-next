package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.core.testing.TestPacer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** E06-S01 (one card per request) and E06-S03 (after Stop, approval cards clear) on the requests engine fixture. */
@OptIn(ExperimentalCoroutinesApi::class)
class E06RequestCardsTest {
    private val port = FakeRequestsPort()
    private val clock = TestClock()

    private fun TestScope.engine(): RequestsEngine =
        RequestsEngine(port, backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), clock, TestPacer())
            .also { runCurrent() }

    private fun TestScope.ask(vararg requests: io.github.eslamasabry.oc.core.model.PermissionRequest) {
        port.permissions = port.permissions.filterNot { old -> requests.any { it.id == old.id } } + requests
        requests.forEach { port.events.emit(GatewayEvent.PermissionAsked(it)) }
        runCurrent()
    }

    private fun serverListsAsks() {
        port.capabilities.value =
            ServerCapabilities.Unknown.copy(permissionRequests = true, legacyQuestionRequests = true, persistentPermissionGrants = true)
    }

    private fun RequestsEngine.phaseOf(id: String): AnswerPhase? = pending.value.firstOrNull { it.id == RequestId(id) }?.phase

    private fun RequestsEngine.ids(): List<String> = pending.value.map { it.id.raw }

    @Test
    fun `E06-S01 a chat's requests are all listed in arrival order, not one plus a count`() =
        runTest {
            serverListsAsks()
            val engine = engine()
            ask(permission("p1"), permission("p2", session = S2), permission("p3"))
            port.questions = listOf(question("q1"))
            port.events.emit(GatewayEvent.QuestionAsked(question("q1")))
            runCurrent()
            ask(permission("p4"))

            val mine = SessionRequests.of(engine.pending.value, S1)
            assertEquals(listOf("p1", "p3", "q1", "p4"), mine.all.map { it.id.raw })
            assertEquals("p1", mine.current?.id?.raw)
        }

    @Test
    fun `E06-S01 a question answered elsewhere leaves on its event, the others stay`() =
        runTest {
            serverListsAsks()
            val engine = engine()
            ask(permission("p1"))
            port.events.emit(GatewayEvent.QuestionAsked(question("q1")))
            runCurrent()
            assertEquals(listOf("p1", "q1"), engine.ids())

            // No reread happens here: before E06-S01 the question card waited for the next one (about 40 s).
            port.events.emit(GatewayEvent.QuestionSettled(S1, RequestId("q1")))
            runCurrent()
            assertEquals(listOf("p1"), engine.ids())
        }

    @Test
    fun `E06-S01 answering one card leaves only that card`() =
        runTest {
            serverListsAsks()
            val engine = engine()
            ask(permission("p1"), permission("p2"), permission("p3"))

            val second = engine.pending.value[1]
            assertEquals(Outcome.Ok(Unit), engine.answer(RequestId("p2"), RequestAnswer.Permission(PermissionReply.Once), second))
            advanceTimeBy(HELD_ANSWER + 1.milliseconds)
            runCurrent()
            assertEquals(listOf("reply p2 Once"), port.replies)
            port.events.emit(GatewayEvent.PermissionSettled(S1, RequestId("p2")))
            runCurrent()
            assertEquals(listOf("p1", "p3"), engine.ids())
            assertEquals(AnswerPhase.Waiting, engine.phaseOf("p1"))
            assertEquals(AnswerPhase.Waiting, engine.phaseOf("p3"))
        }

    @Test
    fun `E06-S03 acknowledged Stop marks the chat's cards No longer needed, then they leave after the read hold`() =
        runTest {
            serverListsAsks()
            val engine = engine()
            ask(permission("p1"), permission("p2", session = S2))
            port.events.emit(GatewayEvent.QuestionAsked(question("q1")))
            runCurrent()
            val captured = engine.pending.value.filter { it.sessionId == S1 }

            engine.discardStopped(captured)
            assertEquals(AnswerPhase.NoLongerNeeded, engine.phaseOf("p1"))
            assertEquals(AnswerPhase.NoLongerNeeded, engine.phaseOf("q1"))
            assertEquals(AnswerPhase.Waiting, engine.phaseOf("p2"))
            // Nothing can answer a retired card, not even a notification action.
            assertIs<Outcome.Failed>(engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once)))
            assertIs<Outcome.Failed>(engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once)))

            // P-109: the line stays long enough to read (RETIRED_SHOWN), then the cards leave.
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(AnswerPhase.NoLongerNeeded, engine.phaseOf("p1"))
            advanceTimeBy(RETIRED_SHOWN - 2.seconds + 1.milliseconds)
            runCurrent()
            assertEquals(listOf("p2"), engine.ids())
            engine.refresh() // the server's stale list does not bring them back
            assertEquals(listOf("p2"), engine.ids())
            assertTrue(port.replies.isEmpty())
        }

    @Test
    fun `E06-S03 a run stopped anywhere else retires its asks and drops a held answer`() =
        runTest {
            serverListsAsks()
            val engine = engine()
            ask(permission("p1"), permission("p2"), permission("p3", session = S2))
            engine.answer(RequestId("p2"), RequestAnswer.Permission(PermissionReply.Once))

            port.events.emit(GatewayEvent.RunEnded(S1, RunOutcome.Interrupted(InterruptReason.User)))
            runCurrent()
            assertEquals(AnswerPhase.NoLongerNeeded, engine.phaseOf("p1"))
            assertEquals(AnswerPhase.NoLongerNeeded, engine.phaseOf("p2"))
            assertEquals(AnswerPhase.Waiting, engine.phaseOf("p3"))

            advanceTimeBy(RETIRED_SHOWN + 1.milliseconds)
            runCurrent()
            assertEquals(listOf("p3"), engine.ids())
            // The held "Allow" never reaches the server after the stop.
            advanceTimeBy(HELD_ANSWER)
            runCurrent()
            assertTrue(port.replies.isEmpty())
        }

    @Test
    fun `E06-S03 an aborted run retires its asks, a run that simply ends does not`() =
        runTest {
            serverListsAsks()
            val engine = engine()
            ask(permission("p1"), permission("p2", session = S2))

            port.events.emit(GatewayEvent.RunEnded(S2, RunOutcome.Idle))
            port.events.emit(GatewayEvent.RunEnded(S2, RunOutcome.Failed(MessageErrorKind.ProviderAuth, null, null)))
            runCurrent()
            assertEquals(AnswerPhase.Waiting, engine.phaseOf("p2"))

            port.events.emit(GatewayEvent.RunEnded(S1, RunOutcome.Failed(MessageErrorKind.Aborted, null, null)))
            runCurrent()
            assertEquals(AnswerPhase.NoLongerNeeded, engine.phaseOf("p1"))
        }

    @Test
    fun `E06-S03 a new ask after the stop is a new turn's and keeps waiting`() =
        runTest {
            serverListsAsks()
            val engine = engine()
            ask(permission("p1"))
            port.events.emit(GatewayEvent.RunEnded(S1, RunOutcome.Interrupted(InterruptReason.User)))
            runCurrent()
            ask(permission("p2"))

            advanceTimeBy(RETIRED_SHOWN + 1.milliseconds)
            runCurrent()
            assertEquals(listOf("p2"), engine.ids())
            assertEquals(AnswerPhase.Waiting, engine.phaseOf("p2"))
        }
}
