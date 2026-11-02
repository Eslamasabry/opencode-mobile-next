package io.github.eslamasabry.oc.feature.files

import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.feature.files.presentation.FolderPhase
import io.github.eslamasabry.oc.feature.files.presentation.FolderViewModel
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.FilesRoute
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

@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModelTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private val tree =
        mapOf(
            "" to
                listOf(
                    FileNode("lib", "lib", true),
                    FileNode(".git", ".git", true),
                    FileNode("README.md", "README.md", false),
                ),
            "lib" to listOf(FileNode("main.dart", "lib/main.dart", false)),
            "secret" to listOf(FileNode(".env", "secret/.env", false)),
            "empty" to emptyList(),
        )

    private fun vm(path: String = "", connections: FakeConnections = FakeConnections(FakeFileGateway(tree))) =
        FolderViewModel(FilesRoute.Folder(conversation, path), connections, StackNavigator(ChatRoute(conversation)))

    @Test
    fun `B-NAV-026 Files opened from a conversation lists that conversation's project root`() = runTest {
        val gateway = FakeFileGateway(tree)
        val model = vm(connections = FakeConnections(gateway))
        val ui = model.state.value
        assertEquals(FolderPhase.Ready, ui.phase)
        assertEquals(listOf("lib", "README.md"), ui.entries.map { it.name })
        assertEquals(1, ui.hiddenCount)
        assertEquals(listOf(""), gateway.listed)
    }

    @Test
    fun `B-NAV-026 a folder row opens the next folder and a file row opens the viewer, both for the same chat`() =
        runTest {
            val nav = StackNavigator(ChatRoute(conversation))
            val model = FolderViewModel(FilesRoute.Folder(conversation), FakeConnections(FakeFileGateway(tree)), nav)
            model.open(FileNode("lib", "lib", true))
            assertEquals(FilesRoute.Folder(conversation, "lib"), nav.top.value)
            model.open(FileNode("README.md", "README.md", false))
            assertEquals(FilesRoute.File(conversation, "README.md"), nav.top.value)
            model.back()
            assertEquals(FilesRoute.Folder(conversation, "lib"), nav.top.value)
        }

    @Test
    fun `B-NAV-026 Show hidden files is a visible toggle and an all-hidden folder says so`() = runTest {
        val model = vm("secret")
        assertTrue(model.state.value.onlyHidden)
        model.toggleHidden()
        assertEquals(listOf(".env"), model.state.value.entries.map { it.name })
        assertFalse(model.state.value.onlyHidden)
        model.toggleHidden()
        assertTrue(model.state.value.onlyHidden)
    }

    @Test
    fun `B-NAV-026 an empty folder is empty, a failed read is a failure with Try again`() = runTest {
        assertTrue(vm("empty").state.value.empty)
        val failing = vm("nope")
        assertEquals(FolderPhase.Failed, failing.state.value.phase)
        assertTrue(failing.state.value.failure != null)
    }

    @Test
    fun `B-NAV-026 a disconnected server and a server without file browsing say so instead of an empty folder`() =
        runTest {
            assertEquals(FolderPhase.NotConnected, vm(connections = FakeConnections(gateway = null)).state.value.phase)
            assertEquals(FolderPhase.Unavailable, vm(connections = FakeConnections(browse = false)).state.value.phase)
        }

    @Test
    fun `B-NAV-026 Try again reads the folder again once the server is back`() = runTest {
        val connections = FakeConnections(gateway = null)
        val model = vm(connections = connections)
        assertEquals(FolderPhase.NotConnected, model.state.value.phase)
        connections.gateway = FakeFileGateway(tree)
        model.retry()
        assertEquals(FolderPhase.Ready, model.state.value.phase)
    }

    @Test
    fun `B-NAV-026 name search shows project-wide results, blank returns to the folder`() = runTest {
        val gateway = FakeFileGateway(tree, found = listOf("lib/main.dart"))
        val model = vm(connections = FakeConnections(gateway))
        model.search(" main ")
        assertEquals("main", model.state.value.query)
        assertEquals(listOf("lib/main.dart"), model.state.value.results.map { it.path })
        assertEquals(listOf("main"), gateway.found_)
        model.search("")
        assertEquals(null, model.state.value.query)
        assertEquals(FolderPhase.Ready, model.state.value.phase)
    }
}
