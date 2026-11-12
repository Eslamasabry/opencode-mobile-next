package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Owner 11-12 "Where is thinking efforts in Codex?": a Codex model's efforts as Paseo 0.15.3 lists them. The entry has
 * the shape of `buildCodexModelDefinition` (server codex-app-server-agent.js): `label` from Codex's displayName,
 * `thinkingOptions` with ids from `supportedReasoningEfforts`, `defaultThinkingOptionId`. The test daemon on the PC has
 * Codex switched off, so this is the schema's shape, not a capture.
 */
class PaseoCodexEffortTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private fun option(id: String, default: Boolean = false) =
        mapOf("id" to id, "label" to id, "description" to "Reasoning effort $id", "isDefault" to default)

    private val codexEntry =
        mapOf(
            "provider" to "codex",
            "status" to "ready",
            "enabled" to true,
            "source" to "builtin",
            "label" to "Codex",
            "models" to
                listOf(
                    mapOf(
                        "provider" to "codex",
                        "id" to "gpt-6.1-sol",
                        "label" to "GPT-6.1-Sol",
                        "isDefault" to true,
                        "thinkingOptions" to
                            listOf(option("low"), option("medium", default = true), option("high"), option("xhigh")),
                        "defaultThinkingOptionId" to "medium",
                    ),
                    mapOf("provider" to "codex", "id" to "gpt-6.1-terra", "label" to "GPT-6.1 Terra"),
                ),
        )

    private fun TestScope.daemon(): GatewayHarness {
        val daemon =
            FakeDaemon().apply {
                version = "0.15.3"
                handlers["get_providers_snapshot_request"] = {
                    "get_providers_snapshot_response" to
                        jsonOf("cwd" to Paseo0153Frames.PASEO_0153_DIR, "entries" to listOf(codexEntry))
                }
                handlers["fetch_agents_request"] = {
                    "fetch_agents_response" to jsonOf("entries" to emptyList<Any>(), "pageInfo" to mapOf("hasMore" to false))
                }
                handlers["create_agent_request"] = { "rpc_error" to jsonOf("error" to "stop here") }
            }
        return GatewayHarness(this, daemon, directory = Paseo0153Frames.PASEO_0153_DIR)
    }

    @Test
    fun `a Codex model's thinking options are its efforts, by the daemon's ids, with the daemon's own names`() = runTest {
        val h = daemon()
        val providers = h.gateway.providers().ok()
        val codex = providers.providers.single { it.id == "codex" }
        // What the New conversation page reads (NewConversationWiring: modelData "variants" keys).
        val variants = codex.modelData["gpt-6.1-sol"]!!.obj("variants")!!
        assertEquals(listOf("low", "medium", "high", "xhigh"), variants.keys.toList())
        // What the chat's model sheet reads (the catalog).
        val catalog = PaseoOperations(h.gateway).catalog().ok()
        val sol = catalog.models.single { it.id == "gpt-6.1-sol" }
        assertEquals(listOf("low", "medium", "high", "xhigh"), sol.variants.map { it.id })
        // Names are the daemon's labels (Codex's displayName), as given: no app prettifier makes them agree.
        assertEquals("GPT-6.1-Sol", sol.name)
        assertEquals("GPT-6.1 Terra", catalog.models.single { it.id == "gpt-6.1-terra" }.name)
        assertEquals(emptyList(), catalog.models.single { it.id == "gpt-6.1-terra" }.variants)
    }

    @Test
    fun `the chosen effort goes out in the create frame as thinkingOptionId`() = runTest {
        val h = daemon()
        val draft = h.gateway.createSession(NewSession()).ok().id
        h.gateway.prompt(draft, OutgoingPrompt("hi", ModelSelection(ModelRef("codex", "gpt-6.1-sol"), "xhigh")))
        val config = h.daemon.of("create_agent_request").single().obj("config") as JsonObject
        assertEquals("codex", config.str("provider"))
        assertEquals("gpt-6.1-sol", config.str("model"))
        assertEquals("xhigh", config.str("thinkingOptionId"))
    }
}
