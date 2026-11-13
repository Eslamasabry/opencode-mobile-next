package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.testing.FakeGatewayEvents
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.core.testing.TestPacer
import io.github.eslamasabry.oc.engine.requests.RequestsEngine
import io.github.eslamasabry.oc.engine.requests.RequestsPort
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestsModel
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestPlace
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/**
 * RC1 device pass (2026-11-13, pictures w1-w3): Stop pressed while a permission card waited; a second later the card
 * still said "The agent waits until you answer. Nothing is lost." with live Deny and Allow once beside "Stopping", and
 * it left only after about 4 s. The card retired only when the server confirmed the Stop. Now it follows the chat's
 * own Stop at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopRetiresCardTest {
    private val chat: SessionId = Ref.sessionId
    private val clock = TestClock()

    private class Port : RequestsPort {
        override val events = FakeGatewayEvents()
        override val capabilities = MutableStateFlow(ServerCapabilities())
        val replies = mutableListOf<String>()
        var permissions: List<PermissionRequest> = emptyList()

        override suspend fun awaitActionTransport(): Outcome<Unit> = Unit.ok()

        override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> = permissions.toImmutableList().ok()

        override suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>> = persistentListOf<PendingQuestion>().ok()

        override suspend fun replyPermission(session: SessionId, request: RequestId, reply: PermissionReply, note: String?): Outcome<Unit> {
            replies += "reply ${request.raw} $reply"
            return Unit.ok()
        }

        override suspend fun answerQuestion(session: SessionId, request: RequestId, answers: ImmutableList<ImmutableList<String>>): Outcome<Unit> =
            Unit.ok()

        override suspend fun rejectQuestion(session: SessionId, request: RequestId): Outcome<Unit> = Unit.ok()
    }

    private class World(model: ChatRequestsModel) : FakeChatWorld() {
        override val requests: StateFlow<ChatRequestsModel?> = MutableStateFlow(model)
    }

    private fun write(id: String) =
        PermissionRequest(
            RequestId(id), chat, "edit", persistentListOf("w1.txt"), JsonObject(emptyMap()),
            persistentListOf("*"), null, true, null,
        )

    private class Setup(val port: Port, val model: ChatRequestsModel, val world: World)

    private fun TestScope.setup(): Setup {
        val port = Port()
        val engine = RequestsEngine(port, backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), clock, TestPacer())
        val model = ChatRequestsModel(engine, chat, port.capabilities, MutableStateFlow(RequestPlace("p", "/root/p")), backgroundScope)
        runCurrent()
        val ask = write("perm1")
        port.permissions = listOf(ask)
        port.events.emit(GatewayEvent.PermissionAsked(ask))
        runCurrent()
        val world = World(model)
        world.transcript.value = stateOf(*turn(1).toTypedArray(), run = RunFacts(busy = true))
        return Setup(port, model, world)
    }

    private fun cardState(model: ChatRequestsModel): CardState? = model.state.value.cards.singleOrNull()?.state

    @Test
    fun `RC1-STOP-001 the waiting card retires the moment Stop is pressed, before the server answers`() = runTest {
        val s = setup()
        runCurrent()
        assertEquals(CardState.Waiting, cardState(s.model))
        var resume: Continuation<Unit>? = null
        val now = ChatLauncher { block -> block.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() }) }
        val p = ChatPresenter(s.world, RecordingNavigator(), { block -> now.launch { suspendCoroutine<Unit> { resume = it }; block() } })
        p.stop()
        runCurrent()
        assertEquals(CardState.NoLongerNeeded, cardState(s.model), "no live answers while the run is ending")
        s.model.allowOnce(RequestId("perm1"))
        runCurrent()
        assertTrue(s.port.replies.isEmpty(), "a tap that raced the Stop runs nothing")
        assertNotNull(resume).resumeWith(Result.success(Unit))
        runCurrent()
        assertEquals(CardState.NoLongerNeeded, cardState(s.model), "still ending until the run has stopped")
    }

    @Test
    fun `RC1-STOP-002 a refused Stop gives the card back its answers`() = runTest {
        val s = setup()
        runCurrent()
        s.world.stopResult = Outcome.Failed(Failure.TimedOut())
        var resume: Continuation<Unit>? = null
        val now = ChatLauncher { block -> block.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() }) }
        val p = ChatPresenter(s.world, RecordingNavigator(), { block -> now.launch { suspendCoroutine<Unit> { resume = it }; block() } })
        p.stop()
        runCurrent()
        assertEquals(CardState.NoLongerNeeded, cardState(s.model))
        assertNotNull(resume).resumeWith(Result.success(Unit))
        runCurrent()
        assertEquals(CardState.Waiting, cardState(s.model), "the run goes on, so the card waits again")
    }
}
