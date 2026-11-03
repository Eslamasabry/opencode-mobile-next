package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Port of the Dart "uncertain start" tests (lib/paseo/gateway/uncertain_start.dart, audit 2026-10-27). */
class PaseoUncertainStartTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private val claude = OutgoingPrompt("hello", ModelSelection(ModelRef("claude", "haiku")))

    @Test
    fun `B-AGENT-188 a first send whose create answer was lost can send again once a read finds nothing started`() = runTest {
        val h = GatewayHarness(this)
        h.gateway.providers().ok()
        val draft = h.gateway.createSession(NewSession()).ok().id
        // No create answer: the 90 s create timeout elapses.
        assertEquals(PromptAdmission.Uncertain, h.gateway.prompt(draft, claude).ok())
        assertEquals(1, h.daemon.of("create_agent_request").size)
        // Reading the draft settles it: the daemon's agents here (a1) were not started by this message.
        h.daemon.handlers["fetch_agent_timeline_request"] = {
            "fetch_agent_timeline_response" to jsonOf("entries" to emptyList<Any>())
        }
        assertEquals(0, h.gateway.messagePage(draft, null, 100).ok().items.size)
        h.daemon.handlers["create_agent_request"] = {
            "status" to jsonOf("status" to "agent_created", "agent" to agentJson("real-2", status = "initializing"))
        }
        assertEquals(PromptAdmission.Confirmed(null), h.gateway.prompt(draft, claude).ok())
        assertEquals(2, h.daemon.of("create_agent_request").size)
    }

    @Test
    fun `B-AGENT-188 a lost create that did start an agent is adopted, and its first message is never sent twice`() = runTest {
        val h = GatewayHarness(this)
        h.gateway.providers().ok()
        val draft = h.gateway.createSession(NewSession()).ok().id
        assertEquals(PromptAdmission.Uncertain, h.gateway.prompt(draft, claude).ok())
        val messageId = h.daemon.of("create_agent_request").single().str("clientMessageId")!!
        // The daemon did start it: the newest agent here carries that message first.
        h.daemon.handlers["fetch_agents_request"] = {
            "fetch_agents_response" to jsonOf(
                "entries" to listOf(mapOf("agent" to agentJson("real-9", title = null))),
                "pageInfo" to mapOf("hasMore" to false),
            )
        }
        h.daemon.handlers["fetch_agent_timeline_request"] = {
            "fetch_agent_timeline_response" to jsonOf(
                "entries" to listOf(
                    mapOf("item" to mapOf("type" to "user_message", "text" to "hello", "messageId" to messageId)),
                ),
            )
        }
        h.daemon.handlers["fetch_agent_request"] = { m ->
            assertEquals("real-9", m.str("agentId"))
            "fetch_agent_response" to jsonOf("agent" to agentJson("real-9", title = null))
        }
        h.gateway.messagePage(draft, null, 100).ok()
        assertEquals("real-9", h.gateway.daemonSessionId(draft).raw)
        h.daemon.handlers["send_agent_message_request"] = {
            "send_agent_message_response" to jsonOf("agentId" to "real-9", "accepted" to true)
        }
        assertEquals(PromptAdmission.Confirmed(null), h.gateway.prompt(draft, OutgoingPrompt("again")).ok())
        assertEquals(1, h.daemon.of("create_agent_request").size)
        assertEquals("real-9", h.daemon.of("send_agent_message_request").single().str("agentId"))
    }
}
