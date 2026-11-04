@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.FakeSockets
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from test/paseo_host_update_test.dart, test/paseo_provider_import_test.dart and test/paseo_payload_use_test.dart. */
class PaseoHostWorkTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    // ---- B-AGENT-167 updating the helper

    private fun GatewayHarness.updateId(): JsonPrimitive = daemon.of("daemon.update.request").single()["requestId"] as JsonPrimitive

    @Test
    fun `B-AGENT-167 progress phases arrive in order and do not end the request`() = runTest {
        val h = GatewayHarness(this)
        val phases = mutableListOf<HostUpdatePhase>()
        val result = async { h.gateway.updateHostDaemon(phases::add) }
        h.pump()
        for (phase in listOf("starting", "downloading", "installing")) {
            h.daemon.push("daemon.update.progress", jsonOf("requestId" to h.updateId(), "phase" to phase))
        }
        h.pump()
        assertEquals(listOf(HostUpdatePhase.Starting, HostUpdatePhase.Downloading, HostUpdatePhase.Installing), phases)
        assertFalse(result.isCompleted, "progress is not the answer")
        h.daemon.push("daemon.update.progress", jsonOf("requestId" to h.updateId(), "phase" to "complete"))
        h.daemon.push(
            "daemon.update.response",
            jsonOf("requestId" to h.updateId(), "success" to true, "error" to null, "previousVersion" to "0.9.1", "newVersion" to "0.9.2"),
        )
        val finished = result.await().ok()
        assertEquals(HostUpdatePhase.Complete, phases.last())
        assertTrue(finished.success)
        assertEquals("0.9.1", finished.previousVersion)
        assertEquals("0.9.2", finished.newVersion)
        assertFalse(finished.alreadyUpToDate)
    }

    @Test
    fun `B-AGENT-167 the same version before and after is already up to date`() = runTest {
        val h = GatewayHarness(this)
        val result = async { h.gateway.updateHostDaemon() }
        h.pump()
        h.daemon.push(
            "daemon.update.response",
            jsonOf("requestId" to h.updateId(), "success" to true, "error" to null, "previousVersion" to "0.9.2", "newVersion" to "0.9.2"),
        )
        assertTrue(result.await().ok().alreadyUpToDate)
    }

    @Test
    fun `B-AGENT-167 a failed update is a result with the reason, not a failure`() = runTest {
        val h = GatewayHarness(this)
        val result = async { h.gateway.updateHostDaemon() }
        h.pump()
        h.daemon.push(
            "daemon.update.response",
            jsonOf("requestId" to h.updateId(), "success" to false, "error" to "npm ERR! EACCES: permission denied", "previousVersion" to "0.9.1", "newVersion" to null),
        )
        val finished = result.await().ok()
        assertFalse(finished.success)
        assertTrue("EACCES" in finished.reason!!)
        assertNull(finished.newVersion)
    }

    @Test
    fun `B-AGENT-167 the connection returns by itself after the helper restarts`() = runTest {
        val initial = FakeDaemon().apply {
            handlers["fetch_agents_request"] = { "fetch_agents_response" to jsonOf("entries" to emptyList<Any>(), "pageInfo" to mapOf("hasMore" to false)) }
        }
        val replacement = FakeDaemon().apply {
            handlers["fetch_agents_request"] = {
                "fetch_agents_response" to jsonOf("entries" to listOf(mapOf("agent" to agentJson("a1"))), "pageInfo" to mapOf("hasMore" to false))
            }
        }
        val h = GatewayHarness(this, initial, sockets = FakeSockets(initial, replacement))
        val statuses = mutableListOf<StreamStatus>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { h.gateway.events.status.collect { statuses += it } }
        h.gateway.startListening()
        h.pump()
        val update = async { h.gateway.updateHostDaemon() }
        h.pump()
        initial.push(
            "daemon.update.response",
            jsonOf("requestId" to initial.of("daemon.update.request").single()["requestId"], "success" to true, "error" to null, "previousVersion" to "0.9.1", "newVersion" to "0.9.2"),
        )
        assertEquals("0.9.2", update.await().ok().newVersion)
        // The daemon restarts itself right after it answers.
        initial.drop()
        advanceTimeBy(5_000)
        h.pump()
        val connected = statuses.indexOfFirst { it == StreamStatus.Connected }
        val reconnecting = statuses.indexOfFirst { it is StreamStatus.Reconnecting }
        assertTrue(connected in 0 until reconnecting, "$statuses")
        assertEquals(StreamStatus.Connected, statuses.last(), "$statuses")
        assertTrue(replacement.of("fetch_agents_request").isNotEmpty())
    }

    // ---- B-AGENT-174 importing Claude Code conversations

    private fun importEntry(handle: String, title: String? = null, first: String? = null, at: String = "2026-10-09T10:00:00.000Z") = mapOf(
        "providerId" to "claude", "providerLabel" to "Claude Code", "providerHandleId" to handle, "cwd" to DIR,
        "title" to title, "firstPromptPreview" to first, "lastPromptPreview" to null, "lastActivityAt" to at,
    )

    @Test
    fun `B-AGENT-174 lists this project's Claude Code conversations, newest first`() = runTest {
        val h = GatewayHarness(this)
        h.daemon.handlers["fetch_recent_provider_sessions_request"] = {
            "fetch_recent_provider_sessions_response" to jsonOf(
                "entries" to listOf(
                    importEntry("old", first = "Fix the login bug", at = "2026-10-01T10:00:00Z"),
                    importEntry("new", title = "Release notes", at = "2026-10-09T10:00:00Z"),
                    importEntry("bad id\u0000", first = "x"),
                    mapOf("providerId" to "claude"),
                ),
                "filteredAlreadyImportedCount" to 2,
            )
        }
        val found = h.gateway.importableConversations().ok()
        val sent = h.daemon.of("fetch_recent_provider_sessions_request").single()
        assertEquals(DIR, sent.str("cwd"))
        assertEquals(jsonOf("x" to listOf("claude"))["x"], sent["providers"])
        assertEquals(listOf("new", "old"), found.items.map { it.handle })
        assertEquals("Release notes", found.items.first().displayTitle)
        assertEquals("Fix the login bug", found.items[1].displayTitle)
        assertEquals(2, found.alreadyImported)
        assertEquals(DIR, h.gateway.providerImportDirectory)
    }

    /** An import sends exactly a conversation this project's list just offered. */
    private suspend fun listedOne(h: GatewayHarness): ImportableConversation {
        h.daemon.handlers["fetch_recent_provider_sessions_request"] = {
            "fetch_recent_provider_sessions_response" to jsonOf("entries" to listOf(importEntry("native-1", first = "x")))
        }
        return h.gateway.importableConversations().ok().items.single()
    }

    @Test
    fun `B-AGENT-174 importing sends the agent, its handle and folder, then reads it`() = runTest {
        val h = GatewayHarness(this)
        h.daemon.handlers["import_agent_request"] = { "status" to jsonOf("status" to "agent_resumed", "agentId" to "imp-1", "agent" to agentJson("imp-1")) }
        h.daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to agentJson("imp-1")) }
        val session = h.gateway.importConversation(listedOne(h)).ok()
        val sent = h.daemon.of("import_agent_request").single()
        assertEquals("claude", sent.str("providerId"))
        assertEquals("native-1", sent.str("providerHandleId"))
        assertEquals(DIR, sent.str("cwd"))
        assertEquals("imp-1", session.id.raw)
        assertEquals(DIR, session.directory)
    }

    @Test
    fun `B-AGENT-174 a refusal is a failure, not a conversation`() = runTest {
        val h = GatewayHarness(this)
        h.daemon.handlers["import_agent_request"] = { "status" to jsonOf("status" to "agent_create_failed", "error" to "session file is gone") }
        assertIs<Outcome.Failed>(h.gateway.importConversation(listedOne(h)))
        // A status the daemon answers without an error is a failure too.
        h.daemon.handlers["import_agent_request"] = { "status" to jsonOf("status" to "agent_create_failed") }
        assertIs<Outcome.Failed>(h.gateway.importConversation(listedOne(h)))
        assertTrue(h.daemon.of("fetch_agent_request").isEmpty())
    }

    // ---- B-AGENT-173 payload-use admission

    private inner class World(val scope: TestScope, hooks: Boolean = true) {
        val trace = mutableListOf<String>()
        var pending: CompletableDeferred<Unit>? = null
        var deny = false
        var releaseFails = false
        var active = 0
        var released = 0
        val daemon = FakeDaemon().also { d ->
            d.handlers["create_agent_request"] = { trace += "rpc:create"; "create_agent_response" to jsonOf("agent" to agentJson("a1")) }
            d.handlers["fetch_agent_request"] = { r -> trace += "rpc:fetch"; "fetch_agent_response" to jsonOf("agent" to agentJson(r.str("agentId")!!)) }
            d.handlers["get_providers_snapshot_request"] = {
                "get_providers_snapshot_response" to jsonOf(
                    "entries" to listOf(mapOf("provider" to "claude", "status" to "ready", "models" to emptyList<Any>(), "modes" to emptyList<Any>())),
                )
            }
            d.handlers["send_agent_message_request"] = { r -> trace += "rpc:send"; "send_agent_message_response" to jsonOf("agentId" to r.str("agentId"), "accepted" to true) }
        }
        val h = GatewayHarness(scope, daemon)

        init {
            if (hooks) {
                h.gateway.beforePayloadUse = {
                    trace += "acquire"
                    if (deny) {
                        Outcome.Failed(Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("phoneHost")))
                    } else {
                        active++
                        pending?.await()
                        Outcome.Ok(Unit)
                    }
                }
                h.gateway.afterPayloadUse = {
                    trace += "release"
                    released++
                    active--
                    if (releaseFails) error("synthetic-release-private-detail")
                }
            }
        }

        val rpcs: List<String> get() = daemon.sent.mapNotNull { it.str("type") }.filter { it != "get_providers_snapshot_request" }
    }

    @Test
    fun `B-AGENT-173 denied acquisition prevents draft create and does not release`() = runTest {
        val w = World(this)
        val draft = w.h.gateway.createSession(NewSession()).ok()
        w.deny = true
        val failed = assertIs<Outcome.Failed>(w.h.gateway.prompt(draft.id, OutgoingPrompt("hello")))
        assertEquals(Failure.ComputerDidNotAnswer::class, failed.failure::class)
        assertTrue(w.daemon.of("create_agent_request").isEmpty())
        assertEquals(0, w.released)
        assertEquals(0, w.active)
    }

    @Test
    fun `B-AGENT-173 acquires before the first request and holds until the RPC acknowledgement`() = runTest {
        val w = World(this)
        val draft = w.h.gateway.createSession(NewSession()).ok()
        w.daemon.handlers["create_agent_request"] = { w.trace += "rpc:create"; null }
        val sending = async { w.h.gateway.prompt(draft.id, OutgoingPrompt("hello")) }
        w.h.pump()
        assertEquals("acquire", w.trace.first())
        assertEquals(listOf("acquire", "rpc:create"), w.trace)
        assertEquals(1, w.active)
        assertEquals(0, w.released)
        w.daemon.push("create_agent_response", jsonOf("requestId" to w.daemon.of("create_agent_request").single()["requestId"], "agent" to agentJson("a1")))
        sending.await().ok()
        assertEquals(0, w.active)
        assertEquals(1, w.released)
        assertEquals("release", w.trace.last())
    }

    @Test
    fun `B-AGENT-173 held acquisition blocks all draft RPC work until admitted`() = runTest {
        val w = World(this)
        val draft = w.h.gateway.createSession(NewSession()).ok()
        w.pending = CompletableDeferred()
        val sending = async { w.h.gateway.prompt(draft.id, OutgoingPrompt("hello")) }
        w.h.pump()
        assertTrue(w.rpcs.isEmpty(), "${w.rpcs}")
        assertEquals(0, w.released)
        w.pending!!.complete(Unit)
        sending.await().ok()
        assertEquals(1, w.daemon.of("create_agent_request").size)
        assertEquals(1, w.released)
    }

    @Test
    fun `B-AGENT-173 an existing conversation's send and a correlated send also lease`() = runTest {
        val w = World(this)
        w.h.gateway.prompt(SessionId("a1"), OutgoingPrompt("hello")).ok()
        assertEquals("acquire", w.trace.first())
        assertEquals("release", w.trace.last())
        w.trace.clear()
        w.h.gateway.promptWithMessageId(SessionId("a1"), io.github.eslamasabry.oc.core.common.ids.MessageId("answer"), OutgoingPrompt("hello")).ok()
        assertEquals("acquire", w.trace.first())
        assertEquals("release", w.trace.last())
        assertEquals(2, w.daemon.of("send_agent_message_request").size)
        assertEquals(0, w.active)
        assertEquals(2, w.released)
    }

    @Test
    fun `B-AGENT-173 a denied existing send dispatches nothing and never releases`() = runTest {
        val w = World(this)
        w.deny = true
        assertIs<Outcome.Failed>(w.h.gateway.prompt(SessionId("a1"), OutgoingPrompt("hello")))
        assertTrue(w.daemon.sent.isEmpty())
        assertEquals(0, w.released)
        assertEquals(0, w.active)
    }

    @Test
    fun `B-AGENT-173 RPC failure releases without replacing its fixed error`() = runTest {
        val w = World(this)
        val draft = w.h.gateway.createSession(NewSession()).ok()
        w.daemon.handlers["create_agent_request"] = { "create_agent_response" to jsonOf("error" to "synthetic-private-daemon-detail") }
        w.releaseFails = true
        val failed = assertIs<Outcome.Failed>(w.h.gateway.prompt(draft.id, OutgoingPrompt("hello")))
        assertFalse("release" in failed.failure.toString())
        assertEquals(0, w.active)
        assertEquals(1, w.released)
        assertEquals("release", w.trace.last())
    }

    @Test
    fun `B-AGENT-173 scope changed during admission releases and dispatches nothing`() = runTest {
        val w = World(this)
        val draft = w.h.gateway.createSession(NewSession()).ok()
        val before = w.daemon.sent.size
        w.pending = CompletableDeferred()
        val sending = async { w.h.gateway.prompt(draft.id, OutgoingPrompt("hello")) }
        w.h.pump()
        w.h.gateway.setLocation(ServerLocation(directory = "/work/other"))
        w.h.pump()
        w.pending!!.complete(Unit)
        assertIs<Outcome.Failed>(sending.await())
        assertEquals(before, w.daemon.sent.size)
        assertEquals(1, w.released)
        assertEquals(0, w.active)
    }

    @Test
    fun `B-AGENT-173 gateway closed during admission releases and dispatches nothing`() = runTest {
        val w = World(this)
        val draft = w.h.gateway.createSession(NewSession()).ok()
        val before = w.daemon.sent.size
        w.pending = CompletableDeferred()
        val sending = async { w.h.gateway.prompt(draft.id, OutgoingPrompt("hello")) }
        w.h.pump()
        w.h.gateway.close()
        w.pending!!.complete(Unit)
        assertIs<Outcome.Failed>(sending.await())
        assertEquals(before, w.daemon.sent.size)
        assertEquals(1, w.released)
        assertEquals(0, w.active)
    }

    @Test
    fun `B-AGENT-173 default callbacks keep ordinary create and send paths working`() = runTest {
        val w = World(this, hooks = false)
        val draft = w.h.gateway.createSession(NewSession()).ok()
        w.h.gateway.prompt(draft.id, OutgoingPrompt("hello")).ok()
        w.h.gateway.prompt(SessionId("a2"), OutgoingPrompt("next")).ok()
        assertEquals(1, w.daemon.of("create_agent_request").size)
        assertEquals(1, w.daemon.of("send_agent_message_request").size)
        assertEquals(0, w.released)
    }
}
