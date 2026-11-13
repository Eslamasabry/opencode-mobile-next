package io.github.eslamasabry.oc.feature.terminal

import androidx.compose.ui.test.ExperimentalTestApi
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
import kotlinx.collections.immutable.persistentListOf

/** The terminals list says which shell still runs and how an ended one ended, and offers New terminal in the list. */
@OptIn(ExperimentalTestApi::class)
class TerminalListStateTest {
    private class Recording : TerminalActions {
        var created = 0

        override fun back() = Unit
        override fun refresh() = Unit
        override fun create() {
            created++
        }
        override fun select(id: String) = Unit
        override fun showList() = Unit
        override fun send(id: String, text: String) = Unit
        override fun rename(id: String, title: String) = Unit
        override fun resize(id: String, rows: Int, cols: Int) = Unit
        override fun remove(id: String) = Unit
    }

    @Test
    fun `B-TERM-LIST-1 the list shows running and ended terminals and a New terminal row`() = runComposeUiTest {
        val actions = Recording()
        val state =
            RemoteTerminalState(
                loading = false,
                terminals =
                    persistentListOf(
                        TerminalProcess("a", "Build", "bash", persistentListOf(), "/work", true, 7, null),
                        TerminalProcess("b", "Old", "bash", persistentListOf(), "/work", false, 8, 2),
                    ),
            )
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                TerminalScreen(state, actions)
            }
        }
        onNodeWithText("Running · /work").assertExists()
        onNodeWithText("Ended · code 2 · /work").assertExists()
        onNodeWithText("New terminal").performClick()
        assertEquals(1, actions.created)
    }
}
