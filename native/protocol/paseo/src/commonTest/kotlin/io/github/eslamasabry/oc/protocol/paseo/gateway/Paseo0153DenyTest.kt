package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoDeclinedToolCall
import io.github.eslamasabry.oc.protocol.paseo.requests.paseoPermission
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Faults 4 and 5 of the Paseo 0.15.3 device pass, from one real capture ([Paseo0153Frames] `deny*`): OpenCode 2 asked
 * to write shade-probe.txt, the answer was `{behavior: "deny"}`, the call failed "The user declined this tool call"
 * and the turn ended `turn_canceled`.
 */
class Paseo0153DenyTest {
    private val agent = Paseo0153Frames.denyAgentCreated.obj("agent")!!
    private val agentId = agent.str("id")!!

    @Test
    fun `fault 4 OpenCode 2's ask names its file only in the description, and that is the file the shade allows`() {
        val request = Paseo0153Frames.denyPermissionRequest.obj("request")!!
        val permission = paseoPermission(agentId, request)
        assertEquals("edit", permission.permission)
        assertEquals(listOf("shade-probe.txt"), permission.patterns)
        // The notification reads `filePath` for "Edit shade-probe.txt?" with Allow once / Reject (B-SEC-105).
        assertEquals(JsonPrimitive("shade-probe.txt"), permission.metadata["filePath"])
    }

    @Test
    fun `fault 4 a description that is prose is never read as the file`() {
        val request = Paseo0153Frames.denyPermissionRequest.obj("request")!!
        val prose =
            JsonObject(LinkedHashMap(request).apply { put("description", JsonPrimitive("Allow edits outside the project")) })
        val permission = paseoPermission(agentId, prose)
        assertNull(permission.metadata["filePath"])
        assertEquals(emptyList(), permission.patterns)
    }

    private fun withDescription(description: String, name: String = "edit"): JsonObject {
        val request = Paseo0153Frames.denyPermissionRequest.obj("request")!!
        return JsonObject(
            LinkedHashMap(request).apply {
                put("description", JsonPrimitive(description))
                put("name", JsonPrimitive(name))
            }
        )
    }

    @Test
    fun `fault 4 only a plain single file is named, so a one-tap Allow never covers more than the shade says`() {
        // Each of these would get "Edit <last segment>?" with Allow once if read as the file: never.
        val notOneFile =
            listOf(
                "rm -rf x", // a command (spaces)
                "my notes.txt", // a path with a space: unnamed, Open only
                "https://example.com/a.txt", // a link
                "src/*.kt", // a pattern
                "a.txt,b.txt", // a list
                "../outside/secret.txt", // out of the folder; its last segment would look harmless
                "src/", // a folder
                "a.txt;rm", // a command joined to a name
                "line1\nline2",
            )
        for (description in notOneFile) {
            val permission = paseoPermission(agentId, withDescription(description))
            assertNull(permission.metadata["filePath"], description)
            assertEquals(emptyList(), permission.patterns, description)
        }
        // A tool that is not a file tool never names a file, whatever its description says.
        val bash = paseoPermission(agentId, withDescription("deploy.sh", name = "bash"))
        assertNull(bash.metadata["filePath"])
        // Plain files, nested or absolute, are named exactly as the card shows them.
        for (path in listOf("README.md", "src/main/App.kt", "/work/project/notes.txt", ".env.example")) {
            val permission = paseoPermission(agentId, withDescription(path))
            assertEquals(JsonPrimitive(path), permission.metadata["filePath"], path)
            assertEquals(listOf(path), permission.patterns, path)
            assertEquals("edit", permission.permission, path)
        }
        assertEquals("read", paseoPermission(agentId, withDescription("README.md", name = "read")).permission)
    }

    private fun failedCall(error: JsonObject): JsonObject {
        val item = Paseo0153Frames.denyToolDeclined.obj("event")!!.obj("item")!!
        return JsonObject(LinkedHashMap(item).apply { put("error", error) })
    }

    private fun error(type: String, message: String): JsonObject =
        JsonObject(mapOf("type" to JsonPrimitive(type), "message" to JsonPrimitive(message)))

    @Test
    fun `fault 5 only OpenCode's own aborted sentence is a decline, never words that merely contain it`() {
        assertTrue(paseoDeclinedToolCall(Paseo0153Frames.denyToolDeclined.obj("event")!!.obj("item")!!))
        assertTrue(paseoDeclinedToolCall(failedCall(error("aborted", "The user declined this tool call."))))
        // A real failure stays a failure: another error type, or the sentence inside a tool's or model's own words.
        assertFalse(paseoDeclinedToolCall(failedCall(error("tool_error", "The user declined this tool call"))))
        assertFalse(
            paseoDeclinedToolCall(
                failedCall(error("aborted", "grep found 'The user declined this tool call' in notes.txt"))
            )
        )
        assertFalse(
            paseoDeclinedToolCall(failedCall(error("aborted", "The user declined this tool call; the build then failed")))
        )
        assertFalse(paseoDeclinedToolCall(failedCall(error("aborted", "The user declined to rotate the key"))))
        assertFalse(paseoDeclinedToolCall(failedCall(error("aborted", "Operation aborted"))))
    }

    private fun TestScope.daemon0153(): GatewayHarness {
        val daemon =
            FakeDaemon().apply {
                version = "0.15.3"
                handlers["fetch_agents_request"] = {
                    "fetch_agents_response" to
                        jsonOf("entries" to listOf(mapOf("agent" to agent)), "pageInfo" to mapOf("hasMore" to false))
                }
                handlers["cancel_agent_request"] = { "cancel_agent_response" to jsonOf("accepted" to true) }
            }
        return GatewayHarness(this, daemon, directory = Paseo0153Frames.PASEO_0153_DIR)
    }

    private fun GatewayHarness.denied() {
        daemon.push("agent_stream", Paseo0153Frames.denyToolRunning)
        daemon.push("agent_permission_request", Paseo0153Frames.denyPermissionRequest)
        daemon.push("agent_stream", Paseo0153Frames.denyToolDeclined)
        daemon.push("agent_stream", Paseo0153Frames.denyTurnCanceled)
        pump()
    }

    @Test
    fun `fault 5 a declined call reads as the person's choice and its turn end is not a Stop`() = runTest {
        val h = daemon0153()
        assertIs<Outcome.Ok<*>>(h.gateway.sessionPage(null, 50))
        h.denied()
        val call =
            h.events.filterIsInstance<GatewayEvent.PartUpserted>().map { it.part }.last { it.tool != null }
        assertEquals(ToolStatus.Error, call.tool!!.status)
        // Before: "" (the card's "Failed"); the chat's isDeclinedToolError reads this as "You declined this".
        assertEquals("The user declined this tool call", call.tool!!.output)
        val end = h.events.filterIsInstance<GatewayEvent.RunEnded>().last()
        assertEquals(SessionId(agentId), end.sessionId)
        // Before: Interrupted(User), which the chat reads "Stopped" although nobody pressed Stop.
        assertEquals(RunOutcome.Interrupted(InterruptReason.Unknown), end.outcome)
    }

    @Test
    fun `fault 5 Stop pressed in this app still ends the turn as the person's Stop`() = runTest {
        val h = daemon0153()
        assertIs<Outcome.Ok<*>>(h.gateway.sessionPage(null, 50))
        h.daemon.push("agent_stream", Paseo0153Frames.denyToolRunning)
        h.pump()
        assertIs<Outcome.Ok<*>>(h.gateway.stop(SessionId(agentId), false))
        h.daemon.push("agent_stream", Paseo0153Frames.denyToolDeclined)
        h.daemon.push("agent_stream", Paseo0153Frames.denyTurnCanceled)
        h.pump()
        val end = h.events.filterIsInstance<GatewayEvent.RunEnded>().last()
        assertEquals(RunOutcome.Interrupted(InterruptReason.User), end.outcome)
    }
}
