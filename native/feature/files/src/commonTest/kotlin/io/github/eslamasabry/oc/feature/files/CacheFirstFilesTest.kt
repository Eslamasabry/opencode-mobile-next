package io.github.eslamasabry.oc.feature.files

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.engine.files.FileBody
import io.github.eslamasabry.oc.engine.files.WifiShare
import io.github.eslamasabry.oc.feature.files.presentation.FilePhase
import io.github.eslamasabry.oc.feature.files.presentation.FileViewModel
import io.github.eslamasabry.oc.feature.files.presentation.FilesLastKnown
import io.github.eslamasabry.oc.feature.files.presentation.FolderPhase
import io.github.eslamasabry.oc.feature.files.presentation.FolderViewModel
import io.github.eslamasabry.oc.feature.files.presentation.KeptFile
import io.github.eslamasabry.oc.feature.files.presentation.KeptFolder
import io.github.eslamasabry.oc.feature.files.presentation.NoFileLinks
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Cache-first (owner rule, 2026-11-09): Files draw their real rows from the last data seen, then refresh in place. */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstFilesTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private class Shelf : FilesLastKnown {
        val folders = HashMap<String, KeptFolder>()
        val texts = HashMap<String, KeptFile>()

        override fun peekFolder(profile: ProfileId, key: String) = folders[key]

        override suspend fun readFolder(profile: ProfileId, key: String) = folders[key]

        override suspend fun keepFolder(profile: ProfileId, key: String, folder: KeptFolder) {
            folders[key] = folder
        }

        override fun peekFile(profile: ProfileId, key: String) = texts[key]

        override suspend fun readFile(profile: ProfileId, key: String) = texts[key]

        override suspend fun keepFile(profile: ProfileId, key: String, file: KeptFile) {
            texts[key] = file
        }
    }

    private val tree =
        mapOf(
            "" to listOf(FileNode("lib", "lib", true), FileNode("README.md", "README.md", false)),
        )

    private fun folder(shelf: Shelf, connections: FakeConnections) =
        FolderViewModel(
            FilesRoute.Folder(conversation, ""),
            connections,
            StackNavigator(ChatRoute(conversation)),
            emptySet(),
            FakeMentions(),
            emptySet(),
            setOf(shelf),
        )

    @Test
    fun `CF-30 a folder opens on its kept rows with the live frame's structure, even with the server away`() = runTest {
        val shelf = Shelf()
        val live = folder(shelf, FakeConnections(FakeFileGateway(tree))).state.value
        assertEquals(FolderPhase.Ready, live.phase)
        assertTrue(shelf.folders.isNotEmpty(), "the live listing is kept")
        val away = folder(shelf, FakeConnections(gateway = null)).state.value
        assertEquals(FolderPhase.Ready, away.phase)
        assertEquals(live.entries, away.entries)
        assertTrue(away.offline, "the status slot says the server is away")
    }

    @Test
    fun `CF-31 a failed read with kept rows keeps the rows and says so, and a later live read replaces them`() = runTest {
        val shelf = Shelf()
        shelf.folders["c:s1|"] = KeptFolder.of(tree.getValue(""))
        val gateway = FakeFileGateway(emptyMap())
        val model = folder(shelf, FakeConnections(gateway))
        assertEquals(FolderPhase.Ready, model.state.value.phase)
        assertEquals(listOf("lib", "README.md"), model.state.value.entries.map { it.name })
        assertTrue(model.state.value.offline)
        gateway.nodes = mapOf("" to listOf(FileNode("src", "src", true)))
        model.retry()
        assertEquals(listOf("src"), model.state.value.entries.map { it.name })
        assertFalse(model.state.value.offline)
        assertEquals(listOf("src"), shelf.folders.getValue("c:s1|").entries.map { it.name })
    }

    @Test
    fun `CF-34 with nothing kept a folder still loads as before`() = runTest {
        val model = folder(Shelf(), FakeConnections(gateway = null))
        assertEquals(FolderPhase.NotConnected, model.state.value.phase)
    }

    private fun file(scope: TestScope, shelf: Shelf, connections: FakeConnections, path: String = "README.md") =
        FileViewModel(
            FilesRoute.File(conversation, path),
            connections,
            FakeMentions(),
            WifiShare(null, Clock.System, scope.backgroundScope),
            emptySet(),
            emptySet(),
            StackNavigator(ChatRoute(conversation)),
            AppCoroutineScope(scope.backgroundScope.coroutineContext),
            emptySet(),
            NoFileLinks(),
            emptySet(),
            lastKnownPorts = setOf(shelf),
        )

    private val readme = FileContent("# Hello", "text", null, null)

    @Test
    fun `CF-32 a text file opens on its kept text with the live frame's body, even with the server away`() = runTest {
        val shelf = Shelf()
        val live = file(this, shelf, FakeConnections(FakeFileGateway(contents = mapOf("README.md" to readme)))).state.value
        assertEquals(FilePhase.Ready, live.phase)
        assertTrue(shelf.texts.isNotEmpty())
        val away = file(this, shelf, FakeConnections(gateway = null)).state.value
        assertEquals(FilePhase.Ready, away.phase)
        assertIs<FileBody.Markdown>(away.body)
        assertEquals(live.body, away.body)
        assertTrue(away.offline)
    }

    @Test
    fun `CF-33 a failed read with kept text keeps the text, and binary files are never kept`() = runTest {
        val shelf = Shelf()
        shelf.texts["c:s1|README.md"] = KeptFile("# Hello")
        val kept = file(this, shelf, FakeConnections(FakeFileGateway(contents = emptyMap()))).state.value
        assertEquals(FilePhase.Ready, kept.phase)
        assertTrue(kept.offline)
        val png = FileContent(kotlin.io.encoding.Base64.encode(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)), "binary", "base64", null)
        val binary = Shelf()
        file(this, binary, FakeConnections(FakeFileGateway(contents = mapOf("logo.png" to png))), "logo.png")
        assertTrue(binary.texts.isEmpty())
    }
}
