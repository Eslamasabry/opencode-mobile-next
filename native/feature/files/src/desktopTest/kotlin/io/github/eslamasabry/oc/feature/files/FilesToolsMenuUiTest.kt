package io.github.eslamasabry.oc.feature.files

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.feature.files.presentation.FilesProjectTools
import io.github.eslamasabry.oc.feature.files.presentation.FolderActions
import io.github.eslamasabry.oc.feature.files.presentation.FolderPhase
import io.github.eslamasabry.oc.feature.files.presentation.FolderUi
import io.github.eslamasabry.oc.feature.files.ui.folderToolItems
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** Device walk 2026-11-08: the Files ⋯ menu leads with the project's tools, in Flutter's order and words. */
@OptIn(ExperimentalTestApi::class)
class FilesToolsMenuUiTest {
    private class Recorder : FolderActions {
        val calls = mutableListOf<String>()
        override fun back() = Unit
        override fun retry() = Unit
        override fun open(node: FileNode) = Unit
        override fun toggleSourceFirst() = Unit
        override fun toggleHidden() = Unit
        override fun search(query: String) = Unit
        override fun projectHealth() { calls += "health" }
        override fun worktrees() { calls += "worktrees" }
        override fun devServices() { calls += "services" }
        override fun switchProject() { calls += "switch" }
        override fun reviewChanges() { calls += "changes" }
    }

    @Test
    fun `WALK-1108-F1 the menu lists Project health, Worktrees, Development services and Switch project`() =
        runComposeUiTest {
        val actions = Recorder()
        var closed = 0
        var items: List<KitMenuItem> = emptyList()
        var none: List<KitMenuItem> = listOf()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                items =
                    folderToolItems(
                        FolderUi(
                            path = "",
                            phase = FolderPhase.Ready,
                            tools = FilesProjectTools(true, true, true, true),
                            inConversation = false,
                        ),
                        actions,
                    ) { closed++ }
                none = folderToolItems(FolderUi(path = "", phase = FolderPhase.Ready, inConversation = false), actions) {}
            }
        }
        waitForIdle()
        assertEquals(
            listOf("Project health", "Worktrees", "Development services", "Switch project"),
            items.map { it.label },
        )
        assertEquals(emptyList(), none)
        items.forEach { it.onSelect() }
        assertEquals(listOf("health", "worktrees", "services", "switch"), actions.calls)
        assertEquals(4, closed)
    }

    @Test
    fun `a conversation's menu leads with Changes and its count, and opens the whole review`() = runComposeUiTest {
        val actions = Recorder()
        var items: List<KitMenuItem> = emptyList()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                items = folderToolItems(FolderUi(path = "", phase = FolderPhase.Ready), actions) {}
            }
        }
        waitForIdle()
        assertEquals(listOf("Changes"), items.map { it.label }.map { it.substringBefore(" ·") })
        items.first().onSelect()
        assertEquals(listOf("changes"), actions.calls)
    }
}
