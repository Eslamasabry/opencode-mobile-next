package io.github.eslamasabry.oc.engine.project.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalKeyBarTest {
    @Test
    fun `B-LINUX-224 Ctrl turns letters into control bytes`() {
        assertEquals("\u0003", terminalModifiedText("c", ctrl = true))
        assertEquals("\u0003", terminalModifiedText("C", ctrl = true))
        assertEquals("\u0004", terminalModifiedText("d", ctrl = true))
        assertEquals("\u0012", terminalModifiedText("r", ctrl = true))
        assertEquals("\u001a", terminalModifiedText("z", ctrl = true))
    }

    @Test
    fun `B-LINUX-224 Ctrl on the symbols that have a control form`() {
        assertEquals("\u001b", terminalModifiedText("[", ctrl = true))
        assertEquals("\u001c", terminalModifiedText("\\", ctrl = true))
        assertEquals("\u001f", terminalModifiedText("_", ctrl = true))
        assertEquals("\u0000", terminalModifiedText(" ", ctrl = true))
        assertEquals("\u007f", terminalModifiedText("?", ctrl = true))
    }

    @Test
    fun `B-LINUX-224 text without a control form passes unchanged`() {
        assertEquals("5", terminalModifiedText("5", ctrl = true))
        assertEquals("é", terminalModifiedText("é", ctrl = true))
        assertEquals("\r", terminalModifiedText("\r", ctrl = true))
        assertEquals("🙂x", terminalModifiedText("🙂x", ctrl = true))
    }

    @Test
    fun `B-LINUX-224 Alt puts Esc first and only the first character is modified`() {
        assertEquals("\u001bb", terminalModifiedText("b", alt = true))
        assertEquals("\u0003d", terminalModifiedText("cd", ctrl = true))
        assertEquals("\u001b\u0018", terminalModifiedText("x", ctrl = true, alt = true))
        assertEquals("ls", terminalModifiedText("ls"))
    }

    @Test
    fun `B-LINUX-224 sticky Ctrl applies to the next text once then releases`() {
        val keys = TerminalKeyBarController()
        keys.toggle(TerminalBarKey.Ctrl)
        assertTrue(keys.ctrl)
        assertEquals("\u0003", keys.apply("c"))
        assertFalse(keys.ctrl)
        assertEquals("c", keys.apply("c"))
    }

    @Test
    fun `B-LINUX-224 a second tap releases the latch`() {
        val keys = TerminalKeyBarController()
        keys.toggle(TerminalBarKey.Ctrl)
        keys.toggle(TerminalBarKey.Ctrl)
        assertEquals("c", keys.apply("c"))
    }

    @Test
    fun `B-LINUX-224 arrows follow the cursor-key mode programs ask for`() {
        val screen = TerminalScreen()
        val sent = mutableListOf<String>()
        sent += terminalBarKeyText(TerminalBarKey.Up, applicationCursorKeys = screen.cursorKeysMode)
        // vim and htop switch to application cursor keys.
        screen.write("\u001b[?1h")
        sent += terminalBarKeyText(TerminalBarKey.Up, applicationCursorKeys = screen.cursorKeysMode)
        assertEquals(listOf("\u001b[A", "\u001bOA"), sent)
        screen.write("\u001b[?1l")
        assertFalse(screen.cursorKeysMode)
    }

    @Test
    fun `B-LINUX-224 Esc Tab symbols and page keys`() {
        val sent =
            listOf(
                TerminalBarKey.Esc,
                TerminalBarKey.Tab,
                TerminalBarKey.Pipe,
                TerminalBarKey.Tilde,
                TerminalBarKey.PageUp,
            ).map { terminalBarKeyText(it) }
        assertEquals(listOf("\u001b", "\t", "|", "~", "\u001b[5~"), sent)
        assertEquals("\u001b[6~", terminalBarKeyText(TerminalBarKey.PageDown))
        assertEquals("\u0003", terminalBarKeyText(TerminalBarKey.Interrupt))
        assertEquals("\u0004", terminalBarKeyText(TerminalBarKey.EndOfInput))
    }

    @Test
    fun `B-LINUX-224 Ctrl with an arrow sends the modified sequence`() {
        assertEquals("\u001b[1;5D", terminalBarKeyText(TerminalBarKey.Left, ctrl = true))
        // A modified arrow ignores the application cursor mode.
        assertEquals("\u001b[1;5D", terminalBarKeyText(TerminalBarKey.Left, ctrl = true, applicationCursorKeys = true))
    }

    @Test
    fun `B-LINUX-224 a latched Ctrl modifies the next bar key only`() {
        val keys = TerminalKeyBarController()
        assertEquals("", keys.press(TerminalBarKey.Ctrl))
        assertTrue(keys.ctrl)
        assertEquals("\u001b[1;5D", keys.press(TerminalBarKey.Left))
        assertEquals("\u001b[D", keys.press(TerminalBarKey.Left))
        assertFalse(keys.ctrl)
    }

    @Test
    fun `B-LINUX-224 the bar has every key the spec lists`() {
        assertEquals(
            listOf("Esc", "/", "-", "|", "Home", "↑", "End", "PgUp"),
            TerminalBarKey.rows[0].map { it.face },
        )
        assertEquals(
            listOf("Tab", "Ctrl", "Alt", "~", "←", "↓", "→", "PgDn"),
            TerminalBarKey.rows[1].map { it.face },
        )
        assertEquals(listOf(TerminalBarKey.Interrupt, TerminalBarKey.EndOfInput), TerminalBarKey.extraRow)
    }
}
