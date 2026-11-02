package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
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
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.CatalogModel
import io.github.eslamasabry.oc.core.model.CatalogProvider
import io.github.eslamasabry.oc.core.model.CatalogSnapshot
import io.github.eslamasabry.oc.core.model.ChatDefaults
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.SessionTime
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
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The world slice of the native composer contract (2026-11-02): model choices live in the server
 * session, files ride the same immutable snapshot as the text, and unknown acknowledgements never
 * duplicate a file-only send.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class WorldComposerFilesModelsTest {
    private val ref = ConversationRef(ProfileId("p"), SessionId("s"))
    private val profile =
        ServerProfile(
            ref.profileId,
            "Fixture",
            BackendKind.OpenCode2,
            ServerOrigin("https", "example.invalid", 443),
            ProfileTrust(null, null),
            false,
            null,
        )

    private val profiles =
        object : ComposerProfiles {
            override fun exists(profileId: String): Boolean = profileId in setOf("p", "p2")

            override fun count(): Int = 2
        }

    private fun file(name: String, text: String = "payload") =
        PromptAttachment(
            mime = "text/plain",
            filename = name,
            url = "data:text/plain;base64," + Base64.encode(text.encodeToByteArray()),
        )

    private fun catalogModel(
        id: String,
        provider: String = "prov",
        enabled: Boolean = true,
        attachments: Boolean = true,
        name: String = id,
    ) = CatalogModel(
        id = id,
        providerId = provider,
        name = name,
        family = null,
        enabled = enabled,
        status = "available",
        contextLimit = 128_000,
        outputLimit = 4_096,
        reasoning = false,
        attachments = attachments,
        tools = true,
        variants = persistentListOf(),
        cost = null,
        releasedAtMs = null,
    )

    private fun snapshot(vararg models: CatalogModel) =
        CatalogSnapshot(
            persistentListOf(
                CatalogProvider("prov", "Prov", true, null),
                CatalogProvider("off", "Off", false, null),
            ),
            models.toList().toImmutableList(),
            persistentListOf(),
        )

    private class ScriptedOperations : FakeOperationsGateway() {
        var catalog: Outcome<CatalogSnapshot> =
            Outcome.Failed(Failure.Unavailable(FeatureId("serverCatalog")))
        var defaults: Outcome<ChatDefaults> = Outcome.Ok(ChatDefaults(null, null))

        override suspend fun catalog(): Outcome<CatalogSnapshot> = catalog

        override suspend fun chatDefaults(): Outcome<ChatDefaults> = defaults
    }

    /** The session selection facet: setSessionModel records every call; the readback answers [applied]. */
    private class SelectionGateway(
        val base: FakeServerGateway,
        var applied: ModelRef?,
    ) : ServerGateway by base, SessionSelectionGateway {
        val modelCalls = mutableListOf<ModelRef>()
        var acknowledge: Boolean = true
        var failNextPrompt: Boolean = false

        override suspend fun createSelectedSession(defaults: SessionSelection): Outcome<Session> =
            Outcome.Failed(Failure.Unavailable(FeatureId("createSelectedSession")))

        override suspend fun setSessionAgent(session: SessionId, agent: String): Outcome<Unit> =
            Outcome.Failed(Failure.Unavailable(FeatureId("setSessionAgent")))

        override suspend fun setSessionModel(
            session: SessionId,
            model: ModelRef,
            variant: String,
        ): Outcome<Unit> {
            modelCalls += model
            if (acknowledge) applied = model
            return Outcome.Ok(Unit)
        }

        override suspend fun session(id: SessionId): Outcome<Session> =
            when (val read = base.session(id)) {
                is Outcome.Failed -> read
                is Outcome.Ok ->
                    Outcome.Ok(
                        read.value.copy(
                            selection = applied?.let { SessionSelection(it, "", null, true, false) }
                        )
                    )
            }

        override suspend fun prompt(
            session: SessionId,
            prompt: io.github.eslamasabry.oc.core.model.OutgoingPrompt,
            commandId: String?,
        ): Outcome<PromptAdmission> {
            if (failNextPrompt) {
                failNextPrompt = false
                return Outcome.Failed(Failure.Network(NetworkWhy.Refused))
            }
            return base.prompt(session, prompt, commandId)
        }
    }

    private inner class Fixture(val test: TestScope, selection: ModelRef? = null) {
        val base =
            FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20))))
        val gateway = SelectionGateway(base, selection)
        val operations = ScriptedOperations()
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        val dispatchers = TestDispatchers(dispatcher)
        val clock = TestClock()
        val app = AppCoroutineScope(test.backgroundScope.coroutineContext)
        val kv =
            PrefsKeyValueStore(
                InMemoryPrefsBackend(),
                FlutterPrefsCodec(JavaListDecoder.Unsupported),
                StateMigrationGate.alwaysOpen(),
                dispatcher,
            )
        val drafts =
            ConversationDrafts(
                Drafts(MemoryComposerPrefs(), AttachmentVault(MemoryPayloadStore()), profiles, TestClock()),
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
                        override val session = ConnectionSession(p, pair.live)
                        override val chatDelivery =
                            ConnectionDelivery(
                                pair.live,
                                p,
                                scope,
                                clock,
                                setOf(
                                    DeliveryStorage(
                                        kv,
                                        DefaultWriterLanes(),
                                        ProfileDirectory { true },
                                    )
                                ),
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
        val worlds = GraphChatWorlds(registry, setOf(drafts))
        val world: ChatWorld by lazy { worlds.open(ref) }

        suspend fun open() {
            base.events.connect()
            registry.open(profile)
            world
            test.runCurrent()
        }
    }

    // ---- Model choices ----

    @Test
    fun `B-CHAT-105 loadModels maps only enabled models of enabled providers and never raw config`() =
        runTest {
            val f = Fixture(this, selection = ModelRef("prov", "m1"))
            f.operations.catalog =
                Outcome.Ok(
                    snapshot(
                        catalogModel("m1", name = "Model One"),
                        catalogModel("m2", enabled = false),
                        catalogModel("m3", provider = "off"),
                    )
                )
            f.operations.defaults = Outcome.Ok(ChatDefaults(ModelRef("prov", "m1"), null))
            f.open()
            assertIs<Outcome.Ok<Unit>>(f.world.loadModels())
            val models = f.world.models.value
            assertEquals(listOf("m1"), models.choices.map { it.modelId })
            assertEquals("prov", models.choices.single().providerId)
            assertEquals("Model One", models.choices.single().label)
            assertTrue(models.choices.single().supportsAttachments)
            assertEquals("m1", models.selected?.modelId)
            assertFalse(models.loading)
            assertNull(models.failure)
        }

    @Test
    fun `B-CHAT-105 an absent session model resolves to the catalog default`() = runTest {
        val f = Fixture(this, selection = null)
        f.operations.catalog =
            Outcome.Ok(snapshot(catalogModel("m1"), catalogModel("m2", attachments = false)))
        f.operations.defaults = Outcome.Ok(ChatDefaults(ModelRef("prov", "m2"), null))
        f.open()
        assertIs<Outcome.Ok<Unit>>(f.world.loadModels())
        val selected = f.world.models.value.selected
        assertEquals("m2", selected?.modelId)
        assertFalse(selected!!.supportsAttachments)
    }

    @Test
    fun `B-CHAT-105 a failed catalog load keeps the previous choices and surfaces the failure`() =
        runTest {
            val f = Fixture(this, selection = ModelRef("prov", "m1"))
            f.operations.catalog = Outcome.Ok(snapshot(catalogModel("m1")))
            f.operations.defaults = Outcome.Ok(ChatDefaults(ModelRef("prov", "m1"), null))
            f.open()
            f.world.loadModels()
            f.operations.catalog =
                Outcome.Failed(Failure.Network(NetworkWhy.Refused))
            assertIs<Outcome.Failed>(f.world.loadModels())
            assertEquals(listOf("m1"), f.world.models.value.choices.map { it.modelId })
            assertNotNull(f.world.models.value.failure)
            assertFalse(f.world.models.value.loading)
        }

    @Test
    fun `B-CHAT-105 a choice is published only after the session readback acknowledges it`() =
        runTest {
            val f = Fixture(this, selection = ModelRef("prov", "m1"))
            f.operations.catalog = Outcome.Ok(snapshot(catalogModel("m1"), catalogModel("m2")))
            f.operations.defaults = Outcome.Ok(ChatDefaults(ModelRef("prov", "m1"), null))
            f.open()
            f.world.loadModels()
            val m2 = f.world.models.value.choices.single { it.modelId == "m2" }
            f.gateway.acknowledge = false
            assertIs<Outcome.Failed>(f.world.selectModel(m2))
            assertEquals("m1", f.world.models.value.selected?.modelId)
            f.gateway.acknowledge = true
            assertIs<Outcome.Ok<Unit>>(f.world.selectModel(m2))
            assertEquals(listOf(ModelRef("prov", "m2"), ModelRef("prov", "m2")), f.gateway.modelCalls)
            assertEquals("m2", f.world.models.value.selected?.modelId)
        }

    @Test
    fun `B-CHAT-105 a choice outside the loaded catalog fails closed without a server call`() =
        runTest {
            val f = Fixture(this, selection = ModelRef("prov", "m1"))
            f.operations.catalog = Outcome.Ok(snapshot(catalogModel("m1")))
            f.operations.defaults = Outcome.Ok(ChatDefaults(ModelRef("prov", "m1"), null))
            f.open()
            f.world.loadModels()
            assertIs<Outcome.Failed>(
                f.world.selectModel(ComposerModel("ghost", "ghost", "Ghost", true))
            )
            assertTrue(f.gateway.modelCalls.isEmpty())
            assertEquals("m1", f.world.models.value.selected?.modelId)
        }

    @Test
    fun `B-CHAT-105 a null selection resolves the concrete catalog default or fails closed`() =
        runTest {
            val f = Fixture(this, selection = ModelRef("prov", "m1"))
            f.operations.catalog = Outcome.Ok(snapshot(catalogModel("m1"), catalogModel("m2")))
            f.operations.defaults = Outcome.Ok(ChatDefaults(ModelRef("prov", "m2"), null))
            f.open()
            f.world.loadModels()
            assertIs<Outcome.Ok<Unit>>(f.world.selectModel(null))
            assertEquals(listOf(ModelRef("prov", "m2")), f.gateway.modelCalls)
            assertEquals("m2", f.world.models.value.selected?.modelId)

            val other = Fixture(this, selection = ModelRef("prov", "m1"))
            other.operations.catalog = Outcome.Ok(snapshot(catalogModel("m1")))
            other.operations.defaults = Outcome.Ok(ChatDefaults(null, null))
            other.open()
            other.world.loadModels()
            assertIs<Outcome.Failed>(other.world.selectModel(null))
            assertTrue(other.gateway.modelCalls.isEmpty())
            assertEquals("m1", other.world.models.value.selected?.modelId)
        }

    @Test
    fun `B-CHAT-105 without session model switching the catalog never loads`() = runTest {
        val f = Fixture(this, selection = ModelRef("prov", "m1"))
        f.operations.catalog = Outcome.Ok(snapshot(catalogModel("m1")))
        f.operations.defaults = Outcome.Ok(ChatDefaults(ModelRef("prov", "m1"), null))
        f.open()
        f.base.capabilities.value =
            io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities(
                sessionModelProviderSwitching = false
            )
        assertIs<Outcome.Failed>(f.world.loadModels())
        assertTrue(f.world.models.value.choices.isEmpty())
        assertIs<Outcome.Failed>(f.world.selectModel(null))
        assertTrue(f.gateway.modelCalls.isEmpty())
    }

    // ---- Files on the send path ----

    @Test
    fun `B-CHAT-106 the send captures the exact attachment snapshot with its text`() = runTest {
        val f = Fixture(this)
        f.open()
        f.world.draftChanged("with a file")
        assertIs<Outcome.Ok<Unit>>(f.world.replaceAttachments(listOf(file("notes.txt"))))
        assertIs<Outcome.Ok<Unit>>(f.world.send("with a file"))
        runCurrent()
        val prompt = f.base.sentPrompts.single().second
        assertEquals("with a file", prompt.text)
        assertEquals(listOf("notes.txt"), prompt.attachments.map { it.filename })
    }

    @Test
    fun `B-CHAT-106 an attachment-only send is admitted with empty text`() = runTest {
        val f = Fixture(this)
        f.open()
        assertIs<Outcome.Ok<Unit>>(f.world.replaceAttachments(listOf(file("only.txt"))))
        assertIs<Outcome.Ok<Unit>>(f.world.send(""))
        runCurrent()
        val prompt = f.base.sentPrompts.single().second
        assertTrue(prompt.text.isEmpty())
        assertEquals(listOf("only.txt"), prompt.attachments.map { it.filename })
    }

    @Test
    fun `09-4_2 a blank send without files is refused and never reaches the transport`() = runTest {
        val f = Fixture(this)
        f.open()
        assertIs<Outcome.Failed>(f.world.send("   "))
        assertTrue(f.base.sentPrompts.isEmpty())
        assertTrue(f.world.pending.value.isEmpty())
    }

    @Test
    fun `B-CHAT-107 retry reuses the original files and model, not the current composer`() =
        runTest {
            val f = Fixture(this, selection = ModelRef("prov", "m1"))
            f.operations.catalog = Outcome.Ok(snapshot(catalogModel("m1"), catalogModel("m2")))
            f.operations.defaults = Outcome.Ok(ChatDefaults(ModelRef("prov", "m1"), null))
            f.open()
            assertIs<Outcome.Ok<Unit>>(f.world.loadModels())
            f.world.draftChanged("original words")
            assertIs<Outcome.Ok<Unit>>(f.world.replaceAttachments(listOf(file("first.txt"))))
            f.gateway.failNextPrompt = true
            assertIs<Outcome.Ok<Unit>>(f.world.send("original words"))
            runCurrent()
            advanceTimeBy(60)
            runCurrent()
            val bubble = f.world.pending.value.single { it.state == PendingState.Failed }
            // The person changes everything before tapping Try again.
            f.world.draftChanged("changed mind")
            assertIs<Outcome.Ok<Unit>>(f.world.replaceAttachments(listOf(file("second.txt"))))
            val m2 = f.world.models.value.choices.single { it.modelId == "m2" }
            assertIs<Outcome.Ok<Unit>>(f.world.selectModel(m2))
            assertIs<Outcome.Ok<Unit>>(f.world.retrySend(bubble.localId))
            runCurrent()
            val retry = f.base.sentPrompts.single().second
            assertEquals("original words", retry.text)
            assertEquals(listOf("first.txt"), retry.attachments.map { it.filename })
            assertEquals(ModelRef("prov", "m1"), retry.model?.model)
        }

    @Test
    fun `B-STATE-103 a file-only unconfirmed send never settles on a blank row or duplicates`() =
        runTest {
            val f = Fixture(this)
            f.base.admission = PromptAdmission.Uncertain
            f.open()
            assertIs<Outcome.Ok<Unit>>(f.world.replaceAttachments(listOf(file("unsent.txt"))))
            assertIs<Outcome.Ok<Unit>>(f.world.send(""))
            runCurrent()
            assertEquals(PendingState.Unconfirmed, f.world.pending.value.single().state)
            // A blank-text history row (a file-only prompt from anywhere) must not settle it.
            f.base.events.emit(
                GatewayEvent.MessageUpserted(
                    MessageInfo("blank", ref.sessionId.raw, Role.User)
                )
            )
            f.base.events.emit(
                GatewayEvent.PartUpserted(
                    Part("bp", "blank", ref.sessionId.raw, PartType.Text, "text", "")
                )
            )
            advanceTimeBy(60)
            runCurrent()
            assertEquals(1, f.world.pending.value.size)
            assertEquals(PendingState.Unconfirmed, f.world.pending.value.single().state)
            // The still-attached file cannot be dispatched a second time by accident.
            assertTrue(f.world.draft.value.sendBlocked)
            assertIs<Outcome.Ok<Unit>>(f.world.replaceAttachments(emptyList()))
            assertFalse(f.world.draft.value.sendBlocked)
        }

    // ---- Draft attachment facts ----

    @Test
    fun `B-STATE-104 replaceAttachments publishes the acknowledged snapshot and unsaved compares files`() =
        runTest {
            val f = Fixture(this)
            f.open()
            f.world.draftChanged("keep")
            advanceTimeBy(600)
            runCurrent()
            assertFalse(f.world.draft.value.unsaved)
            assertIs<Outcome.Ok<Unit>>(f.world.replaceAttachments(listOf(file("notes.txt"))))
            assertEquals(listOf("notes.txt"), f.world.draft.value.attachments.map { it.filename })
            assertEquals(
                f.world.draft.value.attachments,
                f.world.draft.value.savedAttachments,
                "only the acknowledged snapshot is published",
            )
            assertFalse(
                f.world.draft.value.toString().contains("notes.txt"),
                "draft facts stay content-free",
            )
            // The Back guard compares text AND files (07 §3.4): files alone are work to lose.
            val stored = file("stored.txt")
            assertFalse(
                DraftFacts(text = "a", saved = "a", attachments = listOf(stored), savedAttachments = listOf(stored)).unsaved
            )
            assertTrue(
                DraftFacts(text = "a", saved = "a", attachments = listOf(stored), savedAttachments = emptyList()).unsaved
            )
            assertFalse(
                DraftFacts(text = "", saved = "", attachments = emptyList(), savedAttachments = emptyList()).unsaved
            )
        }
}
