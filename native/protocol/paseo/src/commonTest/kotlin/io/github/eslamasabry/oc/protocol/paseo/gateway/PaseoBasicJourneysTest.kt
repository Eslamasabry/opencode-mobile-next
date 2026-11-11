@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.list
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The basic journeys through Paseo, pinned on the wire (w4-paseo audit). Ports of the Dart tests that pin the hard-won
 * daemon facts: test/paseo_correlated_prompt_test.dart
 * and test/support/phone_agents_reopen_tests.dart (reopen in place, never `resume_agent_request`: in Paseo 0.11.2 it
 * takes no agent id and makes a new agent on every call), test/paseo_gateway_test.dart (the agent's own commands) and
 * test/paseo_images_test.dart (pictures as `images[{data, mimeType}]`). Message shapes follow Paseo 0.15.3's
 * protocol schemas (`messages.js`: SendAgentMessageRequest, CreateAgentRequest, ListCommandsRequest).
 */
class PaseoBasicJourneysTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private fun agent(id: String, session: String, title: String?, updatedAt: String): JsonObject {
        val base = agentJson(id, title = title, updatedAt = updatedAt, loaded = false)
        return JsonObject(base + ("persistence" to jsonOf("provider" to "claude", "sessionId" to session)))
    }

    /** Pages of `fetch_agents_request`, in order (the last repeats); `fetch_agent_request` answers any listed record. */
    private fun FakeDaemon.listing(vararg pages: List<JsonObject>) {
        val byId = pages.flatMap { it }.associateBy { it.str("id") }
        handlers["fetch_agent_request"] = { m -> "fetch_agent_response" to jsonOf("agent" to byId[m.str("agentId")]) }
        var read = 0
        handlers["fetch_agents_request"] = {
            val page = pages[read.coerceAtMost(pages.size - 1)]
            val more = read < pages.size - 1
            read++
            "fetch_agents_response" to jsonOf(
                "entries" to page.map { mapOf("agent" to it) },
                "pageInfo" to mapOf("nextCursor" to if (more) "next-$read" else null, "prevCursor" to null, "hasMore" to more),
            )
        }
    }

    private val png = PromptAttachment(mime = "image/png", filename = "photo.png", url = "data:image/png;base64,iVBORw0KGgo=")
    private val pngWire = jsonOf("data" to "iVBORw0KGgo=", "mimeType" to "image/png")

    // ---- reopen: in place, never resume

    @Test
    fun `W4-PASEO a saved conversation reopens in place and the next message goes to the same agent`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        // After a daemon restart the record is saved, not loaded (no activeTurn).
        val saved = agent("a1", "native-a1", title = "Saved chat", updatedAt = "2026-10-04T08:00:00Z")
        daemon.listing(listOf(saved))
        daemon.handlers["fetch_agent_timeline_request"] = { m ->
            assertEquals("a1", m.str("agentId"))
            "fetch_agent_timeline_response" to jsonOf("agentId" to "a1", "entries" to emptyList<Any>())
        }
        daemon.handlers["send_agent_message_request"] = {
            "send_agent_message_response" to jsonOf("agentId" to "a1", "accepted" to true, "error" to null)
        }
        val h = GatewayHarness(this, daemon)
        h.gateway.sessionPage(null, 50).ok()
        h.gateway.messagePage(SessionId("a1"), null, 100).ok()
        assertEquals("projected", daemon.of("fetch_agent_timeline_request").single().str("projection"))
        val sent = h.gateway.prompt(SessionId("a1"), OutgoingPrompt("continue", attachments = persistentListOf(png))).ok()
        assertEquals(PromptAdmission.Confirmed(null), sent)
        val send = daemon.of("send_agent_message_request").single()
        assertEquals("a1", send.str("agentId"))
        assertEquals("continue", send.str("text"))
        assertEquals(listOf(pngWire), send.list("images").toList())
        assertTrue(daemon.of("resume_agent_request").isEmpty())
        assertTrue(daemon.of("create_agent_request").isEmpty())
        assertTrue(daemon.of("update_agent_request").isEmpty())
    }

    @Test
    fun `W4-PASEO-GATE a row that stands for several records opens the newest in place and sends to it`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        daemon.listing(
            listOf(
                agent("new", "native-1", title = null, updatedAt = "2026-10-04T08:00:00Z"),
                agent("old", "native-1", title = "Fix the login bug", updatedAt = "2026-10-03T08:00:00Z"),
            ),
        )
        daemon.handlers["fetch_agent_timeline_request"] = { m ->
            "fetch_agent_timeline_response" to jsonOf("agentId" to m.str("agentId"), "entries" to emptyList<Any>())
        }
        daemon.handlers["send_agent_message_request"] = { m ->
            "send_agent_message_response" to jsonOf("agentId" to m.str("agentId"), "accepted" to true, "error" to null)
        }
        val h = GatewayHarness(this, daemon)
        val row = h.gateway.sessionPage(null, 50).ok().items.single()
        assertEquals("new", row.id.raw)
        h.gateway.messagePage(row.id, null, 100).ok()
        assertEquals(listOf("new"), daemon.of("fetch_agent_timeline_request").map { it.str("agentId") })
        assertEquals(PromptAdmission.Confirmed(null), h.gateway.prompt(row.id, OutgoingPrompt("continue")).ok())
        assertEquals("new", daemon.of("send_agent_message_request").single().str("agentId"))
        assertTrue(daemon.of("resume_agent_request").isEmpty())
        assertTrue(daemon.of("create_agent_request").isEmpty())
        assertTrue(daemon.of("update_agent_request").isEmpty())
    }

    // ---- a start the daemon did not make

    @Test
    fun `W4-PASEO-GATE a start answered without an agent record fails at once and the next send starts it`() = runTest {
        val h = GatewayHarness(this)
        h.gateway.providers().ok()
        val draft = h.gateway.createSession(NewSession()).ok().id
        val prompt = OutgoingPrompt("hello", ModelSelection(ModelRef("claude", "haiku")))
        // The daemon refuses with its own words, then answers with no record: neither waits out the 90 s create time,
        // and neither is "maybe started" (that would hold the next send for a reconcile).
        h.daemon.handlers["create_agent_request"] = {
            "status" to jsonOf("status" to "agent_create_failed", "error" to "spawn failed")
        }
        val refused = assertIs<Outcome.Failed>(h.gateway.prompt(draft, prompt)).failure
        assertTrue((refused as? Failure.Refused)?.reason != RefusalReason.DispatchUncertain, "$refused")
        h.daemon.handlers["create_agent_request"] = { "status" to jsonOf("status" to "agent_created") }
        val empty = assertIs<Outcome.Failed>(h.gateway.prompt(draft, prompt)).failure
        assertTrue((empty as? Failure.Refused)?.reason != RefusalReason.DispatchUncertain, "$empty")
        assertTrue(testScheduler.currentTime < PaseoGateway.CREATE_TIMEOUT_MS, "waited ${testScheduler.currentTime} ms")
        h.daemon.handlers["create_agent_request"] = {
            "status" to jsonOf("status" to "agent_created", "agent" to agentJson("real-1", status = "initializing"))
        }
        assertEquals(PromptAdmission.Confirmed(null), h.gateway.prompt(draft, prompt).ok())
        assertEquals(3, h.daemon.of("create_agent_request").size)
        assertTrue(h.daemon.of("resume_agent_request").isEmpty())
    }

    // ---- new conversation with a picture

    @Test
    fun `W4-PASEO a new conversation's first message carries its picture as images data and mimeType`() = runTest {
        val h = GatewayHarness(this)
        h.daemon.handlers["create_agent_request"] = {
            "status" to jsonOf("status" to "agent_created", "agent" to agentJson("real-1", status = "initializing"))
        }
        h.gateway.providers().ok()
        val draft = h.gateway.createSession(NewSession()).ok().id
        val prompt =
            OutgoingPrompt("what is this", ModelSelection(ModelRef("claude", "haiku")), attachments = persistentListOf(png))
        assertEquals(PromptAdmission.Confirmed(null), h.gateway.prompt(draft, prompt).ok())
        val create = h.daemon.of("create_agent_request").single()
        assertEquals("what is this", create.str("initialPrompt"))
        assertEquals(listOf(pngWire), create.list("images").toList())
        assertEquals("haiku", create.obj("config")?.str("model"))
        assertTrue(h.daemon.of("resume_agent_request").isEmpty())
    }

    // ---- slash commands

    @Test
    fun `W4-PASEO commands are asked for a draft with a named model and listed without the agent's plumbing`() = runTest {
        val h = GatewayHarness(this)
        h.daemon.handlers["list_commands_request"] = {
            "list_commands_response" to jsonOf(
                "commands" to listOf(
                    mapOf("name" to "compact", "description" to "Compact the conversation"),
                    mapOf("name" to "__remote-workflow"),
                    mapOf("name" to "/__internal"),
                    mapOf("name" to "/review"),
                ),
            )
        }
        val commands = PaseoOperations(h.gateway).commands().ok()
        assertEquals(listOf("compact", "review"), commands.map { it.name })
        val request = h.daemon.of("list_commands_request").single()
        // Without agentId '' and a draft config naming a model the daemon answers with no commands.
        assertEquals("", request.str("agentId"))
        assertEquals(jsonOf("provider" to "claude", "cwd" to DIR, "model" to "haiku"), request.obj("draftConfig"))
    }
}
