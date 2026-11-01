package io.github.eslamasabry.oc.ui.kit.content.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Accessible-mode transcript rules (plan 06 §7.7; DESKTOP-WEB-CROSS-30, Dart `terminal_accessibility_test`). */
class TerminalTranscriptTest {
    @Test
    fun `DESKTOP-WEB-CROSS-30 accessible transcript strips terminal and transport controls`() {
        val transcript = TerminalTranscript()
        listOf(
            "\u001b]0;private",
            " title\u0007Hello \u001b[31mworld\u001b[0m\r",
            "\n",
            "\u0000{\"cursor\":87}",
            "\u001bPprivate",
            " transport\u001b\\Done\u0001\n",
            "\u001b(",
            "BISO ",
            "\u001b)0text\n",
            "{\"cursor\":99}\n",
        ).forEach(transcript::add)

        assertEquals("Hello world\nDone\nISO text\n{\"cursor\":99}\n", transcript.text)
        assertFalse("private" in transcript.text)
        assertFalse('\u001b' in transcript.text)
        assertFalse('\u0000' in transcript.text)
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 a lone carriage return becomes a line break and CR LF one break`() {
        val transcript = TerminalTranscript()
        transcript.add("a\r\nb\rc")
        assertEquals("a\nb\nc", transcript.text)
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 C1 controls and OSC with ST are dropped`() {
        val transcript = TerminalTranscript()
        transcript.add("x\u009b31my\u009dtitle\u009cz\u001b]8;;https://e.invalid\u001b\\w")
        assertEquals("xyzw", transcript.text)
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 the transcript keeps only the newest 100000 characters`() {
        val transcript = TerminalTranscript()
        transcript.add("a".repeat(60_000))
        transcript.add("b".repeat(60_000))
        assertEquals(TerminalTranscript.MAX_CHARS, transcript.text.length)
        assertTrue(transcript.text.endsWith("b".repeat(60_000)))
        assertTrue(transcript.text.startsWith("a"))
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 display text is stripped and capped even when the host sent raw text`() {
        val raw = "\u001b[1mbold\u001b[0m\r\n" + "z".repeat(TerminalTranscript.MAX_CHARS)
        val shown = TerminalTranscript.forDisplay(raw)
        assertEquals(TerminalTranscript.MAX_CHARS, shown.length)
        assertFalse('\u001b' in shown)
        assertEquals("plain\ntext", TerminalTranscript.forDisplay("plain\ntext"))
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 lines split for the list, trailing break gives no empty row`() {
        assertEquals(listOf("one", "two"), TerminalTranscript.lines("one\ntwo\n"))
        assertEquals(emptyList(), TerminalTranscript.lines(""))
        assertEquals(listOf("one", "", "three"), TerminalTranscript.lines("one\n\nthree"))
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 sending writes the value and a carriage return`() {
        assertEquals("pwd\r", TerminalTranscript.sendLine("pwd"))
        assertEquals("\r", TerminalTranscript.sendLine(""))
    }
}

class KitTerminalRulesTest {
    @Test
    fun `DESKTOP-WEB-CROSS-30 accessible mode follows the screen reader until the person chooses`() {
        assertFalse(KitTerminalRules.accessibleOn(choice = null, screenReader = false, hasGrid = true))
        assertTrue(KitTerminalRules.accessibleOn(choice = null, screenReader = true, hasGrid = true))
        assertFalse(KitTerminalRules.accessibleOn(choice = false, screenReader = true, hasGrid = true))
        assertTrue(KitTerminalRules.accessibleOn(choice = true, screenReader = false, hasGrid = true))
        assertTrue(KitTerminalRules.accessibleOn(choice = false, screenReader = false, hasGrid = false))
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 the announcement is the newest three lines`() {
        assertEquals("b\nc\nd", KitTerminalRules.announcement(listOf("a", "b", "c", "d")))
        assertEquals("", KitTerminalRules.announcement(emptyList()))
    }
}
