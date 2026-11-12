package io.github.eslamasabry.oc.feature.terminal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalState
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalActions
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalLine
import io.github.eslamasabry.oc.feature.terminal.presentation.terminalKeyText
import io.github.eslamasabry.oc.feature.terminal.presentation.terminalLine
import io.github.eslamasabry.oc.feature.terminal.ui.TerminalScreen
import io.github.eslamasabry.oc.feature.terminal.ui.TerminalTags
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalBarKey
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf

/**
 * Device walk 2026-11-08, Terminal: the line said "disconnected" while the shell was live, the bar's buttons were cut
 * off at phone width, and there was no key bar. Flutter terminal_surface.dart is the reference.
 */
@OptIn(ExperimentalTestApi::class)
class ServerTerminalWalkTest {
    private val shell = TerminalProcess(
        "t1",
        "Shell",
        "bash",
        persistentListOf(),
        "/work",
        running = true,
        pid = 7,
        exitCode = null,
    )

    private class Sent : TerminalActions {
        val texts = mutableListOf<String>()
        val selected = mutableListOf<String>()
        override fun back() = Unit
        override fun refresh() = Unit
        override fun create() = Unit
        override fun select(id: String) {
            selected += id
        }
        override fun showList() = Unit
        override fun send(id: String, text: String) {
            texts += text
        }
        override fun rename(id: String, title: String) = Unit
        override fun resize(id: String, rows: Int, cols: Int) = Unit
        override fun remove(id: String) = Unit
    }

    private fun live() = RemoteTerminalState(
        loading = false,
        terminals = persistentListOf(shell),
        selected = shell,
        connected = true,
    )

    @Test
    fun `WALK-1108-T1 a connected shell has no status line and never says disconnected`() = runComposeUiTest {
        assertEquals(TerminalLine.None, terminalLine(live()))
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) { TerminalScreen(live(), Sent()) }
        }
        onAllNodesWithText("disconnected", substring = true, ignoreCase = true).assertCountEquals0()
        onAllNodesWithText("Connection closed").assertCountEquals0()
    }

    @Test
    fun `WALK-1108-T1 the line says why only while not connected, with Reconnect`() = runComposeUiTest {
        val closed = live().copy(connected = false)
        assertEquals(TerminalLine.Closed, terminalLine(closed))
        assertEquals(TerminalLine.Connecting, terminalLine(closed.copy(working = true)))
        assertEquals(
            TerminalLine.Ended("bash", 1),
            terminalLine(closed.copy(selected = shell.copy(running = false, exitCode = 1))),
        )
        val actions = Sent()
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) { TerminalScreen(closed, actions) }
        }
        onNodeWithText("Connection closed").assertExists()
        onAllNodesWithText("Reconnect").fetchSemanticsNodes().let { kotlin.test.assertTrue(it.isNotEmpty()) }
        onAllNodesWithText("Reconnect")[0].performClick()
        assertEquals(listOf("t1"), actions.selected)
    }

    @Test
    fun `WALK-1108-T2 the bar holds Copy and one menu, the rest is in the menu`() = runComposeUiTest {
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) { TerminalScreen(live(), Sent()) }
        }
        onNodeWithTag(TerminalTags.COPY, useUnmergedTree = true).assertExists()
        // The old bar's words are not on the bar any more.
        onAllNodesWithText("Terminal size").assertCountEquals0()
        onNodeWithContentDescription("Actions for Shell").performClick()
        for (label in listOf(
            "New terminal",
            "All terminals",
            "Paste into Shell",
            "Reconnect",
            "Rename Shell",
            "Terminal size",
            "Terminal details",
            "Stop Shell",
        ))
            onNodeWithText(label).assertExists()
    }

    @Test
    fun `sweep C P42 the page is titled Terminal, never the server's random id`() = runComposeUiTest {
        val named = shell.copy(title = "Terminal 0Ome")
        val state = live().copy(terminals = persistentListOf(named), selected = named)
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) { TerminalScreen(state, Sent()) }
        }
        onAllNodesWithText("Terminal 0Ome").assertCountEquals0()
        kotlin.test.assertTrue(onAllNodesWithText("Terminal").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `B-LINUX-224 the key bar sends arrows, Esc, Tab and a latched Ctrl to the shell`() = runComposeUiTest {
        val actions = Sent()
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) { TerminalScreen(live(), actions) }
        }
        onNodeWithTag(TerminalTags.KEYS, useUnmergedTree = true).assertExists()
        for (key in listOf(
            "Up arrow key",
            "Escape key",
            "Tab key",
            "Control",
            "Left arrow key",
            "Interrupt, Control C",
        ))
            onNodeWithContentDescription(key).performClick()
        assertEquals(listOf("\u001b[A", "\u001b", "\t", "\u001b[1;5D", "\u0003"), actions.texts)
    }

    @Test
    fun `B-XPLAT-011 key text for a server shell matches xterm`() {
        assertEquals("\u001b[B", terminalKeyText(KitTerminalBarKey.Down, ctrl = false, alt = false))
        assertEquals("\u001b[1;3C", terminalKeyText(KitTerminalBarKey.Right, ctrl = false, alt = true))
        assertEquals("\u001b[H", terminalKeyText(KitTerminalBarKey.Home, ctrl = false, alt = false))
        assertEquals("\u001b[F", terminalKeyText(KitTerminalBarKey.End, ctrl = false, alt = false))
        assertEquals("\u001b[5~", terminalKeyText(KitTerminalBarKey.PageUp, ctrl = false, alt = false))
        assertEquals("\u001b[6;5~", terminalKeyText(KitTerminalBarKey.PageDown, ctrl = true, alt = false))
        assertEquals("\u0004", terminalKeyText(KitTerminalBarKey.EndOfInput, ctrl = false, alt = false))
        assertEquals("\u001b|", terminalKeyText(KitTerminalBarKey.Pipe, ctrl = false, alt = true))
        assertNull(terminalKeyText(KitTerminalBarKey.Ctrl, ctrl = false, alt = false))
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() {
    assertEquals(0, fetchSemanticsNodes().size)
}
