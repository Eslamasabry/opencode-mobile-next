package io.github.eslamasabry.oc.ui.kit.content.rb.o27

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.ContentFakePlatform
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalLinkFlow
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalLinks
import io.github.eslamasabry.oc.ui.kit.content.terminal.LocalTerminalLinkHost
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalLinkHost
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalLinkReview
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalLinks
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalOsc52
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalRow
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** part-kit_terminal_links: every constant and rule of `lib/ui/kit/kit_terminal_links.dart` at 8302de537. */
@OptIn(ExperimentalTestApi::class)
class KitTerminalLinksParityTest {
    private val url = "https://example.invalid/device?code=ABCD-1234"

    @Test
    fun `osc52 caps a write at 64 KiB and never answers a read`() {
        assertEquals(64 * 1024, TerminalOsc52.MAX_BYTES) // dart :20
        assertNull(TerminalOsc52.parse(listOf("c")))
        assertNull(TerminalOsc52.parse(listOf("c", "?")))
        assertNull(TerminalOsc52.parse(listOf("c", "")))
        assertNull(TerminalOsc52.parse(listOf("c", "not base64!")))
        assertEquals("hi", TerminalOsc52.parse(listOf("c", "aGk=")))
        // `;` inside the payload is rejoined (dart :32).
        assertNull(TerminalOsc52.parse(listOf("c", "aG", "k=")))
        val big = "A".repeat((64 * 1024 * 4 / 3) + 9)
        assertNull(TerminalOsc52.parse(listOf("c", big)))
    }

    @Test
    fun `a link wrapped over full rows opens whole, up to 60 rows`() {
        val rows = TerminalLinks.layout("open $url now", 10)
        for (y in rows.indices.filter { rows[it].text.isNotBlank() }.take(4).drop(1))
            assertEquals(url, TerminalLinks.urlAt(rows, 10, 2, y))
        // A row that does not reach the last column ends the line.
        val short = listOf(TerminalRow("go https://a.example/x"), TerminalRow("next"))
        assertEquals("https://a.example/x", TerminalLinks.urlAt(short, 40, 8, 0))
        assertNull(TerminalLinks.urlAt(short, 40, 0, 1))
        assertNull(TerminalLinks.urlAt(short, 40, 0, 5))
    }

    @Test
    fun `trailing punctuation is not part of the link and a closing bracket stays only when opened`() {
        fun at(text: String) = TerminalLinks.urlAt(listOf(TerminalRow(text)), 80, 0, 0)
        assertEquals("https://a.example/x", at("https://a.example/x.,;:!?'\"]}"))
        assertEquals("https://a.example/x", at("https://a.example/x)"))
        assertEquals("https://a.example/w(x)", at("https://a.example/w(x)"))
        assertEquals("http://a.example", at("http://a.example<b>"))
    }

    @Test
    fun `the flow opens the sheet first, then Open goes through the host and Copy writes the whole address`() {
        val flow = KitTerminalLinkFlow()
        val asked = mutableListOf<String>()
        var opened = 0
        val host = TerminalLinkHost { asked += it; TerminalLinkReview("example.invalid", insecure = false) { opened++ } }
        flow.tap(url)
        assertEquals(url, flow.sheet)
        assertEquals(emptyList(), asked)
        flow.open(host)
        assertNull(flow.sheet)
        assertEquals(listOf(url), asked)
        flow.confirm()
        assertEquals(1, opened)
        assertNull(flow.review)

        val copied = mutableListOf<String>()
        flow.tap(url)
        flow.copy { copied += it }
        assertNull(flow.sheet)
        assertEquals(listOf(url), copied)
        assertEquals(url, flow.shown)

        flow.tap(url)
        flow.dismiss()
        assertNull(flow.sheet)
        flow.open(host)
        assertEquals(1, asked.size)
    }

    @Test
    fun `a link button opens the sheet with the whole address, Open link and Copy link`() = runComposeUiTest {
        val asked = mutableListOf<String>()
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), ContentFakePlatform(), localeTag = "en", stillFrame = true) {
                CompositionLocalProvider(LocalTerminalLinkHost provides TerminalLinkHost { asked += it; null }) {
                    KitSheetHost { KitTerminalLinks("sign in at $url\r\n") }
                }
            }
        }
        onNodeWithText("Open link · example.invalid").performClick()
        onNodeWithText("Link in the terminal").assertExists()
        onNodeWithTag("terminal-link-url").assertExists()
        onNodeWithText("Copy link").assertExists()
        assertEquals(emptyList(), asked)
        onNodeWithText("Open link").performClick()
        waitForIdle()
        assertEquals(listOf(url), asked)
    }
}
