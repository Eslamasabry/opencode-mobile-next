package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
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
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorlds
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.feature.chat.world.PhoneServerWait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
 * Issue 147, owner's phone (build 10057, updated from 10045): a Claude Code conversation opened from Home read
 * "Connecting" for minutes while the agents' helper was down; nothing started it until the Agents page was opened. Every
 * page that opens a conversation (Home row, All conversations, a notification, the edge light, search) opens it through
 * the one `ChatWorlds.open` → [GraphChatWorld.attach], so the ask is made there, and the wait always ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HelperAutostartTest {
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

    /** The agents' helper of this phone, as the chat sees it: not this phone's OpenCode, started by the app. */
    private class Agents : ChatPhoneServer {
        val wait = MutableStateFlow(PhoneServerWait.None)
        val wanted = mutableListOf<ProfileId>()

        override fun runsHere(profile: ProfileId): Boolean = false

        override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override fun isAgentHelper(profile: ProfileId): Boolean = true

        override fun wantAgentHelper(profile: ProfileId) {
            wanted += profile
        }

        override fun waiting(profile: ProfileId): StateFlow<PhoneServerWait> = wait
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

    @Test
    fun `I147-HAS-1 a phone agent conversation opened with its helper down asks for its start and opens once it is up`() =
        runTest {
            val gateway = FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20))))
            val registry = registry(gateway)
            val agents = Agents()
            val world = GraphChatWorld(ref, registry, phoneServer = agents)
            world.attach()
            runCurrent()
            assertEquals(listOf(ref.profileId), agents.wanted, "the app's start of the helper was asked for")
            assertEquals(ChatLoad.Loading, world.load.value)
            assertEquals(ChatServerWait.Connecting, world.serverWait.value)
            // The helper started (fake: its connection opens 30 s later); the conversation opens by itself.
            advanceTimeBy(30_000)
            gateway.events.connect()
            registry.open(profile)
            runCurrent()
            advanceTimeBy(2_500)
            runCurrent()
            assertEquals(ChatLoad.Live, world.load.value)
            assertNull(world.serverWait.value)
            world.detach()
        }

    @Test
    fun `I147-HAS-2 an Antigravity conversation is not held by a signed-out Claude Code, and a helper that does not come up ends the wait`() =
        runTest {
            val agents = Agents()
            // Claude Code read "Sign in needed" on the Agents page; the helper itself never answered.
            agents.wait.value = PhoneServerWait.AgentNeedsSignIn
            val world = GraphChatWorld(ref, registry(FakeServerGateway(emptyList())), phoneServer = agents)
            world.attach()
            runCurrent()
            assertEquals(ChatServerWait.AgentNeedsSignIn, world.serverWait.value)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(ChatLoad.Loading, world.load.value)
            // The start budget is spent: the failure page (its line offers Restart Claude Code), never waiting on.
            advanceTimeBy(40_000)
            runCurrent()
            val failed = assertIs<ChatLoad.Failed>(world.load.value)
            assertEquals("Network · Offline", failed.failure.label())
            assertNull(world.serverWait.value)
            world.detach()
        }

    @Test
    fun `I147-HAS-3 a conversation of another server asks for no helper start`() = runTest {
        val other =
            object : ChatPhoneServer {
                val wanted = mutableListOf<ProfileId>()

                override fun runsHere(profile: ProfileId): Boolean = false

                override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

                override fun wantAgentHelper(profile: ProfileId) {
                    wanted += profile
                }
            }
        val world = GraphChatWorld(ref, registry(FakeServerGateway(emptyList())), phoneServer = other)
        world.attach()
        runCurrent()
        assertEquals(emptyList(), other.wanted)
        world.detach()
    }

    @Test
    fun `I147-HAS-4 every way into the conversation asks for the helper start on a cold open`() = runTest {
        // Home row, All conversations and Search open ChatRoute(ref); a notification and the edge light carry the
        // request too (EdgeDockScreens, ShellNavigator). Every one reaches ChatViewModel, which opens its world with
        // ChatWorlds.open(route.ref).
        val entries =
            mapOf(
                "Home row" to ChatRoute(ref),
                "All conversations" to ChatRoute(ref, thenAction = null),
                "Search" to ChatRoute(ref),
                "notification" to ChatRoute(ref, focusComposer = true, requestId = "perm-1"),
                "edge light" to ChatRoute(ref, requestId = "perm-1"),
            )
        for ((entry, route) in entries) {
            val agents = Agents()
            val worlds = GraphChatWorlds(registry(FakeServerGateway(emptyList())), phoneServers = setOf(agents))
            val world = worlds.open(route.ref)
            runCurrent()
            assertEquals(listOf(ref.profileId), agents.wanted, "$entry: the helper start was asked for")
            worlds.release(world)
            runCurrent()
        }
    }

    @Test
    fun `I147-HAS-5 with Claude Code signed out a Codex conversation leaves Connecting as soon as the helper is up`() =
        runTest {
            val gateway = FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20))))
            val registry = registry(gateway)
            val agents = Agents()
            agents.wait.value = PhoneServerWait.AgentNeedsSignIn
            val world = GraphChatWorld(ref, registry, phoneServer = agents)
            world.attach()
            runCurrent()
            advanceTimeBy(20_000)
            gateway.events.connect()
            registry.open(profile)
            runCurrent()
            advanceTimeBy(2_500)
            runCurrent()
            assertEquals(ChatLoad.Live, world.load.value)
            assertNull(world.serverWait.value, "no Connecting, no sign-in wait once it is up")
            world.detach()
        }
}
