package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.*
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Flutter test/api2_gateway_mappers_test.dart (capabilities), api2_stable_flags_gateway_test.dart,
 * v2_feature_gating_test.dart, api2_mcp_scope_test.dart, api2_provider_workflows_test.dart,
 * managed_shell_gateway_test.dart.
 */
class Oc2OperationsRowsTest {
    private val demo = ServerLocation("/root/projects/demo")

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `B-PROTO-016 capability truth per the port matrix`() {
        val caps = Oc2Gateway.Capabilities
        assertFalse(caps.managedWorkspaces)
        assertFalse(caps.workspaceWarp)
        assertFalse(caps.sessionSteal)
        assertFalse(caps.consoleOrganizations)
        assertFalse(caps.mcpOAuth)
        assertFalse(caps.mcpConfigWrites)
        assertTrue(caps.mcpRuntimeAdds)
        assertFalse(caps.sessionShare)
        assertFalse(caps.sessionArchive)
        assertFalse(caps.sessionTodos)
        assertFalse(caps.messageDelete)
        assertFalse(caps.workspaceSymbols)
        assertFalse(caps.textSearch)
        assertFalse(caps.languageServiceStatus)
        assertFalse(caps.formatterStatus)
        assertFalse(caps.toolInventory)
        assertFalse(caps.experimentalCapabilities)
        assertTrue(caps.shellSettings)
        assertFalse(caps.remoteUpgrade)
        assertFalse(caps.clientDiagnostics)
        assertTrue(caps.gitInit)
        assertTrue(caps.worktreeCreate)
        assertFalse(caps.providerRuntimeRefresh)
        assertFalse(caps.configuredProviderFallback)
        assertTrue(caps.globalEventStream)
        assertFalse(caps.worktreeReset)
        assertFalse(caps.legacyQuestionRequests)
        // The domain defaults (OC1) write MCP config; OC2 adds runtime connectors only.
        assertTrue(ServerCapabilities().mcpConfigWrites)
    }

    @Test
    fun `B-PROTO-124 an attached v2 gateway narrows the flags and the unattached defaults stay conservative`() = runTest {
        val server = RowServer { error("capabilities need no request") }
        val caps = server.gateway.capabilities.value
        assertFalse(caps.sessionShare)
        assertFalse(caps.toolInventory)
        assertFalse(caps.mcpOAuth)
        assertFalse(caps.providerRuntimeRefresh)
        assertTrue(server.seen.isEmpty())
        server.gateway.close()
    }

    // ---- B-PROTO-035

    @Test
    fun `B-PROTO-035 the three 2_0_29 features are advertised`() {
        assertTrue(Oc2Gateway.Capabilities.gitInit)
        assertTrue(Oc2Gateway.Capabilities.worktreeCreate)
        assertTrue(Oc2Gateway.Capabilities.shellSettings)
    }

    @Test
    fun `B-PROTO-035 git init posts vcs init for git in the active location and 409 counts as done`() = runTest {
        val ok = RowServer(location = demo) { respond("", HttpStatusCode.NoContent) }
        assertEquals(Outcome.Ok(Unit), ok.gateway.initializeGitRepository())
        val post = ok.seen.single()
        assertEquals("POST", post.method)
        assertEquals("/api/vcs/init", post.path)
        assertEquals("git", post.query["provider"])
        assertEquals("/root/projects/demo", post.query["location[directory]"])
        ok.gateway.close()

        val exists = RowServer(location = demo) { json("""{"error":"exists"}""", HttpStatusCode.Conflict) }
        assertEquals(Outcome.Ok(Unit), exists.gateway.initializeGitRepository())
        exists.gateway.close()

        val other = RowServer(location = demo) { json("""{"error":"nope"}""", HttpStatusCode.NotImplemented) }
        assertIs<Outcome.Failed>(other.gateway.initializeGitRepository())
        other.gateway.close()
    }

    @Test
    fun `B-PROTO-035 worktree create posts projectID and name and reads the directory`() = runTest {
        val server = RowServer(location = demo) { seen ->
            if (seen.method == "GET" && seen.path == "/api/location") json("""{"project":{"id":"prj_1"}}""")
            else json("""{"directory":"/data/worktree/abc123/probe-a"}""")
        }
        val created = assertIs<Outcome.Ok<WorktreeInfo>>(server.gateway.createWorktree("/root/projects/demo", "probe-a")).value
        assertEquals("/data/worktree/abc123/probe-a", created.directory)
        assertEquals("probe-a", created.name)
        val post = server.seen.single { it.method == "POST" }
        assertEquals("/api/worktree", post.path)
        assertEquals(obj("""{"projectID":"prj_1","name":"probe-a"}"""), post.json)
        server.gateway.close()
    }

    private fun shellServer() = RowServer(location = demo) { seen ->
        when {
            seen.method == "GET" && seen.path == "/api/config/shell" ->
                json("""[{"path":"/bin/bash","name":"bash","acceptable":true},{"path":"/usr/sbin/nologin","name":"nologin","acceptable":false}]""")
            seen.method == "GET" && seen.path == "/api/config" ->
                json("""{"data":[{"type":"global","info":{"shell":"/bin/sh"}},{"type":"project","info":{"shell":"/bin/bash"}},{"type":"project","info":{}}]}""")
            else -> respond("", HttpStatusCode.NoContent)
        }
    }

    @Test
    fun `B-PROTO-035 shell settings list shells and read the highest-priority choice`() = runTest {
        val server = shellServer()
        val settings = assertIs<Outcome.Ok<TerminalShellSettings>>(server.gateway.terminalShellSettings()).value
        assertEquals("/bin/bash", settings.selected)
        assertEquals(listOf("/bin/bash", "/usr/sbin/nologin"), settings.options.map { it.path })
        assertFalse(settings.options.last().acceptable)
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-035 shell settings patch only an acceptable shell, refuse unlisted ones and clear with null`() = runTest {
        val server = shellServer()
        assertEquals(Outcome.Ok(Unit), server.gateway.selectTerminalShell("/bin/bash"))
        val patch = server.seen.single { it.method == "PATCH" }
        assertEquals("/api/experimental/config", patch.path)
        assertEquals(obj("""{"shell":"/bin/bash"}"""), patch.json)
        for (bad in listOf("/usr/sbin/nologin", "/nope/zsh")) assertIs<Outcome.Failed>(server.gateway.selectTerminalShell(bad))
        assertEquals(1, server.seen.count { it.method == "PATCH" })
        assertEquals(Outcome.Ok(Unit), server.gateway.selectTerminalShell(""))
        assertEquals(obj("""{"shell":null}"""), server.seen.last { it.method == "PATCH" }.json)
        server.gateway.close()
    }

    // ---- B-PROTO-026

    private val docs = McpServerDraft("docs", McpServerKind.Remote, "https://example.com/mcp", persistentListOf(), null,
        persistentMapOf(), persistentMapOf(), true, null)

    @Test
    fun `B-PROTO-026 v2 rejects persistent scope and existing names without PUT`() = runTest {
        val server = RowServer { json("""{"data":[{"name":"docs","status":{"status":"disabled"}}]}""") }
        for (scope in listOf(McpConfigScope.Project, McpConfigScope.Global)) {
            assertEquals(
                Failure.Unavailable(FeatureId("mcpConfigWrites")),
                assertIs<Outcome.Failed>(server.gateway.addMcpServer(docs, scope)).failure,
            )
        }
        assertTrue(server.seen.isEmpty())
        assertIs<Failure.Conflict>(assertIs<Outcome.Failed>(server.gateway.addMcpServer(docs, McpConfigScope.RuntimeLocation)).failure)
        assertEquals(listOf("GET"), server.seen.map { it.method })
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-026 MCP uniqueness read and add keep the same location across awaits`() = runTest {
        val read = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val server = RowServer(location = ServerLocation("/work/first", "first")) { seen ->
            if (seen.method == "GET") {
                read.complete(Unit)
                release.await()
                json("""{"data":[]}""")
            } else {
                assertEquals("https://example.com/mcp", (seen.json as JsonObject)["config"]!!.jsonObject["url"].toString().trim('"'))
                respond("", HttpStatusCode.NoContent)
            }
        }
        val pending = async { server.gateway.addMcpServer(docs, McpConfigScope.RuntimeLocation) }
        read.await()
        server.gateway.setLocation(ServerLocation("/work/second", "second"))
        release.complete(Unit)
        assertEquals(Outcome.Ok(Unit), pending.await())
        assertEquals(2, server.seen.size)
        for (seen in server.seen) {
            assertEquals("/work/first", seen.query["location[directory]"])
            assertEquals("first", seen.query["location[workspace]"])
        }
        assertEquals("/api/experimental/mcp/docs", server.seen.last().path)
        server.gateway.close()
    }

    // ---- B-PROTO-030 (protocol half: catalog failure while the first request waits; MCP add scope and timeouts)

    @Test
    fun `B-PROTO-030 the catalog handles a later failure while the first request waits`() = runTest {
        val first = CompletableDeferred<Unit>()
        val server = RowServer { seen ->
            when (seen.path) {
                "/api/provider" -> {
                    first.await()
                    json("""{"data":[]}""")
                }
                "/api/model" -> json("""{"code":"service_starting"}""", HttpStatusCode.ServiceUnavailable)
                else -> json("""{"data":[]}""")
            }
        }
        val providers = async { server.gateway.providers() }
        val catalog = async { server.gateway.catalog() }
        first.complete(Unit)
        assertIs<Failure.Busy>(assertIs<Outcome.Failed>(providers.await()).failure)
        assertIs<Failure.Busy>(assertIs<Outcome.Failed>(catalog.await()).failure)
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-030 MCP actions keep their scope and send the v2 timeout object`() = runTest {
        val server = RowServer(location = ServerLocation("/work/third", "workspace-third")) { seen ->
            if (seen.method == "GET") json("""{"data":[]}""") else respond("", HttpStatusCode.NoContent)
        }
        val g = server.gateway
        assertEquals(Outcome.Ok(Unit), g.addMcpServer(McpServerDraft("local", McpServerKind.Local, null, persistentListOf("node", "server.js"), null, persistentMapOf(), persistentMapOf(), true, 5000), McpConfigScope.RuntimeLocation))
        assertEquals(Outcome.Ok(Unit), g.addMcpServer(docs.copy(name = "remote", timeoutMs = 7000), McpConfigScope.RuntimeLocation))
        assertEquals(Outcome.Ok(Unit), g.addMcpServer(docs.copy(name = "default"), McpConfigScope.RuntimeLocation))
        for (seen in server.seen) {
            assertEquals("/work/third", seen.query["location[directory]"], seen.path)
            assertEquals("workspace-third", seen.query["location[workspace]"], seen.path)
        }
        val configs = server.seen.filter { it.method == "PUT" }.map { (it.json as JsonObject)["config"]!!.jsonObject }
        assertEquals(obj("""{"startup":5000,"catalog":5000,"execution":5000}"""), configs[0]["timeout"])
        assertEquals(obj("""{"startup":7000,"catalog":7000,"execution":7000}"""), configs[1]["timeout"])
        assertFalse("timeout" in configs[2])
        assertEquals(obj("""{"type":"local","command":["node","server.js"],"timeout":{"startup":5000,"catalog":5000,"execution":5000}}"""), configs[0])
        // An invalid draft (credentials in the URL) never reaches the server.
        val before = server.seen.size
        assertIs<Outcome.Failed>(g.addMcpServer(docs.copy(name = "bad", url = "https://user:pw@example.com/mcp"), McpConfigScope.RuntimeLocation))
        assertEquals(before, server.seen.size)
        g.close()
    }

    // ---- B-PROTO-059

    private val shellJson =
        """{"id":"sh_a","command":"flutter test","status":"running","cwd":"/private/work","file":"/private/output","shell":"/bin/sh","metadata":{"sessionID":"ses_a"},"time":{"started":1000}}"""

    @Test
    fun `B-PROTO-059 shell reads byte cursors timeout and delete retain workspace scope`() = runTest {
        // The beta keeps PATCH /shell/{id}/timeout (the stable line removed it: B-PROTO-013).
        val server = RowServer(Oc2Dialect.Beta, ServerLocation("/work", "ws_a")) { seen ->
            when (seen.path) {
                "/api/shell" -> json("""{"data":[$shellJson]}""")
                "/api/shell/sh_a" -> if (seen.method == "DELETE") respond("", HttpStatusCode.NoContent) else json("""{"data":$shellJson}""")
                "/api/shell/sh_a/timeout" -> {
                    assertEquals("PATCH", seen.method)
                    json("""{"data":$shellJson}""")
                }
                "/api/shell/sh_a/output" -> {
                    assertEquals("2", seen.query["cursor"])
                    assertEquals("65536", seen.query["limit"])
                    json("""{"data":{"output":"🙂","cursor":6,"size":6,"truncated":false}}""")
                }
                "/api/health" -> json("""{"healthy":true,"version":"0.0.0-beta-18600","pid":77}""")
                else -> error("Unexpected ${seen.path}")
            }
        }
        val g = server.gateway
        val list = assertIs<Outcome.Ok<ManagedShellList>>(g.runningShells()).value
        assertTrue(list.supported)
        assertEquals("ses_a", list.shells.single().sessionId)
        assertEquals("/private/work", list.shells.single().directory)
        assertEquals(ManagedShellStatus.Running, assertIs<Outcome.Ok<ManagedShell?>>(g.managedShell("sh_a")).value!!.status)
        val page = assertIs<Outcome.Ok<ManagedShellOutput>>(g.managedShellOutput("sh_a", 2, 1_000_000)).value
        assertEquals(6L, page.cursor)
        assertEquals("🙂", page.text)
        assertIs<Outcome.Ok<*>>(g.setManagedShellTimeout("sh_a", 5.minutes))
        assertIs<Outcome.Ok<*>>(g.setManagedShellTimeout("sh_a", null))
        assertEquals(Outcome.Ok(Unit), g.stopManagedShell("sh_a"))
        assertEquals("77", assertIs<Outcome.Ok<String?>>(g.managedShellServerIdentity()).value)
        val updates = server.seen.filter { it.method == "PATCH" }
        assertEquals(obj("""{"timeout":300000}"""), updates[0].json)
        assertEquals(obj("""{"timeout":0}"""), updates[1].json)
        for (seen in server.seen.filter { it.path != "/api/health" }) {
            assertEquals("/work", seen.query["location[directory]"], seen.path)
            assertEquals("ws_a", seen.query["location[workspace]"], seen.path)
        }
        g.close()
    }

    @Test
    fun `B-PROTO-059 shell discovery handles HTTP failures without advertising false support`() = runTest {
        for (status in listOf(404, 405, 501, 401, 500)) {
            val server = RowServer(credentials = ConnectionCredentials("opencode", "test")) {
                respond("", HttpStatusCode.fromValue(status))
            }
            val result = server.gateway.runningShells()
            if (status in listOf(404, 405, 501)) assertFalse(assertIs<Outcome.Ok<ManagedShellList>>(result).value.supported, "$status")
            else assertIs<Outcome.Failed>(result, "$status")
            server.gateway.close()
        }
    }
}
