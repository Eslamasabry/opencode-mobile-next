package io.github.eslamasabry.oc.core.data.secret

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicReference
import kotlin.io.encoding.Base64

/** Shapes only (class names) ever reach `Failure.details`. */
internal class SecretFormatUnknown : Exception()
internal class SecretWriteRefused : Exception()
internal class SecretReadBackMismatch : Exception()

/**
 * The secret file format (plan 05 §4.1): entry name = the secret's name verbatim; entry value =
 * `Base64(0x01 ‖ IV[12] ‖ ciphertext ‖ tag[16])` (standard alphabet, no line breaks), AAD = UTF-8 of the entry name,
 * so a ciphertext copied under another name fails to open.
 *
 * - Every read and write registers the value with the [Redactor] BEFORE returning or persisting it (a failing cipher
 *   may echo its input).
 * - `write` commits, reads back and compares; a mismatch deletes the entry and fails.
 * - Values are cached after the first read (Keystore decrypt is 5–20 ms).
 * - All calls are serialised on one lock and run on [io].
 */
public class AeadSecretStore(
    private val entries: SecretEntries,
    private val aead: Aead,
    private val redactor: Redactor,
    private val io: CoroutineDispatcher,
) : SecretStore {

    private val lock = Mutex()
    private val cache = AtomicReference<PersistentMap<String, String>>(persistentHashMapOf())

    override suspend fun read(name: String): Outcome<String?> {
        cache.load()[name]?.let { return Outcome.Ok(it) }
        return withContext(io) {
            lock.withLock {
                attempt(::secretFailure) {
                    val stored = entries.get(name) ?: return@attempt null
                    val plain = decrypt(name, stored)
                    redactor.registerSecret(plain)
                    remember(name, plain)
                    plain
                }
            }
        }
    }

    override suspend fun write(name: String, value: String): Outcome<Unit> {
        redactor.registerSecret(value) // before any cipher or disk call (plan 05 §4.2)
        // An admitted secret write finishes its commit and its read-back check (plan 03 §17.5 rule 2).
        return withContext(io + NonCancellable) {
            lock.withLock {
                attempt(::secretFailure) {
                    if (!entries.put(name, encrypt(name, value))) throw SecretWriteRefused()
                    // A read-back that throws (bad tag, bad Base64) counts as a mismatch: nothing half-written stays.
                    val back = try {
                        entries.get(name)?.let { decrypt(name, it) }
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        null
                    }
                    if (back != value) {
                        forget(name)
                        entries.remove(name)
                        throw SecretReadBackMismatch()
                    }
                    remember(name, value)
                }
            }
        }
    }

    override suspend fun delete(name: String): Outcome<Unit> =
        withContext(io + NonCancellable) {
            lock.withLock {
                attempt(::secretFailure) {
                    forget(name)
                    if (entries.get(name) != null && !entries.remove(name)) throw SecretWriteRefused()
                }
            }
        }

    override suspend fun names(prefix: String): Outcome<Set<String>> =
        withContext(io) { lock.withLock { attempt(::secretFailure) { entries.names().filterTo(LinkedHashSet()) { it.startsWith(prefix) } } } }

    private fun encrypt(name: String, value: String): String {
        val sealed = aead.seal(value.encodeToByteArray(), name.encodeToByteArray())
        require(sealed.iv.size == IV_BYTES)
        val out = ByteArray(1 + IV_BYTES + sealed.ciphertextAndTag.size)
        out[0] = FORMAT_V1
        sealed.iv.copyInto(out, 1)
        sealed.ciphertextAndTag.copyInto(out, 1 + IV_BYTES)
        return Base64.Default.encode(out)
    }

    private fun decrypt(name: String, stored: String): String {
        val bytes = Base64.Default.decode(stored)
        if (bytes.size < 1 + IV_BYTES + TAG_BYTES || bytes[0] != FORMAT_V1) throw SecretFormatUnknown()
        val iv = bytes.copyOfRange(1, 1 + IV_BYTES)
        val body = bytes.copyOfRange(1 + IV_BYTES, bytes.size)
        return aead.open(iv, body, name.encodeToByteArray()).decodeToString(throwOnInvalidSequence = true)
    }

    private fun remember(name: String, value: String) {
        while (true) {
            val c = cache.load()
            if (cache.compareAndSet(c, c.putting(name, value))) return
        }
    }

    private fun forget(name: String) {
        while (true) {
            val c = cache.load()
            if (cache.compareAndSet(c, c.removing(name))) return
        }
    }

    public companion object {
        public const val FORMAT_V1: Byte = 0x01
        public const val IV_BYTES: Int = 12
        public const val TAG_BYTES: Int = 16

        /** The Android entry file and Keystore alias (plan 05 §4.1). */
        public const val FILE_NAME: String = "oc_secrets_v1"
        public const val KEY_ALIAS: String = "oc.secrets.aesgcm.v1"

        private fun secretFailure(e: Throwable): Failure = Failure.SecretUnavailable(Redacted.shape(e))
    }
}
