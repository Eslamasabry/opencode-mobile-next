package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
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
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Agent-open-flash (owner's phone, build 10059): opening a saved Antigravity conversation while the agents' helper or
 * the agent was still starting showed "Couldn't open this conversation" with Set up Antigravity, which then went away
 * by itself. The helper's dropped socket or error answer reached the chat as ComputerDidNotAnswer, which the wait did
 * not count. Every load the world publishes is recorded: the presenter shows ChatPhase.Failed exactly for
 * ChatLoad.Failed when nothing is kept (ChatPresenter's phase), so a Failed load never published is a page never shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentOpenFlashTest {
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
    fun `AOF-001 the helper's dropped read while it restarts keeps the page waiting, then the conversation opens, never the failure page`() =
        runTest {
            val base = base()
            base.events.connect()
            // Helper down: the history read finds its socket gone (Paseo Disconnected → ComputerDidNotAnswer).
            val gateway = ScriptedHistory(base, ArrayDeque(listOf(Failure.ComputerDidNotAnswer())))
            val seen = mutableListOf<ChatLoad>()
            val world = open(gateway, Agents(), seen)
            assertTrue(seen.none { it is ChatLoad.Failed }, "shown: $seen")
            // Helper up: the next read, by itself, opens it.
            advanceTimeBy(2_500)
            runCurrent()
            assertEquals(ChatLoad.Live, world.load.value)
            assertTrue(seen.none { it is ChatLoad.Failed }, "shown: $seen")
            assertTrue(gateway.reads >= 2)
            world.detach()
        }

    @Test
    fun `AOF-002 a conversation not listed while the helper starts waits, and once it is up a real NotFound shows at once`() =
        runTest {
            val agents = Agents()
            agents.wait.value = PhoneServerWait.Starting
            val gateway = ScriptedHistory(base(), ArrayDeque(listOf<Failure>(Failure.NotFound())), forever = true)
            val seen = mutableListOf<ChatLoad>()
            val world = open(gateway, agents, seen)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(ChatLoad.Loading, world.load.value)
            assertTrue(seen.none { it is ChatLoad.Failed }, "shown while starting: $seen")
            // Fully up: the helper's wait state is None and this connection answered once.
            gateway.base.events.connect()
            agents.wait.value = PhoneServerWait.None
            advanceTimeBy(2_500)
            runCurrent()
            assertEquals(ChatLoad.Failed(Failure.NotFound()), world.load.value)
            world.detach()
        }

    @Test
    fun `AOF-003 an agent that needs a sign-in shows its state at once, with no wait`() = runTest {
        val agents = Agents()
        agents.wait.value = PhoneServerWait.Starting
        val failure = Failure.SignIn(SignInWhy.PasswordRejected)
        val gateway = ScriptedHistory(base(), ArrayDeque(listOf<Failure>(failure)), forever = true)
        val seen = mutableListOf<ChatLoad>()
        val world = open(gateway, agents, seen)
        assertEquals(ChatLoad.Failed(failure), world.load.value)
        world.detach()
    }

    @Test
    fun `AOF-004 a helper that never answers ends the bounded wait in the failure page, which then stays`() = runTest {
        val base = base()
        base.events.connect()
        val gateway =
            ScriptedHistory(base, ArrayDeque(listOf<Failure>(Failure.ComputerDidNotAnswer())), forever = true)
        val seen = mutableListOf<ChatLoad>()
        val world = open(gateway, Agents(), seen)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(ChatLoad.Loading, world.load.value)
        assertTrue(seen.none { it is ChatLoad.Failed }, "shown within the wait: $seen")
        advanceTimeBy(40_000)
        runCurrent()
        assertIs<ChatLoad.Failed>(world.load.value)
        val shownAt = seen.size
        advanceTimeBy(120_000)
        runCurrent()
        assertIs<ChatLoad.Failed>(world.load.value)
        assertEquals(shownAt, seen.size, "the failure page changed by itself: $seen")
        world.detach()
    }

    @Test
    fun `AOF-005 a failure answered right before a success never crosses Loading to Failed to Live`() = runTest {
        val base = base()
        base.events.connect()
        // Fully up, the helper answers a real failure, and a read already on its way succeeds a moment later.
        val gateway = ScriptedHistory(base, ArrayDeque(listOf<Failure>(Failure.UnreadableReply())))
        val seen = mutableListOf<ChatLoad>()
        val world = open(gateway, Agents(), seen)
        advanceTimeBy(100)
        world.reload()
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        assertTrue(seen.none { it is ChatLoad.Failed }, "shown: $seen")
        world.detach()
    }

    @Test
    fun `AOF-006 a real answer from a helper that is fully up shows the failure page and it stays`() = runTest {
        val base = base()
        base.events.connect()
        val gateway =
            ScriptedHistory(base, ArrayDeque(listOf<Failure>(Failure.UnreadableReply())), forever = true)
        val seen = mutableListOf<ChatLoad>()
        val world = open(gateway, Agents(), seen)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(ChatLoad.Failed(Failure.UnreadableReply()), world.load.value)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(ChatLoad.Failed(Failure.UnreadableReply()), world.load.value)
        world.detach()
    }
}
