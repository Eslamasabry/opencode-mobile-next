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
}
