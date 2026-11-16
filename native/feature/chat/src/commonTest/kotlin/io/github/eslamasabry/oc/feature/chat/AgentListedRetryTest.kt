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
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
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
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorld
import io.github.eslamasabry.oc.feature.chat.world.PhoneServerWait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The helper's own conversation attach signal wakes a waiting open before the fallback timer. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentListedRetryTest {
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

    /** The agents' helper of this phone, as the chat sees it: started by the app, its wait state the app's own. */
    private class Agents : ChatPhoneServer {
        val wait = MutableStateFlow(PhoneServerWait.None)

        override fun runsHere(profile: ProfileId): Boolean = false

        override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override fun isAgentHelper(profile: ProfileId): Boolean = true

        override fun waiting(profile: ProfileId): StateFlow<PhoneServerWait> = wait
    }

    /** The helper answers the history read with [failures] first (the last one for good if [forever]), then history. */
    private class ScriptedHistory(
        val base: FakeServerGateway,
        val failures: ArrayDeque<Failure>,
        val forever: Boolean = false,
    ) : ServerGateway by base {
        var reads = 0

        override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> {
            reads++
            val next = if (forever && failures.size == 1) failures.first() else failures.removeFirstOrNull()
            return next?.let { Outcome.Failed(it) } ?: base.messagePage(id, cursor, limit)
        }
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

    private fun base() = FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20))))

    private suspend fun TestScope.open(
        gateway: ServerGateway,
        agents: Agents,
        seen: MutableList<ChatLoad>,
    ): GraphChatWorld {
        val registry = registry(gateway)
        registry.open(profile)
        val world = GraphChatWorld(ref, registry, phoneServer = agents)
        backgroundScope.launch { world.load.collect { seen += it } }
        world.attach()
        runCurrent()
        return world
    }

    @Test
    fun `B-AGENT-162 a helper attach signal rereads a waiting conversation before the retry tick`() = runTest {
        val gateway = ScriptedHistory(base(), ArrayDeque(listOf(Failure.ComputerDidNotAnswer())))
        val world = open(gateway, Agents(), mutableListOf())
        assertEquals(ChatLoad.Loading, world.load.value)
        val before = gateway.reads
        advanceTimeBy(100)
        gateway.base.events.emit(io.github.eslamasabry.oc.core.domain.events.GatewayEvent.SessionUpserted(
            Session(ref.sessionId, time = SessionTime(idleMs = 20))
        ))
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        assertEquals(before + 1, gateway.reads)
        gateway.base.events.emit(io.github.eslamasabry.oc.core.domain.events.GatewayEvent.SessionUpserted(
            Session(ref.sessionId, time = SessionTime(idleMs = 20))
        ))
        runCurrent()
        assertEquals(before + 1, gateway.reads, "a live conversation does not reread")
        world.detach()
    }

    @Test
    fun `B-AGENT-162 another conversation signal does not retry this one and the timer remains`() = runTest {
        val gateway = ScriptedHistory(base(), ArrayDeque(listOf(Failure.ComputerDidNotAnswer())))
        val world = open(gateway, Agents(), mutableListOf())
        val before = gateway.reads
        advanceTimeBy(100)
        gateway.base.events.emit(io.github.eslamasabry.oc.core.domain.events.GatewayEvent.SessionUpserted(
            Session(SessionId("other"), time = SessionTime(idleMs = 20))
        ))
        runCurrent()
        assertEquals(ChatLoad.Loading, world.load.value)
        assertEquals(before, gateway.reads)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        world.detach()
    }

    @Test
    fun `B-AGENT-162 repeated helper attach signals cannot extend the wait limit`() = runTest {
        val base = base()
        base.events.connect()
        val gateway = ScriptedHistory(base, ArrayDeque(listOf<Failure>(Failure.ComputerDidNotAnswer())), forever = true)
        val world = open(gateway, Agents(), mutableListOf())
        repeat(100) {
            advanceTimeBy(1_000)
            base.events.emit(io.github.eslamasabry.oc.core.domain.events.GatewayEvent.SessionUpserted(
                Session(ref.sessionId, time = SessionTime(idleMs = 20))
            ))
            runCurrent()
        }
        assertIs<ChatLoad.Failed>(world.load.value)
        world.detach()
    }
}
