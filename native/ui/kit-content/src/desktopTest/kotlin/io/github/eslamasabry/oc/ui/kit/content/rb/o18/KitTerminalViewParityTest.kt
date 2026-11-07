package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalColor
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalScreen
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalTheme
import io.github.eslamasabry.oc.ui.kit.content.terminal.palette
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalViewDefaults
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalAnsi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `part-kit_terminal_view`: the constants of lib/ui/kit/kit_terminal_view.dart and the xterm behaviour the live
 * goldens depend on (third_party/xterm at FLUTTER_REF): where the sample lands, how a resize reflows it, the 256-colour
 * palette, and the output form's ANSI colours.
 */
class KitTerminalViewParityTest {
    private val sample =
        "\u001b[32mdev@build-server\u001b[0m:\u001b[34m~/app\u001b[0m\$ ls --color\r\n" +
            "\u001b[34mlib\u001b[0m  \u001b[34mtest\u001b[0m  \u001b[34mtool\u001b[0m  " +
            "\u001b[32mbuild.sh\u001b[0m  pubspec.yaml\r\n" +
            "\u001b[32mdev@build-server\u001b[0m:\u001b[34m~/app\u001b[0m\$ flutter test\r\n" +
            "\u001b[31merror: test/app_test.dart:12:3: Expected a value\u001b[0m\r\n" +
            "\u001b[32m00:06 +42: All tests passed!\u001b[0m\r\n" +
            "\u001b[32mdev@build-server\u001b[0m:\u001b[34m~/app\u001b[0m\$ "

    private fun KitTerminalScreen.texts() = bufferLines.map { it.text }

    @Test
    fun `the constants are Flutter's`() {
        assertEquals(14f, KitTerminalViewDefaults.CALM_FONT_SIZE) // kit_terminal_view.dart:139
        assertEquals(1.5f, KitTerminalViewDefaults.CALM_LINE_HEIGHT) // :140
        assertEquals(2000, KitTerminalViewDefaults.INLINE_LINE_CAP) // :183
        assertEquals(40, KitTerminalViewDefaults.TAIL_LINES) // :87
        assertEquals(.28f, KitTerminalViewDefaults.SELECTION_ALPHA) // :186
        assertEquals(0.5f, KitTerminalViewDefaults.FAINT_ALPHA) // xterm painter.dart:181
        val screen = KitTerminalScreen()
        assertEquals(80, screen.viewWidth) // xterm terminal.dart:116
        assertEquals(24, screen.viewHeight) // :118
        assertEquals(1000, screen.maxLines) // :74
    }

    @Test
    fun `the sample lands on six lines with the cursor after the last prompt`() {
        val screen = KitTerminalScreen().apply { write(sample) }
        assertEquals(24, screen.bufferLines.size)
        assertEquals(
            listOf(
                "dev@build-server:~/app$ ls --color",
                "lib  test  tool  build.sh  pubspec.yaml",
                "dev@build-server:~/app$ flutter test",
                "error: test/app_test.dart:12:3: Expected a value",
                "00:06 +42: All tests passed!",
                "dev@build-server:~/app$",
            ),
            screen.texts().take(6).map { it.trimEnd() },
        )
        assertEquals(24, screen.cursorX)
        assertEquals(5, screen.absoluteCursorY)
        val first = screen.bufferLines[0]
        assertEquals(KitTerminalColor.Palette(2), first[0]!!.style.foreground)
        assertEquals(KitTerminalColor.Default, first[16]!!.style.foreground)
        assertEquals(KitTerminalColor.Palette(4), first[17]!!.style.foreground)
    }

    @Test
    fun `a narrower, taller window wraps the long line and scrolls one line, as xterm does at 360 dp`() {
        val screen = KitTerminalScreen().apply { write(sample) }
        screen.resize(44, 35)
        // 24 lines grow to 35, the error line takes two: one line goes past the screen.
        assertEquals(36, screen.bufferLines.size)
        assertEquals("error: test/app_test.dart:12:3: Expected a v", screen.texts()[3])
        assertEquals("alue", screen.texts()[4].trimEnd())
        assertTrue(screen.bufferLines[4].isWrapped)
        assertEquals(6, screen.absoluteCursorY)
        assertEquals("dev@build-server:~/app$", screen.texts()[6].trimEnd())
    }

    @Test
    fun `at 2x text the shrink drops lines below the cursor first, then reflows, and the cursor keeps its screen row`() {
        val screen = KitTerminalScreen().apply { write(sample) }
        screen.resize(25, 20)
        // 24 -> 20 rows pops four empty lines; six lines reflow into eleven rows.
        assertEquals(25, screen.bufferLines.size)
        assertEquals("dev@build-server:~/app$ f", screen.texts()[4])
        assertEquals("lutter test", screen.texts()[5].trimEnd())
        assertEquals(10, screen.absoluteCursorY)
        assertEquals("dev@build-server:~/app$", screen.texts()[10].trimEnd())
    }

    @Test
    fun `a wider window joins wrapped lines back`() {
        val screen = KitTerminalScreen().apply { write(sample) }
        screen.resize(25, 24)
        screen.resize(80, 24)
        assertEquals("error: test/app_test.dart:12:3: Expected a value", screen.texts().first { it.startsWith("error") }.trimEnd())
        assertFalse(screen.bufferLines.any { it.isWrapped })
    }

    @Test
    fun `cursor moves, erase and DECTCEM`() {
        val screen = KitTerminalScreen()
        screen.write("abc\u001b[2DX\u001b[K")
        assertEquals("aX", screen.texts()[0])
        screen.write("\u001b[?25l")
        assertFalse(screen.cursorVisible)
        screen.write("\u001b[2J\u001b[H")
        assertEquals("", screen.texts()[0])
        assertEquals(0, screen.cursorX)
    }

    @Test
    fun `an OSC 52 write reaches the listener with its arguments`() {
        val screen = KitTerminalScreen()
        var got: List<String>? = null
        screen.onOsc52 = { got = it }
        screen.write("\u001b]52;c;aGk=\u0007after")
        assertEquals(listOf("c", "aGk="), got)
        assertEquals("after", screen.texts()[0])
    }

    @Test
    fun `the output form keeps the ANSI colour index - yellow is codeString, not text2`() {
        val redact: (String) -> String = { it }
        val line = "\u001b[32m14 passed\u001b[0m, \u001b[33m1 skipped\u001b[0m, \u001b[31m1 failed\u001b[0m"
        assertTrue(TerminalAnsi.keepsColours(line, redact))
        val spans = TerminalAnsi.spans(line, redact)
        assertEquals(listOf("14 passed", ", ", "1 skipped", ", ", "1 failed"), spans.map { it.text })
        assertEquals(listOf(2, null, 3, null, 1), spans.map { it.color })
        val faint = TerminalAnsi.spans("\u001b[2mTest run took 6.1 s\u001b[0m", redact).single()
        assertTrue(faint.faint)
        val bright = TerminalAnsi.spans("\u001b[94mx", redact).single()
        assertTrue(bright.bright)
        assertEquals(4, bright.color)
        assertFalse(TerminalAnsi.keepsColours("plain", redact))
        assertFalse(TerminalAnsi.keepsColours("\u001b[1mBearer x", { "masked" }))
    }

    @Test
    fun `the 256-colour palette follows xterm`() {
        val screen = KitTerminalScreen()
        screen.write("\u001b[38;5;196mx\u001b[48;2;1;2;3my")
        assertEquals(KitTerminalColor.Palette(196), screen.bufferLines[0][0]!!.style.foreground)
        assertEquals(KitTerminalColor.Rgb(1, 2, 3), screen.bufferLines[0][1]!!.style.background)
        val normal = List(8) { Color(it / 10f, 0f, 0f) }
        val bright = List(8) { Color(0f, it / 10f, 0f) }
        val theme = KitTerminalTheme(Color.Red, Color.Red, Color.White, Color.Black, normal, bright)
        assertEquals(normal[3], theme.palette(3))
        assertEquals(bright[1], theme.palette(9))
        // xterm builds entry 15 from the theme's white, not bright white (palette_builder.dart:47-48).
        assertEquals(normal[7], theme.palette(15))
        assertEquals(Color(0f, 0f, 0f), theme.palette(16))
        assertEquals(Color(0f, 0f, 95 / 255f), theme.palette(17))
        assertEquals(Color(1f, 1f, 1f), theme.palette(231))
        assertEquals(Color(8 / 255f, 8 / 255f, 8 / 255f), theme.palette(232))
        assertEquals(Color(238 / 255f, 238 / 255f, 238 / 255f), theme.palette(255))
    }
}
