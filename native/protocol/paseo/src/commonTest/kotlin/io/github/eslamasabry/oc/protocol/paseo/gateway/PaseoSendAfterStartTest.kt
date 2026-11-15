package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.fail

/**
 * SEND-STALE (owner 2026-11-15, build 10044): a phone agent's follow-up message was refused as stale. These follow the
 * app's real sequence on the helper's gateway (New conversation moves it to the project, the first message creates
 * the agent, the page opens, a second message carries the composer's model) with one gateway, a second one at the
 * helper's root, a replaced one, and a second one moved elsewhere: the gateway itself never refuses the second message.
 */
class PaseoSendAfterStartTest {
    private val root = "/root/projects"
    private val lottie = "/root/projects/Lottie"

    private class Helper(val provider: String, val cwd: String) {
        val agents = LinkedHashMap<String, JsonObject>()

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
                                "models" to listOf(mapOf("provider" to provider, "id" to "flash", "label" to "Flash")),
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
                    "fetch_agent_timeline_response" to jsonOf("agentId" to m.str("agentId"), "error" to null, "entries" to emptyList<Any>())
                }
                handlers["create_agent_request"] = { m ->
                    val config = m["config"] as JsonObject
                    val id = "real-${agents.size + 1}"
                    val agent = agentJson(id, cwd = config.str("cwd") ?: cwd, provider = provider, status = "running", title = null)
                        .let { JsonObject(it + ("model" to JsonPrimitive(config.str("model") ?: "flash"))) }
                    agents[id] = agent
                    "status" to jsonOf("status" to "agent_created", "agentId" to id, "agent" to agent)
                }
                handlers["set_agent_model_request"] = { m ->
                    val id = m.str("agentId")!!
                    agents[id]?.let { agents[id] = JsonObject(it + ("model" to JsonPrimitive(m.str("modelId")))) }
                    "set_agent_model_response" to jsonOf("ok" to true)
                }
                handlers["set_agent_thinking_request"] = { "set_agent_thinking_response" to jsonOf("ok" to true) }
                handlers["set_agent_mode_request"] = { "set_agent_mode_response" to jsonOf("ok" to true) }
                handlers["send_agent_message_request"] = { m ->
                    "send_agent_message_response" to jsonOf("agentId" to m.str("agentId"), "accepted" to true, "error" to null)
                }
            }
    }

    private fun TestScope.helperGateway(helper: Helper, book: PaseoIdentityBook): GatewayHarness =
        GatewayHarness(this, helper.daemon(), directory = root, identities = book).also {
            it.gateway.includeSubfolders = true
            it.gateway.subfolderRoot = root
            it.gateway.startListening()
            it.pump()
        }

    /** What the delivery engine does for a send carrying the composer's model (GatewaySendLink.applySelection). */
    private suspend fun GatewayHarness.deliver(id: SessionId, n: Int, model: ModelRef, first: Boolean): Outcome<*> {
        val set = gateway.setSessionModel(id, model, "")
        if (set is Outcome.Failed) return set
        val back = gateway.session(id)
        if (back is Outcome.Failed) return back
        val chosen = (back as Outcome.Ok).value.selection
        if (chosen?.model != model) fail("readback differs: ${chosen?.model} vs $model")
        val prompt = OutgoingPrompt("hi $n", ModelSelection(model))
        return if (first) gateway.promptNewSessionWithMessageId(id, MessageId("m$n"), prompt) { true }
        else gateway.promptWithMessageId(id, MessageId("m$n"), prompt) { true }
    }

    private fun Outcome<*>.mustBeOk(what: String) {
        if (this is Outcome.Failed) fail("$what: $failure")
    }

    @Test
    fun `SEND-STALE-1a a second message after New conversation moved the helper's gateway is sent`() = runTest {
        val helper = Helper("antigravity", lottie)
        val h = helperGateway(helper, PaseoIdentityBook())
        h.gateway.changeLocation(ServerLocation(directory = lottie)).mustBeOk("move")
        val id = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(
            h.gateway.createSession(NewSession(title = "Hi test", location = ServerLocation(directory = lottie)))
        ).value.id
        h.gateway.seedDraftProvider(id, "antigravity").mustBeOk("seed")
        h.gateway.session(id).mustBeOk("located")
        val model = ModelRef("antigravity", "flash")
        h.deliver(id, 1, model, first = true).mustBeOk("first")
        h.pump()
        h.gateway.session(id).mustBeOk("page session")
        h.gateway.messagePage(id, null, 100).mustBeOk("page history")
        h.deliver(id, 2, model, first = false).mustBeOk("second")
    }

    @Test
    fun `SEND-STALE-1b a second gateway to the helper at its root does not make the second message stale`() = runTest {
        val helper = Helper("antigravity", lottie)
        val book = PaseoIdentityBook()
        val home = helperGateway(helper, book)
        val h = helperGateway(helper, book)
        h.gateway.changeLocation(ServerLocation(directory = lottie)).mustBeOk("move")
        val id = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(
            h.gateway.createSession(NewSession(title = "Hi test", location = ServerLocation(directory = lottie)))
        ).value.id
        h.gateway.seedDraftProvider(id, "antigravity").mustBeOk("seed")
        home.gateway.sessionPage(null, 100).mustBeOk("home list")
        val model = ModelRef("antigravity", "flash")
        h.deliver(id, 1, model, first = true).mustBeOk("first")
        home.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to helper.agents.values.first()))
        h.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to helper.agents.values.first()))
        h.pump()
        home.pump()
        home.gateway.sessionPage(null, 100).mustBeOk("home list after")
        home.gateway.session(id).mustBeOk("home reads it")
        h.gateway.session(id).mustBeOk("page session")
        h.deliver(id, 2, model, first = false).mustBeOk("second on its gateway")
        home.deliver(id, 3, model, first = false).mustBeOk("second on the home gateway")
    }

    @Test
    fun `SEND-STALE-1c a replaced gateway at the helper's root sends the second message`() = runTest {
        val helper = Helper("antigravity", lottie)
        val book = PaseoIdentityBook()
        val h = helperGateway(helper, book)
        h.gateway.changeLocation(ServerLocation(directory = lottie)).mustBeOk("move")
        val id = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(
            h.gateway.createSession(NewSession(title = "Hi test", location = ServerLocation(directory = lottie)))
        ).value.id
        h.gateway.seedDraftProvider(id, "antigravity").mustBeOk("seed")
        val model = ModelRef("antigravity", "flash")
        h.deliver(id, 1, model, first = true).mustBeOk("first")
        h.gateway.close()
        val next = helperGateway(helper, book)
        next.gateway.session(id).mustBeOk("page session")
        next.gateway.messagePage(id, null, 100).mustBeOk("page history")
        next.deliver(id, 2, model, first = false).mustBeOk("second")
    }

    @Test
    fun `SEND-STALE-1d a second gateway moved to another project does not make the second message stale`() = runTest {
        val helper = Helper("antigravity", lottie)
        val book = PaseoIdentityBook()
        val h = helperGateway(helper, book)
        h.gateway.changeLocation(ServerLocation(directory = lottie)).mustBeOk("move")
        val id = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(
            h.gateway.createSession(NewSession(title = "Hi test", location = ServerLocation(directory = lottie)))
        ).value.id
        h.gateway.seedDraftProvider(id, "antigravity").mustBeOk("seed")
        val model = ModelRef("antigravity", "flash")
        h.deliver(id, 1, model, first = true).mustBeOk("first")
        val other = helperGateway(helper, book)
        other.gateway.changeLocation(ServerLocation(directory = "$root/Other")).mustBeOk("other move")
        other.gateway.sessionPage(null, 100).mustBeOk("other list")
        h.deliver(id, 2, model, first = false).mustBeOk("second")
    }

    @Test
    fun `SEND-STALE-2a a conversation the helper made is named by the helper's id on every gateway to it, a draft is not`() =
        runTest {
            val helper = Helper("omp-acp", lottie)
            val book = PaseoIdentityBook()
            val h = helperGateway(helper, book)
            h.gateway.changeLocation(ServerLocation(directory = lottie)).mustBeOk("move")
            val made = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(
                h.gateway.createSession(NewSession(location = ServerLocation(directory = lottie)))
            ).value.id
            val draft = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(
                h.gateway.createSession(NewSession(location = ServerLocation(directory = lottie)))
            ).value.id
            h.gateway.seedDraftProvider(made, "omp-acp").mustBeOk("seed")
            kotlin.test.assertEquals(made, h.gateway.daemonSessionId(made), "a draft is only the app's")
            h.deliver(made, 1, ModelRef("omp-acp", "flash"), first = true).mustBeOk("first")
            kotlin.test.assertEquals("real-1", h.gateway.daemonSessionId(made).raw)
            kotlin.test.assertEquals(draft, h.gateway.daemonSessionId(draft), "an unsent draft stays the app's")
            // The home's gateway to the same helper, at its root, knows it as well.
            val home = helperGateway(helper, book)
            kotlin.test.assertEquals("real-1", home.gateway.daemonSessionId(made).raw)
        }

    /**
     * The coordinator's lead (Antigravity 1.5.9 names its models with the effort in the id, "Gemini 3.8 Flash (High)"
     * = `gemini-3.8-flash-high`): the second message sends that exact id in `set_agent_model`, no separate effort, and
     * the helper names it back as set (Paseo keeps `config.model` as asked, agent-manager setAgentModel), so the
     * read-back matches and the message is sent.
     */
    @Test
    fun `SEND-STALE-6b a model whose id carries its effort is set and read back as the same id`() = runTest {
        val helper = Helper("antigravity", lottie)
        val ids = listOf("gemini-3.8-flash-high", "gemini-4-hydrogen-high", "claude-opus-5-5-high")
        val h =
            GatewayHarness(this, helper.daemon().apply {
                handlers["get_providers_snapshot_request"] = {
                    "get_providers_snapshot_response" to jsonOf(
                        "entries" to listOf(
                            mapOf(
                                "provider" to "antigravity",
                                "status" to "ready",
                                "enabled" to true,
                                "models" to ids.map { mapOf("provider" to "antigravity", "id" to it, "label" to it) },
                            ),
                        ),
                    )
                }
            }, directory = root, identities = PaseoIdentityBook()).also {
                it.gateway.includeSubfolders = true
                it.gateway.subfolderRoot = root
                it.gateway.startListening()
                it.pump()
            }
        h.gateway.changeLocation(ServerLocation(directory = lottie)).mustBeOk("move")
        val id = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(
            h.gateway.createSession(NewSession(location = ServerLocation(directory = lottie)))
        ).value.id
        h.gateway.seedDraftProvider(id, "antigravity").mustBeOk("seed")
        val model = ModelRef("antigravity", "gemini-3.8-flash-high")
        h.deliver(id, 1, model, first = true).mustBeOk("first")
        h.deliver(id, 2, model, first = false).mustBeOk("second")
        kotlin.test.assertEquals(
            listOf("gemini-3.8-flash-high"),
            h.daemon.of("set_agent_model_request").map { it.str("modelId") },
            "the chip's own id, effort included",
        )
        kotlin.test.assertTrue(h.daemon.of("set_agent_thinking_request").isEmpty(), "no separate effort")
    }
}
