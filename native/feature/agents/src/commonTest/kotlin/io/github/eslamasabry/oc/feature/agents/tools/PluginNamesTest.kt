package io.github.eslamasabry.oc.feature.agents.tools

import io.github.eslamasabry.oc.core.domain.LibraryPlugin
import io.github.eslamasabry.oc.core.domain.LibraryPluginSource
import io.github.eslamasabry.oc.core.domain.LibraryPluginStatus
import io.github.eslamasabry.oc.feature.agents.tools.presentation.PluginGroups
import io.github.eslamasabry.oc.feature.agents.tools.presentation.readablePluginName
import kotlin.test.Test
import kotlin.test.assertEquals

/** A6 (sweep-b 2026-11-11): Tools shows plugin names a person reads, the server's own folded into one row. */
class PluginNamesTest {
    @Test
    fun `A6 plugin ids read as names, as Flutter's readablePluginName does`() {
        assertEquals("Input repair", readablePluginName("opencode.tool.input.repair"))
        assertEquals("Worktree", readablePluginName("opencode.config.worktree"))
        assertEquals("MCP", readablePluginName("opencode.config.mcp"))
        assertEquals("MCP codemode defaults", readablePluginName("opencode.mcp.codemode.defaults"))
        assertEquals("Lint", readablePluginName("@acme/opencode-plugin-lint@1.2.0"))
        assertEquals("Agent", readablePluginName("opencode.agent"))
    }

    @Test
    fun `A6 built-in plugins fold into one group with their counts, added ones stay listed`() {
        fun plugin(id: String, source: LibraryPluginSource, status: LibraryPluginStatus = LibraryPluginStatus.Active) =
            LibraryPlugin(id, status, source, terminalUi = false, packageName = null)
        val groups =
            PluginGroups.of(
                listOf(
                    plugin("opencode.tool.input.repair", LibraryPluginSource.Builtin),
                    plugin("opencode.config.mcp", LibraryPluginSource.Builtin),
                    plugin("opencode.vcs.git", LibraryPluginSource.Builtin, LibraryPluginStatus.Failed),
                    plugin("@acme/opencode-plugin-lint", LibraryPluginSource.Package),
                )
            )
        assertEquals(listOf("@acme/opencode-plugin-lint"), groups.added.map { it.id })
        assertEquals(3, groups.builtIn.size)
        assertEquals(2, groups.builtInActive)
        assertEquals(1, groups.builtInFailed)
    }
}
