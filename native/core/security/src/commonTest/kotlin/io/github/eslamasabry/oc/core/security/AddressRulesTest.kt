package io.github.eslamasabry.oc.core.security

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.ServerOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Address vectors ported from Flutter test/private_network_cleartext_test.dart and test/server_trust_test.dart. */
class AddressRulesTest {
    @Test
    fun `B-SEC-119 private IPv4 ranges are in, their neighbours are out`() {
        for (h in listOf("10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.255", "192.168.0.1", "192.168.255.255", "169.254.1.1")) {
            assertTrue(AddressRules.isPrivateNetwork(h), h)
        }
        for (h in listOf(
            "9.255.255.255", "11.0.0.1", "172.15.255.255", "172.32.0.1", "192.167.1.1", "192.169.0.1", "169.253.1.1",
            "169.255.0.1", "8.8.8.8", "192.0.2.4", "100.64.0.1", "127.0.0.1", "0.0.0.0",
        )) {
            assertFalse(AddressRules.isPrivateNetwork(h), h)
        }
    }

    @Test
    fun `B-SEC-119 IPv6 unique-local and link-local are in, the rest is out`() {
        for (h in listOf("fc00::1", "fd12:3456::1", "[fd12:3456::1]", "fe80::1", "febf::1", "fe80::1%wlan0", "::ffff:192.168.1.5")) {
            assertTrue(AddressRules.isPrivateNetwork(h), h)
        }
        for (h in listOf("fbff::1", "fec0::1", "2001:db8::1", "2606:4700::1111", "::1", "::", "::ffff:8.8.8.8")) {
            assertFalse(AddressRules.isPrivateNetwork(h), h)
        }
    }

    @Test
    fun `B-SEC-119 only dot-local names count as private names`() {
        for (h in listOf("desk.local", "Desk.Local.", "my.desk.local")) assertTrue(AddressRules.isPrivateNetwork(h), h)
        for (h in listOf(
            "local", ".local", "a..local", "desk.localhost", "desk.local.example.com", "example.com", "router",
            "localhost", "pc.tail1234.ts.net", "10.0.0.1.example.com", "",
        )) {
            assertFalse(AddressRules.isPrivateNetwork(h), h)
        }
    }

    @Test
    fun `B-SEC-122 classification separates loopback, private, Tailscale, link-local and public`() {
        assertEquals(HostClass.Loopback, AddressRules.classify("127.0.0.1"))
        assertEquals(HostClass.Loopback, AddressRules.classify("localhost"))
        assertEquals(HostClass.Loopback, AddressRules.classify("[::1]"))
        assertEquals(HostClass.Tailnet, AddressRules.classify("100.64.0.1"))
        assertEquals(HostClass.Tailnet, AddressRules.classify("100.127.255.254"))
        assertEquals(HostClass.Public, AddressRules.classify("100.128.0.1"))
        assertEquals(HostClass.Tailnet, AddressRules.classify("box.tail1234.ts.net"))
        assertEquals(HostClass.PrivateLan, AddressRules.classify("172.20.1.1"))
        assertEquals(HostClass.PrivateLan, AddressRules.classify("desk.local"))
        assertEquals(HostClass.PrivateLan, AddressRules.classify("fd12::1"))
        assertEquals(HostClass.LinkLocal, AddressRules.classify("169.254.3.4"))
        assertEquals(HostClass.LinkLocal, AddressRules.classify("fe80::1%wlan0"))
        assertEquals(HostClass.Public, AddressRules.classify("example.com"))
        assertEquals(HostClass.Invalid, AddressRules.classify(""))
        assertEquals(HostClass.Invalid, AddressRules.classify("a b"))
    }

    @Test
    fun `B-SEC-153 the trust origin includes the effective port and ignores the path`() {
        assertEquals(Outcome.Ok(ServerOrigin("http", "example.com", 80)), AddressRules.origin("http://EXAMPLE.com/api"))
        assertEquals(Outcome.Ok(ServerOrigin("https", "example.com", 443)), AddressRules.origin("https://example.com:443/"))
        assertEquals("http://[::1]:4098", (AddressRules.origin("http://[::1]:4098") as Outcome.Ok).value.toString())
    }

    @Test
    fun `B-SEC-153 credentials, query, fragment and non-HTTP addresses are refused`() {
        val refused =
            mapOf(
                "https://user:password@example.com" to RefusalReason.CredentialsInAddress,
                "https://example.com?token=secret" to RefusalReason.InvalidAddress,
                "https://example.com#secret" to RefusalReason.InvalidAddress,
                "file:///tmp/socket" to RefusalReason.InvalidAddress,
                "wss://example.com" to RefusalReason.InvalidAddress,
                "http://" to RefusalReason.InvalidAddress,
                "http://host:99999" to RefusalReason.InvalidAddress,
            )
        for ((url, reason) in refused) {
            val failure = assertIs<Outcome.Failed>(AddressRules.origin(url), url).failure
            assertEquals(Failure.Refused(reason), failure, url)
            assertFalse("secret" in failure.toString() || "password" in failure.toString())
        }
    }

    @Test
    fun `B-SEC-153 loopback needs no consent and every remote HTTP origin does`() {
        for (h in listOf("127.0.0.1", "localhost", "[::1]")) {
            assertFalse(AddressRules.needsCleartextConsent((AddressRules.origin("http://$h:4098") as Outcome.Ok).value), h)
        }
        for (h in listOf("192.168.1.2", "100.64.0.2", "machine.tail.ts.net", "203.0.113.2", "example.com")) {
            assertTrue(AddressRules.needsCleartextConsent((AddressRules.origin("http://$h:4098") as Outcome.Ok).value), h)
        }
        assertFalse(AddressRules.needsCleartextConsent(ServerOrigin("https", "192.168.1.20", 4096)))
    }

    @Test
    fun `B-SEC-120 a bare address gains https, except loopback which gains http`() {
        assertEquals(Outcome.Ok(ServerOrigin("https", "192.168.1.20", 4096)), AddressRules.normalize("192.168.1.20:4096"))
        assertEquals(Outcome.Ok(ServerOrigin("https", "desk.local", 443)), AddressRules.normalize("desk.local"))
        assertEquals(Outcome.Ok(ServerOrigin("http", "127.0.0.1", 4096)), AddressRules.normalize(" 127.0.0.1:4096 "))
        assertEquals(Outcome.Ok(ServerOrigin("http", "10.0.0.2", 4096)), AddressRules.normalize("http://10.0.0.2:4096"))
    }

    @Test
    fun `B-SEC-153 a pin is a complete SHA-256, accepts colons and cannot be downgraded to HTTP`() {
        assertEquals(Outcome.Ok("ab".repeat(32)), AddressRules.normalizePin(List(32) { "AB" }.joinToString(":")))
        for (bad in listOf("", "abc", "g".repeat(64), "a".repeat(63), "a".repeat(65))) {
            assertIs<Outcome.Failed>(AddressRules.normalizePin(bad), "length ${bad.length}")
        }
        assertEquals(
            Outcome.Failed(Failure.Refused(RefusalReason.PinNeedsHttps)),
            AddressRules.checkPin(ServerOrigin("http", "localhost", 4098), "a".repeat(64)),
        )
        assertEquals(Outcome.Ok("a".repeat(64)), AddressRules.checkPin(ServerOrigin("https", "example.com", 443), "A".repeat(64)))
    }
}
