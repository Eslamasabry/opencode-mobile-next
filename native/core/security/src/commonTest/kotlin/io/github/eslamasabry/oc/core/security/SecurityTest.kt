package io.github.eslamasabry.oc.core.security

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SecurityTest {
    @Test
    fun `redactor masks registered secrets and url credentials`() {
        val r = Redactor().apply { registerSecret("hunter22") }
        val out = r.redact("pw hunter22 at http://opencode:hunter22@10.0.0.2:4096/api?token=abc&x=1")
        assertFalse("hunter22" in out)
        assertFalse("abc" in out)
        assertEquals("pw ••• at http://•••@10.0.0.2:4096/api?token=•••&x=1", out)
    }

    @Test
    fun `link policy refuses intent and credential urls and asks first for server content`() {
        val p = ExternalLinkPolicy()
        assertEquals(LinkDecision.Refuse(LinkRefusal.IntentScheme), p.check("intent://x#Intent;end", LinkOrigin.Markdown))
        assertEquals(LinkDecision.Refuse(LinkRefusal.CredentialsInUrl), p.check("https://a:b@x.io", LinkOrigin.Markdown))
        assertEquals(LinkDecision.Refuse(LinkRefusal.BadScheme), p.check("javascript:alert(1)", LinkOrigin.Markdown))
        assertIs<LinkDecision.AskFirst>(p.check("https://example.com/a", LinkOrigin.ServerContent))
        assertIs<LinkDecision.Open>(p.check("https://example.com/a", LinkOrigin.AppAuthored))
    }

    @Test
    fun `address rules classify cgnat tailnet and private ranges`() {
        assertEquals(HostClass.Tailnet, AddressRules.classify("100.64.0.1"))
        assertEquals(HostClass.Tailnet, AddressRules.classify("100.127.255.254"))
        assertEquals(HostClass.Public, AddressRules.classify("100.128.0.1"))
        assertEquals(HostClass.Tailnet, AddressRules.classify("box.tail1234.ts.net"))
        assertEquals(HostClass.PrivateLan, AddressRules.classify("172.20.1.1"))
        assertEquals(HostClass.Loopback, AddressRules.classify("[::1]"))
        assertEquals(Outcome.Ok(ServerOrigin("http", "10.0.0.2", 4096)), AddressRules.normalize("10.0.0.2:4096"))
    }
}
