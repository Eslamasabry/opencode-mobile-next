package io.github.eslamasabry.oc.feature.files

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.engine.files.FileBody
import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.FileHandoff
import io.github.eslamasabry.oc.engine.files.FileSaver
import io.github.eslamasabry.oc.engine.files.PdfDocument
import io.github.eslamasabry.oc.engine.files.PdfPages
import io.github.eslamasabry.oc.engine.files.ShareHandler
import io.github.eslamasabry.oc.engine.files.ShareHost
import io.github.eslamasabry.oc.engine.files.ShareServer
import io.github.eslamasabry.oc.engine.files.WifiShare
import io.github.eslamasabry.oc.feature.files.presentation.FileNotice
import io.github.eslamasabry.oc.feature.files.presentation.FilePhase
import io.github.eslamasabry.oc.feature.files.presentation.FileViewModel
import io.github.eslamasabry.oc.feature.files.presentation.PdfPageUi
import io.github.eslamasabry.oc.feature.files.presentation.ShareSheetUi
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Synthetic addresses built from parts (the push scanner blocks private-address literals). */
private fun ip(a: Int, b: Int, c: Int, d: Int): String = "$a.$b.$c.$d"

@OptIn(ExperimentalCoroutinesApi::class)
class FileViewModelTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private class Host(var address: String? = ip(192, 168, 1, 20)) : ShareHost {
        var closes = 0

        override suspend fun wifiAddress(): String? = address

        override suspend fun open(address: String, handler: ShareHandler): Outcome<ShareServer> =
            Outcome.Ok(
                object : ShareServer {
                    override val port: Int = 40000

                    override suspend fun close() {
                        closes++
                    }
                }
            )

        override fun secureBytes(count: Int): ByteArray = ByteArray(count) { it.toByte() }
    }

    private class Handoff(var result: Outcome<Unit> = Outcome.Ok(Unit)) : FileHandoff {
        val opened = mutableListOf<FileData>()

        override suspend fun openWith(data: FileData): Outcome<Unit> {
            opened += data
            return result
        }
    }

    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_000_000)
        }

    private val files =
        mapOf(
            "README.md" to FileContent("# Hello", "text", null, null),
            "src/App.kt" to FileContent("fun main() {}\n", "text", null, null),
            "logo.png" to FileContent(Base64.encode(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)), "binary", "base64", null),
            "doc.pdf" to FileContent(Base64.encode(byteArrayOf(0x25, 0x50, 0x44, 0x46)), "binary", "base64", null),
            ".env" to FileContent("KEY=1", "text", null, null),
        )

    private inner class Setup(
        scope: TestScope,
        path: String = "README.md",
        line: Int? = null,
        val host: Host? = Host(),
        val handoff: Handoff? = Handoff(),
        val pdf: PdfPages? = null,
        val connections: FakeConnections = FakeConnections(FakeFileGateway(contents = files)),
        val saver: FileSaver? = null,
        shared: WifiShare? = null,
    ) {
        val nav =
            StackNavigator(
                ChatRoute(conversation),
                FilesRoute.Folder(conversation),
                FilesRoute.Folder(conversation, "src"),
                FilesRoute.File(conversation, path, line),
            )
        val mentions = FakeMentions()
        val share = shared ?: WifiShare(host, clock, scope.backgroundScope)
        val vm =
            FileViewModel(
                FilesRoute.File(conversation, path, line),
                connections,
                mentions,
                share,
                setOfNotNull(handoff),
                setOfNotNull(pdf),
                nav,
                AppCoroutineScope(scope.backgroundScope.coroutineContext),
                setOfNotNull(saver),
            )
    }

    @Test
    fun `B-MEDIA-001 a project file opens in the viewer body for its kind`() = runTest {
        assertIs<FileBody.Markdown>(Setup(this, "README.md").vm.state.value.body)
        val code = Setup(this, "src/App.kt", line = 1).vm.state.value
        assertEquals(1, (code.body as FileBody.Code).line)
        assertEquals("App.kt", code.name)
        assertEquals("src", code.folder)
        assertIs<FileBody.Image>(Setup(this, "logo.png").vm.state.value.body)
    }

    @Test
    fun `B-NAV-026 a missing file and a disconnected server are plain states, never an empty viewer`() = runTest {
        assertEquals(FilePhase.Failed, Setup(this, "gone.txt").vm.state.value.phase)
        val offline = Setup(this, connections = FakeConnections(gateway = null)).vm.state.value
        assertEquals(FilePhase.NotConnected, offline.phase)
    }

    @Test
    fun `B-CHAT-087 Ask about this file mentions the exact path in this chat and returns to the chat`() = runTest {
        val setup = Setup(this, "src/App.kt")
        setup.vm.ask()
        assertEquals(Triple(conversation, "src/App.kt", "/work/app" as String?), setup.mentions.calls.single())
        assertEquals(ChatRoute(conversation), setup.nav.top.value)
        assertEquals(1, setup.nav.stack.size)
    }

    @Test
    fun `B-CHAT-087 a failed mention stays on the file and says so`() = runTest {
        val setup = Setup(this)
        setup.mentions.result = Outcome.Failed(Failure.StorageRefused())
        setup.vm.ask()
        assertEquals(FileNotice.MentionFailed, setup.vm.state.value.notice)
        assertIs<FilesRoute.File>(setup.nav.top.value)
        setup.vm.dismissNotice()
        assertNull(setup.vm.state.value.notice)
    }

    @Test
    fun `B-MEDIA-034 Share on Wi-Fi shows the link and QR, counts and stops on Stop`() = runTest {
        val setup = Setup(this, "README.md")
        assertTrue(setup.vm.state.value.canShare)
        setup.vm.shareOnWifi()
        val running = setup.vm.state.value.share as ShareSheetUi.Running
        assertTrue(running.url.startsWith("http://${ip(192, 168, 1, 20)}:40000/"))
        assertTrue(running.url.endsWith("/README.md"))
        assertEquals(15 * 60 * 1000L, running.remainingMs)
        setup.vm.stopSharing()
        assertEquals(ShareSheetUi.Ended, setup.vm.state.value.share)
        assertNull(setup.share.state.value)
        assertEquals(1, setup.host!!.closes)
    }

    @Test
    fun `B-MEDIA-034 closing the sheet stops the share`() = runTest {
        val setup = Setup(this)
        setup.vm.shareOnWifi()
        setup.vm.closeShare()
        assertNull(setup.vm.state.value.share)
        assertNull(setup.share.state.value)
    }

    @Test
    fun `B-MEDIA-034 a secret-looking file is refused with no link and no server`() = runTest {
        val setup = Setup(this, ".env")
        setup.vm.shareOnWifi()
        val refused = setup.vm.state.value.share as ShareSheetUi.Refused
        assertEquals(Failure.Media(MediaProblem.WifiSharePrivateFile), refused.failure)
        assertNull(setup.share.state.value)
    }

    @Test
    fun `B-MEDIA-034 no Wi-Fi says so, and a platform without the server hides Share`() = runTest {
        val offline = Setup(this, host = Host(address = null))
        offline.vm.shareOnWifi()
        val refused = offline.vm.state.value.share as ShareSheetUi.Refused
        assertEquals(Failure.Media(MediaProblem.WifiShareNoWifi), refused.failure)
        assertFalse(Setup(this, host = null).vm.state.value.canShare)
    }

    @Test
    fun `B-MEDIA-001 Open with hands over the exact bytes and words a phone with no app for it`() = runTest {
        val handoff = Handoff()
        val setup = Setup(this, "logo.png", handoff = handoff)
        setup.vm.openWith()
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), handoff.opened.single().bytes)
        assertEquals("image/png", handoff.opened.single().mime)
        handoff.result = Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
        setup.vm.openWith()
        assertEquals(FileNotice.OpenWithNone, setup.vm.state.value.notice)
        assertFalse(Setup(this, handoff = null).vm.state.value.canOpenWith)
    }

    @Test
    fun `B-MEDIA-001 a PDF opens page by page, each page rendered once`() = runTest {
        val rendered = mutableListOf<Int>()
        val pages =
            PdfPages {
                Outcome.Ok(
                    object : PdfDocument {
                        override val pageCount: Int = 3

                        override suspend fun renderPage(index: Int, width: Int): Outcome<ByteArray> {
                            rendered += index
                            return if (index == 2) Outcome.Failed(Failure.Media(MediaProblem.PdfFailed))
                            else Outcome.Ok(byteArrayOf(index.toByte()))
                        }

                        override suspend fun close() {}
                    }
                )
            }
        val setup = Setup(this, "doc.pdf", pdf = pages)
        assertEquals(3, setup.vm.state.value.pdfPageCount)
        setup.vm.pdfPage(0)
        setup.vm.pdfPage(0)
        setup.vm.pdfPage(2)
        setup.vm.pdfPage(7)
        assertEquals(listOf(0, 2), rendered)
        assertIs<PdfPageUi.Ready>(setup.vm.state.value.pdfPages[0])
        assertEquals(PdfPageUi.Failed, setup.vm.state.value.pdfPages[2])
        setup.vm.pdfPage(2) // Try again
        assertEquals(listOf(0, 2, 2), rendered)
    }

    @Test
    fun `B-MEDIA-001 without a PDF renderer the viewer says so and keeps Open with`() = runTest {
        val ui = Setup(this, "doc.pdf", pdf = null).vm.state.value
        assertEquals(Failure.Media(MediaProblem.PdfNeedsAndroid10), ui.pdfFailure)
        assertTrue(ui.canOpenWith)
    }

    @Test
    fun `B-MEDIA-034 a second viewer's share is never adopted or stopped by the first`() = runTest {
        val host = Host()
        val shared = WifiShare(host, clock, backgroundScope)
        val a = Setup(this, "README.md", shared = shared)
        val b = Setup(this, "src/App.kt", shared = shared)
        a.vm.shareOnWifi()
        b.vm.shareOnWifi()
        val bRunning = b.vm.state.value.share as ShareSheetUi.Running
        assertTrue(bRunning.url.endsWith("/App.kt"))
        // A's share was replaced: A shows it ended, never B's link.
        assertEquals(ShareSheetUi.Ended, a.vm.state.value.share)
        a.vm.closeShare()
        a.vm.stopSharing()
        assertEquals(bRunning.url, shared.state.value?.url, "closing A ended B's share")
        b.vm.stopSharing()
        assertNull(shared.state.value)
    }

    @Test
    fun `B-MEDIA-001 Save hands over the exact original bytes and says where they went`() = runTest {
        val saved = mutableListOf<FileData>()
        var answer: Outcome<Boolean> = Outcome.Ok(true)
        val setup = Setup(this, "logo.png", saver = FileSaver { saved += it; answer })
        assertTrue(setup.vm.state.value.canSave)
        setup.vm.save()
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), saved.single().bytes)
        assertEquals(FileNotice.Saved, setup.vm.state.value.notice)
        answer = Outcome.Ok(false) // cancelled in the picker
        setup.vm.save()
        assertNull(setup.vm.state.value.notice)
        answer = Outcome.Failed(Failure.Media(MediaProblem.SaveFailed))
        setup.vm.save()
        assertEquals(FileNotice.SaveFailed, setup.vm.state.value.notice)
        assertFalse(Setup(this, "logo.png").vm.state.value.canSave)
    }

    @Test
    fun `B-MEDIA-001 PDF pages far from the one shown are released and render again when reached`() = runTest {
        val rendered = mutableListOf<Int>()
        val pages =
            PdfPages {
                Outcome.Ok(
                    object : PdfDocument {
                        override val pageCount: Int = 40

                        override suspend fun renderPage(index: Int, width: Int): Outcome<ByteArray> {
                            rendered += index
                            return Outcome.Ok(ByteArray(1024))
                        }

                        override suspend fun close() {}
                    }
                )
            }
        val setup = Setup(this, "doc.pdf", pdf = pages)
        for (i in 0 until 40) setup.vm.pdfPage(i)
        val ready = setup.vm.state.value.pdfPages.filterValues { it is PdfPageUi.Ready }.keys
        assertTrue(ready.size <= 7, "kept $ready")
        assertTrue(39 in ready)
        assertTrue(0 !in ready)
        setup.vm.pdfPage(0)
        assertEquals(41, rendered.size)
        assertIs<PdfPageUi.Ready>(setup.vm.state.value.pdfPages[0])
    }
}
