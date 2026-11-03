package io.github.eslamasabry.oc.engine.project.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun write(text: String): TerminalScreen = TerminalScreen(cols = 40, rows = 5).apply { write(text) }

private fun TerminalScreen.underlined(x: Int): Boolean = lines[0].style(x).underline

class TerminalSgrTest {
    @Test
    fun `B-LINUX-251 plain 4 underlines and 24 ends it`() {
        val t = write("\u001b[4mab\u001b[24mcd")
        assertTrue(t.underlined(0))
        assertFalse(t.underlined(2))
    }

    @Test
    fun `B-LINUX-251 4 colon 0 ends an underline and is not a black background`() {
        val t = write("\u001b[4:1mab\u001b[4:0mcd")
        assertTrue(t.underlined(1))
        assertFalse(t.underlined(2))
        assertEquals(TerminalColor.Default, t.lines[0].style(2).background)
    }

    @Test
    fun `B-LINUX-251 4 colon 3 is an underline not a yellow background`() {
        val t = write("\u001b[4:3mab\u001b[0mcd")
        assertTrue(t.underlined(0))
        assertEquals(TerminalColor.Default, t.lines[0].style(0).background)
        assertFalse(t.underlined(2))
    }

    @Test
    fun `B-LINUX-251 underline colour is consumed and does not leak into other styles`() {
        val t = write("\u001b[4:3;58:2::255:0:0mab\u001b[59;24mcd\u001b[58;5;196mef")
        assertTrue(t.underlined(0))
        assertFalse(t.underlined(2))
        assertEquals(t.lines[0].style(2).foreground, t.lines[0].style(4).foreground)
        assertEquals(t.lines[0].style(2), t.lines[0].style(4))
        assertFalse(t.underlined(4))
    }

    @Test
    fun `B-LINUX-251 colon true colour and 256 colour set the colour`() {
        val a = write("\u001b[38:2::10:20:30mx\u001b[38:2:40:50:60my\u001b[48:5:99mz")
        val line = a.lines[0]
        assertEquals(TerminalColor.Rgb(10, 20, 30), line.style(0).foreground)
        assertEquals(0x0a141e, (line.style(0).foreground as TerminalColor.Rgb).packed)
        assertEquals(0x28323c, (line.style(1).foreground as TerminalColor.Rgb).packed)
        assertEquals(TerminalColor.Palette(99), line.style(2).background)
        assertFalse(a.underlined(0))
        // Semicolon forms still work.
        val b = write("\u001b[38;2;1;2;3;48;5;7mq\u001b[31;42mr\u001b[39;49ms")
        assertEquals(TerminalColor.Rgb(1, 2, 3), b.lines[0].style(0).foreground)
        assertEquals(TerminalColor.Palette(7), b.lines[0].style(0).background)
        assertEquals(TerminalColor.Named(1), b.lines[0].style(1).foreground)
        assertEquals(TerminalColor.Named(2), b.lines[0].style(1).background)
        assertEquals(TerminalStyle.Plain, b.lines[0].style(2))
    }

    @Test
    fun `B-LINUX-251 a terminal reset RIS ends an underline left on`() {
        val t = write("\u001b[4mab\u001bcxy")
        assertFalse(t.underlined(0))
        assertEquals("xy", t.lines[0].text(0, 2))
    }

    @Test
    fun `B-LINUX-251 a sequence split across writes still applies`() {
        val t = TerminalScreen(cols = 40, rows = 5)
        t.write("\u001b[4")
        t.write(":0;4ma")
        assertTrue(t.underlined(0))
    }
}
