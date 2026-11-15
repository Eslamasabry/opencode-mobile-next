package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * OMP-OPEN (owner 2026-11-15, Oh My Pi signed in, a GPT model chosen, "Couldn't open this conversation", Details
 * `NotFound`): a conversation of the phone's agents was known to the app by its own id, and only the gateway that
 * started it knew what that id meant (a draft, or the daemon's agent its first message created). A gateway closed or
 * moved to another folder forgot it, and the next read asked the daemon for an agent it never had (`agent: null`, the
 * answer a real Paseo 0.15.4 gives for an unknown id). These tests start a conversation for every agent the phone's
 * helper runs, against a daemon whose agent (a) starts, (b) fails to start, (c) starts and then stops, and check that
 * no later read of it, on the same gateway or on the next one, answers NotFound.
 */
class PaseoIdentityBookTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    /** The helper's runtimes, as the app's catalog names them (engine/agents pins: routes paseoNative and acpPaseo). */
    private val helperProviders =
        listOf("claude", "codex", "antigravity", "gemini", "qwen", "kilo", "cline", "goose", "omp-acp", "fx")

    /** One Paseo daemon behind every socket the test opens: the agents it holds, as fetch and timeline reads see them. */
    private class Helper(val provider: String) {
        val agents = LinkedHashMap<String, JsonObject>()
        var create: (JsonObject) -> Pair<String, JsonObject>? = { _ ->
            val id = "real-${agents.size + 1}"
            val agent = agentJson(id, provider = provider, status = "running", title = null)
            agents[id] = agent
            "status" to jsonOf("status" to "agent_created", "agentId" to id, "agent" to agent)
        }

        fun daemon(): FakeDaemon =
            FakeDaemon().apply {
                version = "0.15.4"
                handlers["get_providers_snapshot_request"] = {
                    "get_providers_snapshot_response" to jsonOf(
                        "entries" to listOf(
                            mapOf(
                                "provider" to provider,
                                "status" to "ready",
                                "enabled" to true,
                                "models" to listOf(mapOf("provider" to provider, "id" to "gpt-5.5", "label" to "GPT-5.5")),
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
                // Paseo 0.15.4 answers an id it never assigned with `agent: null` (reproduced off the phone).
                handlers["fetch_agent_request"] = { m ->
                    "fetch_agent_response" to jsonOf("agent" to agents[m.str("agentId")], "error" to null)
                }
                handlers["fetch_agent_timeline_request"] = { m ->
                    val known = agents[m.str("agentId")]
                    "fetch_agent_timeline_response" to jsonOf(
                        "agentId" to m.str("agentId"),
                        "error" to if (known == null) "Agent not found" else null,
                        "entries" to if (known == null) emptyList() else listOf(
                            mapOf("item" to mapOf("type" to "user_message", "text" to "hi", "messageId" to "m-1")),
                        ),
                    )
                }
                handlers["create_agent_request"] = { m -> create(m) }
            }
    }

    private fun TestScope.gateway(helper: Helper, book: PaseoIdentityBook): GatewayHarness =
        GatewayHarness(this, helper.daemon(), identities = book)

    private fun prompt(provider: String) = OutgoingPrompt("hi", ModelSelection(ModelRef(provider, "gpt-5.5")))

    /** A read of [id] that answers NotFound is the bug: the conversation page would say it cannot be opened. */
    private fun <T> Outcome<T>.notNotFound(what: String): Outcome<T> {
        if (this is Outcome.Failed && failure is Failure.NotFound) fail("$what answered NotFound")
        return this
    }

    private suspend fun GatewayHarness.readsWithoutNotFound(id: SessionId, what: String) {
        gateway.session(id).notNotFound("$what: session").ok()
        gateway.messagePage(id, null, 100).notNotFound("$what: first page").ok()
    }

    @Test
    fun `OMP-OPEN-1 a conversation whose first message started its agent opens on the next gateway too, for every phone agent`() =
        runTest {
            for (provider in helperProviders) {
                val helper = Helper(provider)
                val book = PaseoIdentityBook()
                val first = gateway(helper, book)
                val draft = first.gateway.createSession(NewSession()).ok().id
                first.gateway.seedDraftProvider(draft, provider).ok()
                assertEquals(PromptAdmission.Confirmed(null), first.gateway.prompt(draft, prompt(provider)).ok(), provider)
                first.readsWithoutNotFound(draft, "$provider, same gateway")
                // The helper's connection is replaced (a reconnect, the helper restarted, its graph closed).
                first.gateway.close()
                val next = gateway(helper, book)
                next.readsWithoutNotFound(draft, "$provider, next gateway")
                assertEquals("real-1", next.daemon.of("fetch_agent_request").first().str("agentId"), provider)
                assertEquals(provider, next.gateway.providerIdForSession(draft), provider)
            }
        }

    @Test
    fun `OMP-OPEN-2 a conversation whose agent failed to start stays a conversation that opens and can be sent again, for every phone agent`() =
        runTest {
            for (provider in helperProviders) {
                val helper = Helper(provider)
                helper.create = { "rpc_error" to jsonOf("error" to "Failed to start the agent") }
                val book = PaseoIdentityBook()
                val first = gateway(helper, book)
                val draft = first.gateway.createSession(NewSession()).ok().id
                first.gateway.seedDraftProvider(draft, provider).ok()
                val refused = first.gateway.prompt(draft, prompt(provider))
                assertIs<Outcome.Failed>(refused, provider)
                assertTrue(refused.failure !is Failure.NotFound, "$provider: a start that failed is not a missing conversation")
                first.readsWithoutNotFound(draft, "$provider, same gateway")
                first.gateway.close()
                val next = gateway(helper, book)
                next.readsWithoutNotFound(draft, "$provider, next gateway")
                // Nothing was asked of the daemon for the app's own id.
                assertTrue(next.daemon.of("fetch_agent_request").none { it.str("agentId") == draft.raw }, provider)
                // The person's Try again starts it on the next gateway, with the agent they chose.
                helper.create = { _ ->
                    val agent = agentJson("real-9", provider = provider, status = "running", title = null)
                    helper.agents["real-9"] = agent
                    "status" to jsonOf("status" to "agent_created", "agentId" to "real-9", "agent" to agent)
                }
                assertEquals(PromptAdmission.Confirmed(null), next.gateway.prompt(draft, prompt(provider)).ok(), provider)
                assertEquals(provider, next.daemon.of("create_agent_request").single().let { it["config"] as JsonObject }.str("provider"))
                next.readsWithoutNotFound(draft, "$provider, after Try again")
            }
        }

    @Test
    fun `OMP-OPEN-3 a conversation whose agent started and then stopped still opens, on the same gateway and the next, for every phone agent`() =
        runTest {
            for (provider in helperProviders) {
                val helper = Helper(provider)
                val book = PaseoIdentityBook()
                val first = gateway(helper, book)
                val draft = first.gateway.createSession(NewSession()).ok().id
                first.gateway.seedDraftProvider(draft, provider).ok()
                first.gateway.prompt(draft, prompt(provider)).ok()
                // The agent's process ends: the daemon keeps its record, in error (Paseo 0.15.4, reproduced).
                val stopped = agentJson("real-1", provider = provider, status = "error", title = null)
                helper.agents["real-1"] = stopped
                first.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to stopped))
                first.pump()
                first.readsWithoutNotFound(draft, "$provider, same gateway")
                first.gateway.close()
                gateway(helper, book).readsWithoutNotFound(draft, "$provider, next gateway")
            }
        }

    @Test
    fun `OMP-OPEN-4 a gateway moved to another folder and back still knows the conversation it started`() = runTest {
        val helper = Helper("omp-acp")
        val h = gateway(helper, PaseoIdentityBook())
        val draft = h.gateway.createSession(NewSession()).ok().id
        h.gateway.seedDraftProvider(draft, "omp-acp").ok()
        h.gateway.changeLocation(ServerLocation(directory = "/work/elsewhere")).ok()
        h.gateway.changeLocation(ServerLocation(directory = DIR)).ok()
        h.readsWithoutNotFound(draft, "draft, back in its folder")
        h.gateway.prompt(draft, prompt("omp-acp")).ok()
        h.gateway.changeLocation(ServerLocation(directory = "/work/elsewhere")).ok()
        h.gateway.changeLocation(ServerLocation(directory = DIR)).ok()
        h.readsWithoutNotFound(draft, "started, back in its folder")
        assertEquals(1, h.daemon.of("create_agent_request").size, "its first message is never sent twice")
    }

    @Test
    fun `OMP-OPEN-5 a draft read from another folder is stale, never missing, and a deleted conversation is forgotten for good`() =
        runTest {
            val helper = Helper("omp-acp")
            val book = PaseoIdentityBook()
            val h = gateway(helper, book)
            val draft = h.gateway.createSession(NewSession()).ok().id
            h.gateway.changeLocation(ServerLocation(directory = "/work/elsewhere")).ok()
            val read = h.gateway.session(draft)
            assertIs<Outcome.Failed>(read)
            assertIs<Failure.Refused>(read.failure, "another folder's conversation is not 'gone'")
            h.gateway.changeLocation(ServerLocation(directory = DIR)).ok()
            h.gateway.deleteSession(draft).ok()
            h.gateway.close()
            val next = gateway(helper, book)
            assertIs<Failure.NotFound>(assertIs<Outcome.Failed>(next.gateway.session(draft)).failure)
        }
}
