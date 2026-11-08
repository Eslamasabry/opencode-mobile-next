package io.github.eslamasabry.oc.feature.integrations.catalog

import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flutter mcp_catalog_screen.dart `_row`: each listing says where it runs and what it needs. */
class McpCatalogRowsTest {
    @Test
    fun `B-AGENT-212 a hosted listing names its host and asks for its key`() {
        val row = mcpCatalogRow(entry("Docs search"), emptySet())
        assertEquals(
            listOf(McpCatalogFact.HostedBy("mcp.example.com"), McpCatalogFact.NeedsKey),
            row.facts,
        )
        assertNull(row.blocked)
    }

    @Test
    fun `B-AGENT-212 Node and Python listings say what the server needs`() {
        assertEquals(
            listOf(McpCatalogFact.NeedsNode, McpCatalogFact.NeedsKey),
            mcpCatalogRow(entry("Weather"), emptySet()).facts,
        )
        assertEquals(
            listOf(McpCatalogFact.NeedsPython, McpCatalogFact.NeedsSettings),
            mcpCatalogRow(entry("Notes"), emptySet()).facts,
        )
    }

    @Test
    fun `B-AGENT-212 a listing the app cannot start says why instead of opening`() {
        val docker = mcpCatalogRow(entry("Box runner"), emptySet())
        assertEquals(McpCatalogBlocked.Docker, docker.blocked)
        assertTrue(docker.facts.isEmpty())
        assertEquals(
            McpCatalogBlocked.NoEndpoint,
            mcpCatalogRow(entry("Empty listing"), emptySet()).blocked,
        )
        // One already on this server is never blocked.
        val on = mcpCatalogRow(entry("Box runner"), setOf("box"))
        assertTrue(on.installed)
        assertNull(on.blocked)
    }

    @Test
    fun `B-AGENT-212 the price note sits under a list and not on the consent page`() {
        val entries = registryEntries()
        assertTrue(mcpCatalogUi(McpCatalogState(optedIn = true, entries = entries), false).priceNote)
        assertFalse(mcpCatalogUi(McpCatalogState(optedIn = false, entries = entries), false).priceNote)
        assertFalse(mcpCatalogUi(McpCatalogState(optedIn = true), false).priceNote)
    }
}
