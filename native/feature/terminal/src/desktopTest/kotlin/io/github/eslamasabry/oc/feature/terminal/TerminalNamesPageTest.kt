package io.github.eslamasabry.oc.feature.terminal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalState
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalActions
import io.github.eslamasabry.oc.feature.terminal.ui.TerminalScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** Device pass 2026-11-13: the chat's terminal page names a terminal by the same rule as the project page. */
@OptIn(ExperimentalTestApi::class)
class TerminalNamesPageTest {
    private fun process(id: String, title: String) =
        TerminalProcess(id, title, "bash", persistentListOf(), "/work", true, 7, null)

    private object Quiet : TerminalActions {
        override fun back() = Unit
        override fun refresh() = Unit
        override fun create() = Unit
        override fun select(id: String) = Unit
        override fun showList() = Unit
        override fun send(id: String, text: String) = Unit
        override fun rename(id: String, title: String) = Unit
        override fun resize(id: String, rows: Int, cols: Int) = Unit
        override fun remove(id: String) = Unit
    }

    private fun open(all: List<TerminalProcess>, selected: Int) =
        RemoteTerminalState(
            loading = false,
            terminals = persistentListOf(*all.toTypedArray()),
            selected = all[selected],
            connected = true,
        )

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: RemoteTerminalState) =
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                TerminalScreen(state, Quiet)
            }
        }

    private fun SemanticsNodeInteractionCollection.count() = fetchSemanticsNodes().size

    @Test
    fun `B-TERM-NAME-1 one unnamed terminal is called Terminal`() = runComposeUiTest {
        show(open(listOf(process("a", "Terminal 0Ome")), 0))
        onNodeWithContentDescription("Actions for Terminal").performClick()
        onNodeWithText("Rename Terminal").assertExists()
        assertEquals(0, onAllNodesWithText("0Ome", substring = true).count())
    }

    @Test
    fun `B-TERM-NAME-2 the second terminal is called Terminal 2 in the bar and the menu`() = runComposeUiTest {
        show(open(listOf(process("a", "Terminal 0Ome"), process("b", "Terminal BYbZ")), 1))
        assertTrue(onAllNodesWithText("Terminal 2").count() >= 1)
        onNodeWithContentDescription("Actions for Terminal 2").performClick()
        onNodeWithText("Rename Terminal 2").assertExists()
        onNodeWithText("Paste into Terminal 2").assertExists()
        assertEquals(0, onAllNodesWithText("BYbZ", substring = true).count())
        assertEquals(0, onAllNodesWithText("0Ome", substring = true).count())
    }

    @Test
    fun `B-TERM-NAME-3 a renamed terminal shows its own name in the bar and the Rename label`() = runComposeUiTest {
        show(open(listOf(process("a", "Terminal 0Ome"), process("b", "Build")), 1))
        assertTrue(onAllNodesWithText("Build").count() >= 1)
        onNodeWithContentDescription("Actions for Build").performClick()
        onNodeWithText("Rename Build").assertExists()
    }

    @Test
    fun `B-TERM-NAME-4 the list names every terminal by the same rule`() = runComposeUiTest {
        val all = listOf(process("a", "Terminal 0Ome"), process("b", "Build"), process("c", "Terminal BYbZ"))
        show(open(all, 0).copy(selected = null))
        assertTrue(onAllNodesWithText("Terminal").count() >= 1)
        onNodeWithText("Build").assertExists()
        onNodeWithText("Terminal 3").assertExists()
        assertEquals(0, onAllNodesWithText("0Ome", substring = true).count())
        assertEquals(0, onAllNodesWithText("BYbZ", substring = true).count())
    }
}
