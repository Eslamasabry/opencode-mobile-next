package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.model.McpServerKind
import io.github.eslamasabry.oc.engine.integrations.catalog.McpAddProblem
import io.github.eslamasabry.oc.engine.integrations.catalog.McpAddResult
import io.github.eslamasabry.oc.feature.settings.presentation.McpAddFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** s23-projects hook: the Library's Add MCP sheet sends what Flutter's form rules make of its fields. */
class McpAddFieldsTest {
    @Test
    fun `B-MCP-020 a local server sends its program, arguments, environment and a timeout in milliseconds`() {
        val result =
            McpAddFields.draft(
                name = " files ",
                local = true,
                url = "https://ignored.example",
                command = "npx\n -y \n@scope/server\n",
                variables = listOf("TOKEN" to " abc "),
                timeoutSeconds = "30",
                cwd = " /srv/app ",
                detectOAuth = true,
            )
        val draft = assertIs<McpAddResult.Ready>(result).draft
        assertEquals("files", draft.name)
        assertEquals(McpServerKind.Local, draft.kind)
        assertEquals(null, draft.url)
        assertEquals(listOf("npx", "-y", "@scope/server"), draft.command)
        assertEquals(mapOf("TOKEN" to "abc"), draft.environment)
        assertEquals(emptyMap(), draft.headers)
        assertEquals(30_000, draft.timeoutMs)
        assertEquals("/srv/app", draft.cwd)
        assertEquals(false, draft.detectOAuth, "sign-in detection is for remote servers")
    }

    @Test
    fun `B-MCP-021 a remote server sends headers only, keeps sign-in detection, and a bad timeout or name is refused`() {
        val remote =
            McpAddFields.draft("docs", local = false, url = "https://mcp.example/sse", command = "", variables = listOf("Authorization" to "Bearer x"),
                timeoutSeconds = "", cwd = "/ignored", detectOAuth = true)
        val draft = assertIs<McpAddResult.Ready>(remote).draft
        assertEquals(mapOf("Authorization" to "Bearer x"), draft.headers)
        assertEquals(null, draft.timeoutMs)
        assertEquals(null, draft.cwd)
        assertEquals(true, draft.detectOAuth)
        val zero = McpAddFields.draft("docs", false, "https://mcp.example", "", emptyList(), "0", "", false)
        assertEquals(McpAddProblem.Timeout, assertIs<McpAddResult.Invalid>(zero).problem)
        val badName = McpAddFields.draft("docs", false, "https://mcp.example", "", listOf("A=B" to "v"), "", "", false)
        assertEquals(McpAddProblem.PairName(headers = true, row = 1), assertIs<McpAddResult.Invalid>(badName).problem)
        assertEquals("45", McpAddFields.secondsOf(45_000))
        assertEquals("", McpAddFields.secondsOf(null))
    }
}
