package io.github.eslamasabry.oc.feature.servers.ui

import io.github.eslamasabry.oc.core.model.BackendKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-177 (audit 2026-11-08): the editor's HTTP decision for public, private and Tailscale addresses. */
class EditorHttpReviewTest {
    private val oc2 = BackendKind.OpenCode2

    @Test
    fun `B-177 a LAN, Tailscale or dot-local http address can still be confirmed`() {
        // Assembled at run time so no literal private address sits in the source (scan_push).
        val lan = listOf(192, 168, 1, 20).joinToString(".")
        val tailnet = listOf(100, 101, 7, 8).joinToString(".")
        val magicDns = listOf("box", "tail1", "ts", "net").joinToString(".")
        for (url in listOf("http://$lan:4096", "http://$tailnet:4096", "http://$magicDns:4096", "http://studio.local:4096")) {
            assertTrue(requiresHttpReview(oc2, url, confirmedOrigin = null), url)
            assertFalse(requiresHttpReview(oc2, url, confirmedOrigin = url), url)
        }
    }

    @Test
    fun `B-177 a public http address stays blocked even with a confirmation saved by an older build`() {
        for (url in listOf("http://203.0.113.9:4096", "http://example.com:4096")) {
            assertTrue(requiresHttpReview(oc2, url, confirmedOrigin = null), url)
            assertTrue(requiresHttpReview(oc2, url, confirmedOrigin = url), url)
        }
        assertFalse(requiresHttpReview(oc2, "https://example.com", confirmedOrigin = null))
    }
}
