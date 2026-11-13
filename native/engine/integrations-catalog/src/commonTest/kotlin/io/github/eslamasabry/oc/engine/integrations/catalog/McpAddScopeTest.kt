package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.McpConfigScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Device sweep C-044: the server's MCP tool servers page said "Nothing to show" with no way to add one, because it
 * offered Add only to servers that write configuration, while an OpenCode 2 server adds at run time. One rule now
 * answers every add page.
 */
class McpAddScopeTest {
    @Test
    fun `MCP-ADD-001 a server that only adds at run time is offered Add, kept until it restarts`() {
        assertEquals(
            McpConfigScope.RuntimeLocation,
            mcpAddScope(ServerCapabilities(mcpConfigWrites = false, mcpRuntimeAdds = true)),
        )
    }

    @Test
    fun `MCP-ADD-002 a server that writes its configuration keeps the server in the project`() {
        assertEquals(
            McpConfigScope.Project,
            mcpAddScope(ServerCapabilities(mcpConfigWrites = true, mcpRuntimeAdds = true)),
        )
    }

    @Test
    fun `MCP-ADD-003 a server that can do neither is not offered Add`() {
        assertNull(mcpAddScope(ServerCapabilities(mcpConfigWrites = false, mcpRuntimeAdds = false)))
    }
}
