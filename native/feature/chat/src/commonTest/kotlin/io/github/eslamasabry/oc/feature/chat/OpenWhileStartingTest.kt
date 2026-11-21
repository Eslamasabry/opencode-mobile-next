package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.label
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
import io.github.eslamasabry.oc.feature.chat.ui.ChatPane
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatPhoneServer
import io.github.eslamasabry.oc.feature.chat.world.ChatServerWait
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorld
import io.github.eslamasabry.oc.feature.chat.world.PhoneServerWait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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
 * Open while starting (owner's phone, release 3.0.0+10030, right after an update in place): a conversation of This
 * phone opened onto "Couldn't open this conversation" with Details `un5`, R8's name of `Failure$Refused`. A refusal that
 * only means "not ready yet", or this phone's server not answering yet, must keep the page loading and open it by
 * itself; only a real failure, or a wait past its limit with nothing in progress, is the failure page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenWhileStartingTest {
    private val ref = ConversationRef(ProfileId("phone"), SessionId("s"))

    private val profile =
        ServerProfile(
            ref.profileId,
            "This phone",
            BackendKind.OpenCode2,
            ServerOrigin("http", "127.0.0.1", 4096),
            ProfileTrust(null, null),
            false,
            null,
        )

    private class Phone(here: Boolean = true) : ChatPhoneServer {
        val wait = MutableStateFlow(PhoneServerWait.None)
        private val runs = here

        override fun runsHere(profile: ProfileId): Boolean = runs

        override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override fun waiting(profile: ProfileId): StateFlow<PhoneServerWait> = wait
    }

    /** The server answers the history read with [failures] first, then with its (empty) history. */
    private class ScriptedHistory(val base: FakeServerGateway, val failures: ArrayDeque<Failure>) : ServerGateway by base {
        var reads = 0

        override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> {
            reads++
            return failures.removeFirstOrNull()?.let { Outcome.Failed(it) } ?: base.messagePage(id, cursor, limit)
        }
    }

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
    fun `OWS-001 a read refused because its connection moved is read again, never the failure page`() = runTest {
        val base = FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20))))
        val gateway = ScriptedHistory(base, ArrayDeque(listOf(Failure.Refused(RefusalReason.StaleScope))))
        val registry = registry(gateway)
        base.events.connect()
        registry.open(profile)
        val world = GraphChatWorld(ref, registry)
        val seen = mutableListOf<ChatLoad>()
        backgroundScope.launch { world.load.collect { seen += it } }
        world.attach()
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        // The refusal never reached the page: it kept loading, read again by itself and opened.
        assertEquals(ChatLoad.Live, world.load.value)
        assertTrue(seen.none { it is ChatLoad.Failed }, "shown: $seen")
        assertTrue(gateway.reads >= 2)
        world.detach()
    }

    @Test
    fun `OWS-002 this phone's server not connected yet keeps the conversation loading and says it is connecting`() = runTest {
        val phone = Phone()
        val world = GraphChatWorld(ref, registry(), phoneServer = phone)
        world.attach()
        runCurrent()
        assertEquals(ChatLoad.Loading, world.load.value)
        assertEquals(ChatServerWait.Connecting, world.serverWait.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(ChatLoad.Loading, world.load.value)
        // A real timeout: nothing in progress for longer than the limit is the failure page, with words for This phone.
        advanceTimeBy(40_000)
        runCurrent()
        val failed = assertIs<ChatLoad.Failed>(world.load.value)
        assertEquals("Network · Offline", failed.failure.label())
        assertNull(world.serverWait.value)
        world.detach()
    }

    @Test
    fun `OWS-003 while this phone's OpenCode updates the conversation waits for it and says so, without a timeout`() = runTest {
        val phone = Phone()
        phone.wait.value = PhoneServerWait.Updating
        val world = GraphChatWorld(ref, registry(), phoneServer = phone)
        world.attach()
        runCurrent()
        assertEquals(ChatServerWait.Updating, world.serverWait.value)
        advanceTimeBy(10 * 60_000)
        runCurrent()
        assertEquals(ChatLoad.Loading, world.load.value)
        phone.wait.value = PhoneServerWait.Starting
        runCurrent()
        assertEquals(ChatServerWait.Starting, world.serverWait.value)
        world.detach()
    }

    @Test
    fun `B-CHAT-308 after the person's own stop the chat says so, never fails or counts down`() = runTest {
        val phone = Phone()
        phone.wait.value = PhoneServerWait.StoppedByPerson
        val world = GraphChatWorld(ref, registry(), phoneServer = phone)
        world.attach()
        runCurrent()
        assertEquals(ChatServerWait.StoppedByPerson, world.serverWait.value)
        // No timeout turns the stop into the failure page, however long it stays stopped.
        advanceTimeBy(10 * 60_000)
        runCurrent()
        assertEquals(ChatLoad.Loading, world.load.value)
        assertEquals(ChatServerWait.StoppedByPerson, world.serverWait.value)
        // Starting it again is a wait like any start.
        phone.wait.value = PhoneServerWait.Starting
        runCurrent()
        assertEquals(ChatServerWait.Starting, world.serverWait.value)
        world.detach()
    }

    @Test
    fun `OWS-006 while this phone's agents update, an agent conversation waits for them and says so`() = runTest {
        // The helper's server is not "this phone's OpenCode" (here = false): it still waits instead of failing.
        val phone = Phone(here = false)
        phone.wait.value = PhoneServerWait.UpdatingAgents
        val world = GraphChatWorld(ref, registry(), phoneServer = phone)
        world.attach()
        runCurrent()
        assertEquals(ChatServerWait.UpdatingAgents, world.serverWait.value)
        advanceTimeBy(10 * 60_000)
        runCurrent()
        assertEquals(ChatLoad.Loading, world.load.value)
        phone.wait.value = PhoneServerWait.None
        runCurrent()
        world.detach()
    }

    @Test
    fun `OWS-004 a computer that is not connected still says so at once`() = runTest {
        val world = GraphChatWorld(ref, registry(), phoneServer = Phone(here = false))
        world.attach()
        runCurrent()
        assertIs<ChatLoad.Failed>(world.load.value)
        assertNull(world.serverWait.value)
        world.detach()
    }

    @Test
    fun `OWS-005 the details fold names the refusal in stable words, not a class name`() {
        assertEquals("Refused · StaleScope", ChatPane.detailsNote(Failure.Refused(RefusalReason.StaleScope)))
        assertEquals("Network · Refused", ChatPane.detailsNote(Failure.Network(NetworkWhy.Refused)))
        assertEquals("Server · HTTP 503", ChatPane.detailsNote(Failure.Server(503)))
    }
}
