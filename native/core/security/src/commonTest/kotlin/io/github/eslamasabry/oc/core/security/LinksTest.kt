package io.github.eslamasabry.oc.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LinksTest {
    @Test
    fun `link policy refuses intent and credential urls and asks first for server content`() {
        val p = ExternalLinkPolicy()
        assertEquals(LinkDecision.Refuse(LinkRefusal.IntentScheme), p.check("intent://x#Intent;end", LinkOrigin.Markdown))
        assertEquals(LinkDecision.Refuse(LinkRefusal.CredentialsInUrl), p.check("https://a:b@x.io", LinkOrigin.Markdown))
        assertEquals(LinkDecision.Refuse(LinkRefusal.BadScheme), p.check("javascript:alert(1)", LinkOrigin.Markdown))
        assertIs<LinkDecision.AskFirst>(p.check("https://example.com/a", LinkOrigin.ServerContent))
        assertIs<LinkDecision.Open>(p.check("https://example.com/a", LinkOrigin.AppAuthored))
    }
}
