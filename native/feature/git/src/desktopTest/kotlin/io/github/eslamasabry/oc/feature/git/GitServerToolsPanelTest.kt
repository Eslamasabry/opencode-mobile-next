package io.github.eslamasabry.oc.feature.git

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.git.servertools.GitServerToolsActions
import io.github.eslamasabry.oc.feature.git.servertools.GitServerToolsPanel
import io.github.eslamasabry.oc.feature.git.servertools.GitServerToolsUi
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Server project tools sheet: a folder that is not a repository is a normal state, not an error. */
@OptIn(ExperimentalTestApi::class)
class GitServerToolsPanelTest {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest
    fun before() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun after() = Locale.setDefault(previous)

    private val actions =
        object : GitServerToolsActions {
            override fun show() {}
            override fun close() {}
            override fun initialize() {}
            override fun createWorktree(name: String) {}
            override fun selectShell(path: String) {}
            override fun readShells() {}
        }

    private val failure = "That did not work. Check the server and try again."

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: GitServerToolsUi) =
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(412.dp)) { GitServerToolsPanel(state, actions) }
            }
        }

    @Test
    fun `opening on a folder that is not a repository offers to initialize and shows no error`() = runComposeUiTest {
        show(GitServerToolsUi(directory = "/work/plain", gitInit = true, worktreeCreate = true, shellSettings = true, open = true))
        onNodeWithText("Initialize Git in this folder").assertExists()
        assertEquals(0, onAllNodesWithText(failure).fetchSemanticsNodes().size)
    }

    @Test
    fun `a shell list that could not be read says so inside the shell section, with Retry`() = runComposeUiTest {
        show(GitServerToolsUi(directory = "/work/plain", gitInit = true, shellSettings = true, open = true, shellsFailed = true))
        assertEquals(1, onAllNodesWithText(failure).fetchSemanticsNodes().size)
        onNodeWithText("Initialize Git in this folder").assertExists()
    }

    @Test
    fun `an action that did not work shows the failure above the tools`() = runComposeUiTest {
        show(GitServerToolsUi(directory = "/work/plain", gitInit = true, open = true, failed = true))
        assertEquals(1, onAllNodesWithText(failure).fetchSemanticsNodes().size)
    }
}
