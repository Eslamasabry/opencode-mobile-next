@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.mappers.PASEO_CONTINUE_MAX
import io.github.eslamasabry.oc.protocol.paseo.mappers.PASEO_CONTINUE_NOTICE
import io.github.eslamasabry.oc.protocol.paseo.mappers.PASEO_CONTINUE_PROMPT
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoItemMessage
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Issue 147, "it disconnected between the terminal commands". The fake helper plays what `agy acp` 1.5.9 did on a
 * computer (2026-11-15) for "Run the shell command sleep 20 and then run uname -a and tell me what it printed", as
 * Paseo 0.15.4 turns it into its stream: the command step (`execute`, run by the agent itself) still running when the
 * turn completes, after words that say it went on in the background; nothing more until the next prompt, which first
 * brings the step's completion.
 */
class PaseoAgentContinueTest {
    private val agentId = "ag1"
    private val call = "call_14073602957350555277"

    private fun commandItem(callId: String = call, status: String = "running", output: String? = null): JsonObject =
        jsonOf(
            "type" to "tool_call",
            "callId" to callId,
            "name" to "execute",
            "status" to status,
            "error" to null,
            "detail" to buildMap {
                put("type", "shell")
                put("command", "sleep 20 && uname -a")
                if (output != null) put("output", output)
            },
            "metadata" to mapOf("kind" to "execute", "title" to "sleep 20 && uname -a"),
        )

    private val startedWords =
        "I have started `sleep 20 && uname -a` in the background and will share the output once it finishes."

    private fun TestScope.helper(provider: String = "antigravity"): GatewayHarness {
        val daemon = FakeDaemon().seedDefaults()
        val record = agentJson(agentId, provider = provider)
        daemon.handlers["fetch_agents_request"] = {
            "fetch_agents_response" to jsonOf("entries" to listOf(mapOf("agent" to record)), "pageInfo" to mapOf("hasMore" to false))
        }
        daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to record) }
        // The phone's helper reports the agent's runtime ready (a runtime it does not report cannot be prompted).
        daemon.handlers["get_providers_snapshot_request"] = {
            "get_providers_snapshot_response" to jsonOf(
                "entries" to listOf(
                    mapOf("provider" to provider, "status" to "ready", "models" to emptyList<Any>()),
                ),
                "generatedAt" to "2026-09-19T03:35:27.840Z",
            )
        }
        daemon.handlers["fetch_agent_timeline_request"] = {
            "fetch_agent_timeline_response" to jsonOf(
                "agentId" to agentId,
                "agent" to record,
                "epoch" to "epoch-1",
                "entries" to emptyList<Any>(),
                "endCursor" to null,
                "hasOlder" to false,
                "hasNewer" to false,
            )
        }
        daemon.handlers["send_agent_message_request"] = {
            "send_agent_message_response" to jsonOf("agentId" to agentId, "accepted" to true, "error" to null)
        }
        return GatewayHarness(this, daemon)
    }

    private var seq = 0L

    /** The conversation is listed and opened, as the app does before it reads the agent's stream. */
    private suspend fun GatewayHarness.open() {
        gateway.allSessions().ok()
        gateway.messagePage(io.github.eslamasabry.oc.core.common.ids.SessionId(agentId), null, 100).ok()
    }

    private fun GatewayHarness.event(event: JsonObject) {
        daemon.push("agent_stream", stream(agentId, event, seq = ++seq))
        pump()
    }

    private fun GatewayHarness.item(item: JsonObject, provider: String = "antigravity") =
        event(jsonOf("type" to "timeline", "provider" to provider, "item" to item))

    /** One recorded turn: the request, the command left running, the words, the turn's end. */
    private fun GatewayHarness.turnLeavingCommand(
        request: String?,
        callId: String = call,
        end: String = "turn_completed",
        provider: String = "antigravity",
    ) {
        event(turn("turn_started", provider))
        if (request != null) item(jsonOf("type" to "user_message", "text" to request, "messageId" to "u-$callId"), provider)
        item(commandItem(callId), provider)
        item(jsonOf("type" to "assistant_message", "text" to startedWords), provider)
        event(turn(end, provider))
        test.testScheduler.advanceTimeBy(1_000)
        pump()
    }

    /** The prompts the app sent on its own; the send it launched is given a moment to reach the helper first. */
    private fun GatewayHarness.continuations(): List<JsonObject> {
        test.testScheduler.advanceTimeBy(1_000)
        pump()
        return daemon.of("send_agent_message_request").filter { it.str("text") == PASEO_CONTINUE_PROMPT }
    }

    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    @Test
    fun `a turn that ends with its command still running is continued once, with the goal prompt`() = runTest {
        val h = helper()
        h.open()
        h.turnLeavingCommand("Run the shell command sleep 20 and then run uname -a and tell me what it printed")
        val sent = h.continuations()
        assertEquals(1, sent.size, "the app carries the agent on after the turn that left its command running")
        assertEquals(agentId, sent.single().str("agentId"))
        // The continuation's turn: the agent first brings the command's end, then reports. Nothing is sent again.
        h.event(turn("turn_started"))
        h.item(jsonOf("type" to "user_message", "text" to PASEO_CONTINUE_PROMPT, "messageId" to "u-continue"))
        h.item(commandItem(status = "completed", output = "Command exited with code 0.\n\nCommand output:\nLinux test 7.1.5 x86_64 GNU/Linux"))
        h.item(jsonOf("type" to "assistant_message", "text" to "It printed `Linux test 7.1.5 x86_64 GNU/Linux`.\n\n<!-- GOAL_COMPLETE -->"))
        h.event(turn("turn_completed"))
        assertEquals(1, h.continuations().size)
    }

    @Test
    fun `a stopped turn, or another agent's, is never continued`() = runTest {
        val stopped = helper()
        stopped.open()
        stopped.turnLeavingCommand("Run it", end = "turn_canceled")
        assertTrue(stopped.continuations().isEmpty(), "Stop means stop")
        val claude = helper(provider = "claude")
        claude.open()
        claude.turnLeavingCommand("Run it", provider = "claude")
        assertTrue(claude.continuations().isEmpty(), "only agents that leave commands running past the turn")
    }

    @Test
    fun `continuations are bounded per request of the person`() = runTest {
        val h = helper()
        h.open()
        h.turnLeavingCommand("Run the whole build", callId = "c1")
        for (i in 2..PASEO_CONTINUE_MAX + 2) h.turnLeavingCommand(null, callId = "c$i")
        assertEquals(PASEO_CONTINUE_MAX, h.continuations().size)
        // The person's own next request lets the app carry the agent on again.
        h.turnLeavingCommand("And now the tests", callId = "c-next")
        assertEquals(PASEO_CONTINUE_MAX + 1, h.continuations().size)
    }

    // ---- what the transcript reads

    private fun mapped(item: JsonObject, provider: String = "antigravity") =
        paseoItemMessage(agentId, item, "m1", provider, 1_000, null)!!

    @Test
    fun `the command the turn left running reads running, never Ran`() {
        val running = mapped(commandItem()).parts.single().tool!!
        assertEquals(ToolStatus.Running, running.status)
        assertEquals(JsonPrimitive(true), running.metadata?.get("background"))
        val done = mapped(commandItem(status = "completed", output = "ok")).parts.single().tool!!
        assertNull(done.metadata?.get("background"))
        assertNull(mapped(commandItem(), provider = "claude").parts.single().tool!!.metadata?.get("background"))
    }

    @Test
    fun `the continuation reads as a quiet line and the goal marker is never shown`() {
        for (text in listOf(PASEO_CONTINUE_PROMPT, " " + PASEO_CONTINUE_PROMPT.removePrefix("/goal").trim())) {
            val message = mapped(jsonOf("type" to "user_message", "text" to text, "messageId" to "u9"))
            assertEquals(Role.User, message.info.role)
            val part = message.parts.single()
            assertEquals(PartType.Notice, part.type)
            assertEquals(PASEO_CONTINUE_NOTICE, part.toolName)
            assertEquals("", part.text)
        }
        val words = mapped(jsonOf("type" to "assistant_message", "text" to "It printed Linux.\n\n<!-- GOAL_COMPLETE -->"))
        assertEquals("It printed Linux.\n\n", words.parts.single().text)
    }
}
