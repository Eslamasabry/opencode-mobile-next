@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ported from test/paseo_gateway_test.dart group 'sub-agents' (lines 1138-1300). */
class PaseoSubAgentsTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private fun agentCall(status: String): JsonObject = timeline(
        jsonOf(
            "type" to "tool_call",
            "callId" to "toolu_sub",
            "name" to "Agent",
            "status" to status,
            "error" to null,
            "detail" to mapOf("type" to "unknown", "input" to mapOf("description" to "List the files", "subagent_type" to "Explore")),
        ),
    )

    private fun descriptor(status: String = "running") = jsonOf(
        "id" to "s1", "parentAgentId" to "a1", "parentSubagentId" to null, "provider" to "claude",
        "title" to "List the files", "description" to "List the files", "status" to status,
        "createdAt" to "2026-10-06T10:00:00.000Z", "updatedAt" to "2026-10-06T10:00:01.000Z",
        "toolCallId" to "toolu_sub", "cwd" to DIR, "subtitle" to null,
    )

    private suspend fun TestScope.ready(): GatewayHarness {
        val h = GatewayHarness(this)
        h.gateway.allSessions().ok()
        h.events.clear()
        return h
    }

    private val sub = paseoSubagentSessionId("s1")

    private fun GatewayHarness.lastToolPart() =
        events.filterIsInstance<GatewayEvent.PartUpserted>().map { it.part }.last { it.tool != null }

    @Test
    fun `B-AGENT-165 a sub-agent is its conversation's child, and its card opens it`() = runTest {
        val h = ready()
        h.daemon.push("agent_stream", stream("a1", agentCall("running"), seq = 1))
        h.daemon.push("agent.provider_subagents.update", jsonOf("kind" to "upsert", "subagent" to descriptor()))
        h.pump()
        val info = h.events.filterIsInstance<GatewayEvent.SessionUpserted>().first().session
        assertEquals(sub, info.id.raw)
        assertEquals("a1", info.parentId?.raw)
        assertEquals("List the files", info.title)
        assertEquals(SessionStatus.Busy, h.gateway.sessionStatuses().ok()[info.id])
        // The card shown before the sub-agent was reported now links to it.
        val card = h.lastToolPart()
        assertEquals("task", card.toolName)
        assertEquals(sub, card.tool!!.metadata!!.str("sessionId"))
        assertEquals(listOf(sub), PaseoOperations(h.gateway).sessionChildren(SessionId("a1")).ok().map { it.id.raw })
        assertTrue(h.gateway.capabilities.value.subagentSessions)
    }

    @Test
    fun `B-AGENT-165 its steps stream into its own conversation`() = runTest {
        val h = ready()
        h.daemon.push("agent.provider_subagents.update", jsonOf("kind" to "upsert", "subagent" to descriptor()))
        h.pump()
        h.events.clear()
        h.daemon.push(
            "agent.provider_subagents.update",
            jsonOf(
                "kind" to "timeline", "parentAgentId" to "a1", "subagentId" to "s1", "provider" to "claude",
                "item" to mapOf("type" to "assistant_message", "text" to "Found 7 files", "messageId" to "m_sub"),
                "timestamp" to "2026-10-06T10:00:02.000Z", "seq" to 3, "epoch" to "e1",
            ),
        )
        h.pump()
        val message = h.events.filterIsInstance<GatewayEvent.MessageUpserted>().first().message
        assertEquals(sub, message.sessionId)
    }

    @Test
    fun `B-AGENT-165 its history is read from the helper and it takes no replies`() = runTest {
        val h = ready()
        h.daemon.push("agent.provider_subagents.update", jsonOf("kind" to "upsert", "subagent" to descriptor(status = "completed")))
        h.daemon.handlers["agent.provider_subagents.timeline.get.request"] = { m ->
            assertEquals("a1", m.str("parentAgentId"))
            assertEquals("s1", m.str("subagentId"))
            "agent.provider_subagents.timeline.get.response" to jsonOf(
                "parentAgentId" to "a1", "subagentId" to "s1", "provider" to "claude", "epoch" to "e1",
                "endCursor" to mapOf("epoch" to "e1", "seq" to 4),
                "rows" to listOf(
                    mapOf(
                        "seq" to 4, "seqStart" to 4, "timestamp" to "2026-10-06T10:00:03.000Z",
                        "item" to mapOf("type" to "assistant_message", "text" to "7 files", "messageId" to "m_done"),
                    ),
                ),
                "error" to null,
            )
        }
        h.pump()
        val id = SessionId(sub)
        val history = h.gateway.messagePage(id, null, 100).ok().items
        assertEquals("7 files", history.single().parts.single().text)
        assertEquals("a1", h.gateway.session(id).ok().parentId?.raw)
        val refused = assertIs<Outcome.Failed>(h.gateway.prompt(id, OutgoingPrompt("more")))
        assertEquals(Failure.Unavailable(FeatureId("subagentReplies")), refused.failure)
        assertTrue(h.daemon.of("send_agent_message_request").isEmpty())
    }

    @Test
    fun `B-AGENT-165 an old conversation's cards link to the sub-agents the helper still knows`() = runTest {
        val h = ready()
        h.daemon.handlers["agent.provider_subagents.list.request"] = {
            "agent.provider_subagents.list.response" to jsonOf("parentAgentId" to "a1", "subagents" to listOf(descriptor(status = "completed")), "error" to null)
        }
        h.daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to agentJson("a1")) }
        h.daemon.handlers["fetch_agent_timeline_request"] = {
            "fetch_agent_timeline_response" to jsonOf(
                "agentId" to "a1", "epoch" to "epoch-1",
                "entries" to listOf(
                    mapOf("provider" to "claude", "seqStart" to 1, "timestamp" to "2026-10-06T10:00:00.000Z", "item" to agentCall("completed")["item"]),
                ),
            )
        }
        val history = h.gateway.messagePage(SessionId("a1"), null, 100).ok().items
        assertEquals("task", history.single().parts.single().toolName)
        // The list arrives beside the history; the card then links.
        h.pump()
        assertEquals(sub, h.lastToolPart().tool!!.metadata!!.str("sessionId"))
        assertEquals("a1", h.daemon.of("agent.provider_subagents.list.request").single().str("parentAgentId"))
    }
}
