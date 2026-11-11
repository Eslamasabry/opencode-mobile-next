package io.github.eslamasabry.oc.feature.projects

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.feature.projects.presentation.FolderRowUi
import io.github.eslamasabry.oc.feature.projects.presentation.KeptProjectList
import io.github.eslamasabry.oc.feature.projects.presentation.NoPhoneProjectDiscovery
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectRules
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServer
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServers
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPickerViewModel
import io.github.eslamasabry.oc.feature.projects.presentation.SessionOpenedFolders
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * ONE-PHONE (owner 2026-11-11, "This phone" and "Phone agents" side by side): This phone's agents are part of This
 * phone. Projects offers one This phone, with the folders the agents work in among its projects, each folder once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnePhoneProjectsTest {
    private class Nav : AppNavigator {
        val opened = mutableListOf<AppRoute>()
        override val top: MutableStateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            opened += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class Servers(list: List<ProjectServer>, active: ProfileId? = null) : ProjectServers {
        override val servers = MutableStateFlow<ImmutableList<ProjectServer>>(list.toImmutableList())
        override val active = MutableStateFlow(active)
    }

    private val phone = ProfileId("phone")
    private val agents = ProfileId("phone-agents")
    private val laptop = ProfileId("laptop")

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun gateway(vararg sessions: io.github.eslamasabry.oc.core.model.Session) =
        FakeServerGateway(sessions.toList()).also {
            it.capabilities.value = ServerCapabilities(projectManagement = false)
        }

    @Test
    fun `ONE-PHONE Projects offers This phone once, with its agents' folders among its projects, each once`() {
        val nav = Nav()
        val own = gateway(session("a", "/root/projects/app", 30), session("b", "/root/projects/web", 20))
        val helper = gateway(session("c1", "/root/projects/app", 40), session("c2", "/root/projects/notes", 10))
        val servers =
            Servers(
                listOf(
                    ProjectServer(laptop, "Laptop", gateway(session("l", "/home/me/site", 5)), null),
                    ProjectServer(phone, "This phone", own, null),
                    // Saved under the phone's name and listed beside it: still never a server of its own here.
                    ProjectServer(agents, "This phone", helper, null, partOf = phone),
                ),
                active = phone,
            )
        // Opened from an agent's conversation: the page is This phone's.
        val vm = ProjectsPickerViewModel(ProjectsRoute.Picker(agents), nav, servers, SessionOpenedFolders(), NoPhoneProjectDiscovery())
        val ui = vm.state.value
        assertEquals(listOf(laptop, phone), ui.servers.map { it.profile })
        assertEquals(phone, ui.servers.single { it.selected }.profile)
        // The phone's own folders first, then the agents' folder only they work in; "app" (both) is one row.
        assertEquals(listOf("/root/projects/app", "/root/projects/web", "/root/projects/notes"), ui.projects.map { it.directory })
        assertEquals(listOf(null, null, agents), ui.projects.map { it.opensOn })
        // A folder only the agents work in opens on their connection, where its conversations are; the rest on the phone.
        vm.openProject("/root/projects/notes")
        vm.openProject("/root/projects/app/")
        assertEquals(
            listOf<AppRoute>(ProjectsRoute.Project(agents, "/root/projects/notes"), ProjectsRoute.Project(phone, "/root/projects/app")),
            nav.opened,
        )
    }

    @Test
    fun `ONE-PHONE with This phone not connected its agents still show their projects, as This phone`() {
        val helper = gateway(session("c1", "/root/projects/notes", 10))
        val servers = Servers(listOf(ProjectServer(agents, "This phone", helper, null, partOf = phone)))
        val vm = ProjectsPickerViewModel(ProjectsRoute.Picker(), Nav(), servers, SessionOpenedFolders(), NoPhoneProjectDiscovery())
        assertEquals("This phone", vm.state.value.serverName)
        assertEquals(listOf("/root/projects/notes"), vm.state.value.projects.map { it.directory })
    }

    @Test
    fun `ONE-PHONE joining keeps the server's rows and adds each other folder once`() {
        val own = listOf(FolderRowUi("/w/app", "app", conversations = 2), FolderRowUi("/w/web", "web"))
        val others =
            listOf(
                FolderRowUi("/w/app/", "app", conversations = 5, opensOn = agents),
                FolderRowUi("/w/notes", "notes", opensOn = agents),
                FolderRowUi("/w/notes", "notes", opensOn = agents),
            )
        val joined = ProjectRules.joined(own, others)
        assertEquals(listOf("/w/app", "/w/web", "/w/notes"), joined.map { it.directory })
        assertEquals(2, joined.first().conversations, "a folder both work in keeps the server's own row")
        // The kept list remembers where a folder opens, so a cached row opens the same place.
        assertEquals(joined, KeptProjectList.of(joined).toRows())
    }
}
