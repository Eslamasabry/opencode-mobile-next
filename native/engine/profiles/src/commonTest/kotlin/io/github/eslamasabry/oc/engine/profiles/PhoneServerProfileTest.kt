package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.BackendKind
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Wave 3 conn 2: the connect path built This phone's server as a computer (LoadedProfile.toServerProfile's
 * managedOnPhone defaulted to false), so the AI Team page, Phone files, Plans and the project signals treated it as
 * one. The profile store now answers it for every row it publishes, from the app's own managed port.
 */
class PhoneServerProfileTest {
    private val port = 4098

    private fun TestScope.harness() =
        Harness(StandardTestDispatcher(testScheduler), phoneServer = { PhoneServerProfile.managedOnPhone(it, port) })

    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this).value

    private fun row(url: String, backend: BackendKind = BackendKind.OpenCode2) =
        StoredProfile(id("p"), "P", url, backend = backend)

    @Test
    fun `W3-CONN-010 the app's managed port on the phone's own address is this phone`() {
        assertTrue(PhoneServerProfile.managedOnPhone(row("http://127.0.0.1:$port"), port))
        // Setup adopts a localhost row at that port as the phone's server too.
        assertTrue(PhoneServerProfile.managedOnPhone(row("http://localhost:$port/"), port))
    }

    @Test
    fun `W3-CONN-011 loopback alone is not this phone`() {
        // A tunnel to a computer on the phone's loopback, on another port.
        assertFalse(PhoneServerProfile.managedOnPhone(row("http://127.0.0.1:4096"), port))
        assertFalse(PhoneServerProfile.managedOnPhone(row("http://localhost:8080"), port))
        assertFalse(PhoneServerProfile.managedOnPhone(row("https://127.0.0.1:$port"), port))
        // The phone agents' helper is not the phone's OpenCode server.
        assertFalse(PhoneServerProfile.managedOnPhone(row("ws://127.0.0.1:$port", BackendKind.Paseo), port))
        assertFalse(PhoneServerProfile.managedOnPhone(row("http://studio-pc.example:$port"), port))
    }

    @Test
    fun `W3-CONN-012 a row already saved at the managed address loads as this phone and connects as it`() = runTest {
        val h = harness()
        // Saved by setup, the Flutter-data migration or an older build: no flag is stored, the load answers it.
        h.seed(
            "oc.profiles" to
                """[{"id":"phone","name":"This phone","baseUrl":"http://127.0.0.1:$port","username":"opencode","flavor":"v2"},""" +
                """{"id":"pc","name":"Studio","baseUrl":"http://127.0.0.1:4096","username":"opencode","flavor":"v2"}]""",
        )
        val loaded = h.store.load().ok().associateBy { it.id.raw }
        assertTrue(loaded.getValue("phone").managedOnPhone)
        assertEquals(true, loaded.getValue("phone").toServerProfile()?.managedOnPhone)
        assertFalse(loaded.getValue("pc").managedOnPhone, "a loopback tunnel to a computer")
        assertEquals(false, loaded.getValue("pc").toServerProfile()?.managedOnPhone)
    }

    @Test
    fun `W3-CONN-013 setup's new row and an edit follow the rule`() = runTest {
        val h = harness()
        h.store.load().ok()
        val created = h.store.upsert(StoredProfile(id("phone"), "This phone", "http://127.0.0.1:$port", username = "opencode")).ok()
        assertTrue(created.managedOnPhone)
        assertTrue(h.store.profile(id("phone"))!!.managedOnPhone)
        // An edit that points the row at a computer makes it one.
        val moved = h.store.upsert(created.profile.copy(baseUrl = "http://127.0.0.1:4096")).ok()
        assertFalse(moved.managedOnPhone)
        assertEquals(false, h.store.profile(id("phone"))?.toServerProfile()?.managedOnPhone)
    }

    @Test
    fun `W3-CONN-014 a caller may still pin the flag explicitly`() = runTest {
        val h = harness()
        h.store.load().ok()
        val created = h.store.upsert(StoredProfile(id("phone"), "This phone", "http://127.0.0.1:$port")).ok()
        assertEquals(false, created.toServerProfile(managedOnPhone = false)?.managedOnPhone)
    }
}
