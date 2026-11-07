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
    fun `B-CHAT-123 the chat shows its own asks first to last, worded from the request`() =
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
            // E06-S01: every ask of this chat has its own card; the other chat's ask is not here.
            assertEquals(listOf("p1", "p3"), slot.cards.map { it.id.raw })
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
            val stale = model.state.value.cardFor(RequestId("p3"))!!
            // p3 is asked again with other contents: the card the person saw is stale.
            ask(shell("p3").copy(patterns = persistentListOf("rm -rf build")))

            model.allowOnce(RequestId("p2"))
            model.performReviewed(stale, ReviewedRequestAction.AllowOnce)
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
            // The confirm also captures the exact request it answers, so compare what it names.
            val confirm = model.state.value.confirmAlways
            assertEquals(RequestId("p1"), confirm?.id)
            assertEquals(AlwaysGrant.Named("npm install"), confirm?.grant)
            assertEquals(RequestId("p1"), confirm?.captured?.id)
            assertEquals(false, confirm?.invitation)
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

    @Test
    fun `E06-S01 three asks and a question show four cards in arrival order, answered in any order`() =
        runTest {
            port.capabilities.value = ServerCapabilities()
            val (_, model) = model()
            ask(shell("p1"))
            port.questions = listOf(question("q1"))
            port.events.emit(GatewayEvent.QuestionAsked(question("q1")))
            runCurrent()
            ask(shell("p2"), shell("p3"))

            val slot = model.state.value
            assertEquals(listOf("p1", "q1", "p2", "p3"), slot.cards.map { it.id.raw })
            assertEquals(listOf(true, false, true, true), slot.cards.map { it is RequestCardUi.Permission })
            assertEquals(List(4) { CardState.Waiting }, slot.cards.map { it.state })

            // The third card first, then the question: each answers only itself.
            model.allowOnce(RequestId("p2"))
            model.choose(RequestId("q1"), "Yes")
            runCurrent()
            val held = model.state.value.cards.associate { it.id.raw to it.state }
            assertEquals(CardState.Held(AnswerWords.Allowed), held["p2"])
            assertEquals(CardState.Held(AnswerWords.Chose("Yes")), held["q1"])
            assertEquals(CardState.Waiting, held["p1"])
            assertEquals(CardState.Waiting, held["p3"])
            passHold()
            assertEquals(setOf("reply p2 Once", "answer q1 [[Yes]]"), port.replies.toSet())

            // The server settles p2: that card alone leaves, on the event.
            port.events.emit(GatewayEvent.PermissionSettled(chat, RequestId("p2")))
            runCurrent()
            assertEquals(listOf("p1", "q1", "p3"), model.state.value.cards.map { it.id.raw })
        }

    @Test
    fun `E06-S01 Always allow confirm and shown age belong to the card that asked`() =
        runTest {
            val (_, model) = model()
            ask(shell("p1"), shell("p2"))

            model.shown(RequestId("p2"))
            model.askAlwaysAllow(RequestId("p2"))
            runCurrent()
            assertEquals(RequestId("p2"), model.state.value.confirmAlways?.id)
            assertNull(model.state.value.cardFor(RequestId("p1"))?.waitingSinceMs)
            assertEquals(clock.now.toEpochMilliseconds(), model.state.value.cardFor(RequestId("p2"))?.waitingSinceMs)
            model.confirmAlwaysAllow()
            passHold()
            assertEquals(listOf("reply p2 Always"), port.replies)
            assertEquals(CardState.Waiting, model.state.value.cardFor(RequestId("p1"))?.state)
        }

    @Test
    fun `E06-S03 after Stop every card says No longer needed, answers nothing, and leaves within 2 s`() =
        runTest {
            val (engine, model) = model()
            ask(shell("p1"), shell("p2"), shell("p3", session = other))
            model.allowOnce(RequestId("p2"))
            runCurrent()

            engine.discardStopped(engine.pending.value.filter { it.sessionId == chat })
            runCurrent()
            assertEquals(
                listOf(CardState.NoLongerNeeded, CardState.NoLongerNeeded),
                model.state.value.cards.map { it.state },
            )
            model.allowOnce(RequestId("p1"))
            model.undo(RequestId("p2"))
            runCurrent()
            assertEquals(listOf(CardState.NoLongerNeeded, CardState.NoLongerNeeded), model.state.value.cards.map { it.state })

            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(emptyList(), model.state.value.cards)
            assertEquals(emptyList(), port.replies)
        }

    @Test
    fun `E06-S03 a run stopped elsewhere clears this chat's cards too`() =
        runTest {
            val (_, model) = model()
            ask(shell("p1"))

            port.events.emit(
                GatewayEvent.RunEnded(
                    chat,
                    io.github.eslamasabry.oc.core.domain.events.RunOutcome.Interrupted(
                        io.github.eslamasabry.oc.core.domain.events.InterruptReason.User
                    ),
                )
            )
            runCurrent()
            assertEquals(CardState.NoLongerNeeded, model.state.value.card?.state)
            advanceTimeBy(2.seconds)
            runCurrent()
            assertNull(model.state.value.card)
        }
}
