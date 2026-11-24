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
import io.github.eslamasabry.oc.feature.chat.world.ChatConnectionOpener
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatPhoneServer
import io.github.eslamasabry.oc.feature.chat.world.ChatServerWait
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorld
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.PhoneServerWait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Try again in a chat reconnects a computer that went away. With no open connection to it, Try again used to only
 * read again and show the same failure; only the home's Try again opened a connection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatReopenConnectionTest {
    private val ref = ConversationRef(ProfileId("computer"), SessionId("s"))

    private val profile =
        ServerProfile(
            ref.profileId,
            "Computer",
            BackendKind.OpenCode2,
            ServerOrigin("https", "computer.example", 443),
            ProfileTrust(null, null),
            false,
            null,
        )

    /** This phone's own server, as the chat sees it: the app starts it, so Try again never connects it. */
    private class Phone : ChatPhoneServer {
        override fun runsHere(profile: ProfileId): Boolean = true

        override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)
    }

    /** The agents' helper of this phone, as the chat sees it (the shape of AgentListedRetryTest's Agents). */
    private class Agents : ChatPhoneServer {
        val wait = MutableStateFlow(PhoneServerWait.None)

        override fun runsHere(profile: ProfileId): Boolean = false

        override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override fun isAgentHelper(profile: ProfileId): Boolean = true

        override fun waiting(profile: ProfileId): StateFlow<PhoneServerWait> = wait
    }

    /**
     * The chat's way to open a computer's connection: it records the call, waits for its gate, then opens the
     * connection for real, or answers the failure it was given.
     */
    private class Opener(
        private val registry: ConnectionRegistry,
        private val server: ServerProfile,
    ) : ChatConnectionOpener {
        val opens = ArrayList<ProfileId>()
        val holds = ArrayList<Pair<ProfileId, Boolean>>()
        val gate = CompletableDeferred<Unit>()
        var failure: Failure? = null

        override suspend fun open(profile: ProfileId): Outcome<Unit> {
            opens += profile
            gate.await()
            failure?.let { return Outcome.Failed(it) }
            return when (val opened = registry.open(server)) {
                is Outcome.Ok -> Outcome.Ok(Unit)
                is Outcome.Failed -> opened
            }
        }

        override fun hold(profile: ProfileId, held: Boolean) {
            holds += profile to held
        }
    }

    /** A real registry whose one backend connects [gateway] (null: no backend at all). */
    private fun TestScope.registry(gateway: ServerGateway? = null): ConnectionRegistry {
        val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
        val app = AppCoroutineScope(backgroundScope.coroutineContext)
        val clock = TestClock()
        val factories =
            if (gateway == null) emptySet()
            else
                setOf(
                    object : GatewayFactory {
                        override val backend = BackendKind.OpenCode2

                        override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
                            Outcome.Failed(Failure.Network(NetworkWhy.Refused))

                        override suspend fun connect(spec: GatewaySpec) =
                            Outcome.Ok(GatewayPair(gateway, FakeOperationsGateway()))
                    }
                )
        return ConnectionRegistry(
            factories,
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

    @Test
    fun `B-CHAT-422 Try again opens a computer's connection that is not open`() = runTest {
        val registry = registry(FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20)))))
        val opener = Opener(registry, profile)
        val world = GraphChatWorld(ref, registry, connectionOpener = opener)
        world.attach()
        runCurrent()
        assertIs<ChatLoad.Failed>(world.load.value)
        backgroundScope.launch { world.reload() }
        runCurrent()
        assertEquals(ChatLoad.Loading, world.load.value)
        assertEquals(ChatServerWait.Connecting, world.serverWait.value)
        backgroundScope.launch { world.reload() }
        runCurrent()
        assertEquals(listOf(ref.profileId), opener.opens, "a second tap while connecting opens nothing more")
        opener.gate.complete(Unit)
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        assertNull(world.serverWait.value)
        world.detach()
    }

    @Test
    fun `B-CHAT-423 a computer still away shows its failure, not an endless wait`() = runTest {
        val registry = registry()
        val opener = Opener(registry, profile).apply { failure = Failure.TimedOut() }
        opener.gate.complete(Unit)
        val world = GraphChatWorld(ref, registry, connectionOpener = opener)
        world.attach()
        runCurrent()
        world.reload()
        runCurrent()
        assertEquals(Failure.TimedOut(), assertIs<ChatLoad.Failed>(world.load.value).failure)
        assertEquals(ChatLink.Offline, world.link.value)
        assertNull(world.serverWait.value)
        world.detach()
    }

    @Test
    fun `B-CHAT-424 this phone's server and the agents' helper never use the opener`() = runTest {
        val registry = registry()
        val opener = Opener(registry, profile)
        val phone = GraphChatWorld(ref, registry, phoneServer = Phone(), connectionOpener = opener)
        phone.attach()
        runCurrent()
        phone.reload()
        phone.detach()
        val helper = GraphChatWorld(ref, registry, phoneServer = Agents(), connectionOpener = opener)
        helper.attach()
        runCurrent()
        helper.reload()
        helper.detach()
        assertEquals(emptyList<ProfileId>(), opener.opens)
    }

    @Test
    fun `B-CHAT-425 an open chat holds its computer until it closes`() = runTest {
        val registry = registry()
        val opener = Opener(registry, profile)
        val worlds = GraphChatWorlds(registry, connectionOpeners = setOf(opener))
        val first = worlds.open(ref)
        val second = worlds.open(ref)
        worlds.release(first)
        worlds.release(second)
        assertEquals(listOf(ref.profileId to true, ref.profileId to false), opener.holds)
    }

    @Test
    fun `B-CHAT-427 a second Try again while connecting keeps the Connecting line`() = runTest {
        val registry = registry(FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20)))))
        val opener = Opener(registry, profile)
        val world = GraphChatWorld(ref, registry, connectionOpener = opener)
        world.attach()
        runCurrent()
        backgroundScope.launch { world.reload() }
        runCurrent()
        backgroundScope.launch { world.reload() }
        runCurrent()
        assertEquals(ChatServerWait.Connecting, world.serverWait.value)
        assertEquals(ChatLoad.Loading, world.load.value)
        assertEquals(listOf(ref.profileId), opener.opens)
        opener.gate.complete(Unit)
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        world.detach()
    }
}
