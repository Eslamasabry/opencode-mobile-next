package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * PZ4 and PZ5 against the frames of a real Paseo 0.15.3 daemon whose only enabled runtime is
 * OpenCode ([Paseo0153Frames]): the model sheet reads that runtime's models, and a first message
 * nobody picked a model for starts there instead of on the unavailable Claude.
 */
class Paseo0153StartTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private fun TestScope.daemon0153(): GatewayHarness {
        val daemon =
            FakeDaemon().apply {
                version = "0.15.3"
                handlers["get_providers_snapshot_request"] = {
                    "get_providers_snapshot_response" to Paseo0153Frames.providersSnapshot
                }
                handlers["fetch_agents_request"] = {
                    "fetch_agents_response" to
                        jsonOf("entries" to emptyList<Any>(), "pageInfo" to mapOf("hasMore" to false))
                }
            }
        return GatewayHarness(this, daemon, directory = Paseo0153Frames.PASEO_0153_DIR)
    }

    @Test
    fun `PZ4 the 0_15_3 provider snapshot is a catalog of the OpenCode runtime with its model names, never the unavailable ones`() =
        runTest {
            val h = daemon0153()
            val catalog = PaseoOperations(h.gateway).catalog().ok()
            // Claude, Codex, Copilot, Pi ... are listed `unavailable` / disabled: not offered.
            assertEquals(listOf("opencode"), catalog.providers.map { it.id })
            assertTrue(catalog.providers.single().enabled)
            assertEquals(12, catalog.models.size)
            assertTrue(catalog.models.all { it.providerId == "opencode" && it.enabled })
            val pickle = catalog.models.first()
            assertEquals("opencode/big-pickle", pickle.id)
            // The frame's label, not the id.
            assertEquals("Big Pickle", pickle.name)
            assertEquals(200_000, pickle.contextLimit)
            assertFalse(pickle.reasoning)
            val deepseek = catalog.models.single { it.id == "opencode/deepseek-v4.1-flash-free" }
            assertEquals("DeepSeek V4.1 Flash Free", deepseek.name)
            assertTrue(deepseek.reasoning)
            assertEquals(listOf("low", "high", "max"), deepseek.variants.map { it.id })
            assertEquals(
                listOf("Big Pickle", "DeepSeek V4.1 Flash Free", "GLM-5.3-Flash Free"),
                catalog.models.take(3).map { it.name },
            )
            // A new conversation starts where the composer says it will.
            assertEquals(
                ModelRef("opencode", "opencode/big-pickle"),
                PaseoOperations(h.gateway).chatDefaults().ok().model,
            )
        }

    @Test
    fun `PZ5 a first message with nothing picked creates the agent on the snapshot's default runtime and model, as the daemon's own client does`() =
        runTest {
            val h = daemon0153()
            h.daemon.handlers["create_agent_request"] = { "status" to Paseo0153Frames.agentCreated }
            val draft = h.gateway.createSession(NewSession()).ok().id
            assertEquals(PromptAdmission.Confirmed(null), h.gateway.prompt(draft, OutgoingPrompt("say hi")).ok())
            val wire = h.daemon.of("create_agent_request").single()
            val real = Paseo0153Frames.createRequest
            // Before: config.provider "claude", refused on the phone and never written.
            assertEquals(real["config"], wire["config"])
            assertEquals(real["initialPrompt"], wire["initialPrompt"])
            assertEquals(real["labels"], wire["labels"])
            assertEquals(real.keys, wire.keys)
            assertEquals("opencode", h.gateway.providerIdForSession(draft))
        }

    @Test
    fun `PZ5 a start refused before its frame is written is nothing-sent, one lost after it stays uncertain`() =
        runTest {
            val h = daemon0153()
            // The person picked Claude, which this daemon lists as unavailable: refused on the phone.
            val first = h.gateway.createSession(NewSession()).ok().id
            val refused =
                assertIs<Outcome.Failed>(
                    h.gateway.prompt(first, OutgoingPrompt("say hi", ModelSelection(ModelRef("claude", "opus"))))
                )
            // Failure.Unavailable is the delivery engine's "nothing left the device": not sent, Try again.
            assertIs<Failure.Unavailable>(refused.failure)
            assertTrue(refused.failure.details != null, "the daemon's reason stays under Details")
            assertTrue(h.daemon.of("create_agent_request").isEmpty())

            // The daemon answered the written frame with an error: it may have arrived, not "nothing sent".
            h.daemon.handlers["create_agent_request"] = { "rpc_error" to jsonOf("error" to "workspace is locked") }
            val second = h.gateway.createSession(NewSession()).ok().id
            val answered = assertIs<Outcome.Failed>(h.gateway.prompt(second, OutgoingPrompt("say hi")))
            assertIs<Failure.ComputerDidNotAnswer>(answered.failure)
            assertEquals(1, h.daemon.of("create_agent_request").size)

            // The frame left and no answer came: uncertain, never resent.
            h.daemon.handlers.remove("create_agent_request")
            val third = h.gateway.createSession(NewSession()).ok().id
            assertEquals(PromptAdmission.Uncertain, h.gateway.prompt(third, OutgoingPrompt("say hi")).ok())
            assertEquals(2, h.daemon.of("create_agent_request").size)
        }
}
