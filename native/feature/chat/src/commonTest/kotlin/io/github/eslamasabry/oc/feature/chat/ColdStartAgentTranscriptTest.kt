package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ConnectionSession
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.requests.ConnectionRequests
import io.github.eslamasabry.oc.engine.transcript.KeptTranscript
import io.github.eslamasabry.oc.engine.transcript.TranscriptAccessors
import io.github.eslamasabry.oc.engine.transcript.TranscriptEngine
import io.github.eslamasabry.oc.engine.transcript.TranscriptHandle
import io.github.eslamasabry.oc.engine.transcript.detachedTranscript
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatLastKnown
import io.github.eslamasabry.oc.feature.chat.world.ChatPhoneServer
import io.github.eslamasabry.oc.feature.chat.world.ChatServerWait
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorld
import io.github.eslamasabry.oc.feature.chat.world.LaunchStage
import io.github.eslamasabry.oc.feature.chat.world.PhoneServerWait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * B-486: opening an existing phone-agent conversation during cold start (before the helper is
 * connected) must show its saved messages right away — never a blank wall waiting for the helper
 * to boot, even if Android reaped the app mid-reply.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ColdStartAgentTranscriptTest {
    private val ref = ConversationRef(ProfileId("phone-agents"), SessionId("claude-1"))

    private class StartingHelper : ChatPhoneServer {
        val stages = MutableStateFlow<LaunchStage?>(LaunchStage.Helper)
        val waits = MutableStateFlow(PhoneServerWait.Starting)
        var wantedCount = 0

        override fun runsHere(profile: ProfileId): Boolean = false

        override fun isAgentHelper(profile: ProfileId): Boolean = true

        override fun wantAgentHelper(profile: ProfileId) {
            wantedCount++
        }

        override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override fun waiting(profile: ProfileId): StateFlow<PhoneServerWait> = waits

        override fun stage(profile: ProfileId): StateFlow<LaunchStage?> = stages
    }

    private class MemoryKept(
        val dispatchers: AppDispatchers,
        val clock: TestClock,
        val scope: CoroutineScope,
    ) : ChatLastKnown {
        var page: KeptTranscript? = null

        override fun peek(ref: ConversationRef): KeptTranscript? = page

        override suspend fun read(ref: ConversationRef): KeptTranscript? = page

        override suspend fun keep(
            ref: ConversationRef,
            title: String?,
            messages: List<MessageWithParts>,
        ) {
            page = KeptTranscript.fit(title, messages, 512 * 1024)
        }

        override fun detached(ref: ConversationRef): TranscriptHandle =
            detachedTranscript(ref.sessionId.raw, dispatchers, clock, scope)
    }

    private fun coldRegistry(
        test: TestScope,
        dispatchers: TestDispatchers,
        clock: TestClock,
    ): ConnectionRegistry {
        val base = FakeServerGateway(emptyList())
        return ConnectionRegistry(
            setOf(
                object : GatewayFactory {
                    override val backend = BackendKind.OpenCode2

                    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
                        Outcome.Failed(Failure.Network(NetworkWhy.Refused))

                    override suspend fun connect(spec: GatewaySpec) =
                        Outcome.Ok(GatewayPair(base, FakeOperationsGateway()))
                }
            ),
            { p, pair, scope ->
                object : ConnectionGraph, ChatGraphAccessors, TranscriptAccessors {
                    override val profile = p
                    override val scope = scope
                    override val session = ConnectionSession(p, pair.live, scope)
                    override val chatDelivery =
                        ConnectionDelivery(pair.live, p, scope, clock, emptySet(), dispatchers)
                    override val chatRequests = ConnectionRequests(pair.live, scope, dispatchers, clock)
                    override val chatGateway = pair.live
                    override val chatOperations = pair.operations
                    override val transcriptEngine = TranscriptEngine(pair.live, dispatchers, clock, scope)
                }
            },
            AppCoroutineScope(test.backgroundScope.coroutineContext),
            dispatchers,
        )
    }

    private fun savedMessages(completed: Boolean): List<MessageWithParts> =
        listOf(
            MessageWithParts(
                MessageInfo("u1", ref.sessionId.raw, Role.User, providerId = "claude-code", time = MsgTime(10, 11)),
                listOf(Part("p1", "u1", ref.sessionId.raw, PartType.Text, "text", text = "Fix the build"))
                    .toImmutableList(),
            ),
            MessageWithParts(
                MessageInfo(
                    "a1",
                    ref.sessionId.raw,
                    Role.Assistant,
                    providerId = "claude-code",
                    time = MsgTime(12, if (completed) 15 else null),
                    finish = if (completed) "stop" else null,
                ),
                listOf(Part("p2", "a1", ref.sessionId.raw, PartType.Text, "text", text = "Checking the Gradle config."))
                    .toImmutableList(),
            ),
        )

    @Test
    fun `B-CHAT-486 cold-start open of an agent chat kept mid-reply shows saved messages at once`() =
        runTest {
            val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
            val clock = TestClock()
            val registry = coldRegistry(this, dispatchers, clock)
            val helper = StartingHelper()
            val kept = MemoryKept(dispatchers, clock, backgroundScope)
            kept.page = KeptTranscript.fit("Fix the build", savedMessages(completed = false), 512 * 1024)

            val world = GraphChatWorld(ref, registry, phoneServer = helper, lastKnown = kept)
            val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
            world.attach()
            runCurrent()

            assertTrue(
                world.transcript.value.rows.isNotEmpty(),
                "saved messages must show at once on cold start without waiting for KEPT_MID_REPLY_WAIT_MS",
            )
            assertEquals(ChatPhase.Live, presenter.current().phase)
            assertEquals(ChatServerWait.StartingHelper, world.serverWait.value)
            assertEquals(1, helper.wantedCount, "opening asks the helper to start")
            world.detach()
        }

    @Test
    fun `B-CHAT-486 cold-start open of a completed agent chat shows saved messages while helper boots`() =
        runTest {
            val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
            val clock = TestClock()
            val registry = coldRegistry(this, dispatchers, clock)
            val helper = StartingHelper()
            val kept = MemoryKept(dispatchers, clock, backgroundScope)
            kept.page = KeptTranscript.fit("Fix the build", savedMessages(completed = true), 512 * 1024)

            val world = GraphChatWorld(ref, registry, phoneServer = helper, lastKnown = kept)
            val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
            world.attach()
            runCurrent()

            assertTrue(world.transcript.value.rows.isNotEmpty(), "saved messages draw immediately")
            assertEquals(ChatPhase.Live, presenter.current().phase)
            assertEquals(ChatServerWait.StartingHelper, world.serverWait.value)
            world.detach()
        }
}
