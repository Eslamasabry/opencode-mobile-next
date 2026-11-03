package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from the "endpoint policy" group of test/paseo_gateway_test.dart. */
class PaseoEndpointTest {
    @Test
    fun `B-SEC-113 cleartext is limited to this device and Tailscale`() {
        assertEquals("ws://127.0.0.1:6767/ws", PaseoEndpoint.parse("ws://127.0.0.1:6767").url)
        assertEquals("pc.tail1234.ts.net", PaseoEndpoint.parse("ws://pc.tail1234.ts.net:6767").host)
        assertEquals("ws", PaseoEndpoint.parse("ws://pc.tail1234.ts.net:6767").scheme)
        assertEquals("wss://pc.tail1234.ts.net/ws", PaseoEndpoint.parse("wss://pc.tail1234.ts.net").url)
        assertEquals("ws://[::1]:6767/ws", PaseoEndpoint.parse("ws://[::1]:6767/").url)
        assertEquals("http://127.0.0.1:6767/ws", PaseoEndpoint.parse("ws://127.0.0.1:6767/ws").httpUrl)
        for (bad in listOf(
            "ws://198.51.100.10:6767",
            "ws://paseo.example:6767",
            "wss://paseo.example:6767",
            "http://127.0.0.1:6767",
            "ws://user:pw@127.0.0.1:6767",
            "ws://127.0.0.1:6767/other",
            "ws://127.0.0.1:6767/ws?token=1",
            "ws://127.0.0.1:6767#frag",
            "not a url",
        )) {
            val failure = assertFailsWith<PaseoException>(bad) { PaseoEndpoint.parse(bad) }
            assertEquals(PaseoFailureKind.InvalidEndpoint, failure.kind, bad)
            assertEquals(RefusalReason.InvalidAddress, (failure.toFailure() as Failure.Refused).reason)
        }
    }

    @Test
    fun `B-SEC-113 profile validators agree and an empty password is allowed`() {
        assertNull(PaseoProfileRules.validateUrl("ws://pc.tail1234.ts.net:6767"))
        assertEquals(PaseoUrlProblem.NotPrivate, PaseoProfileRules.validateUrl("ws://198.51.100.10:6767"))
        assertEquals(PaseoUrlProblem.NotPrivate, PaseoProfileRules.validateUrl("wss://public.example:6767"))
        assertNull(PaseoProfileRules.validateUrl("wss://pc.tail1234.ts.net:6767"))
        assertEquals(PaseoUrlProblem.Empty, PaseoProfileRules.validateUrl("  "))
        assertEquals(PaseoUrlProblem.WrongScheme, PaseoProfileRules.validateUrl("http://127.0.0.1:6767"))
        assertEquals(PaseoUrlProblem.CredentialsInAddress, PaseoProfileRules.validateUrl("ws://u:p@127.0.0.1:6767"))
        assertEquals(PaseoUrlProblem.QueryOrFragment, PaseoProfileRules.validateUrl("ws://127.0.0.1:6767?x=1"))
        assertEquals(PaseoUrlProblem.HasPath, PaseoProfileRules.validateUrl("ws://127.0.0.1:6767/api"))
        assertTrue(PaseoProfileRules.normalizeUrl("pc.tail1234.ts.net:6767").startsWith("ws://"))
        assertTrue(PaseoProfileRules.normalizeUrl("paseo.example").startsWith("wss://"))
        assertEquals("ws://127.0.0.1:1", PaseoProfileRules.normalizeUrl("ws://127.0.0.1:1"))
        assertTrue(PaseoProfileRules.passwordAcceptable(""))
        assertTrue(PaseoProfileRules.passwordAcceptable("s3cret"))
        assertFalse(PaseoProfileRules.passwordAcceptable("has space"))
        assertFalse(PaseoProfileRules.passwordAcceptable("a,b"))
        assertFalse(PaseoProfileRules.passwordAcceptable("tab\there"))
        assertFalse(PaseoProfileRules.passwordAcceptable("x".repeat(4097)))
        // Validators agree with the transport's own check.
        for (url in listOf("ws://pc.tail1234.ts.net:6767", "ws://198.51.100.10:6767", "ws://127.0.0.1:6767/ws", "wss://a.example")) {
            val parsed = try {
                PaseoEndpoint.parse(url)
            } catch (_: PaseoException) {
                null
            }
            assertEquals(PaseoProfileRules.validateUrl(url) == null, parsed != null, url)
        }
    }

    @Test
    fun `B-SEC-113 a host with characters a socket library reads as an authority end is refused`() {
        // A tailnet-looking suffix after `\` (or other non-hostname characters) must not pass: OkHttp ends the
        // authority at `\` and would send the password to the public host before it.
        for (bad in listOf(
            "wss://attacker.example\\.tail1234.ts.net",
            "ws://attacker.example\\.tail1234.ts.net:6767",
            "wss://attacker.example%5c.tail1234.ts.net",
            "wss://attacker.example;.tail1234.ts.net",
            "wss://attacker.example .tail1234.ts.net",
            "wss://attacker.example\t.tail1234.ts.net",
            "ws://[::1\\x]:6767",
            "wss://..tail1234.ts.net",
        )) {
            assertFailsWith<PaseoException>(bad) { PaseoEndpoint.parse(bad) }
            assertEquals(PaseoUrlProblem.Incomplete, PaseoProfileRules.validateUrl(bad), bad)
        }
        assertEquals("pc-1.tail1234.ts.net", PaseoEndpoint.parse("wss://PC-1.tail1234.ts.net").host)
        assertEquals("::1", PaseoEndpoint.parse("ws://[::1]:6767").host)
    }

    @Test
    fun `B-SEC-113 daemon reasons are tidied and capped for Details`() {
        assertNull(tidyPaseoReason("  \n "))
        assertEquals("a b c", tidyPaseoReason(" a\n b\t c "))
        val long = tidyPaseoReason("x".repeat(500))
        assertNotNull(long)
        assertEquals(240, long.length)
        assertTrue(long.endsWith("…"))
    }
}
