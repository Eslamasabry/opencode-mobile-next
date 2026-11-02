@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The "permissions" group of test/paseo_gateway_test.dart, end to end through the gateway and the requests book. */
class PaseoGatewayPermissionsTest {
    private suspend fun TestScope.asked(): GatewayHarness {
        val h = GatewayHarness(this)
        assertIs<Outcome.Ok<*>>(h.gateway.allSessions())
        h.events.clear()
        h.daemon.push("agent_permission_request", jsonOf("agentId" to "a1", "request" to writePermission("perm-1")))
        h.pump()
        return h
    }

    private suspend fun GatewayHarness.reply(id: String, reply: PermissionReply, message: String? = null) =
        assertIs<Outcome.Ok<Unit>>(gateway.replyPermission(SessionId("a1"), RequestId(id), reply, message))

    private fun GatewayHarness.responses(): List<JsonObject> = daemon.of("agent_permission_response")

    @Test
    fun `B-SEC-114 a write request becomes an edit card with its file`() = runTest {
        val h = asked()
        val request = h.events.filterIsInstance<GatewayEvent.PermissionAsked>().single().request
        assertEquals("edit", request.permission)
        assertEquals(listOf("$DIR/hello.txt"), request.patterns)
        assertEquals(listOf("$DIR/hello.txt"), request.always)
        assertEquals(
            "--- /dev/null\n+++ b/$DIR/hello.txt\n@@ -0,0 +1,1 @@\n+hi",
            (request.metadata["diff"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
        assertEquals("perm-1", (h.gateway.pendingPermissions() as Outcome.Ok).value.single().id.raw)
    }

    @Test
    fun `B-SEC-114 once allows while always also applies the provider rule`() = runTest {
        val h = asked()
        h.reply("perm-1", PermissionReply.Once)
        val once = h.responses().single()
        assertEquals("a1", once.str("agentId"))
        assertEquals("perm-1", once.str("requestId"))
        assertEquals(jsonOf("behavior" to "allow"), once["response"])
        assertTrue((h.gateway.pendingPermissions() as Outcome.Ok).value.isEmpty())
        h.daemon.push("agent_permission_request", jsonOf("agentId" to "a1", "request" to writePermission("perm-2")))
        h.pump()
        h.reply("perm-2", PermissionReply.Always)
        assertEquals(
            jsonOf(
                "behavior" to "allow",
                "updatedPermissions" to listOf(mapOf("type" to "setMode", "mode" to "acceptEdits", "destination" to "session")),
            ),
            h.responses().last()["response"],
        )
    }

    @Test
    fun `B-SEC-114 reject denies with the reason and cancel also interrupts`() = runTest {
        val h = asked()
        h.reply("perm-1", PermissionReply.Reject, "wrong file")
        assertEquals(jsonOf("behavior" to "deny", "message" to "wrong file"), h.responses().single()["response"])
        h.daemon.push("agent_permission_request", jsonOf("agentId" to "a1", "request" to writePermission("perm-3")))
        h.pump()
        assertIs<Outcome.Ok<Unit>>(h.gateway.cancelPermission(SessionId("a1"), RequestId("perm-3")))
        assertEquals(jsonOf("behavior" to "deny", "interrupt" to true), h.responses().last()["response"])
    }

    @Test
    fun `B-SEC-114 a call rejected with a typed reason reads as declined`() = runTest {
        val h = asked()
        h.reply("perm-1", PermissionReply.Reject, "use the other file")
        h.events.clear()
        h.daemon.push(
            "agent_stream",
            stream(
                "a1",
                timeline(
                    jsonOf(
                        "type" to "tool_call", "callId" to "toolu_1", "name" to "Write", "status" to "failed",
                        "error" to mapOf("content" to "use the other file"),
                        "detail" to mapOf("type" to "write", "filePath" to "$DIR/hello.txt"),
                    ),
                ),
                seq = 1,
            ),
        )
        h.pump()
        val tool = h.events.filterIsInstance<GatewayEvent.PartUpserted>().last().part.tool
        assertEquals(ToolStatus.Error, tool?.status)
        assertEquals("The user declined this tool call", tool?.output)
    }

    @Test
    fun `B-SEC-114 an answered request does not return from a stale snapshot`() = runTest {
        val h = asked()
        h.reply("perm-1", PermissionReply.Once)
        h.events.clear()
        h.daemon.push(
            "agent_update",
            jsonOf("kind" to "upsert", "agent" to agentJson("a1", status = "running", pendingPermissions = listOf(writePermission("perm-1")))),
        )
        h.pump()
        assertTrue(h.events.none { it is GatewayEvent.PermissionAsked })
        val again = assertIs<Outcome.Failed>(h.gateway.replyPermission(SessionId("a1"), RequestId("perm-1"), PermissionReply.Once, null))
        assertEquals(RefusalReason.StaleScope, assertIs<Failure.Refused>(again.failure).reason)
    }

    @Test
    fun `B-SEC-114 a request answered elsewhere is retired by the next snapshot`() = runTest {
        val h = asked()
        h.events.clear()
        h.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to agentJson("a1")))
        h.pump()
        assertTrue(h.events.any { it is GatewayEvent.PermissionSettled })
        assertTrue((h.gateway.pendingPermissions() as Outcome.Ok).value.isEmpty())
    }

    @Test
    fun `B-SEC-115 a dropped link retires the permission without resending, and the list re-reports it`() = runTest {
        val h = asked()
        h.daemon.drop()
        h.pump()
        assertTrue((h.gateway.pendingPermissions() as Outcome.Ok).value.isEmpty())
        assertTrue(h.responses().isEmpty())
    }
}
