package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
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
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ConnectionSession
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.requests.ConnectionRequests
import io.github.eslamasabry.oc.engine.transcript.TranscriptAccessors
import io.github.eslamasabry.oc.engine.transcript.TranscriptEngine
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatPhoneServer
import io.github.eslamasabry.oc.feature.chat.world.ChatServerWait
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorld
import io.github.eslamasabry.oc.feature.chat.world.LaunchStage
import io.github.eslamasabry.oc.feature.chat.world.PhoneServerWait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Stage words (owner: "get the customer the peek of what's happening in backend"): while a phone agent's conversation
 * is getting ready, the page's waiting line names the real current stage the app's signals report, never a guess.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StageWordsWorldTest {
    private val ref = ConversationRef(ProfileId("phone-agents"), SessionId("s"))

    private val profile =
        ServerProfile(
            ref.profileId,
            "This phone",
            BackendKind.OpenCode2,
            ServerOrigin("http", "127.0.0.1", 4099),
            ProfileTrust(null, null),
            false,
            null,
        )

    /** The agents' helper as the chat sees it, with its stage flow driven by the test. */
    private class Agents : ChatPhoneServer {
        val stages = MutableStateFlow<LaunchStage?>(null)

        override fun runsHere(profile: ProfileId): Boolean = false

        override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override fun isAgentHelper(profile: ProfileId): Boolean = true

        override fun waiting(profile: ProfileId): StateFlow<PhoneServerWait> = MutableStateFlow(PhoneServerWait.None)

        override fun stage(profile: ProfileId): StateFlow<LaunchStage?> = stages
    }

    private fun TestScope.registry(gateway: ServerGateway): ConnectionRegistry {
        val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
        val app = AppCoroutineScope(backgroundScope.coroutineContext)
        val clock = TestClock()
        return ConnectionRegistry(
            setOf(
                object : GatewayFactory {
                    override val backend = BackendKind.OpenCode2

                    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
                        Outcome.Failed(Failure.Network(NetworkWhy.Refused))

                    override suspend fun connect(spec: GatewaySpec) =
                        Outcome.Ok(GatewayPair(gateway, FakeOperationsGateway()))
                }
            ),
            { p, pair, scope ->
                object : ConnectionGraph, ChatGraphAccessors, TranscriptAccessors {
                    override val profile = p
                    override val scope = scope
                    override val session = ConnectionSession(p, pair.live, scope)
                    override val chatDelivery = ConnectionDelivery(pair.live, p, scope, clock, emptySet(), dispatchers)
                    override val chatRequests = ConnectionRequests(pair.live, scope, dispatchers, clock)
                    override val chatGateway = pair.live
                    override val chatOperations = pair.operations
                    override val transcriptEngine = TranscriptEngine(pair.live, dispatchers, clock, scope)
                }
            },
            app,
            dispatchers,
        )
    }

    private fun gateway() = FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20))))

    @Test
    fun `SW-1 the line names each real stage in order as the signals come`() = runTest {
        val agents = Agents()
        val world = GraphChatWorld(ref, registry(gateway()), phoneServer = agents)
        world.attach()
        runCurrent()
        agents.stages.value = LaunchStage.Linux
        runCurrent()
        assertEquals(ChatServerWait.StartingLinux, world.serverWait.value)
        advanceTimeBy(600)
        agents.stages.value = LaunchStage.Helper
        runCurrent()
        assertEquals(ChatServerWait.StartingHelper, world.serverWait.value)
        advanceTimeBy(600)
        agents.stages.value = LaunchStage.Agent
        runCurrent()
        assertEquals(ChatServerWait.StartingAgent, world.serverWait.value)
        world.detach()
    }

    @Test
    fun `SW-2 a stage holds about half a second before the next shows, and the newest one wins`() = runTest {
        val agents = Agents()
        val world = GraphChatWorld(ref, registry(gateway()), phoneServer = agents)
        world.attach()
        runCurrent()
        agents.stages.value = LaunchStage.Linux
        runCurrent()
        advanceTimeBy(100)
        agents.stages.value = LaunchStage.Helper
        runCurrent()
        advanceTimeBy(100)
        agents.stages.value = LaunchStage.Agent
        runCurrent()
        assertEquals(ChatServerWait.StartingLinux, world.serverWait.value, "the first still holds at 200 ms")
        advanceTimeBy(250)
        runCurrent()
        assertEquals(ChatServerWait.StartingLinux, world.serverWait.value, "and at 450 ms")
        advanceTimeBy(100)
        runCurrent()
        assertEquals(ChatServerWait.StartingAgent, world.serverWait.value, "after the hold the newest stage shows")
        world.detach()
    }

    @Test
    fun `SW-3 guard no stage signal keeps the generic words`() = runTest {
        val agents = Agents()
        val world = GraphChatWorld(ref, registry(gateway()), phoneServer = agents)
        world.attach()
        runCurrent()
        assertEquals(ChatServerWait.Connecting, world.serverWait.value)
        world.detach()
    }

    @Test
    fun `SW-4 guard the page opening ends the line at once, a held stage never delays it`() = runTest {
        val gateway = gateway()
        val registry = registry(gateway)
        val agents = Agents()
        val world = GraphChatWorld(ref, registry, phoneServer = agents)
        world.attach()
        runCurrent()
        agents.stages.value = LaunchStage.Helper
        runCurrent()
        assertEquals(ChatServerWait.StartingHelper, world.serverWait.value)
        gateway.events.connect()
        registry.open(profile)
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        assertNull(world.serverWait.value, "the line is gone with the page, whatever the stage says")
        world.detach()
    }
}
