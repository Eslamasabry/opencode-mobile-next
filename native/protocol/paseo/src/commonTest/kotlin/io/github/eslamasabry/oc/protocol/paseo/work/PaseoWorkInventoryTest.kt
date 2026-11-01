package io.github.eslamasabry.oc.protocol.paseo.work

import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoPush
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from test/paseo_idle_busy_test.dart. The Dart tests drive a whole gateway with `trackLocalWork: true`; here
 * [World] plays that gateway's part (capture the revision, send the list, observe the page, list loaded parents'
 * sub-agents, forward pushes), exactly as PaseoWorkInventory's wiring table says.
 */
class PaseoWorkInventoryTest {
    private fun agentJson(
        id: String,
        cwd: String = "/work/app",
        status: String = "idle",
        archivedAt: String? = null,
        loaded: Boolean = true,
    ): JsonObject {
        val base = mutableMapOf<String, Any?>(
            "id" to id,
            "provider" to "claude",
            "cwd" to cwd,
            "status" to status,
            "updatedAt" to "2026-09-19T03:35:31.806Z",
            "pendingPermissions" to emptyList<Any>(),
            "archivedAt" to archivedAt,
        )
        if (loaded) base["activeTurn"] = if (status == "running") mapOf("turnId" to "turn-$id") else null
        return jsonOf(*base.toList().toTypedArray())
    }

    private fun stream(id: String, event: Map<String, Any?>, seq: Any?) =
        jsonOf("agentId" to id, "event" to event, "seq" to seq, "epoch" to "epoch-1")

    private inner class World {
        val inventory = PaseoWorkInventory()
        var epoch = 1
        var connected = true
        var entries = mutableListOf(agentJson("local"), agentJson("elsewhere", cwd = "/work/other"))
        var pages: ((String?) -> JsonObject)? = null
        var children: (String) -> JsonObject = { parent -> jsonOf("parentAgentId" to parent, "subagents" to emptyList<Any>(), "error" to null) }
        val childRequests = mutableListOf<String>()

        fun page(rows: List<JsonObject>, next: String? = null) =
            jsonOf("entries" to rows.map { mapOf("agent" to it) }, "pageInfo" to mapOf("hasMore" to (next != null), "nextCursor" to next))

        suspend fun sessionPage(cursor: String? = null): String? {
            val revision = inventory.revision
            val result = pages?.invoke(cursor) ?: page(entries)
            inventory.observePage(result, cursor, epoch, revision, listChildren = { parent ->
                childRequests += parent
                children(parent)
            }, stillCurrent = { true })
            return (result["pageInfo"] as? JsonObject)?.get("nextCursor")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        }

        suspend fun sessions() {
            var cursor: String? = null
            repeat(20) {
                cursor = sessionPage(cursor) ?: return
            }
        }

        fun push(type: String, payload: JsonObject) = inventory.event(PaseoPush(epoch, type, payload))

        val busy: Boolean? get() = inventory.read(connected, epoch)
    }

    @Test
    fun `B-AGENT-168 a complete all-helper idle inventory establishes known idle`() = runTest {
        val w = World()
        assertNull(w.busy)
        val seen = mutableListOf<Boolean?>()
        backgroundScope.launch { w.inventory.changes.collect { seen += w.busy } }
        runCurrent()
        w.sessions()
        assertEquals(false, w.busy)
        runCurrent()
        assertTrue(false in seen)
    }

    @Test
    fun `B-AGENT-168 live work outside the selected directory blocks helper idle`() = runTest {
        val w = World()
        w.sessions()
        w.push("agent_stream", stream("elsewhere", mapOf("type" to "turn_started", "provider" to "claude"), 1))
        assertEquals(true, w.busy)
        // A lagging idle list must not erase the streamed turn.
        w.sessions()
        assertEquals(true, w.busy)
        w.push("agent_stream", stream("elsewhere", mapOf("type" to "turn_completed", "provider" to "claude"), 2))
        assertEquals(false, w.busy)
    }

    @Test
    fun `B-AGENT-168 a partial inventory cannot authorize idle`() = runTest {
        val w = World()
        w.pages = { cursor ->
            if (cursor == null) w.page(listOf(w.entries.first()), next = "page-2") else w.page(listOf(w.entries.last()))
        }
        w.sessionPage()
        assertNull(w.busy)
        w.sessionPage("page-2")
        assertEquals(false, w.busy)
    }

    @Test
    fun `B-AGENT-168 initializing and permissions elsewhere keep the helper busy`() = runTest {
        val w = World()
        w.entries[1] = agentJson("elsewhere", cwd = "/work/other", status = "initializing")
        w.sessions()
        assertEquals(true, w.busy)
        w.push("agent_update", jsonOf("agent" to agentJson("elsewhere", cwd = "/work/other")))
        w.push("agent_permission_request", jsonOf("agentId" to "elsewhere", "request" to mapOf("id" to "permission-1")))
        assertEquals(true, w.busy)
        w.push("agent_permission_resolved", jsonOf("requestId" to "permission-1"))
        assertEquals(false, w.busy)
    }

    @Test
    fun `B-AGENT-168 sub-agent work elsewhere survives an idle parent snapshot`() = runTest {
        val w = World()
        w.sessions()
        fun child(status: String) =
            w.push("agent.provider_subagents.update", jsonOf("kind" to "upsert", "subagent" to mapOf("id" to "child", "parentAgentId" to "elsewhere", "status" to status)))
        child("running")
        w.sessions()
        assertEquals(true, w.busy)
        child("completed")
        assertEquals(false, w.busy)
    }

    @Test
    fun `B-AGENT-168 a malformed inventory is unknown while known work takes precedence`() = runTest {
        val w = World()
        w.entries += jsonOf("id" to "broken", "status" to "unexpected")
        w.sessions()
        assertNull(w.busy)
        w.push("agent_stream", stream("elsewhere", mapOf("type" to "turn_started"), 2))
        assertEquals(true, w.busy)
        // An older sequence number cannot end the newer turn.
        w.push("agent_stream", stream("elsewhere", mapOf("type" to "turn_completed"), 1))
        assertEquals(true, w.busy)
    }

    @Test
    fun `B-AGENT-168 disconnect and closure revoke known idle without reading a clock`() = runTest {
        val w = World()
        w.sessions()
        assertEquals(false, w.busy)
        w.inventory.invalidate()
        assertNull(w.busy)
        w.sessions()
        assertEquals(false, w.busy)
        w.connected = false
        assertNull(w.busy)
        // A new socket epoch forgets everything learned on the old one.
        w.connected = true
        w.epoch = 2
        assertNull(w.busy)
    }

    @Test
    fun `B-AGENT-168 a cold idle parent cannot conceal a running sub-agent`() = runTest {
        val w = World()
        w.children = { parent ->
            jsonOf(
                "parentAgentId" to parent,
                "subagents" to if (parent == "elsewhere") listOf(mapOf("id" to "cold-child", "parentAgentId" to "elsewhere", "status" to "running")) else emptyList(),
                "error" to null,
            )
        }
        w.sessions()
        assertEquals(true, w.busy)
    }

    @Test
    fun `B-AGENT-168 saved agents are never loaded just to ask for sub-agents, even one stored as running`() = runTest {
        for (saved in listOf(agentJson("saved", loaded = false), agentJson("rebooted", status = "running", loaded = false))) {
            val w = World()
            w.entries = mutableListOf(agentJson("local"), saved)
            w.sessions()
            assertEquals(listOf("local"), w.childRequests)
            assertEquals(false, w.busy)
        }
    }

    @Test
    fun `B-AGENT-168 an unsupported sub-agent inventory cannot prove idle`() = runTest {
        val w = World()
        w.children = { jsonOf("error" to "unsupported") }
        w.sessions()
        assertNull(w.busy)
        val thrown = World()
        thrown.children = { error("timed out") }
        thrown.sessions()
        assertNull(thrown.busy)
    }

    @Test
    fun `B-AGENT-168 a new idle agent needs cold child coverage before known idle`() = runTest {
        val w = World()
        w.sessions()
        assertEquals(false, w.busy)
        w.push("agent_update", jsonOf("agent" to agentJson("new", cwd = "/work/new")))
        assertNull(w.busy)
    }

    @Test
    fun `B-AGENT-168 an empty complete helper inventory proves idle`() = runTest {
        val w = World()
        w.entries = mutableListOf()
        w.sessions()
        assertEquals(false, w.busy)
        assertTrue(w.childRequests.isEmpty())
    }

    @Test
    fun `B-AGENT-168 a new parent during the child inventory cannot restore known idle`() = runTest {
        val w = World()
        var introduced = false
        w.children = { parent ->
            if (!introduced) {
                introduced = true
                w.push("agent_update", jsonOf("agent" to agentJson("new-parent", cwd = "/work/new")))
            }
            jsonOf("parentAgentId" to parent, "subagents" to emptyList<Any>(), "error" to null)
        }
        w.sessions()
        assertNull(w.busy)
        assertFalse("new-parent" in w.childRequests)
    }

    @Test
    fun `B-AGENT-168 a running push during the fetch outranks the stale idle inventory`() = runTest {
        val w = World()
        w.pages = {
            w.push("agent_update", jsonOf("agent" to agentJson("elsewhere", cwd = "/work/other", status = "running")))
            w.page(w.entries)
        }
        w.sessions()
        assertEquals(true, w.busy)
    }

    @Test
    fun `B-AGENT-168 a closed parent status does not prove independent children idle`() = runTest {
        val w = World()
        w.entries[1] = agentJson("elsewhere", cwd = "/work/other", status = "error")
        w.children = { parent ->
            jsonOf(
                "parentAgentId" to parent,
                "subagents" to if (parent == "elsewhere") listOf(mapOf("id" to "detached-child", "parentAgentId" to "elsewhere", "status" to "running")) else emptyList(),
                "error" to null,
            )
        }
        w.sessions()
        assertEquals(true, w.busy)
    }

    @Test
    fun `B-AGENT-168 malformed completion ordering cannot erase an active turn`() = runTest {
        val w = World()
        w.sessions()
        w.push("agent_stream", stream("elsewhere", mapOf("type" to "turn_started"), 1))
        assertEquals(true, w.busy)
        w.push("agent_stream", stream("elsewhere", mapOf("type" to "turn_completed"), "invalid"))
        assertEquals(true, w.busy)
    }

    @Test
    fun `B-AGENT-168 a stale archived snapshot cannot erase an active turn or child`() = runTest {
        val w = World()
        w.sessions()
        w.push("agent_stream", stream("elsewhere", mapOf("type" to "turn_started"), 1))
        w.push("agent.provider_subagents.update", jsonOf("kind" to "upsert", "subagent" to mapOf("id" to "live-child", "parentAgentId" to "elsewhere", "status" to "running")))
        w.entries[1] = agentJson("elsewhere", cwd = "/work/other", archivedAt = "2026-10-08T10:00:00Z")
        w.sessions()
        assertEquals(true, w.busy)
        w.push("agent_stream", stream("elsewhere", mapOf("type" to "turn_completed"), 2))
        assertEquals(true, w.busy)
        w.push("agent.provider_subagents.update", jsonOf("kind" to "upsert", "subagent" to mapOf("id" to "live-child", "parentAgentId" to "elsewhere", "status" to "completed")))
        // The archived snapshot retired child coverage; live boundaries do not reuse that retired proof.
        assertNull(w.busy)
        w.sessions()
        assertEquals(false, w.busy)
    }

    @Test
    fun `B-AGENT-168 an unseen parent needs a child inventory after its turn, permission or child ends`() = runTest {
        val turn = World()
        turn.sessions()
        turn.push("agent_stream", stream("unseen-parent", mapOf("type" to "turn_started"), 1))
        assertEquals(true, turn.busy)
        turn.push("agent_stream", stream("unseen-parent", mapOf("type" to "turn_completed"), 2))
        assertNull(turn.busy)

        val permission = World()
        permission.sessions()
        permission.push("agent_permission_request", jsonOf("agentId" to "unseen-parent", "request" to mapOf("id" to "new-request")))
        assertEquals(true, permission.busy)
        permission.push("agent_permission_resolved", jsonOf("requestId" to "new-request"))
        assertNull(permission.busy)

        val child = World()
        child.sessions()
        fun push(status: String) =
            child.push("agent.provider_subagents.update", jsonOf("kind" to "upsert", "subagent" to mapOf("id" to "unseen-child", "parentAgentId" to "unseen-parent", "status" to status)))
        push("running")
        assertEquals(true, child.busy)
        push("completed")
        assertNull(child.busy)
    }

    @Test
    fun `B-AGENT-168 the gateway's own evidence and a repeated cursor are honored`() = runTest {
        val w = World()
        w.sessions()
        assertEquals(true, w.inventory.read(connected = true, currentEpoch = w.epoch, gatewayBusy = true))
        // A page whose next cursor repeats an earlier one cannot complete the scan.
        val loop = World()
        loop.pages = { cursor -> loop.page(if (cursor == null) listOf(loop.entries.first()) else listOf(loop.entries.last()), next = "same") }
        loop.sessionPage()
        loop.sessionPage("same")
        assertNull(loop.busy)
    }
}
