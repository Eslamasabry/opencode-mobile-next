package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * B-AGENT-PLAN: Plan holds in the app itself. Layer 1: Plan reaches the agent as its own read-only mode with the first
 * turn. Layer 2: a command or a file change asked in a Plan conversation is declined by the app, with no card.
 */
class PaseoPlanDeclineTest {
    private fun shellPermission(id: String): JsonObject =
        jsonOf(
            "id" to id, "provider" to "claude", "name" to "Bash", "kind" to "tool",
            "input" to mapOf("command" to "printf 'hi' > p1.txt"),
            "detail" to mapOf("type" to "shell", "command" to "printf 'hi' > p1.txt"),
            "metadata" to mapOf("toolUseId" to "toolu_9"),
        )

    private fun readPermission(id: String): JsonObject =
        jsonOf(
            "id" to id, "provider" to "claude", "name" to "Read", "kind" to "tool",
            "input" to mapOf("file_path" to "$DIR/hello.txt"),
            "detail" to mapOf("type" to "read", "filePath" to "$DIR/hello.txt"),
            "metadata" to mapOf("toolUseId" to "toolu_8"),
        )

    /** A known agent `a1` (claude lists "plan" here) whose mode is [plan] or the normal "Ask first" one. */
    private suspend fun TestScope.known(plan: Boolean): GatewayHarness {
        val h = GatewayHarness(this)
        assertIs<Outcome.Ok<*>>(h.gateway.allSessions())
        h.daemon.handlers["set_agent_mode_request"] = { "set_agent_mode_response" to jsonOf("ok" to true) }
        if (plan) h.gateway.planRequested += "a1"
        h.events.clear()
        return h
    }

    private fun GatewayHarness.asks(vararg requests: JsonObject) {
        requests.forEach { daemon.push("agent_permission_request", jsonOf("agentId" to "a1", "request" to it)) }
        pump()
    }

    private fun GatewayHarness.cards() = events.filterIsInstance<GatewayEvent.PermissionAsked>()

    @Test
    fun `B-AGENT-PLAN-010 a file change and a command asked in Plan are declined by the app with no card`() = runTest {
        val h = known(plan = true)
        h.asks(writePermission("w1"), shellPermission("s1"))
        assertTrue(h.cards().isEmpty(), "no card was shown")
        assertTrue((h.gateway.pendingPermissions() as Outcome.Ok).value.isEmpty())
        val answers = h.daemon.of("agent_permission_response")
        assertEquals(listOf("w1", "s1"), answers.map { it.str("requestId") })
        answers.forEach {
            val response = it.obj("response")!!
            assertEquals("deny", response.str("behavior"))
            assertTrue(response.str("message")!!.contains("Plan mode"), "the agent is told Plan forbids it")
        }
    }

    @Test
    fun `B-AGENT-PLAN-011 a read asked in Plan goes on as today`() = runTest {
        val h = known(plan = true)
        h.asks(readPermission("r1"))
        assertEquals(1, h.cards().size)
        assertTrue(h.daemon.of("agent_permission_response").isEmpty())
    }

    @Test
    fun `B-AGENT-PLAN-012 in Ask first the same requests still show cards`() = runTest {
        val h = known(plan = false)
        h.asks(writePermission("w1"), shellPermission("s1"))
        assertEquals(listOf("w1", "s1"), h.cards().map { it.request.id.raw })
        assertTrue(h.daemon.of("agent_permission_response").isEmpty())
    }

    @Test
    fun `B-AGENT-PLAN-013 a call Plan stopped reads as stopped by Plan in the transcript`() = runTest {
        val h = known(plan = true)
        h.asks(shellPermission("s1"))
        h.events.clear()
        h.daemon.push(
            "agent_stream",
            stream(
                "a1",
                timeline(
                    jsonOf(
                        "type" to "tool_call", "callId" to "toolu_9", "name" to "Bash", "status" to "failed",
                        "error" to mapOf("content" to "Plan mode is on"),
                        "detail" to mapOf("type" to "shell", "command" to "printf 'hi' > p1.txt"),
                    ),
                ),
                seq = 1,
            ),
        )
        h.pump()
        val tool = h.events.filterIsInstance<GatewayEvent.PartUpserted>().last().part.tool
        assertEquals(JsonPrimitive(true), tool?.metadata?.get("oc.planStopped"))
    }

    @Test
    fun `B-AGENT-PLAN-001 a conversation started in Plan sends Codex its read-only mode with the first turn`() = runTest {
        val codex =
            mapOf(
                "provider" to "codex", "status" to "ready", "enabled" to true, "label" to "Codex",
                "modes" to listOf(mapOf("id" to "auto"), mapOf("id" to "read-only"), mapOf("id" to "full-access")),
                "models" to listOf(mapOf("provider" to "codex", "id" to "gpt-6", "label" to "GPT-6", "isDefault" to true)),
            )
        val daemon =
            FakeDaemon().apply {
                handlers["get_providers_snapshot_request"] = {
                    "get_providers_snapshot_response" to jsonOf("cwd" to DIR, "entries" to listOf(codex))
                }
                handlers["fetch_agents_request"] = {
                    "fetch_agents_response" to jsonOf("entries" to emptyList<Any>(), "pageInfo" to mapOf("hasMore" to false))
                }
                handlers["create_agent_request"] = { "rpc_error" to jsonOf("error" to "stop here") }
            }
        val h = GatewayHarness(this, daemon)
        val draft = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(
            h.gateway.createSelectedSession(SessionSelection(ModelRef("codex", "gpt-6"), "", null, modelKnown = true, agentKnown = false)),
        ).value.id
        // The New conversation page: the person chose Plan, the first message carries no mode of its own.
        assertIs<Outcome.Ok<*>>(h.gateway.seedDraftPlan(draft))
        h.gateway.prompt(draft, OutgoingPrompt("Create p1.txt with hi", ModelSelection(ModelRef("codex", "gpt-6"))))
        val config = h.daemon.of("create_agent_request").single().obj("config")!!
        assertEquals("read-only", config.str("modeId"))
    }
}
