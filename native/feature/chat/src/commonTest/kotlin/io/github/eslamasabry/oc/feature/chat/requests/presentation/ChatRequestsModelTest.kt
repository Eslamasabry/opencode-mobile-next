package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.QuestionChoice
import io.github.eslamasabry.oc.core.model.QuestionPrompt
import io.github.eslamasabry.oc.core.testing.FakeGatewayEvents
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.core.testing.TestPacer
import io.github.eslamasabry.oc.engine.requests.HELD_ANSWER
import io.github.eslamasabry.oc.engine.requests.RequestsEngine
import io.github.eslamasabry.oc.engine.requests.RequestsPort
import io.github.eslamasabry.oc.engine.requests.ask.AlwaysGrant
import io.github.eslamasabry.oc.engine.requests.ask.AskVerb
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRequestsModelTest {
    private val chat = SessionId("ses_1")
    private val other = SessionId("ses_2")
    private val port = Port()
    private val clock = TestClock()

    private class Port : RequestsPort {
        override val events = FakeGatewayEvents()
        override val capabilities = MutableStateFlow(ServerCapabilities())
        val replies = mutableListOf<String>()
        var permissions: List<PermissionRequest> = emptyList()
        var questions: List<PendingQuestion> = emptyList()
        var failNext: Failure? = null

        override suspend fun awaitActionTransport(): Outcome<Unit> = Unit.ok()

        override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> = permissions.toImmutableList().ok()

        override suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>> = questions.toImmutableList().ok()

        override suspend fun replyPermission(session: SessionId, request: RequestId, reply: PermissionReply, note: String?) =
            record("reply ${request.raw} $reply")

        override suspend fun answerQuestion(session: SessionId, request: RequestId, answers: ImmutableList<ImmutableList<String>>) =
            record("answer ${request.raw} $answers")

        override suspend fun rejectQuestion(session: SessionId, request: RequestId) = record("reject ${request.raw}")

        private fun record(call: String): Outcome<Unit> {
            replies += call
            failNext?.let {
                failNext = null
                return it.failed()
            }
            return Unit.ok()
        }
    }

    private fun TestScope.model(place: RequestPlace = RequestPlace("neon-breaker", "/home/me/neon-breaker")): Pair<RequestsEngine, ChatRequestsModel> {
        val engine = RequestsEngine(port, backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), clock, TestPacer())
        val model = ChatRequestsModel(engine, chat, port.capabilities, MutableStateFlow(place), backgroundScope)
        runCurrent()
        return engine to model
    }

    private fun TestScope.ask(vararg requests: PermissionRequest) {
        port.permissions = port.permissions + requests
        requests.forEach { port.events.emit(GatewayEvent.PermissionAsked(it)) }
        runCurrent()
    }

    private fun shell(id: String, session: SessionId = chat, canAlwaysAllow: Boolean = true) =
        PermissionRequest(
            RequestId(id), session, "shell", persistentListOf("npm install"), JsonObject(emptyMap()),
            persistentListOf("npm install *"), null, canAlwaysAllow, null,
        )

    private fun question(id: String, prompts: Int = 1, custom: Boolean = true) =
        PendingQuestion(
            RequestId(id),
            chat,
            List(prompts) {
                QuestionPrompt("Deploy", "Ship it now?", false, custom, false, persistentListOf(QuestionChoice("Yes", ""), QuestionChoice("No", "")), false)
            }.toImmutableList(),
        )

    private fun TestScope.passHold() {
        advanceTimeBy(HELD_ANSWER + 1.milliseconds)
        runCurrent()
    }

    @Test
    fun `B-CHAT-123 the chat shows its own oldest ask, worded from the request, and how many more wait`() =
        runTest {
            val (_, model) = model()
            ask(shell("p1"), shell("p2", session = other), shell("p3"))

            val slot = model.state.value
            val card = assertIs<RequestCardUi.Permission>(slot.card)
            assertEquals(RequestId("p1"), card.id)
            assertEquals(AskVerb.Run, card.ask.verb)
            assertEquals("npm install", card.ask.name)
            assertEquals("neon⁠-⁠breaker", card.ask.where)
            assertEquals(CardState.Waiting, card.state)
            assertEquals(1, slot.moreWaiting)
            assertNull(card.waitingSinceMs)

            model.shown(RequestId("p1"))
            runCurrent()
            assertEquals(clock.now.toEpochMilliseconds(), (model.state.value.card as RequestCardUi.Permission).waitingSinceMs)
        }

    @Test
    fun `B-CHAT-050 Allow once collapses to Allowed with Undo for 3 s, then sends once`() =
        runTest {
            val (_, model) = model()
            ask(shell("p1"))

            model.allowOnce(RequestId("p1"))
            model.allowOnce(RequestId("p1"))
            model.reject(RequestId("p1"))
            runCurrent()
            assertEquals(CardState.Held(AnswerWords.Allowed), model.state.value.card?.state)

            model.undo(RequestId("p1"))
            runCurrent()
            assertEquals(CardState.Waiting, model.state.value.card?.state)
            passHold()
            assertEquals(emptyList(), port.replies)

            model.reject(RequestId("p1"))
            passHold()
            assertEquals(listOf("reply p1 Reject"), port.replies)
            assertEquals(CardState.Sent(AnswerWords.Rejected), model.state.value.card?.state)

            // The request leaves when it settles; the next card waits until then.
            port.events.emit(GatewayEvent.PermissionSettled(chat, RequestId("p1")))
            runCurrent()
            assertNull(model.state.value.card)
        }

    @Test
    fun `B-CHAT-050 leaving the chat sends the held answer at once`() =
        runTest {
            val (_, model) = model()
            ask(shell("p1"))
            model.allowOnce(RequestId("p1"))

            model.leave()
            assertEquals(listOf("reply p1 Once"), port.replies)
        }

    @Test
    fun `B-CHAT-074 a tap naming another chat's request, or a stale card, never answers`() =
        runTest {
            val (_, model) = model()
            ask(shell("p1"), shell("p2", session = other), shell("p3"))

            model.allowOnce(RequestId("p2"))
            model.allowOnce(RequestId("p3"))
            model.retry(RequestId("p1"))
            model.leave()
            assertEquals(emptyList(), port.replies)
        }

    @Test
    fun `B-AGENT-148 Always allow sits on the card only where the server keeps grants and the request offers one`() =
        runTest {
            val (_, model) = model()
            ask(shell("p1"))
            assertEquals(true, (model.state.value.card as RequestCardUi.Permission).canAlwaysAllow)

            port.capabilities.value = ServerCapabilities(persistentPermissionGrants = false)
            runCurrent()
            assertEquals(false, (model.state.value.card as RequestCardUi.Permission).canAlwaysAllow)
            model.askAlwaysAllow(RequestId("p1"))
            runCurrent()
            assertNull(model.state.value.confirmAlways)

            port.capabilities.value = ServerCapabilities()
            port.events.emit(GatewayEvent.PermissionSettled(chat, RequestId("p1")))
            ask(shell("p2", canAlwaysAllow = false))
            assertEquals(false, (model.state.value.card as RequestCardUi.Permission).canAlwaysAllow)
        }

    @Test
    fun `B-AGENT-148 Always allow opens one confirm naming what, and only the confirm answers always`() =
        runTest {
            val (_, model) = model()
            ask(shell("p1"))

            model.askAlwaysAllow(RequestId("p1"))
            runCurrent()
            assertEquals(AlwaysConfirmUi(RequestId("p1"), AlwaysGrant.Named("npm install")), model.state.value.confirmAlways)
            passHold()
            assertEquals(emptyList(), port.replies)

            model.cancelAlwaysAllow()
            runCurrent()
            assertNull(model.state.value.confirmAlways)
            model.confirmAlwaysAllow()
            passHold()
            assertEquals(emptyList(), port.replies)

            model.askAlwaysAllow(RequestId("p1"))
            model.confirmAlwaysAllow()
            runCurrent()
            assertEquals(CardState.Held(AnswerWords.AlwaysAllowed), model.state.value.card?.state)
            assertNull(model.state.value.confirmAlways)
            passHold()
            assertEquals(listOf("reply p1 Always"), port.replies)
        }

    @Test
    fun `B-CHAT-075 an answer that landed while the confirm was open closes it and confirming sends nothing`() =
        runTest {
            val (_, model) = model()
            ask(shell("p1"), shell("p2"))

            model.askAlwaysAllow(RequestId("p1"))
            port.events.emit(GatewayEvent.PermissionSettled(chat, RequestId("p1")))
            runCurrent()
            assertNull(model.state.value.confirmAlways)
            assertEquals(RequestId("p2"), model.state.value.card?.id)
            model.confirmAlwaysAllow()
            passHold()
            assertEquals(emptyList(), port.replies)

            // Answered once on the card while the confirm was open: the confirm sends nothing more.
            model.askAlwaysAllow(RequestId("p2"))
            runCurrent()
            assertEquals(RequestId("p2"), model.state.value.confirmAlways?.id)
            // Grants switched off while it was open: the confirm closes.
            port.capabilities.value = ServerCapabilities(persistentPermissionGrants = false)
            runCurrent()
            assertNull(model.state.value.confirmAlways)
            port.capabilities.value = ServerCapabilities()
            runCurrent()
            model.allowOnce(RequestId("p2"))
            runCurrent()
            assertNull(model.state.value.confirmAlways)
            model.confirmAlwaysAllow()
            passHold()
            assertEquals(listOf("reply p2 Once"), port.replies)
        }

    @Test
    fun `B-CHAT-072 a one-answer question sends the tapped option, never one it did not offer`() =
        runTest {
            val (engine, model) = model()
            port.questions = listOf(question("q1"))
            engine.refresh()
            runCurrent()
            val card = assertIs<RequestCardUi.Question>(model.state.value.card)
            assertEquals(listOf("Yes", "No"), card.options)
            assertEquals(true, card.oneAnswer)

            model.choose(RequestId("q1"), "Maybe")
            model.choose(RequestId("q1"), "No")
            runCurrent()
            assertEquals(CardState.Held(AnswerWords.Chose("No")), model.state.value.card?.state)
            passHold()
            assertEquals(listOf("answer q1 [[No]]"), port.replies)
        }

    @Test
    fun `B-CHAT-072 Something else keeps its text as the request's draft and sends it as typed`() =
        runTest {
            val (engine, model) = model()
            port.questions = listOf(question("q1"))
            engine.refresh()
            model.openOther(RequestId("q1"))
            model.sendOther(RequestId("q1"))
            model.editOther(RequestId("q1"), "  After lunch ")
            runCurrent()
            assertEquals(OtherAnswerUi("  After lunch "), (model.state.value.card as RequestCardUi.Question).other)
            assertEquals(emptyList(), port.replies)

            model.sendOther(RequestId("q1"))
            model.undo(RequestId("q1"))
            runCurrent()
            assertEquals(OtherAnswerUi("  After lunch "), (model.state.value.card as RequestCardUi.Question).other)
            model.sendOther(RequestId("q1"))
            passHold()
            assertEquals(listOf("answer q1 [[After lunch]]"), port.replies)
        }

    @Test
    fun `B-CHAT-072 a longer question is answered in the sheet, not on the card`() =
        runTest {
            val (engine, model) = model()
            port.questions = listOf(question("q1", prompts = 2))
            engine.refresh()
            runCurrent()
            val card = assertIs<RequestCardUi.Question>(model.state.value.card)
            assertEquals(false, card.oneAnswer)
            assertEquals(emptyList(), card.options)

            model.choose(RequestId("q1"), "Yes")
            model.openSheet(RequestId("q1"))
            runCurrent()
            assertEquals(RequestId("q1"), model.sheetFor.value)
            passHold()
            assertEquals(emptyList(), port.replies)
        }

    @Test
    fun `B-CHAT-076 a refused answer says Not accepted and Try again sends it once more`() =
        runTest {
            val (_, model) = model()
            ask(shell("p1"))
            port.failNext = Failure.Server(500)

            model.allowOnce(RequestId("p1"))
            passHold()
            assertEquals(CardState.NotAccepted(AnswerWords.Allowed), model.state.value.card?.state)

            model.retry(RequestId("p1"))
            runCurrent()
            assertEquals(listOf("reply p1 Once", "reply p1 Once"), port.replies)
            assertEquals(CardState.Sent(AnswerWords.Allowed), model.state.value.card?.state)
            advanceTimeBy(5.seconds)
        }
}
