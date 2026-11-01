package io.github.eslamasabry.oc.protocol.paseo.requests

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from the permission group of test/paseo_gateway_test.dart (B-SEC-114), test/paseo_native_permissions_test.dart
 * (B-SEC-115) and test/paseo_always_allow_offer_test.dart (B-AGENT-148, the Paseo half).
 */
class PaseoPermissionsTest {
    private val host = FakeRequestHost()
    private val book = PaseoRequestBook(host)

    private fun asked(): List<GatewayEvent.PermissionAsked> = host.events.filterIsInstance<GatewayEvent.PermissionAsked>()

    // ---- B-SEC-114

    @Test
    fun `B-SEC-114 a write request becomes an edit card with its file`() {
        book.onPermissionRequest("a1", writePermission("perm-1"))
        val card = asked().single().request
        assertEquals("edit", card.permission)
        assertEquals(listOf("$DIR/hello.txt"), card.patterns)
        assertEquals(listOf("$DIR/hello.txt"), card.always)
        // A new file is a patch of added lines, so the diff view can draw it.
        assertEquals("--- /dev/null\n+++ b/$DIR/hello.txt\n@@ -0,0 +1,1 @@\n+hi", card.metadata.str("diff"))
        assertEquals("perm-1", book.pendingPermissions().single().id.raw)
        assertEquals(SessionId("a1"), card.sessionId)
        assertEquals("toolu_1", card.tool?.callId)
    }

    @Test
    fun `B-SEC-114 once allows and always also applies the provider's rule`() = runTest {
        book.onPermissionRequest("a1", writePermission("perm-1"))
        book.replyPermission("perm-1", PaseoPermissionAnswer.Once)
        assertEquals(
            jsonOf("agentId" to "a1", "requestId" to "perm-1", "response" to mapOf("behavior" to "allow"), "type" to "agent_permission_response"),
            host.responses().single(),
        )
        assertTrue(book.pendingPermissions().isEmpty())
        assertEquals(GatewayEvent.PermissionSettled(SessionId("a1"), RequestId("perm-1")), host.events.last())

        book.onPermissionRequest("a1", writePermission("perm-2"))
        book.replyPermission("perm-2", PaseoPermissionAnswer.Always)
        assertEquals(
            jsonOf(
                "behavior" to "allow",
                "updatedPermissions" to listOf(mapOf("type" to "setMode", "mode" to "acceptEdits", "destination" to "session")),
            ),
            host.lastResponse(),
        )
    }

    @Test
    fun `B-SEC-114 reject denies with the reason and cancel also interrupts`() = runTest {
        book.onPermissionRequest("a1", writePermission("perm-1"))
        book.replyPermission("perm-1", PaseoPermissionAnswer.Reject, message = "  wrong file ")
        assertEquals(jsonOf("behavior" to "deny", "message" to "wrong file"), host.lastResponse())
        book.onPermissionRequest("a1", writePermission("perm-3"))
        book.replyPermission("perm-3", PaseoPermissionAnswer.Cancel)
        assertEquals(jsonOf("behavior" to "deny", "interrupt" to true), host.lastResponse())
    }

    @Test
    fun `B-SEC-114 a call rejected at its card reads as declined, a once-allowed one does not`() = runTest {
        book.onPermissionRequest("a1", writePermission("perm-1"))
        book.replyPermission("perm-1", PaseoPermissionAnswer.Reject, message = "use the other file")
        assertTrue(book.wasRejected("toolu_1"))
        assertFalse(book.wasRejected("toolu_2"))
    }

    @Test
    fun `B-SEC-114 an answered request does not return from a stale snapshot`() = runTest {
        book.onPermissionRequest("a1", writePermission("perm-1"))
        book.replyPermission("perm-1", PaseoPermissionAnswer.Once)
        host.events.clear()
        book.syncSnapshot("a1", JsonArray(listOf(writePermission("perm-1"))))
        assertTrue(asked().isEmpty())
        val stale = assertFailsWith<PaseoException> { book.replyPermission("perm-1", PaseoPermissionAnswer.Once) }
        assertEquals(PaseoFailureKind.StaleRequest, stale.kind)
        assertEquals(1, host.responses().size)
    }

    @Test
    fun `B-SEC-114 a request answered elsewhere is retired by the next snapshot`() {
        book.onPermissionRequest("a1", writePermission("perm-1"))
        host.events.clear()
        book.syncSnapshot("a1", JsonArray(emptyList()))
        assertIs<GatewayEvent.PermissionSettled>(host.events.single())
        assertTrue(book.pendingPermissions().isEmpty())
        // A snapshot also adds what this client missed; a non-list snapshot changes nothing.
        book.syncSnapshot("a1", JsonArray(listOf(writePermission("perm-9"))))
        assertEquals("perm-9", book.pendingPermissions().single().id.raw)
        book.syncSnapshot("a1", JsonPrimitive("nope"))
        assertEquals(1, book.pendingPermissions().size)
    }

    @Test
    fun `B-SEC-114 permission mapping covers shell and unknown tools`() {
        val shell = paseoPermission(
            "a1",
            jsonOf("id" to "p", "name" to "Bash", "kind" to "tool", "detail" to mapOf("type" to "shell", "command" to "rm -rf build")),
        )
        assertEquals("bash", shell.permission)
        assertEquals(listOf("rm -rf build"), shell.patterns)
        assertTrue(shell.always.isEmpty())
        assertFalse(shell.canAlwaysAllow)
        val other = paseoPermission(
            "a1",
            jsonOf(
                "id" to "q",
                "name" to "WebFetch",
                "kind" to "tool",
                "input" to mapOf("url" to "https://example.com", "nested" to mapOf("x" to 1)),
                "description" to "Fetch a page",
            ),
        )
        assertEquals("webfetch", other.permission)
        assertEquals(
            jsonOf("url" to "https://example.com", "facts" to listOf(mapOf("key" to "url", "value" to "https://example.com"))),
            other.metadata,
        )
        assertEquals("Fetch a page", other.message)
        assertEquals("notebook_edit", toolId("NotebookEdit"))
        assertEquals("Max results", askLabel("maxResults"))
        assertEquals("Max results", askLabel("max_results"))
    }

    @Test
    fun `B-SEC-114 a request whose id or wrong-typed fields are malformed never becomes a card`() {
        book.onPermissionRequest("a1", writePermission("perm-1").without("id"))
        book.onPermissionRequest("a1", writePermission("x").with("name" to 5))
        book.onPermissionRequest("a1", writePermission("y").with("detail" to "write"))
        book.onPermissionRequest("a1", jsonOf("id" to "z", "kind" to "tool", "detail" to mapOf("type" to "shell")))
        assertTrue(book.pendingPermissions().isEmpty())
        assertTrue(host.events.isEmpty())
    }

    // ---- B-AGENT-148 (Paseo half)

    @Test
    fun `B-AGENT-148 always allow is offered only when the agent suggested a rule, else refused before sending`() = runTest {
        book.onPermissionRequest("a1", writePermission("perm-1").without("suggestions"))
        val card = book.pendingPermissions().single()
        assertFalse(card.canAlwaysAllow)
        assertTrue(card.always.isEmpty())
        val refused = assertFailsWith<PaseoException> { book.replyPermission("perm-1", PaseoPermissionAnswer.Always) }
        assertEquals(PaseoFailureKind.Unavailable, refused.kind)
        assertTrue(host.responses().isEmpty())
        book.onPermissionRequest("a1", writePermission("perm-2"))
        assertTrue(book.pendingPermissions().last().canAlwaysAllow)
    }

    // ---- B-SEC-115 (test/paseo_native_permissions_test.dart)

    private fun request(command: String = "pwd", mode: String = "default"): JsonObject =
        jsonOf(
            "id" to "permission-one",
            "provider" to "claude",
            "name" to "Bash",
            "kind" to "tool",
            "detail" to mapOf("type" to "shell", "command" to command),
            "suggestions" to listOf(mapOf("type" to "setMode", "mode" to mode, "destination" to "session")),
        )

    @Test
    fun `B-SEC-115 a same-id command and suggested rule replace the pending permission`() = runTest {
        book.onPermissionRequest("a1", request())
        assertEquals(listOf("pwd"), book.pendingPermissions().single().patterns)
        book.onPermissionRequest("a1", request(command = "git status", mode = "acceptEdits"))
        assertEquals(listOf("git status"), book.pendingPermissions().single().patterns)
        book.replyPermission("permission-one", PaseoPermissionAnswer.Always, legacySessionId = "a1")
        assertEquals(
            jsonOf("behavior" to "allow", "updatedPermissions" to listOf(mapOf("type" to "setMode", "mode" to "acceptEdits", "destination" to "session"))),
            host.lastResponse(),
        )
        assertTrue(host.requests.isEmpty()) // no resume, no timeline read
    }

    @Test
    fun `B-SEC-115 snapshots are detached immutable data and equivalent revisions stay current`() {
        book.onPermissionRequest("a1", request())
        val captured = assertNotNull(book.permissionSnapshot("a1"))
        // Immutable values: a reader cannot mutate the book's copy.
        assertIs<kotlinx.collections.immutable.ImmutableList<String>>(captured.permission.patterns)
        assertEquals(listOf("pwd"), captured.permission.always)
        val before = host.events.size
        book.onPermissionRequest("a1", request())
        assertEquals(before, host.events.size) // an equal revision is not re-announced
        assertTrue(book.isPermissionCurrent("a1", "permission-one", captured.revision))
        assertFalse(book.isPermissionCurrent("other", "permission-one", captured.revision))
        assertNull(book.permissionSnapshot("other"))
    }

    @Test
    fun `B-SEC-115 a hidden suggested-rule change invalidates a captured approval`() {
        book.onPermissionRequest("a1", request())
        val captured = book.permissionSnapshot("a1")!!
        book.onPermissionRequest("a1", request(mode = "acceptEdits"))
        val changed = book.permissionSnapshot("a1")!!
        assertEquals(captured.permission.patterns, changed.permission.patterns)
        assertFalse(book.isPermissionCurrent("a1", "permission-one", captured.revision))
        assertTrue(book.isPermissionCurrent("a1", "permission-one", changed.revision))
    }

    @Test
    fun `B-SEC-115 changed hidden input invalidates an otherwise identical display`() {
        book.onPermissionRequest("a1", request().with("input" to mapOf("command" to "pwd")))
        val captured = book.permissionSnapshot("a1")!!
        book.onPermissionRequest("a1", request().with("input" to mapOf("command" to "git status")))
        assertEquals(listOf("pwd"), book.permissionSnapshot("a1")!!.permission.patterns)
        assertFalse(book.isPermissionCurrent("a1", "permission-one", captured.revision))
    }

    @Test
    fun `B-SEC-115 key order alone is not a new revision`() {
        book.onPermissionRequest("a1", request())
        val captured = book.permissionSnapshot("a1")!!
        val reordered = JsonObject(request().entries.reversed().associate { it.key to it.value })
        book.onPermissionRequest("a1", reordered)
        assertTrue(book.isPermissionCurrent("a1", "permission-one", captured.revision))
    }

    @Test
    fun `B-SEC-115 a malformed replacement retires the prior actionable permission`() = runTest {
        book.onPermissionRequest("a1", request())
        val captured = book.permissionSnapshot("a1")!!
        book.onPermissionRequest("a1", request().with("suggestions" to "malformed"))
        assertNull(book.permissionSnapshot("a1"))
        assertFalse(book.isPermissionCurrent("a1", "permission-one", captured.revision))
        assertFailsWith<PaseoException> { book.replyPermission("permission-one", PaseoPermissionAnswer.Once) }
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-SEC-115 resolve and disconnect retire revisions without resending`() = runTest {
        val start = book.revision.value
        book.onPermissionRequest("a1", request())
        val captured = book.permissionSnapshot("a1")!!
        book.onPermissionResolved("permission-one")
        assertNull(book.permissionSnapshot("a1"))
        assertFalse(book.isPermissionCurrent("a1", "permission-one", captured.revision))
        assertEquals(start + 2, book.revision.value)
        book.onPermissionRequest("a1", request())
        val next = book.permissionSnapshot("a1")!!
        host.connected = false
        host.epoch++
        book.disconnect()
        assertNull(book.permissionSnapshot("a1"))
        assertFalse(book.isPermissionCurrent("a1", "permission-one", next.revision))
        assertTrue(host.responses().isEmpty())
        // A request from the old socket cannot be answered through a new one.
        host.connected = true
        assertFailsWith<PaseoException> { book.replyPermission("permission-one", PaseoPermissionAnswer.Once) }
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-SEC-115 a captured permission from an older epoch is not current after a reconnect`() = runTest {
        book.onPermissionRequest("a1", request())
        val captured = book.permissionSnapshot("a1")!!
        host.epoch++ // the socket was replaced without the book hearing a disconnect yet
        assertFalse(book.isPermissionCurrent("a1", "permission-one", captured.revision))
        assertNull(book.permissionSnapshot("a1"))
        val stale = assertFailsWith<PaseoException> { book.replyPermission("permission-one", PaseoPermissionAnswer.Once) }
        assertEquals(PaseoFailureKind.StaleRequest, stale.kind)
    }

    @Test
    fun `B-SEC-115 location change and session deletion notify and retire permissions`() {
        val start = book.revision.value
        book.onPermissionRequest("a1", request())
        val captured = book.permissionSnapshot("a1")!!
        book.retireSession("a1")
        assertFalse(book.isPermissionCurrent("a1", "permission-one", captured.revision))
        assertEquals(start + 2, book.revision.value)
        book.clear()
        assertEquals(start + 3, book.revision.value)
        assertNull(book.permissionSnapshot("a1"))
    }

    @Test
    fun `B-SEC-115 feed attention updates without a refresh or timeline read`() = runTest {
        assertFalse(book.hasPermission("a1"))
        val start = book.revision.value
        book.onPermissionRequest("a1", request())
        assertTrue(book.hasPermission("a1"))
        assertEquals(start + 1, book.revision.value)
        book.replyPermission("permission-one", PaseoPermissionAnswer.Reject, legacySessionId = "a1")
        assertFalse(book.hasPermission("a1"))
        assertEquals(start + 2, book.revision.value)
        assertTrue(host.requests.isEmpty())
    }

    @Test
    fun `B-SEC-115 a reply from the wrong session or for an unknown request never sends`() = runTest {
        book.onPermissionRequest("a1", request())
        assertEquals(
            PaseoFailureKind.StaleRequest,
            assertFailsWith<PaseoException> { book.replyPermission("permission-one", PaseoPermissionAnswer.Once, legacySessionId = "a2") }.kind,
        )
        assertFailsWith<PaseoException> { book.replyPermission("nope", PaseoPermissionAnswer.Once) }
        // A colliding id from another session cannot replace or retire the card.
        host.providers["a2"] = "claude"
        book.onPermissionRequest("a2", request(command = "rm -rf /"))
        assertEquals(listOf("pwd"), book.pendingPermissions().single().patterns)
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-SEC-114 the daemon's id travels as the agent id of a reply`() = runTest {
        host.realIds["draft-1"] = "real-1"
        host.providers["draft-1"] = "claude"
        book.onPermissionRequest("draft-1", writePermission("perm-1"))
        book.replyPermission("perm-1", PaseoPermissionAnswer.Once)
        assertEquals("real-1", host.responses().single().str("agentId"))
        assertEquals("draft-1", (host.events.first() as GatewayEvent.PermissionAsked).request.sessionId.raw)
        assertNotNull(host.responses().single().obj("response"))
    }
}
