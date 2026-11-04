package io.github.eslamasabry.oc.engine.runtime.switching

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/forget_in_app_server_test.dart (B-LINUX-070): Reset Linux forgets the active "This phone" profile. */
class InAppServerForgettingTest {
    /** Preferences, Keystore, profile rows, the offline queue and the phone-server owners, in memory. */
    private class Device : ForgetStore, ForgetQueue, ForgetRuntime {
        val rows = mutableListOf<LocalProfile>()
        val prefs = mutableMapOf<String, String>()
        val secrets = mutableMapOf<String, String>()
        val queue = mutableListOf<Pair<String, String>>()
        val savedPrompts = mutableListOf<String>()
        var queueReadable = true
        var refuseKeys = emptySet<String>()
        var connected: String? = null
        val suspended = mutableListOf<String>()
        val order = mutableListOf<String>()

        override fun profiles(): List<LocalProfile> = rows.toList()

        override fun activeId(): String? = prefs["oc.activeProfile"]

        override fun preferenceKeys(): Set<String> = prefs.keys.toSet()

        override suspend fun removePreference(key: String): Boolean {
            if (key in refuseKeys) return false
            prefs.remove(key)
            return true
        }

        override suspend fun removeProfile(id: String): Outcome<Unit> {
            if (rows.none { it.id == id }) return Failure.StorageRefused().failed()
            order += "row"
            rows.removeAll { it.id == id }
            if (prefs["oc.activeProfile"] == id) prefs.remove("oc.activeProfile")
            secrets.remove("pw.$id")
            return Unit.ok()
        }

        override fun queuedFor(profileId: String): List<String>? =
            if (!queueReadable) null else queue.filter { it.second == profileId }.map { it.first }

        override suspend fun keepAsSaved(profileId: String, ids: List<String>): Boolean {
            savedPrompts += ids
            return true
        }

        override suspend fun removeFor(profileId: String): Boolean {
            queue.removeAll { it.second == profileId }
            return true
        }

        override suspend fun suspendRecovery(profileId: String) {
            order += "recovery"
            suspended += profileId
        }

        override suspend fun clearRuntimeOwner(profileId: String) {
            order += "owner"
            if (prefs["oc.builtinServerOwner"] == profileId) prefs["oc.builtinServerOwner"] = ""
        }

        override fun connectedProfileId(): String? = connected

        override suspend fun disconnect() {
            order += "disconnect"
            connected = null
        }
    }

    private val device = Device()
    private val phone = LocalProfile("phone", "This phone", ManagedLocalServer.BUILTIN_URL)
    private val forgetting = InAppServerForgetting(device, device, device)

    private fun activePhone() {
        device.rows += phone
        device.prefs["oc.activeProfile"] = phone.id
        device.secrets["pw.phone"] = "synthetic-password"
        device.connected = phone.id
        device.prefs["oc.builtinServerOwner"] = phone.id
        device.prefs["oc.builtinRecovery.phone"] = "{\"version\":1,\"attempts\":0}"
        device.prefs["oc.automation.phone"] = "{}"
        device.prefs["oc.model.phone"] = "anthropic/model"
        device.prefs["oc.developmentServices.phone"] = "{\"version\":1,\"services\":[]}"
        device.prefs["oc.phoneServerStopped.phone"] = "true"
        device.prefs["oc.notify.phone"] = "app-wide"
    }

    private fun leftovers(id: String): List<String> =
        device.prefs.keys.filter { key -> key.startsWith("oc.") && key.split('.').drop(2).firstOrNull() == id && !key.startsWith("oc.notify.") }

    @Test
    fun `B-LINUX-070 the active in-app profile is forgotten and nothing is left`() = runTest {
        activePhone()
        val problems = forgetting.forgetAll()
        assertTrue(problems.isEmpty(), problems.toString())
        assertTrue(device.rows.isEmpty())
        assertNull(device.activeId())
        assertFalse(device.secrets.containsKey("pw.phone"))
        assertEquals(emptyList(), leftovers(phone.id))
        assertEquals("", device.prefs["oc.builtinServerOwner"], "an empty pointer suppresses fallback to another profile")
        assertNull(device.connected)
        assertEquals(listOf(phone.id), device.suspended)
        assertEquals("app-wide", device.prefs["oc.notify.phone"], "app-wide namespaces are never swept")
        // Owners are revoked before anything goes; the row goes last.
        assertEquals(listOf("recovery", "owner", "disconnect", "row"), device.order)
    }

    @Test
    fun `B-LINUX-070 queued prompts are kept in Saved prompts and other profiles are untouched`() = runTest {
        activePhone()
        val remote = LocalProfile("remote", "Computer", "https://server.example")
        device.rows += remote
        device.secrets["pw.remote"] = "remote-secret"
        device.prefs["oc.model.remote"] = "x"
        device.queue += "q1" to phone.id
        device.queue += "q2" to remote.id
        assertTrue(forgetting.forgetAll().isEmpty())
        assertEquals(listOf("q1"), device.savedPrompts)
        assertEquals(listOf("q2" to remote.id), device.queue)
        assertEquals(listOf(remote), device.rows)
        assertEquals("remote-secret", device.secrets["pw.remote"])
        assertEquals("x", device.prefs["oc.model.remote"])
    }

    @Test
    fun `B-LINUX-070 an unreadable queue keeps the profile and says so`() = runTest {
        activePhone()
        device.queueReadable = false
        assertEquals(listOf<ForgetProblem>(ForgetProblem.QueuedNotKept("This phone")), forgetting.forgetAll())
        assertEquals(listOf(phone), device.rows)
        assertEquals("synthetic-password", device.secrets["pw.phone"])
        assertTrue(device.suspended.isEmpty(), "nothing destructive before the queue is preserved")
    }

    @Test
    fun `B-LINUX-070 a setting that cannot be cleared keeps the row and its password`() = runTest {
        activePhone()
        device.refuseKeys = setOf("oc.model.phone")
        val problems = forgetting.forgetAll()
        assertEquals(listOf<ForgetProblem>(ForgetProblem.Left("This phone", connectionKept = true, failures = listOf(LeftOver.Settings(1)))), problems)
        assertEquals(listOf(phone), device.rows)
        assertEquals("synthetic-password", device.secrets["pw.phone"])
        assertEquals("phone", device.connected)
    }
}
