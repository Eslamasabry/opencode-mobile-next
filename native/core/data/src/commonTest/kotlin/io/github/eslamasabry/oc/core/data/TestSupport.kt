package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.migration.MigrationGate
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.secret.Aead
import io.github.eslamasabry.oc.core.data.secret.SecretEntries
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlinx.coroutines.CoroutineDispatcher

/** A backend that can refuse (return false) or throw on the next commits, and records every commit it saw. */
internal class FakeBackend(initial: Map<String, RawPref> = emptyMap()) : InMemoryPrefsBackend(initial) {
    var refuseNext = 0
    var throwNext = 0
    val commits = mutableListOf<Map<String, RawPref?>>()

    /** Mirrors SharedPreferencesImpl: memory takes the change even when the disk write is refused. */
    val memory: LinkedHashMap<String, RawPref> = LinkedHashMap(initial)

    override fun commit(changes: Map<String, RawPref?>): Boolean {
        commits += LinkedHashMap(changes)
        for ((k, v) in changes) if (v == null) memory.remove(k) else memory[k] = v
        if (throwNext > 0) {
            throwNext--
            throw IllegalStateException("disk full: secret-value-must-not-leak")
        }
        if (refuseNext > 0) {
            refuseNext--
            return false
        }
        return super.commit(changes)
    }
}

internal fun physical(vararg pairs: Pair<String, RawPref>): Map<String, RawPref> =
    pairs.associate { (k, v) -> FlutterPrefsCodec.physicalKey(k) to v }

internal val testCodec = FlutterPrefsCodec(JavaListDecoder.Unsupported)

internal fun storeOver(
    backend: InMemoryPrefsBackend,
    io: CoroutineDispatcher,
    gate: MigrationGate = StateMigrationGate.alwaysOpen(),
    codec: FlutterPrefsCodec = testCodec,
): PrefsKeyValueStore = PrefsKeyValueStore(backend, codec, gate, io)

/** Not a cipher: an XOR stream plus a SHA-256 tag over (aad ‖ plaintext), so a wrong AAD fails like GCM does. */
internal class FakeAead : Aead {
    private var counter = 0

    override fun seal(plaintext: ByteArray, aad: ByteArray): Aead.Sealed {
        val iv = ByteArray(12) { (counter + it).toByte() }.also { counter++ }
        val body = plaintext.mapIndexed { i, b -> (b.toInt() xor iv[i % 12].toInt()).toByte() }.toByteArray()
        return Aead.Sealed(iv, body + tag(aad, plaintext))
    }

    override fun open(iv: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray {
        val body = ciphertextAndTag.copyOfRange(0, ciphertextAndTag.size - 16)
        val plain = body.mapIndexed { i, b -> (b.toInt() xor iv[i % 12].toInt()).toByte() }.toByteArray()
        check(tag(aad, plain).contentEquals(ciphertextAndTag.copyOfRange(body.size, ciphertextAndTag.size))) { "bad tag" }
        return plain
    }

    private fun tag(aad: ByteArray, plain: ByteArray): ByteArray =
        DartJson.sha256Hex(aad + byteArrayOf(0) + plain).chunked(2).take(16).map { it.toInt(16).toByte() }.toByteArray()
}

internal open class MapSecretEntries : SecretEntries {
    val map = LinkedHashMap<String, String>()
    var beforePut: ((String, String) -> Unit)? = null

    override fun get(name: String): String? = map[name]

    override fun names(): Set<String> = map.keys.toSet()

    override fun put(name: String, value: String): Boolean {
        beforePut?.invoke(name, value)
        map[name] = value
        return true
    }

    override fun remove(name: String): Boolean = map.remove(name) != null
}

internal class MapSecretStore(initial: Map<String, String> = emptyMap()) : SecretStore {
    val map = LinkedHashMap(initial)

    override suspend fun read(name: String): Outcome<String?> = Outcome.Ok(map[name])

    override suspend fun write(name: String, value: String): Outcome<Unit> = Outcome.Ok(Unit).also { map[name] = value }

    override suspend fun delete(name: String): Outcome<Unit> = Outcome.Ok(Unit).also { map.remove(name) }

    override suspend fun names(prefix: String): Outcome<Set<String>> = Outcome.Ok(map.keys.filter { it.startsWith(prefix) }.toSet())
}
