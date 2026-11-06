package io.github.eslamasabry.oc.protocol.codex.handoff

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.codex.FakeAppServer
import io.github.eslamasabry.oc.protocol.codex.FakeSockets
import io.github.eslamasabry.oc.protocol.codex.gateway
import io.github.eslamasabry.oc.protocol.codex.gateway.CodexHandoffSnapshot
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.params
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * E02-S09 (one Codex client): the Agents hub's handoff review reads its Codex source through the same
 * gateway the chats use. Before the merge the review asked for a second, unwired `CodexGateway`
 * class, so on a phone a Codex conversation never reached the review.
 */
class CodexHandoffSnapshotTest {
    private val sid = SessionId("thread-source")

    private fun source(cwd: String = "/project") =
        jsonOf(
            "id" to "thread-source",
            "cwd" to cwd,
            "name" to "Repair parser",
            "createdAt" to 100,
            "updatedAt" to 999,
            "historyMode" to "legacy",
            "status" to mapOf("type" to "idle"),
            "turns" to listOf(
                mapOf(
                    "id" to "turn-0",
                    "status" to "completed",
                    "itemsView" to "full",
                    "startedAt" to 100,
                    "completedAt" to 101,
                    "items" to listOf(
                        mapOf("id" to "user-0", "type" to "userMessage", "content" to listOf(mapOf("type" to "text", "text" to "Fix the parser"))),
                        mapOf("id" to "think-0", "type" to "reasoning", "summary" to listOf("private chain of thought")),
                        mapOf(
                            "id" to "tool-0", "type" to "commandExecution", "command" to "dart analyze", "cwd" to "/project",
                            "status" to "completed", "exitCode" to 1, "aggregatedOutput" to "1 issue",
                        ),
                        mapOf("id" to "note-0", "type" to "agentMessage", "phase" to "commentary", "text" to "thinking aloud"),
                        mapOf("id" to "agent-0", "type" to "agentMessage", "phase" to "final_answer", "text" to "Fixed"),
                    ),
                ),
                mapOf(
                    "id" to "turn-1",
                    "status" to "failed",
                    "itemsView" to "full",
                    "startedAt" to 102,
                    "completedAt" to 103,
                    "items" to emptyList<Any>(),
                ),
            ),
        )

    private fun fixture(scope: kotlinx.coroutines.test.TestScope, thread: () -> JsonObject): Pair<FakeAppServer, io.github.eslamasabry.oc.protocol.codex.gateway.CodexGateway> {
        val server = FakeAppServer().apply { answers["thread/read"] = { jsonOf("thread" to thread()) } }
        return server to scope.gateway(FakeSockets(server))
    }

    @Test
    fun `B-AGENT-055 the live Codex gateway reads a handoff snapshot once without resuming it`() = runTest {
        val (server, g) = fixture(this) { source() }
        val snapshot = assertIs<Outcome.Ok<CodexHandoffSnapshot>>(g.handoffSnapshot(sid)).value
        assertEquals(sid, snapshot.session.id)
        assertEquals("Repair parser", snapshot.session.title)
        assertEquals("/project", snapshot.session.directory)
        assertEquals(jsonOf("threadId" to "thread-source", "includeTurns" to true), server.calls("thread/read").single().params())
        assertTrue(server.methods().all { it in setOf("initialize", "initialized", "thread/read") }, server.methods().toString())
        g.close()
    }

    @Test
    fun `B-AGENT-055 the snapshot keeps the handoff projection and drops reasoning and commentary`() = runTest {
        val (_, g) = fixture(this) { source() }
        val messages = assertIs<Outcome.Ok<CodexHandoffSnapshot>>(g.handoffSnapshot(sid)).value.messages
        assertEquals(listOf("user-0", "tool-0", "agent-0", "handoff:turn:turn-1"), messages.map { it.info.id })
        assertEquals(Role.User, messages.first().info.role)
        assertEquals("Fix the parser", messages.first().parts.single().text)
        val tool = messages[1].parts.single()
        assertEquals(PartType.Tool, tool.type)
        assertEquals("shell", tool.toolName)
        // A non-zero exit is an error, not a success.
        assertEquals(ToolStatus.Error, tool.tool?.status)
        assertEquals("Fixed", messages[2].parts.single().text)
        val all = messages.flatMap { it.parts }.joinToString { it.text }
        assertFalse("private chain of thought" in all)
        assertFalse("thinking aloud" in all)
        assertTrue(messages.last().info.errorText != null)
        g.close()
    }

    @Test
    fun `B-AGENT-063 a snapshot of another folder's thread is refused before any text is mapped`() = runTest {
        val (_, g) = fixture(this) { source(cwd = "/elsewhere") }
        assertIs<Outcome.Failed>(g.handoffSnapshot(sid))
        g.close()
    }

    @Test
    fun `B-AGENT-055 a malformed snapshot is refused, never partly mapped`() = runTest {
        val (_, g) = fixture(this) { JsonObject(source() - "historyMode") }
        assertIs<Outcome.Failed>(g.handoffSnapshot(sid))
        g.close()
    }
}
