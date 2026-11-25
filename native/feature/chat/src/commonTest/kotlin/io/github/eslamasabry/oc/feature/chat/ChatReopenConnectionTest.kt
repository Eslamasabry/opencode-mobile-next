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
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
        val chance = MutableStateFlow(true)
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

        override suspend fun awaitChance(profile: ProfileId) {
            chance.first { it }
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

    @Test
    fun `B-CHAT-428 a chat closed while Try again connects shows its failure when reopened`() = runTest {
        val registry = registry()
        val opener = Opener(registry, profile)
        val world = GraphChatWorld(ref, registry, connectionOpener = opener)
        world.attach()
        runCurrent()
        backgroundScope.launch { world.reload() }
        runCurrent()
        world.detach()
        opener.failure = Failure.TimedOut()
        opener.gate.complete(Unit)
        runCurrent()
        world.attach()
        runCurrent()
        assertIs<ChatLoad.Failed>(world.load.value)
        assertEquals(ChatLink.Offline, world.link.value)
        world.detach()
    }

    @Test
    fun `B-CHAT-429 a computer that went away reconnects on its own, with no tap`() = runTest {
        val registry = registry(FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20)))))
        val opener =
            Opener(registry, profile).apply {
                failure = Failure.TimedOut()
                gate.complete(Unit)
            }
        val world = GraphChatWorld(ref, registry, connectionOpener = opener)
        val loads = mutableListOf<ChatLoad>()
        backgroundScope.launch { world.load.collect { loads += it } }
        world.attach()
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(1, opener.opens.size)
        assertIs<ChatLoad.Failed>(world.load.value)
        assertEquals(ChatLink.Offline, world.link.value)
        assertNull(world.serverWait.value)
        opener.failure = null
        advanceTimeBy(4_001)
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        assertEquals(2, opener.opens.size)
        val afterFailed = loads.dropWhile { it !is ChatLoad.Failed }
        assertTrue(afterFailed.isNotEmpty())
        assertTrue(afterFailed.none { it is ChatLoad.Loading }, "loads: $loads")
        world.detach()
    }

    @Test
    fun `B-CHAT-430 the line says when it tries again`() = runTest {
        val registry = registry(FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20)))))
        val opener = Opener(registry, profile).apply { gate.complete(Unit) }
        val world = GraphChatWorld(ref, registry, clock = TestClock(), connectionOpener = opener)
        world.attach()
        runCurrent()
        assertNotNull(world.linkRetryAtMs.value)
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        assertNull(world.linkRetryAtMs.value)
        world.detach()
    }

    @Test
    fun `B-CHAT-431 no tries while out of sight or offline`() = runTest {
        val registry = registry(FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20)))))
        val opener =
            Opener(registry, profile).apply {
                chance.value = false
                gate.complete(Unit)
            }
        val world = GraphChatWorld(ref, registry, connectionOpener = opener)
        world.attach()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(emptyList<ProfileId>(), opener.opens)
        opener.chance.value = true
        runCurrent()
        assertEquals(1, opener.opens.size)
        world.detach()
    }

    @Test
    fun `B-CHAT-432 a refusal no wait mends stops the tries`() = runTest {
        val registry = registry()
        val rejected = Failure.SignIn(SignInWhy.PasswordRejected)
        val opener =
            Opener(registry, profile).apply {
                failure = rejected
                gate.complete(Unit)
            }
        val world = GraphChatWorld(ref, registry, connectionOpener = opener)
        world.attach()
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(1, opener.opens.size)
        assertEquals(ChatLoad.Failed(rejected), world.load.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, opener.opens.size)
        world.detach()
    }

    @Test
    fun `B-CHAT-433 a reply still running on the computer shows as running after the quiet reconnect`() = runTest {
        val gateway =
            FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20)))).apply {
                statuses = persistentMapOf(ref.sessionId to SessionStatus.Busy)
            }
        val registry = registry(gateway)
        val opener = Opener(registry, profile).apply { gate.complete(Unit) }
        val world = GraphChatWorld(ref, registry, connectionOpener = opener)
        world.attach()
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        assertTrue(world.transcript.value.run.busy)
        world.detach()
    }

    @Test
    fun `B-CHAT-434 a closed chat stops trying`() = runTest {
        val registry = registry()
        val opener = Opener(registry, profile).apply { gate.complete(Unit) }
        val world = GraphChatWorld(ref, registry, connectionOpener = opener)
        world.attach()
        runCurrent()
        world.detach()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(emptyList<ProfileId>(), opener.opens)
    }

    @Test
    fun `B-CHAT-435 this phone's server and the agents' helper never try on their own`() = runTest {
        val registry = registry()
        val opener = Opener(registry, profile).apply { gate.complete(Unit) }
        val phone = GraphChatWorld(ref, registry, phoneServer = Phone(), connectionOpener = opener)
        phone.attach()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        phone.detach()
        val helper = GraphChatWorld(ref, registry, phoneServer = Agents(), connectionOpener = opener)
        helper.attach()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        helper.detach()
        assertEquals(emptyList<ProfileId>(), opener.opens)
    }
}
