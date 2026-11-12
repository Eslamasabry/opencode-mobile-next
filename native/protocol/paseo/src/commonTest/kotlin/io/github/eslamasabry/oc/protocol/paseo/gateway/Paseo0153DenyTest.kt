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
import io.github.eslamasabry.oc.protocol.paseo.requests.paseoPermission
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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
