package io.github.eslamasabry.oc.protocol.paseo.mappers

import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ported from test/paseo_tool_names_test.dart, test/paseo_tool_output_test.dart and test/paseo_midturn_send_test.dart:494. */
class PaseoToolMapperTest {
    private fun tool(name: String, detail: JsonObject, status: String = "completed", vararg extra: Pair<String, Any?>): ToolState =
        paseoItemMessage(
            "agent-1",
            item("type" to "tool_call", "callId" to "call-1", "name" to name, "status" to status, "detail" to detail, *extra),
            id = "call-1",
            provider = "claude",
            createdMs = null,
            completedMs = null,
        )!!.toolState

    @Test
    fun `B-AGENT-184 Claude's sub-agent tool is the app's sub-agent card`() {
        assertEquals("task", paseoToolName("Agent", jsonOf("type" to "unknown")))
        assertEquals("task", paseoToolName("Task", jsonOf("type" to "unknown")))
        assertEquals("task", paseoToolName("whatever", jsonOf("type" to "sub_agent")))
    }

    @Test
    fun `B-AGENT-184 other tools keep their names`() {
        assertEquals("bash", paseoToolName("Bash", jsonOf("type" to "shell")))
        assertEquals("todowrite", paseoToolName("TodoWrite", jsonOf("type" to "unknown")))
        assertEquals("tool", paseoToolName("", jsonOf()))
        assertEquals("glob", paseoToolName("Glob", jsonOf("type" to "search", "toolName" to "glob")))
        assertEquals("websearch", paseoToolName("WebSearch", jsonOf("type" to "search", "toolName" to "web_search")))
        assertEquals("grep", paseoToolName("Grep", jsonOf("type" to "search")))
        assertEquals("webfetch", paseoToolName("WebFetch", jsonOf("type" to "fetch")))
        assertEquals("skill", paseoToolName("Skill", jsonOf("type" to "plain_text")))
    }

    @Test
    fun `B-AGENT-185 an MCP tool answering JSON keeps it as a value`() {
        val state = tool(
            "mcp__agent_cards__search_connectors",
            jsonOf(
                "type" to "unknown",
                "input" to mapOf("query" to "github", "limit" to 5),
                "output" to mapOf("output" to mapOf("status" to "ok", "matches" to listOf(mapOf("catalogId" to "io.github/github", "name" to "GitHub")))),
            ),
        )
        val value = assertIs<JsonObject>(state.outputValue)
        assertEquals("ok", value.str("status"))
        assertTrue("GitHub" in state.output!!)
        assertEquals(jsonOf("query" to "github", "limit" to 5), state.input)
    }

    @Test
    fun `B-AGENT-185 an MCP tool answering text shows the text`() {
        val state = tool("mcp__drive__search", jsonOf("type" to "unknown", "input" to mapOf("query" to "notes"), "output" to mapOf("output" to "Found 3 files")))
        assertEquals("Found 3 files", state.output)
    }

    @Test
    fun `B-AGENT-185 ToolSearch answering a list shows it`() {
        val state = tool(
            "ToolSearch",
            jsonOf("type" to "unknown", "input" to mapOf("query" to "github"), "output" to mapOf("output" to listOf(mapOf("type" to "tool_reference", "tool_name" to "mcp__github__search")))),
        )
        assertTrue("mcp__github__search" in state.output!!)
    }

    @Test
    fun `B-AGENT-185 grep, glob, fetch and skills show what they found`() {
        assertEquals("a.dart:3: todo", tool("Grep", jsonOf("type" to "search", "query" to "todo", "content" to "a.dart:3: todo")).output)
        assertEquals("a.dart\nb.dart", tool("Glob", jsonOf("type" to "search", "query" to "*.dart", "filePaths" to listOf("a.dart", "b.dart"))).output)
        assertEquals("Example Domain", tool("WebFetch", jsonOf("type" to "fetch", "url" to "https://example.com", "result" to "Example Domain")).output)
        assertEquals("Reviewed.", tool("Skill", jsonOf("type" to "plain_text", "label" to "review", "text" to "Reviewed.")).output)
        val web = tool(
            "WebSearch",
            jsonOf("type" to "search", "toolName" to "web_search", "query" to "kotlin", "webResults" to listOf(mapOf("title" to "Kotlin", "url" to "https://kotlinlang.org")), "annotations" to listOf("note")),
        )
        assertEquals("**Kotlin**  \nhttps://kotlinlang.org\n\nnote", web.output)
        assertEquals(jsonOf("query" to "kotlin"), web.input)
        assertEquals(1, web.metadata!!["numResults"].toString().toInt())
    }

    @Test
    fun `B-AGENT-185 shell and read keep working`() {
        val shell = tool("Bash", jsonOf("type" to "shell", "command" to "ls", "output" to "a\nb", "exitCode" to 0))
        assertEquals("a\nb", shell.output)
        assertEquals(jsonOf("command" to "ls"), shell.input)
        assertEquals(jsonOf("exit" to 0), shell.metadata)
        assertEquals("hello", tool("Read", jsonOf("type" to "read", "filePath" to "/a", "content" to "hello")).output)
    }

    @Test
    fun `B-AGENT-186 a failed tool still says so in the app's words`() {
        val state = tool(
            "mcp__x__y",
            jsonOf("type" to "unknown", "output" to mapOf("output" to "secret detail")),
            status = "failed",
            "error" to "boom: stack",
        )
        assertEquals(ToolStatus.Error, state.status)
        assertFalse("secret detail" in state.output.orEmpty())
        assertFalse("boom" in state.output.orEmpty())
        assertFalse(state.declined)
    }

    private fun failed(error: Any): ToolState =
        tool("Write", jsonOf("type" to "write", "filePath" to "index.html"), status = "failed", "error" to error)

    @Test
    fun `B-AGENT-186 a call the person declined at its permission card reads as declined, not failed`() {
        for (error in listOf<Any>(
            mapOf("message" to "Permission denied"),
            "Permission request denied",
            "The user doesn't want to proceed with this tool use. STOP.",
        )) {
            assertTrue(failed(error).declined, "$error")
            assertTrue(paseoDeclinedToolCall(item("status" to "failed", "error" to error)), "$error")
        }
        // The app knows the person rejected this call's card.
        assertTrue(paseoDeclinedToolCall(item("status" to "failed", "error" to "my own reason", "declinedByPerson" to true)))
    }

    @Test
    fun `B-AGENT-186 a real failure stays a failure`() {
        assertFalse(failed(mapOf("message" to "ENOSPC: disk full")).declined)
        assertEquals(ToolStatus.Error, failed(mapOf("message" to "ENOSPC: disk full")).status)
        assertFalse(paseoDeclinedToolCall(item("status" to "completed", "error" to "Permission denied")))
    }

    @Test
    fun `B-AGENT-187 a background-task notice carries how it ended, its sentence once`() {
        val sentence = "Background agent \"Fix login\" didn't finish before the previous session ended"
        val state = tool(
            "task_notification",
            jsonOf("type" to "plain_text", "label" to sentence, "icon" to "wrench", "text" to sentence),
            "completed",
            "metadata" to mapOf("synthetic" to true, "source" to "claude_task_notification", "taskId" to "a1b2c3d4e5", "status" to "stopped"),
        )
        assertEquals(jsonOf("summary" to sentence, "status" to "stopped", "taskId" to "a1b2c3d4e5"), state.input)
        assertEquals("", state.output)
    }

    @Test
    fun `B-AGENT-187 a notice's raw envelope gives only a finished agent's result`() {
        val state = tool(
            "task_notification",
            jsonOf(
                "type" to "plain_text",
                "label" to "Background task completed",
                "text" to "<task-notification><task-id>t1</task-id><status>completed</status><result>All 12 suites pass.</result></task-notification>",
            ),
            "completed",
            "metadata" to mapOf("status" to "completed"),
        )
        assertEquals(jsonOf("status" to "completed"), state.input)
        assertEquals("All 12 suites pass.", state.output)
    }

    @Test
    fun `B-AGENT-187 a shell step sent to the background is marked with its task id`() {
        val state = tool(
            "Bash",
            jsonOf("type" to "shell", "command" to "sleep 60", "output" to "Command running in background with ID: bash_7", "description" to "Wait"),
        )
        assertEquals(jsonOf("id" to "bash_7", "label" to "Wait"), state.metadata!!.obj("backgroundTask"))
    }

    private val command = "du -sh /work/app/box"

    private fun bash(status: String): JsonObject =
        item("type" to "tool_call", "callId" to "toolu_1", "name" to "Bash", "status" to status, "error" to null, "detail" to mapOf("type" to "shell", "command" to command))

    /** What claude/agent.js handleToolResult emits for the interrupted call once its cache entry is gone. */
    private val staleInterruptResult = item(
        "type" to "tool_call",
        "callId" to "toolu_1",
        "name" to "tool",
        "status" to "failed",
        "detail" to mapOf("type" to "unknown", "input" to null, "output" to null),
        "error" to mapOf("type" to "tool_result", "tool_use_id" to "toolu_1", "content" to "[Request interrupted by user for tool use]", "is_error" to true),
    )

    @Test
    fun `B-AGENT-171 the late interrupt report keeps the step it belongs to`() {
        val stopped = paseoItemMessage("a1", staleInterruptResult, id = "toolu_1", provider = "claude", createdMs = null, completedMs = null)!!.parts.single()
        assertEquals(ToolStatus.Cancelled, stopped.tool!!.status)
        assertFalse("[Request" in stopped.tool!!.output.orEmpty())
        assertTrue(paseoInterruptedToolCall(staleInterruptResult))
    }

    @Test
    fun `B-AGENT-171 read back from the history, the stopped step stays where it ran, with its name and command`() {
        val timeline = paseoTimeline(
            "a1",
            jsonOf(
                "entries" to listOf(
                    entry(item("type" to "user_message", "text" to "Tidy", "messageId" to "u1"), 1),
                    entry(bash("canceled"), 2),
                    entry(item("type" to "user_message", "text" to "Leave it", "messageId" to "u2"), 3, turnId = "turn-2"),
                    entry(staleInterruptResult, 4, turnId = "turn-2"),
                ),
            ),
            busy = true,
        )
        assertEquals(listOf("u1", "toolu_1", "u2"), timeline.messages.map { it.info.id })
        val step = timeline.messages[1].parts.single()
        assertTrue(step.toolName != "tool")
        assertEquals(ToolStatus.Cancelled, step.tool!!.status)
        assertEquals(command, step.tool!!.input.str("command"))
    }

    @Test
    fun `B-AGENT-171 a finished step does not start again from a late running report`() {
        val merged = paseoMergeToolCall(bash("completed"), bash("running"))
        assertEquals("completed", merged.str("status"))
        // A report naming no tool keeps the step's name and parsed detail.
        val renamed = paseoMergeToolCall(bash("running"), item("type" to "tool_call", "callId" to "toolu_1", "name" to "tool", "status" to "completed", "detail" to mapOf("type" to "unknown")))
        assertEquals("Bash", renamed.str("name"))
        assertEquals("shell", renamed.obj("detail")!!.str("type"))
        assertEquals("completed", renamed.str("status"))
    }
}
