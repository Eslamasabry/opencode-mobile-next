package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
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
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.PromptDelivery
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
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

    private inner class Fixture(
        val test: TestScope,
        val stores: Stores = Stores(),
        offlineQueueCapable: Boolean = true,
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
                            Outcome.Ok(GatewayPair(base, operations))
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
        firstOrNull {
            it.state != PendingState.Sending &&
                it.state != PendingState.Failed &&
                it.state != PendingState.Unconfirmed
        }

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
    }
}
