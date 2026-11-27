package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
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
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.PromptDelivery
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
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
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueueStore
import io.github.eslamasabry.oc.engine.delivery.queue.QueueLimits
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRead
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import io.github.eslamasabry.oc.engine.requests.ConnectionRequests
import io.github.eslamasabry.oc.engine.requests.consent.AutomationBehavior
import io.github.eslamasabry.oc.engine.requests.consent.AutomationPolicy
import io.github.eslamasabry.oc.engine.requests.consent.AutomationPolicyOwner
import io.github.eslamasabry.oc.engine.transcript.TranscriptAccessors
import io.github.eslamasabry.oc.engine.transcript.TranscriptEngine
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.ChatInboxRow
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.PendingNotice
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class OfflineOutboxTest {
    private val ref = ConversationRef(ProfileId("p1"), SessionId("s1"))
    private val profile =
        ServerProfile(
            ref.profileId,
            "Home server",
            BackendKind.OpenCode2,
            ServerOrigin("https", "example.invalid", 443),
            ProfileTrust(null, null),
            false,
            null,
        )

    private val profiles =
        object : ComposerProfiles {
            override fun exists(profileId: String): Boolean = profileId == "p1"

            override fun count(): Int = 1
        }

    private fun image(name: String = "screenshot.png") =
        PromptAttachment(
            mime = "image/png",
            filename = name,
            url = "data:image/png;base64," + Base64.encode(byteArrayOf(1, 2, 3, 4)),
        )

    private class Stores(
        val prefsBackend: InMemoryPrefsBackend = InMemoryPrefsBackend(),
        val composerPrefs: MemoryComposerPrefs = MemoryComposerPrefs(),
        val payloads: MemoryPayloadStore = MemoryPayloadStore(),
    )

    private class ScriptedGateway(
        private val delegate: FakeServerGateway,
        var history: List<MessageWithParts> = emptyList(),
        var onPrompt: (suspend (SessionId, OutgoingPrompt) -> Outcome<PromptAdmission>?)? = null,
    ) : ServerGateway by delegate {
        val promptAttempts = mutableListOf<OutgoingPrompt>()

        override suspend fun messagePage(
            id: SessionId,
            cursor: String?,
            limit: Int,
        ): Outcome<ServerPage<MessageWithParts>> =
            if (history.isEmpty()) delegate.messagePage(id, cursor, limit)
            else Outcome.Ok(ServerPage(history.toImmutableList(), null))

        override suspend fun prompt(
            session: SessionId,
            prompt: OutgoingPrompt,
            commandId: String?,
        ): Outcome<PromptAdmission> {
            promptAttempts += prompt
            return onPrompt?.invoke(session, prompt) ?: delegate.prompt(session, prompt, commandId)
        }
    }

    private inner class Fixture(
        val test: TestScope,
        val stores: Stores = Stores(),
        offlineQueueCapable: Boolean = true,
        history: List<MessageWithParts> = emptyList(),
    ) {
        val base =
            FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20)))).apply {
                capabilities.value =
                    capabilities.value.copy(
                        offlinePromptQueue = offlineQueueCapable,
                        promptSteering = true,
                        promptAttachments = true,
                    )
            }
        val gateway = ScriptedGateway(base, history = history)
        val operations = FakeOperationsGateway()
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        val dispatchers = TestDispatchers(dispatcher)
        val clock = TestClock()
        val app = AppCoroutineScope(test.backgroundScope.coroutineContext)
        val kv =
            PrefsKeyValueStore(
                stores.prefsBackend,
                FlutterPrefsCodec(JavaListDecoder.Unsupported),
                StateMigrationGate.alwaysOpen(),
                dispatcher,
            )
        val storage = DeliveryStorage(kv, DefaultWriterLanes(), ProfileDirectory { true })
        val drafts =
            ConversationDrafts(
                Drafts(stores.composerPrefs, AttachmentVault(stores.payloads), profiles, TestClock()),
                app,
                dispatchers,
            )
        val registry =
            ConnectionRegistry(
                setOf(
                    object : GatewayFactory {
                        override val backend = profile.backend

                        override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
                            Outcome.Failed(Failure.Network(NetworkWhy.Refused))

                        override suspend fun connect(spec: GatewaySpec) =
                            Outcome.Ok(GatewayPair(gateway, operations))
                    }
                ),
                { p, pair, scope ->
                    object : ConnectionGraph, ChatGraphAccessors, TranscriptAccessors {
                        override val profile = p
                        override val scope = scope
                        override val session = ConnectionSession(p, pair.live, scope)
                        override val chatDelivery =
                            ConnectionDelivery(
                                pair.live,
                                p,
                                scope,
                                clock,
                                setOf(storage),
                                dispatchers,
                            )
                        override val chatRequests =
                            ConnectionRequests(pair.live, scope, dispatchers, clock)
                        override val chatGateway = pair.live
                        override val chatOperations = pair.operations
                        override val transcriptEngine =
                            TranscriptEngine(pair.live, dispatchers, clock, scope)
                    }
                },
                app,
                dispatchers,
            )
        val worlds = GraphChatWorlds(registry, setOf(drafts), clock = clock, storages = setOf(storage))
        val world: ChatWorld by lazy { worlds.open(ref) }

        suspend fun openOnline() {
            base.events.connect()
            registry.open(profile)
            world
            test.runCurrent()
        }

        fun goOffline() {
            base.events.status.value = StreamStatus.WaitingForNetwork
            test.runCurrent()
        }

        fun reconnect() {
            base.events.connect()
            test.runCurrent()
        }

        fun echoUser(id: String, text: String) {
            base.events.emit(GatewayEvent.MessageUpserted(MessageInfo(id, ref.sessionId.raw, Role.User)))
            base.events.emit(
                GatewayEvent.PartUpserted(Part("p-$id", id, ref.sessionId.raw, PartType.Text, "text", text))
            )
            test.advanceTimeBy(60)
            test.runCurrent()
        }
    }

    private fun List<PendingSend>.waitingBubble(): PendingSend? =
        firstOrNull { it.state == PendingState.WaitingForConnection }

    @Test
    fun `B-500 offline send queues waiting item with attachments instead of failing`() = runTest {
        val f = Fixture(this)
        f.openOnline()
        f.goOffline()
        val pic = image()
        assertIs<Outcome.Ok<Unit>>(f.world.replaceAttachments(listOf(pic)))
        f.world.draftChanged("Hold this until signal returns")
        val sent = f.world.send("Hold this until signal returns")
        runCurrent()
        assertIs<Outcome.Ok<Unit>>(sent, "offline send must hold the message instead of failing")
        val waiting =
            assertNotNull(
                f.world.pending.value.waitingBubble(),
                "waiting bubble must stay visible in the chat while offline",
            )
        assertEquals("Hold this until signal returns", waiting.text)
        assertEquals(listOf("screenshot.png"), waiting.files.map { it.name })
        assertTrue(f.base.sentPrompts.isEmpty(), "nothing sent while offline")
    }

    @Test
    fun `B-500 reconnect sends waiting message once automatically with no duplicate`() = runTest {
        val f = Fixture(this)
        f.openOnline()
        f.goOffline()
        f.world.draftChanged("Send once on reconnect")
        assertIs<Outcome.Ok<Unit>>(f.world.send("Send once on reconnect"))
        runCurrent()
        assertNotNull(f.world.pending.value.waitingBubble(), "must wait in chat while offline")
        assertTrue(f.base.sentPrompts.isEmpty())

        f.reconnect()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("Send once on reconnect"), f.base.sentPrompts.map { it.second.text })
        f.echoUser("u-reconnected", "Send once on reconnect")
        assertTrue(f.world.pending.value.isEmpty(), "bubble settles cleanly with no duplicate")
    }

    @Test
    fun `B-500 reopen restores waiting message across restart and sends on reconnect`() = runTest {
        val stores = Stores()
        val first = Fixture(this, stores = stores)
        first.openOnline()
        first.goOffline()
        first.world.draftChanged("Survives app restart")
        assertIs<Outcome.Ok<Unit>>(first.world.send("Survives app restart"))
        runCurrent()
        assertNotNull(first.world.pending.value.waitingBubble(), "held in outbox before close")
        first.worlds.release(first.world)

        val reopened = Fixture(this, stores = stores)
        reopened.world
        runCurrent()
        val restored =
            assertNotNull(
                reopened.world.pending.value.waitingBubble(),
                "waiting message must be restored after app restart while still offline",
            )
        assertEquals("Survives app restart", restored.text)

        reopened.openOnline()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("Survives app restart"), reopened.base.sentPrompts.map { it.second.text })
    }

    @Test
    fun `B-500 online send during active reply steers immediately and never queues`() = runTest {
        val f = Fixture(this)
        f.openOnline()
        f.base.events.emit(GatewayEvent.RunStarted(ref.sessionId))
        advanceTimeBy(60)
        runCurrent()
        assertTrue(f.world.transcript.value.run.busy)

        f.world.draftChanged("Change direction now")
        assertIs<Outcome.Ok<Unit>>(f.world.send("Change direction now"))
        runCurrent()
        assertEquals(listOf("Change direction now"), f.base.sentPrompts.map { it.second.text })
        assertEquals(PromptDelivery.Steer, f.base.sentPrompts.single().second.delivery)
        assertEquals(null, f.world.pending.value.waitingBubble(), "online steer must never queue")
    }

    @Test
    fun `B-500 cancel drops waiting item so reconnect sends nothing`() = runTest {
        val f = Fixture(this)
        f.openOnline()
        f.goOffline()
        f.world.draftChanged("Do not send this")
        assertIs<Outcome.Ok<Unit>>(f.world.send("Do not send this"))
        runCurrent()
        val waiting =
            assertNotNull(
                f.world.pending.value.waitingBubble(),
                "waiting item must exist before cancel",
            )
        assertEquals("Do not send this", waiting.text)

        assertIs<Outcome.Ok<Unit>>(f.world.cancelWaitingSend(waiting.localId))
        runCurrent()
        assertTrue(f.world.pending.value.isEmpty(), "cancelled waiting item must disappear")

        f.reconnect()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(f.base.sentPrompts.isEmpty(), "cancelled item must never send on reconnect")
    }

    @Test
    fun `B-500 server without offline queue capability still holds offline message and sends once`() =
        runTest {
            val f = Fixture(this, offlineQueueCapable = false)
            f.openOnline()
            f.goOffline()
            f.world.draftChanged("Hold on non-queue backend")
            assertIs<Outcome.Ok<Unit>>(f.world.send("Hold on non-queue backend"))
            runCurrent()
            val waiting =
                assertNotNull(
                    f.world.pending.value.waitingBubble(),
                    "waiting item must be held while offline",
                )
            assertEquals("Hold on non-queue backend", waiting.text)
            assertTrue(f.base.sentPrompts.isEmpty())

            f.reconnect()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf("Hold on non-queue backend"), f.base.sentPrompts.map { it.second.text })
            f.echoUser("u-nonqueue", "Hold on non-queue backend")
            assertTrue(f.world.pending.value.isEmpty(), "bubble settles cleanly with no duplicate")
        }

    @Test
    fun `B-500 presenter keeps waiting message visible offline and forwards cancel`() {
        val world = FakeChatWorld()
        val nav = RecordingNavigator()
        world.load.value = io.github.eslamasabry.oc.feature.chat.world.ChatLoad.Failed(
            Failure.Network(NetworkWhy.Offline),
        )
        world.link.value = io.github.eslamasabry.oc.feature.chat.world.ChatLink.Offline
        world.pending.value =
            kotlinx.collections.immutable.persistentListOf(
                PendingSend(42L, "Held offline", PendingState.WaitingForConnection),
            )
        val presenter =
            io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter(
                world,
                nav,
                Immediate,
            )
        val ui = presenter.current()
        assertEquals(io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase.Live, ui.phase)
        assertEquals(listOf(42L), ui.pending.map { it.localId })
        presenter.cancelWaitingSend(42L)
        assertEquals(listOf("cancelWaitingSend 42"), world.calls)
    }

    @Test
    fun `B-500 no-connection offline send respects closed profile and cancel removes entry`() =
        runTest {
            val f = Fixture(this)
            f.world
            runCurrent()
            f.world.draftChanged("Typed while cold offline")
            assertIs<Outcome.Ok<Unit>>(f.world.send("Typed while cold offline"))
            runCurrent()
            val waiting =
                assertNotNull(
                    f.world.pending.value.waitingBubble(),
                    "waiting bubble must appear even before any connection graph exists",
                )
            val stored =
                assertIs<QueueRead.Readable>(OfflineQueueStore(f.kv, f.storage.lanes.app).load())
            assertEquals(listOf("Typed while cold offline"), stored.entries.map { it.text })

            assertIs<Outcome.Ok<Unit>>(f.world.cancelWaitingSend(waiting.localId))
            runCurrent()
            assertTrue(f.world.pending.value.isEmpty(), "cancelled no-connection bubble must clear")
            val afterCancel =
                assertIs<QueueRead.Readable>(OfflineQueueStore(f.kv, f.storage.lanes.app).load())
            assertTrue(afterCancel.entries.isEmpty(), "cancelled no-connection entry must leave store")

            f.storage.journals.closeProfile(ref.profileId)
            f.world.draftChanged("Blocked after profile close")
            val refused = f.world.send("Blocked after profile close")
            runCurrent()
            val failed =
                assertIs<Outcome.Failed>(
                    refused,
                    "closed profile must refuse offline send through engine queue",
                )
            assertEquals(Failure.Refused(RefusalReason.ProfileClosing), failed.failure)
            assertEquals(
                null,
                f.world.pending.value.waitingBubble(),
                "refused send must not add a waiting bubble",
            )
            val failedBubble =
                assertNotNull(
                    f.world.pending.value.singleOrNull(),
                    "refused offline hold must record a Failed bubble",
                )
            assertEquals(PendingState.Failed, failedBubble.state)
            assertEquals(Failure.Refused(RefusalReason.ProfileClosing), failedBubble.failure)
        }

    @Test
    fun `B-500 non-queue backend keeps saved offline entry if dispatch fails on reconnect`() =
        runTest {
            val f = Fixture(this, offlineQueueCapable = false)
            f.openOnline()
            f.goOffline()
            f.world.draftChanged("Survive failed reconnect dispatch")
            assertIs<Outcome.Ok<Unit>>(f.world.send("Survive failed reconnect dispatch"))
            runCurrent()

            f.gateway.onPrompt = { _, _ ->
                Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            }
            f.reconnect()
            advanceTimeBy(1_000)
            runCurrent()
            val saved =
                assertIs<QueueRead.Readable>(OfflineQueueStore(f.kv, f.storage.lanes.app).load())
            assertEquals(
                listOf("Survive failed reconnect dispatch"),
                saved.entries.map { it.text },
                "failed dispatch must never delete the saved offline entry",
            )
            assertNotNull(
                f.world.pending.value.waitingBubble(),
                "failed pre-wire dispatch must remain waiting for next reconnect",
            )

            f.goOffline()
            f.gateway.onPrompt = null
            f.reconnect()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(
                listOf("Survive failed reconnect dispatch"),
                f.base.sentPrompts.map { it.second.text },
            )
            val afterSent =
                assertIs<QueueRead.Readable>(OfflineQueueStore(f.kv, f.storage.lanes.app).load())
            assertTrue(afterSent.entries.isEmpty(), "saved entry is removed only after dispatch accepts")
        }

    @Test
    fun `B-500 non-queue backend unconfirmed manual flush never resends on reconnect or restart`() =
        runTest {
            val stores = Stores()
            val f = Fixture(this, stores = stores, offlineQueueCapable = false)
            f.openOnline()
            f.goOffline()
            f.world.draftChanged("Survive uncertain wire drop")
            assertIs<Outcome.Ok<Unit>>(f.world.send("Survive uncertain wire drop"))
            runCurrent()

            f.gateway.onPrompt = { _, _ -> Outcome.Failed(Failure.Network(NetworkWhy.Offline)) }
            f.reconnect()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(1, f.gateway.promptAttempts.size, "initial flush must try once")
            assertEquals(
                PendingState.Unconfirmed,
                f.world.pending.value.single().state,
                "uncertain post-wire drop must stay unconfirmed instead of auto-replaying",
            )
            val afterUnconfirmed =
                assertIs<QueueRead.Readable>(OfflineQueueStore(f.kv, f.storage.lanes.app).load())
            assertTrue(
                afterUnconfirmed.entries.isEmpty(),
                "unconfirmed manual flush must remove the undispatched saved copy",
            )

            f.gateway.onPrompt = null
            f.registry.close(profile.id)
            runCurrent()
            f.registry.open(profile)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(
                1,
                f.gateway.promptAttempts.size,
                "replacing connection graph must never resend an unconfirmed prompt",
            )
            assertEquals(
                null,
                f.world.pending.value.waitingBubble(),
                "replacing connection graph must not adopt a second waiting bubble",
            )

            f.worlds.release(f.world)
            val reopenedWorld = f.worlds.open(ref)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(
                1,
                f.gateway.promptAttempts.size,
                "reopening the world must never resend an unconfirmed prompt",
            )
            assertEquals(
                null,
                reopenedWorld.pending.value.waitingBubble(),
                "reopening the world must not adopt a second waiting bubble",
            )

            val fresh = Fixture(this, stores = stores, offlineQueueCapable = false)
            fresh.openOnline()
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(
                fresh.gateway.promptAttempts.isEmpty(),
                "fresh restart must send nothing more after unconfirmed manual flush",
            )
            assertEquals(
                null,
                fresh.world.pending.value.waitingBubble(),
                "fresh restart must not adopt a waiting bubble",
            )
        }

    @Test
    fun `B-500 non-queue backend honors disabled automatic queued sends policy`() = runTest {
        val f = Fixture(this, offlineQueueCapable = false)
        val policyJson =
            AutomationPolicy()
                .withBehavior(AutomationBehavior.ReconcileQueuedSends, false)
                .toJson()
                .toString()
        f.kv.commit(f.storage.lanes.app) {
            putString(AutomationPolicyOwner.keyFor(ref.profileId.raw), policyJson)
        }
        f.openOnline()
        f.goOffline()
        f.world.draftChanged("Respect policy off")
        assertIs<Outcome.Ok<Unit>>(f.world.send("Respect policy off"))
        runCurrent()

        f.reconnect()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(
            f.base.sentPrompts.isEmpty(),
            "disabled reconcileQueuedSends must not auto-send on reconnect",
        )
        assertNotNull(f.world.pending.value.waitingBubble(), "message must remain waiting")
    }

    @Test
    fun `B-500 cancelling waiting item while another send is on wire drops bubble`() = runTest {
        val f = Fixture(this, offlineQueueCapable = true)
        f.openOnline()
        f.goOffline()
        f.world.draftChanged("First on wire")
        assertIs<Outcome.Ok<Unit>>(f.world.send("First on wire"))
        runCurrent()
        f.world.draftChanged("Second to cancel")
        assertIs<Outcome.Ok<Unit>>(f.world.send("Second to cancel"))
        runCurrent()

        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        f.gateway.onPrompt = { _, prompt ->
            if (prompt.text == "First on wire") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            null
        }

        f.reconnect()
        runCurrent()
        firstStarted.await()

        val queue = (f.registry.graphFor(ref) as ChatGraphAccessors).chatDelivery.queue!!
        val secondId = queue.entries().first { it.text == "Second to cancel" }.id
        assertEquals(Outcome.Ok(true), queue.remove(secondId))
        runCurrent()

        assertTrue(
            f.world.pending.value.none { it.text == "Second to cancel" },
            "cancelled entry must be dropped instead of stuck Sending while another send is on wire",
        )
        releaseFirst.complete(Unit)
        runCurrent()
    }

    @Test
    fun `B-500 reopened waiting message with same text as prior history refreshes baseline`() =
        runTest {
            val stores = Stores()
            val prior =
                MessageWithParts(
                    MessageInfo("u-old", ref.sessionId.raw, Role.User),
                    persistentListOf(
                        Part("p-old", "u-old", ref.sessionId.raw, PartType.Text, "text", "Run the tests"),
                    ),
                )
            val first = Fixture(this, stores = stores, history = listOf(prior))
            first.openOnline()
            first.goOffline()
            first.world.draftChanged("Run the tests")
            assertIs<Outcome.Ok<Unit>>(first.world.send("Run the tests"))
            runCurrent()
            first.worlds.release(first.world)

            val reopened = Fixture(this, stores = stores, history = listOf(prior))
            reopened.world
            runCurrent()
            assertNotNull(reopened.world.pending.value.waitingBubble())

            reopened.base.events.status.value = StreamStatus.WaitingForNetwork
            reopened.registry.open(profile)
            runCurrent()
            assertTrue(
                reopened.world.transcript.value.rows.isNotEmpty(),
                "prior history must load before reconnect flush",
            )

            reopened.reconnect()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf("Run the tests"), reopened.base.sentPrompts.map { it.second.text })
            val sending = reopened.world.pending.value.singleOrNull()
            assertNotNull(
                sending,
                "sending bubble must not prematurely settle against prior u-old history row",
            )
            assertEquals(PendingState.Sending, sending.state)

            reopened.echoUser("u-new", "Run the tests")
            assertTrue(reopened.world.pending.value.isEmpty(), "bubble settles once u-new arrives")
        }

    private suspend fun seedQueue(f: Fixture, count: Int, startCreatedAt: Long) {
        val store = OfflineQueueStore(f.kv, f.storage.lanes.app)
        val entries =
            List(count) { i ->
                QueuedPrompt(
                    id = "seed-$i",
                    profileId = ref.profileId,
                    sessionId = SessionId("other-session"),
                    text = "Seeded prompt $i",
                    createdAt = startCreatedAt + i,
                )
            }
        assertTrue(store.save(entries), "seeding $count queue entries must succeed")
    }

    @Test
    fun `B-500 non-queue backend retry after server refusal clears saved queue before restart`() =
        runTest {
            val stores = Stores()
            val f = Fixture(this, stores = stores, offlineQueueCapable = false)
            f.openOnline()
            f.goOffline()
            f.world.draftChanged("Retry after refusal")
            assertIs<Outcome.Ok<Unit>>(f.world.send("Retry after refusal"))
            runCurrent()

            f.gateway.onPrompt = { _, _ -> Outcome.Failed(Failure.Rejected(status = 400)) }
            f.reconnect()
            advanceTimeBy(1_000)
            runCurrent()
            val failedBubble =
                assertNotNull(
                    f.world.pending.value.singleOrNull(),
                    "server refusal with status must leave a Failed bubble",
                )
            assertEquals(PendingState.Failed, failedBubble.state)

            f.gateway.onPrompt = null
            assertIs<Outcome.Ok<Unit>>(f.world.retrySend(failedBubble.localId))
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(
                listOf("Retry after refusal"),
                f.base.sentPrompts.map { it.second.text },
                "Try again must send the refused prompt",
            )
            f.echoUser("u-retried", "Retry after refusal")
            assertTrue(f.world.pending.value.isEmpty(), "retried bubble must settle cleanly")

            val fresh = Fixture(this, stores = stores, offlineQueueCapable = false)
            fresh.openOnline()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(
                null,
                fresh.world.pending.value.waitingBubble(),
                "no waiting bubble after restart once Try again succeeded",
            )
            val saved =
                assertIs<QueueRead.Readable>(OfflineQueueStore(fresh.kv, fresh.storage.lanes.app).load())
            assertTrue(saved.entries.isEmpty(), "saved queue must be empty after Try again accepted")
            assertTrue(
                fresh.gateway.promptAttempts.isEmpty(),
                "nothing more may be sent after restart",
            )
        }

    @Test
    fun `B-500 refused offline hold records Failed bubble with reason`() = runTest {
        val fullFixture = Fixture(this)
        seedQueue(
            fullFixture,
            count = QueueLimits.MAX_ENTRIES,
            startCreatedAt = fullFixture.clock.now().toEpochMilliseconds() + 1_000L,
        )
        fullFixture.world
        runCurrent()
        fullFixture.world.draftChanged("51st refused when full")
        val fullResult = fullFixture.world.send("51st refused when full")
        runCurrent()
        assertEquals(
            Failure.Refused(RefusalReason.OfflineQueueFull),
            assertIs<Outcome.Failed>(fullResult).failure,
        )
        val fullBubble =
            assertNotNull(
                fullFixture.world.pending.value.firstOrNull { it.text == "51st refused when full" },
                "full-queue refusal must record a Failed bubble",
            )
        assertEquals(PendingState.Failed, fullBubble.state)
        assertEquals(Failure.Refused(RefusalReason.OfflineQueueFull), fullBubble.failure)

        val slashFixture = Fixture(this)
        slashFixture.openOnline()
        slashFixture.goOffline()
        slashFixture.world.draftChanged("/compact")
        val slashResult = slashFixture.world.send("/compact")
        runCurrent()
        assertEquals(
            Failure.Network(NetworkWhy.Offline),
            assertIs<Outcome.Failed>(slashResult).failure,
        )
        val slashBubble =
            assertNotNull(
                slashFixture.world.pending.value.singleOrNull(),
                "offline slash command must record a Failed bubble",
            )
        assertEquals(PendingState.Failed, slashBubble.state)
        assertEquals(Failure.Network(NetworkWhy.Offline), slashBubble.failure)
    }

    @Test
    fun `B-500 waiting notice omits held offline bubble words while keeping others`() {
        val held = ChatInboxRow("q1", "Hold this offline", local = true, dispatched = false)
        val other = ChatInboxRow("q2", "Another waiting note", local = true, dispatched = false)
        val bubble = PendingSend(1L, "Hold this offline", PendingState.WaitingForConnection)
        assertEquals(
            listOf(other),
            PendingNotice.unsaid(listOf(held, other), listOf(bubble)),
            "waiting line must not repeat words already shown on a held offline bubble",
        )
    }
}
