package io.github.eslamasabry.oc.engine.project.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerminalPasteTest {
    @Test
    fun `B-NAV-040 a copied command line is pasted whole, line ends and all`() {
        val command = "git log --oneline \\\n  --graph | head -n 20\n"
        assertEquals(command, TerminalPaste.accept(command))
    }

    @Test
    fun `B-NAV-040 an empty clipboard pastes nothing`() {
        assertNull(TerminalPaste.accept(null))
        assertNull(TerminalPaste.accept(""))
    }

    @Test
    fun `B-NAV-040 a paste is never cut short - over the bound it is refused whole`() {
        assertEquals(TerminalPaste.MAX_BYTES, TerminalPaste.accept("a".repeat(TerminalPaste.MAX_BYTES))?.length)
        assertNull(TerminalPaste.accept("a".repeat(TerminalPaste.MAX_BYTES + 1)))
        // Counted in UTF-8 bytes, as the shell receives it: 21,846 three-byte characters are 65,538 bytes.
        val arabic = "ع".repeat(TerminalPaste.MAX_BYTES / 2)
        assertEquals(arabic, TerminalPaste.accept(arabic))
        assertNull(TerminalPaste.accept("€".repeat(TerminalPaste.MAX_BYTES / 3 + 1)))
    }
}
