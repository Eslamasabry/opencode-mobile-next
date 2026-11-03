package io.github.eslamasabry.oc.engine.entry.pairing

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Which pairing address is used (test/pairing_payload_test.dart:423-700), over a scripted probe. */
class PairingAddressSelectorTest {
    private val desk = "https://desk.example.invalid:4097"
    private val local = "http://localhost:4097"
    private val lan = "http://desk.lan.example.invalid:4097"
    private val creds = PairingCredentials("opencode", "pair-password-fixture")

    private class Scripted(private val answers: Map<String, PairingVerdict>) : PairingAddressProbe {
        val order = mutableListOf<String>()

        override suspend fun probe(baseUrl: String, credentials: PairingCredentials): PairingVerdict {
            order += baseUrl
            return answers[baseUrl] ?: fail("probed an address the test did not allow: $baseUrl")
        }
    }

    private suspend fun select(urls: List<String>, probe: PairingAddressProbe, preferLoopback: Boolean = false) =
        PairingAddressSelector.select(urls, creds, probe, preferLoopback)

    @Test
    fun `B-PROTO-068 tries addresses in order and stops at the first that connects`() = runTest {
        val phone = Scripted(mapOf(desk to PairingVerdict.Connected, local to PairingVerdict.Connected))
        val onPhone = select(listOf(local, desk), phone, preferLoopback = false)
        assertEquals(listOf(desk), phone.order)
        assertEquals(desk, onPhone.chosenUrl)
        assertTrue(onPhone.ok)
        assertTrue(onPhone.connected)

        val desktop = Scripted(mapOf(desk to PairingVerdict.Connected, local to PairingVerdict.Connected))
        val onDesktop = select(listOf(desk, local), desktop, preferLoopback = true)
        assertEquals(listOf(local), desktop.order)
        assertEquals(local, onDesktop.chosenUrl)
    }

    @Test
    fun `B-PROTO-068 falls through to the next address when the first is refused and records why`() = runTest {
        val selection = select(listOf(desk, local), Scripted(mapOf(desk to PairingVerdict.Refused, local to PairingVerdict.Connected)))
        assertEquals(local, selection.chosenUrl)
        assertTrue(selection.connected)
        val skipped = selection.outcomes.first { it.url == desk }
        assertTrue(skipped.probed)
        assertEquals(PairingReason.Refused, skipped.reason)
    }

    @Test
    fun `B-PROTO-068 a cleartext LAN address is refused before any request is made`() = runTest {
        val probe = Scripted(mapOf(local to PairingVerdict.Connected))
        val selection = select(listOf(lan, local), probe)
        assertEquals(listOf(local), probe.order)
        assertEquals(local, selection.chosenUrl)
        val held = selection.outcomes.first { it.url == lan }
        assertFalse(held.probed)
        assertEquals(PairingReason.CleartextNeedsConfirm, held.reason)
        assertTrue(held.needsCleartextConfirm)
        assertNull(held.verdict)

        // Once the person confirmed that origin, it may be dialed.
        val confirmed = Scripted(mapOf(lan to PairingVerdict.Connected))
        val after = PairingAddressSelector.select(listOf(lan), creds, confirmed, false, setOf(lan))
        assertEquals(lan, after.chosenUrl)
        assertEquals(listOf(lan), confirmed.order)
    }

    @Test
    fun `B-PROTO-068 a malformed address is reported and never dialed`() = runTest {
        val probe = Scripted(mapOf(local to PairingVerdict.Connected))
        val selection = select(listOf("ftp://desk.example.invalid", local), probe)
        assertEquals(local, selection.chosenUrl)
        val bad = selection.outcomes.first { it.url == "ftp://desk.example.invalid" }
        assertFalse(bad.probed)
        assertNotNull(bad.reason)
        assertEquals(listOf(local), probe.order)
    }

    @Test
    fun `B-PROTO-068 when nothing answers each address keeps its own reason`() = runTest {
        val selection = select(listOf(local, desk), Scripted(mapOf(local to PairingVerdict.Refused, desk to PairingVerdict.TimedOut)))
        assertFalse(selection.ok)
        assertNull(selection.chosenUrl)
        assertEquals(2, selection.outcomes.size)
        assertEquals(
            listOf(PairingFailureLine(desk, PairingReason.TimedOut), PairingFailureLine(local, PairingReason.Refused)).toSet(),
            selection.failureLines.toSet(),
        )
    }

    @Test
    fun `B-PROTO-068 the failure lines strip URL secrets and carry no probe text`() = runTest {
        val secret = "url-query-secret-fixture"
        val raw = "https://pair-user:pair-password@desk.example.invalid:4097/?token=$secret#fragment-$secret"
        val selection = select(listOf(raw), PairingAddressProbe { _, _ -> fail("must not dial a URL with credentials") })
        val line = selection.failureLines.single()
        assertEquals(desk, line.address)
        assertEquals(PairingReason.CredentialsInUrl, line.reason)
        val shown = selection.failureLines.joinToString { "${it.address} ${it.reason}" }
        for (leak in listOf("pair-user", "pair-password", secret)) assertFalse(leak in shown, leak)
    }

    @Test
    fun `B-PROTO-068 a thrown probe is isolated and the next address is tried`() = runTest {
        val first = "https://first.example.invalid:4097"
        val second = "https://second.example.invalid:4097"
        val order = mutableListOf<String>()
        val selection =
            select(listOf(first, second), PairingAddressProbe { url, _ ->
                order += url
                if ("first" in url) error("probe-thrown-secret-fixture")
                PairingVerdict.Connected
            })
        assertEquals(listOf(first, second), order)
        assertEquals(second, selection.chosenUrl)
        assertTrue(selection.connected)
        val failed = selection.outcomes.first { it.url == first }
        assertEquals(PairingReason.ProbeFailed, failed.reason)
        assertFalse("probe-thrown-secret-fixture" in failed.toString())
    }

    @Test
    fun `B-PROTO-068 an address answering 401 still counts as the host`() = runTest {
        val selection = select(listOf(desk, local), Scripted(mapOf(desk to PairingVerdict.PasswordRejected, local to PairingVerdict.Refused)))
        assertTrue(selection.ok)
        assertEquals(desk, selection.chosenUrl)
        assertFalse(selection.connected)
        assertEquals(PairingVerdict.PasswordRejected, selection.chosenVerdict)
    }

    @Test
    fun `B-PROTO-068 a working address beats one that merely answers`() = runTest {
        val selection = select(listOf(desk, local), Scripted(mapOf(desk to PairingVerdict.PasswordRejected, local to PairingVerdict.Connected)))
        assertEquals(local, selection.chosenUrl)
        assertTrue(selection.connected)
    }

    @Test
    fun `B-PROTO-068 the pairing credentials reach the probe unchanged`() = runTest {
        var seen: PairingCredentials? = null
        PairingAddressSelector.select(listOf(local), creds, PairingAddressProbe { _, c -> seen = c; PairingVerdict.Connected }, true)
        assertEquals("opencode", seen?.username)
        assertEquals("pair-password-fixture", seen?.password)
        assertFalse("pair-password-fixture" in creds.toString())
    }

    @Test
    fun `B-PROTO-068 a bare host is normalized before use`() = runTest {
        val probe = Scripted(mapOf(local to PairingVerdict.Connected, desk to PairingVerdict.Connected))
        assertEquals(local, select(listOf("localhost:4097"), probe, preferLoopback = true).chosenUrl)
        assertEquals(desk, select(listOf("desk.example.invalid:4097"), probe).chosenUrl)
        assertEquals("http://localhost:4097", PairingAddress.normalize("  localhost:4097 "))
    }

    @Test
    fun `B-PROTO-068 every address rejected up front still gives a reason per address`() = runTest {
        val other = "http://other.lan.example.invalid:4097"
        val selection = select(listOf(lan, other), PairingAddressProbe { url, _ -> fail("nothing here is safe to dial: $url") })
        assertFalse(selection.ok)
        assertEquals(2, selection.outcomes.size)
        assertTrue(selection.outcomes.none { it.probed })
        assertEquals(listOf(lan, other), selection.failureLines.map { it.address })
    }

    @Test
    fun `B-PROTO-068 the URL policy refuses paths queries and bad ports`() {
        assertEquals(PairingReason.PathPresent, (PairingAddress.check("$desk/api") as PairingAddressCheck.Unusable).reason)
        assertEquals(PairingReason.QueryOrFragment, (PairingAddress.check("$desk?x=1") as PairingAddressCheck.Unusable).reason)
        assertEquals(PairingReason.InvalidAddress, (PairingAddress.check("https://desk.example.invalid:99999") as PairingAddressCheck.Unusable).reason)
        assertEquals(PairingReason.InvalidAddress, (PairingAddress.check("https://desk.example.invalid:") as PairingAddressCheck.Unusable).reason)
        val ok = PairingAddress.check("HTTPS://Desk.Example.Invalid:4097/") as PairingAddressCheck.Usable
        assertEquals(desk, ok.url)
        assertFalse(ok.cleartext)
        assertNull(PairingAddress.display("not a url"))
    }
}
