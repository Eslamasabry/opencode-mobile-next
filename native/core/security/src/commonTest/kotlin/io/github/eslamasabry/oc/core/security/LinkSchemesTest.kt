package io.github.eslamasabry.oc.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** B-SEC-064, ported from the Flutter `safeExternalLinkUri` cases (test/external_link_test.dart). */
class LinkSchemesTest {
    private val policy = ExternalLinkPolicy()

    private fun refused(raw: String) = policy.check(raw, LinkOrigin.ServerContent) is LinkDecision.Refuse

    @Test
    fun `B-SEC-064 https opens after the host is shown, whatever the scheme case or outer spaces`() {
        val d = assertIs<LinkDecision.AskFirst>(policy.check("  HTTPS://example.com/a?b=c#d ", LinkOrigin.ServerContent))
        assertEquals("example.com", d.shownHost)
        assertFalse(d.link.insecure)
    }

    @Test
    fun `B-SEC-064 http is allowed only so the caller can warn`() {
        val d = assertIs<LinkDecision.AskFirst>(policy.check("http://example.com/", LinkOrigin.Markdown))
        assertTrue(d.link.insecure)
    }

    @Test
    fun `B-SEC-064 every other scheme is refused before reaching the platform`() {
        for (value in listOf(
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
        )) {
            assertTrue(refused(value), value)
        }
    }

    @Test
    fun `B-SEC-064 embedded credentials are refused in any form`() {
        for (value in listOf("https://user:pass@example.com/", "https://user@example.com/", "https://a%40b:c@example.com/")) {
            assertEquals(LinkDecision.Refuse(LinkRefusal.CredentialsInUrl), policy.check(value, LinkOrigin.ServerContent), value)
        }
    }

    @Test
    fun `B-SEC-064 hostless, empty and unparseable values are refused`() {
        for (value in listOf("", "   ", "https://", "https:///path", "not a url at all", "http://[oops", "https://exa mple.com/")) {
            assertTrue(refused(value), "'$value'")
        }
    }

    @Test
    fun `B-SEC-064 an overlong URL is refused rather than passed on`() {
        assertEquals(
            LinkDecision.Refuse(LinkRefusal.TooLong),
            policy.check("https://example.com/" + "a".repeat(2048), LinkOrigin.ServerContent),
        )
        assertIs<LinkDecision.AskFirst>(policy.check("https://example.com/" + "a".repeat(2000), LinkOrigin.ServerContent))
    }

    @Test
    fun `B-SEC-064 the same rules hold for links the app wrote itself`() {
        assertIs<LinkDecision.Open>(policy.check("https://example.com:8443/a", LinkOrigin.AppAuthored))
        assertTrue(policy.check("mailto:someone@example.com", LinkOrigin.AppAuthored) is LinkDecision.Refuse)
        assertTrue(policy.check("https:///nohost", LinkOrigin.AppAuthored) is LinkDecision.Refuse)
    }
}
