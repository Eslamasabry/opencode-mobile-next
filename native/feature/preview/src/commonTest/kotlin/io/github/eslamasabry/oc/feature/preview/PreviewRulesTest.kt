package io.github.eslamasabry.oc.feature.preview

import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewTarget
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewBlock
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewEgress
import io.github.eslamasabry.oc.feature.preview.presentation.WebOrigin
import io.github.eslamasabry.oc.ui.routes.PreviewLink
import io.github.eslamasabry.oc.ui.routes.PreviewLinks
import io.github.eslamasabry.oc.ui.routes.PreviewPort
import io.github.eslamasabry.oc.ui.routes.PreviewRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from Flutter `test/dev_preview_test.dart`. */
class PreviewRulesTest {
    @Test
    fun `B-CHAT-025 local links with a port become previews keeping path query and https`() {
        assertEquals(PreviewLink(3000), PreviewLinks.parse("http://localhost:3000"))
        assertEquals(PreviewLink(5173, path = "/app", query = "x=1"), PreviewLinks.parse("http://127.0.0.1:5173/app?x=1"))
        assertEquals(PreviewLink(8443, https = true), PreviewLinks.parse("https://0.0.0.0:8443/"))
        assertEquals(PreviewLink(4000, path = "/a"), PreviewLinks.parse("http://[::1]:4000/a#top"))
        assertEquals(PreviewLink(3000), PreviewLinks.parse("  HTTP://LOCALHOST:3000  "))
    }

    @Test
    fun `B-CHAT-025 other links are left alone`() {
        listOf(
            "http://localhost",
            "http://example.com:3000",
            "ftp://localhost:21",
            "http://user:pw@localhost:3000",
            "http://localhost:0",
            "http://localhost:70000",
            "http://localhost:abc",
            "localhost:3000",
            "",
            null,
            "http://localhost:3000/" + "a".repeat(3000),
        ).forEach { assertNull(PreviewLinks.parse(it), "should not preview: $it") }
    }

    @Test
    fun `B-CHAT-026 scanning prose finds links and drops trailing punctuation`() {
        val found = PreviewLinks.scan("Running at http://localhost:5173/. Also (see http://127.0.0.1:8080/api), done!")
        assertEquals(listOf(PreviewLink(5173), PreviewLink(8080, path = "/api")), found)
        assertEquals(emptyList(), PreviewLinks.scan("visit https://example.com:3000 or http://localhost"))
    }

    @Test
    fun `B-CHAT-026 recent previews are newest first one per port and capped`() {
        val texts =
            listOf(
                "http://localhost:3000 then http://localhost:3001",
                "http://localhost:3000/again",
                "https://localhost:3000",
                (1..10).joinToString(" ") { "http://localhost:${4000 + it}" },
            )
        val recent = PreviewLinks.recent(texts)
        assertEquals(5, recent.size)
        assertEquals(listOf(4010, 4009, 4008, 4007, 4006), recent.map { it.port })
        val all = PreviewLinks.recent(texts.take(3), limit = 10)
        assertEquals(listOf("https:3000", "http:3000", "http:3001"), all.map { it.key })
        assertEquals("/again", all[1].path)
    }

    @Test
    fun `B-CHAT-026 the ports route carries only ports and schemes`() {
        val route = PreviewRoute.Ports.from(null, listOf("see http://localhost:3000/secret?token=x and https://127.0.0.1:8443"))
        assertEquals(listOf(PreviewPort(8443, https = true), PreviewPort(3000)), route.mentioned)
    }

    @Test
    fun `B-CHAT-027 a phone server opens on loopback with 0000 written as 127001`() {
        val link = PreviewLinks.parse("http://0.0.0.0:3000/x?y=1")!!
        assertEquals(DevPreviewTarget.Phone("http://127.0.0.1:3000/x?y=1"), DevPreviewTarget.resolve(link, "http://127.0.0.1:4096"))
        assertEquals(DevPreviewTarget.Phone("http://127.0.0.1:3000/x?y=1"), DevPreviewTarget.resolve(link, "http://localhost:4096"))
    }

    @Test
    fun `B-CHAT-027 a remote server opens on its host with the link port and scheme`() {
        val link = PreviewLinks.parse("https://localhost:5173/app")!!
        val target = DevPreviewTarget.resolve(link, "https://box.tail.ts.net:4096")
        assertEquals(DevPreviewTarget.Remote("https://box.tail.ts.net:5173/app", "box.tail.ts.net"), target)
        val plain = DevPreviewTarget.resolve(PreviewLink(8080), "http://devbox.lan:4096")
        assertEquals(DevPreviewTarget.Remote("http://devbox.lan:8080/", "devbox.lan"), plain)
    }

    @Test
    fun `B-CHAT-027 an agent on the phone over a lease opens on the phone, any other lease is blocked`() {
        val link = PreviewLink(3000)
        assertEquals(DevPreviewTarget.Phone("http://127.0.0.1:3000/"), DevPreviewTarget.resolve(link, "http://127.0.0.1:9000", leased = true))
        assertEquals(DevPreviewTarget.Blocked(PreviewBlock.NoServer), DevPreviewTarget.resolve(link, "https://box.example:4096", leased = true))
    }

    @Test
    fun `B-CHAT-027 with no server or no host the preview is blocked with a reason`() {
        val link = PreviewLink(3000)
        assertEquals(DevPreviewTarget.Blocked(PreviewBlock.NoServer), DevPreviewTarget.resolve(link, null))
        assertEquals(DevPreviewTarget.Blocked(PreviewBlock.NoServer), DevPreviewTarget.resolve(link, "  "))
        assertEquals(DevPreviewTarget.Blocked(PreviewBlock.NoHost), DevPreviewTarget.resolve(link, "not an address"))
        assertEquals(DevPreviewTarget.Blocked(PreviewBlock.NoHost), DevPreviewTarget.resolve(link, "https://user:pw@box.example"))
    }

    @Test
    fun `B-SEC-058 the preview loads only the same host and port over http(s) without credentials`() {
        val egress = PreviewEgress(WebOrigin.of("http://devbox.lan:5173")!!)
        assertTrue(egress.mayLoad("http://devbox.lan:5173/"))
        assertTrue(egress.mayLoad("http://devbox.lan:5173/deep/page?q=1"))
        assertTrue(egress.mayLoad("https://devbox.lan:5173/"))
        assertFalse(egress.mayLoad("http://devbox.lan:5174/"))
        assertFalse(egress.mayLoad("http://otherbox.lan:5173/"))
        assertFalse(egress.mayLoad("http://evil.example/"))
        assertFalse(egress.mayLoad("ws://devbox.lan:5173/hmr"))
        assertFalse(egress.mayLoad("file:///etc/hosts"))
        assertFalse(egress.mayLoad("javascript:alert(1)"))
        assertFalse(egress.mayLoad("intent://x#Intent;end"))
        assertFalse(egress.mayLoad("http://user:pw@devbox.lan:5173/"))
        assertFalse(egress.mayLoad("http://devbox.lan/"))
    }

    @Test
    fun `B-SEC-058 posts never leave and documents refuse frames websockets and other origins`() {
        val egress = PreviewEgress(WebOrigin.of("http://127.0.0.1:3000/")!!)
        assertTrue(egress.methodAllowed("GET"))
        assertTrue(egress.methodAllowed("head"))
        listOf("POST", "PUT", "PATCH", "DELETE", "OPTIONS").forEach { assertFalse(egress.methodAllowed(it), it) }
        val policy = egress.documentPolicy
        assertTrue("frame-src 'none'" in policy)
        assertTrue("child-src 'none'" in policy)
        assertTrue("worker-src 'none'" in policy)
        assertTrue("connect-src http://127.0.0.1:3000 https://127.0.0.1:3000;" in policy)
        assertFalse("'self'" in policy, "'self' would admit ws:// to the same host")
        assertFalse("ws:" in policy)
        assertFalse("*" in policy)
    }

    @Test
    fun `B-SEC-058 origins parse default ports and refuse credentials and odd shapes`() {
        assertEquals(WebOrigin("http", "box", 80), WebOrigin.of("http://BOX/path"))
        assertEquals(WebOrigin("https", "box", 443), WebOrigin.of("https://box"))
        assertEquals(WebOrigin("http", "[fd7a::1]", 3000), WebOrigin.of("http://[fd7a::1]:3000/"))
        assertEquals("https://box", WebOrigin("https", "box", 443).address)
        listOf("http://a:b:c/", "http://[::1", "http://box:99999", "http://u@box", "box:3000", "http://").forEach {
            assertNull(WebOrigin.of(it), it)
        }
    }
}
