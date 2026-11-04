package io.github.eslamasabry.oc.feature.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.engine.files.FileBody
import io.github.eslamasabry.oc.feature.files.presentation.FileActions
import io.github.eslamasabry.oc.feature.files.presentation.FileNotice
import io.github.eslamasabry.oc.feature.files.presentation.FilePhase
import io.github.eslamasabry.oc.feature.files.presentation.FileUi
import io.github.eslamasabry.oc.feature.files.presentation.FolderActions
import io.github.eslamasabry.oc.feature.files.presentation.FolderPhase
import io.github.eslamasabry.oc.feature.files.presentation.FolderUi
import io.github.eslamasabry.oc.feature.files.presentation.ShareSheetUi
import io.github.eslamasabry.oc.feature.files.ui.FileScreen
import io.github.eslamasabry.oc.feature.files.ui.FolderScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** The real Files screens by visible text (P12-FILES). */
@OptIn(ExperimentalTestApi::class)
class FilesScreensTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private class FolderCalls : FolderActions {
        val opened = mutableListOf<FileNode>()
        var toggles = 0
        var retries = 0

        override fun back() {}

        override fun retry() {
            retries++
        }

        override fun open(node: FileNode) {
            opened += node
        }

        override fun toggleHidden() {
            toggles++
        }

        override fun search(query: String) {}
    }

    private class FileCalls : FileActions {
        val calls = mutableListOf<String>()

        override fun openLink(url: String) {
            calls += "openLink"
        }

        override fun confirmLink() {
            calls += "confirmLink"
        }

        override fun cancelLink() {
            calls += "cancelLink"
        }

        override fun back() {
            calls += "back"
        }

        override fun retry() {
            calls += "retry"
        }

        override fun ask() {
            calls += "ask"
        }

        override fun openWith() {
            calls += "openWith"
        }

        override fun save() {
            calls += "save"
        }

        override fun shareOnWifi() {
            calls += "share"
        }

        override fun stopSharing() {
            calls += "stop"
        }

        override fun closeShare() {
            calls += "close"
        }

        override fun dismissNotice() {}

        override fun pdfPage(index: Int) {}
    }

    @Composable
    private fun Themed(locale: String = "en", content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = locale,
            stillFrame = true,
        ) {
            Box(Modifier.width(412.dp).height(860.dp)) { content() }
        }
    }

    private val root =
        FolderUi(
            path = "",
            phase = FolderPhase.Ready,
            entries = persistentListOf(FileNode("lib", "lib", true), FileNode("README.md", "README.md", false)),
            hiddenCount = 2,
        )

    @Test
    fun `B-NAV-026 the project root lists its folders and files as rows that open`() = runComposeUiTest {
        val calls = FolderCalls()
        setContent { Themed { FolderScreen(root, calls) } }
        onNodeWithText("Project").assertExists()
        // The kit breaks file names before the extension (a zero-width space); the row's spoken name stays exact.
        onNodeWithContentDescription("README.md").performClick()
        onNodeWithText("lib", substring = true).performClick()
        assertEquals(listOf("README.md", "lib"), calls.opened.map { it.name })
    }

    @Test
    fun `B-NAV-026 Show hidden files is a visible action, and an all-hidden folder says so`() = runComposeUiTest {
        val calls = FolderCalls()
        setContent {
            Themed {
                FolderScreen(FolderUi(path = "secret", phase = FolderPhase.Ready, hiddenCount = 1), calls)
            }
        }
        onNodeWithText("This folder has only hidden files and folders.").assertExists()
        onNodeWithTag("files-show-hidden").performClick()
        assertEquals(1, calls.toggles)
    }

    @Test
    fun `B-NAV-026 a disconnected server is said plainly with Try again`() = runComposeUiTest {
        val calls = FolderCalls()
        setContent { Themed { FolderScreen(FolderUi(path = "", phase = FolderPhase.NotConnected), calls) } }
        onNodeWithText(
                "The server for this conversation isn't connected. Go back to the conversation and try again."
            )
            .assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(1, calls.retries)
    }

    @Test
    fun `B-NAV-026 the root reads Project in Arabic too`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Themed("ar") { FolderScreen(root, FolderCalls()) } }
        onNodeWithText("المشروع").assertExists()
    }

    private val readme =
        FileUi(
            path = "docs/README.md",
            phase = FilePhase.Ready,
            body = FileBody.Markdown("Hello files", truncated = false),
            canShare = true,
            canOpenWith = true,
        )

    @Test
    fun `B-CHAT-087 the viewer offers Ask about this file, Share on Wi-Fi and Open with as visible actions`() =
        runComposeUiTest {
            val calls = FileCalls()
            setContent { Themed { FileScreen(readme, calls) } }
            onNodeWithText("Hello files", substring = true).assertExists()
            onNodeWithText("Ask about this file").performClick()
            onNodeWithText("Share on Wi-Fi").performClick()
            onNodeWithText("Open with").performClick()
            assertEquals(listOf("ask", "share", "openWith"), calls.calls)
        }

    @Test
    fun `B-MEDIA-034 a running share shows who can download, the count and Stop sharing`() = runComposeUiTest {
        val calls = FileCalls()
        val running = ShareSheetUi.Running("http://phone.example:4000/t/README.md", "README.md", 1, 14 * 60_000L)
        setContent { Themed { FileScreen(readme.copy(share = running), calls) } }
        onNodeWithText("Anyone on this Wi-Fi with the link can download this file.").assertExists()
        onNodeWithText("Downloaded 1 time").assertExists()
        onNodeWithText("Stops sharing in 14:00").assertExists()
        onNodeWithText("Stop sharing").performClick()
        assertEquals(listOf("stop"), calls.calls)
    }

    @Test
    fun `B-MEDIA-034 a secret-looking file is refused in plain words with no way to share it`() = runComposeUiTest {
        val refused = ShareSheetUi.Refused(Failure.Media(MediaProblem.WifiSharePrivateFile))
        setContent { Themed { FileScreen(readme.copy(path = ".env", share = refused), FileCalls()) } }
        onNodeWithText("This file can't be shared because it may hold keys or passwords.").assertExists()
        onNodeWithText("Stop sharing").assertDoesNotExist()
        onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `B-MEDIA-001 a file that is not text says what it is and offers Open with`() = runComposeUiTest {
        val calls = FileCalls()
        val binary = readme.copy(path = "app.bin", body = FileBody.Binary("application/octet-stream", 2048), sizeBytes = 2048)
        setContent { Themed { FileScreen(binary, calls) } }
        onNodeWithText("Can't show this file").assertExists()
    }

    @Test
    fun `B-MEDIA-001 Save is a visible action and a saved file says so`() = runComposeUiTest {
        val calls = FileCalls()
        setContent { Themed { FileScreen(readme.copy(canSave = true, notice = FileNotice.Saved), calls) } }
        onNodeWithText("README.md saved to your device.").assertExists()
        onNodeWithText("Save").performClick()
        assertEquals(listOf("save"), calls.calls)
    }

    @Test
    fun `B-MEDIA-001 a file longer than the viewer shows says only part of it is shown`() = runComposeUiTest {
        val long = readme.copy(path = "big.txt", body = FileBody.Text("first line", truncated = true))
        setContent { Themed { FileScreen(long, FileCalls()) } }
        onNodeWithText("Showing part of this file").assertExists()
    }
}
