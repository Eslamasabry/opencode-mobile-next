package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome

import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.McpAuthLaunch
import io.github.eslamasabry.oc.core.model.McpConfigScope
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.McpServerKind
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun draft(
    name: String,
    kind: McpServerKind,
    url: String? = null,
    command: List<String> = emptyList(),
    headers: Map<String, String> = emptyMap(),
    timeoutMs: Int? = null,
) = McpServerDraft(
    name = name,
    kind = kind,
    url = url,
    command = command.toImmutableList(),
    cwd = null,
    headers = kotlinx.collections.immutable.persistentMapOf<String, String>().putAll(headers),
    environment = persistentMapOf(),
    detectOAuth = true,
    timeoutMs = timeoutMs,
)

private class ChatGateway : McpGateway {
    var inventory: List<McpServerInfo> = emptyList()
    var adds = 0
    var auths = 0
    var cancels = 0
    var completions = 0
    var failure: Failure? = null
    var statusAfterAdd = "connected"
    var launch = McpAuthLaunch("https://login.example.com/authorize?state=test-state", "test-state")
    var receivedCode: String? = null

    override suspend fun mcpServers(): Outcome<ImmutableList<McpServerInfo>> =
        failure?.failed() ?: inventory.toImmutableList().ok()

    override suspend fun addMcpServer(draft: McpServerDraft, scope: McpConfigScope): Outcome<Unit> {
        adds++
        assertEquals(McpConfigScope.RuntimeLocation, scope)
        inventory = listOf(McpServerInfo(draft.name, statusAfterAdd, null))
        return Unit.ok()
    }

    override suspend fun startMcpAuthentication(name: String): Outcome<McpAuthLaunch> {
        auths++
        return launch.ok()
    }

    override suspend fun completeMcpAuthentication(name: String, code: String): Outcome<McpServerInfo> {
        completions++
        receivedCode = code
        inventory = listOf(McpServerInfo(name, "connected", null))
        return inventory.single().ok()
    }

    override suspend fun cancelMcpAuthentication(name: String): Outcome<Unit> {
        cancels++
        return Unit.ok()
    }
}

class McpTest {
    // ---- B-PROTO-080 (test/product_repository_mcp_test.dart:5)

    @Test
    fun `B-PROTO-080 MCP drafts fail closed on unsafe transport data`() {
        val cases =
            listOf(
                draft("remote", McpServerKind.Remote, url = "ftp://mcp.example.com") to McpDraftProblem.RemoteUrl,
                draft("header", McpServerKind.Remote, url = "https://mcp.example.com", headers = mapOf("Authorization\nInjected" to "secret")) to
                    McpDraftProblem.HeaderPair,
                draft("local", McpServerKind.Local) to McpDraftProblem.LocalCommand,
                draft("timeout", McpServerKind.Local, command = listOf("server"), timeoutMs = 0) to McpDraftProblem.Timeout,
                draft("creds", McpServerKind.Remote, url = "https://user:pw@mcp.example.com") to McpDraftProblem.RemoteUrl,
                draft(" \n", McpServerKind.Local, command = listOf("server")) to McpDraftProblem.Name,
            )
        for ((d, problem) in cases) assertEquals(McpDraftCheck.Invalid(problem), mcpDraftConfig(d), d.name)
        val ok = assertIs<McpDraftCheck.Valid>(mcpDraftConfig(draft("docs", McpServerKind.Remote, url = " https://mcp.example.com ", timeoutMs = 5)))
        assertEquals(JsonPrimitive("remote"), ok.config["type"])
        assertEquals(JsonPrimitive("https://mcp.example.com"), ok.config["url"])
        assertEquals(JsonPrimitive(5), ok.config["timeout"])
        assertNull(ok.config["headers"])
    }

    // ---- B-PROTO-108 (test/sv3_mcp_test.dart:20, :41, :80, :95)

    @Test
    fun `B-PROTO-108 runtime disconnect sends the exact name only when supported`() =
        runTest {
            val sent = mutableListOf<String>()
            val available = ServerCapabilities(serverCatalog = true)
            val unavailable = ServerCapabilities(serverCatalog = false)
            assertTrue(available.mcpRuntimeDisconnect)
            assertFalse(unavailable.mcpRuntimeDisconnect)
            val disconnect: suspend (String) -> Outcome<Unit> = { sent += it; Unit.ok() }
            assertEquals(McpRuntimeResult.Disconnected, disconnectMcpRuntime("docs/server", available, Redactor(), disconnect))
            assertEquals(listOf("docs/server"), sent)
            assertEquals(
                McpRuntimeResult.Refused(McpRuntimeFailure.Unavailable),
                disconnectMcpRuntime("docs/server", unavailable, Redactor(), disconnect),
            )
            assertEquals(listOf("docs/server"), sent)
        }

    @Test
    fun `B-PROTO-108 runtime disconnect rejects unsafe names and returns safe errors`() =
        runTest {
            val sent = mutableListOf<String>()
            var fail = false
            val disconnect: suspend (String) -> Outcome<Unit> = {
                sent += it
                if (fail) Failure.Server(500, details = Redactor().details("untrusted server response")).failed() else Unit.ok()
            }
            val caps = ServerCapabilities(serverCatalog = true)
            for (name in listOf("", " docs ", "docs\nserver", "token=fake-value")) {
                assertEquals(McpRuntimeResult.Refused(McpRuntimeFailure.InvalidName), disconnectMcpRuntime(name, caps, Redactor(), disconnect), name)
            }
            assertTrue(sent.isEmpty())
            fail = true
            val result = disconnectMcpRuntime("docs", caps, Redactor(), disconnect)
            assertEquals(McpRuntimeResult.Refused(McpRuntimeFailure.RequestFailed), result)
            assertFalse(result.toString().contains("untrusted server response"))
        }

    @Test
    fun `B-PROTO-108 MCP setup capabilities do not enable unsupported recovery actions`() {
        val caps =
            ServerCapabilities(
                mcpConfigWrites = true, mcpRuntimeAdds = true, mcpRuntimeRemovals = true, mcpOAuth = true, integrationCredentials = true,
            )
        assertFalse(caps.mcpConfigDeletion)
        assertFalse(caps.mcpRuntimeRemovalUndo)
        assertFalse(caps.mcpOAuthTokenExpiry)
        assertFalse(caps.mcpExpiryAutoReconnect)
    }

    @Test
    fun `B-PROTO-108 unavailable MCP setup keeps all stronger actions unavailable`() {
        val caps =
            ServerCapabilities(
                mcpConfigWrites = false, mcpRuntimeAdds = false, mcpRuntimeRemovals = false, mcpOAuth = false, integrationCredentials = false,
            )
        assertFalse(caps.mcpConfigDeletion)
        assertFalse(caps.mcpRuntimeRemovalUndo)
        assertFalse(caps.mcpOAuthTokenExpiry)
        assertFalse(caps.mcpExpiryAutoReconnect)
    }

    // ---- B-SEC-100 (test/mcp_chat_controller_test.dart:192, :246, :367)

    private val suggestion =
        McpChatSuggestion("design", draft("design", McpServerKind.Remote, url = "https://example.com/mcp"), hosted = true)
    private val caps =
        ServerCapabilities(mcpRuntimeAdds = true, mcpChatConnect = true, mcpChatToolRefresh = true, mcpChatOAuth = true)

    private fun controller(gateway: ChatGateway, current: () -> Boolean = { true }) =
        McpChatConnect(suggestion, { gateway }, { caps }, current)

    @Test
    fun `B-SEC-100 gateway errors never escape or expose raw credential text`() =
        runTest {
            val gateway = ChatGateway().apply { failure = Failure.Server(500, details = Redactor().details("secret-test-value")) }
            val c = controller(gateway)
            c.connect("design")
            assertEquals(McpChatFailure.ConnectFailed, c.snapshot.value.failure)
            assertFalse(c.snapshot.value.toString().contains("secret-test-value"))
        }

    @Test
    fun `B-SEC-100 a throwing gateway is contained the same way`() =
        runTest {
            val throwing =
                object : McpGateway {
                    override suspend fun mcpServers(): Outcome<ImmutableList<McpServerInfo>> = throw IllegalStateException("secret-test-value")
                }
            val c = McpChatConnect(suggestion, { throwing }, { caps }, { true })
            c.connect("design")
            assertEquals(McpChatFailure.ConnectFailed, c.snapshot.value.failure)
            assertFalse(c.snapshot.value.toString().contains("secret-test-value"))
        }

    @Test
    fun `B-SEC-100 dispose never revokes server credentials`() =
        runTest {
            val gateway = ChatGateway().apply { statusAfterAdd = "needs_auth" }
            val c = controller(gateway)
            c.connect("design")
            assertEquals(McpChatPhase.NeedsAuthentication, c.snapshot.value.phase)
            c.startOAuth()
            assertEquals(McpChatPhase.Authorizing, c.snapshot.value.phase)
            c.dispose()
            assertEquals(0, gateway.cancels)
        }

    @Test
    fun `B-SEC-100 explicit invalidation cannot be undone by returning to source`() =
        runTest {
            val gateway = ChatGateway()
            val c = controller(gateway)
            c.invalidate()
            c.connect("design")
            assertEquals(0, gateway.adds)
            assertEquals(McpChatFailure.SourceChanged, c.snapshot.value.failure)
        }

    @Test
    fun `B-SEC-100 OAuth uses a validated code and only an owned cancel reaches the server`() =
        runTest {
            val gateway = ChatGateway().apply { statusAfterAdd = "needs_auth" }
            val c = controller(gateway)
            c.cancelOAuth()
            assertEquals(0, gateway.cancels)
            c.connect("design")
            assertEquals(0, gateway.auths)
            c.startOAuth()
            assertEquals("https://login.example.com/authorize?state=test-state", c.snapshot.value.authorizationUrl)
            assertTrue(c.snapshot.value.manualCodeRequired)
            c.completeOAuth("http://127.0.0.1/callback?code=abc&state=wrong")
            assertEquals(0, gateway.completions)
            assertEquals(McpChatFailure.AuthenticationFailed, c.snapshot.value.failure)

            val ok = controller(ChatGateway().apply { statusAfterAdd = "needs_auth" })
            ok.connect("design")
            ok.startOAuth()
            ok.completeOAuth("http://127.0.0.1/callback?code=abc&state=test-state")
            assertEquals(McpChatPhase.ToolsReady, ok.snapshot.value.phase)
            assertNull(ok.snapshot.value.authorizationUrl)

            val unsafe = ChatGateway().apply { statusAfterAdd = "needs_auth"; launch = McpAuthLaunch("javascript:alert(1)", "test-state") }
            val u = controller(unsafe)
            u.connect("design")
            u.startOAuth()
            assertNull(u.snapshot.value.authorizationUrl)
            assertEquals(McpChatFailure.AuthenticationFailed, u.snapshot.value.failure)

            val cancel = ChatGateway().apply { statusAfterAdd = "needs_auth" }
            val k = controller(cancel)
            k.connect("design")
            k.startOAuth()
            k.cancelOAuth()
            assertEquals(1, cancel.cancels)
            assertNull(k.snapshot.value.authorizationUrl)
        }

    // ---- B-SEC-102 (test/mcp_setup_screen_test.dart:538, :668)

    @Test
    fun `B-SEC-102 header values are secrets - masked, revealable, never prefilled, never in failures`() {
        val redactor = Redactor()
        val form = McpSetupHeaders(redactor)
        assertEquals(listOf(McpHeaderRow()), form.rows)
        assertEquals("", form.rows[0].key)
        assertEquals("", form.rows[0].value)
        assertFalse(form.canRemove(0))
        val fakeToken = "sekret-abc123XYZ"
        form.edit(0, key = "Authorization", value = "Bearer $fakeToken")
        assertTrue(form.rows[0].masked)
        form.toggleReveal(0)
        assertFalse(form.rows[0].masked)
        form.toggleReveal(0)
        assertTrue(form.rows[0].masked)
        // A refused save keeps a fixed reason only.
        val (refused, problem) = form.draft("docs", "ftp://mcp.example.com")
        assertNull(refused)
        assertEquals(McpDraftProblem.RemoteUrl, problem)
        assertFalse(form.saveFailed.toString().contains(fakeToken))
        // The real value reaches the draft sent to the server, and the redactor now knows it.
        val (draft, none) = form.draft("docs", "https://mcp.example.com")
        assertNull(none)
        assertEquals(mapOf("Authorization" to "Bearer $fakeToken"), draft!!.headers)
        assertFalse(redactor.redact("saved with Bearer $fakeToken").contains(fakeToken))
    }

    @Test
    fun `B-SEC-102 adds and removes header rows, a lone row has no remove`() {
        val form = McpSetupHeaders(Redactor())
        form.add()
        assertEquals(2, form.rows.size)
        assertTrue(form.canRemove(1))
        form.remove(1)
        assertEquals(1, form.rows.size)
        assertFalse(form.canRemove(0))
        form.remove(0)
        assertEquals(1, form.rows.size)
    }

    @Test
    fun `B-SEC-102 diagnostics also redact an Authorization=Bearer header value`() {
        val fakeToken = "p0-1-bearer-abcXYZ"
        val line = mcpDiagnosticsLine(Redactor(), "MCP save failed: Authorization=Bearer $fakeToken")
        assertFalse(line.contains(fakeToken))
    }

    @Test
    fun `B-SEC-102 the redacted draft config object carries headers only to the server`() {
        val ok = assertIs<McpDraftCheck.Valid>(mcpDraftConfig(draft("x", McpServerKind.Remote, url = "https://a.example", headers = mapOf("K" to "v"))))
        assertEquals(JsonObject(mapOf("K" to JsonPrimitive("v"))), ok.config["headers"])
        assertEquals(persistentListOf<String>(), draft("x", McpServerKind.Remote).command)
    }
}
