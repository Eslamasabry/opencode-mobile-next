package io.github.eslamasabry.oc.feature.terminal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalState
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalActions
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalDetails
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalDetailsUi
import io.github.eslamasabry.oc.feature.terminal.ui.TerminalScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parity audit 2026-11-08 row 464 (Flutter terminal_surface.dart `_details`). */
@OptIn(ExperimentalTestApi::class)
class TerminalDetailsTest {
    private val ended =
        TerminalProcess("t1", "Build", "npm", persistentListOf("run", "build"), "/work/app", running = false, pid = 4242, exitCode = 2)

    @Test
    fun `AUDIT-464 the details are state, command with arguments, folder, pid and exit code`() {
        val details = TerminalDetails.of(ended)
        assertFalse(details.running)
        assertEquals("Build", details.title)
        assertEquals("npm run build", details.command)
        assertEquals("/work/app", details.folder)
        assertEquals(4242, details.pid)
        assertEquals(2, details.exitCode)
    }

    @Test
    fun `AUDIT-464 a running terminal has no exit code and an empty folder is left out`() {
        val details = TerminalDetails.of(ended.copy(running = true, exitCode = 0, directory = ""))
        assertTrue(details.running)
        assertNull(details.exitCode)
        assertNull(details.folder)
    }

    @Test
    fun `AUDIT-464 Terminal details opens from the top bar with every value`() = runComposeUiTest {
        KitRedact.registerKnownSecret("sk-live-123456")
        val withToken = ended.copy(arguments = persistentListOf("run", "build", "--token", "sk-live-123456"))
        val actions =
            object : TerminalActions {
                override fun back() = Unit
                override fun refresh() = Unit
                override fun create() = Unit
                override fun select(id: String) = Unit
                override fun showList() = Unit
                override fun send(id: String, text: String) = Unit
                override fun rename(id: String, title: String) = Unit
                override fun resize(id: String, rows: Int, cols: Int) = Unit
                override fun remove(id: String) = Unit
                override fun details(): TerminalDetailsUi = TerminalDetails.of(withToken)
            }
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                TerminalScreen(
                    RemoteTerminalState(loading = false, terminals = persistentListOf(ended), selected = ended, connected = true),
                    actions,
                )
            }
        }
        // A wide window shows every top-bar action; a narrow one folds them under More.
        if (onAllNodesWithContentDescription("More").fetchSemanticsNodes().isNotEmpty()) onNodeWithContentDescription("More").performClick()
        onAllNodesWithText("Terminal details").onFirst().performClick()
        onNodeWithText("Build details").assertExists()
        onNodeWithText("State: Ended", substring = true).assertExists()
        onNodeWithText("npm run build", substring = true).assertExists()
        onNodeWithText("/work/app", substring = true).assertExists()
        onNodeWithText("4242", substring = true).assertExists()
        onNodeWithText("Exit code: 2", substring = true).assertExists()
        // A command line can carry a token: it is masked before it is drawn.
        onNodeWithText("sk-live-123456", substring = true).assertDoesNotExist()
        KitRedact.clearKnownSecrets()
    }
}
