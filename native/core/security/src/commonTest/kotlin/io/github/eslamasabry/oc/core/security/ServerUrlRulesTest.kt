package io.github.eslamasabry.oc.core.security

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports test/release_blockers_test.dart (URL policy, IPv6 loopback, bare IPv6). */
class ServerUrlRulesTest {
    @Test
    fun `B-SEC-146 release URL policy requires explicit consent for remote HTTP`() {
        assertNull(ServerUrlRules.validate("http://localhost:4096"))
        assertNull(ServerUrlRules.validate("http://127.0.0.1:4096"))
        assertNull(ServerUrlRules.validate("https://192.0.2.4:4096"))
        assertEquals(ServerUrlProblem.MissingScheme, ServerUrlRules.validate("devbox.local:4096"))
        assertNull(ServerUrlRules.validate("http://192.0.2.4:4096"))
        assertEquals(ServerUrlProblem.HasPath, ServerUrlRules.validate("https://server.example:4096/api"))
        assertNull(ServerUrlRules.validate("https://server.example:4096/"))
        // Valid to save, but remote HTTP still needs the separate explicit choice.
        assertTrue(ServerUrlRules.needsCleartextConfirmation("http://192.0.2.4:4096"))
        assertFalse(ServerUrlRules.needsCleartextConfirmation("http://127.0.0.1:4096"))
        assertFalse(ServerUrlRules.needsCleartextConfirmation("https://192.0.2.4:4096"))
        assertEquals(ServerUrlProblem.CredentialsInUrl, ServerUrlRules.validate("https://" + "user" + ":" + "pw" + "@server.example"))
        assertEquals(ServerUrlProblem.QueryOrFragment, ServerUrlRules.validate("https://server.example?x=1"))
        assertEquals(ServerUrlProblem.BadPort, ServerUrlRules.validate("https://server.example:"))
        assertEquals(ServerUrlProblem.BadScheme, ServerUrlRules.validate("ftp://server.example"))
        assertEquals(ServerUrlProblem.Empty, ServerUrlRules.validate("  "))
    }

    @Test
    fun `B-SEC-146 IPv6 loopback is loopback everywhere`() {
        assertNull(ServerUrlRules.validate("http://[::1]:4096"))
        assertNull(ServerUrlRules.validate("http://[::1]"))
        assertNull(ServerUrlRules.validate("http://[2001:db8::1]:4096"))
        assertNull(ServerUrlRules.validate("https://[2001:db8::1]:4096"))
        assertTrue(ServerUrlRules.needsCleartextConfirmation("http://[2001:db8::1]:4096"))
        assertFalse(ServerUrlRules.needsCleartextConfirmation("http://[::1]:4096"))
        assertTrue(AddressRules.isLoopback("::1"))
        assertFalse(AddressRules.isLoopback("::2"))
    }

    @Test
    fun `B-SEC-146 bare IPv6 addresses normalize into usable URLs`() {
        assertEquals("http://[::1]:4096", ServerUrlRules.normalizeText("[::1]:4096"))
        assertEquals("http://[::1]", ServerUrlRules.normalizeText("[::1]"))
        assertEquals("http://[::1]", ServerUrlRules.normalizeText("::1"))
        assertEquals(ServerOrigin("http", "::1", 80), assertIs<Outcome.Ok<ServerOrigin>>(AddressRules.normalize("::1")).value)
        assertEquals("https://[2001:db8::1]:4096", ServerUrlRules.normalizeText("[2001:db8::1]:4096"))
        val linkLocal = "fe" + "80::1"
        assertEquals("https://[$linkLocal]", ServerUrlRules.normalizeText(linkLocal))
        assertEquals("http://127.0.0.1:4096", ServerUrlRules.normalizeText("127.0.0.1:4096"))
        assertEquals(":4096", ServerUrlRules.normalizeText(":4096"))
    }
}
