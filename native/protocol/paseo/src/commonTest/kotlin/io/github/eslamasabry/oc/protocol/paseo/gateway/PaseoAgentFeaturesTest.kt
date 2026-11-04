@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.plus
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.toJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ported from test/paseo_agent_features_test.dart:154-260 and test/paseo_title_agree_test.dart:121. */
class PaseoAgentFeaturesTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private fun fast(on: Boolean) = jsonOf(
        "type" to "toggle", "id" to "fast_mode", "label" to "Fast",
        "description" to "Lower latency Opus responses at higher token cost", "tooltip" to "Toggle fast mode", "icon" to "zap", "value" to on,
    )

    private fun daemon(): FakeDaemon {
        val d = FakeDaemon()
        d.handlers["get_providers_snapshot_request"] = {
            "get_providers_snapshot_response" to jsonOf(
                "entries" to listOf(
                    mapOf(
                        "provider" to "claude", "status" to "ready",
                        "models" to listOf(mapOf("id" to "opus", "label" to "Opus", "aliases" to listOf("claude-opus-4-8"))),
                        "modes" to listOf(mapOf("id" to "default", "label" to "Always Ask")),
                    ),
                ),
            )
        }
        return d
    }

    @Test
    fun `B-AGENT-146 before the conversation starts the daemon lists what the chosen agent would offer`() = runTest {
        val h = GatewayHarness(this, daemon())
        h.daemon.handlers["list_provider_features_request"] = {
            "list_provider_features_response" to jsonOf("provider" to "claude", "features" to listOf(fast(false)), "fetchedAt" to "2026-10-10T00:00:00Z")
        }
        val draft = h.gateway.createSession(NewSession()).ok()
        h.gateway.setSessionModel(draft.id, ModelRef("claude", "opus"), "").ok()
        val features = h.gateway.agentFeatures(draft.id).ok()
        assertEquals("fast_mode", features.single().id)
        assertEquals(AgentFeatureKind.Toggle, features.single().kind)
        val config = h.daemon.of("list_provider_features_request").single().obj("draftConfig")!!
        assertEquals("claude", config.str("provider"))
        assertEquals("opus", config.str("model"))
        assertEquals("/work/app", config.str("cwd"))
    }

    @Test
    fun `B-AGENT-146 a choice made first is sent when the conversation starts`() = runTest {
        val h = GatewayHarness(this, daemon())
        h.daemon.handlers["list_provider_features_request"] = { request ->
            val values = request.obj("draftConfig")?.obj("featureValues")
            "list_provider_features_response" to jsonOf(
                "provider" to "claude",
                "features" to listOf(fast(values?.get("fast_mode") == JsonPrimitive(true))),
                "fetchedAt" to "2026-10-10T00:00:00Z",
            )
        }
        h.daemon.handlers["create_agent_request"] = {
            "status" to jsonOf("status" to "agent_created", "agentId" to "a9", "agent" to (agentJson("a9") + mapOf("model" to JsonPrimitive("opus"))))
        }
        val draft = h.gateway.createSession(NewSession()).ok()
        h.gateway.setSessionModel(draft.id, ModelRef("claude", "opus"), "").ok()
        val after = h.gateway.setAgentFeature(draft.id, "fast_mode", true).ok()
        assertTrue(after.single().on)
        h.gateway.providers().ok()
        h.gateway.prompt(draft.id, OutgoingPrompt("hello", ModelSelection(ModelRef("claude", "opus")), agent = "default")).ok()
        val created = h.daemon.of("create_agent_request").single()
        assertEquals(jsonOf("fast_mode" to true), created.obj("config")?.obj("featureValues"))
    }

    @Test
    fun `B-AGENT-146 a running agent's long model id is shown as the list's own id`() = runTest {
        val h = GatewayHarness(this, daemon())
        h.daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to (agentJson("a1") + mapOf("model" to JsonPrimitive("claude-opus-4-8")))) }
        h.gateway.providers().ok()
        assertEquals("opus", h.gateway.session(SessionId("a1")).ok().selection?.model?.modelId)
    }

    @Test
    fun `B-AGENT-146 an agent read before the model list shows the list's id once the list arrives`() = runTest {
        val h = GatewayHarness(this, daemon())
        h.daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to (agentJson("a1") + mapOf("model" to JsonPrimitive("claude-opus-4-8")))) }
        assertEquals("claude-opus-4-8", h.gateway.session(SessionId("a1")).ok().selection?.model?.modelId)
        h.events.clear()
        h.gateway.providers().ok()
        h.pump()
        val updated = h.events.filterIsInstance<io.github.eslamasabry.oc.core.domain.events.GatewayEvent.SessionUpserted>().single().session
        assertEquals("opus", updated.selection?.model?.modelId)
    }

    @Test
    fun `B-AGENT-182 a model reported as provider slash model is the list model`() = runTest {
        val d = FakeDaemon()
        d.handlers["get_providers_snapshot_request"] = {
            "get_providers_snapshot_response" to jsonOf(
                "entries" to listOf(
                    mapOf(
                        "provider" to "kilo", "status" to "ready", "label" to "Kilo",
                        "models" to listOf(mapOf("id" to "google/gemini-3-pro-image", "label" to "Nano Banana Pro")),
                    ),
                ),
            )
        }
        d.handlers["fetch_agent_request"] = {
            "fetch_agent_response" to jsonOf(
                "agent" to (agentJson("new", provider = "kilo") + mapOf("model" to toJson("kilo/google/gemini-3-pro-image"))) as JsonObject,
            )
        }
        val h = GatewayHarness(this, d)
        h.gateway.providers().ok()
        assertEquals("google/gemini-3-pro-image", h.gateway.session(SessionId("new")).ok().selection?.model?.modelId)
    }
}
