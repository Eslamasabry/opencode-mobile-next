package io.github.eslamasabry.oc.feature.files

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.FindMatch
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.WorkspaceSymbol
import io.github.eslamasabry.oc.engine.files.FileBody
import io.github.eslamasabry.oc.feature.files.presentation.FileActions
import io.github.eslamasabry.oc.feature.files.presentation.FileNotice
import io.github.eslamasabry.oc.feature.files.presentation.FilePhase
import io.github.eslamasabry.oc.feature.files.presentation.FileSearchSurface
import io.github.eslamasabry.oc.feature.files.presentation.FileUi
import io.github.eslamasabry.oc.feature.files.presentation.FilesProjectTools
import io.github.eslamasabry.oc.feature.files.presentation.FilesStorageBlock
import io.github.eslamasabry.oc.feature.files.presentation.FolderActions
import io.github.eslamasabry.oc.feature.files.presentation.FolderPhase
import io.github.eslamasabry.oc.feature.files.presentation.FolderUi
import io.github.eslamasabry.oc.feature.files.presentation.ShareSheetUi
import io.github.eslamasabry.oc.feature.files.ui.FileScreen
import io.github.eslamasabry.oc.feature.files.ui.FolderScreen
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** Audit capture only: every Files screen state the models allow, six variants each. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "files"

    private class FolderCalls : FolderActions {
        override fun back() = Unit
        override fun retry() = Unit
        override fun open(node: FileNode) = Unit
        override fun toggleSourceFirst() = Unit
        override fun toggleHidden() = Unit
        override fun search(query: String) = Unit
    }

    private class FileCalls : FileActions {
        override fun openLink(url: String) = Unit
        override fun confirmLink() = Unit
        override fun cancelLink() = Unit
        override fun back() = Unit
        override fun retry() = Unit
        override fun ask() = Unit
        override fun openWith() = Unit
        override fun save() = Unit
        override fun shareOnWifi() = Unit
        override fun stopSharing() = Unit
        override fun closeShare() = Unit
        override fun dismissNotice() = Unit
        override fun pdfPage(index: Int) = Unit
    }

    private val entries = persistentListOf(
        FileNode("lib", "lib", true),
        FileNode("test", "test", true),
        FileNode("docs", "docs", true),
        FileNode("README.md", "README.md", false),
        FileNode("build.gradle.kts", "build.gradle.kts", false),
        FileNode("pubspec.yaml", "pubspec.yaml", false),
    )

    private val longName = "a_remarkably_long_generated_file_name_for_the_quarterly_reconciliation_report_final_v2_copy.kt"

    private val code = """
package io.github.eslamasabry.oc.auth

/** Where the app sends a person after sign-in. */
class LoginRedirect(private val session: SessionStore) {
    fun remember(route: String) {
        if (route.startsWith("/login")) return
        target.value = route
    }
}
""".trimIndent()

    private val readme = FileUi(
        path = "docs/README.md",
        phase = FilePhase.Ready,
        body = FileBody.Markdown("# Hello files\n\nThis project **builds** with one command.\n\n- first\n- second", truncated = false),
        canShare = true,
        canOpenWith = true,
        canSave = true,
        canAttach = true,
    )

    @Test
    fun folder() = Audit.guard("${m}__folder__build") {
        val ready = FolderUi(path = "", phase = FolderPhase.Ready, entries = entries, hiddenCount = 2, projectName = "my-app", canTerminal = true)
        Audit.shoot(m, "folder", "populated") { FolderScreen(ready, FolderCalls()) }
        Audit.shoot(m, "folder", "tools-available") {
            FolderScreen(ready.copy(tools = FilesProjectTools(health = true, worktrees = true, services = true, switchProject = true)), FolderCalls())
        }
        Audit.shoot(m, "folder", "loading") { FolderScreen(FolderUi(path = ""), FolderCalls()) }
        Audit.shoot(m, "folder", "empty") { FolderScreen(FolderUi(path = "empty", phase = FolderPhase.Ready), FolderCalls()) }
        Audit.shoot(m, "folder", "only-hidden") { FolderScreen(FolderUi(path = "secret", phase = FolderPhase.Ready, hiddenCount = 3), FolderCalls()) }
        Audit.shoot(m, "folder", "failed") {
            FolderScreen(FolderUi(path = "", phase = FolderPhase.Failed, failure = Failure.Network()), FolderCalls())
        }
        Audit.shoot(m, "folder", "timed-out") {
            FolderScreen(FolderUi(path = "", phase = FolderPhase.Failed, failure = Failure.TimedOut()), FolderCalls())
        }
        Audit.shoot(m, "folder", "not-connected") { FolderScreen(FolderUi(path = "", phase = FolderPhase.NotConnected), FolderCalls()) }
        Audit.shoot(m, "folder", "unavailable") { FolderScreen(FolderUi(path = "", phase = FolderPhase.Unavailable), FolderCalls()) }
        Audit.shoot(m, "folder", "subfolder-long-names") {
            FolderScreen(
                FolderUi(
                    path = "src/main/kotlin/io/github/some/deeply/nested/package/with/long/path",
                    phase = FolderPhase.Ready,
                    entries = persistentListOf(FileNode(longName, "x/$longName", false), FileNode("a-very-long-folder-name-that-keeps-going-and-going", "x/y", true)),
                ),
                FolderCalls(),
            )
        }
        Audit.shoot(m, "folder", "source-first") { FolderScreen(ready.copy(sourceFirst = true, canOrder = true, showHidden = true), FolderCalls()) }
    }

    @Test
    fun folderChanges() = Audit.guard("${m}__folder-changes__build") {
        val ready = FolderUi(path = "lib", phase = FolderPhase.Ready, entries = entries, projectName = "my-app")
        Audit.shoot(m, "folder", "with-changes") {
            FolderScreen(
                ready.copy(
                    changes = persistentMapOf(
                        "README.md" to VersionControlFile("README.md", "modified", 4, 1),
                        "pubspec.yaml" to VersionControlFile("pubspec.yaml", "added", 12, 0),
                        "build.gradle.kts" to VersionControlFile("build.gradle.kts", "deleted", 0, 9),
                    ),
                ),
                FolderCalls(),
            )
        }
        Audit.shoot(m, "folder", "changes-failed") { FolderScreen(ready.copy(changesFailure = Failure.Network()), FolderCalls()) }
        Audit.shoot(m, "folder", "storage-permission") {
            FolderScreen(FolderUi(path = "", phase = FolderPhase.Ready, storageBlock = FilesStorageBlock.AppPermission), FolderCalls())
        }
        Audit.shoot(m, "folder", "storage-selected-folder") {
            FolderScreen(FolderUi(path = "", phase = FolderPhase.Ready, storageBlock = FilesStorageBlock.SelectedFolder), FolderCalls())
        }
    }

    @Test
    fun folderActionsMenu() = Audit.guard("${m}__folder-actions__build") {
        val node = FileNode("main.dart", "lib/main.dart", false)
        Audit.shoot(m, "folder-actions", "file-menu") {
            FolderScreen(
                FolderUi(
                    path = "lib",
                    phase = FolderPhase.Ready,
                    entries = persistentListOf(node),
                    actionsFor = node,
                    canAttach = true,
                    changes = persistentMapOf(node.path to VersionControlFile(node.path, "modified", 1, 0)),
                ),
                FolderCalls(),
            )
        }
        val dir = FileNode("lib", "lib", true)
        Audit.shoot(m, "folder-actions", "folder-menu") {
            FolderScreen(FolderUi(path = "", phase = FolderPhase.Ready, entries = entries, actionsFor = dir), FolderCalls())
        }
    }

    @Test
    fun folderSearch() = Audit.guard("${m}__folder-search__build") {
        val base = FolderUi(path = "", phase = FolderPhase.Ready, entries = entries, query = "read")
        Audit.shoot(m, "folder-search", "files-results") {
            FolderScreen(base.copy(results = persistentListOf(FileNode("README.md", "README.md", false), FileNode("readme_old.md", "docs/readme_old.md", false))), FolderCalls())
        }
        Audit.shoot(m, "folder-search", "searching") { FolderScreen(base.copy(searching = true), FolderCalls()) }
        Audit.shoot(m, "folder-search", "no-results") { FolderScreen(base.copy(query = "zzzzzz"), FolderCalls()) }
        Audit.shoot(m, "folder-search", "failed") { FolderScreen(base.copy(searchFailure = Failure.Network()), FolderCalls()) }
        Audit.shoot(m, "folder-search", "text-matches") {
            FolderScreen(
                base.copy(
                    surface = FileSearchSurface.Text,
                    canSearchText = true,
                    canSearchSymbols = true,
                    textMatches = persistentListOf(
                        FindMatch("lib/main.dart", 12, "final reader = Reader();"),
                        FindMatch("lib/src/long.dart", 480, "// read the whole file before deciding what to do with the very long line that keeps going"),
                    ),
                ),
                FolderCalls(),
            )
        }
        Audit.shoot(m, "folder-search", "symbols") {
            FolderScreen(
                base.copy(
                    surface = FileSearchSurface.Symbols,
                    canSearchText = true,
                    canSearchSymbols = true,
                    symbols = persistentListOf(WorkspaceSymbol("Reader", 5, "lib/reader.dart", 3, 1), WorkspaceSymbol("readAll", 6, "lib/reader.dart", 30, 3)),
                ),
                FolderCalls(),
            )
        }
        Audit.shoot(m, "folder-search", "results-limited") {
            FolderScreen(base.copy(results = persistentListOf(FileNode("README.md", "README.md", false)), resultsLimited = true), FolderCalls())
        }
    }

    @Test
    fun file() = Audit.guard("${m}__file__build") {
        Audit.shoot(m, "file", "markdown") { FileScreen(readme, FileCalls()) }
        Audit.shoot(m, "file", "code") {
            FileScreen(readme.copy(path = "src/main/kotlin/auth/LoginRedirect.kt", body = FileBody.Code(code, "kotlin", truncated = false, line = null), canChooseWrap = true), FileCalls())
        }
        Audit.shoot(m, "file", "code-wrapped-line") {
            FileScreen(readme.copy(path = "src/Login.kt", body = FileBody.Code(code, "kotlin", truncated = false, line = 5), canChooseWrap = true, wrapCode = true), FileCalls())
        }
        Audit.shoot(m, "file", "plain-text") {
            FileScreen(readme.copy(path = "notes.txt", body = FileBody.Text("First line\nSecond line\nThird line", truncated = false)), FileCalls())
        }
        Audit.shoot(m, "file", "truncated") {
            FileScreen(readme.copy(path = "big.txt", body = FileBody.Text("first line", truncated = true)), FileCalls())
        }
        Audit.shoot(m, "file", "csv") {
            FileScreen(
                readme.copy(
                    path = "data.csv",
                    body = FileBody.Delimited(
                        rows = persistentListOf(persistentListOf("name", "count"), persistentListOf("apples", "3"), persistentListOf("pears", "12")),
                        original = "name,count\napples,3\npears,12",
                        truncated = false,
                    ),
                ),
                FileCalls(),
            )
        }
        Audit.shoot(m, "file", "binary") {
            FileScreen(readme.copy(path = "app.bin", body = FileBody.Binary("application/octet-stream", 2048), sizeBytes = 2048), FileCalls())
        }
        Audit.shoot(m, "file", "long-name") {
            FileScreen(readme.copy(path = "some/very/deep/folder/structure/$longName", body = FileBody.Text("x", truncated = false)), FileCalls())
        }
    }

    @Test
    fun fileStates() = Audit.guard("${m}__file-states__build") {
        Audit.shoot(m, "file", "loading") { FileScreen(FileUi(path = "docs/README.md"), FileCalls()) }
        Audit.shoot(m, "file", "failed") { FileScreen(FileUi(path = "docs/README.md", phase = FilePhase.Failed, failure = Failure.Network()), FileCalls()) }
        Audit.shoot(m, "file", "not-connected") { FileScreen(FileUi(path = "docs/README.md", phase = FilePhase.NotConnected), FileCalls()) }
        Audit.shoot(m, "file", "unavailable") { FileScreen(FileUi(path = "docs/README.md", phase = FilePhase.Unavailable), FileCalls()) }
        Audit.shoot(m, "file", "saved-notice") { FileScreen(readme.copy(notice = FileNotice.Saved), FileCalls()) }
        Audit.shoot(m, "file", "save-failed-notice") { FileScreen(readme.copy(notice = FileNotice.SaveFailed), FileCalls()) }
        Audit.shoot(m, "file", "link-confirm") {
            FileScreen(readme.copy(confirmLinkHost = "example.com", linkUrl = "https://example.com/page", linkHost = "example.com"), FileCalls())
        }
        Audit.shoot(m, "file", "link-refused") { FileScreen(readme.copy(notice = FileNotice.LinkRefused), FileCalls()) }
    }

    @Test
    fun fileShare() = Audit.guard("${m}__file-share__build") {
        Audit.shoot(m, "file-share", "starting") { FileScreen(readme.copy(share = ShareSheetUi.Starting), FileCalls()) }
        Audit.shoot(m, "file-share", "running") {
            FileScreen(readme.copy(share = ShareSheetUi.Running("http://phone.example:4000/t/README.md", "README.md", 1, 14 * 60_000L)), FileCalls())
        }
        Audit.shoot(m, "file-share", "refused-secret") {
            FileScreen(readme.copy(path = ".env", share = ShareSheetUi.Refused(Failure.Media(MediaProblem.WifiSharePrivateFile))), FileCalls())
        }
        Audit.shoot(m, "file-share", "ended") { FileScreen(readme.copy(share = ShareSheetUi.Ended), FileCalls()) }
    }
}
