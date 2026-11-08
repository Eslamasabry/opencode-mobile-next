package io.github.eslamasabry.oc.feature.files

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.feature.files.presentation.FilesConnections
import io.github.eslamasabry.oc.feature.files.presentation.FilesProjectTools
import io.github.eslamasabry.oc.feature.files.presentation.FolderViewModel
import io.github.eslamasabry.oc.feature.files.presentation.ProjectFilesSource
import io.github.eslamasabry.oc.ui.routes.DevServicesRoute
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Device walk 2026-11-08, Files: the ⋯ menu lacked Project health, Worktrees, Dev services and Switch project
 * (Flutter project_hub_screen.dart `_toolsMenu`, gated as `ProjectHub.toolsFor`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilesToolsMenuTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private val profile = ProfileId("p1")
    private val tree = mapOf("" to listOf(FileNode("README.md", "README.md", false)))

    private class Project(val source: ProjectFilesSource) : FilesConnections {
        override fun gateway(ref: ConversationRef): FileGateway? = null

        override fun canBrowse(ref: ConversationRef): Boolean = false

        override suspend fun directory(ref: ConversationRef): String? = null

        override fun project(profile: ProfileId, directory: String): ProjectFilesSource = source
    }

    private fun vm(tools: FilesProjectTools, nav: StackNavigator) =
        FolderViewModel(
            FilesRoute.ProjectFolder(profile, "/work/app"),
            Project(ProjectFilesSource(Any(), FakeFileGateway(tree), canBrowse = true, tools = tools)),
            nav,
            emptySet(),
            FakeMentions(),
            emptySet(),
        )

    @Test
    fun `WALK-1108-F1 the tools follow the server's capabilities as Flutter's toolsFor does`() {
        assertEquals(FilesProjectTools(true, true, false, true), FilesProjectTools.of(true, false, false))
        assertEquals(FilesProjectTools(false, true, false, false), FilesProjectTools.of(false, true, false))
        assertEquals(FilesProjectTools(false, false, true, false), FilesProjectTools.of(false, false, true))
        assertEquals(FilesProjectTools.None, FilesProjectTools.of(false, false, false))
    }

    @Test
    fun `WALK-1108-F1 each tool opens its own page for this project's folder`() = runTest {
        val nav = StackNavigator(ProjectsRoute.Project(profile, "/work/app"))
        val model = vm(FilesProjectTools(health = true, worktrees = true, services = true, switchProject = true), nav)
        assertEquals(FilesProjectTools(true, true, true, true), model.state.value.tools)
        model.projectHealth()
        assertEquals(SearchRoute.ProjectHealth(profile, "/work/app"), nav.top.value)
        model.worktrees()
        assertEquals(ProjectsRoute.Project(profile, "/work/app", initialWorkspace = true), nav.top.value)
        model.devServices()
        assertEquals(DevServicesRoute.Home(profile, "/work/app"), nav.top.value)
        model.switchProject()
        assertEquals(ProjectsRoute.Open, nav.top.value)
    }

    @Test
    fun `WALK-1108-F1 a tool the server does not offer is neither shown nor opened`() = runTest {
        val nav = StackNavigator(ProjectsRoute.Project(profile, "/work/app"))
        val model = vm(FilesProjectTools.None, nav)
        assertEquals(FilesProjectTools.None, model.state.value.tools)
        model.projectHealth()
        model.worktrees()
        model.devServices()
        model.switchProject()
        assertEquals(ProjectsRoute.Project(profile, "/work/app"), nav.top.value)
    }
}
