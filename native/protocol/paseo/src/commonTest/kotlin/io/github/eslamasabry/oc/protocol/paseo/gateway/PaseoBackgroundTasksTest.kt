@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoItemMessage
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Ported from test/background_tasks_test.dart (Paseo mapper, API failure line) and test/paseo_background_tasks_test.dart. */
class PaseoBackgroundTasksTest {
    private val callId = "toolu_01BuildOwner"
    private val command = "cd /root/projects/IPTV_King && bash tool/build_tv_release.sh --owner"
    private val started = Instant.parse("2026-09-19T03:34:00Z").toEpochMilliseconds()

    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private fun bashItem(
        output: String = "Command running in background with ID: b1x2y3. Output is being written to: /tmp/claude-1000/tasks/b1x2y3.output",
        description: String? = null,
    ): JsonObject = jsonOf(
        "type" to "tool_call",
        "callId" to callId,
        "name" to "Bash",
        "status" to "completed",
        "error" to null,
        "detail" to buildMap {
            put("type", "shell")
            put("command", command)
            put("cwd", "/root/projects/IPTV_King")
            put("output", output)
            if (description != null) put("description", description)
        },
    )

    private val noticeItem: JsonObject = jsonOf(
        "type" to "tool_call",
        "callId" to "task_notification_b1x2y3",
        "name" to "task_notification",
        "status" to "completed",
        "error" to null,
        "detail" to mapOf("type" to "plain_text", "label" to "Background command \"Build the owner APK\" completed (exit code 0)", "icon" to "wrench"),
        "metadata" to mapOf(
            "synthetic" to true,
            "source" to "claude_task_notification",
            "taskId" to "b1x2y3",
            "toolUseId" to callId,
            "status" to "completed",
            "outputFile" to "/tmp/claude-1000/tasks/b1x2y3.output",
        ),
    )

    private fun message(item: JsonObject, id: String): MessageWithParts = paseoItemMessage("agent-1", item, id, "claude", started, null)!!

    private fun said(text: String, id: String = "a9") = message(jsonOf("type" to "assistant_message", "text" to text, "messageId" to id), id)

    // ---- B-AGENT-034 the Paseo mapper

    @Test
    fun `B-AGENT-034 a shell step that went to the background is marked with its id`() {
        val part = message(bashItem(), callId).parts.single()
        assertEquals("bash", part.toolName)
        assertEquals(callId, part.callId)
        assertEquals(jsonOf("id" to "b1x2y3"), part.tool!!.metadata!!.obj("backgroundTask"))
    }

    @Test
    fun `B-AGENT-034 the step's description, when Paseo passes it, names the task, a blank one is no name`() {
        val named = message(bashItem(description = "  Wait for the owner build  "), callId).parts.single()
        assertEquals(jsonOf("id" to "b1x2y3", "label" to "Wait for the owner build"), named.tool!!.metadata!!.obj("backgroundTask"))
        val blank = message(bashItem(description = " "), callId).parts.single()
        assertEquals(jsonOf("id" to "b1x2y3"), blank.tool!!.metadata!!.obj("backgroundTask"))
    }

    @Test
    fun `B-AGENT-034 an ordinary shell step carries no marker`() {
        val part = message(bashItem(output = "Built in 12 s"), callId).parts.single()
        assertNull(part.tool!!.metadata?.get("backgroundTask"))
    }

    @Test
    fun `B-AGENT-034 the notice keeps which call and task it ended`() {
        val input = message(noticeItem, "task_notification_b1x2y3").parts.single().tool!!.input
        assertEquals(callId, input.str("toolUseId"))
        assertEquals("b1x2y3", input.str("taskId"))
        assertEquals("completed", input.str("status"))
        // The output file's path is not something the app shows.
        assertFalse("/tmp/" in input.values.joinToString(" "))
    }

    // ---- B-AGENT-035 Claude Code's API failure line

    @Test
    fun `B-AGENT-035 the API failure line is a failed turn, never prose`() {
        for (line in listOf(
            "API Error: Can't reach the API server — check your internet or DNS (EAI_AGAIN)",
            "API Error: 529 {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}",
            "API Error: Request timed out.",
            "Claude AI usage limit reached|1761836400",
            "Credit balance is too low",
        )) {
            val m = said(line)
            assertTrue(m.parts.isEmpty(), line)
            assertEquals(line, m.info.errorText)
            assertEquals("claude", m.info.providerId)
        }
    }

    @Test
    fun `B-AGENT-035 words that only mention it stay a reply`() {
        val m = said("The API Error: line you saw came from the proxy; I fixed it.")
        assertNull(m.info.errorText)
        assertTrue(m.parts.single().text.startsWith("The API Error"))
    }

    @Test
    fun `B-AGENT-035 the book holds the turn failed until the next prompt`() {
        val book = PaseoBackgroundTaskBook()
        val now = started + 60_000
        book.replace("agent-1", listOf(message(bashItem(), callId), said("API Error: Can't reach the API server (EAI_AGAIN)")))
        assertTrue(book.turnFailed("agent-1"))
        assertTrue(book.running("agent-1", now))
        // A task's notice is not the agent going on.
        book.observe("agent-1", message(noticeItem, "task_notification_b1x2y3"))
        assertTrue(book.turnFailed("agent-1"))
        assertFalse(book.running("agent-1", now))
        book.observe("agent-1", message(jsonOf("type" to "user_message", "text" to "Continue", "messageId" to "u2"), "u2"))
        assertFalse(book.turnFailed("agent-1"))
        // Words after the failure end it too.
        book.replace("agent-1", listOf(said("API Error: Request timed out."), said("Picked up again; the build is done.", id = "a10")))
        assertFalse(book.turnFailed("agent-1"))
        // Six hours on, nothing is believed running.
        book.replace("agent-1", listOf(message(bashItem(), callId)))
        assertFalse(book.running("agent-1", started + PASEO_BACKGROUND_MAX_AGE_MS))
    }

    // ---- B-AGENT-151 the gateway's book, read by the chat feed

    private var lastWords = "The owner build is compiling."

    private fun agent(loaded: Boolean = true) = agentJson("kilo", loaded = loaded, title = "Build the owner APK")

    private fun entry(seq: Int, item: JsonObject, at: String) =
        mapOf("seqStart" to seq, "seqEnd" to seq, "provider" to "claude", "timestamp" to at, "item" to item)

    private fun TestScope.world(loaded: Boolean = true): GatewayHarness {
        val daemon = FakeDaemon().seedDefaults()
        var record = agent(loaded)
        daemon.handlers["fetch_agents_request"] = {
            "fetch_agents_response" to jsonOf("entries" to listOf(mapOf("agent" to record)), "pageInfo" to mapOf("hasMore" to false))
        }
        daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to record) }
        daemon.handlers["fetch_agent_timeline_request"] = {
            "fetch_agent_timeline_response" to jsonOf(
                "agentId" to "kilo",
                "agent" to record,
                "epoch" to "epoch-1",
                "entries" to listOf(
                    entry(1, jsonOf("type" to "user_message", "text" to "Make the owner build", "messageId" to "u1"), "2026-09-19T03:33:00Z"),
                    entry(2, bashItem(), "2026-09-19T03:34:00Z"),
                    entry(3, jsonOf("type" to "assistant_message", "text" to lastWords, "messageId" to "a1"), "2026-09-19T03:34:00Z"),
                ),
                "endCursor" to mapOf("seq" to 3),
                "hasOlder" to false,
                "hasNewer" to false,
            )
        }
        return GatewayHarness(this, daemon, defaultProviderModes = mapOf("claude" to "default"))
    }

    private fun GatewayHarness.streamed(seq: Long, item: JsonObject) {
        daemon.push("agent_stream", stream("kilo", jsonOf("type" to "timeline", "provider" to "claude", "item" to item), seq = seq))
        pump()
    }

    private val kilo = SessionId("kilo")

    @Test
    fun `B-AGENT-151 a finished turn with a command still running reads as Running until its notice arrives`() = runTest {
        val h = world()
        val port = h.gateway.feedPort()
        h.gateway.allSessions().ok()
        assertFalse(port.backgroundTasksRunning(kilo))
        h.gateway.messagePage(kilo, null, 100).ok()
        assertTrue(h.gateway.backgroundTasksRunning(kilo))
        assertTrue(port.backgroundTasksRunning(kilo))
        assertFalse(port.lastTurnFailed(kilo))
        h.streamed(4, noticeItem)
        assertFalse(h.gateway.backgroundTasksRunning(kilo))
        assertFalse(port.backgroundTasksRunning(kilo))
    }

    @Test
    fun `B-AGENT-151 the agent seen without its process ends what ran before`() = runTest {
        val h = world()
        h.gateway.allSessions().ok()
        h.gateway.messagePage(kilo, null, 100).ok()
        assertTrue(h.gateway.backgroundTasksRunning(kilo))
        assertNull(h.gateway.agentRuntimeGapAt(kilo))
        // The helper restarted: the agent is a saved record again.
        h.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to agent(loaded = false)))
        h.pump()
        assertNotNull(h.gateway.agentRuntimeGapAt(kilo))
        assertFalse(h.gateway.backgroundTasksRunning(kilo))
        assertFalse(h.gateway.feedPort().backgroundTasksRunning(kilo))
    }

    @Test
    fun `B-AGENT-151 a turn that ended on an API error reads as Failed whatever it left running`() = runTest {
        lastWords = "API Error: Can't reach the API server — check your internet or DNS (EAI_AGAIN)"
        val h = world()
        val port = h.gateway.feedPort()
        h.gateway.allSessions().ok()
        h.gateway.messagePage(kilo, null, 100).ok()
        assertTrue(h.gateway.backgroundTasksRunning(kilo))
        assertTrue(h.gateway.lastTurnFailed(kilo))
        assertTrue(port.lastTurnFailed(kilo))
        assertTrue(port.agentFailed(kilo))
        // A task's notice is not the agent going on.
        h.streamed(4, noticeItem)
        assertTrue(port.lastTurnFailed(kilo))
        // The next prompt starts a new turn.
        h.streamed(5, jsonOf("type" to "user_message", "text" to "Continue", "messageId" to "u2"))
        assertFalse(port.lastTurnFailed(kilo))
    }

    @Test
    fun `B-AGENT-151 a record read before the history counts nothing as running`() = runTest {
        val h = world(loaded = false)
        h.gateway.messagePage(kilo, null, 100).ok()
        assertFalse(h.gateway.backgroundTasksRunning(kilo))
    }
}
