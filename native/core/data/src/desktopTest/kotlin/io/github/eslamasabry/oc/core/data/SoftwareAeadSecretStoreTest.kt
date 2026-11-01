package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.secret.AeadSecretStore
import io.github.eslamasabry.oc.core.data.secret.Aead
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The Keystore seam with a software AES-256-GCM key (plan 05 §4.2 `SoftwareAead`). */
internal class SoftwareAead(key: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }) : Aead {
    private val key = SecretKeySpec(key, "AES")

    override fun seal(plaintext: ByteArray, aad: ByteArray): Aead.Sealed {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        c.updateAAD(aad)
        return Aead.Sealed(iv, c.doFinal(plaintext))
    }

    override fun open(iv: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        c.updateAAD(aad)
        return c.doFinal(ciphertextAndTag)
    }
}

class SoftwareAeadSecretStoreTest {
    @Test
    fun `B-MIGR-006 real AES-GCM round trip, and an entry swapped to another name fails its tag`() = runTest {
        val entries = MapSecretEntries()
        val aead = SoftwareAead()
        val store = AeadSecretStore(entries, aead, Redactor(), StandardTestDispatcher(testScheduler))
        val value = "كلمة-سر-🔑"
        assertEquals(Outcome.Ok(Unit), store.write("oc.quotaToken.1", value))
        val reader = AeadSecretStore(entries, aead, Redactor(), StandardTestDispatcher(testScheduler))
        assertEquals(Outcome.Ok(value), reader.read("oc.quotaToken.1"))

        entries.map["pw.1"] = entries.map.getValue("oc.quotaToken.1")
        val swapped = reader.read("pw.1") as Outcome.Failed
        assertIs<Failure.SecretUnavailable>(swapped.failure)
        assertEquals("AEADBadTagException", swapped.failure.details?.text)

        val bytes = Base64.Default.decode(entries.map.getValue("oc.quotaToken.1"))
        bytes[bytes.size - 1] = (bytes.last() + 1).toByte()
        entries.map["oc.quotaToken.2"] = Base64.Default.encode(bytes)
        assertIs<Outcome.Failed>(reader.read("oc.quotaToken.2"), "a tampered tag never yields a value")
    }

    @Test
    fun `B-MIGR-006 another key cannot open the entries (a lost Keystore key is unreadable, never guessed)`() = runTest {
        val entries = MapSecretEntries()
        AeadSecretStore(entries, SoftwareAead(), Redactor(), StandardTestDispatcher(testScheduler)).write("pw.1", "fixture-pass-1")
        val other = AeadSecretStore(entries, SoftwareAead(), Redactor(), StandardTestDispatcher(testScheduler))
        assertIs<Outcome.Failed>(other.read("pw.1"))
    }
}
