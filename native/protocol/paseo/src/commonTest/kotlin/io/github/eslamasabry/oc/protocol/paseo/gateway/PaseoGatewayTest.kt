@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.FakeSockets
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from test/paseo_gateway_test.dart (handshake listings, new conversation, streaming, history, lifecycle). */
class PaseoGatewayTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private fun TestScope.harness(daemon: FakeDaemon = FakeDaemon().seedDefaults()) = GatewayHarness(this, daemon)

    // ---- B-AGENT-162 handshake and listings

    @Test
    fun `B-AGENT-162 sessions are this folder's unarchived agents`() = runTest {
        val h = harness()
        val sessions = h.gateway.allSessions().ok()
        assertEquals(listOf("a1"), sessions.map { it.id.raw })
        assertEquals("Agent a1", sessions.single().title)
        assertEquals("claude/claude-haiku-4-5", sessions.single().model)
        assertEquals(0.04, sessions.single().cost)
        assertEquals("opencode-mobile", h.daemon.of("fetch_agents_request").single().obj("subscribe")?.str("subscriptionId"))
    }

    @Test
    fun `B-AGENT-162 an update keeps the conversation's model and mode`() = runTest {
        val h = harness()
        h.gateway.allSessions().ok()
        h.events.clear()
        h.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to agentJson("a1", status = "running")))
        h.pump()
        val session = h.events.filterIsInstance<GatewayEvent.SessionUpserted>().first().session
        assertEquals("claude-haiku-4-5", session.selection?.model?.modelId)
        assertEquals("default", session.selection?.agent)
        assertTrue(session.selection?.modelKnown == true)
    }

    @Test
    fun `B-AGENT-162 ready runtimes become providers and modes become agents`() = runTest {
        val h = harness()
        val response = h.gateway.providers().ok()
        assertEquals(listOf("claude", "pi"), response.providers.map { it.id })
        assertEquals("Claude Code", response.providers.first().name)
        assertEquals(listOf("opus", "haiku"), response.providers.first().modelIds)
        assertEquals(listOf(PaseoGateway.DEFAULT_MODEL), response.providers.last().modelIds)
        assertEquals("claude", response.defaultProviderId)
        assertEquals("haiku", response.defaultModelId)
        assertEquals(listOf("plan", "default"), h.gateway.agents().ok().map { it.name })
        val defaults = h.gateway.chatDefaults().ok()
        assertEquals("default", defaults.agent)
        assertEquals("haiku", defaults.model?.modelId)
    }

    @Test
    fun `B-AGENT-162 a daemon still listing its models is waited for, not believed`() = runTest {
        val h = harness()
        val settled = h.daemon.handlers.getValue("get_providers_snapshot_request")
        var reads = 0
        h.daemon.handlers["get_providers_snapshot_request"] = { message ->
            reads++
            if (reads < 3) {
                "get_providers_snapshot_response" to jsonOf(
                    "entries" to listOf(mapOf("provider" to "claude", "status" to "loading")),
                    "generatedAt" to "2026-09-19T03:35:27.840Z",
                )
            } else {
                settled(message)
            }
        }
        val defaults = h.gateway.chatDefaults().ok()
        assertEquals(3, reads)
        assertEquals(ModelRef("claude", "haiku"), defaults.model)
        // Settled snapshots are cached; a loading one never is.
        h.gateway.providers().ok()
        assertEquals(3, reads)
    }

    @Test
    fun `B-AGENT-162 health reports the daemon's own version`() = runTest {
        val h = harness(FakeDaemon().seedDefaults().apply { version = "0.11.2" })
        assertEquals("0.11.2", h.gateway.health().ok().version)
    }

    // ---- B-AGENT-163 a new conversation

    private suspend fun GatewayHarness.create(before: List<() -> Unit> = emptyList()): SessionId {
        daemon.handlers["create_agent_request"] = {
            before.forEach { it() }
            "status" to jsonOf("status" to "agent_created", "agent" to agentJson("real-1", status = "initializing"))
        }
        val draft = gateway.createSession(NewSession()).ok().id
        val sent = gateway.prompt(draft, OutgoingPrompt("hello", ModelSelection(ModelRef("claude", "haiku")), agent = "plan")).ok()
        assertEquals(PromptAdmission.Confirmed(null), sent)
        return draft
    }

    @Test
    fun `B-AGENT-163 a new conversation stays local until its first prompt names the runtime`() = runTest {
        val h = harness()
        val draft = h.gateway.createSession(NewSession()).ok()
        assertTrue(h.daemon.of("create_agent_request").isEmpty())
        assertTrue(h.gateway.messagePage(draft.id, null, 100).ok().items.isEmpty())
        assertEquals(draft.id, h.gateway.allSessions().ok().first().id)
        h.gateway.providers().ok()
        h.create()
        val request = h.daemon.of("create_agent_request").single()
        assertEquals(
            jsonOf("provider" to "claude", "cwd" to DIR, "model" to "haiku", "modeId" to "plan"),
            request.obj("config"),
        )
        assertEquals("hello", request.str("initialPrompt"))
    }

    @Test
    fun `B-AGENT-163 keeps its app id while the daemon uses its own`() = runTest {
        val h = harness()
        val draft = h.create()
        h.daemon.handlers["fetch_agent_request"] = { m ->
            assertEquals("real-1", m.str("agentId"))
            "fetch_agent_response" to jsonOf("agent" to agentJson("real-1"))
        }
        assertEquals(draft, h.gateway.session(draft).ok().id)
        h.daemon.handlers["send_agent_message_request"] = {
            "send_agent_message_response" to jsonOf("agentId" to "real-1", "accepted" to true, "error" to null)
        }
        h.gateway.prompt(draft, OutgoingPrompt("again")).ok()
        val send = h.daemon.of("send_agent_message_request").single()
        assertEquals("real-1", send.str("agentId"))
        assertEquals("steer", send.str("activeTurnBehavior"))
        assertEquals("real-1", h.gateway.daemonSessionId(draft).raw)
        h.events.clear()
        h.daemon.push("agent_stream", stream("real-1", turn("turn_started")))
        h.daemon.push(
            "agent_stream",
            stream("real-1", timeline(jsonOf("type" to "assistant_message", "text" to "Hi", "messageId" to "msg_1")), seq = 2),
        )
        h.pump()
        val updated = h.events.filterIsInstance<GatewayEvent.MessageUpserted>().first()
        assertEquals(draft.raw, updated.message.sessionId)
    }

    @Test
    fun `B-AGENT-163 replays events that raced ahead of the create answer`() = runTest {
        val h = harness()
        val draft = h.create(
            before = listOf(
                { h.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to agentJson("real-1", status = "running"))) },
                {
                    h.daemon.push(
                        "agent_stream",
                        stream("real-1", timeline(jsonOf("type" to "user_message", "text" to "hello", "messageId" to "u1")), seq = 1),
                    )
                },
            ),
        )
        h.pump()
        // No second session under the daemon's id; the early message lands on the shown conversation.
        assertTrue(h.events.filterIsInstance<GatewayEvent.SessionUpserted>().none { it.session.id.raw == "real-1" })
        assertEquals(draft.raw, h.events.filterIsInstance<GatewayEvent.MessageUpserted>().first().message.sessionId)
        assertTrue(h.gateway.allSessions().ok().none { it.id.raw == "real-1" })
    }

    @Test
    fun `B-AGENT-163 a spawn-time idle snapshot does not end the run`() = runTest {
        val h = harness()
        val draft = h.create()
        h.events.clear()
        h.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to agentJson("real-1", status = "idle")))
        h.daemon.push("agent_stream", stream("real-1", turn("turn_started")))
        // Snapshots lag the stream: this idle describes the moment before.
        h.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to agentJson("real-1", status = "idle")))
        h.pump()
        assertTrue(h.events.none { it is GatewayEvent.RunEnded })
        assertEquals(SessionStatus.Busy, h.gateway.sessionStatuses().ok()[draft])
        h.daemon.push("agent_stream", stream("real-1", turn("turn_completed")))
        h.pump()
        assertTrue(h.events.any { it is GatewayEvent.RunEnded && it.outcome == RunOutcome.Succeeded })
        assertEquals(SessionStatus.Idle, h.gateway.sessionStatuses().ok()[draft])
    }

    // ---- B-CHAT-070 streaming

    private suspend fun TestScope.streaming(): GatewayHarness {
        val h = harness()
        h.gateway.allSessions().ok()
        h.events.clear()
        return h
    }

    private fun assistant(text: String, id: String) = timeline(jsonOf("type" to "assistant_message", "text" to text, "messageId" to id))

    @Test
    fun `B-CHAT-070 a finished turn finishes its replies`() = runTest {
        val h = streaming()
        h.daemon.push("agent_stream", stream("a1", assistant("Created flow1.txt", "msg_9"), seq = 30))
        h.pump()
        assertNull(h.events.filterIsInstance<GatewayEvent.MessageUpserted>().last().message.time?.completedMs)
        h.daemon.push("agent_stream", stream("a1", turn("turn_completed"), seq = 31))
        h.pump()
        val finished = h.events.filterIsInstance<GatewayEvent.MessageUpserted>().last { it.message.id == "msg_9" }
        assertTrue(finished.message.time?.completedMs != null)
    }

    @Test
    fun `B-CHAT-070 assistant text is announced once, then streamed as deltas`() = runTest {
        val h = streaming()
        listOf("Done", ". The file", " exists.").forEachIndexed { i, text ->
            h.daemon.push("agent_stream", stream("a1", assistant(text, "msg_1"), seq = 10L + i))
        }
        h.pump()
        assertEquals(listOf("MessageUpserted", "PartUpserted", "PartDelta", "PartDelta"), h.types())
        assertEquals("Done", (h.events[1] as GatewayEvent.PartUpserted).part.text)
        val delta = h.events[2] as GatewayEvent.PartDelta
        assertEquals("msg_1:0", delta.partId.raw)
        assertEquals(DeltaField.Text, delta.field)
        assertEquals(" exists.", (h.events[3] as GatewayEvent.PartDelta).delta)
    }

    @Test
    fun `B-CHAT-070 a replayed sequence number is ignored`() = runTest {
        val h = streaming()
        val frame = stream("a1", assistant("Hi", "m"), seq = 5)
        h.daemon.push("agent_stream", frame)
        h.daemon.push("agent_stream", frame)
        h.pump()
        assertEquals(listOf("MessageUpserted", "PartUpserted"), h.types())
    }

    @Test
    fun `B-CHAT-070 reasoning deltas share one message until another item`() = runTest {
        val h = streaming()
        h.daemon.push("agent_stream", stream("a1", timeline(jsonOf("type" to "reasoning", "text" to "Let me")), seq = 1))
        h.daemon.push("agent_stream", stream("a1", timeline(jsonOf("type" to "reasoning", "text" to " think")), seq = 2))
        h.daemon.push(
            "agent_stream",
            stream(
                "a1",
                timeline(
                    jsonOf(
                        "type" to "tool_call", "callId" to "toolu_1", "name" to "Bash", "status" to "running",
                        "detail" to mapOf("type" to "shell", "command" to "ls"),
                    ),
                ),
                seq = 3,
            ),
        )
        h.daemon.push("agent_stream", stream("a1", timeline(jsonOf("type" to "reasoning", "text" to "Next")), seq = 4))
        h.pump()
        assertEquals(listOf("r:1", "toolu_1", "r:4"), h.events.filterIsInstance<GatewayEvent.MessageUpserted>().map { it.message.id })
        val delta = h.events.filterIsInstance<GatewayEvent.PartDelta>().single()
        assertEquals("r:1", delta.messageId.raw)
        assertEquals(DeltaField.Reasoning, delta.field)
    }

    @Test
    fun `B-CHAT-070 a tool call is one message updated by snapshots`() = runTest {
        val h = streaming()
        fun call(status: String, output: String? = null) = timeline(
            jsonOf(
                "type" to "tool_call", "callId" to "toolu_9", "name" to "Bash", "status" to status, "error" to null,
                "detail" to jsonOf("type" to "shell", "command" to "cat hello.txt", "output" to output).withoutNulls(),
            ),
        )
        h.daemon.push("agent_stream", stream("a1", call("running"), seq = 1))
        h.daemon.push("agent_stream", stream("a1", call("completed", "hi"), seq = 2))
        h.pump()
        assertEquals(listOf("MessageUpserted", "PartUpserted", "PartUpserted"), h.types())
        val part = (h.events.last() as GatewayEvent.PartUpserted).part
        assertEquals("bash", part.toolName)
        assertEquals("toolu_9", part.callId)
        assertEquals(ToolStatus.Completed, part.tool?.status)
        assertEquals(jsonOf("command" to "cat hello.txt"), part.tool?.input)
        assertEquals("hi", part.tool?.output)
    }

    @Test
    fun `B-CHAT-070 events for another folder's agent are dropped`() = runTest {
        val h = streaming()
        h.daemon.push("agent_stream", stream("other", turn("turn_started")))
        h.pump()
        assertTrue(h.events.isEmpty())
    }

    // ---- B-CHAT-071 failures

    @Test
    fun `B-CHAT-071 a failed turn surfaces safe fixed copy`() = runTest {
        val h = streaming()
        h.daemon.push("agent_stream", stream("a1", turn("turn_started")))
        h.daemon.push("agent_stream", stream("a1", jsonOf("type" to "turn_failed", "provider" to "claude", "error" to "Not logged in")))
        h.pump()
        val ended = h.events.filterIsInstance<GatewayEvent.RunEnded>().single()
        val failed = assertIs<RunOutcome.Failed>(ended.outcome)
        assertEquals(PaseoTurnFailedTag, failed.tag)
        assertNull(failed.details) // never the daemon's words
        assertNull(failed.kind)
    }

    @Test
    fun `B-CHAT-071 a failed turn whose reply already said why adds no second error`() = runTest {
        val h = streaming()
        h.daemon.push("agent_stream", stream("a1", turn("turn_started", "kilo")))
        h.daemon.push(
            "agent_stream",
            stream(
                "a1",
                jsonOf(
                    "type" to "timeline", "provider" to "kilo",
                    "item" to mapOf("type" to "assistant_message", "text" to "[System Error] Internal error: You need to sign in to use this model."),
                ),
            ),
        )
        h.daemon.push("agent_stream", stream("a1", turn("turn_failed", "kilo")))
        h.pump()
        assertTrue(h.events.filterIsInstance<GatewayEvent.RunEnded>().none { it.outcome is RunOutcome.Failed })
        // The error row itself carries the agent's words for Details only.
        val row = h.events.filterIsInstance<GatewayEvent.MessageUpserted>().single().message
        assertTrue(row.errorText != null)
        // The next turn that fails without a word still says so.
        h.events.clear()
        h.daemon.push("agent_stream", stream("a1", turn("turn_started", "kilo")))
        h.daemon.push("agent_stream", stream("a1", turn("turn_failed", "kilo")))
        h.pump()
        assertTrue(h.events.filterIsInstance<GatewayEvent.RunEnded>().any { it.outcome is RunOutcome.Failed })
    }

    @Test
    fun `B-CHAT-071 a refused request is a fixed failure, never the daemon's text`() = runTest {
        val h = harness()
        h.daemon.handlers["cancel_agent_request"] = { "rpc_error" to jsonOf("error" to "secret internals", "code" to "x") }
        h.gateway.allSessions().ok()
        val failure = assertIs<Outcome.Failed>(h.gateway.stop(SessionId("a1"), false)).failure
        assertIs<Failure.ComputerDidNotAnswer>(failure)
        assertEquals("The agent request could not be completed.", h.transport.lastDaemonError)
        assertFalse(failure.toString().contains("secret internals") && failure.details == null)
    }

    // ---- B-AGENT-164 history, archive, reconnect

    @Test
    fun `B-AGENT-164 history maps the merged timeline and primes the live stream`() = runTest {
        val h = harness()
        h.gateway.allSessions().ok()
        h.daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to agentJson("a1")) }
        h.daemon.handlers["fetch_agent_timeline_request"] = { m ->
            assertEquals("projected", m.str("projection"))
            "fetch_agent_timeline_response" to jsonOf(
                "agentId" to "a1",
                "epoch" to "epoch-1",
                "endCursor" to mapOf("epoch" to "epoch-1", "seq" to 12),
                "entries" to listOf(
                    mapOf(
                        "provider" to "claude", "timestamp" to "2026-09-19T03:35:27.922Z", "seqStart" to 1,
                        "item" to mapOf("type" to "user_message", "text" to "Read it", "messageId" to "u1"),
                    ),
                    mapOf(
                        "provider" to "claude", "timestamp" to "2026-09-19T03:35:30.000Z", "seqStart" to 2,
                        "item" to mapOf(
                            "type" to "tool_call", "callId" to "toolu_1", "name" to "Read", "status" to "completed",
                            "detail" to mapOf("type" to "read", "filePath" to "$DIR/README.md", "content" to "# spike"),
                        ),
                    ),
                    mapOf(
                        "provider" to "claude", "timestamp" to "2026-09-19T03:35:31.000Z", "seqStart" to 5,
                        "item" to mapOf("type" to "reasoning", "text" to "Thinking"),
                    ),
                    mapOf(
                        "provider" to "claude", "timestamp" to "2026-09-19T03:35:47.208Z", "seqStart" to 9,
                        "item" to mapOf("type" to "assistant_message", "text" to "Done.", "messageId" to "msg_1"),
                    ),
                ),
            )
        }
        val messages = h.gateway.messagePage(SessionId("a1"), null, 100).ok().items
        assertEquals(listOf("u1", "toolu_1", "r:5", "msg_1"), messages.map { it.info.id })
        assertEquals(listOf(Role.User, Role.Assistant, Role.Assistant, Role.Assistant), messages.map { it.info.role })
        assertEquals("read", messages[1].parts.single().toolName)
        assertEquals("# spike", messages[1].parts.single().tool?.output)
        assertEquals(PartType.Reasoning, messages[2].parts.single().type)
        assertTrue(messages.last().info.time?.completedMs != null)
        // Already-hydrated text continues as a delta; old sequence numbers of a replay are skipped.
        h.events.clear()
        h.daemon.push("agent_stream", stream("a1", assistant("x", "msg_1"), seq = 12))
        h.daemon.push("agent_stream", stream("a1", assistant(" More", "msg_1"), seq = 13))
        h.pump()
        assertEquals(listOf("PartDelta"), h.types())
        assertEquals(" More", (h.events.single() as GatewayEvent.PartDelta).delta)
    }

    @Test
    fun `B-AGENT-164 delete archives, and an archive elsewhere removes the session`() = runTest {
        val h = harness()
        h.gateway.allSessions().ok()
        h.daemon.handlers["archive_agent_request"] = { m ->
            "agent_archived" to jsonOf("agentId" to m.str("agentId"), "archivedAt" to "2026-09-19T05:00:00.000Z")
        }
        h.gateway.deleteSession(SessionId("a1")).ok()
        assertEquals("a1", h.daemon.of("archive_agent_request").single().str("agentId"))
        assertTrue(h.daemon.of("delete_agent_request").isEmpty())
        // Archived on another device.
        h.gateway.allSessions().ok()
        h.events.clear()
        h.daemon.push("agent_archived", jsonOf("agentId" to "a1", "archivedAt" to "2026-09-19T05:00:00.000Z"))
        h.pump()
        assertEquals(listOf(SessionId("a1")), h.events.filterIsInstance<GatewayEvent.SessionDeleted>().map { it.sessionId })
    }

    @Test
    fun `B-AGENT-164 a dropped socket reconnects and re-reads the agent list`() = runTest {
        val first = FakeDaemon().seedDefaults()
        val replacement = FakeDaemon().seedDefaults()
        val h = GatewayHarness(this, first, FakeSockets(first, replacement))
        val statuses = mutableListOf<StreamStatus>()
        backgroundScope.launchCollect(h.gateway.events.status) { statuses += it }
        h.gateway.startListening()
        h.pump()
        assertEquals(StreamStatus.Connected, statuses.last())
        assertEquals(1, h.gateway.events.connects.value)
        first.drop()
        h.pump()
        assertIs<StreamStatus.Reconnecting>(statuses.last())
        advanceTimeBy(1_001)
        h.pump()
        assertEquals(StreamStatus.Connected, statuses.last())
        assertTrue(replacement.of("fetch_agents_request").isNotEmpty())
        assertEquals(2, h.gateway.events.connects.value)
        assertTrue(h.events.count { it == GatewayEvent.Connected } == 2)
    }

    @Test
    fun `B-AGENT-164 a refused password stops reconnecting for good`() = runTest {
        val first = FakeDaemon().seedDefaults()
        val refusing = FakeDaemon().apply { rejectReason = "incorrect_password" }
        val h = GatewayHarness(this, first, FakeSockets(first, refusing))
        h.gateway.startListening()
        h.pump()
        first.drop()
        h.pump()
        advanceTimeBy(1_001)
        h.pump()
        val status = assertIs<StreamStatus.Stopped>(h.gateway.events.status.value)
        assertIs<Failure.Net.SignInNeeded>(status.failure)
        advanceTimeBy(60_000)
        h.pump()
        assertIs<StreamStatus.Stopped>(h.gateway.events.status.value)
    }

    // ---- B-AGENT-155 correlated prompts

    @Test
    fun `B-AGENT-155 a correlated prompt forwards the caller's exact id after the final fence, a refusal sends nothing`() = runTest {
        val h = harness()
        h.gateway.allSessions().ok()
        h.daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to agentJson("a1")) }
        h.daemon.handlers["send_agent_message_request"] = { "send_agent_message_response" to jsonOf("accepted" to true) }
        val order = mutableListOf<String>()
        h.gateway.promptWithMessageId(SessionId("a1"), MessageId("client-42"), OutgoingPrompt("go")) {
            order += "fence"
            true
        }.ok()
        assertEquals(listOf("fence"), order)
        assertEquals("client-42", h.daemon.of("send_agent_message_request").single().str("messageId"))
        val refused = h.gateway.promptWithMessageId(SessionId("a1"), MessageId("client-43"), OutgoingPrompt("no")) { false }
        assertIs<Outcome.Failed>(refused)
        assertEquals(1, h.daemon.of("send_agent_message_request").size)
        // A draft is refused before any server change.
        val draft = h.gateway.createSession(NewSession()).ok()
        assertIs<Outcome.Failed>(h.gateway.promptWithMessageId(draft.id, MessageId("c-1"), OutgoingPrompt("x")) { true })
        assertTrue(h.daemon.of("create_agent_request").isEmpty())
    }

    @Test
    fun `B-AGENT-188 a lost send answer is uncertain, and the chat refuses to send again until reloaded`() = runTest {
        val h = harness()
        h.gateway.allSessions().ok()
        h.daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to agentJson("a1")) }
        h.daemon.handlers["fetch_agent_timeline_request"] = { "fetch_agent_timeline_response" to jsonOf("entries" to emptyList<Any>()) }
        // No answer: the 60 s send timeout elapses.
        val first = h.gateway.prompt(SessionId("a1"), OutgoingPrompt("hello"))
        assertEquals(PromptAdmission.Uncertain, first.ok())
        val again = h.gateway.prompt(SessionId("a1"), OutgoingPrompt("hello"))
        assertIs<Outcome.Ok<PromptAdmission>>(again)
        assertEquals(PromptAdmission.Uncertain, again.value)
        assertEquals(1, h.daemon.of("send_agent_message_request").size)
        // Reading the conversation settles it; then sending works again.
        h.gateway.messagePage(SessionId("a1"), null, 100).ok()
        h.daemon.handlers["send_agent_message_request"] = { "send_agent_message_response" to jsonOf("accepted" to true) }
        assertEquals(PromptAdmission.Confirmed(null), h.gateway.prompt(SessionId("a1"), OutgoingPrompt("hello")).ok())
    }

    @Test
    fun `B-AGENT-170 a non-picture attachment is refused in words before anything is sent`() = runTest {
        val h = harness()
        h.gateway.allSessions().ok()
        val prompt = OutgoingPrompt(
            "see file",
            attachments = kotlinx.collections.immutable.persistentListOf(
                io.github.eslamasabry.oc.core.model.PromptAttachment("text/plain", "a.txt", "data:text/plain;base64,aGk="),
            ),
        )
        val failure = assertIs<Outcome.Failed>(h.gateway.prompt(SessionId("a1"), prompt)).failure
        assertIs<Failure.Unavailable>(failure)
        assertTrue(h.daemon.sent.none { it.str("type") == "send_agent_message_request" })
    }

    @Test
    fun `WP-04-10 a draft's model choice reaches create and a mid-turn prompt joins Claude's turn`() = runTest {
        val h = harness()
        h.gateway.providers().ok()
        val draft = h.gateway.createSession(NewSession()).ok().id
        h.gateway.setSessionModel(draft, ModelRef("claude", "opus"), "high").ok()
        h.daemon.handlers["create_agent_request"] = {
            "status" to jsonOf("status" to "agent_created", "agent" to agentJson("real-2", status = "initializing"))
        }
        h.gateway.prompt(draft, OutgoingPrompt("go")).ok()
        val config = h.daemon.of("create_agent_request").single().obj("config")!!
        assertEquals("opus", config.str("model"))
        assertEquals("high", config.str("thinkingOptionId"))
        assertEquals(io.github.eslamasabry.oc.core.model.MidTurnPrompt.JoinsTurn, h.gateway.midTurnPrompt(draft))
        assertNull(h.gateway.midTurnPrompt(SessionId("unknown")))
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
}

private fun <T> kotlinx.coroutines.CoroutineScope.launchCollect(flow: kotlinx.coroutines.flow.Flow<T>, block: (T) -> Unit) =
    launch(kotlinx.coroutines.test.UnconfinedTestDispatcher()) { flow.collect { block(it) } }

private fun kotlinx.coroutines.CoroutineScope.launch(
    context: kotlin.coroutines.CoroutineContext,
    block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit,
) = kotlinx.coroutines.launch(this, context, block = block)
