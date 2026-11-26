package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * B-399 (F-044): a follow-up sent right after an Antigravity task finishes on This phone must deliver cleanly
 * with no false "connection changed while sending" (StaleScope) refusal, while real scope changes still fail closed.
 */
class AntigravityFollowUpSendTest {
    private val root = "/root/projects"
    private val project = "/root/projects/Lottie"

    private class AntigravityDaemon(
        val cwd: String,
        val reportedModel: (String?) -> String? = { it },
        val reportedCwd: (String) -> String = { it },
    ) {
        val agents = LinkedHashMap<String, JsonObject>()

        fun build(): FakeDaemon =
            FakeDaemon().apply {
                version = "0.15.4"
                handlers["get_providers_snapshot_request"] = {
                    "get_providers_snapshot_response" to jsonOf(
                        "entries" to listOf(
                            mapOf(
                                "provider" to "antigravity",
                                "status" to "ready",
                                "enabled" to true,
                                "models" to listOf(
                                    mapOf(
                                        "provider" to "antigravity",
                                        "id" to "gemini-3.8-flash-high",
                                        "label" to "Gemini 3.8 Flash (High)",
                                        "aliases" to listOf("gemini-flash-high-alias"),
                                    ),
                                ),
                            ),
                        ),
                    )
                }
                handlers["fetch_agents_request"] = {
                    "fetch_agents_response" to jsonOf(
                        "entries" to agents.values.map { mapOf("agent" to it) },
                        "pageInfo" to mapOf("hasMore" to false),
                    )
                }
                handlers["fetch_agent_request"] = { m ->
                    "fetch_agent_response" to jsonOf("agent" to agents[m.str("agentId")], "error" to null)
                }
                handlers["fetch_agent_timeline_request"] = { m ->
                    "fetch_agent_timeline_response" to jsonOf(
                        "agentId" to m.str("agentId"),
                        "error" to null,
                        "entries" to emptyList<Any>(),
                    )
                }
                handlers["create_agent_request"] = { m ->
                    val config = m["config"] as JsonObject
                    val id = "real-${agents.size + 1}"
                    val base = agentJson(
                        id,
                        cwd = reportedCwd(config.str("cwd") ?: cwd),
                        provider = "antigravity",
                        status = "running",
                        title = null,
                    )
                    val model = reportedModel(config.str("model"))
                    val agent = JsonObject(base + ("model" to (model?.let(::JsonPrimitive) ?: JsonNull)))
                    agents[id] = agent
                    "status" to jsonOf("status" to "agent_created", "agentId" to id, "agent" to agent)
                }
                handlers["set_agent_model_request"] = { m ->
                    val id = m.str("agentId")!!
                    val model = reportedModel(m.str("modelId"))
                    agents[id]?.let {
                        agents[id] = JsonObject(it + ("model" to (model?.let(::JsonPrimitive) ?: JsonNull)))
                    }
                    "set_agent_model_response" to jsonOf("ok" to true)
                }
                handlers["set_agent_thinking_request"] = { "set_agent_thinking_response" to jsonOf("ok" to true) }
                handlers["set_agent_mode_request"] = { "set_agent_mode_response" to jsonOf("ok" to true) }
                handlers["send_agent_message_request"] = { m ->
                    "send_agent_message_response" to jsonOf(
                        "agentId" to m.str("agentId"),
                        "accepted" to true,
                        "error" to null,
                    )
                }
            }
    }

    private fun TestScope.harness(daemon: FakeDaemon, subfolders: Boolean = true): GatewayHarness =
        GatewayHarness(this, daemon, directory = if (subfolders) root else project, identities = PaseoIdentityBook())
            .also {
                it.gateway.includeSubfolders = subfolders
                it.gateway.subfolderRoot = if (subfolders) root else null
                it.gateway.startListening()
                it.pump()
            }

    private fun finishFirstTurn(h: GatewayHarness, daemon: AntigravityDaemon, realId: String) {
        val current = daemon.agents[realId] ?: fail("missing agent $realId")
        val done = JsonObject(current + ("status" to JsonPrimitive("idle")) + ("activeTurn" to JsonNull))
        daemon.agents[realId] = done
        h.daemon.push("agent_stream", stream(realId, turn("turn_completed", provider = "antigravity")))
        h.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to done))
        h.pump()
    }

    private suspend fun startConversation(h: GatewayHarness, model: ModelRef): SessionId {
        if (h.gateway.location.value.directory != project) {
            assertIs<Outcome.Ok<*>>(h.gateway.changeLocation(ServerLocation(directory = project)))
        }
        val session = assertIs<Outcome.Ok<Session>>(
            h.gateway.createSession(NewSession(title = "Hi test", location = ServerLocation(directory = project))),
        ).value.id
        assertIs<Outcome.Ok<*>>(h.gateway.seedDraftProvider(session, "antigravity"))
        val first = OutgoingPrompt("first ask", ModelSelection(model))
        assertIs<Outcome.Ok<*>>(h.gateway.promptNewSessionWithMessageId(session, MessageId("m1"), first) { true })
        return session
    }

    @Test
    fun `B-399-1a Antigravity follow-up after providers_snapshot_update canonicalizes alias model id`() = runTest {
        val model = ModelRef("antigravity", "gemini-3.8-flash-high")
        val backend = AntigravityDaemon(
            cwd = project,
            reportedModel = { "antigravity/gemini-3.8-flash-high" },
        )
        val h = harness(backend.build())
        // Load catalog once, as the chat page does before the first reply finishes.
        assertIs<Outcome.Ok<*>>(h.gateway.providers())
        val id = startConversation(h, model)
        finishFirstTurn(h, backend, "real-1")
        // Helper pushes a provider refresh right as the turn ends.
        h.daemon.push("providers_snapshot_update", jsonOf())
        h.pump()

        assertIs<Outcome.Ok<*>>(h.gateway.setSessionModel(id, model, ""))
        val readback = assertIs<Outcome.Ok<Session>>(h.gateway.session(id)).value
        assertEquals(model, readback.selection?.model, "canonical id must survive providers_snapshot_update")
        val followUp = OutgoingPrompt("follow up", ModelSelection(model))
        assertIs<Outcome.Ok<*>>(h.gateway.promptWithMessageId(id, MessageId("m2"), followUp) { true })
        assertEquals(1, h.daemon.of("send_agent_message_request").size)
    }

    @Test
    fun `B-399-1b Antigravity follow-up with default model sentinel does not send literal default`() = runTest {
        val model = ModelRef("antigravity", "default")
        val backend = AntigravityDaemon(cwd = project, reportedModel = { null })
        val h = harness(backend.build())
        val id = startConversation(h, model)
        finishFirstTurn(h, backend, "real-1")

        assertIs<Outcome.Ok<*>>(h.gateway.setSessionModel(id, model, ""))
        assertTrue(
            h.daemon.of("set_agent_model_request").isEmpty(),
            "sentinel 'default' must not be sent as a literal modelId",
        )
        val followUp = OutgoingPrompt("follow up", ModelSelection(model))
        assertIs<Outcome.Ok<*>>(h.gateway.promptWithMessageId(id, MessageId("m2"), followUp) { true })
        assertEquals(1, h.daemon.of("send_agent_message_request").size)
    }

    @Test
    fun `B-399-1c Antigravity follow-up holds when daemon reports agent cwd with trailing slash`() = runTest {
        val model = ModelRef("antigravity", "gemini-3.8-flash-high")
        val backend = AntigravityDaemon(
            cwd = project,
            reportedCwd = { dir -> dir.trimEnd('/') + "/" },
        )
        val h = harness(backend.build(), subfolders = false)
        val id = startConversation(h, model)
        finishFirstTurn(h, backend, "real-1")

        val readback = assertIs<Outcome.Ok<Session>>(h.gateway.session(id)).value
        assertEquals(model, readback.selection?.model)
        val followUp = OutgoingPrompt("follow up", ModelSelection(model))
        assertIs<Outcome.Ok<*>>(h.gateway.promptWithMessageId(id, MessageId("m2"), followUp) { true })
        assertEquals(1, h.daemon.of("send_agent_message_request").size)
    }

    @Test
    fun `B-399-1d moving a pinned gateway to another project still refuses follow-up as StaleScope`() = runTest {
        val model = ModelRef("antigravity", "gemini-3.8-flash-high")
        val backend = AntigravityDaemon(cwd = project)
        val h = harness(backend.build(), subfolders = false)
        val id = startConversation(h, model)
        finishFirstTurn(h, backend, "real-1")
        assertIs<Outcome.Ok<*>>(h.gateway.changeLocation(ServerLocation(directory = "/root/other")))

        val refused = assertIs<Outcome.Failed>(h.gateway.session(id))
        assertEquals(RefusalReason.StaleScope, assertIs<Failure.Refused>(refused.failure).reason)
    }
}
