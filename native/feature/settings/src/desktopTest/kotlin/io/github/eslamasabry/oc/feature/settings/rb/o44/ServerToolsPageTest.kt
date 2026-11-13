package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.integrations.ServerIntegrationActions
import io.github.eslamasabry.oc.feature.settings.integrations.ServerIntegrationScreen
import io.github.eslamasabry.oc.feature.settings.integrations.ServerIntegrationUi
import io.github.eslamasabry.oc.ui.routes.ServerIntegrationPage
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Settings > Agent > Tools > a server (device review C-043, C-044): the server's Commands and tools page and its MCP
 * tool servers page sit on the page margin, and an empty MCP page offers Add when the server can add one.
 */
@OptIn(ExperimentalTestApi::class)
class ServerToolsPageTest {
    private val calls = mutableListOf<String>()

    private fun actions(): ServerIntegrationActions =
        Proxy.newProxyInstance(
            ServerIntegrationActions::class.java.classLoader,
            arrayOf(ServerIntegrationActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TestActions"
                else -> {
                    calls += method.name
                    null
                }
            }
        } as ServerIntegrationActions

    @Test
    fun `C-044 an empty MCP page offers Add on the page margin`() = runComposeUiTest {
        val state =
            ServerIntegrationUi(
                ServerIntegrationPage.Mcp,
                server = "This phone",
                loading = false,
                available = true,
                mayAdd = true,
            )
        setContent { SettingsThemed { ServerIntegrationScreen(state, actions()) } }
        onNodeWithText("Nothing to show for this server.").assertExists()
        // The whole notice, its mark included, sits inside the page gutter (it began at the window edge).
        val notices = onAllNodesWithTag("kit-notice", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(notices.isNotEmpty())
        notices.forEach {
            val left = it.boundsInRoot.left
            assertTrue(left >= 16f * density.density - 1f, "a notice starts at ${left}px")
        }
        val add = onNodeWithText("Add tool server").getBoundsInRoot()
        assertTrue(add.left >= 16.dp - 0.5.dp, "the add action starts at ${add.left}")
        onNodeWithText("Add tool server").performClick()
        assertEquals(listOf("beginAdd"), calls)
    }

    @Test
    fun `C-043 the server's Commands and tools note keeps the page margin and reads as plain words`() =
        runComposeUiTest {
            val state =
                ServerIntegrationUi(
                    ServerIntegrationPage.Tools,
                    server = "This phone",
                    loading = false,
                    available = true,
                    catalog = true,
                    toolsListed = false,
                )
            setContent { SettingsThemed { ServerIntegrationScreen(state, actions()) } }
            onNodeWithText("The pages above show what it has", substring = true).assertExists()
            val width = onRoot().fetchSemanticsNode().boundsInRoot.right
            val gutter = 16f * density.density
            // The note and its mark sit between the page gutters: it ran from the window edge to the other edge.
            val notes = onAllNodesWithTag("kit-notice", useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(notes.isNotEmpty())
            notes.forEach {
                assertTrue(it.boundsInRoot.left >= gutter - 1f, "the note starts at ${it.boundsInRoot.left}px")
                assertTrue(it.boundsInRoot.right <= width - gutter + 1f, "the note ends at ${it.boundsInRoot.right}px")
            }
            onNodeWithText("MCP tool servers").performClick()
            assertEquals(listOf("go"), calls)
        }
}
