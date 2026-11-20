package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
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
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.SessionTime
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
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatLastKnown
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatPhoneServer
import io.github.eslamasabry.oc.feature.chat.world.ChatRestart
import io.github.eslamasabry.oc.feature.chat.world.ChatServerWait
import io.github.eslamasabry.oc.feature.chat.world.LaunchStage
import io.github.eslamasabry.oc.feature.chat.world.PhoneServerWait
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * Issue 147 (Galaxy S22 and the owner's phone, build 10045): after the app restarted, every Antigravity conversation
 * refused to open ("Start a new conversation? ... Antigravity can't reopen this one"), so the person could no longer
 * read their own work. The helper keeps no history it can hand out without loading the agent, and Antigravity cannot
 * be loaded again, so the page must open read-only from the page this app kept, with the message box replaced by
 * one way on, and must continue in place when reading did load the agent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OldAgentConversationTest {
    private val ref = ConversationRef(ProfileId("agents"), SessionId("ag-1"))
    private val profile =
        ServerProfile(
            ref.profileId,
            "Phone agents",
            BackendKind.OpenCode2,
            ServerOrigin("http", "127.0.0.1", 6767),
            ProfileTrust(null, null),
            false,
            null,
        )

    private val session =
        Session(
            ref.sessionId,
            "Build the habit tracker",
            directory = "/root/projects/habits",
            time = SessionTime(idleMs = 20),
            selection = SessionSelection(ModelRef("antigravity", "default"), "", null, modelKnown = true, agentKnown = false),
        )

    /** The agents helper as the chat asks it: whether this conversation continues in a new one. */
    private class Helper(var needsNew: Boolean?) : ChatPhoneServer {
        override fun runsHere(profile: ProfileId): Boolean = false

        override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override fun needsNewSession(ref: ConversationRef): Boolean? = needsNew
    }

    /** The helper's history read: refused (the agent cannot be loaded again), or answered after loading it. */
    private class History(
        val base: FakeServerGateway,
        val messages: List<MessageWithParts>?,
        val onRead: () -> Unit = {},
    ) : ServerGateway by base {
        var reads = 0

        override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> {
            reads++
            onRead()
            return messages?.let { Outcome.Ok(ServerPage(it.toImmutableList(), null)) }
                ?: Outcome.Failed(Failure.Unavailable(FeatureId("agentTimeline")))
        }
    }

    /** The page this app kept from the last visit, in memory, through the same fit the store uses. */
    private class Kept(val dispatchers: AppDispatchers, val clock: TestClock, val scope: CoroutineScope) : ChatLastKnown {
        var page: KeptTranscript? = null

        override fun peek(ref: ConversationRef): KeptTranscript? = page

        override suspend fun read(ref: ConversationRef): KeptTranscript? = page

        override suspend fun keep(ref: ConversationRef, title: String?, messages: List<MessageWithParts>) {
            page = KeptTranscript.fit(title, messages, 512 * 1024)
        }

        override fun detached(ref: ConversationRef): TranscriptHandle =
            detachedTranscript(ref.sessionId.raw, dispatchers, clock, scope)
    }

    private fun info(id: String, role: Role, at: Long) =
        MessageInfo(id, ref.sessionId.raw, role, providerId = "antigravity", modelId = "default", time = MsgTime(at, at + 4))

    private fun text(id: String, mid: String, words: String) =
        Part(id, mid, ref.sessionId.raw, PartType.Text, "text", text = words)

    private val conversation =
        listOf(
            MessageWithParts(info("u1", Role.User, 10), listOf(text("u1-t", "u1", "Build a habit tracker")).toImmutableList()),
            MessageWithParts(
                info("a1", Role.Assistant, 11).copy(finish = "stop"),
                listOf(text("a1-t", "a1", "Created the tracker with a daily list.")).toImmutableList(),
            ),
        )

    private inner class Fixture(test: TestScope, gateway: (FakeServerGateway) -> ServerGateway) {
        val dispatchers = TestDispatchers(StandardTestDispatcher(test.testScheduler))
        val clock = TestClock()
        val base = FakeServerGateway(listOf(session))
        val live = gateway(base)
        val kept = Kept(dispatchers, clock, test.backgroundScope)
        val registry =
            ConnectionRegistry(
                setOf(
                    object : GatewayFactory {
                        override val backend = BackendKind.OpenCode2

                        override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
                            Outcome.Failed(Failure.Network(NetworkWhy.Refused))

                        override suspend fun connect(spec: GatewaySpec) = Outcome.Ok(GatewayPair(live, FakeOperationsGateway()))
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
                AppCoroutineScope(test.backgroundScope.coroutineContext),
                dispatchers,
            )

        suspend fun connect(scope: TestScope) {
            base.events.connect()
            registry.open(profile)
            scope.runCurrent()
        }
    }

    @Test
    fun `I147-OLD-002 an old Antigravity conversation opens read-only with what it had, and offers to continue`() = runTest {
        val f = Fixture(this) { History(it, messages = null) }
        f.kept.page = KeptTranscript.fit("Build the habit tracker", conversation, 512 * 1024)
        f.connect(this)
        val helper = Helper(needsNew = true)
        val world = GraphChatWorld(ref, f.registry, phoneServer = helper, lastKnown = f.kept)
        world.attach()
        runCurrent()
        // The helper could not load it: the page draws what this app kept, never a refusal or a blank page.
        assertIs<ChatLoad.Failed>(world.load.value)
        val rows = world.transcript.value.rows
        assertTrue(rows.isNotEmpty(), "the kept conversation is on the page")
        // The message box is replaced by the one way on: a new conversation with the same agent, in the same folder.
        assertEquals(
            ChatRestart("antigravity", "/root/projects/habits", "Antigravity"),
            world.readOnly.value?.restart,
        )
        world.detach()
    }

    @Test
    fun `I147-OLD-003 when reading the conversation loads its agent again, it continues in place`() = runTest {
        val helper = Helper(needsNew = true)
        // The helper's history read loads the saved agent (its in-place resume): from then on it holds it.
        val f = Fixture(this) { History(it, conversation) { helper.needsNew = false } }
        f.connect(this)
        val world = GraphChatWorld(ref, f.registry, phoneServer = helper, lastKnown = f.kept)
        world.attach()
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        assertNull(world.readOnly.value, "a resumed conversation keeps its message box")
        assertFalse(world.transcript.value.rows.isEmpty())
        world.detach()
    }

    /** The agents helper while the app itself starts it: a stage, a wait, and an agent it does not hold yet. */
    private class StartingHelper(var needsNew: Boolean?) : ChatPhoneServer {
        val stages = MutableStateFlow<LaunchStage?>(LaunchStage.Helper)
        val waits = MutableStateFlow(PhoneServerWait.Starting)

        override fun runsHere(profile: ProfileId): Boolean = false

        override fun isAgentHelper(profile: ProfileId): Boolean = true

        override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override fun waiting(profile: ProfileId): StateFlow<PhoneServerWait> = waits

        override fun stage(profile: ProfileId): StateFlow<LaunchStage?> = stages

        override fun needsNewSession(ref: ConversationRef): Boolean? = needsNew

        fun up() {
            stages.value = null
            waits.value = PhoneServerWait.None
        }
    }

    /** The history read: not answering while the helper starts, then loading the agent and answering. */
    private class StartingHistory(val base: FakeServerGateway, val messages: List<MessageWithParts>?, val helper: StartingHelper) :
        ServerGateway by base {
        override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> =
            when {
                helper.stages.value != null -> Outcome.Failed(Failure.Network(NetworkWhy.Offline))
                messages == null -> Outcome.Failed(Failure.Unavailable(FeatureId("agentTimeline")))
                else -> {
                    helper.needsNew = false
                    Outcome.Ok(ServerPage(messages.toImmutableList(), null))
                }
            }
    }

    @Test
    fun `B-CHAT-304 an old agent conversation opened while agents start waits instead of refusing`() = runTest {
        val helper = StartingHelper(needsNew = true)
        val f = Fixture(this) { StartingHistory(it, conversation, helper) }
        f.connect(this)
        val world = GraphChatWorld(ref, f.registry, phoneServer = helper, lastKnown = f.kept)
        val seen = mutableListOf<io.github.eslamasabry.oc.feature.chat.world.ReadOnlyChat?>()
        backgroundScope.launch { world.readOnly.collect { seen += it } }
        world.attach()
        runCurrent()
        advanceTimeBy(5_000)
        // Helper still starting: the page says the real stage, offers no refusal and no Continue.
        assertNull(world.readOnly.value, "no notice while the helper starts")
        assertTrue(world.load.value !is ChatLoad.Live)
        assertEquals(ChatServerWait.StartingHelper, world.serverWait.value)
        // The helper comes up and the read loads the agent: the box appears.
        helper.up()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(ChatLoad.Live, world.load.value)
        assertNull(world.readOnly.value)
        assertTrue(seen.none { it?.restart != null }, "the notice never appeared, not even for a moment")
        world.detach()
    }

    @Test
    fun `B-CHAT-305 an old agent conversation the running helper truly refuses shows the notice and keeps it`() = runTest {
        val helper = StartingHelper(needsNew = true)
        helper.up()
        val f = Fixture(this) { StartingHistory(it, null, helper) }
        f.kept.page = KeptTranscript.fit("Build the habit tracker", conversation, 512 * 1024)
        f.connect(this)
        val world = GraphChatWorld(ref, f.registry, phoneServer = helper, lastKnown = f.kept)
        world.attach()
        runCurrent()
        advanceTimeBy(10_000)
        assertEquals(
            ChatRestart("antigravity", "/root/projects/habits", "Antigravity"),
            world.readOnly.value?.restart,
        )
        advanceTimeBy(60_000)
        assertTrue(world.readOnly.value?.restart != null, "it stays")
        world.detach()
    }
}
