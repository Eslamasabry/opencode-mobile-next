package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.NO_PLAN_MODE_FEATURE
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The app's Plan never reached Codex: the helper switches a mode only to a name the agent lists, and Codex lists
 * read-only, auto, auto-review and full-access, so a Codex conversation in Plan ran in Codex's normal mode.
 */
class PaseoPlanModeTest {
    private fun agent(id: String, provider: String, modes: List<String>): JsonObject =
        JsonObject(
            agentJson(id, provider = provider) +
                ("availableModes" to jsonOf("list" to modes.map { mapOf("id" to it, "label" to it) })["list"]!!) +
                ("currentModeId" to jsonOf("m" to modes.first())["m"]!!)
        )

    private fun TestScope.helper(agent: JsonObject): GatewayHarness {
        val daemon = FakeDaemon().seedDefaults().apply {
            handlers["get_providers_snapshot_request"] = {
                "get_providers_snapshot_response" to jsonOf(
                    "entries" to listOf(mapOf("provider" to agent.str("provider"), "status" to "ready", "enabled" to true)),
                )
            }
            handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to agent) }
            handlers["set_agent_mode_request"] = { "set_agent_mode_response" to jsonOf("ok" to true) }
            handlers["send_agent_message_request"] = { m ->
                "send_agent_message_response" to jsonOf("agentId" to m.str("agentId"), "accepted" to true, "error" to null)
            }
        }
        return GatewayHarness(this, daemon).also { it.gateway.startListening(); it.pump() }
    }

    @Test
    fun `I147-PLAN-1 Codex in Plan runs in its read-only mode`() = runTest {
        val h = helper(agent("c1", "codex", listOf("auto", "read-only", "auto-review", "full-access")))
        assertIs<Outcome.Ok<*>>(h.gateway.prompt(SessionId("c1"), OutgoingPrompt("look only", agent = "plan")))
        assertEquals(listOf("read-only"), h.daemon.of("set_agent_mode_request").map { it.str("modeId") })
        assertEquals(1, h.daemon.of("send_agent_message_request").size)
        // Chosen from the mode menu as well.
        assertIs<Outcome.Ok<*>>(h.gateway.setSessionAgent(SessionId("c1"), "plan"))
        assertEquals("read-only", h.daemon.of("set_agent_mode_request").last().str("modeId"))
    }

    @Test
    fun `I147-PLAN-2 an agent with no plan or read-only mode is never run in its normal mode for Plan`() = runTest {
        val h = helper(agent("g1", "goose", listOf("default", "yolo")))
        val sent = h.gateway.prompt(SessionId("g1"), OutgoingPrompt("look only", agent = "plan"))
        val failed = assertIs<Outcome.Failed>(sent)
        val unavailable = assertIs<Failure.Unavailable>(failed.failure)
        assertEquals(NO_PLAN_MODE_FEATURE, unavailable.feature.name)
        assertTrue(h.daemon.of("send_agent_message_request").isEmpty(), "nothing was sent")
        assertTrue(h.daemon.of("set_agent_mode_request").isEmpty())
    }

    @Test
    fun `I147-PLAN-3 an agent that lists plan keeps it, other modes pass unchanged (guard)`() = runTest {
        val h = helper(agent("p1", "claude", listOf("default", "plan", "acceptEdits")))
        assertIs<Outcome.Ok<*>>(h.gateway.setSessionAgent(SessionId("p1"), "plan"))
        assertEquals(listOf("plan"), h.daemon.of("set_agent_mode_request").map { it.str("modeId") })
        assertIs<Outcome.Ok<*>>(h.gateway.setSessionAgent(SessionId("p1"), "acceptEdits"))
        assertEquals("acceptEdits", h.daemon.of("set_agent_mode_request").last().str("modeId"))
    }
}
