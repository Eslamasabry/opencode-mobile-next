package io.github.eslamasabry.oc.feature.projects

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.feature.projects.presentation.ChatTitle
import io.github.eslamasabry.oc.feature.projects.presentation.FolderRowUi
import io.github.eslamasabry.oc.feature.projects.presentation.KeptFolderList
import io.github.eslamasabry.oc.feature.projects.presentation.KeptPageRows
import io.github.eslamasabry.oc.feature.projects.presentation.KeptProjectList
import io.github.eslamasabry.oc.feature.projects.presentation.KeptProjectPage
import io.github.eslamasabry.oc.feature.projects.presentation.NoPhoneProjectDiscovery
import io.github.eslamasabry.oc.feature.projects.presentation.OpenFolderUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectStep
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectViewModel
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectChatUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPagePhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageViewModel
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServer
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServers
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsLastKnown
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPickerViewModel
import io.github.eslamasabry.oc.feature.projects.presentation.SessionOpenedFolders
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json

/**
 * Cache-first (owner rule, 2026-11-09), projects pages: the first frame with kept rows has the same structure as the
 * live frame, live rows replace kept ones in place, and a failed read keeps the rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstProjectsTest {
    private class Nav : AppNavigator {
        override val top: MutableStateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class Servers(list: List<ProjectServer>) : ProjectServers {
        override val servers = MutableStateFlow<ImmutableList<ProjectServer>>(list.toImmutableList())
        override val active = MutableStateFlow<ProfileId?>(null)
    }

    /** The server's conversation list answers only when released, or fails. */
    private class Gate(val inner: FakeServerGateway) : ServerGateway by inner {
        var hold: CompletableDeferred<Unit>? = CompletableDeferred()
        var fail: Failure? = null

        override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> {
            hold?.await()
            fail?.let { return Outcome.Failed(it) }
            return inner.sessionPage(cursor, limit)
        }
    }

    private class Memory : ProjectsLastKnown {
        var projects: List<FolderRowUi>? = null
        val pages = mutableMapOf<String, KeptProjectPage>()
        val folders = mutableMapOf<String?, List<OpenFolderUi>>()

        override fun peekProjects(profile: ProfileId) = projects

        override suspend fun readProjects(profile: ProfileId) = projects

        override suspend fun keepProjects(profile: ProfileId, rows: List<FolderRowUi>) {
            projects = rows
        }

        override fun peekPage(profile: ProfileId, directory: String) = pages[directory]

        override suspend fun readPage(profile: ProfileId, directory: String) = pages[directory]

        override suspend fun keepPage(profile: ProfileId, directory: String, page: KeptProjectPage) {
            pages[directory] = page
        }

        override fun peekFolders(profile: ProfileId, path: String?) = folders[path]

        override suspend fun readFolders(profile: ProfileId, path: String?) = folders[path]

        override suspend fun keepFolders(profile: ProfileId, path: String?, folders: List<OpenFolderUi>) {
            this.folders[path] = folders
        }
    }

    private val p1 = ProfileId("p1")

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun gate(): Gate {
        val inner = FakeServerGateway(listOf(session("a", "/work/api", 30), session("b", "/work/web", 20)))
        inner.capabilities.value = ServerCapabilities(projectManagement = false)
        return Gate(inner)
    }

    private fun picker(gate: Gate, store: ProjectsLastKnown) =
        ProjectsPickerViewModel(
            ProjectsRoute.Picker(),
            Nav(),
            Servers(listOf(ProjectServer(p1, "srv", gate, null))),
            SessionOpenedFolders(),
            NoPhoneProjectDiscovery(),
            lastKnown = store,
        )

    private fun page(gate: Gate, store: ProjectsLastKnown) =
        ProjectPageViewModel(
            ProjectsRoute.Project(p1, "/work/api"),
            Nav(),
            Servers(listOf(ProjectServer(p1, "srv", gate, null))),
            SessionOpenedFolders(),
            lastKnown = store,
        )

    private fun open(gate: Gate, store: ProjectsLastKnown) =
        OpenProjectViewModel(
            Nav(),
            Servers(listOf(ProjectServer(p1, "srv", gate, null))),
            SessionOpenedFolders(),
            lastKnown = store,
        )

    private val keptRows = listOf(FolderRowUi("/work/api", "api", 1, 0), FolderRowUi("/work/web", "web", 1, 0))

    @Test
    fun `CF-20 the picker draws its kept projects at once, with the structure of the live frame`() {
        val store = Memory().also { it.projects = keptRows }
        val gate = gate()
        val vm = picker(gate, store)
        val first = vm.state.value
        assertEquals(ProjectsPhase.Ready, first.phase)
        assertEquals(listOf("api", "web"), first.projects.map { it.name })
        gate.hold!!.complete(Unit)
        val live = vm.state.value
        assertEquals(ProjectsPhase.Ready, live.phase)
        assertEquals(first.projects.map { it.directory }, live.projects.map { it.directory })
        assertFalse(live.projectsLoading)
    }

    @Test
    fun `CF-21 a failed projects read keeps the kept rows and only adds the failure`() {
        val store = Memory().also { it.projects = keptRows }
        val gate = gate()
        gate.fail = Failure.Network(NetworkWhy.Reset)
        val vm = picker(gate, store)
        gate.hold!!.complete(Unit)
        assertEquals(listOf("api", "web"), vm.state.value.projects.map { it.name })
        assertNotNull(vm.state.value.projectsFailure)
    }

    @Test
    fun `CF-22 the live projects replace the kept ones and are kept for next time`() {
        val store = Memory().also { it.projects = listOf(FolderRowUi("/old", "old")) }
        val gate = gate()
        val vm = picker(gate, store)
        assertEquals(listOf("old"), vm.state.value.projects.map { it.name })
        gate.hold!!.complete(Unit)
        assertEquals(listOf("api", "web"), vm.state.value.projects.map { it.name })
        assertEquals(listOf("api", "web"), store.projects?.map { it.name })
    }

    @Test
    fun `CF-23 the project page draws its kept conversations at once as the Ready page`() {
        val kept =
            KeptProjectPage(
                listOf(ProjectChatUi(ConversationRef(p1, SessionId("a")), ChatTitle.Text("Chat a"), "api", false)),
                emptyList(),
            )
        val store = Memory().also { it.pages["/work/api"] = kept }
        val gate = gate()
        val vm = page(gate, store)
        assertEquals(ProjectPagePhase.Ready, vm.state.value.phase)
        assertEquals(listOf("a"), vm.state.value.conversations.map { it.ref.sessionId.raw })
        gate.hold!!.complete(Unit)
        assertEquals(ProjectPagePhase.Ready, vm.state.value.phase)
        assertEquals(listOf("a"), vm.state.value.conversations.map { it.ref.sessionId.raw })
    }

    @Test
    fun `CF-24 a failed page read keeps the kept conversations and reports the failure`() {
        val kept =
            KeptProjectPage(
                listOf(ProjectChatUi(ConversationRef(p1, SessionId("a")), ChatTitle.Text("Chat a"), "api", false)),
                emptyList(),
            )
        val store = Memory().also { it.pages["/work/api"] = kept }
        val gate = gate()
        gate.fail = Failure.Network(NetworkWhy.Reset)
        val vm = page(gate, store)
        gate.hold!!.complete(Unit)
        assertEquals(ProjectPagePhase.Ready, vm.state.value.phase)
        assertEquals(1, vm.state.value.conversations.size)
        assertNotNull(vm.state.value.failure)
    }

    @Test
    fun `CF-25 the live page is kept, and a first-ever open still shows the skeleton phase`() {
        val store = Memory()
        val gate = gate()
        val vm = page(gate, store)
        assertEquals(ProjectPagePhase.Opening, vm.state.value.phase)
        gate.hold!!.complete(Unit)
        assertEquals(ProjectPagePhase.Ready, vm.state.value.phase)
        assertEquals(listOf("a"), store.pages["/work/api"]?.conversations?.map { it.ref.sessionId.raw })
    }

    @Test
    fun `CF-26 the open-project server list draws kept projects at once, not skeleton rows`() {
        val store = Memory().also { it.folders[null] = listOf(OpenFolderUi("api", "/work/api", known = true)) }
        val gate = gate()
        val vm = open(gate, store)
        vm.chooseServer(p1)
        assertEquals(OpenProjectStep.Projects, vm.state.value.step)
        assertFalse(vm.state.value.loading)
        assertEquals(listOf("api"), vm.state.value.folders.map { it.name })
        gate.hold!!.complete(Unit)
        assertEquals(listOf("api", "web"), vm.state.value.folders.map { it.name })
        assertEquals(listOf("api", "web"), store.folders[null]?.map { it.name })
    }

    @Test
    fun `CF-27 a failed listing keeps the kept folders and reports the failure`() {
        val store = Memory().also { it.folders[null] = listOf(OpenFolderUi("api", "/work/api", known = true)) }
        val gate = gate()
        gate.fail = Failure.Network(NetworkWhy.Reset)
        val vm = open(gate, store)
        vm.chooseServer(p1)
        gate.hold!!.complete(Unit)
        assertEquals(listOf("api"), vm.state.value.folders.map { it.name })
        assertNotNull(vm.state.value.failure)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `CF-28 a first-ever listing still shows its loading rows`() {
        val vm = open(gate(), Memory())
        vm.chooseServer(p1)
        assertTrue(vm.state.value.loading)
        assertTrue(vm.state.value.folders.isEmpty())
    }

    @Test
    fun `CF-29 the kept shapes survive the store's JSON round trip`() {
        val json = Json
        val rows = listOf(FolderRowUi("/a", "a", 2, 1, true, "id1"))
        val list = json.decodeFromString(KeptProjectList.serializer(), json.encodeToString(KeptProjectList.serializer(), KeptProjectList.of(rows)))
        assertEquals(rows, list.toRows())
        val page =
            KeptProjectPage(
                listOf(ProjectChatUi(ConversationRef(p1, SessionId("a")), ChatTitle.Text("T"), "api", true)),
                listOf(ProjectChatUi(ConversationRef(p1, SessionId("b")), ChatTitle.New, null, false)),
            )
        val back = json.decodeFromString(KeptPageRows.serializer(), json.encodeToString(KeptPageRows.serializer(), KeptPageRows.of(page)))
        assertEquals(page, back.toPage(p1))
        val folders = listOf(OpenFolderUi("x", "/x", git = true, known = true))
        val kept = json.decodeFromString(KeptFolderList.serializer(), json.encodeToString(KeptFolderList.serializer(), KeptFolderList.of(folders)))
        assertEquals(folders, kept.toFolders())
        assertNull(null)
    }
}
