package io.github.eslamasabry.oc.feature.projects

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.WorkspaceProject
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPagePhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageViewModel
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServer
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServers
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPickerViewModel
import io.github.eslamasabry.oc.feature.projects.presentation.SessionOpenedFolders
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {
    private class Nav : AppNavigator {
        val calls = mutableListOf<String>()
        override val top: MutableStateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            calls += "open $route"
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            calls += "chat ${ref.profileId.raw}/${ref.sessionId.raw} focus=$focusComposer"
        }

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {
            calls += "back"
        }
    }

    private class Servers(list: List<ProjectServer>, active: ProfileId? = null) : ProjectServers {
        override val servers = MutableStateFlow<ImmutableList<ProjectServer>>(list.toImmutableList())
        override val active = MutableStateFlow(active)
    }

    /** Records where each new session was asked to work. */
    private class Creating(val inner: FakeServerGateway) : ServerGateway by inner {
        val made = mutableListOf<NewSession>()
        var refuse: Failure? = null

        override suspend fun createSession(spec: NewSession): Outcome<Session> {
            made += spec
            refuse?.let {
                refuse = null
                return Outcome.Failed(it)
            }
            return session("new1", spec.location?.directory, 100).ok()
        }
    }

    private val p1 = ProfileId("p1")
    private val p2 = ProfileId("p2")

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-NAV-011 a server without a project list shows the folders its conversations work in`() {
        val gateway =
            FakeServerGateway(listOf(session("a", "/work/api", 30), session("b", "/work/web", 20), session("c", "/work/api", 10)))
        gateway.statuses = persistentMapOf(SessionId("a") to SessionStatus.Busy)
        gateway.capabilities.value = ServerCapabilities(projectManagement = false)
        val vm = ProjectsPickerViewModel(ProjectsRoute.Picker(), Nav(), Servers(listOf(ProjectServer(p1, "laptop", gateway, null))), SessionOpenedFolders())
        val ui = vm.state.value
        assertEquals(ProjectsPhase.Ready, ui.phase)
        assertEquals("laptop", ui.serverName)
        assertEquals(listOf("api", "web"), ui.projects.map { it.name })
        assertEquals(2, ui.projects[0].conversations)
        assertEquals(1, ui.projects[0].running)
        assertFalse(ui.projectsLoading)
    }

    @Test
    fun `B-NAV-011 a server that lists projects uses its list, newest first, and a failure keeps Try again`() {
        val gateway = FakeServerGateway(listOf(session("a", "/srv/one", 5)))
        gateway.capabilities.value = ServerCapabilities(projectManagement = true)
        var fail = true
        val ops =
            object : FakeOperationsGateway() {
                override suspend fun listProjects(): Outcome<ImmutableList<WorkspaceProject>> =
                    if (fail) Outcome.Failed(Failure.Network(NetworkWhy.Reset))
                    else
                        persistentListOf(
                            WorkspaceProject("1", "One", "/srv/one", persistentListOf(), 10, true),
                            WorkspaceProject("2", "", "/srv/two", persistentListOf(), 20, false),
                        ).ok()
            }
        val vm = ProjectsPickerViewModel(ProjectsRoute.Picker(), Nav(), Servers(listOf(ProjectServer(p1, "srv", gateway, ops))), SessionOpenedFolders())
        assertNotNull(vm.state.value.projectsFailure)
        fail = false
        vm.retry()
        assertEquals(listOf("two", "One"), vm.state.value.projects.map { it.name })
        assertEquals(listOf(0, 1), vm.state.value.projects.map { it.conversations })
    }

    @Test
    fun `no live server says so and offers Servers`() {
        val nav = Nav()
        val vm = ProjectsPickerViewModel(ProjectsRoute.Picker(), nav, Servers(emptyList()), SessionOpenedFolders())
        assertEquals(ProjectsPhase.NoServer, vm.state.value.phase)
        vm.openServers()
        assertEquals(listOf("open ${ServersRoute.Home}"), nav.calls)
    }

    @Test
    fun `B-ANDR-078 a typed folder opens its project page, and the page puts it under Opened before for that server only`() = runTest {
        val nav = Nav()
        val folders = SessionOpenedFolders()
        val one = FakeServerGateway()
        val two = FakeServerGateway()
        val servers = Servers(listOf(ProjectServer(p1, "one", one, null), ProjectServer(p2, "two", two, null)), active = p2)
        val picker = ProjectsPickerViewModel(ProjectsRoute.Picker(), nav, servers, folders)
        assertEquals(listOf("one", "two"), picker.state.value.servers.map { it.name })
        assertTrue(picker.state.value.servers.single { it.selected }.profile == p2) // the active server first
        picker.askPath()
        assertTrue(picker.state.value.askingPath)
        picker.finishPath("relative/path") // refused by the dialog's check; nothing opens
        assertTrue(nav.calls.isEmpty())
        picker.askPath()
        picker.finishPath(" /work/site/ ")
        assertEquals("open ${ProjectsRoute.Project(p2, "/work/site")}", nav.calls.single())
        ProjectPageViewModel(ProjectsRoute.Project(p2, "/work/site"), nav, servers, folders)
        assertEquals(listOf("/work/site"), folders.recent(p2))
        assertTrue(folders.recent(p1).isEmpty())
        picker.chooseServer(p1)
        assertTrue(picker.state.value.openedBefore.isEmpty())
        picker.chooseServer(p2)
        assertEquals(listOf("site"), picker.state.value.openedBefore.map { it.name })
    }

    @Test
    fun `New conversation here starts a conversation that works in this folder and opens it`() {
        val nav = Nav()
        val gateway = Creating(FakeServerGateway(listOf(session("old", "/work/app", 5), session("there", "/work/site", 9))))
        val folders = SessionOpenedFolders()
        val vm = ProjectPageViewModel(ProjectsRoute.Project(p1, "/work/app"), nav, Servers(listOf(ProjectServer(p1, "srv", gateway, null))), folders)
        assertEquals(ProjectPagePhase.Ready, vm.state.value.phase)
        assertEquals(listOf("old"), vm.state.value.conversations.map { it.ref.sessionId.raw })
        assertEquals(listOf("there"), vm.state.value.elsewhere.map { it.ref.sessionId.raw })
        vm.newConversationHere()
        assertEquals("/work/app", gateway.made.single().location?.directory)
        assertEquals("chat p1/new1 focus=true", nav.calls.last())
        assertEquals(listOf("new1", "old"), vm.state.value.conversations.map { it.ref.sessionId.raw })
        // The shared connection's location never moved.
        assertFalse(gateway.inner.calls.contains("setLocation"))
    }

    @Test
    fun `a refused new conversation says so with its Details and Try again, and opens nothing`() {
        val nav = Nav()
        val gateway = Creating(FakeServerGateway())
        gateway.refuse = Failure.Server(500)
        val vm = ProjectPageViewModel(ProjectsRoute.Project(p1, "/work/app"), nav, Servers(listOf(ProjectServer(p1, "srv", gateway, null))), SessionOpenedFolders())
        vm.newConversationHere()
        assertNotNull(vm.state.value.createFailure)
        assertFalse(vm.state.value.creating)
        assertTrue(nav.calls.none { it.startsWith("chat") })
        vm.newConversationHere()
        assertTrue(nav.calls.last().startsWith("chat p1/new1"))
    }

    @Test
    fun `the project page without its server says so instead of loading forever`() {
        val vm = ProjectPageViewModel(ProjectsRoute.Project(p1, "/work/app"), Nav(), Servers(emptyList()), SessionOpenedFolders())
        assertEquals(ProjectPagePhase.NoServer, vm.state.value.phase)
    }

    @Test
    fun `B-ANDR-078 coming back from a project page shows that folder under Opened before at once`() {
        val nav = Nav()
        val folders = SessionOpenedFolders()
        val servers = Servers(listOf(ProjectServer(p1, "one", FakeServerGateway(), null)))
        val route = ProjectsRoute.Picker()
        nav.top.value = route
        val picker = ProjectsPickerViewModel(route, nav, servers, folders)
        assertTrue(picker.state.value.openedBefore.isEmpty())
        picker.openProject("/work/site")
        nav.top.value = ProjectsRoute.Project(p1, "/work/site")
        ProjectPageViewModel(ProjectsRoute.Project(p1, "/work/site"), nav, servers, folders)
        nav.top.value = route // Back
        assertEquals(listOf("site"), picker.state.value.openedBefore.map { it.name })
    }

    @Test
    fun `B-AGENT-264 the AI Team's own folders are never offered as projects`() {
        val gateway =
            FakeServerGateway(
                listOf(
                    session("a", "/root/projects/my-app", 30),
                    session("b", "/root/aiteam/city", 20),
                    session("c", "/work/city/.gc/worktrees/my-app/polecat", 10),
                )
            )
        gateway.capabilities.value = ServerCapabilities(projectManagement = false)
        val vm = ProjectsPickerViewModel(ProjectsRoute.Picker(), Nav(), Servers(listOf(ProjectServer(p1, "srv", gateway, null))), SessionOpenedFolders())
        assertEquals(listOf("my-app"), vm.state.value.projects.map { it.name })
    }
}
