package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPlace
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import io.github.eslamasabry.oc.ui.routes.ServerIntegrationPage
import io.github.eslamasabry.oc.ui.routes.ServerIntegrationRoute
import io.github.eslamasabry.oc.ui.routes.ServerSettingsRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import io.github.eslamasabry.oc.ui.routes.TerminalRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S24-CHAT story 2 (Flutter `_builtinChatCommands`): the launcher's added commands open this
 * conversation's own places, and the gated ones follow the server's capabilities.
 */
class S24CommandPlacesTest {
    private class FolderWorld(var folder: String?) : FakeChatWorld() {
        override val directory: String?
            get() = folder
    }

    private val world = FolderWorld("/root/projects/app")
    private val nav = RecordingNavigator()
    private val presenter = ChatPresenter(world, nav, Immediate)

    @Test
    fun `B-CHAT-S24-10 each launcher place opens its route for this conversation's server and project`() {
        ChatPlace.entries.forEach(presenter::openPlace)
        val p = Ref.profileId
        assertEquals(
            listOf<AppRoute>(
                ProjectsRoute.Project(p, "/root/projects/app", initialWorkspace = true),
                SearchRoute.ProjectHealth(p, "/root/projects/app"),
                TerminalRoute.Conversation(Ref),
                ServerIntegrationRoute(p, ServerIntegrationPage.Mcp),
                ServerIntegrationRoute(p, ServerIntegrationPage.Providers),
                ServerIntegrationRoute(p, ServerIntegrationPage.Skills),
                ServerIntegrationRoute(p, ServerIntegrationPage.References),
                ServerSettingsRoute(p),
                SettingsRoute.ReportProblem,
                SettingsRoute.Target("settings-appearance"),
            ),
            nav.opened.toList(),
        )
    }

    @Test
    fun `B-CHAT-S24-11 with no known folder, workspaces open the picker and health opens nothing`() {
        world.folder = null
        presenter.openPlace(ChatPlace.Workspaces)
        presenter.openPlace(ChatPlace.Health)
        assertEquals(listOf<AppRoute>(ProjectsRoute.Picker(Ref.profileId)), nav.opened.toList())
    }

    @Test
    fun `B-CHAT-S24-12 terminal, agents, variants, approvals and health follow the server's flags`() {
        world.capabilities.value =
            ServerCapabilities(
                terminal = false,
                agentSelection = false,
                serverCatalog = false,
                permissionRequests = false,
                languageServiceStatus = false,
                formatterStatus = false,
            )
        val off = presenter.current().capabilities
        assertFalse(off.terminal || off.agents || off.serverCatalog || off.approvals || off.health)
        world.capabilities.value = ServerCapabilities(languageServiceStatus = false, formatterStatus = true)
        val on = presenter.current().capabilities
        assertTrue(on.terminal && on.agents && on.serverCatalog && on.approvals && on.health)
    }
}
