package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.StagedRevertGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RevertKind
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionRevert
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
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ConnectionSession
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.delivery.DeliveryStorage
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import io.github.eslamasabry.oc.engine.requests.ConnectionRequests
import io.github.eslamasabry.oc.engine.transcript.TranscriptAccessors
import io.github.eslamasabry.oc.engine.transcript.TranscriptEngine
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorld
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * B-473: undone turns disappear right away on OpenCode 2 (never lingering while reload runs), and sending while an
 * undo is finishing waits cleanly / shows "Finishing undo…" instead of failing cryptically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UndoDirectChatTest {
    private val ref = ConversationRef(ProfileId("p1"), SessionId("s1"))
    private val profile =
        ServerProfile(
            ref.profileId,
            "Phone",
            BackendKind.OpenCode2,
            ServerOrigin("http", "127.0.0.1", 4096),
            ProfileTrust(null, null),
            false,
            null,
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun userMsg(id: String, at: Long, text: String): MessageWithParts =
        MessageWithParts(
            MessageInfo(id, ref.sessionId.raw, Role.User, time = MsgTime(at, at)),
            persistentListOf(Part("p$id", id, ref.sessionId.raw, PartType.Text, "text", text = text)),
        )

    private fun replyMsg(id: String, at: Long, text: String): MessageWithParts =
        MessageWithParts(
            MessageInfo(id, ref.sessionId.raw, Role.Assistant, time = MsgTime(at, at)),
            persistentListOf(Part("p$id", id, ref.sessionId.raw, PartType.Text, "text", text = text)),
        )

    private class Oc2UndoGateway(
        val base: FakeServerGateway,
        var history: List<MessageWithParts>,
    ) : ServerGateway by base, StagedRevertGateway {
        var staged: SessionRevert? = null
        var holdNextHistory: CompletableDeferred<Unit>? = null
        var undoInProgress: Boolean = false
        var sentMidUndo: Boolean = false

        override suspend fun session(id: SessionId): Outcome<Session> =
            Session(id, reverted = staged != null, stagedRevert = staged).ok()

        override suspend fun messagePage(
            id: SessionId,
            cursor: String?,
            limit: Int,
        ): Outcome<ServerPage<MessageWithParts>> {
            holdNextHistory?.let { gate ->
                holdNextHistory = null
                gate.await()
            }
            return ServerPage(history.toImmutableList(), null).ok()
        }

        override suspend fun prompt(
            session: SessionId,
            prompt: OutgoingPrompt,
            commandId: String?,
        ): Outcome<PromptAdmission> {
            if (undoInProgress) {
                sentMidUndo = true
                return Outcome.Failed(Failure.Unexpected())
            }
            return base.prompt(session, prompt, commandId)
        }

        override suspend fun sessionRevertPrompt(session: SessionId, message: MessageId): Outcome<String?> =
            history.firstOrNull { it.info.id == message.raw }?.parts?.first()?.text.ok()

        override suspend fun stageSessionRevert(
            session: SessionId,
            message: MessageId,
            applyFiles: Boolean,
        ): Outcome<SessionRevert> = SessionRevert(message, null, null, persistentListOf()).also { staged = it }.ok()

        override suspend fun clearSessionRevert(session: SessionId): Outcome<Unit> = Unit.ok().also { staged = null }

        override suspend fun commitSessionRevert(session: SessionId): Outcome<Unit> = Unit.ok().also { staged = null }
    }

    private fun TestScope.worldFixture(
        gateway: ServerGateway,
        ops: ServerOperationsGateway,
    ): Pair<ConnectionRegistry, GraphChatWorld> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = TestDispatchers(dispatcher)
        val app = AppCoroutineScope(backgroundScope.coroutineContext)
        val clock = TestClock()
        val kv =
            PrefsKeyValueStore(
                InMemoryPrefsBackend(),
                FlutterPrefsCodec(JavaListDecoder.Unsupported),
                StateMigrationGate.alwaysOpen(),
                dispatcher,
            )
        val storage = DeliveryStorage(kv, DefaultWriterLanes(), ProfileDirectory { true })
        val profiles =
            object : ComposerProfiles {
                override fun exists(profileId: String): Boolean = profileId == ref.profileId.raw

                override fun count(): Int = 1
            }
        val drafts =
            ConversationDrafts(
                Drafts(MemoryComposerPrefs(), AttachmentVault(MemoryPayloadStore()), profiles, clock),
                app,
                dispatchers,
            )
        val registry =
            ConnectionRegistry(
                setOf(
                    object : GatewayFactory {
                        override val backend = BackendKind.OpenCode2

                        override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
                            Outcome.Failed(Failure.Network(NetworkWhy.Refused))

                        override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> =
                            Outcome.Ok(GatewayPair(gateway, ops))
                    }
                ),
                { p, pair, scope ->
                    object : ConnectionGraph, ChatGraphAccessors, TranscriptAccessors {
                        override val profile = p
                        override val scope = scope
                        override val session = ConnectionSession(p, pair.live, scope)
                        override val chatDelivery =
                            ConnectionDelivery(pair.live, p, scope, clock, setOf(storage), dispatchers)
                        override val chatRequests = ConnectionRequests(pair.live, scope, dispatchers, clock)
                        override val chatGateway = pair.live
                        override val chatOperations = pair.operations
                        override val transcriptEngine = TranscriptEngine(pair.live, dispatchers, clock, scope)
                    }
                },
                app,
                dispatchers,
            )
        return registry to GraphChatWorld(ref, registry, drafts)
    }

    @Test
    fun `B-473 undone turns clear immediately when OpenCode 2 stages an undo`() =
        runTest(UnconfinedTestDispatcher()) {
            val base = FakeServerGateway(listOf(Session(ref.sessionId))).apply {
                capabilities.value = capabilities.value.copy(sessionRevert = true)
            }
            val history =
                listOf(
                    userMsg("u1", 1, "first"),
                    replyMsg("a1", 2, "one"),
                    userMsg("u2", 3, "second"),
                    replyMsg("a2", 4, "two"),
                )
            val gateway = Oc2UndoGateway(base, history)
            val (reg, world) = worldFixture(gateway, FakeOperationsGateway())
            val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
            world.attach()
            base.events.connect()
            reg.open(profile)
            runCurrent()
            assertEquals(ChatLoad.Live, world.load.value)
            assertTrue(world.transcript.value.rows.any { it.key == "prompt:u2" })

            // Pause the reload fetch so we observe the state right after OpenCode 2 stages the undo.
            val reloadGate = CompletableDeferred<Unit>()
            gateway.holdNextHistory = reloadGate
            val revert = SessionRevert(MessageId("u2"), null, "snap", persistentListOf())
            gateway.staged = revert
            base.events.emit(GatewayEvent.RevertChanged(ref.sessionId, RevertKind.Staged))
            runCurrent()

            assertTrue(
                world.transcript.value.rows.none { it.key == "prompt:u2" || it.turnId == "u2" },
                "undone turn u2 must clear immediately, not linger while reload finishes",
            )
            assertTrue(
                presenter.current().rows.none { it.key == "prompt:u2" || it.turnId == "u2" },
                "presenter must drop undone turn u2 right away",
            )
            assertTrue(
                world.transcript.value.rows.any { it.key == "prompt:u1" },
                "earlier turn u1 must stay visible",
            )

            reloadGate.complete(Unit)
            runCurrent()
            world.detach()
        }

    @Test
    fun `B-473 sending while undo is finishing waits cleanly and shows Finishing undo`() =
        runTest(UnconfinedTestDispatcher()) {
            val base = FakeServerGateway(listOf(Session(ref.sessionId))).apply {
                capabilities.value = capabilities.value.copy(sessionRevert = true)
            }
            val history =
                listOf(
                    userMsg("u1", 1, "first"),
                    replyMsg("a1", 2, "one"),
                    userMsg("u2", 3, "second"),
                    replyMsg("a2", 4, "two"),
                )
            val gateway = Oc2UndoGateway(base, history)
            val undoGate = CompletableDeferred<Unit>()
            val ops =
                object : FakeOperationsGateway() {
                    override suspend fun revertSession(id: SessionId, to: MessageId): Outcome<Unit> {
                        gateway.undoInProgress = true
                        undoGate.await()
                        gateway.history = history.take(2)
                        gateway.undoInProgress = false
                        return Unit.ok()
                    }
                }
            val (reg, world) = worldFixture(gateway, ops)
            val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
            val worlds =
                object : ChatWorlds {
                    override fun open(ref: ConversationRef): ChatWorld = world

                    override fun release(world: ChatWorld) = Unit
                }
            val vm = BasicComposerViewModel(ComposerTarget(ref.profileId.raw, ref.sessionId.raw), worlds)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

            world.attach()
            base.events.connect()
            reg.open(profile)
            runCurrent()
            assertEquals(ChatLoad.Live, world.load.value)

            val undoJob = async { world.revertFrom(MessageId("u2")) }
            runCurrent()
            assertEquals(
                "FinishingUndo",
                presenter.current().status?.kind?.name,
                "status line must show FinishingUndo while undo is in flight",
            )

            val sendJob = async { vm.send("follow up after undo") }
            runCurrent()
            assertFalse(gateway.sentMidUndo, "send must wait instead of racing mid-undo into the server")
            assertTrue(world.pending.value.none { it.state == PendingState.Failed }, "no cryptic send failure")

            undoGate.complete(Unit)
            runCurrent()
            assertEquals(Outcome.Ok(Unit), undoJob.await())
            assertTrue(sendJob.await(), "send should complete cleanly once undo finishes")
            assertFalse(gateway.sentMidUndo, "send must never hit the server while undo is running")
            assertTrue(base.sentPrompts.any { it.second.text == "follow up after undo" })
            world.detach()
        }
}
