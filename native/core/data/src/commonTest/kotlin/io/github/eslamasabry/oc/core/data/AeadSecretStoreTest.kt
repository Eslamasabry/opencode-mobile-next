package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.secret.AeadSecretStore
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AeadSecretStoreTest {
    private val password = "fixture-pass-1"

    @Test
    fun `B-MIGR-006 a secret round-trips in the v1 format with the entry name as AAD`() = runTest {
        val entries = MapSecretEntries()
        val store = AeadSecretStore(entries, FakeAead(), Redactor(), StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Ok(Unit), store.write("pw.1", password))
        val bytes = Base64.Default.decode(entries.map.getValue("pw.1"))
        assertEquals(0x01, bytes[0].toInt(), "format byte")
        assertEquals(1 + 12 + password.length + 16, bytes.size, "0x01 ‖ IV[12] ‖ ciphertext ‖ tag[16]")
        assertFalse(entries.map.getValue("pw.1").contains(password))

        val fresh = AeadSecretStore(entries, FakeAead(), Redactor(), StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Ok(password), fresh.read("pw.1"))
        assertEquals(Outcome.Ok(null), fresh.read("pw.2"))
    }

    @Test
    fun `B-MIGR-006 a ciphertext moved under another name does not open`() = runTest {
        val entries = MapSecretEntries()
        val store = AeadSecretStore(entries, FakeAead(), Redactor(), StandardTestDispatcher(testScheduler))
        store.write("pw.1", password)
        entries.map["pw.2"] = entries.map.getValue("pw.1")
        val swapped = store.read("pw.2")
        assertIs<Failure.SecretUnavailable>((swapped as Outcome.Failed).failure)
        assertFalse(swapped.failure.details!!.text.contains(password))

        entries.map["pw.3"] = Base64.Default.encode(byteArrayOf(0x02) + ByteArray(40))
        assertIs<Outcome.Failed>(store.read("pw.3"), "an unknown format version is unreadable, never guessed")
    }

    @Test
    fun `B-MIGR-011 every value is registered with the redactor before it is persisted or returned`() = runTest {
        val redactor = Redactor()
        val entries = MapSecretEntries()
        val store = AeadSecretStore(entries, FakeAead(), redactor, StandardTestDispatcher(testScheduler))
        var registeredBeforePut = false
        entries.beforePut = { _, _ -> registeredBeforePut = !redactor.redact("x $password y").contains(password) }
        store.write("pw.1", password)
        assertTrue(registeredBeforePut)

        val other = Redactor()
        val reader = AeadSecretStore(entries, FakeAead(), other, StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Ok(password), reader.read("pw.1"))
        assertFalse(other.redact("log $password").contains(password))
    }

    @Test
    fun `B-MIGR-006 a write whose read-back differs is deleted and reported`() = runTest {
        val entries = object : MapSecretEntries() {
            override fun get(name: String): String? = super.get(name)?.let { it.dropLast(4) + "AAA=" }
        }
        val store = AeadSecretStore(entries, FakeAead(), Redactor(), StandardTestDispatcher(testScheduler))
        assertIs<Outcome.Failed>(store.write("pw.1", password))
        assertNull(entries.map["pw.1"])
        assertEquals(Outcome.Ok(null), store.read("pw.1"), "nothing half-written stays behind")
    }

    @Test
    fun `names by prefix and delete`() = runTest {
        val store = AeadSecretStore(MapSecretEntries(), FakeAead(), Redactor(), StandardTestDispatcher(testScheduler))
        store.write("pw.1", "a")
        store.write("pw.2", "b")
        store.write("oc.codexToken.1", "c")
        assertEquals(Outcome.Ok(setOf("pw.1", "pw.2")), store.names("pw."))
        assertEquals(Outcome.Ok(Unit), store.delete("pw.1"))
        assertEquals(Outcome.Ok(null), store.read("pw.1"))
        assertEquals(Outcome.Ok(setOf("pw.2")), store.names("pw."))
    }

    @Test
    fun `B-MIGR-006 a refused delete is retried for real and the password does not come back after restart`() = runTest {
        val entries = SplitSecretEntries()
        val store = AeadSecretStore(entries, FakeAead(), Redactor(), StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Ok(Unit), store.write("pw.1", password))
        entries.refuseNext = 1
        assertIs<Outcome.Failed>(store.delete("pw.1"))
        // Memory already says "gone"; the disk still holds it. The retry must commit, not report success.
        assertEquals(Outcome.Ok(Unit), store.delete("pw.1"))
        assertFalse("pw.1" in entries.onDisk)
        val afterRestart = AeadSecretStore(entries.restarted(), FakeAead(), Redactor(), StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Ok(null), afterRestart.read("pw.1"))
    }

    @Test
    fun `B-MIGR-006 a refused write is never persisted by a later successful write of the file`() = runTest {
        val entries = SplitSecretEntries()
        val store = AeadSecretStore(entries, FakeAead(), Redactor(), StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Ok(Unit), store.write("pw.1", "old-pass"))
        entries.refuseNext = 1
        assertIs<Outcome.Failed>(store.write("pw.1", "refused-pass"))
        assertEquals(Outcome.Ok("old-pass"), store.read("pw.1"))
        assertEquals(Outcome.Ok(Unit), store.write("pw.2", "other")) // writes the whole file
        val afterRestart = AeadSecretStore(entries.restarted(), FakeAead(), Redactor(), StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Ok("old-pass"), afterRestart.read("pw.1"))

        // A first write that is refused leaves nothing behind either.
        entries.refuseNext = 1
        assertIs<Outcome.Failed>(store.write("pw.3", "never-saved"))
        assertEquals(Outcome.Ok(Unit), store.write("pw.2", "other-2"))
        val again = AeadSecretStore(entries.restarted(), FakeAead(), Redactor(), StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Ok(null), again.read("pw.3"))
    }
}
