package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.model.transcript.*
import io.github.eslamasabry.oc.core.testing.*
import io.github.eslamasabry.oc.engine.connection.*
import io.github.eslamasabry.oc.engine.transcript.*
import kotlin.test.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatWorldReviewTest {
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

    private fun message(id: String, text: String) =
        MessageWithParts(
            MessageInfo(id, ref.sessionId.raw, Role.User),
            persistentListOf(Part("t", id, ref.sessionId.raw, PartType.Text, "text", text)),
        )

    private inner class Fixture(val test: TestScope) {
        val base =
            FakeServerGateway(listOf(Session(ref.sessionId, time = SessionTime(idleMs = 20))))
        var beforePrompt: suspend () -> Unit = {}
        var beforeTransport: suspend () -> Unit = {}
        var pages = 0
        var storageEnabled = true
        val kv =
            io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore(
                io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend(),
                io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec(
                    io.github.eslamasabry.oc.core.data.kv.JavaListDecoder.Unsupported
                ),
                io.github.eslamasabry.oc.core.data.migration.StateMigrationGate.alwaysOpen(),
                StandardTestDispatcher(test.testScheduler),
            )
        val stores =
            setOf(
                io.github.eslamasabry.oc.engine.delivery.DeliveryStorage(
                    kv,
                    io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes(),
                    io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory { true },
                )
            )
        var page: suspend (String?) -> ServerPage<MessageWithParts> = {
            ServerPage(persistentListOf(message("old", "continue")), null)
        }
        val gateway =
            object : ServerGateway by base {
                override suspend fun awaitActionTransport(): Outcome<Unit> {
                    beforeTransport()
                    return Outcome.Ok(Unit)
                }

                override suspend fun prompt(
                    session: SessionId,
                    prompt: OutgoingPrompt,
                    commandId: String?,
                ): Outcome<PromptAdmission> {
                    beforePrompt()
                    return base.prompt(session, prompt, commandId)
                }

                override suspend fun messagePage(
                    id: SessionId,
                    cursor: String?,
                    limit: Int,
                ): Outcome<ServerPage<MessageWithParts>> {
                    pages++
                    return Outcome.Ok(page(cursor))
                }
            }
        val operations = FakeOperationsGateway()
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        val dispatchers = TestDispatchers(dispatcher)
        val clock = TestClock()
        val app = AppCoroutineScope(test.backgroundScope.coroutineContext)
        val factory =
            object : GatewayFactory {
                override val backend = profile.backend

                override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
                    Outcome.Failed(Failure.Unexpected())

                override suspend fun connect(spec: GatewaySpec) =
                    Outcome.Ok(GatewayPair(gateway, operations))
            }
        val registry =
            ConnectionRegistry(
                setOf(factory),
                { p, pair, scope ->
                    object : ConnectionGraph, ChatGraphAccessors, TranscriptAccessors {
                        override val profile = p
                        override val scope = scope
                        override val session = ConnectionSession(p, pair.live)
                        override val chatDelivery =
                            io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery(
                                pair.live,
                                p,
                                scope,
                                clock,
                                if (storageEnabled) stores else emptySet(),
                                dispatchers,
                            )
                        override val chatRequests =
                            io.github.eslamasabry.oc.engine.requests.ConnectionRequests(
                                pair.live,
                                scope,
                                dispatchers,
                                clock,
                            )
                        override val chatGateway = pair.live
                        override val chatOperations = pair.operations
                        override val transcriptEngine =
                            TranscriptEngine(pair.live, dispatchers, clock, scope)
                    }
                },
                app,
                dispatchers,
            )
        val worlds = GraphChatWorlds(registry)
        val world: ChatWorld by lazy { worlds.open(ref) }

        suspend fun open() {
            base.events.connect()
            registry.open(profile)
            world
            test.runCurrent()
        }

        fun promptIds() =
            world.transcript.value.rows
                .filterIsInstance<io.github.eslamasabry.oc.engine.transcript.state.PromptRow>()
                .map { it.key }
    }

    @Test
    fun `B-AGENT-243 stopping a waiting draft clears its Sending chat bubble`() = runTest {
        val f = Fixture(this)
        f.open()
        val ready = CompletableDeferred<Unit>()
        f.beforeTransport = { ready.await() }
        assertIs<Outcome.Ok<Unit>>(f.world.send("waiting draft"))
        runCurrent()
        assertEquals(PendingState.Sending, f.world.pending.value.single().state)
        f.clock.advance(
            io.github.eslamasabry.oc.engine.delivery.PromptDelivery.STOP_IGNORE_AFTER_SEND
        )
        f.world.stop()
        runCurrent()
        val bubble = f.world.pending.value.single()
        assertEquals("waiting draft", bubble.text)
        assertEquals(PendingState.Failed, bubble.state, "Stop must settle the matching chat bubble")
        ready.complete(Unit)
        runCurrent()
    }

    @Test
    fun `chat finding 3 close invalidates and reopen rebinds the world`() = runTest {
        val f = Fixture(this)
        f.open()
        f.registry.close(ref.profileId)
        runCurrent()
        assertEquals(ChatLink.Offline, f.world.link.value)
        assertIs<Outcome.Failed>(f.world.stop())
        f.registry.open(profile)
        runCurrent()
        assertEquals(ChatLink.Live, f.world.link.value)
        assertEquals(2, f.pages)
    }

    @Test
    fun `chat finding 4 first reconnect after attachment refetches idle history`() = runTest {
        val f = Fixture(this)
        f.open()
        f.base.events.connect()
        runCurrent()
        assertEquals(2, f.pages)
        f.base.events.emit(
            io.github.eslamasabry.oc.core.domain.events.GatewayEvent.TranscriptStale(ref.sessionId)
        )
        runCurrent()
        assertEquals(3, f.pages)
    }

    @Test
    fun `chat finding 6 historical equal text never settles a new send`() = runTest {
        val f = Fixture(this)
        f.open()
        f.base.admission = PromptAdmission.Uncertain
        f.world.send("continue")
        runCurrent()
        f.base.events.emit(
            io.github.eslamasabry.oc.core.domain.events.GatewayEvent.RunStarted(ref.sessionId)
        )
        advanceTimeBy(60)
        runCurrent()
        assertEquals(1, f.world.pending.value.size)
    }

    @Test
    fun `chat finding 8 older pages remain reachable and search is partial`() = runTest {
        val f = Fixture(this)
        f.page = { cursor ->
            if (cursor == null) ServerPage(persistentListOf(message("recent", "recent")), "older")
            else ServerPage(persistentListOf(message("older", "older")), null)
        }
        f.open()
        assertTrue(f.world.hasOlder.value)
        val nav =
            java.lang.reflect.Proxy.newProxyInstance(
                io.github.eslamasabry.oc.ui.routes.AppNavigator::class.java.classLoader,
                arrayOf(io.github.eslamasabry.oc.ui.routes.AppNavigator::class.java),
            ) { _, _, _ ->
                null
            } as io.github.eslamasabry.oc.ui.routes.AppNavigator
        val presenter =
            io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter(
                f.world,
                nav,
                io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher { block ->
                    backgroundScope.launch { block() }
                },
            )
        presenter.find("older")
        assertTrue(presenter.current().find!!.partial)
        f.world.loadOlder()
        runCurrent()
        assertFalse(presenter.current().find!!.partial)
        assertTrue("prompt:older" in f.promptIds())
    }

    @Test
    fun `B-CHAT-012 chat finding 10 visible chat advances the connection watermark`() = runTest {
        val f = Fixture(this)
        f.open()
        val owner = f.registry.graphFor(ref)!!.session.readState
        val nav =
            java.lang.reflect.Proxy.newProxyInstance(
                io.github.eslamasabry.oc.ui.routes.AppNavigator::class.java.classLoader,
                arrayOf(io.github.eslamasabry.oc.ui.routes.AppNavigator::class.java),
            ) { _, _, _ ->
                null
            } as io.github.eslamasabry.oc.ui.routes.AppNavigator
        val presenter =
            io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter(
                f.world,
                nav,
                io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher { block ->
                    backgroundScope.launch { block() }
                },
            )
        presenter.states(
            backgroundScope,
            kotlin.coroutines.EmptyCoroutineContext,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
        )
        runCurrent()
        assertEquals(20, owner.state.value[ref.sessionId]?.viewedMs)
        assertEquals(false, owner.state.value[ref.sessionId]?.finishedUnseen)
        f.base.sessionList =
            persistentListOf(Session(ref.sessionId, time = SessionTime(idleMs = 40)))
        f.base.events.emit(
            io.github.eslamasabry.oc.core.domain.events.GatewayEvent.RunStarted(ref.sessionId)
        )
        advanceTimeBy(60)
        runCurrent()
        f.base.events.emit(
            io.github.eslamasabry.oc.core.domain.events.GatewayEvent.RunEnded(
                ref.sessionId,
                io.github.eslamasabry.oc.core.domain.events.RunOutcome.Succeeded,
            )
        )
        advanceTimeBy(60)
        runCurrent()
        assertEquals(40, owner.state.value[ref.sessionId]?.viewedMs)
    }

    @Test
    fun `B-CHAT-115 chat finding 8 searches full reasoning tool output and filenames`() = runTest {
        val f = Fixture(this)
        f.page = {
            ServerPage(
                persistentListOf(
                    MessageWithParts(
                        MessageInfo("answer", ref.sessionId.raw, Role.Assistant),
                        persistentListOf(
                            Part(
                                "reason",
                                "answer",
                                ref.sessionId.raw,
                                PartType.Reasoning,
                                "reasoning",
                                "First line\nneedle reasoning",
                            ),
                            Part(
                                "tool",
                                "answer",
                                ref.sessionId.raw,
                                PartType.Tool,
                                "tool",
                                toolName = "read",
                                tool = ToolState(ToolStatus.Completed, output = "needle output"),
                            ),
                            Part(
                                "file",
                                "answer",
                                ref.sessionId.raw,
                                PartType.File,
                                "file",
                                filename = "needle.txt",
                            ),
                        ),
                    )
                ),
                null,
            )
        }
        f.open()
        assertEquals(
            3,
            io.github.eslamasabry.oc.feature.chat.presentation.TranscriptFind.search(
                    f.world.transcript.value,
                    "needle",
                )
                .size,
        )
    }

    @Test
    fun `chat finding 2 composer keeps words until durable delivery admission`() = runTest {
        val f = Fixture(this)
        f.storageEnabled = false
        f.open()
        Dispatchers.setMain(f.dispatcher)
        try {
            val worlds =
                object : ChatWorlds {
                    override fun open(ref: ConversationRef): ChatWorld = f.world

                    override fun release(world: ChatWorld) {}
                }
            val vm =
                io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel(
                    io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget(
                        ref.profileId.raw,
                        ref.sessionId.raw,
                    ),
                    worlds,
                    emptySet(),
                )
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertTrue(vm.state.value.available)
            assertFalse(
                vm.send("keep these words"),
                "without a durable store the composer must not clear",
            )
            runCurrent()
            assertTrue(f.base.sentPrompts.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `chat finding 2 accepted send survives entry destruction and uncertain recovery never resends`() =
        runTest {
            val f = Fixture(this)
            f.base.capabilities.value =
                io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities(
                    offlinePromptQueue = true
                )
            val reply = CompletableDeferred<Unit>()
            f.beforePrompt = { reply.await() }
            f.base.admission = PromptAdmission.Uncertain
            f.open()
            val entry = CoroutineScope(Job() + f.dispatcher)
            val admission = entry.async { f.world.send("survive leaving") }
            runCurrent()
            assertTrue(
                admission.isCompleted,
                "admission waits for durable storage, not the network reply",
            )
            assertIs<Outcome.Ok<*>>(admission.await())
            val delivery = (f.registry.graphFor(ref) as ChatGraphAccessors).chatDelivery
            assertEquals(1, delivery.queue!!.entries().size)
            f.worlds.release(f.world)
            entry.cancel()
            reply.complete(Unit)
            runCurrent()
            val reopened = f.worlds.open(ref)
            runCurrent()
            assertEquals(PendingState.Unconfirmed, reopened.pending.value.single().state)
            assertEquals(1, f.base.sentPrompts.size)
            f.worlds.release(reopened)
            // Reconstruct the feature owner over the connection's durable state, as after owner
            // recreation.
            val recovery = GraphChatWorlds(f.registry).open(ref)
            runCurrent()
            assertEquals(PendingState.Unconfirmed, recovery.pending.value.single().state)
            assertEquals(1, f.base.sentPrompts.size)
        }

    @Test
    fun `chat finding 6 one new canonical prompt settles at most one identical admitted send`() =
        runTest {
            val f = Fixture(this)
            f.open()
            f.world.send("continue")
            f.world.send("continue")
            runCurrent()
            assertEquals(2, f.world.pending.value.size)
            val next = message("new", "continue")
            f.base.events.emit(
                io.github.eslamasabry.oc.core.domain.events.GatewayEvent.MessageUpserted(next.info)
            )
            f.base.events.emit(
                io.github.eslamasabry.oc.core.domain.events.GatewayEvent.PartUpserted(
                    next.parts.single()
                )
            )
            advanceTimeBy(60)
            runCurrent()
            assertEquals(1, f.world.pending.value.size)
            f.base.events.emit(
                io.github.eslamasabry.oc.core.domain.events.GatewayEvent.RunStarted(ref.sessionId)
            )
            advanceTimeBy(60)
            runCurrent()
            assertEquals(1, f.world.pending.value.size)
        }

    @Test
    fun `chat finding 7 the production world owns a permission response model`() = runTest {
        val f = Fixture(this)
        f.open()
        val id = RequestId("ask")
        val ask =
            PermissionRequest(
                id,
                ref.sessionId,
                "edit",
                persistentListOf("*.txt"),
                kotlinx.serialization.json.JsonObject(emptyMap()),
                persistentListOf(),
                null,
                false,
                null,
            )
        f.base.events.emit(
            io.github.eslamasabry.oc.core.domain.events.GatewayEvent.PermissionAsked(ask)
        )
        runCurrent()
        val model = assertNotNull(f.world.requests.value)
        assertEquals(id, model.state.value.card?.id)
        model.allowOnce(id)
        advanceTimeBy(3_100)
        runCurrent()
        assertTrue(f.base.calls.any { it.startsWith("replyPermission") })
    }

    @Test
    fun `chat finding 6 buffered pre-dispatch messages cannot settle a new send`() = runTest {
        val f = Fixture(this)
        f.open()
        val previous = message("buffered", "continue")
        f.base.events.emit(
            io.github.eslamasabry.oc.core.domain.events.GatewayEvent.MessageUpserted(previous.info)
        )
        f.base.events.emit(
            io.github.eslamasabry.oc.core.domain.events.GatewayEvent.PartUpserted(
                previous.parts.single()
            )
        )
        runCurrent() // Applied on the model lane, waiting for the 50 ms presentation flush.
        f.world.send("continue")
        runCurrent()
        advanceTimeBy(60)
        runCurrent()
        assertEquals(1, f.world.pending.value.size)
    }

    @Test
    fun `chat finding 6 initial history must load before an uncorrelated send`() = runTest {
        val f = Fixture(this)
        val history = CompletableDeferred<Unit>()
        f.page = {
            history.await()
            ServerPage(persistentListOf(message("old", "continue")), null)
        }
        f.open()
        assertIs<Outcome.Failed>(f.world.send("continue"))
        assertTrue(f.base.sentPrompts.isEmpty())
        history.complete(Unit)
        runCurrent()
    }

    @Test
    fun `chat finding 3 closed generation preserves one uncertain marker without replay`() =
        runTest {
            val f = Fixture(this)
            f.base.capabilities.value =
                io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities(
                    offlinePromptQueue = true
                )
            f.beforePrompt = { awaitCancellation() }
            f.open()
            assertIs<Outcome.Ok<*>>(f.world.send("retain uncertainty"))
            runCurrent()
            f.registry.close(ref.profileId)
            runCurrent()
            assertEquals(PendingState.Unconfirmed, f.world.pending.value.single().state)
            f.registry.open(profile)
            runCurrent()
            assertEquals(PendingState.Unconfirmed, f.world.pending.value.single().state)
            assertTrue(f.base.sentPrompts.isEmpty())
        }

    @Test
    fun `chat finding 2 admission preserves words typed after send was captured`() = runTest {
        val f = Fixture(this)
        f.open()
        // The field can change while admission waits for the reducer/storage; only that field owns
        // draft text.
        f.world.draftChanged("newer typing")
        assertIs<Outcome.Ok<*>>(f.world.send("captured earlier"))
        assertEquals("newer typing", f.world.draft.value.text)
    }
}
