@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.PaseoUrlProblem
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The probe group of test/paseo_gateway_test.dart, Dart PaseoFailure words (paseo_files_test 760…851), first messages. */
class PaseoProbeAndFailureTest {
    @Test
    fun `B-PROTO-070 bad input never opens a socket`() = runTest {
        var opened = false
        val result = probePaseoConnection("ws://198.51.100.4:6767", "", DIR) { _, _, _ ->
            opened = true
            GatewayHarness(this).gateway
        }
        assertEquals(PaseoUrlProblem.NotPrivate, assertIs<PaseoProbeResult.InvalidInput>(result).url)
        assertFalse(opened)
        // A password that cannot travel in a subprotocol and a relative folder are refused the same way.
        assertTrue(assertIs<PaseoProbeResult.InvalidInput>(probePaseoConnection("127.0.0.1:6767", "a b", DIR) { _, _, _ -> error("opened") }).password)
        assertTrue(assertIs<PaseoProbeResult.InvalidInput>(probePaseoConnection("127.0.0.1:6767", "", "work") { _, _, _ -> error("opened") }).directory)
    }

    @Test
    fun `B-PROTO-070 a verified daemon names its ready runtimes and the gateway is closed`() = runTest {
        val h = GatewayHarness(this)
        var url = ""
        val result = probePaseoConnection("127.0.0.1:6767", "", DIR) { u, _, _ ->
            url = u
            h.gateway
        }
        assertEquals("ws://127.0.0.1:6767", url)
        val verified = assertIs<PaseoProbeResult.Verified>(result)
        assertEquals(listOf("Claude Code", "Pi"), verified.runtimes)
        assertEquals("0.8.0", verified.version)
        assertEquals(1, h.daemon.of("fetch_agents_request").single().obj("page")?.get("limit").toString().toInt())
        assertTrue(h.gateway.isClosed)
    }

    @Test
    fun `B-AGENT-160 failure words - an unanswered daemon is about the computer, an unreadable reply says it answered, a real clash changed meanwhile, daemon text only in Details`() {
        assertIs<Failure.ComputerDidNotAnswer>(PaseoException(PaseoFailureKind.Disconnected).toFailure())
        val refused = assertIs<Failure.ComputerDidNotAnswer>(PaseoException(PaseoFailureKind.Unavailable, "fatal: not a git repository").toFailure())
        assertEquals("fatal: not a git repository", refused.details?.text)
        assertIs<Failure.Net.UnreadableReply>(PaseoException(PaseoFailureKind.InvalidResponse).toFailure())
        assertEquals(RefusalReason.StaleScope, assertIs<Failure.Refused>(PaseoException(PaseoFailureKind.StaleRequest).toFailure()).reason)
        assertEquals(RefusalReason.DispatchUncertain, assertIs<Failure.Refused>(PaseoException(PaseoFailureKind.DeliveryUnknown).toFailure()).reason)
        assertIs<Failure.Net.Conflict>(PaseoException(PaseoFailureKind.NewChatRequired).toFailure())
        assertIs<Failure.Net.NotFound>(PaseoException(PaseoFailureKind.NotFound).toFailure())
        assertEquals(Failure.Net.ServerBusy.Kind.Overloaded, assertIs<Failure.Net.ServerBusy>(PaseoException(PaseoFailureKind.Overloaded).toFailure()).kind)
        // The exception message never carries the daemon's sentence.
        assertFalse("git" in PaseoException(PaseoFailureKind.Unavailable, "fatal: not a git repository").message.orEmpty())
    }

    @Test
    fun `B-AGENT-161 a first message the daemon refuses names its reason under Details only`() = runTest {
        val h = GatewayHarness(this)
        h.daemon.handlers["create_agent_request"] = { "rpc_error" to jsonOf("error" to "Provider claude refused: workspace is locked") }
        val draft = (h.gateway.createSession(NewSession()) as Outcome.Ok).value
        val failure = assertIs<Outcome.Failed>(h.gateway.prompt(draft.id, OutgoingPrompt("hi", ModelSelection(ModelRef("claude", "haiku"))))).failure
        assertIs<Failure.ComputerDidNotAnswer>(failure)
        assertEquals("Provider claude refused: workspace is locked", failure.details?.text)
        // A host that cannot start the runtime says why (its own word) before anything is created.
        val stopped = FakeDaemon().seedDefaults()
        val h2 = GatewayHarness(this, stopped)
        val draft2 = (h2.gateway.createSession(NewSession()) as Outcome.Ok).value
        val noCodex = assertIs<Outcome.Failed>(h2.gateway.prompt(draft2.id, OutgoingPrompt("hi", ModelSelection(ModelRef("codex", "gpt"))))).failure
        assertEquals("not installed", noCodex.details?.text)
        assertTrue(stopped.of("create_agent_request").isEmpty())
    }

    @Test
    fun `B-AGENT-172 a follow-up's thinking level reaches the runtime before the message, and the default resets with null`() = runTest {
        val h = GatewayHarness(this)
        h.gateway.allSessions()
        h.daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to agentJson("a1")) }
        h.daemon.handlers["set_agent_thinking_request"] = { "set_agent_thinking_response" to jsonOf("ok" to true) }
        h.daemon.handlers["set_agent_model_request"] = { "set_agent_model_response" to jsonOf("ok" to true) }
        h.daemon.handlers["send_agent_message_request"] = { "send_agent_message_response" to jsonOf("accepted" to true) }
        h.gateway.prompt(SessionId("a1"), OutgoingPrompt("go", ModelSelection(ModelRef("claude", "fixture-model-1"), "high"))).let { assertIs<Outcome.Ok<*>>(it) }
        val order = h.daemon.sent.map { it.str("type") }.filter { it == "set_agent_thinking_request" || it == "send_agent_message_request" }
        assertEquals(listOf("set_agent_thinking_request", "send_agent_message_request"), order)
        assertEquals("high", h.daemon.of("set_agent_thinking_request").single().str("thinkingOptionId"))
        h.gateway.setSessionModel(SessionId("a1"), ModelRef("claude", "fixture-model-1"), "").let { assertIs<Outcome.Ok<*>>(it) }
        assertEquals("null", h.daemon.of("set_agent_thinking_request").last()["thinkingOptionId"].toString())
        // A refused thinking change stops the send.
        h.daemon.handlers["set_agent_thinking_request"] = { "rpc_error" to jsonOf("error" to "no such level") }
        val sends = h.daemon.of("send_agent_message_request").size
        assertIs<Outcome.Failed>(h.gateway.prompt(SessionId("a1"), OutgoingPrompt("go", ModelSelection(ModelRef("claude", "fixture-model-1"), "max"))))
        assertEquals(sends, h.daemon.of("send_agent_message_request").size)
    }

    private fun kotlinx.serialization.json.JsonObject.obj(key: String) = this[key] as? kotlinx.serialization.json.JsonObject
}
