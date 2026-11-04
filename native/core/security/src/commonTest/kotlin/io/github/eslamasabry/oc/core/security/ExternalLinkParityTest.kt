package io.github.eslamasabry.oc.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ports test/external_link_test.dart (safeExternalLinkUri, externalLinkHost, openAgentSignInPage). */
class ExternalLinkParityTest {
    private val policy = ExternalLinkPolicy()

    private fun ask(raw: String): LinkDecision.AskFirst = assertIs(policy.check(raw, LinkOrigin.Markdown))

    private fun refused(raw: String) = assertIs<LinkDecision.Refuse>(policy.check(raw, LinkOrigin.Markdown), raw)

    @Test
    fun `B-SEC-064 https opens after asking and the scheme case is normalized`() {
        assertEquals("https://example.com/a?b=c#d", ask("https://example.com/a?b=c#d").link.uri)
        assertTrue(ask("HTTPS://example.com/").link.uri.startsWith("https://"))
        assertEquals("example.com", ask("  https://example.com/ ").shownHost)
        assertEquals(false, ask("https://example.com/").insecure)
    }

    @Test
    fun `B-SEC-064 http is accepted only so the caller can warn`() {
        val d = ask("http://example.com/")
        assertTrue(d.link.uri.startsWith("http://"))
        assertEquals(true, d.insecure)
    }

    @Test
    fun `B-SEC-064 every scheme a link label cannot describe is refused`() {
        listOf(
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "content://com.android.providers.downloads/all_downloads/1",
            "intent://scan/#Intent;scheme=zxing;end",
            "opencode://settings",
            "tel:+15550100",
            "mailto:someone@example.com",
            "//example.com/protocol-relative",
            "/just/a/path",
        ).forEach(::refused)
    }

    @Test
    fun `B-SEC-064 embedded credentials are refused in any form`() {
        val at = "@"
        listOf("https://user:pass${at}example.com/", "https://user${at}example.com/", "https://a%40b:c${at}example.com/").forEach {
            assertEquals(LinkDecision.Refuse(LinkRefusal.CredentialsInUrl), policy.check(it, LinkOrigin.Markdown))
        }
    }

    @Test
    fun `B-SEC-064 hostless, empty and unparseable values are refused`() {
        listOf("", "   ", "https://", "https:///path", "not a url at all", "http://[oops").forEach(::refused)
    }

    @Test
    fun `B-SEC-064 an overlong URL is refused rather than passed to the platform`() {
        assertEquals(
            LinkDecision.Refuse(LinkRefusal.TooLong),
            policy.check("https://example.com/" + "a".repeat(2048), LinkOrigin.Markdown),
        )
        ask("https://example.com/" + "a".repeat(2000))
    }

    @Test
    fun `B-SEC-065 the confirmation names the host and shows the port only when the URL does`() {
        assertEquals("example.com", ask("https://example.com/a").shownHost)
        assertEquals("example.com:8443", ask("https://example.com:8443/a").shownHost)
    }

    @Test
    fun `B-SEC-065 the verified sign-in page opens without a second sheet, any other address still asks`() {
        val page = "https://claude.com/cai/oauth/authorize?state=s&code=true"
        val open = assertIs<LinkDecision.Open>(policy.checkSignInPage(page))
        assertEquals(page, open.link.uri)
        assertIs<LinkDecision.AskFirst>(policy.checkSignInPage("https://claude.com.evil.example/cai"))
        assertIs<LinkDecision.AskFirst>(policy.checkSignInPage("https://claude.com:443/cai/oauth/authorize"))
        assertIs<LinkDecision.Refuse>(policy.checkSignInPage("javascript:alert(1)"))
    }
}
