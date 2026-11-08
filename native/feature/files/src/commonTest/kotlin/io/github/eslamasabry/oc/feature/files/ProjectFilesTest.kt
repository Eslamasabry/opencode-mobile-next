package io.github.eslamasabry.oc.feature.files

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.feature.files.presentation.FilesConnections
import io.github.eslamasabry.oc.feature.files.presentation.FolderPhase
import io.github.eslamasabry.oc.feature.files.presentation.FolderViewModel
import io.github.eslamasabry.oc.feature.files.presentation.ProjectFilesSource
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.TerminalRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Files opened from the project page: one project's folder with no conversation behind it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectFilesTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private val profile = ProfileId("p1")
    private val tree =
        mapOf(
            "" to listOf(FileNode("lib", "lib", true), FileNode("README.md", "README.md", false)),
            "lib" to listOf(FileNode("main.kt", "lib/main.kt", false)),
        )

    /** Conversation reads fail loudly: a project place must never use them. */
    private class ProjectOnly(var source: ProjectFilesSource?) : FilesConnections {
        val asked = mutableListOf<Pair<ProfileId, String>>()

        override fun gateway(ref: ConversationRef): FileGateway? = error("conversation read")

        override fun canBrowse(ref: ConversationRef): Boolean = error("conversation read")

        override suspend fun directory(ref: ConversationRef): String? = error("conversation read")

        override fun project(profile: ProfileId, directory: String): ProjectFilesSource? {
            asked += profile to directory
            return source
        }
    }

    private fun vm(connections: FilesConnections, nav: StackNavigator, path: String = "") =
        FolderViewModel(
            FilesRoute.ProjectFolder(profile, "/work/app", path),
            connections,
            nav,
            emptySet(),
            FakeMentions(),
            emptySet(),
        )

    @Test
    fun `B-NAV-026 Files from the project page lists that project's folder with no conversation`() = runTest {
        val gateway = FakeFileGateway(tree)
        val connections = ProjectOnly(ProjectFilesSource(Any(), gateway, canBrowse = true, canTerminal = true))
        val nav = StackNavigator(ProjectsRoute.Project(profile, "/work/app"))
        val model = vm(connections, nav)
        val ui = model.state.value
        assertEquals(FolderPhase.Ready, ui.phase)
        assertEquals(listOf("lib", "README.md"), ui.entries.map { it.name })
        assertEquals("app", ui.projectName)
        assertEquals(listOf(profile to "/work/app"), connections.asked.distinct())
        // No conversation: nothing to ask in, reference into or attach to.
        assertFalse(ui.inConversation)
        assertFalse(ui.canAttach)
    }

    @Test
    fun `B-NAV-026 project Files walks folders and opens files in the same project, and Terminal opens there`() = runTest {
        val connections =
            ProjectOnly(ProjectFilesSource(Any(), FakeFileGateway(tree), canBrowse = true, canTerminal = true))
        val nav = StackNavigator(ProjectsRoute.Project(profile, "/work/app"))
        val model = vm(connections, nav)
        model.open(FileNode("lib", "lib", true))
        assertEquals(FilesRoute.ProjectFolder(profile, "/work/app", "lib"), nav.top.value)
        model.open(FileNode("README.md", "README.md", false))
        assertEquals(FilesRoute.ProjectFile(profile, "/work/app", "README.md"), nav.top.value)
        model.terminal()
        assertEquals(TerminalRoute.Home(profile, "/work/app"), nav.top.value)
    }

    @Test
    fun `B-NAV-026 project Files says not connected while the server is away, and lists once it is back`() = runTest {
        val connections = ProjectOnly(null)
        val nav = StackNavigator(ProjectsRoute.Project(profile, "/work/app"))
        val model = vm(connections, nav)
        assertEquals(FolderPhase.NotConnected, model.state.value.phase)
        connections.source = ProjectFilesSource(Any(), FakeFileGateway(tree), canBrowse = true)
        model.retry()
        assertEquals(FolderPhase.Ready, model.state.value.phase)
    }

    @Test
    fun `B-NAV-026 a reconnect while a project folder is reading drops the old answer`() = runTest {
        val first = FakeFileGateway(tree)
        val connections = ProjectOnly(ProjectFilesSource(Any(), first, canBrowse = true))
        val nav = StackNavigator(ProjectsRoute.Project(profile, "/work/app"))
        val model = vm(connections, nav)
        assertTrue(model.state.value.phase == FolderPhase.Ready)
        // A different live connection is a different source: the next read goes to it, not the old gateway.
        val second = FakeFileGateway(mapOf("" to listOf(FileNode("new.txt", "new.txt", false))))
        connections.source = ProjectFilesSource(Any(), second, canBrowse = true)
        model.retry()
        assertEquals(listOf("new.txt"), model.state.value.entries.map { it.name })
        assertEquals(listOf(""), second.listed)
    }
}
