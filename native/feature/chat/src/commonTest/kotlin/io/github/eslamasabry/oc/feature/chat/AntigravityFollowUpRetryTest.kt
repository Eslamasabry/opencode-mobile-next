package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.domain.AgentFeaturesGateway
import io.github.eslamasabry.oc.core.domain.CorrelatedPromptGateway
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
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
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-399 (F-044): after a genuine StaleScope failure, Try again stays Sending (never reverted back to Failed by the
 * previous attempt's PutBack outcome) and delivers cleanly, while typed text stays intact and never loops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AntigravityFollowUpRetryTest {
    private val ref = ConversationRef(ProfileId("phone-agents"), SessionId("ag-1"))
    private val model = ModelRef("antigravity", "gemini-3.8-flash-high")
    private val profile =
        ServerProfile(
            ref.profileId,
            "This phone",
            BackendKind.Paseo,
            ServerOrigin("ws", "127.0.0.1", 4099),
            ProfileTrust(null, null),
            false,
            null,
        )

    private val profiles =
        object : ComposerProfiles {
            override fun exists(profileId: String): Boolean = profileId == ref.profileId.raw

            override fun count(): Int = 1
        }

    /** Phone-like immediate main dispatcher so StateFlow collectors run inline inside main-lane updates. */
    @OptIn(InternalCoroutinesApi::class)
    private class ImmediateMain(private val inner: TestDispatcher) : CoroutineDispatcher(), Delay {
        private var onMain = false

        private fun wrap(block: Runnable): Runnable =
            Runnable {
                val prev = onMain
                onMain = true
                try {
                    block.run()
                } finally {
                    onMain = prev
                }
            }

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = !onMain

        override fun dispatch(context: CoroutineContext, block: Runnable) = inner.dispatch(context, wrap(block))

        override fun scheduleResumeAfterDelay(
            timeMillis: Long,
            continuation: kotlinx.coroutines.CancellableContinuation<Unit>,
        ) {
            val handle =
                inner.invokeOnTimeout(
                    timeMillis,
                    wrap(Runnable { with(continuation) { this@ImmediateMain.resumeUndispatched(Unit) } }),
                    continuation.context,
                )
            continuation.invokeOnCancellation { handle.dispose() }
        }

        override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle =
            inner.invokeOnTimeout(timeMillis, wrap(block), context)
    }

    private class AntigravityGateway(val base: FakeServerGateway, var applied: ModelRef?) :
        ServerGateway by base, SessionSelectionGateway, CorrelatedPromptGateway, AgentFeaturesGateway {
        var failNextPrompt: Boolean = false
        var refusal: Failure = Failure.Refused(RefusalReason.StaleScope)
        var holdNextPrompt: CompletableDeferred<Unit>? = null
        var holdNextTransport: CompletableDeferred<Unit>? = null
        private var nextId = 0

        override fun providerIdForSession(session: SessionId): String = "antigravity"

        override suspend fun agentFeatures(session: SessionId) =
            Outcome.Failed(Failure.Unavailable(FeatureId("agentFeatures")))

        override suspend fun setAgentFeature(session: SessionId, featureId: String, value: JsonPrimitive) =
            Outcome.Failed(Failure.Unavailable(FeatureId("agentFeatures")))

        override suspend fun awaitActionTransport(): Outcome<Unit> {
            holdNextTransport?.let {
                holdNextTransport = null
                it.await()
            }
            return base.awaitActionTransport()
        }

        override suspend fun setSessionModel(session: SessionId, model: ModelRef, variant: String): Outcome<Unit> {
            applied = model
            return Outcome.Ok(Unit)
        }

        override suspend fun setSessionAgent(session: SessionId, agent: String): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun createSelectedSession(defaults: SessionSelection): Outcome<Session> =
            base.createSession(NewSession())

        override suspend fun session(id: SessionId): Outcome<Session> =
            when (val read = base.session(id)) {
                is Outcome.Failed -> read
                is Outcome.Ok ->
                    Outcome.Ok(
                        read.value.copy(
                            selection = applied?.let { SessionSelection(it, "", null, true, true) },
                        ),
                    )
            }

        override fun createPromptMessageId(): MessageId = MessageId("msg_client_${++nextId}")

        override suspend fun promptWithMessageId(
            session: SessionId,
            messageId: MessageId,
            prompt: OutgoingPrompt,
            beforeSend: () -> Boolean,
        ): Outcome<PromptAdmission> {
            holdNextPrompt?.let {
                holdNextPrompt = null
                it.await()
            }
            if (!beforeSend()) return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            if (failNextPrompt) {
                failNextPrompt = false
                return Outcome.Failed(refusal)
            }
            return when (val sent = base.prompt(session, prompt, null)) {
                is Outcome.Failed -> sent
                is Outcome.Ok -> Outcome.Ok(PromptAdmission.Confirmed(messageId.raw))
            }
        }
    }

    private inner class Fixture(val test: TestScope) {
        val base = FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20))))
        val gateway = AntigravityGateway(base, model)
        val operations = FakeOperationsGateway()
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        val main = ImmediateMain(dispatcher)
        val dispatchers =
            object : AppDispatchers {
                override val main = this@Fixture.main
                override val default = dispatcher
                override val io = dispatcher

                override fun serial(name: String) = dispatcher
            }
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
                    },
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
                                setOf(DeliveryStorage(kv, DefaultWriterLanes(), ProfileDirectory { true })),
                                dispatchers,
                            )
                        override val chatRequests = ConnectionRequests(pair.live, scope, dispatchers, clock)
                        override val chatGateway = pair.live
                        override val chatOperations = pair.operations
                        override val transcriptEngine = TranscriptEngine(pair.live, dispatchers, clock, scope)
                    }
                },
                app,
                dispatchers,
            )
        val worlds = GraphChatWorlds(registry, setOf(drafts))
        val world: ChatWorld by lazy { worlds.open(ref) }

        suspend fun openAfterFirstTurn() {
            base.capabilities.value =
                base.capabilities.value.copy(
                    serverCatalog = true,
                    promptSteering = true,
                    clientPromptMessageID = true,
                )
            kv.warm()
            registry.open(profile)
            test.runCurrent()
            world
            test.runCurrent()
            // Simulate a finished first Antigravity turn before Bayu types his follow-up.
            base.events.emit(GatewayEvent.MessageUpserted(MessageInfo("u1", ref.sessionId.raw, Role.User)))
            base.events.emit(
                GatewayEvent.PartUpserted(Part("t1", "u1", ref.sessionId.raw, PartType.Text, "text", "first task")),
            )
            base.events.emit(GatewayEvent.MessageUpserted(MessageInfo("a1", ref.sessionId.raw, Role.Assistant)))
            base.events.emit(
                GatewayEvent.PartUpserted(Part("t2", "a1", ref.sessionId.raw, PartType.Text, "text", "Done.")),
            )
            base.events.emit(GatewayEvent.RunEnded(ref.sessionId, RunOutcome.Succeeded))
            test.runCurrent()
            test.advanceTimeBy(60)
            test.runCurrent()
        }
    }

    @Test
    fun `B-399-2a retrySend after async StaleScope PutBack stays Sending while in flight and delivers cleanly`() =
        runTest {
            val f = Fixture(this)
            f.openAfterFirstTurn()
            f.gateway.failNextPrompt = true
            f.world.draftChanged("follow up question")
            assertIs<Outcome.Ok<Unit>>(f.world.send("follow up question"))
            runCurrent()
            advanceTimeBy(60)
            runCurrent()

            val failed = f.world.pending.value.single()
            assertEquals(PendingState.Failed, failed.state)
            assertEquals(Failure.Refused(RefusalReason.StaleScope), failed.failure)
            assertEquals("follow up question", f.world.draft.value.text)
            assertFalse(f.world.draft.value.sendBlocked)

            // Hold the retry in flight: Try again must stay Sending (never flipped back to Failed by stale PutBack).
            val hold = CompletableDeferred<Unit>()
            f.gateway.holdNextPrompt = hold
            assertIs<Outcome.Ok<Unit>>(f.world.retrySend(failed.localId))
            runCurrent()

            val inFlight = f.world.pending.value.single()
            assertEquals(
                PendingState.Sending,
                inFlight.state,
                "Try again must stay Sending while in flight, not revert to Failed",
            )
            assertNull(inFlight.failure)

            hold.complete(Unit)
            runCurrent()
            advanceTimeBy(60)
            runCurrent()

            assertEquals(listOf("follow up question"), f.base.sentPrompts.map { it.second.text })
            assertEquals("", f.world.draft.value.text)
        }

    @Test
    fun `B-399-3a after genuine StaleScope failure typed text is kept and sending again delivers without loop`() =
        runTest {
            val f = Fixture(this)
            f.openAfterFirstTurn()
            f.gateway.failNextPrompt = true
            f.world.draftChanged("second follow up")
            assertIs<Outcome.Ok<Unit>>(f.world.send("second follow up"))
            runCurrent()
            advanceTimeBy(60)
            runCurrent()

            assertEquals(PendingState.Failed, f.world.pending.value.single().state)
            assertEquals("second follow up", f.world.draft.value.text)
            assertFalse(f.world.draft.value.sendBlocked)

            // Sending again from the box retires the failed twin, delivers once, and clears the draft.
            assertIs<Outcome.Ok<Unit>>(f.world.send("second follow up"))
            runCurrent()
            advanceTimeBy(60)
            runCurrent()

            assertEquals(listOf("second follow up"), f.base.sentPrompts.map { it.second.text })
            assertEquals("", f.world.draft.value.text)
            assertTrue(f.world.pending.value.none { it.state == PendingState.Failed })
        }

    @Test
    fun `B-399-3b genuine mid-send folder change still refuses as StaleScope and sends nothing`() = runTest {
        val f = Fixture(this)
        f.openAfterFirstTurn()
        val hold = CompletableDeferred<Unit>()
        f.gateway.holdNextTransport = hold
        f.world.draftChanged("do not cross servers")
        assertIs<Outcome.Ok<Unit>>(f.world.send("do not cross servers"))
        runCurrent()

        // Move the gateway location mid-send (simulating switching away while waking transport).
        f.base.setLocation(ServerLocation(directory = "/other/folder"))
        hold.complete(Unit)
        runCurrent()
        advanceTimeBy(60)
        runCurrent()

        val failed = f.world.pending.value.single()
        assertEquals(PendingState.Failed, failed.state)
        assertEquals(Failure.Refused(RefusalReason.StaleScope), failed.failure)
        assertTrue(f.base.sentPrompts.isEmpty(), "must not deliver after location changed")
        assertEquals("do not cross servers", f.world.draft.value.text, "typed words must not be lost")
    }
}
