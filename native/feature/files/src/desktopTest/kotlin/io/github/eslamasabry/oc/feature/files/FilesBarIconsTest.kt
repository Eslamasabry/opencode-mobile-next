package io.github.eslamasabry.oc.feature.files

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.feature.files.presentation.FolderActions
import io.github.eslamasabry.oc.feature.files.presentation.FolderPhase
import io.github.eslamasabry.oc.feature.files.presentation.FolderUi
import io.github.eslamasabry.oc.feature.files.ui.folderBarActions
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-097: the Files bar shows icon buttons whose words are the TalkBack label, never words cut to "Ter…". */
@OptIn(ExperimentalTestApi::class)
class FilesBarIconsTest {
    private object NoActions : FolderActions {
        override fun back() {}

        override fun retry() {}

        override fun open(node: FileNode) {}

        override fun toggleSourceFirst() {}

        override fun toggleHidden() {}

        override fun search(query: String) {}
    }

    @Test
    fun `B-097 every Files bar action is an icon button with its words as the label`() = runComposeUiTest {
        var bar: List<KitLineAction> = emptyList()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                bar = folderBarActions(FolderUi(path = "", phase = FolderPhase.Ready, canTerminal = true), NoActions) {}
            }
        }
        waitForIdle()
        assertEquals(listOf("Terminal", "Refresh", "View"), bar.map { it.label })
        // A phone bar draws an action without a glyph as words cut to fit ("Ter…", "Ref…").
        assertEquals(emptyList(), bar.filter { it.icon == null }.map { it.label })
    }
}
