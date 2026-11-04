package io.github.eslamasabry.oc.feature.computer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.feature.computer.ui.ComputerScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** The computer page on the desktop JVM, found by the words a person reads. */
@OptIn(ExperimentalTestApi::class)
class ComputerScreenUiTest {
    private var previousLocale: Locale = Locale.getDefault()
    private val list = ImportList("/work/acme", persistentListOf(NEWER, OLDER), alreadyHere = 2)
    private val base = ComputerUi(name = "Studio Mac", version = "1.4.0", imports = ImportPhase.Ready(list))

    @BeforeTest
    fun english() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previousLocale)

    private class Recording : ComputerActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun askUpdate() { calls += "ask" }

        override fun cancelUpdate() { calls += "cancel" }

        override fun confirmUpdate() { calls += "confirm" }

        override fun reloadImports() { calls += "reload" }

        override fun import(key: String) { calls += "import:$key" }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            KitSheetHost {
                Box(Modifier.size(412.dp, 915.dp).background(LocalKitColors.current.ground)) { content() }
            }
        }
    }

    private fun ComposeUiTest.page(state: ComputerUi, actions: Recording = Recording()): Recording {
        setContent { Themed { ComputerScreen(state, actions) } }
        waitForIdle()
        return actions
    }

    private fun ComposeUiTest.shows(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `B-AGENT-167 the update row names the computer and its running version and asks before updating`() =
        runComposeUiTest {
            val actions = page(base)
            onNodeWithText("Update agents on Studio Mac").assertExists()
            onNodeWithText("Running 1.4.0. Installs the newest agent software.").assertExists()
            onNodeWithText("Update agents on Studio Mac").performClick()
            assertEquals(listOf("ask"), actions.calls)
        }

    @Test
    fun `B-AGENT-167 the confirmation says what happens and names the act`() = runComposeUiTest {
        val actions = page(base.copy(confirming = true))
        onNodeWithText("Update agents?").assertExists()
        onNodeWithText("This installs the newest agent software on Studio Mac and restarts it.").assertExists()
        onNodeWithText("Conversations on Studio Mac pause while it restarts.").assertExists()
        onAllNodesWithText("Update agents")[onAllNodesWithText("Update agents").fetchSemanticsNodes().size - 1].performClick()
        assertTrue("confirm" in actions.calls)
    }

    @Test
    fun `B-AGENT-167 a running update shows its phase and finished ones read plainly`() = runComposeUiTest {
        var state by mutableStateOf(base.copy(updating = true, step = UpdateStep.Downloading))
        setContent { Themed { ComputerScreen(state, Recording()) } }
        waitForIdle()
        assertTrue(shows("Downloading the newest version…"))
        state = base.copy(update = UpdateLine.Done("1.5.0"))
        waitForIdle()
        onNodeWithText("Updated to 1.5.0. The app reconnects by itself.").assertExists()
        state = base.copy(update = UpdateLine.Current)
        waitForIdle()
        onNodeWithText("Studio Mac is already up to date.").assertExists()
        state = base.copy(update = UpdateLine.Failed(null))
        waitForIdle()
        onNodeWithText("The update did not finish. Try again, or update it on that computer.").assertExists()
        state = base.copy(update = UpdateLine.Dropped)
        waitForIdle()
        onNodeWithText("The connection dropped during the update. The app is reconnecting.").assertExists()
    }

    @Test
    fun `B-AGENT-174 the import list shows each conversation and importing one is a visible row`() = runComposeUiTest {
        val actions = page(base)
        onNodeWithText("Import from Claude Code").performScrollTo().assertExists()
        onNodeWithText("Fix the login page").performScrollTo().assertExists()
        onNodeWithText("/work/acme · 2 hours ago").assertExists()
        onNodeWithText("Untitled conversation").performScrollTo().assertExists()
        onNodeWithText("2 more conversations are already here.").performScrollTo().assertExists()
        onNodeWithText("Fix the login page").performClick()
        assertEquals(listOf("import:${NEWER.key}"), actions.calls)
    }

    @Test
    fun `B-AGENT-174 empty and failed lists say so in plain words`() = runComposeUiTest {
        var state by mutableStateOf(
            base.copy(imports = ImportPhase.Ready(ImportList("/work/acme", persistentListOf(), 0))),
        )
        val actions = Recording()
        setContent { Themed { ComputerScreen(state, actions) } }
        waitForIdle()
        onNodeWithText("Nothing to import").performScrollTo().assertExists()
        state = base.copy(imports = ImportPhase.Failed(Failure.Network(NetworkWhy.Reset)))
        waitForIdle()
        onNodeWithText("Could not look").performScrollTo().assertExists()
        onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(listOf("reload"), actions.calls)
    }
}
