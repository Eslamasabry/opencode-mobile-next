package io.github.eslamasabry.oc.ui.kit.content.terminal

import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dart `kit_terminal_view_test` › key bar 3, selectedText, 11, 12 and the command. */
class TerminalTextTest {
    private val redactor = Redactor()
    private val redact: (String) -> String = redactor::redact

    private fun drawn(line: String) = TerminalText.line(line, redact).joinToString("") { it.text }

    @Test
    fun `B-XPLAT-011 Ctrl-C and Ctrl-D reach the terminal as 0x03 and 0x04`() {
        val sent =
            listOf(
                TerminalText.keyBytes(TerminalBarKey.Interrupt),
                TerminalText.keyBytes(TerminalBarKey.EndOfInput),
                TerminalText.keyBytes(TerminalBarKey.Interrupt, alt = true),
            )
        assertEquals(listOf("\u0003", "\u0004", "\u001b\u0003"), sent)
    }

    @Test
    fun `B-XPLAT-011 the live view's selected text is only the selection`() {
        assertEquals("world", TerminalText.selectedText("hello world", 6, 11))
        assertEquals("world", TerminalText.selectedText("hello world", 11, 6))
        assertEquals("", TerminalText.selectedText("hello world", 3, 3))
    }

    @Test
    fun `B-XPLAT-011 a value coloured apart from its name is still masked`() {
        val jq = "\u001b[34;1m\"password\"\u001b[0m:\u001b[0;32m\"hunter2\"\u001b[0m"
        assertFalse("hunter2" in drawn(jq))
        assertEquals(redactor.redact("\"password\":\"hunter2\""), drawn(jq))
        assertEquals(redactor.redact("Bearer TOKEN123"), drawn("Bearer \u001b[1mTOKEN123\u001b[0m"))
        assertEquals(redactor.redact("Bearer TOKEN123"), drawn("\u001b[1mBearer\u001b[0m TOKEN123"))
        assertFalse("TOKEN123" in drawn("Bearer \u001b[1mTOKEN123\u001b[0m"))
        // A line with no secret keeps its colours; ANSI red is mapped to text1.
        assertEquals(TerminalRole.Text1, TerminalText.line("\u001b[31mred\u001b[0m", redact).first().role)
    }

    @Test
    fun `B-XPLAT-011 tints - errors text1 semibold, warnings not amber, passes success, test progress parts`() {
        fun styleOf(line: String) = TerminalText.line(line, redact).first()
        styleOf("error: something broke").let {
            assertEquals(TerminalRole.Text1, it.role)
            assertEquals(600, it.weight)
        }
        assertEquals(TerminalRole.Text2, styleOf("warning: deprecated call").role)
        assertEquals(TerminalRole.Text1, styleOf("\u001b[31mred\u001b[0m").role)
        assertEquals(TerminalRole.Success, styleOf("All tests passed!").role)
        assertNull(styleOf("Found 0 errors").role)
        val progress = TerminalText.line("00:07 +6 ~1 -2: x", redact)
        fun part(text: String) = progress.first { it.text == text }
        assertEquals(TerminalRole.Success, part("+6").role)
        assertEquals(TerminalRole.Text2, part(" ~1").role)
        assertEquals(TerminalRole.Text1, part(" -2").role)
        assertEquals(600, part(" -2").weight)
    }

    @Test
    fun `B-XPLAT-011 the command line parses program, folder, flags, strings and operators`() {
        val spans = TerminalText.command("FOO=1 /usr/bin/git log --oneline \"a b\" | head")
        fun roleOf(text: String) = spans.first { it.text == text }.role
        assertEquals(TerminalRole.Text3, roleOf("$ "))
        assertEquals(TerminalRole.Text3, roleOf("FOO=1"))
        assertEquals(TerminalRole.Text3, roleOf("/usr/bin/"))
        assertEquals(TerminalRole.Accent, roleOf("git"))
        assertEquals(TerminalRole.CodeType, roleOf("--oneline"))
        assertEquals(TerminalRole.CodeString, roleOf("\"a b\""))
        assertEquals(TerminalRole.Text3, roleOf("|"))
        assertEquals(TerminalRole.Accent, roleOf("head"))
        assertEquals("x", TerminalText.strip("\u001b[1;31mx\u001b[0m"))
        assertTrue(spans.joinToString("") { it.text }.endsWith("| head"))
    }
}
