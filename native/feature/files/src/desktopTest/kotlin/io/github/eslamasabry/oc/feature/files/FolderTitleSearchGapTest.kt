package io.github.eslamasabry.oc.feature.files

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.feature.files.presentation.FolderActions
import io.github.eslamasabry.oc.feature.files.presentation.FolderPhase
import io.github.eslamasabry.oc.feature.files.presentation.FolderUi
import io.github.eslamasabry.oc.feature.files.ui.FolderScreen
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/** E20-S10 (owner's phone): the Files "Project" title sat almost touching the search field; now the kit's step apart. */
@OptIn(ExperimentalTestApi::class)
class FolderTitleSearchGapTest {
    private object NoActions : FolderActions {
        override fun back() {}

        override fun retry() {}

        override fun open(node: FileNode) {}

        override fun toggleSourceFirst() {}

        override fun toggleHidden() {}

        override fun search(query: String) {}
    }

    @Test
    fun `E20-S10 the project title and the search field are at least one kit step apart`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                FolderScreen(FolderUi(path = "", phase = FolderPhase.Ready), NoActions)
            }
        }
        waitForIdle()
        val bar = onNodeWithTag("kit-top-bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val field = onNodeWithTag("files-search", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val gap = (field.top - bar.bottom) / density.density
        assertTrue(gap >= Space.S2 - 0.5f, "title bar to search field: $gap dp")
    }
}
