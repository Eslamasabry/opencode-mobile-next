package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent as CoreEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome as CoreOutcome
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
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
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatLastKnown
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorlds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * Speed-B: a conversation's page is kept as soon as each turn ends (even before a first live history read succeeded
 * or without leaving the screen), so a cold restart shows the completed turns before any live read answers; and a
 * brand-new conversation with no turns never shows a placeholder of made-up turns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeepOnTurnEndTest {
    private val ref = ConversationRef(ProfileId("phone-agents"), SessionId("ag-1"))
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

    private class DiskBackedFake(
        val jsonByKey: MutableMap<ConversationRef, String> = HashMap(),
    ) {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun port(
            dispatchers: AppDispatchers,
            clock: TestClock,
            scope: CoroutineScope,
            inMemoryOnly: Boolean = false,
        ): ChatLastKnown =
            object : ChatLastKnown {
                private val memory = HashMap<ConversationRef, KeptTranscript>()

                override fun peek(ref: ConversationRef): KeptTranscript? = memory[ref]

                override suspend fun read(ref: ConversationRef): KeptTranscript? {
                    if (inMemoryOnly) return memory[ref]
                    return memory[ref] ?: jsonByKey[ref]?.let {
                        json.decodeFromString(KeptTranscript.serializer(), it).also { parsed -> memory[ref] = parsed }
                    }
                }

                override suspend fun keep(ref: ConversationRef, title: String?, messages: List<MessageWithParts>) {
                    val kept = KeptTranscript.fit(title, messages, 512 * 1024) ?: return
                    memory[ref] = kept
                    jsonByKey[ref] = json.encodeToString(KeptTranscript.serializer(), kept)
                }

                override fun detached(ref: ConversationRef): TranscriptHandle =
                    detachedTranscript(ref.sessionId.raw, dispatchers, clock, scope)
            }
    }

    private class ScriptedHistory(
        val base: FakeServerGateway,
        var answer: suspend () -> Outcome<ServerPage<MessageWithParts>>,
    ) : ServerGateway by base {
        override suspend fun messagePage(
            id: SessionId,
            cursor: String?,
            limit: Int,
        ): Outcome<ServerPage<MessageWithParts>> = answer()
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

    private fun userMessage(id: String, at: Long, text: String) =
        MessageWithParts(
            MessageInfo(id, ref.sessionId.raw, Role.User, providerId = "claude-code", modelId = "sonnet", time = MsgTime(at, at + 1)),
            listOf(Part("$id-p", id, ref.sessionId.raw, PartType.Text, "text", text = text)).toImmutableList(),
        )

    private fun assistantMessage(id: String, at: Long, text: String) =
        MessageWithParts(
            MessageInfo(id, ref.sessionId.raw, Role.Assistant, providerId = "claude-code", modelId = "sonnet", time = MsgTime(at, at + 2), finish = "stop"),
            listOf(Part("$id-p", id, ref.sessionId.raw, PartType.Text, "text", text = text)).toImmutableList(),
        )

    private fun emitTurn(
        events: FakeServerGateway,
        promptId: String,
        promptText: String,
        replyId: String,
        replyHead: String,
        replyTail: String,
        at: Long,
    ) {
        val user = userMessage(promptId, at, promptText)
        val assistant = assistantMessage(replyId, at + 1, "")
        events.events.emit(CoreEvent.RunStarted(ref.sessionId))
        events.events.emit(CoreEvent.MessageUpserted(user.info))
        events.events.emit(CoreEvent.PartUpserted(user.parts.single()))
        events.events.emit(CoreEvent.MessageUpserted(assistant.info))
        events.events.emit(CoreEvent.PartUpserted(assistant.parts.single()))
        events.events.emit(CoreEvent.PartDelta(ref.sessionId, MessageId(replyId), PartId("$replyId-p"), DeltaField.Text, replyHead))
        events.events.emit(CoreEvent.PartDelta(ref.sessionId, MessageId(replyId), PartId("$replyId-p"), DeltaField.Text, replyTail))
        events.events.emit(CoreEvent.RunEnded(ref.sessionId, CoreOutcome.Succeeded))
    }

    @Test
    fun `KTE-001 a turn ends before any live history read succeeded and cold restart shows it before live read`() =
        runTest {
            val disk = DiskBackedFake()
            val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
            val clock = TestClock()

            // First run: initial history read fails (historyReconciled stays false), then a turn streams and ends.
            val base1 = FakeServerGateway(listOf(Session(ref.sessionId, "Phone agent", time = SessionTime(idleMs = 20))))
            base1.events.connect()
            val gw1 = ScriptedHistory(base1) { Outcome.Failed(Failure.UnreadableReply()) }
            val reg1 = registry(gw1)
            reg1.open(profile)
            runCurrent()
            val worlds1 = GraphChatWorlds(reg1, lastKnown = setOf(disk.port(dispatchers, clock, backgroundScope)))
            val open1 = worlds1.open(ref)
            runCurrent()

            emitTurn(base1, "u1", "Check status", "a1", "Everything ", "is green.", at = 10L)
            runCurrent()
            // Process dies without detach(): the page must already be on disk from RunEnded.
            assertNotNull(disk.jsonByKey[ref], "RunEnded kept the page even though history never reconciled")

            // Cold restart: fresh registry, fresh world, fresh ChatLastKnown (empty memory, reads disk), slow live read.
            val gate = CompletableDeferred<Unit>()
            val base2 = FakeServerGateway(listOf(Session(ref.sessionId, "Phone agent", time = SessionTime(idleMs = 20))))
            base2.events.connect()
            val gw2 = ScriptedHistory(base2) {
                gate.await()
                Outcome.Ok(ServerPage(emptyList<MessageWithParts>().toImmutableList(), null))
            }
            val reg2 = registry(gw2)
            reg2.open(profile)
            runCurrent()
            val worlds2 = GraphChatWorlds(reg2, lastKnown = setOf(disk.port(dispatchers, clock, backgroundScope)))
            val open2 = worlds2.open(ref)
            runCurrent()

            val rowsBeforeLive = open2.transcript.value.rows
            assertTrue(rowsBeforeLive.any { it is PromptRow && it.text == "Check status" }, "kept prompt shown: $rowsBeforeLive")
            assertTrue(rowsBeforeLive.any { it is BlockRow && it.text.contains("Everything is green.") }, "kept reply shown: $rowsBeforeLive")
            assertEquals(ChatLoad.Loading, open2.load.value)
            gate.complete(Unit)
            worlds2.release(open2)
            worlds1.release(open1)
        }

    @Test
    fun `KTE-002 a turn ends after an earlier live read and cold restart shows the new turn without leaving first`() =
        runTest {
            val disk = DiskBackedFake()
            val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
            val clock = TestClock()

            val turn1 = listOf(userMessage("u1", 10L, "First prompt"), assistantMessage("a1", 11L, "First reply"))
            val base1 = FakeServerGateway(listOf(Session(ref.sessionId, "Phone agent", time = SessionTime(idleMs = 20))))
            base1.events.connect()
            val gw1 = ScriptedHistory(base1) { Outcome.Ok(ServerPage(turn1.toImmutableList(), null)) }
            val reg1 = registry(gw1)
            reg1.open(profile)
            runCurrent()
            val worlds1 = GraphChatWorlds(reg1, lastKnown = setOf(disk.port(dispatchers, clock, backgroundScope)))
            val open1 = worlds1.open(ref)
            runCurrent()

            emitTurn(base1, "u2", "Second prompt", "a2", "Second ", "reply.", at = 20L)
            runCurrent()
            // No detach() — cold restart immediately after turn 2 ended.
            val gate = CompletableDeferred<Unit>()
            val base2 = FakeServerGateway(listOf(Session(ref.sessionId, "Phone agent", time = SessionTime(idleMs = 20))))
            base2.events.connect()
            val gw2 = ScriptedHistory(base2) {
                gate.await()
                Outcome.Ok(ServerPage(turn1.toImmutableList(), null))
            }
            val reg2 = registry(gw2)
            reg2.open(profile)
            runCurrent()
            val worlds2 = GraphChatWorlds(reg2, lastKnown = setOf(disk.port(dispatchers, clock, backgroundScope)))
            val open2 = worlds2.open(ref)
            runCurrent()

            val rowsBeforeLive = open2.transcript.value.rows
            assertTrue(rowsBeforeLive.any { it is PromptRow && it.text == "First prompt" }, "first turn kept: $rowsBeforeLive")
            assertTrue(rowsBeforeLive.any { it is PromptRow && it.text == "Second prompt" }, "second turn kept on RunEnded: $rowsBeforeLive")
            assertTrue(rowsBeforeLive.any { it is BlockRow && it.text.contains("Second reply.") }, "streamed reply kept: $rowsBeforeLive")
            gate.complete(Unit)
            worlds2.release(open2)
            worlds1.release(open1)
        }

    @Test
    fun `KTE-003 a brand-new conversation never shows a placeholder of made-up turns`() = runTest {
        val disk = DiskBackedFake()
        val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
        val clock = TestClock()

        val brandNew = ConversationRef(profile.id, SessionId("brand-new"))
        val gate = CompletableDeferred<Unit>()
        val base = FakeServerGateway(listOf(Session(brandNew.sessionId, "New conversation", time = SessionTime(idleMs = 20))))
        base.events.connect()
        val gw = ScriptedHistory(base) {
            gate.await()
            Outcome.Ok(ServerPage(emptyList<MessageWithParts>().toImmutableList(), null))
        }
        val reg = registry(gw)
        reg.open(profile)
        runCurrent()
        val worlds = GraphChatWorlds(reg, lastKnown = setOf(disk.port(dispatchers, clock, backgroundScope)))
        val open = worlds.open(brandNew)
        val presenter = ChatPresenter(open, RecordingNavigator(), Immediate)
        runCurrent()

        assertEquals(ChatLoad.Loading, open.load.value)
        assertTrue(open.transcript.value.rows.isEmpty(), "brand-new conversation has no rows while loading")
        assertEquals(ChatPhase.Loading, presenter.current().phase)
        assertTrue(presenter.current().rows.isEmpty())

        // Even an empty RunEnded event never invents or keeps turns.
        base.events.emit(CoreEvent.RunEnded(brandNew.sessionId, CoreOutcome.Idle))
        runCurrent()
        assertNull(disk.jsonByKey[brandNew], "empty conversation kept nothing on RunEnded")
        assertTrue(open.transcript.value.rows.isEmpty())

        gate.complete(Unit)
        runCurrent()
        worlds.release(open)
    }
}
