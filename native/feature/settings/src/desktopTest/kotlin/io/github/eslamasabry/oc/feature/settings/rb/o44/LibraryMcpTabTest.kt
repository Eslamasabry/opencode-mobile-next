package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.integrations.catalog.LibraryInventory
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.LibraryScreen
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The MCP servers tab of Commands and tools, empty: one thing to do, on the page margin, nothing drawn empty. */
@OptIn(ExperimentalTestApi::class)
class LibraryMcpTabTest {
    private val calls = mutableListOf<String>()

    private fun actions(): LibraryActions =
        Proxy.newProxyInstance(LibraryActions::class.java.classLoader, arrayOf(LibraryActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TestActions"
                else -> {
                    calls += method.name
                    null
                }
            }
        } as LibraryActions

    private val empty =
        LibraryUi(
            tab = LibraryTab.Mcp,
            available = true,
            inventory =
                LibraryInventory(
                    loading = false,
                    capabilities = ServerCapabilities(mcpRuntimeAdds = true),
                ),
        )

    @Test
    fun `an empty MCP tab keeps its add action on the page margin and draws no empty group`() = runComposeUiTest {
        setContent { SettingsThemed { LibraryScreen(empty, actions()) } }
        onNodeWithText("No MCP servers configured").assertExists()
        // One add action only: the empty state's own, not a loose button above it.
        onAllNodesWithText("Add an MCP server").assertCountEquals(1)
        val add = onNodeWithText("Add an MCP server").getBoundsInRoot()
        assertTrue(add.left >= 16.dp - 0.5.dp, "the add action starts at ${add.left}")
        // A group with no rows draws nothing: every group node on the page has no height.
        onAllNodesWithTag("kit-row-group", useUnmergedTree = true).fetchSemanticsNodes().forEach {
            assertEquals(0, it.size.height, "an empty row group drew a box")
        }
        onNodeWithText("Add an MCP server").performClick()
        assertEquals(listOf("show"), calls)
    }

    @Test
    fun `the top bar's refresh is an action of the bar that still says Refresh`() = runComposeUiTest {
        setContent { SettingsThemed { LibraryScreen(empty, actions()) } }
        onNode(hasContentDescription("Refresh") or hasText("Refresh")).performClick()
        assertEquals(listOf("refresh"), calls)
    }
}
