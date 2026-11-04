package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.store.SecureBytes
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports test/agent_handoff_controller_test.dart. */
class HandoffEngineTest {
    private class Gateway : HandoffGateway, HandoffTargetPort {
        override var directory: String? = "/project"
        override var workspace: String? = null
        override var isClosed: Boolean = false
        override var capabilities = HandoffCapabilities(agentHandoffFiles = true, sessionTodos = false, sessionDiff = false)
        var enabled = true
        val mutations = mutableListOf<String>()
        var beforePrompt: (suspend () -> Unit)? = null
        var writeError: Failure? = null
        var createError: Failure? = null
        var promptError: Failure? = null
        var sessionError: Throwable? = null
        var changedSource = false
        var sourceReads = 0
        var sentText: String? = null
        var sentModel: HandoffModelRef? = null
        var sentAgent: String? = null
        var markdown: String? = null

        override val handoffTarget: HandoffTargetPort get() = this

        override suspend fun providers(): Outcome<List<HandoffProvider>> =
            (if (enabled) listOf(HandoffProvider("anthropic", listOf("anthropic/opus"), mapOf("anthropic/opus" to emptyMap()))) else emptyList()).ok()

        override suspend fun session(id: String): Outcome<HandoffSession> {
            sourceReads++
            sessionError?.let { throw it }
            return HandoffSession(id, title = "Repair parser", directory = directory, updated = if (changedSource && sourceReads > 1) 2 else 1).ok()
        }

        override suspend fun todos(sessionId: String): Outcome<List<HandoffTodo>> = emptyList<HandoffTodo>().ok()
        override suspend fun diff(sessionId: String): Outcome<List<HandoffFileDiff>> = emptyList<HandoffFileDiff>().ok()

        override val historyReader: HandoffHistoryReader = HandoffHistoryReader.Paged { sessionId, _, _ ->
            HandoffHistoryPage(
                listOf(
                    HandoffMessage("m1", sessionId, "user", listOf(HandoffPart("text", "Keep the parser compatible."))),
                    HandoffMessage("m2", sessionId, "assistant", listOf(HandoffPart("text", "The remaining task is the empty-input case."))),
                ),
                hasMore = false,
            ).ok()
        }

        override suspend fun writeRecord(directory: String, recordId: String, markdown: String): Outcome<String> {
            mutations += "write:$directory"
            this.markdown = markdown
            writeError?.let { return it.failed() }
            return ".handoff/$recordId/record.md".ok()
        }

        override suspend fun createSession(): Outcome<HandoffSession> {
            mutations += "create"
            createError?.let { return it.failed() }
            return HandoffSession("draft-new", directory = directory).ok()
        }

        override suspend fun promptHandoff(
            sessionId: String,
            text: String,
            model: HandoffModelRef,
            agent: String?,
            variant: String?,
            beforeSend: () -> Boolean,
        ): Outcome<Unit> {
            beforePrompt?.invoke()
            if (!beforeSend()) return Failure.Refused(RefusalReason.StaleScope).failed()
            mutations += "prompt:$sessionId"
            sentText = text
            sentModel = model
            sentAgent = agent
            promptError?.let { return it.failed() }
            return Unit.ok()
        }

        override fun durableSessionId(sessionId: String): String = "durable-$sessionId"
        override fun navigationSessionId(sessionId: String): String = if (sessionId == "durable-draft-new") "draft-new" else sessionId
    }

    private class Fixture {
        val prefs = FakeAgentPrefs(mapOf("oc.profiles" to """[{"id":"p"},{"id":"q"}]"""))
        val links = HandoffLinks(prefs)
        val source = Gateway()
        val target = Gateway()
        var current = true
        val phases = mutableListOf<HandoffPhase>()

        fun engine(
            filesystemId: String? = "managed-linux",
            useDefaultRecordId: Boolean = false,
            sameParent: Boolean = false,
            sourceOrigin: HandoffOrigin = HandoffOrigin.Server,
            targetOrigin: HandoffOrigin = HandoffOrigin.Server,
        ) = HandoffEngine(
            onPhase = { phases += it },
            source = source,
            sourceScope = HandoffScope("p", "/project", filesystemId = filesystemId, origin = sourceOrigin),
            sourceSessionId = "source",
            target = HandoffTarget(
                gateway = target,
                scope = HandoffScope(if (sameParent) "p" else "q", "/project", filesystemId = filesystemId, origin = targetOrigin),
                model = HandoffModelRef("anthropic", "anthropic/opus"),
                label = "Claude Code",
                agent = "claude",
            ),
            links = links,
            isCurrent = { current },
            recordId = if (useDefaultRecordId) {
                { HandoffEngine.uuidV4(SecureBytes { Random(7).nextBytes(it) }) }
            } else {
                { "review-record" }
            },
        )

        fun linksFor(profile: String, session: String, origin: HandoffOrigin? = null) =
            links.forSession(profile, session, origin) ?: error("links unavailable")
    }

    private fun ready(outcome: Outcome<HandoffPrepared>) = assertIs<HandoffPrepared.Ready>(assertIs<Outcome.Ok<HandoffPrepared>>(outcome).value).draft

    private fun sent(outcome: Outcome<HandoffSent>) = assertIs<HandoffSent.Sent>(assertIs<Outcome.Ok<HandoffSent>>(outcome).value).navigationSessionId

    private fun prepareRefused(kind: HandoffFailureKind, outcome: Outcome<HandoffPrepared>) =
        assertEquals(kind, assertIs<HandoffPrepared.Refused>(assertIs<Outcome.Ok<HandoffPrepared>>(outcome).value).kind)

    private fun sendRefused(kind: HandoffFailureKind, outcome: Outcome<HandoffSent>) =
        assertEquals(kind, assertIs<HandoffSent.Refused>(assertIs<Outcome.Ok<HandoffSent>>(outcome).value).kind)

    @Test
    fun `B-AGENT-014 edited prompt masks credentials while preserving the exact UUID path`() = runTest {
        val f = Fixture()
        val engine = f.engine(useDefaultRecordId = true)
        val draft = ready(engine.prepare("Waiting for you"))
        val edited = "${draft.prompt}\napi_key=synthetic-sensitive-value"
        val reviewed = engine.sanitizePrompt(draft, edited)
        assertContains(reviewed, draft.relativePath)
        assertFalse(reviewed.contains("synthetic-sensitive-value"))
        sent(engine.send(edited))
        assertEquals(reviewed, f.target.sentText)
    }

    @Test
    fun `B-AGENT-014 preparation never exposes raw server errors`() = runTest {
        val f = Fixture()
        f.source.sessionError = IllegalStateException("synthetic-private-server-detail")
        val engine = f.engine()
        val outcome = engine.prepare("Failed")
        prepareRefused(HandoffFailureKind.PreparationFailed, outcome)
        assertEquals(HandoffFailureKind.PreparationFailed, engine.state.value.failure)
        assertFalse(outcome.toString().contains("synthetic-private"))
        assertFalse(engine.state.value.toString().contains("synthetic-private"))
        assertTrue(f.target.mutations.isEmpty())
    }

    @Test
    fun `B-AGENT-014 prepare exposes editable prompt and record without mutations`() = runTest {
        val f = Fixture()
        val engine = f.engine()
        val draft = ready(engine.prepare("Waiting for you"))
        assertEquals(HandoffPhase.Ready, engine.state.value.phase)
        assertContains(draft.prompt, draft.relativePath)
        assertContains(draft.record.markdown, "Keep the parser compatible.")
        assertContains(draft.record.markdown, "empty-input case")
        assertTrue(f.source.mutations.isEmpty())
        assertTrue(f.target.mutations.isEmpty())
        assertFalse("oc.agentHandoffs.p" in f.prefs.values)
    }

    @Test
    fun `B-AGENT-015 send writes record then creates and sends edited prompt before linking canonical IDs`() =
        runTest {
            val f = Fixture()
            val engine = f.engine()
            val draft = ready(engine.prepare("Waiting for you"))
            val edited = "${draft.prompt}\nStart with the empty-input regression."
            assertEquals("draft-new", sent(engine.send(edited)))
            assertEquals("durable-draft-new", engine.state.value.createdSessionId)
            assertEquals(listOf("write:/project", "create", "prompt:draft-new"), f.target.mutations)
            assertEquals(draft.record.markdown, f.target.markdown)
            assertEquals(edited, f.target.sentText)
            assertEquals("opus", f.target.sentModel!!.modelId)
            assertEquals("claude", f.target.sentAgent)
            val link = f.linksFor("p", "durable-source").single()
            assertEquals("durable-draft-new", link.targetSessionId)
            assertEquals("Repair parser", link.sourceTitle)
            assertEquals("review-record", f.linksFor("q", "durable-draft-new").single().id)
            assertEquals(HandoffPhase.Complete, engine.state.value.phase)
            val order = listOf(HandoffPhase.WritingFile, HandoffPhase.Creating, HandoffPhase.Sending, HandoffPhase.Linking, HandoffPhase.Complete)
            assertEquals(order, f.phases.filter { it in order })
        }

    @Test
    fun `B-AGENT-014 default UUID record path survives prompt redaction and sends unchanged`() = runTest {
        val f = Fixture()
        val engine = f.engine(useDefaultRecordId = true)
        val draft = ready(engine.prepare("Idle"))
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(draft.recordId))
        assertEquals("draft-new", sent(engine.send(draft.prompt)))
        assertContains(f.target.sentText!!, draft.relativePath)
        assertEquals(draft.prompt, f.target.sentText)
        assertEquals(draft.recordId, f.linksFor("q", "durable-draft-new").single().id)
    }

    @Test
    fun `B-AGENT-017 server to phone-agent links persist under durable parent with both origins`() = runTest {
        val f = Fixture()
        val engine = f.engine(sameParent = true, sourceOrigin = HandoffOrigin.Server, targetOrigin = HandoffOrigin.PhoneAgent)
        val draft = ready(engine.prepare("Idle"))
        assertEquals("draft-new", sent(engine.send(draft.prompt)))
        val outgoing = f.linksFor("p", "durable-source", HandoffOrigin.Server).single()
        assertEquals(HandoffOrigin.Server, outgoing.sourceOrigin)
        assertEquals(HandoffOrigin.PhoneAgent, outgoing.targetOrigin)
        assertEquals("p", outgoing.targetProfileId)
        assertEquals(outgoing.id, f.linksFor("p", "durable-draft-new", HandoffOrigin.PhoneAgent).single().id)
        assertTrue(f.linksFor("p", "durable-draft-new", HandoffOrigin.Server).isEmpty())
    }

    @Test
    fun `B-AGENT-017 same runtime model choice routes to native switch without file support`() = runTest {
        val f = Fixture()
        f.source.capabilities = HandoffCapabilities(agentHandoffFiles = false)
        val scope = HandoffScope("p", "/project")
        val engine = HandoffEngine(
            source = f.source,
            sourceScope = scope,
            sourceSessionId = "source",
            target = HandoffTarget(f.source, scope, HandoffModelRef("anthropic", "opus"), "Opus", startNewAgent = false),
            links = f.links,
            isCurrent = { true },
            recordId = { "unused" },
        )
        assertEquals(HandoffRoute.SwitchModel, engine.route(HandoffModelRef("openai", "sol")))
        assertTrue(f.source.mutations.isEmpty())
    }

    @Test
    fun `B-AGENT-016 matching folder names without filesystem evidence cannot hand off`() = runTest {
        val f = Fixture()
        val engine = f.engine(filesystemId = null)
        assertEquals(HandoffRoute.Unavailable, engine.route(null))
        prepareRefused(HandoffFailureKind.ProjectMismatch, engine.prepare("Idle"))
        assertTrue(f.target.mutations.isEmpty())
    }

    @Test
    fun `B-AGENT-016 unavailable target model fails before history or remote mutations`() = runTest {
        val f = Fixture()
        f.target.enabled = false
        prepareRefused(HandoffFailureKind.TargetUnavailable, f.engine().prepare("Idle"))
        assertEquals(0, f.source.sourceReads)
        assertTrue(f.target.mutations.isEmpty())
    }

    @Test
    fun `B-AGENT-016 send revalidates model availability after review`() = runTest {
        val f = Fixture()
        val engine = f.engine()
        val draft = ready(engine.prepare("Idle"))
        f.target.enabled = false
        sendRefused(HandoffFailureKind.TargetUnavailable, engine.send(draft.prompt))
        assertTrue(f.target.mutations.isEmpty())
    }

    @Test
    fun `B-AGENT-016 a changed source snapshot is rejected before a draft is offered`() = runTest {
        val f = Fixture()
        f.source.changedSource = true
        val engine = f.engine()
        prepareRefused(HandoffFailureKind.SourceChanged, engine.prepare("Idle"))
        assertNull(engine.state.value.draft)
        assertTrue(f.target.mutations.isEmpty())
    }

    @Test
    fun `B-AGENT-016 new source activity during review requires a fresh handoff`() = runTest {
        val f = Fixture()
        val engine = f.engine()
        val draft = ready(engine.prepare("Idle"))
        f.source.changedSource = true
        sendRefused(HandoffFailureKind.SourceChanged, engine.send(draft.prompt))
        assertTrue(f.target.mutations.isEmpty())
    }

    @Test
    fun `B-AGENT-016 closing the sheet after review prevents all remote mutations`() = runTest {
        val f = Fixture()
        val engine = f.engine()
        val draft = ready(engine.prepare("Idle"))
        f.current = false
        sendRefused(HandoffFailureKind.StaleScope, engine.send(draft.prompt))
        assertTrue(f.target.mutations.isEmpty())
    }

    @Test
    fun `B-AGENT-016 a closed agent connection is named in the details`() = runTest {
        val f = Fixture()
        val engine = f.engine()
        val draft = ready(engine.prepare("Idle"))
        f.target.isClosed = true
        sendRefused(HandoffFailureKind.StaleScope, engine.send(draft.prompt))
        val detail = assertIs<HandoffDetail.Stale>(engine.state.value.detail)
        assertEquals(HandoffStaleReason.AgentConnectionClosed, detail.reason)
        assertEquals("agent connection closed", HandoffWords.stale(detail.reason))
        assertTrue(f.target.mutations.isEmpty())
    }

    @Test
    fun `B-AGENT-016 scope change or profile deletion during target preparation fences prompt dispatch`() = runTest {
        for (deletion in listOf(false, true)) {
            val f = Fixture()
            val engine = f.engine()
            val draft = ready(engine.prepare("Idle"))
            f.target.beforePrompt = {
                if (deletion) f.links.closeProfile("p") else f.target.directory = "/another-project"
            }
            sendRefused(HandoffFailureKind.DeliveryUnknown, engine.send(draft.prompt))
            assertEquals(listOf("write:/project", "create"), f.target.mutations)
            assertNull(f.target.sentText)
            assertEquals("draft-new", engine.state.value.createdSessionId)
            assertFalse("oc.agentHandoffs.p" in f.prefs.values)
        }
    }

    @Test
    fun `B-AGENT-016 creation or delivery timeout cannot create or send twice`() = runTest {
        for (duringCreation in listOf(false, true)) {
            val f = Fixture()
            val engine = f.engine()
            val draft = ready(engine.prepare("Idle"))
            if (duringCreation) f.target.createError = Failure.TimedOut() else f.target.promptError = Failure.TimedOut()
            sendRefused(
                if (duringCreation) HandoffFailureKind.CreationUnknown else HandoffFailureKind.DeliveryUnknown,
                engine.send(draft.prompt),
            )
            assertEquals(HandoffPhase.Uncertain, engine.state.value.phase)
            val previous = f.target.mutations.toList()
            sendRefused(HandoffFailureKind.InvalidDraft, engine.send(draft.prompt))
            sendRefused(HandoffFailureKind.InvalidDraft, engine.retryLink())
            assertEquals(previous, f.target.mutations)
        }
    }

    @Test
    fun `B-AGENT-015 failed record write never creates a target conversation`() = runTest {
        val f = Fixture()
        val engine = f.engine()
        val draft = ready(engine.prepare("Idle"))
        f.target.writeError = Failure.Unexpected()
        sendRefused(HandoffFailureKind.FileWriteFailed, engine.send(draft.prompt))
        assertEquals(listOf("write:/project"), f.target.mutations)
        assertNull(engine.state.value.createdSessionId)
    }

    @Test
    fun `B-AGENT-016 invalid edited prompts remain reviewable and cause no mutations`() = runTest {
        val f = Fixture()
        val engine = f.engine()
        val draft = ready(engine.prepare("Idle"))
        for (text in listOf("", "Continue without the record", "${draft.prompt}${"x".repeat(17_000)}")) {
            sendRefused(HandoffFailureKind.InvalidDraft, engine.send(text))
            assertEquals(HandoffPhase.Ready, engine.state.value.phase)
        }
        assertTrue(f.target.mutations.isEmpty())
    }

    @Test
    fun `B-AGENT-015 link retry saves metadata only after acknowledged delivery`() = runTest {
        val f = Fixture()
        val engine = f.engine()
        val draft = ready(engine.prepare("Idle"))
        f.prefs.refuseWrites = true
        sendRefused(HandoffFailureKind.LinkFailed, engine.send(draft.prompt))
        assertEquals("durable-draft-new", engine.state.value.createdSessionId)
        val previous = f.target.mutations.toList()
        f.prefs.refuseWrites = false
        assertEquals("draft-new", sent(engine.retryLink()))
        assertEquals(HandoffPhase.Complete, engine.state.value.phase)
        assertNull(engine.state.value.failure)
        assertEquals(previous, f.target.mutations)
        assertEquals(1, f.linksFor("q", "durable-draft-new").size)
    }

    @Test
    fun `B-AGENT-014 handoff words cover every unavailable reason in English and Arabic`() {
        for (reason in HandoffUnavailableReason.entries) {
            for (language in HandoffWords.Language.entries) assertTrue(HandoffWords.unavailable(reason, language).isNotBlank())
        }
        for (reason in HandoffStaleReason.entries) {
            for (language in HandoffWords.Language.entries) assertTrue(HandoffWords.stale(reason, language).isNotBlank())
        }
    }
}
