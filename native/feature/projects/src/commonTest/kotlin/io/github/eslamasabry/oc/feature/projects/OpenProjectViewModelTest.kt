package io.github.eslamasabry.oc.feature.projects

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.domain.LocatedFileGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.WorkspaceProject
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.feature.projects.presentation.OpenFolderUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectDialog
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectStep
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectViewModel
import io.github.eslamasabry.oc.feature.projects.presentation.OpenServerStatus
import io.github.eslamasabry.oc.feature.projects.presentation.OpenServerUi
import io.github.eslamasabry.oc.feature.projects.presentation.PhoneFolderUi
import io.github.eslamasabry.oc.feature.projects.presentation.PhoneFolders
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectCreation
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServer
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServers
import io.github.eslamasabry.oc.feature.projects.presentation.SessionOpenedFolders
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** B-182: Open a project as Flutter's folder browser: where → the server's projects → its folders → Open. */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenProjectViewModelTest {
    private class Nav : AppNavigator {
        val routes = mutableListOf<AppRoute>()
        var backs = 0
        override val top: MutableStateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            routes += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {
            backs++
        }
    }

    private class Servers(list: List<ProjectServer>) : ProjectServers {
        override val servers = MutableStateFlow<ImmutableList<ProjectServer>>(list.toImmutableList())
        override val active = MutableStateFlow<ProfileId?>(null)
    }

    /** A computer's gateway that lists folders located at a project, as OpenCode 2's fs/list does. */
    private class Located(val inner: FakeServerGateway = FakeServerGateway()) :
        ServerGateway by inner, LocatedFileGateway {
        val asked = mutableListOf<Pair<String?, String>>()
        var tree: Map<String, List<FileNode>> = emptyMap()
        var fail: Failure? = null
        var hold: CompletableDeferred<Unit>? = null

        override fun filesAt(location: ServerLocation): FileGateway =
            object : FileGateway by inner {
                override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> {
                    asked += location.directory to path
                    hold?.await()
                    fail?.let { return Outcome.Failed(it) }
                    val key = if (path == ".") location.directory!! else "${location.directory}/$path"
                    return tree[key].orEmpty().toImmutableList().ok()
                }
            }
    }

    private class Ops(var projects: List<WorkspaceProject> = emptyList()) : FakeOperationsGateway() {
        val made = mutableListOf<Pair<String, String>>()
        val cloned = mutableListOf<Triple<String, String, String>>()
        var refuse: Failure? = null

        override suspend fun listProjects(): Outcome<ImmutableList<WorkspaceProject>> = projects.toImmutableList().ok()

        override suspend fun makeProjectFolder(parent: String, name: String): Outcome<String> {
            made += parent to name
            return refuse?.let { Outcome.Failed(it) } ?: "$parent/$name".ok()
        }

        override suspend fun cloneRepository(parent: String, url: String, name: String): Outcome<String> {
            cloned += Triple(parent, url, name)
            return refuse?.let { Outcome.Failed(it) } ?: "$parent/$name".ok()
        }
    }

    /** This phone's own Linux, read from its files. */
    private class Phone(val tree: Map<String, List<String>>) : PhoneFolders {
        val asked = mutableListOf<String>()

        override suspend fun list(path: String): Outcome<ImmutableList<PhoneFolderUi>> = Outcome.Failed(Failure.Unknown())

        override fun builtinAvailable(profile: ProfileId): Boolean = true

        override suspend fun listBuiltin(path: String): Outcome<ImmutableList<PhoneFolderUi>> {
            asked += path
            return tree[path]?.map { PhoneFolderUi(it, "$path/$it") }?.toImmutableList()?.ok()
                ?: Outcome.Failed(Failure.Folder(FolderProblem.Missing))
        }
    }

    private class PhoneCreator(var there: Boolean = true) : ProjectCreation {
        val made = mutableListOf<String>()

        override fun available(server: ProjectServer): Boolean = server.phoneFolders

        override suspend fun create(server: ProjectServer, name: String): Outcome<String> = Outcome.Failed(Failure.Unknown())

        override suspend fun exists(server: ProjectServer, path: String): Boolean = there

        override suspend fun createAt(server: ProjectServer, path: String): Outcome<String> {
            made += path
            return path.ok()
        }
    }

    private val phoneId = ProfileId("phone")
    private val pcId = ProfileId("pc")

    private fun project(dir: String, git: Boolean, at: Long) =
        WorkspaceProject("id$at", "", dir, persistentListOf(), at, git)

    private fun dir(name: String) = FileNode(name, name, isDir = true)

    private val shell = ServerCapabilities(projectManagement = true, projectFolderCreation = true)

    private fun computer(gateway: Located = Located(), ops: Ops = Ops(), status: MutableStateFlow<StreamStatus>? = null): ProjectServer {
        gateway.inner.capabilities.value = shell
        return ProjectServer(pcId, "studio-pc", gateway, ops, status = status)
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-182 Where is the project lists this phone and each computer with its live state`() {
        val phoneStatus = MutableStateFlow<StreamStatus>(StreamStatus.Connected)
        val pcStatus = MutableStateFlow<StreamStatus>(StreamStatus.Connecting)
        val phoneGateway = FakeServerGateway()
        val vm =
            OpenProjectViewModel(
                Nav(),
                Servers(
                    listOf(
                        ProjectServer(phoneId, "This phone", phoneGateway, null, phoneFolders = true, status = phoneStatus),
                        computer(status = pcStatus),
                    )
                ),
                SessionOpenedFolders(),
            )
        assertEquals(OpenProjectStep.Servers, vm.state.value.step)
        assertEquals(
            listOf(
                OpenServerUi(phoneId, "This phone", true, OpenServerStatus.Connected),
                OpenServerUi(pcId, "studio-pc", false, OpenServerStatus.Connecting),
            ),
            vm.state.value.servers,
        )
        pcStatus.value = StreamStatus.Disconnected(6, null)
        assertEquals(OpenServerStatus.NotConnected, vm.state.value.servers[1].status)
        pcStatus.value = StreamStatus.Connected
        assertEquals(OpenServerStatus.Connected, vm.state.value.servers[1].status)
    }

    @Test
    fun `B-182 a computer's projects are marked, a tap goes in, and Back goes up a level at a time`() {
        val gateway = Located()
        gateway.tree =
            mapOf(
                "/srv/app" to listOf(dir("src"), dir(".git"), FileNode("README.md", "README.md", false), dir("docs")),
                "/srv/app/src" to listOf(dir("main")),
            )
        val ops = Ops(listOf(project("/srv/app", git = true, at = 2), project("/srv/notes", git = false, at = 1)))
        val nav = Nav()
        val vm = OpenProjectViewModel(nav, Servers(listOf(computer(gateway, ops))), SessionOpenedFolders())
        vm.chooseServer(pcId)
        val projects = vm.state.value
        assertEquals(OpenProjectStep.Projects, projects.step)
        assertEquals(
            listOf(OpenFolderUi("app", "/srv/app", git = true, known = true), OpenFolderUi("notes", "/srv/notes", known = true)),
            projects.folders,
        )

        vm.enterFolder("/srv/app")
        assertEquals(OpenProjectStep.Folder, vm.state.value.step)
        // Hidden folders and files are left out; the listing is located at the project itself.
        assertEquals(listOf("docs", "src"), vm.state.value.folders.map { it.name })
        assertEquals("/srv/app" to ".", gateway.asked.last())

        vm.enterFolder("/srv/app/src")
        assertEquals("/srv/app" to "src", gateway.asked.last(), "deeper folders stay located at their project")
        assertEquals(listOf(OpenFolderUi("main", "/srv/app/src/main")), vm.state.value.folders)

        vm.back()
        assertEquals("/srv/app", vm.state.value.path)
        vm.back()
        assertEquals(OpenProjectStep.Projects, vm.state.value.step)
        vm.back()
        assertEquals(OpenProjectStep.Servers, vm.state.value.step)
        vm.back()
        assertEquals(1, nav.backs)
        assertTrue(nav.routes.isEmpty())
    }

    @Test
    fun `B-182 Open name opens the folder shown as the project`() {
        val gateway = Located().apply { tree = mapOf("/srv/app" to listOf(dir("api"))) }
        val nav = Nav()
        val vm = OpenProjectViewModel(nav, Servers(listOf(computer(gateway, Ops(listOf(project("/srv/app", true, 1)))))), SessionOpenedFolders())
        vm.chooseServer(pcId)
        vm.enterFolder("/srv/app")
        vm.enterFolder("/srv/app/api")
        vm.openShown()
        assertEquals(listOf<AppRoute>(ProjectsRoute.Project(pcId, "/srv/app/api")), nav.routes)
    }

    @Test
    fun `B-182 on this phone the project space is listed from its files, projects and folders together`() {
        val phone = Phone(mapOf("/root/projects" to listOf("app", "scratch"), "/root/projects/app" to listOf("lib")))
        val ops = Ops(listOf(project("/root/projects/app", git = true, at = 1)))
        val gateway = FakeServerGateway().apply { capabilities.value = shell }
        val vm =
            OpenProjectViewModel(
                Nav(),
                Servers(listOf(ProjectServer(phoneId, "This phone", gateway, ops, phoneFolders = true))),
                SessionOpenedFolders(),
                PhoneCreator(),
                phone,
            )
        vm.chooseServer(phoneId)
        assertEquals(
            listOf(OpenFolderUi("app", "/root/projects/app", git = true, known = true), OpenFolderUi("scratch", "/root/projects/scratch")),
            vm.state.value.folders,
        )
        assertEquals("/root/projects", vm.state.value.makeIn)
        vm.enterFolder("/root/projects/app")
        assertEquals(listOf("lib"), vm.state.value.folders.map { it.name })
        assertEquals("/root/projects/app", phone.asked.last())
    }

    @Test
    fun `B-182 a slow listing says it is still reading, a failed one says why and Try again lists again`() = runTest {
        val gateway = Located().apply { tree = mapOf("/srv/app" to listOf(dir("src"))) }
        val vm = OpenProjectViewModel(Nav(), Servers(listOf(computer(gateway, Ops(listOf(project("/srv/app", true, 1)))))), SessionOpenedFolders())
        vm.chooseServer(pcId)
        val hold = CompletableDeferred<Unit>()
        gateway.hold = hold
        vm.enterFolder("/srv/app")
        runCurrent()
        assertTrue(vm.state.value.loading)
        assertFalse(vm.state.value.slow)
        advanceTimeBy(3_001)
        assertTrue(vm.state.value.slow, "still reading after a moment")

        gateway.fail = Failure.Network(NetworkWhy.Unknown)
        hold.complete(Unit)
        runCurrent()
        assertFalse(vm.state.value.loading)
        assertFalse(vm.state.value.slow)
        assertIs<Failure.Network>(vm.state.value.failure)

        gateway.hold = null
        gateway.fail = null
        vm.retry()
        runCurrent()
        assertNull(vm.state.value.failure)
        assertEquals(listOf("src"), vm.state.value.folders.map { it.name })
    }

    @Test
    fun `B-182 an empty folder is an empty state, not an error`() {
        val gateway = Located().apply { tree = mapOf("/srv/app" to emptyList()) }
        val vm = OpenProjectViewModel(Nav(), Servers(listOf(computer(gateway, Ops(listOf(project("/srv/app", true, 1)))))), SessionOpenedFolders())
        vm.chooseServer(pcId)
        vm.enterFolder("/srv/app")
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.failure)
        assertTrue(vm.state.value.folders.isEmpty())
    }

    @Test
    fun `B-182 New folder is made on the server inside the folder shown, then opens`() {
        val gateway = Located().apply { tree = mapOf("/srv/app" to emptyList()) }
        val ops = Ops(listOf(project("/srv/app", true, 1)))
        val nav = Nav()
        val opened = SessionOpenedFolders()
        val vm = OpenProjectViewModel(nav, Servers(listOf(computer(gateway, ops))), opened)
        vm.chooseServer(pcId)
        // On the projects page it goes beside the newest Git project.
        assertEquals("/srv", vm.state.value.makeIn)
        vm.enterFolder("/srv/app")
        assertTrue(vm.state.value.canMake)
        vm.askNewFolder()
        assertEquals(OpenProjectDialog.NewFolder, vm.state.value.dialog)
        vm.createFolder("tools")
        assertEquals(listOf("/srv/app" to "tools"), ops.made)
        assertNull(vm.state.value.dialog)
        assertEquals(listOf<AppRoute>(ProjectsRoute.Project(pcId, "/srv/app/tools")), nav.routes)
        assertEquals(listOf("/srv/app/tools"), opened.recent(pcId))
    }

    @Test
    fun `B-182 Clone copies a repository into the folder shown; a failure keeps the dialog with the reason`() {
        val gateway = Located()
        val ops = Ops(listOf(project("/srv/app", true, 1)))
        val nav = Nav()
        val vm = OpenProjectViewModel(nav, Servers(listOf(computer(gateway, ops))), SessionOpenedFolders())
        vm.chooseServer(pcId)
        vm.askClone()
        assertEquals(OpenProjectDialog.Clone, vm.state.value.dialog)
        ops.refuse = Failure.Rejected(null)
        vm.cloneRepository("https://example.test/o/web.git", "web")
        assertEquals(OpenProjectDialog.Clone, vm.state.value.dialog)
        assertIs<Failure.Rejected>(vm.state.value.dialogFailure)
        assertTrue(nav.routes.isEmpty())

        ops.refuse = null
        vm.cloneRepository("https://example.test/o/web.git", "web")
        assertEquals(Triple("/srv", "https://example.test/o/web.git", "web"), ops.cloned.last())
        assertEquals(listOf<AppRoute>(ProjectsRoute.Project(pcId, "/srv/web")), nav.routes)

        // An address carrying a token never reaches the server.
        vm.askClone()
        vm.cloneRepository("https://me:token@example.test/o/x.git", "x")
        assertEquals(2, ops.cloned.size)
    }

    @Test
    fun `B-182 a server that cannot make folders offers only Type a path`() {
        val gateway = Located()
        gateway.inner.capabilities.value = ServerCapabilities(projectManagement = true, projectFolderCreation = false)
        val server = ProjectServer(pcId, "old-pc", gateway, Ops(listOf(project("/srv/app", true, 1))))
        val vm = OpenProjectViewModel(Nav(), Servers(listOf(server)), SessionOpenedFolders())
        vm.chooseServer(pcId)
        assertFalse(vm.state.value.canMake)
        assertFalse(vm.state.value.canClone)
        vm.askClone()
        vm.askNewFolder()
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `B-182 Type a path opens it; on this phone a missing folder is offered and made first`() {
        val nav = Nav()
        val creator = PhoneCreator(there = false)
        val gateway = FakeServerGateway().apply { capabilities.value = shell }
        val vm =
            OpenProjectViewModel(
                nav,
                Servers(listOf(ProjectServer(phoneId, "This phone", gateway, Ops(), phoneFolders = true), computer())),
                SessionOpenedFolders(),
                creator,
                Phone(mapOf("/root/projects" to emptyList())),
            )
        vm.chooseServer(phoneId)
        vm.askPath()
        assertEquals(OpenProjectDialog.Path, vm.state.value.dialog)
        vm.finishPath("/root/projects/new-one")
        assertEquals("/root/projects/new-one", vm.state.value.missingPath)
        vm.createMissing()
        assertEquals(listOf("/root/projects/new-one"), creator.made)
        assertEquals(listOf<AppRoute>(ProjectsRoute.Project(phoneId, "/root/projects/new-one")), nav.routes)

        // A computer's path opens as typed; the server reports a missing folder itself.
        vm.chooseServer(pcId)
        vm.askPath()
        vm.finishPath("/home/me/work/site/")
        assertEquals(ProjectsRoute.Project(pcId, "/home/me/work/site"), nav.routes.last())
        // The home folder or the root is never a project.
        vm.askPath()
        vm.finishPath("/")
        assertEquals(2, nav.routes.size)
    }
}
