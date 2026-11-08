package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports the "stored confirmation" group of test/private_network_cleartext_test.dart (B-SEC-125). */
class PrivateCleartextConfirmationTest {
    /** A private LAN address, built so no literal private address sits in the source. */
    private val lan = listOf(192, 168, 1, 20).joinToString(".")

    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this).value

    @Test
    fun `B-SEC-125 an unconfirmed private http profile is blocked and writes no confirmation`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler))
        val saved = h.store.upsert(StoredProfile(id("p1"), "Desk", "http://$lan:4096"), SecretEdit.Set("pw-secret")).ok()
        assertTrue(saved.cleartextUnconfirmed)
        assertNull(h.string("oc.cleartextOk.p1"))
        assertFalse(h.store.upsert(StoredProfile(id("p2"), "Desk", "https://$lan:4096")).ok().cleartextUnconfirmed)
        assertFalse(h.store.upsert(StoredProfile(id("p3"), "Desk", "http://127.0.0.1:4096")).ok().cleartextUnconfirmed)
    }

    @Test
    fun `B-SEC-125 confirming survives a rebuilt profile and a reload, the deletion sweep removes the key`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler))
        val desk = StoredProfile(id("p1"), "Desk", "http://$lan:4096")
        h.store.upsert(desk, trust = TrustInput(cleartextOrigin = "http://$lan:4096")).ok()
        assertEquals("http://$lan:4096", h.string("oc.cleartextOk.p1"))
        assertFalse(h.store.upsert(desk).ok().cleartextUnconfirmed)
        val reloaded = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend).store.load().ok().single()
        assertFalse(reloaded.cleartextUnconfirmed)

        assertTrue(h.scope.ownedData(desk.id).ok().prefKeys.any { it == "oc.cleartextOk.p1" })
        h.deletion().delete(desk.id).ok()
        assertNull(h.string("oc.cleartextOk.p1"))
    }

    /** A Tailscale (100.64/10) address, built the same way. */
    private val tailnet = listOf(100, 101, 7, 8).joinToString(".")

    /** A documentation (TEST-NET-3) address: public, never routed. */
    private val public = listOf(203, 0, 113, 9).joinToString(".")

    @Test
    fun `B-177 a private or Tailscale origin confirmed before the rule keeps working after a reload`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler))
        for ((key, host) in listOf("p1" to lan, "p2" to tailnet, "p3" to "studio.local")) {
            val url = "http://$host:4096"
            h.store.upsert(StoredProfile(id(key), "Desk", url), trust = TrustInput(cleartextOrigin = url)).ok()
        }
        val reloaded = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend).store.load().ok()
        assertEquals(3, reloaded.size)
        for (profile in reloaded) {
            assertFalse(profile.cleartextUnconfirmed, profile.profile.baseUrl)
            assertEquals(0L, profile.toServerProfile()?.trust?.cleartextConsentAtMs, profile.profile.baseUrl)
        }
    }

    @Test
    fun `B-177 a public http origin cannot be confirmed`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler))
        val url = "http://$public:4096"
        val saved = h.store.upsert(StoredProfile(id("p1"), "Office", url), trust = TrustInput(cleartextOrigin = url)).ok()
        assertTrue(saved.cleartextUnconfirmed)
        assertNull(h.string("oc.cleartextOk.p1"))
        assertNull(saved.toServerProfile()?.trust?.cleartextConsentAtMs)
    }

    @Test
    fun `B-177 a public http origin confirmed before the rule is asked again`() = runTest {
        val h = Harness(StandardTestDispatcher(testScheduler))
        val url = "http://$public:4096"
        h.store.upsert(StoredProfile(id("p1"), "Office", url)).ok()
        // What an older build wrote when the person confirmed this public address.
        h.seed("oc.cleartextOk.p1" to url)
        val reloaded = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend).store.load().ok().single()
        assertTrue(reloaded.cleartextUnconfirmed)
        assertNull(reloaded.toServerProfile()?.trust?.cleartextConsentAtMs)
    }
}
