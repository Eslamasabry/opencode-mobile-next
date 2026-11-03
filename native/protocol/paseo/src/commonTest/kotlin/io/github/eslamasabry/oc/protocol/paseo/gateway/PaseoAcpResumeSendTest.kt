package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** B-AGENT-144 on the send path (Dart gateway.dart `sendPrompt` → `loadHostAgentContinuation().requiresNewChat`). */
class PaseoAcpResumeSendTest {
    private fun daemon(agent: kotlinx.serialization.json.JsonObject) = FakeDaemon().seedDefaults().apply {
        handlers["get_providers_snapshot_request"] = {
            "get_providers_snapshot_response" to jsonOf(
                "entries" to listOf(
                    mapOf("provider" to "claude", "status" to "ready", "models" to emptyList<Any>()),
                    mapOf("provider" to "gemini", "status" to "ready", "source" to "acp", "models" to emptyList<Any>()),
                ),
                "generatedAt" to "2026-09-19T03:35:27.840Z",
            )
        }
        handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to agent) }
        handlers["send_agent_message_request"] = {
            "send_agent_message_response" to jsonOf("agentId" to "acp1", "accepted" to true)
        }
    }

    @Test
    fun `B-AGENT-144 a reopened ACP chat with unverified restoration refuses to send and needs a new chat`() = runTest {
        val h = GatewayHarness(this, daemon(agentJson("acp1", provider = "gemini", loaded = false)))
        val sent = h.gateway.prompt(SessionId("acp1"), OutgoingPrompt("continue"))
        assertIs<Failure.Conflict>(assertIs<Outcome.Failed>(sent).failure)
        assertTrue(h.daemon.of("send_agent_message_request").isEmpty())
        assertTrue(h.daemon.of("set_agent_mode_request").isEmpty())
    }

    @Test
    fun `B-AGENT-144 a reopened chat whose persistence handle is gone refuses to send`() = runTest {
        val handleless = jsonOf(*agentJson("acp1", loaded = false).filterKeys { it != "persistence" }.toList().toTypedArray())
        val h = GatewayHarness(this, daemon(handleless))
        val sent = h.gateway.prompt(SessionId("acp1"), OutgoingPrompt("continue"))
        assertIs<Failure.Conflict>(assertIs<Outcome.Failed>(sent).failure)
        assertTrue(h.daemon.of("send_agent_message_request").isEmpty())
    }

    @Test
    fun `B-AGENT-144 an ACP chat the daemon still holds continues without restoration proof`() = runTest {
        val h = GatewayHarness(this, daemon(agentJson("acp1", provider = "gemini", loaded = true)))
        val sent = h.gateway.prompt(SessionId("acp1"), OutgoingPrompt("continue"))
        assertEquals(PromptAdmission.Confirmed(null), assertIs<Outcome.Ok<PromptAdmission>>(sent).value)
        assertEquals(1, h.daemon.of("send_agent_message_request").size)
    }
}
