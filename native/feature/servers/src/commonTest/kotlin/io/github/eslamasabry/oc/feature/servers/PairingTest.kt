package io.github.eslamasabry.oc.feature.servers

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Failure.Net
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.feature.servers.presentation.AddressVerdict
import io.github.eslamasabry.oc.feature.servers.presentation.PairingCode
import io.github.eslamasabry.oc.feature.servers.presentation.PairingDialer
import io.github.eslamasabry.oc.feature.servers.presentation.PairingParse
import io.github.eslamasabry.oc.feature.servers.presentation.PairingPayload
import io.github.eslamasabry.oc.feature.servers.presentation.PairingRejection
import io.github.eslamasabry.oc.feature.servers.presentation.ProbeAnswer
import io.github.eslamasabry.oc.feature.servers.presentation.ProbeTarget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Ported from test/pairing_payload_test.dart and test/private_network_cleartext_test.dart (pairing group). */
class PairingTest {
    /** A stand-in, never a live serve password: a fixture that is also a credential is a credential in git. */
    private val password = "fixture-not-a-live-serve-password-000000000"

    private fun pairJson(urls: List<String> = listOf("http://127.0.0.1:49374"), username: String = "opencode"): String =
        """{"urls":[${urls.joinToString(",") { "\"$it\"" }}],"username":"$username","password":"$password"}"""

    private fun parsed(raw: String): PairingPayload = assertIs<PairingParse.Ok>(PairingCode.parse(raw)).payload

    private fun dialer(probe: suspend (ProbeTarget) -> Outcome<ProbeAnswer>, confirmed: Set<ServerOrigin> = emptySet()) =
        PairingDialer(RulesAddressPolicy, probe, preferLoopback = false, confirmedCleartext = confirmed)

    private val v2Ok: Outcome<ProbeAnswer> = Outcome.Ok(ProbeAnswer("2.1.1"))

    // ---- parsing (the shape `opencode2 pair` prints)

    @Test
    fun `B-SEC-107 parses the exact pair shape and keeps the username`() {
        val p = parsed(pairJson())
        assertEquals(listOf("http://127.0.0.1:49374"), p.urls)
        assertEquals("opencode", p.username)
        assertEquals(password, p.password()?.reveal())
        assertEquals("opencode", parsed("""{"urls":["https://a.example"],"password":"x"}""").username)
        assertEquals("opencode", parsed("""{"urls":["https://a.example"],"username":"  ","password":"x"}""").username)
    }

    @Test
    fun `B-SEC-107 malformed codes are refused with a reason and never echo the secret`() {
        val secret = "secret-fixture-value"
        val cases =
            mapOf(
                "" to PairingRejection.Empty,
                "not json at all" to PairingRejection.NotJson,
                "[\"http://127.0.0.1:4097\"]" to PairingRejection.NotAnObject,
                """{"password":"$secret"}""" to PairingRejection.NoUrls,
                """{"urls":[],"password":"$secret"}""" to PairingRejection.NoAddress,
                """{"urls":["  "],"password":"$secret"}""" to PairingRejection.NoAddress,
                """{"urls":"http://a","password":"$secret"}""" to PairingRejection.UrlsNotAList,
                """{"urls":["https://a.example"]}""" to PairingRejection.NoPassword,
                """{"urls":["https://a.example"],"password":7}""" to PairingRejection.PasswordNotText,
                """{"urls":["https://a.example"],"username":3,"password":"x"}""" to PairingRejection.UsernameNotText,
                """{"urls":["a"],"password":"$secret""" to PairingRejection.NotJson,
            )
        for ((raw, reason) in cases) {
            val result = PairingCode.parse(raw)
            assertEquals(PairingParse.Rejected(reason), result, raw)
            assertFalse(result.toString().contains(secret), raw)
        }
        val many = (0..32).map { "https://h$it.example" }
        assertEquals(PairingParse.Rejected(PairingRejection.TooManyUrls), PairingCode.parse(pairJson(many)))
    }

    @Test
    fun `B-SEC-107 trims and de-duplicates addresses in the server's order`() {
        val p = parsed(pairJson(listOf(" https://b.example:4097 ", "https://a.example:4097", "https://b.example:4097")))
        assertEquals(listOf("https://b.example:4097", "https://a.example:4097"), p.urls)
    }

    @Test
    fun `B-SEC-107 printing redacts the password and keeps the useful half`() {
        val rendered = parsed(pairJson()).toString()
        assertFalse(password in rendered)
        assertTrue("<redacted>" in rendered)
        assertTrue("127.0.0.1:49374" in rendered)
        assertTrue("opencode" in rendered)
    }

    @Test
    fun `B-SEC-107 consume drops the password and printing says so`() {
        val p = parsed(pairJson())
        assertFalse(p.isConsumed)
        p.consume()
        assertTrue(p.isConsumed)
        assertNull(p.password())
        assertTrue("<consumed>" in p.toString())
        p.consume() // twice is fine; the addresses stay for display
        assertTrue(p.urls.isNotEmpty())
    }

    @Test
    fun `B-SEC-107 printing strips credentials and query secrets from addresses`() {
        val urlSecret = "url-query-secret-fixture"
        val rendered =
            parsed(pairJson(listOf("https://pair-user:pair-password@desk.example:4097/?token=$urlSecret#fragment-$urlSecret")))
                .toString()
        assertTrue("https://desk.example:4097" in rendered, rendered)
        assertFalse("pair-user" in rendered)
        assertFalse("pair-password" in rendered)
        assertFalse(urlSecret in rendered)
    }

    @Test
    fun `B-SEC-107 printing does not echo an untrusted non-default username`() {
        val rendered = parsed(pairJson(username = "username-secret-fixture")).toString()
        assertTrue("username: <redacted>" in rendered)
        assertFalse("username-secret-fixture" in rendered)
    }

    @Test
    fun `B-SEC-107 the address list cannot be mutated by a caller`() {
        val urls: List<String> = parsed(pairJson()).urls
        // A persistent list is not a MutableList at all: a caller cannot even cast its way to `add`.
        assertFalse(urls is MutableList<*>)
        assertFailsWith<ClassCastException> {
            @Suppress("UNCHECKED_CAST")
            (urls as MutableList<String>).add("https://evil.example")
        }
    }

    @Test
    fun `B-SEC-107 looksLike claims real and malformed codes, not plain text`() {
        assertTrue(PairingCode.looksLike("  ${pairJson()}  "))
        assertTrue(PairingCode.looksLike("""{"urls":[],"password":"x"}"""))
        assertFalse(PairingCode.looksLike("https://desk.example:4097"))
    }

    // ---- choosing an address

    @Test
    fun `B-SEC-108 when no address answers the reason is given per address`() =
        runTest {
            val p = parsed(pairJson(listOf("http://127.0.0.1:4097", "https://desk.example:4097")))
            val selection =
                dialer({ t ->
                    when (t.origin.host) {
                        "127.0.0.1" -> Outcome.Failed(Net.Unreachable(Net.Unreachable.Why.Refused))
                        else -> Outcome.Failed(Net.Unreachable(Net.Unreachable.Why.Timeout))
                    }
                }).select(p)
            assertFalse(selection.ok)
            assertNull(selection.chosen)
            assertEquals(
                listOf("https://desk.example:4097" to AddressVerdict.TimedOut, "http://127.0.0.1:4097" to AddressVerdict.Refused),
                selection.outcomes.map { it.display to it.verdict },
            )
        }

    @Test
    fun `B-SEC-108 the per-address detail strips URL secrets and never carries probe text`() =
        runTest {
            val urlSecret = "url-query-secret-fixture"
            val probeSecret = "probe-error-secret-fixture"
            val p = parsed(pairJson(listOf("https://pair-user:pair-password@desk.example:4097/?token=$urlSecret")))
            // The address with credentials is refused before any probe; a probe that fails says only a verdict.
            val selection = dialer({ fail("must not be dialed: an address with credentials") }).select(p)
            val outcome = selection.outcomes.single()
            assertEquals("https://desk.example:4097", outcome.display)
            assertEquals(AddressVerdict.CredentialsInAddress, outcome.verdict)
            assertFalse(outcome.probed)
            assertFalse(urlSecret in selection.toString())
            assertFalse("pair-password" in selection.toString())

            val q = parsed(pairJson(listOf("https://desk.example:4097")))
            val failed = dialer({ Outcome.Failed(Failure.Unknown()) }).select(q)
            assertEquals(AddressVerdict.ProbeFailed, failed.outcomes.single().verdict)
            assertFalse(probeSecret in failed.toString())
        }

    @Test
    fun `B-SEC-108 a safe bracketed IPv6 origin stays readable`() =
        runTest {
            val p = parsed(pairJson(listOf("https://[2001:db8::1]:4097")))
            val selection = dialer({ Outcome.Failed(Net.Unreachable(Net.Unreachable.Why.Timeout)) }).select(p)
            assertEquals("https://[2001:db8::1]:4097", selection.outcomes.single().display)
        }

    @Test
    fun `B-SEC-108 the first address that answers is chosen and later ones are not dialed`() =
        runTest {
            val dialed = mutableListOf<String>()
            val p = parsed(pairJson(listOf("https://first.example:4097", "https://second.example:4097", "https://third.example:4097")))
            val selection =
                dialer({ t ->
                    dialed += t.origin.host
                    if (t.origin.host == "first.example") Outcome.Failed(Net.Unreachable(Net.Unreachable.Why.Refused)) else v2Ok
                }).select(p)
            assertEquals(listOf("first.example", "second.example"), dialed)
            assertEquals(ServerOrigin("https", "second.example", 4097), selection.chosen)
            assertEquals(password, selection.chosenTarget?.password?.reveal())
        }

    @Test
    fun `B-SEC-124 a private http address is held, never probed, until confirmed`() =
        runTest {
            val probed = mutableListOf<ServerOrigin>()
            val probe: suspend (ProbeTarget) -> Outcome<ProbeAnswer> = { probed += it.origin; v2Ok }
            val p = parsed(pairJson(listOf("http://192.168.1.20:4097")))
            val held = dialer(probe).select(p)
            assertTrue(probed.isEmpty())
            assertFalse(held.ok)
            val outcome = held.outcomes.single()
            assertEquals(AddressVerdict.NeedsCleartextConsent, outcome.verdict)
            assertFalse(outcome.probed)
            assertEquals("http://192.168.1.20:4097", outcome.display)
            assertEquals(listOf(ServerOrigin("http", "192.168.1.20", 4097)), held.heldForConsent)

            val confirmed = dialer(probe, confirmed = setOf(ServerOrigin("http", "192.168.1.20", 4097))).select(p)
            assertEquals(listOf(ServerOrigin("http", "192.168.1.20", 4097)), probed)
            assertEquals(ServerOrigin("http", "192.168.1.20", 4097), confirmed.chosen)
            assertEquals(ServerOrigin("http", "192.168.1.20", 4097), confirmed.chosenTarget?.consentedOrigin)
        }

    @Test
    fun `B-SEC-124 a different private origin is not covered by the confirmation`() =
        runTest {
            val p = parsed(pairJson(listOf("http://192.168.1.21:4097")))
            val selection =
                dialer({ fail("must not be dialed: ${it.origin}") }, confirmed = setOf(ServerOrigin("http", "192.168.1.20", 4097)))
                    .select(p)
            assertEquals(AddressVerdict.NeedsCleartextConsent, selection.outcomes.single().verdict)
        }

    @Test
    fun `B-SEC-124 public HTTP pairing waits for explicit origin consent`() =
        runTest {
            val p = parsed(pairJson(listOf("http://192.0.2.20:4097")))
            val selection = dialer({ fail("must not be dialed: ${it.origin}") }).select(p)
            val outcome = selection.outcomes.single()
            assertEquals(AddressVerdict.NeedsCleartextConsent, outcome.verdict)
            assertFalse(outcome.probed)
        }

    @Test
    fun `B-SEC-124 loopback http is dialed without asking`() =
        runTest {
            val selection = dialer({ v2Ok }).select(parsed(pairJson(listOf("http://127.0.0.1:4097"))))
            assertEquals(ServerOrigin("http", "127.0.0.1", 4097), selection.chosen)
            assertNull(selection.chosenTarget?.consentedOrigin)
        }
}
