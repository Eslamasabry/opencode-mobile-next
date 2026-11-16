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
import kotlin.test.assertNull
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

    private class PhoneAccess(var block: io.github.eslamasabry.oc.feature.files.presentation.FilesStorageBlock?) :
        io.github.eslamasabry.oc.feature.files.presentation.FilesStorageAccess {
        var requests = 0

        override suspend fun block(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef, directory: String?) =
            io.github.eslamasabry.oc.feature.files.presentation.FilesStorageBlock.None

        override suspend fun request(
            ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef,
            directory: String?,
            block: io.github.eslamasabry.oc.feature.files.presentation.FilesStorageBlock,
        ) = io.github.eslamasabry.oc.feature.files.presentation.FilesStorageResult.Failed

        override suspend fun phoneStorage(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef) = block

        override suspend fun requestPhoneStorage(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef): io.github.eslamasabry.oc.feature.files.presentation.FilesStorageResult {
            requests++
            return io.github.eslamasabry.oc.feature.files.presentation.FilesStorageResult.Cancelled
        }
    }

    @Test
    fun `I147-PHONE-STORAGE the project's top folder offers Phone storage on a phone connection, and asks Android once`() =
        runTest {
            val off = io.github.eslamasabry.oc.feature.files.presentation.FilesStorageBlock.AppPermission
            val access = PhoneAccess(off)
            fun model(path: String, a: PhoneAccess?) =
                FolderViewModel(
                    FilesRoute.Folder(conversation, path),
                    FakeConnections(FakeFileGateway(tree)),
                    StackNavigator(ChatRoute(conversation)),
                    emptySet(),
                    FakeMentions(),
                    setOfNotNull(a),
                )
            val top = model("", access)
            assertEquals(off, top.state.value.phoneStorage)
            top.allowPhoneStorage()
            assertEquals(1, access.requests)
            assertNull(model("lib", access).state.value.phoneStorage, "only at the project's top folder")
            assertNull(model("", null).state.value.phoneStorage, "a connection that is not this phone's has no row")
            assertNull(model("", PhoneAccess(null)).state.value.phoneStorage)
        }

    private fun vm(path: String = "", connections: FakeConnections = FakeConnections(FakeFileGateway(tree))) =
        FolderViewModel(
            FilesRoute.Folder(conversation, path),
            connections,
            StackNavigator(ChatRoute(conversation)),
            emptySet(),
            FakeMentions(),
            emptySet(),
        )

    private val refreshFailed: io.github.eslamasabry.oc.core.common.Outcome<
        kotlinx.collections.immutable.ImmutableList<io.github.eslamasabry.oc.core.model.VersionControlFile>
    > =
        io.github.eslamasabry.oc.core.common.Outcome.Failed(
            io.github.eslamasabry.oc.core.common.Failure.TimedOut()
        )

    @Test
    fun `I147-FILES-NOTGIT a folder that is not a Git repository shows no change-indicator warning`() =
        runTest {
            val model =
                vm(connections = FakeConnections(FakeFileGateway(tree), changesResult = refreshFailed, notGit = true))
            assertEquals(FolderPhase.Ready, model.state.value.phase)
            assertNull(model.state.value.changesFailure)
        }

    @Test
    fun `I147-FILES-NOTGIT a Git folder whose change read fails still shows the warning`() = runTest {
        val model =
            vm(connections = FakeConnections(FakeFileGateway(tree), changesResult = refreshFailed, notGit = false))
        assertTrue(model.state.value.changesFailure != null)
    }

    @Test
    fun `P-025 the root is titled with the project's folder name`() = runTest {
        assertEquals("app", vm(connections = FakeConnections(FakeFileGateway(tree), dir = "/work/app/")).state.value.projectName)
        // Unknown folder: no name, and the screen falls back to "Project".
        assertEquals(null, vm(connections = FakeConnections(FakeFileGateway(tree), dir = null)).state.value.projectName)
    }

    @Test
    fun `P-025 the project's folder name is the last segment of its path`() {
        assertEquals("my-app", io.github.eslamasabry.oc.feature.files.presentation.projectFolderName("/home/me/my-app/"))
        assertEquals("api", io.github.eslamasabry.oc.feature.files.presentation.projectFolderName("C:\\work\\api"))
        assertEquals(null, io.github.eslamasabry.oc.feature.files.presentation.projectFolderName("/"))
        assertEquals(null, io.github.eslamasabry.oc.feature.files.presentation.projectFolderName("C:\\"))
        assertEquals(null, io.github.eslamasabry.oc.feature.files.presentation.projectFolderName(null))
    }

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
            val model = FolderViewModel(
                    FilesRoute.Folder(conversation),
                    FakeConnections(FakeFileGateway(tree)),
                    nav,
                    emptySet(),
                    FakeMentions(),
                    emptySet(),
                )
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
