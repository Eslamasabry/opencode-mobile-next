package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.core.testing.TestPacer
import io.github.eslamasabry.oc.core.testing.assertTimerTable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsEngineTest {
    private val port = FakeRequestsPort()
    private val clock = TestClock()

    private fun TestScope.engine(scope: CoroutineScope = backgroundScope): RequestsEngine {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return RequestsEngine(port, scope, TestDispatchers(dispatcher), clock, TestPacer()).also { runCurrent() }
    }

    private fun TestScope.ask(vararg requests: io.github.eslamasabry.oc.core.model.PermissionRequest) {
        // The server lists what it asked (a reread finds it), and the stream says so.
        port.permissions = port.permissions.filterNot { old -> requests.any { it.id == old.id } } + requests
        requests.forEach { port.events.emit(GatewayEvent.PermissionAsked(it)) }
        runCurrent()
    }

    private fun RequestsEngine.phaseOf(id: String): AnswerPhase? = pending.value.firstOrNull { it.id == RequestId(id) }?.phase

    @Test
    fun `B-CHAT-123 asked permissions wait one card per chat, oldest first, with how many more wait`() =
        runTest {
            val engine = engine()
            ask(permission("p1"), permission("p2", session = S2), permission("p3"))

            assertEquals(listOf("p1", "p2", "p3"), engine.pending.value.map { it.id.raw })
            val mine = engine.forSession(S1).first()
            assertEquals("p1", mine.current?.id?.raw)
            assertEquals(1, mine.moreWaiting)
            assertEquals(SessionRequests.None, SessionRequests.of(engine.pending.value, io.github.eslamasabry.oc.core.common.ids.SessionId("other")))
            // The same ask again (a replay) keeps one card.
            ask(permission("p1"))
            assertEquals(3, engine.pending.value.size)
        }

    @Test
    fun `B-CHAT-050 an answer is held 3 s, then sent exactly once`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))

            assertEquals(Outcome.Ok(Unit), engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once)))
            assertIs<AnswerPhase.Held>(engine.phaseOf("p1"))
            advanceTimeBy(HELD_ANSWER - 1.milliseconds)
            runCurrent()
            assertEquals(emptyList(), port.replies)

            advanceTimeBy(2.milliseconds)
            runCurrent()
            // B-PROTO-028: Allow once sends the decision "once"; the link is woken first (B-PROTO-048).
            assertEquals(listOf("reply p1 Once"), port.replies)
            assertEquals("wake", port.calls[port.calls.indexOf("reply p1 Once") - 1])
            assertIs<AnswerPhase.Sent>(engine.phaseOf("p1"))

            advanceTimeBy(30.seconds)
            runCurrent()
            assertEquals(1, port.replies.size)
        }

    @Test
    fun `B-CHAT-050 Undo inside the hold sends nothing and the card waits again`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Reject))
            advanceTimeBy(1.seconds)

            assertTrue(engine.undo(RequestId("p1")))
            assertEquals(AnswerPhase.Waiting, engine.phaseOf("p1"))
            advanceTimeBy(10.seconds)
            runCurrent()
            assertEquals(emptyList(), port.replies)

            // A new answer after Undo gets its own full window; the first hold's timer never fires it early.
            engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(emptyList(), port.replies)
            advanceTimeBy(1.seconds + 1.milliseconds)
            runCurrent()
            assertEquals(listOf("reply p1 Once"), port.replies)
            // Undo outside the hold does nothing.
            assertEquals(false, engine.undo(RequestId("p1")))
        }

    @Test
    fun `B-CHAT-050 a second answer during the hold is ignored`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))

            val second = engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Reject))
            assertEquals(Outcome.Failed(Failure.Refused(RefusalReason.AlreadyAnswered)), second)
            advanceTimeBy(HELD_ANSWER + 1.milliseconds)
            runCurrent()
            assertEquals(listOf("reply p1 Once"), port.replies)
            // An answer to something no longer waiting is refused the same way.
            assertIs<Outcome.Failed>(engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once)))
            assertIs<Outcome.Failed>(engine.answer(RequestId("gone"), RequestAnswer.Permission(PermissionReply.Once)))
            assertEquals(1, port.replies.size)
        }

    @Test
    fun `B-CHAT-050 leaving the chat sends its held answers at once, and only its own`() =
        runTest {
            val engine = engine()
            ask(permission("p1"), permission("p2", session = S2))
            engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))
            engine.answer(RequestId("p2"), RequestAnswer.Permission(PermissionReply.Once))

            engine.flushHeld(S1)
            assertEquals(listOf("reply p1 Once"), port.replies)
            assertIs<AnswerPhase.Held>(engine.phaseOf("p2"))
            advanceTimeBy(HELD_ANSWER + 1.milliseconds)
            runCurrent()
            assertEquals(listOf("reply p1 Once", "reply p2 Once"), port.replies)
        }

    @Test
    fun `B-CHAT-074 a notification Allow racing a held in-app answer sends one reply and the first decision wins`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Reject))

            val fromNotification = engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))
            assertEquals(Outcome.Ok(Unit), fromNotification)
            assertEquals(listOf("reply p1 Reject"), port.replies)
            advanceTimeBy(10.seconds)
            runCurrent()
            assertEquals(1, port.replies.size)
        }

    @Test
    fun `B-AGENT-031 the in-app and notification buttons share one in-flight write`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            val gate = CompletableDeferred<Unit>()
            port.replyGate = gate

            val first = async { engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once)) }
            runCurrent()
            assertIs<AnswerPhase.Sending>(engine.phaseOf("p1"))
            val second = async { engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Reject)) }
            // An in-app tap while the write is out is refused, never a second write.
            assertIs<Outcome.Failed>(engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once)))
            runCurrent()
            gate.complete(Unit)

            assertEquals(Outcome.Ok(Unit), first.await())
            assertEquals(Outcome.Ok(Unit), second.await())
            assertEquals(listOf("reply p1 Once"), port.replies)
        }

    @Test
    fun `B-CHAT-075 a request answered on another device leaves, and its held answer is never sent`() =
        runTest {
            val engine = engine()
            ask(permission("p1"), permission("p2"))
            engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))

            port.events.emit(GatewayEvent.PermissionSettled(S1, RequestId("p1")))
            runCurrent()
            assertEquals(listOf("p2"), engine.pending.value.map { it.id.raw })
            advanceTimeBy(HELD_ANSWER + 1.milliseconds)
            runCurrent()
            assertEquals(emptyList(), port.replies)
        }

    @Test
    fun `B-CHAT-075 a request no longer listed after a reconnect refetch leaves the count`() =
        runTest {
            val engine = engine()
            ask(permission("p1"), permission("p2"))
            port.permissions = listOf(permission("p2"), permission("p3"))

            port.events.connect()
            runCurrent()
            assertEquals(listOf("p2", "p3"), engine.pending.value.map { it.id.raw })
            assertEquals(1, engine.forSession(S1).first().moreWaiting)
        }

    @Test
    fun `B-PROTO-034 asks are never replayed - a reconnect refetches them, and a failed refetch keeps what is known`() =
        runTest {
            val engine = engine()
            port.permissions = listOf(permission("p1"))
            port.events.connect()
            runCurrent()
            assertEquals(listOf("p1"), engine.pending.value.map { it.id.raw })

            port.failNextList = Failure.Network()
            port.permissions = emptyList()
            port.events.connect()
            runCurrent()
            assertEquals(listOf("p1"), engine.pending.value.map { it.id.raw })
        }

    @Test
    fun `B-CHAT-076 answering a request the server already timed out dismisses it with no error`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            port.failNextReply = Failure.NotFound(WireTag("PermissionNotFoundError"), RequestId("p1"))

            engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))
            advanceTimeBy(HELD_ANSWER + 1.milliseconds)
            runCurrent()
            assertEquals(emptyList(), engine.pending.value)

            // Notification Allow on it is handled too.
            assertEquals(Outcome.Ok(Unit), engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once)))
            assertEquals(1, port.replies.size)
        }

    @Test
    fun `B-CHAT-076 a different 404 is a real failure and keeps the request until Try again lands`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            port.failNextReply = Failure.NotFound(WireTag("SessionNotFoundError"), RequestId("other"))

            val outcome = engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))
            assertIs<Outcome.Failed>(outcome)
            val phase = assertIs<AnswerPhase.NotAccepted>(engine.phaseOf("p1"))
            assertEquals(RequestAnswer.Permission(PermissionReply.Once), phase.answer)

            assertEquals(Outcome.Ok(Unit), engine.retry(RequestId("p1")))
            assertEquals(listOf("reply p1 Once", "reply p1 Once"), port.replies)
            assertIs<AnswerPhase.Sent>(engine.phaseOf("p1"))
            // Retry is only for a refused answer.
            assertIs<Outcome.Failed>(engine.retry(RequestId("p1")))
        }

    @Test
    fun `B-PROTO-074 answering an externally resolved permission is idempotent`() =
        runTest {
            val engine = engine()
            assertEquals(Outcome.Ok(Unit), engine.answerNow(RequestId("p9"), RequestAnswer.Permission(PermissionReply.Once)))
            assertEquals(emptyList(), port.replies)
        }

    @Test
    fun `B-PROTO-048 a late failure after remote resolution shows no error`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            port.failNextReply = Failure.Network()
            port.onReply = {
                port.permissions = emptyList()
                port.events.emit(GatewayEvent.PermissionSettled(S1, RequestId("p1")))
            }

            val outcome = engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))
            runCurrent()
            assertEquals(Outcome.Ok(Unit), outcome)
            assertEquals(emptyList(), engine.pending.value)
        }

    @Test
    fun `B-PROTO-048 a refetch that started before a settle never brings the request back`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            port.permissions = listOf(permission("p1"))
            val gate = CompletableDeferred<Unit>()
            port.listGate = gate

            val refetch = async { engine.refresh() }
            runCurrent()
            port.events.emit(GatewayEvent.PermissionSettled(S1, RequestId("p1")))
            runCurrent()
            gate.complete(Unit)
            refetch.await()
            assertEquals(emptyList(), engine.pending.value)
        }

    @Test
    fun `B-CHAT-072 a one-answer question sends the exact chosen label after the hold`() =
        runTest {
            val engine = engine()
            port.questions = listOf(question("q1"))
            engine.refresh()
            val pending = assertIs<PendingRequest.Question>(engine.pending.value.single())
            assertTrue(pending.oneAnswer)

            engine.answer(RequestId("q1"), RequestAnswer.Question(answers("No")))
            advanceTimeBy(HELD_ANSWER + 1.milliseconds)
            runCurrent()
            assertEquals(listOf("answer q1 [[No]]"), port.replies)
            assertTrue(port.calls.contains("pendingQuestions"))
        }

    @Test
    fun `B-CHAT-072 a dismissed question is rejected, never approved, and several prompts are not one-answer`() =
        runTest {
            val engine = engine()
            port.questions = listOf(question("q1"), question("q2", prompts = 2), question("q3", multiple = true))
            engine.refresh()
            val shapes = engine.pending.value.map { (it as PendingRequest.Question).oneAnswer }
            assertEquals(listOf(true, false, false), shapes)

            engine.answer(RequestId("q1"), RequestAnswer.Decline)
            engine.flushHeld(S1)
            assertEquals(listOf("reject q1"), port.replies)
            // A permission answer is never accepted for a question, nor a question answer for a permission.
            assertIs<Outcome.Failed>(engine.answer(RequestId("q2"), RequestAnswer.Permission(PermissionReply.Once)))
        }

    @Test
    fun `B-PROTO-048 QuestionNotFound resolves the question locally`() =
        runTest {
            val engine = engine()
            port.questions = listOf(question("q1"))
            engine.refresh()
            port.failNextReply = Failure.NotFound(WireTag("QuestionNotFoundError"), RequestId("q1"))

            assertEquals(Outcome.Ok(Unit), engine.answerNow(RequestId("q1"), RequestAnswer.Question(answers("Yes"))))
            assertEquals(emptyList(), engine.pending.value)
        }

    @Test
    fun `B-CHAT-072 questions are read only where the server has them`() =
        runTest {
            val engine = engine()
            port.capabilities.value = ServerCapabilities(legacyQuestionRequests = false)
            port.questions = listOf(question("q1"))
            engine.refresh()
            assertEquals(emptyList(), engine.pending.value)
            assertTrue("pendingQuestions" !in port.calls)
        }

    @Test
    fun `B-AGENT-148 Always allow is answered only where the server keeps grants and the request offers one`() =
        runTest {
            val engine = engine()
            ask(permission("p1"), permission("p2", canAlwaysAllow = false))
            val always = RequestAnswer.Permission(PermissionReply.Always)

            assertEquals(
                Outcome.Failed(Failure.Unavailable(FeatureId("persistentPermissionGrants"))),
                engine.answer(RequestId("p2"), always),
            )
            port.capabilities.value = ServerCapabilities(persistentPermissionGrants = false)
            assertIs<Outcome.Failed>(engine.answer(RequestId("p1"), always))

            port.capabilities.value = ServerCapabilities()
            assertEquals(Outcome.Ok(Unit), engine.answer(RequestId("p1"), always))
            engine.flushHeld(S1)
            assertEquals(listOf("reply p1 Always"), port.replies)
        }

    @Test
    fun `B-CHAT-123 the card's age counts from when this phone first showed it and reopening does not restart it`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            assertNull(engine.pending.value.single().firstShownAtMs)

            engine.markShown(RequestId("p1"))
            val first = engine.pending.value.single().firstShownAtMs
            clock.advance(20.seconds)
            engine.markShown(RequestId("p1"))
            assertEquals(first, engine.pending.value.single().firstShownAtMs)
            assertEquals(clock.now.toEpochMilliseconds() - 20_000, first)
            // A new ask carrying the same id keeps the ledger.
            ask(permission("p1", patterns = listOf("npm test")))
            assertEquals(first, engine.pending.value.single().firstShownAtMs)
        }

    @Test
    fun `B-CHAT-050 closing the connection sends held answers rather than losing them`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            engine.answer(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))
            engine.close()
            assertEquals(listOf("reply p1 Once"), port.replies)
        }

    @Test
    fun `B-AGENT-031 req-1 cancelling the initiating caller does not strand the shared write`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            val gate = CompletableDeferred<Unit>()
            port.replyGate = gate
            val first = async {
                engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))
            }
            runCurrent()
            val second = async {
                engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Reject))
            }
            runCurrent()
            first.cancelAndJoin()
            gate.complete(Unit)
            runCurrent()
            val completed = second.isCompleted
            if (!completed) second.cancelAndJoin()
            assertTrue(completed, "the remaining caller must receive the shared result")
            assertEquals(Outcome.Ok(Unit), second.await())
            assertIs<AnswerPhase.Sent>(engine.phaseOf("p1"))
            assertEquals(listOf("reply p1 Once"), port.replies)
        }

    @Test
    fun `B-PROTO-048 req-2 settlement during transport wake resolves both callers without dispatch`() =
        runTest {
            val engine = engine()
            ask(permission("p1"))
            val gate = CompletableDeferred<Unit>()
            port.wakeGate = gate
            val first = async {
                engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once))
            }
            runCurrent()
            val second = async {
                engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Reject))
            }
            runCurrent()
            port.events.emit(GatewayEvent.PermissionSettled(S1, RequestId("p1")))
            runCurrent()
            gate.complete(Unit)
            assertEquals(Outcome.Ok(Unit), first.await())
            assertEquals(Outcome.Ok(Unit), second.await())
            assertEquals(emptyList(), port.replies)
            assertEquals(emptyList(), engine.pending.value)
        }

    @Test
    fun `B-AGENT-031 req-1 connection cancellation completes the shared result as uncertain without replay`() =
        runTest {
            val owner = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
            val engine = engine(owner)
            ask(permission("p1"))
            port.replyGate = CompletableDeferred()
            val caller = async { engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once)) }
            runCurrent()
            owner.cancel()
            runCurrent()
            val completed = caller.isCompleted
            if (!completed) caller.cancelAndJoin()
            assertTrue(completed)
            assertEquals(Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain)), caller.await())
            assertIs<AnswerPhase.NotAccepted>(engine.phaseOf("p1"))
            port.replyGate?.complete(Unit)
            runCurrent()
            assertEquals(1, port.replies.size)
        }

    @Test
    fun `B-PROTO-048 req-2 settlement at claim publication never loses the captured entry`() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val engine = RequestsEngine(port, backgroundScope, TestDispatchers(dispatcher), clock, TestPacer())
            ask(permission("p1"))
            backgroundScope.launch(dispatcher) {
                engine.pending.collect { pending ->
                    if (pending.any { it.phase is AnswerPhase.Sending }) {
                        port.events.emit(GatewayEvent.PermissionSettled(S1, RequestId("p1")))
                    }
                }
            }
            assertEquals(Outcome.Ok(Unit), engine.answerNow(RequestId("p1"), RequestAnswer.Permission(PermissionReply.Once)))
            assertEquals(emptyList(), engine.pending.value)
            assertEquals(emptyList(), port.replies)
        }

    @Test
    fun `B-PROTO-048 req-3 project snapshots never admit or tombstone another projects asks`() =
        runTest {
            val engine = engine()
            port.events.currentDirectory = "/project-a"
            port.permissions = listOf(permission("a"))
            port.events.emit(GatewayEvent.PermissionAsked(permission("a")), directory = "/project-a")
            port.events.emit(GatewayEvent.PermissionAsked(permission("b", session = S2)), directory = "/project-b")
            runCurrent()
            // Other-project attention belongs to its owner; this engine must not accept an answer for B.
            assertIs<Outcome.Failed>(engine.answer(RequestId("b"), RequestAnswer.Permission(PermissionReply.Once)))
            engine.refresh()
            port.events.currentDirectory = "/project-b"
            port.permissions = listOf(permission("b", session = S2))
            engine.refresh()
            assertEquals(listOf("b"), engine.pending.value.map { it.id.raw })
            assertEquals(Outcome.Ok(Unit), engine.answer(RequestId("b"), RequestAnswer.Permission(PermissionReply.Once)))
            engine.flushHeld(S2)
            assertEquals(listOf("reply b Once"), port.replies)
        }

    @Test
    fun `PB-42 the requests timer table is valid`() = assertTimerTable(RequestsTimers.ALL)
}
