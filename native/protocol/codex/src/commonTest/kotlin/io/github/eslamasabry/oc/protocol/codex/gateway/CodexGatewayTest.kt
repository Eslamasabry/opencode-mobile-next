package io.github.eslamasabry.oc.protocol.codex.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.failureOrNull
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.protocol.codex.CodexCapabilities
import io.github.eslamasabry.oc.protocol.codex.CodexFailureKind
import io.github.eslamasabry.oc.protocol.codex.FakeAppServer
import io.github.eslamasabry.oc.protocol.codex.FakeSockets
import io.github.eslamasabry.oc.protocol.codex.codexKind
import io.github.eslamasabry.oc.protocol.codex.gateway
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.params
import io.github.eslamasabry.oc.protocol.codex.serveThread
import io.github.eslamasabry.oc.protocol.codex.str
import io.github.eslamasabry.oc.protocol.codex.thread
import io.github.eslamasabry.oc.protocol.codex.turn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Port of test/codex_gateway_test.dart (gateway cases) and test/codex_project_navigation_test.dart. */
class CodexGatewayTest {
    private val sid = SessionId("thread-1")

    private fun <T> Outcome<T>.kind() = failureOrNull()?.codexKind()

    /** Records the stream status and events of [g]. */
    private class Watch {
        val statuses = mutableListOf<StreamStatus>()
        val events = mutableListOf<GatewayEvent>()
    }

    private fun TestScope.watch(g: CodexGateway): Watch {
        val w = Watch()
        val d = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(d) { g.events.status.collect { w.statuses += it } }
        backgroundScope.launch(d) { g.events.all.collect { w.events += it.event } }
        return w
    }

    private suspend fun TestScope.approvalFor(server: FakeAppServer, g: CodexGateway, requestId: Int) {
        assertIs<Outcome.Ok<*>>(g.messages(sid))
        server.event("turn/started", jsonOf("threadId" to "thread-1", "turn" to turn(status = "inProgress")))
        server.event(
            "item/commandExecution/requestApproval",
            jsonOf("threadId" to "thread-1", "turnId" to "turn-1", "itemId" to "command-1", "command" to "echo fixture", "startedAtMs" to 1000),
            id = requestId,
        )
        runCurrent()
    }

    @Test
    fun `B-AGENT-068 Codex offers no Project tab tools`() {
        val c = CodexCapabilities.Base
        // Exactly the flags the Project tab reads (Flutter ProjectHub.toolsFor): all off, so the tab is absent.
        for ((name, on) in listOf(
            "fileBrowsing" to c.fileBrowsing, "terminal" to c.terminal, "projectManagement" to c.projectManagement,
            "worktreeBrowsing" to c.worktreeBrowsing, "developmentServices" to c.developmentServices,
            "managedWorkspaces" to c.managedWorkspaces,
        )) {
            assertEquals(false, on, name)
        }
        for (flag in listOf(
            c.promptAttachments, c.promptAgentMentions, c.offlinePromptQueue, c.globalSessionSearch, c.sessionDiff,
            c.sessionFork, c.sessionRevert, c.sessionImportExport, c.sessionNotes, c.serverCatalog, c.profileAttentionPolling,
        )) {
            assertEquals(false, flag)
        }
        assertTrue(c.promptSteering)
        assertTrue(c.agentAccount)
    }

    @Test
    fun `B-AGENT-063 foreign project threads cannot enter a scoped list or send path`() = runTest {
        val server = FakeAppServer().serveThread({ thread(cwd = "/another-project") })
        val g = gateway(FakeSockets(server))
        assertEquals(CodexFailureKind.ScopeMismatch, g.sessionPage(null, 100).kind())
        assertTrue(g.prompt(sid, OutgoingPrompt("Hi")) is Outcome.Failed)
        assertTrue(server.calls("turn/start").isEmpty())
        g.close()
    }

    @Test
    fun `B-AGENT-063 resume preserves item ids, timestamps and the cancelled outcome`() {
        val messages = codexMessages(thread(turns = listOf(turn(status = "interrupted"))))
        assertEquals(listOf("user-1", "agent-1"), messages.map { it.info.id })
        assertEquals("Hello back", messages.last().parts.single().text)
        assertEquals(100_000L, messages.last().info.time?.createdMs)
        assertEquals("cancelled", messages.last().info.finish)
    }

    @Test
    fun `B-AGENT-063 a streamed assistant item shares its ids with authoritative history`() = runTest {
        val server = FakeAppServer().serveThread({ thread(turns = listOf(turn())) })
        val g = gateway(FakeSockets(server))
        val w = watch(g)
        g.messages(sid)
        server.event("turn/started", jsonOf("threadId" to "thread-1", "turn" to turn(status = "inProgress")))
        server.event("item/started", jsonOf("threadId" to "thread-1", "turnId" to "turn-1", "item" to mapOf("id" to "agent-1", "type" to "agentMessage", "text" to "")))
        server.event("item/agentMessage/delta", jsonOf("threadId" to "thread-1", "turnId" to "turn-1", "itemId" to "agent-1", "delta" to "Hello back"))
        runCurrent()
        assertEquals("agent-1", w.events.filterIsInstance<GatewayEvent.MessageUpserted>().single().message.id)
        assertEquals("agent-1:0", w.events.filterIsInstance<GatewayEvent.PartDelta>().single().partId.raw)
        val history = assertIs<Outcome.Ok<List<io.github.eslamasabry.oc.core.model.transcript.MessageWithParts>>>(g.messages(sid)).value
        assertEquals("agent-1:0", history.last().parts.single().id)
        g.close()
    }

    @Test
    fun `B-AGENT-064 an approval requires matching scope and allows only one-shot decisions`() = runTest {
        val server = FakeAppServer().serveThread({ thread(turns = listOf(turn())) })
        val g = gateway(FakeSockets(server))
        approvalFor(server, g, 9)
        val request = assertIs<Outcome.Ok<List<io.github.eslamasabry.oc.core.model.PermissionRequest>>>(g.pendingPermissions()).value.single()
        assertEquals("echo fixture", request.metadata.str("command"))
        assertEquals(listOf("echo fixture"), request.patterns)
        assertTrue(g.replyPermission(sid, request.id, PermissionReply.Always) is Outcome.Failed)
        assertTrue(g.replyPermission(SessionId("thread-other"), request.id, PermissionReply.Once) is Outcome.Failed)
        assertIs<Outcome.Ok<Unit>>(g.replyPermission(sid, request.id, PermissionReply.Once))
        runCurrent()
        assertEquals(jsonOf("id" to 9, "result" to mapOf("decision" to "accept")), server.sent.last())
        assertEquals(CodexFailureKind.StaleRequest, g.replyPermission(sid, request.id, PermissionReply.Once).kind())
        g.close()
    }

    @Test
    fun `B-AGENT-064 a location change invalidates a displayed approval`() = runTest {
        val server = FakeAppServer().serveThread({ thread(turns = listOf(turn())) })
        val g = gateway(FakeSockets(server))
        approvalFor(server, g, 7)
        val request = (g.pendingPermissions() as Outcome.Ok).value.single()
        g.setLocation(ServerLocation(directory = "/new-project"))
        assertTrue(g.replyPermission(sid, request.id, PermissionReply.Once) is Outcome.Failed)
        assertTrue((g.pendingPermissions() as Outcome.Ok).value.isEmpty())
        assertTrue(server.sent.none { it["id"]?.toString() == "7" && it.containsKey("result") })
        g.close()
    }

    @Test
    fun `B-AGENT-064 socket loss invalidates a displayed approval`() = runTest {
        val server = FakeAppServer().serveThread({ thread(turns = listOf(turn())) })
        val g = gateway(FakeSockets(server))
        approvalFor(server, g, 8)
        val request = (g.pendingPermissions() as Outcome.Ok).value.single()
        server.drop()
        runCurrent()
        assertEquals(CodexFailureKind.StaleRequest, g.replyPermission(sid, request.id, PermissionReply.Once).kind())
        assertTrue((g.pendingPermissions() as Outcome.Ok).value.isEmpty())
        g.close()
    }

    /** Recovery fixture: the replacement socket fails [failMethod] generically, or (null) answers resume with another id. */
    private fun recovery(failMethod: String?): Pair<FakeAppServer, FakeAppServer> {
        val first = FakeAppServer().serveThread({ thread(turns = listOf(turn())) })
        val second = FakeAppServer().serveThread({ thread(turns = listOf(turn())) })
        if (failMethod != null) second.errors[failMethod] = -32603
        else second.answers["thread/resume"] = { jsonOf("thread" to JsonObject(thread() + ("id" to kotlinx.serialization.json.JsonPrimitive("other-thread")))) }
        return first to second
    }

    @Test
    fun `B-AGENT-065 a partial reconnect stays degraded when a tracked thread cannot resume`() = runTest {
        val (first, second) = recovery(null)
        val g = gateway(FakeSockets(first, second))
        val w = watch(g)
        g.startListening()
        runCurrent()
        g.messages(sid)
        first.drop()
        advanceTimeBy(1100)
        runCurrent()
        assertTrue(w.statuses.any { it is StreamStatus.Reconnecting })
        assertEquals(1, w.statuses.count { it == StreamStatus.Connected })
        assertIs<StreamStatus.Reconnecting>(w.statuses.last())
        g.close()
    }

    @Test
    fun `B-AGENT-065 a generic read or resume failure keeps reconnect degraded`() = runTest {
        for (method in listOf("thread/read", "thread/resume")) {
            val (first, second) = recovery(method)
            val g = gateway(FakeSockets(first, second))
            val w = watch(g)
            g.startListening()
            runCurrent()
            g.messages(sid)
            first.drop()
            advanceTimeBy(1100)
            runCurrent()
            assertTrue(g.transport.connected, method)
            assertEquals(1, second.calls(method).size, method)
            assertIs<StreamStatus.Reconnecting>(w.statuses.last(), method)
            assertEquals(1, w.statuses.count { it == StreamStatus.Connected }, method)
            assertTrue(second.calls("thread/start").isEmpty() && second.calls("turn/start").isEmpty(), method)
            g.close()
        }
    }

    @Test
    fun `B-AGENT-065 a reconnect authentication failure reaches the error channel`() = runTest {
        val first = FakeAppServer()
        val sockets = FakeSockets(first).apply { refuseAfterFirst = CodexFailureKind.Authentication }
        val g = gateway(sockets)
        val w = watch(g)
        g.startListening()
        runCurrent()
        assertTrue(g.transport.connected)
        first.drop()
        advanceTimeBy(1100)
        runCurrent()
        val stopped = w.statuses.filterIsInstance<StreamStatus.Stopped>().single()
        assertEquals(io.github.eslamasabry.oc.core.common.Failure.SignIn(SignInWhy.TokenRejected), stopped.failure)
        assertEquals(CodexFailureKind.Authentication, stopped.failure.codexKind())
        g.close()
    }

    /** Dart `_expectReadMutationScopeRace`: the read is held, the location changes, the mutation never goes out. */
    private suspend fun TestScope.scopeRace(forbidden: String, activeTurn: Boolean = false, operation: suspend (CodexGateway) -> Outcome<*>) {
        val server = FakeAppServer().serveThread({ thread(turns = listOf(turn(status = if (activeTurn) "inProgress" else "completed"))) })
        server.gated += "thread/read"
        val g = gateway(FakeSockets(server))
        val pending = async { operation(g) }
        runCurrent()
        assertEquals(1, server.calls("thread/read").size)
        g.setLocation(ServerLocation(directory = "/replacement"))
        server.release("thread/read")
        runCurrent()
        assertEquals(CodexFailureKind.ScopeMismatch, pending.await().failureOrNull()?.codexKind(), forbidden)
        assertTrue(server.calls(forbidden).isEmpty(), forbidden)
        g.close()
    }

    @Test
    fun `B-AGENT-065 read to resume, delete, rename and abort never cross a location change`() = runTest {
        scopeRace("thread/resume") { it.messages(sid) }
        scopeRace("thread/delete") { it.deleteSession(sid) }
        scopeRace("thread/name/set") { it.renameSession(sid, "renamed") }
        scopeRace("turn/interrupt", activeTurn = true) { it.stop(sid) }
    }

    /** Dart `_UncertainNewThreadFixture`: the first turn on a new thread loses its reply. */
    private class NewThread(failEmptyReads: Boolean = false) {
        var current: JsonObject = thread(turns = emptyList())
        var loseNextTurn = false
        val first = FakeAppServer()
        val replacement = FakeAppServer()

        init {
            for (s in listOf(first, replacement)) {
                s.answers["thread/start"] = { jsonOf("thread" to current) }
                val read: (JsonObject) -> JsonObject? = { frame ->
                    val id = frame.params().str("threadId")
                    when {
                        id == "thread-durable" -> jsonOf("thread" to JsonObject(thread(turns = listOf(turn())) + ("id" to kotlinx.serialization.json.JsonPrimitive(id))))
                        failEmptyReads && (current["turns"] as kotlinx.serialization.json.JsonArray).isEmpty() -> {
                            s.push(jsonOf("id" to frame["id"], "error" to mapOf("code" to -32603, "message" to "Synthetic read failure")))
                            null
                        }
                        else -> jsonOf("thread" to current)
                    }
                }
                s.answers["thread/read"] = read
                s.answers["thread/resume"] = read
                s.answers["turn/start"] = {
                    if (loseNextTurn) {
                        loseNextTurn = false
                        if (!failEmptyReads) current = thread(turns = listOf(turn()))
                        s.drop()
                        null
                    } else {
                        jsonOf("turn" to turn(status = "inProgress"))
                    }
                }
            }
        }

        val sockets = object : io.github.eslamasabry.oc.protocol.codex.transport.CodexSocketFactory {
            override suspend fun open(endpoint: io.github.eslamasabry.oc.protocol.codex.CodexEndpoint, secret: String) =
                if (first.closed) replacement else first
        }
    }

    @Test
    fun `B-AGENT-062 an uncertain first turn on a new thread requires history recovery instead of resend`() = runTest {
        val f = NewThread()
        val g = gateway(f.sockets)
        val created = (g.createSession(io.github.eslamasabry.oc.core.model.NewSession()) as Outcome.Ok).value
        f.loseNextTurn = true
        assertEquals(Outcome.Ok(PromptAdmission.Uncertain), g.prompt(created.id, OutgoingPrompt("first attempt")))
        assertEquals(1, f.first.calls("turn/start").size)
        // Until history is recovered, a send is refused without dispatch.
        assertEquals(CodexFailureKind.DeliveryUnknown, g.prompt(created.id, OutgoingPrompt("again")).kind())
        val recovered = (g.messages(created.id) as Outcome.Ok).value
        assertTrue(recovered.isNotEmpty())
        assertEquals(1, f.replacement.calls("thread/read").size)
        assertEquals(1, f.replacement.calls("thread/resume").size)
        assertTrue(f.replacement.calls("turn/start").isEmpty())
        assertIs<PromptAdmission.Confirmed>((g.prompt(created.id, OutgoingPrompt("after recovery")) as Outcome.Ok).value)
        assertEquals(1, f.replacement.calls("turn/start").size)
        g.close()
    }

    @Test
    fun `B-AGENT-065 a never-dispatched empty thread retires before recovery and an uncertain one blocks it`() = runTest {
        for (uncertainFirstTurn in listOf(false, true)) {
            val f = NewThread(failEmptyReads = true)
            val g = gateway(f.sockets)
            val w = watch(g)
            g.startListening()
            runCurrent()
            g.messages(SessionId("thread-durable"))
            val created = (g.createSession(io.github.eslamasabry.oc.core.model.NewSession()) as Outcome.Ok).value
            if (uncertainFirstTurn) {
                f.loseNextTurn = true
                assertEquals(Outcome.Ok(PromptAdmission.Uncertain), g.prompt(created.id, OutgoingPrompt("First attempt")))
            } else {
                f.first.drop()
            }
            advanceTimeBy(1100)
            runCurrent()
            val label = "uncertain=$uncertainFirstTurn"
            if (uncertainFirstTurn) assertIs<StreamStatus.Reconnecting>(w.statuses.last(), label) else assertEquals(StreamStatus.Connected, w.statuses.last(), label)
            assertEquals(1, f.replacement.calls("thread/resume").count { it.params().str("threadId") == "thread-durable" }, label)
            assertEquals(if (uncertainFirstTurn) 1 else 0, f.replacement.calls("thread/read").count { it.params().str("threadId") == created.id.raw }, label)
            assertTrue(f.replacement.calls("thread/resume").none { it.params().str("threadId") == created.id.raw }, label)
            assertTrue(f.replacement.calls("thread/start").isEmpty() && f.replacement.calls("turn/start").isEmpty(), label)
            assertTrue(w.events.none { it is GatewayEvent.SessionDeleted }, label)
            assertTrue(created.id in (g.sessionStatuses() as Outcome.Ok).value, label)
            if (!uncertainFirstTurn) {
                // Retired metadata cannot satisfy explicit details or history through the empty-thread shortcut.
                for (access in listOf<suspend () -> Outcome<*>>({ g.session(created.id) }, { g.messages(created.id) })) {
                    val before = f.replacement.sent.size
                    assertTrue(access() is Outcome.Failed, label)
                    assertEquals(before + 1, f.replacement.sent.size, label)
                    assertEquals("thread/read", f.replacement.sent.last().str("method"), label)
                }
            }
            val readsBefore = f.replacement.calls("thread/read").size
            val explicit = g.prompt(created.id, OutgoingPrompt("Explicit retry"))
            assertEquals(if (uncertainFirstTurn) CodexFailureKind.DeliveryUnknown else CodexFailureKind.Unavailable, explicit.kind(), label)
            assertEquals(readsBefore + if (uncertainFirstTurn) 0 else 1, f.replacement.calls("thread/read").size, label)
            assertTrue(f.replacement.calls("turn/start").isEmpty(), label)
            g.close()
        }
    }

    @Test
    fun `B-AGENT-064 an old-epoch approval request id cannot be answered after a reconnect`() = runTest {
        val first = FakeAppServer().serveThread({ thread(turns = listOf(turn())) })
        val second = FakeAppServer().serveThread({ thread(turns = listOf(turn())) })
        val g = gateway(FakeSockets(first, second))
        approvalFor(first, g, 5)
        val request = (g.pendingPermissions() as Outcome.Ok).value.single()
        first.drop()
        runCurrent()
        g.health()
        assertEquals(CodexFailureKind.StaleRequest, g.replyPermission(sid, request.id, PermissionReply.Once).kind())
        assertEquals(CodexFailureKind.StaleRequest, g.replyPermission(sid, RequestId("codex:1:n:5"), PermissionReply.Once).kind())
        assertTrue(second.sent.none { it.containsKey("result") })
        g.close()
    }
}

