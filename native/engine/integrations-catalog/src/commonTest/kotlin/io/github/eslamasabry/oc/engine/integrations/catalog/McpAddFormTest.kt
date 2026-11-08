package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.model.McpServerKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The MCP add form's headers, environment and timeout (Flutter mcp_setup_screen.dart `_pairs`, `_timeoutError`). */
class McpAddFormTest {
    private fun ready(input: McpAddInput) = assertIs<McpAddResult.Ready>(McpAddForm.draft(input)).draft

    private fun problem(input: McpAddInput) = assertIs<McpAddResult.Invalid>(McpAddForm.draft(input)).problem

    @Test
    fun `B-SEC-102 a remote server carries its headers and a timeout in milliseconds`() {
        val draft =
            ready(
                McpAddInput(
                    name = " docs ",
                    url = "https://mcp.example.com/sse",
                    headers =
                        listOf(
                            McpPairRow("Authorization", " Bearer test-token "),
                            McpPairRow("", ""), // the empty extra row is ignored
                            McpPairRow("X-Team", "core"),
                        ),
                    // Environment rows typed before switching to an address are not sent.
                    environment = listOf(McpPairRow("API_KEY", "test-value")),
                    timeoutSeconds = " 30 ",
                )
            )
        assertEquals("docs", draft.name)
        assertEquals(McpServerKind.Remote, draft.kind)
        assertEquals(mapOf("Authorization" to "Bearer test-token", "X-Team" to "core"), draft.headers)
        assertTrue(draft.environment.isEmpty())
        assertEquals(30_000, draft.timeoutMs)
        // The server config carries both; nothing in the draft is refused.
        assertIs<McpDraftCheck.Valid>(mcpDraftConfig(draft))
    }

    @Test
    fun `B-SEC-102 a local server carries its environment, and blank timeout means the server's default`() {
        val draft =
            ready(
                McpAddInput(
                    name = "files",
                    executable = "npx",
                    arguments = "-y\n@modelcontextprotocol/server-filesystem\n\n/work",
                    headers = listOf(McpPairRow("Authorization", "Bearer test-token")),
                    environment = listOf(McpPairRow("ROOT", "/work"), McpPairRow("DEBUG", "")),
                )
            )
        assertEquals(McpServerKind.Local, draft.kind)
        assertEquals(listOf("npx", "-y", "@modelcontextprotocol/server-filesystem", "/work"), draft.command)
        assertEquals(mapOf("ROOT" to "/work", "DEBUG" to ""), draft.environment)
        assertTrue(draft.headers.isEmpty())
        assertEquals(null, draft.timeoutMs)
        assertIs<McpDraftCheck.Valid>(mcpDraftConfig(draft))
    }

    @Test
    fun `B-SEC-102 the timeout must be a whole number of seconds above zero`() {
        val base = McpAddInput(name = "x", url = "https://mcp.example.com")
        for (bad in listOf("0", "-5", "1.5", "ten", "9999999")) {
            assertEquals(McpAddProblem.Timeout, problem(base.copy(timeoutSeconds = bad)), bad)
            assertTrue(McpAddForm.timeoutProblem(bad), bad)
        }
        assertFalse(McpAddForm.timeoutProblem(""))
        assertFalse(McpAddForm.timeoutProblem("120"))
    }

    @Test
    fun `B-SEC-102 a bad pair names its row or name, never its value`() {
        val remote = McpAddInput(name = "x", url = "https://mcp.example.com")
        assertEquals(
            McpAddProblem.PairName(headers = true, row = 2),
            problem(remote.copy(headers = listOf(McpPairRow("A", "1"), McpPairRow("", "test-secret")))),
        )
        assertEquals(
            McpAddProblem.PairName(headers = true, row = 1),
            problem(remote.copy(headers = listOf(McpPairRow("A=B", "1")))),
        )
        assertEquals(
            McpAddProblem.PairValue(headers = true, row = 1),
            problem(remote.copy(headers = listOf(McpPairRow("A", "one\ntwo")))),
        )
        assertEquals(
            McpAddProblem.Duplicate(headers = true, name = "A"),
            problem(remote.copy(headers = listOf(McpPairRow("A", "1"), McpPairRow(" A ", "2")))),
        )
        val local = McpAddInput(name = "x", executable = "node")
        assertEquals(
            McpAddProblem.Duplicate(headers = false, name = "KEY"),
            problem(local.copy(environment = listOf(McpPairRow("KEY", "1"), McpPairRow("KEY", "2")))),
        )
        // Rows never print their values.
        assertFalse(McpPairRow("A", "test-secret").toString().contains("test-secret"))
    }

    @Test
    fun `B-SEC-102 the form needs a name and exactly one of address or command`() {
        assertEquals(McpAddProblem.Name, problem(McpAddInput(name = " ", url = "https://mcp.example.com")))
        assertEquals(McpAddProblem.Name, problem(McpAddInput(name = "a\u0007", url = "https://mcp.example.com")))
        assertEquals(McpAddProblem.Kind, problem(McpAddInput(name = "x")))
        assertEquals(McpAddProblem.Kind, problem(McpAddInput(name = "x", url = "https://a.example", executable = "node")))
    }
}
