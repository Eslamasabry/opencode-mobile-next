package io.github.eslamasabry.oc.ui.kit.content.terminal

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** OSC 52 clipboard writes and links that wrap over several terminal rows (Dart `kit_terminal_links_test`). */
class TerminalLinksTest {
    private fun b64(s: String) = Base64.Default.encode(s.encodeToByteArray())

    private val url =
        "https://claude.com/cai/oauth/authorize?code=true&client_id=abc-123&" +
            "response_type=code&redirect_uri=https%3A%2F%2Fexample.com%2Fcallback"

    @Test
    fun `B-XPLAT-009 a set-clipboard write decodes`() {
        assertEquals("héllo $url", TerminalOsc52.parse(listOf("c", b64("héllo $url"))))
    }

    @Test
    fun `B-XPLAT-009 a query is never answered`() {
        assertNull(TerminalOsc52.parse(listOf("c", "?")))
    }

    @Test
    fun `B-XPLAT-009 an oversize payload is ignored`() {
        assertNull(TerminalOsc52.parse(listOf("c", b64("a".repeat(TerminalOsc52.MAX_BYTES + 1)))))
        assertNotNull(TerminalOsc52.parse(listOf("c", b64("a".repeat(TerminalOsc52.MAX_BYTES)))))
    }

    @Test
    fun `B-XPLAT-009 garbage and empty payloads are ignored`() {
        assertNull(TerminalOsc52.parse(listOf("c", "!!!not base64!!!")))
        assertNull(TerminalOsc52.parse(listOf("c", "")))
        assertNull(TerminalOsc52.parse(listOf("c")))
    }

    @Test
    fun `B-XPLAT-010 a soft-wrapped link is one link from any of its rows`() {
        val rows = TerminalLinks.layout("Use the url below:\r\n$url\r\n", columns = 40)
        for (y in 1..3) assertEquals(url, TerminalLinks.urlAt(rows, 40, x = 3, y = y))
        assertNull(TerminalLinks.urlAt(rows, 40, x = 3, y = 0))
    }

    @Test
    fun `B-XPLAT-010 a link the program wrapped itself with newlines is joined`() {
        val hard = url.chunked(40).joinToString("\r\n") + "\r\n"
        val rows = TerminalLinks.layout(hard, columns = 40).map { it.copy(wrapped = false) }
        assertEquals(url, TerminalLinks.urlAt(rows, 40, x = 1, y = 2))
    }

    @Test
    fun `B-XPLAT-010 trailing punctuation is not part of the link`() {
        val rows = TerminalLinks.layout("see https://a.example/x). ok\r\n", columns = 40)
        assertEquals("https://a.example/x", TerminalLinks.urlAt(rows, 40, x = 8, y = 0))
        assertNull(TerminalLinks.urlAt(rows, 40, x = 0, y = 0))
        // A closing bracket the link opened stays.
        val wiki = TerminalLinks.layout("https://a.example/w(x).", columns = 40)
        assertEquals("https://a.example/w(x)", TerminalLinks.urlAt(wiki, 40, x = 2, y = 0))
    }
}
