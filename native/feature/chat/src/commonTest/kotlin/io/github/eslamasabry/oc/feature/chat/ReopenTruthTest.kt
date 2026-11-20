package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
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
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ConnectionSession
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.delivery.DeliveryStorage
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileDirectory
import io.github.eslamasabry.oc.engine.requests.ConnectionRequests
import io.github.eslamasabry.oc.engine.transcript.KeptTranscript
import io.github.eslamasabry.oc.engine.transcript.TranscriptAccessors
import io.github.eslamasabry.oc.engine.transcript.TranscriptEngine
import io.github.eslamasabry.oc.engine.transcript.TranscriptHandle
import io.github.eslamasabry.oc.engine.transcript.detachedTranscript
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.feature.chat.presentation.StoppedSteps
import io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatLastKnown
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Release 3.0.0+10030 sanity walk, MAJOR 4: leaving a conversation and coming back drew the page kept from the visit
 * before the server answered, but the turn facts (who ended a step, which files an edit changed) were empty until the
 * live read: a declined step read "1 step · Didn't finish" and "Stopped", an edit lost "Edited 1 file", its file card
 * and "All files". The first frame after a reopen must say what the settled one says.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReopenTruthTest {
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

    /** A server whose history read can be held back (a slow server while the conversation opens again). */
    private class SlowHistory(val base: FakeServerGateway, var messages: List<MessageWithParts>) : ServerGateway by base {
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun messagePage(
            id: SessionId,
            cursor: String?,
            limit: Int,
        ): Outcome<ServerPage<MessageWithParts>> {
            gate?.await()
            return Outcome.Ok(ServerPage(messages.toImmutableList(), null))
        }
    }

    /** The app's last-known store, in memory, through the same JSON the store writes. */
    private class MemoryLastKnown(
        val dispatchers: AppDispatchers,
        val clock: TestClock,
        val scope: CoroutineScope,
    ) : ChatLastKnown {
        var page: String? = null
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        override fun peek(ref: ConversationRef): KeptTranscript? =
            page?.let { json.decodeFromString(KeptTranscript.serializer(), it) }

        override suspend fun read(ref: ConversationRef): KeptTranscript? = peek(ref)

        override suspend fun keep(ref: ConversationRef, title: String?, messages: List<MessageWithParts>) {
            page = KeptTranscript.fit(title, messages, 512 * 1024)?.let { json.encodeToString(KeptTranscript.serializer(), it) }
        }

        override fun detached(ref: ConversationRef): TranscriptHandle =
            detachedTranscript(ref.sessionId.raw, dispatchers, clock, scope)
    }

    private inner class Fixture(val test: TestScope, messages: List<MessageWithParts>) {
        val base = FakeServerGateway(listOf(Session(ref.sessionId, "say hello in one line", time = SessionTime(idleMs = 20))))
        val gateway = SlowHistory(base, messages)
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        val dispatchers = TestDispatchers(dispatcher)
        val clock = TestClock()
        val app = AppCoroutineScope(test.backgroundScope.coroutineContext)
        val memory = MemoryLastKnown(dispatchers, clock, test.backgroundScope)
        val kv =
            PrefsKeyValueStore(
                InMemoryPrefsBackend(),
                FlutterPrefsCodec(JavaListDecoder.Unsupported),
                StateMigrationGate.alwaysOpen(),
                dispatcher,
            )
        val registry =
            ConnectionRegistry(
                setOf(
                    object : GatewayFactory {
                        override val backend = profile.backend

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
        val worlds = GraphChatWorlds(registry, lastKnown = setOf(memory))

        suspend fun connect() {
            base.events.connect()
            registry.open(profile)
            test.runCurrent()
        }
    }

    /**
     * What a turn says once it has ended, as the transcript draws it: its work line (the steps' states, or the
     * reducer's own fallback words when the turn facts know nothing of the turn), its closing line, its footer, and the
     * files its card and "All files" list.
     */
    private data class TurnWords(val work: String?, val end: TurnEndNote?, val footer: String?, val files: List<String>)

    private fun endings(world: ChatWorld): Map<String, TurnWords> {
        val facts = TurnFactsPresenter(TestClock()).present((world as TurnFeedSource).turnFeed.value, nowMs = 0)
        val rows = world.transcript.value.rows.reversed()
        return rows.map { it.turnId }.distinct().associateWith { turn ->
            val work = facts.work[turn]
            val line = rows.filterIsInstance<WorkLineRow>().firstOrNull { it.turnId == turn }
            val footer = rows.filterIsInstance<FooterRow>().firstOrNull { it.turnId == turn }
            val stopped = footer != null && footer.stopped && !StoppedSteps.endedOnRefusal(work)
            TurnWords(
                work =
                    line?.let {
                        if (work != null) "${work.state} ${work.steps.map { s -> s.state }}"
                        else "fallback ${it.state} ${it.calls} ${it.failed}"
                    },
                end = facts.reading.endNotes[turn],
                footer =
                    footer?.let {
                        when {
                            it.failed -> "failed"
                            stopped -> "stopped"
                            else -> "done"
                        }
                    },
                files = if (stopped) emptyList() else facts.results[turn]?.files.orEmpty().map { it.name },
            )
        }
    }

    private class Reopened(
        val live: Map<String, TurnWords>,
        val first: Map<String, TurnWords>,
        val settled: Map<String, TurnWords>,
    )

    /** Visit (the live view), leave, come back while the server has not answered yet, then let it answer. */
    private suspend fun TestScope.reopen(messages: List<MessageWithParts>): Reopened {
        val f = Fixture(this, messages)
        f.connect()
        val visit = f.worlds.open(ref)
        runCurrent()
        val live = endings(visit)
        f.worlds.release(visit)
        runCurrent()
        assertNotNull(f.memory.page, "leaving kept the page")

        val gate = CompletableDeferred<Unit>()
        f.gateway.gate = gate
        val again = f.worlds.open(ref)
        runCurrent()
        val first = endings(again)
        gate.complete(Unit)
        runCurrent()
        val settled = endings(again)
        f.worlds.release(again)
        runCurrent()
        return Reopened(live, first, settled)
    }

    // ---- The conversation the sanity walk used ----

    private fun info(id: String, role: Role, at: Long) =
        MessageInfo(id, ref.sessionId.raw, role, providerId = "opencode", modelId = "big-pickle", time = MsgTime(at, at + 4))

    private fun msg(info: MessageInfo, vararg parts: Part) = MessageWithParts(info, parts.toList().toImmutableList())

    private fun prompt(id: String, at: Long, text: String) =
        msg(info(id, Role.User, at), Part("$id-t", id, ref.sessionId.raw, PartType.Text, "text", text = text))

    private fun words(id: String, mid: String, text: String) =
        Part(id, mid, ref.sessionId.raw, PartType.Text, "text", text = text)

    private fun thought(id: String, mid: String) =
        Part(id, mid, ref.sessionId.raw, PartType.Reasoning, "reasoning", text = "The person wants one file.")

    private fun tool(id: String, mid: String, name: String, status: ToolStatus, input: Map<String, String>, output: String, metadata: JsonObject? = null) =
        Part(
            id,
            mid,
            ref.sessionId.raw,
            PartType.Tool,
            "tool",
            callId = "call-$id",
            toolName = name,
            tool =
                ToolState(
                    status = status,
                    input = JsonObject(input.mapValues { JsonPrimitive(it.value) }),
                    output = output,
                    metadata = metadata,
                    startedAtMs = 100,
                    completedAtMs = 200,
                ),
        )

    /** (b) The agent wrote a new file and said so. */
    private fun edited(at: Long) =
        listOf(
            prompt("u$at", at, "create a file s2.txt with one line"),
            msg(
                info("a$at", Role.Assistant, at + 1).copy(finish = "stop"),
                thought("a$at-r", "a$at"),
                tool(
                    "a$at-w",
                    "a$at",
                    "write",
                    ToolStatus.Completed,
                    mapOf("filePath" to "/root/projects/s2.txt", "content" to "hello\n"),
                    "Wrote file successfully.",
                    JsonObject(mapOf("exists" to JsonPrimitive(false))),
                ),
                words("a$at-t", "a$at", "Created /root/projects/s2.txt with a single line: hello"),
            ),
        )

    /** (a) The person pressed Deny at the permission card; OpenCode 2 ends the reply as aborted. */
    private fun declined(at: Long) =
        listOf(
            prompt("u$at", at, "create a file s2b.txt with one line"),
            msg(
                info("a$at", Role.Assistant, at + 1).copy(errorText = "MessageAbortedError", errorKind = MessageErrorKind.Aborted),
                tool(
                    "a$at-w",
                    "a$at",
                    "write",
                    ToolStatus.Error,
                    mapOf("filePath" to "/root/projects/s2b.txt", "content" to "hello\n"),
                    "The user rejected permission to use this specific tool call.",
                ),
            ),
        )

    /** (c) The person pressed Stop while a command ran. */
    private fun stopped(at: Long) =
        listOf(
            prompt("u$at", at, "count to 200 slowly"),
            msg(
                info("a$at", Role.Assistant, at + 1).copy(errorText = "MessageAbortedError", errorKind = MessageErrorKind.Aborted),
                tool("a$at-b", "a$at", "bash", ToolStatus.Error, mapOf("command" to "seq 200"), "Tool execution interrupted"),
            ),
        )

    /** (d) The provider failed after a step. */
    private fun failed(at: Long) =
        listOf(
            prompt("u$at", at, "read the notes"),
            msg(
                info("a$at", Role.Assistant, at + 1).copy(errorText = "ProviderError: the model is overloaded", errorKind = MessageErrorKind.Unknown),
                tool("a$at-r", "a$at", "read", ToolStatus.Completed, mapOf("filePath" to "/root/projects/notes.md"), "notes"),
            ),
        )

    private fun assertFirstFrameIsTheTruth(r: Reopened) {
        assertTrue(r.first.isNotEmpty(), "the kept page draws at once")
        assertEquals(r.settled.keys, r.first.keys, "the same turns")
        for ((turn, first) in r.first) {
            assertTrue(first.work?.startsWith("fallback") != true, "turn $turn has its facts in the first frame: $first")
            assertEquals(r.settled.getValue(turn), first, "turn $turn says in its first frame what it says settled")
            assertEquals(r.live.getValue(turn), first, "turn $turn says what the live view said before leaving")
        }
    }

    @Test
    fun `REOPEN-3 a declined step reads You declined this from the first frame, never Didn't finish or Stopped`() =
        runTest {
            val r = reopen(edited(10) + declined(20))
            val first = r.first.getValue("u20")
            assertEquals(TurnEndNote.Declined, first.end)
            assertEquals("done", first.footer, "no Stopped beside the decline")
            assertEquals("${WorkLineState.Done} ${listOf(StepState.Declined)}", first.work)
            assertFirstFrameIsTheTruth(r)
        }

    @Test
    fun `REOPEN-4 an edited file keeps Edited 1 file, its card and All files from the first frame`() =
        runTest {
            val r = reopen(edited(10))
            val first = r.first.getValue("u10")
            assertEquals(listOf("s2.txt"), first.files)
            assertEquals("${WorkLineState.Done} ${listOf(StepState.Done)}", first.work)
            assertFirstFrameIsTheTruth(r)
        }

    @Test
    fun `REOPEN-5 a reply the person stopped reads Stopped from the first frame`() =
        runTest {
            val r = reopen(edited(10) + stopped(20))
            val first = r.first.getValue("u20")
            assertEquals("stopped", first.footer)
            assertEquals("${WorkLineState.Stopped} ${listOf(StepState.Stopped)}", first.work)
            assertFirstFrameIsTheTruth(r)
        }

    @Test
    fun `REOPEN-6 a failed reply reads failed from the first frame, with its step done`() =
        runTest {
            val r = reopen(edited(10) + failed(20))
            val first = r.first.getValue("u20")
            assertEquals("failed", first.footer)
            assertEquals("${WorkLineState.Done} ${listOf(StepState.Done)}", first.work)
            assertFirstFrameIsTheTruth(r)
        }

    @Test
    fun `REOPEN-7 on reopen no turn's ending changes between the first frame and the settled one but loading to a word`() =
        runTest {
            for (conversation in
                listOf(
                    edited(10) + declined(20) + stopped(30) + failed(40),
                    failed(10) + stopped(20) + edited(30) + declined(40),
                    declined(10) + edited(20),
                )) {
                val r = reopen(conversation)
                assertEquals(r.settled.keys, r.first.keys)
                for ((turn, settled) in r.settled) {
                    val first = r.first.getValue(turn)
                    // The only change allowed: a closing line the first frame did not know yet (loading) gets its word.
                    assertEquals(settled.copy(end = first.end ?: settled.end), first, "turn $turn")
                    assertTrue(first.work?.startsWith("fallback") != true, "turn $turn: $first")
                }
            }
        }

    // ---- F23 (build 10113, edge-light-show E13): a page kept while its reply still ran ----

    /** The story's prompt and its reply: [done] false = the reply still running (no completed time), as kept 2.2 s in. */
    private fun story(done: Boolean): List<MessageWithParts> =
        listOf(
            prompt("u1", 10, "Write a 900 word story about a fox. No tools."),
            msg(
                MessageInfo(
                    "a1",
                    ref.sessionId.raw,
                    Role.Assistant,
                    providerId = "opencode",
                    modelId = "big-pickle",
                    time = MsgTime(11, if (done) 40 else null),
                    finish = if (done) "stop" else null,
                ),
                thought("a1-r", "a1"),
                *(if (done) arrayOf(words("a1-t", "a1", "The fox named the stars, one by one.")) else emptyArray()),
            ),
        )

    private fun shownWords(world: ChatWorld): List<String> =
        world.transcript.value.rows.mapNotNull {
            when (it) {
                is io.github.eslamasabry.oc.engine.transcript.state.PromptRow -> it.text
                is io.github.eslamasabry.oc.engine.transcript.state.BlockRow -> it.text
                else -> null
            }
        }

    @Test
    fun `B-CHAT-KEPT-001 a page kept while its reply ran is not drawn while the live read is a moment away`() =
        runTest {
            val f = Fixture(this, story(done = false))
            f.connect()
            val visit = f.worlds.open(ref)
            runCurrent()
            f.worlds.release(visit) // left 2.2 s into the reply
            runCurrent()
            assertNotNull(f.memory.page, "leaving kept the page")
            // The reply finished while the person was away; the live read answers 250 ms after the chat opens.
            f.gateway.messages = story(done = true)
            val gate = CompletableDeferred<Unit>()
            f.gateway.gate = gate
            val again = f.worlds.open(ref)
            runCurrent()
            advanceTimeBy(250)
            runCurrent()
            assertTrue(
                shownWords(again).isEmpty(),
                "the page kept mid-reply is behind the server: nothing of it is drawn before the live read (${shownWords(again)})",
            )
            gate.complete(Unit)
            runCurrent()
            assertTrue("The fox named the stars, one by one." in shownWords(again), "the chat opens on the finished reply")
            f.worlds.release(again)
            runCurrent()
        }

    @Test
    fun `B-CHAT-KEPT-002 with no live read the page kept mid-reply is still drawn after a moment`() =
        runTest {
            val f = Fixture(this, story(done = false))
            f.connect()
            val visit = f.worlds.open(ref)
            runCurrent()
            f.worlds.release(visit)
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            f.gateway.gate = gate
            val again = f.worlds.open(ref)
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertTrue(
                "Write a 900 word story about a fox. No tools." in shownWords(again),
                "a server that does not answer never leaves the chat empty: the kept page is drawn",
            )
            gate.complete(Unit)
            runCurrent()
            f.worlds.release(again)
            runCurrent()
        }
}
