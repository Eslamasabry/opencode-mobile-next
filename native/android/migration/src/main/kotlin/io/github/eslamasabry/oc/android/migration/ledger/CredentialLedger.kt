package io.github.eslamasabry.oc.android.migration.ledger

import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.migration.FaultPoints
import io.github.eslamasabry.oc.android.migration.readers.migration.Fs
import java.io.File

/**
 * `files/native/credential-ledger.json` (plan 05 §4.4, Astra B3): per secret NAME, the native revision, live or
 * deleted (a tombstone), the FSS ciphertext digest the native value last agreed with, whether the name is queued for
 * the R1 guard, and the transitions in which the guard consumed it. Never a value. Atomic writes (`.tmp`, fsync,
 * rename); outside every snapshot, so R2 and Start fresh never move it.
 */
internal class CredentialLedger(private val file: File, private val faults: FaultPoints = FaultPoints.None) {

    data class Record(
        val rev: Long,
        val state: State,
        val legacy: String?,
        val staleQueued: Boolean,
        val guardConsumed: List<String> = emptyList(),
    )

    enum class State(val wire: String) { Live("live"), Deleted("deleted") }

    fun all(): Map<String, Record> {
        if (!file.isFile) return emptyMap()
        val names = (MiniJson.parseOrNull(file.readText(Charsets.UTF_8)) as? Map<*, *>)?.get("names") as? Map<*, *>
            ?: return UNREADABLE
        val out = LinkedHashMap<String, Record>()
        for ((k, v) in names) {
            val m = v as? Map<*, *> ?: continue
            out[k as String] = Record(
                rev = m["rev"] as? Long ?: 0L,
                state = if (m["state"] == State.Deleted.wire) State.Deleted else State.Live,
                legacy = m["legacy"] as? String,
                staleQueued = m["staleQueued"] == true,
                guardConsumed = (m["guardConsumed"] as? List<*>).orEmpty().filterIsInstance<String>(),
            )
        }
        return out
    }

    /**
     * True when the ledger knows the name at all. Plan 05 §4.4 rule 2: any record suppresses an M2 retry, because a value
     * the person typed, a renewal, a sign-out or a reset always wins over the old Flutter copy. An unreadable ledger
     * answers true for every name: refusing a retry costs one "Enter password"; a wrong refill would resurrect a
     * credential.
     */
    fun has(name: String): Boolean {
        val all = all()
        return all === UNREADABLE || name in all
    }

    /** Every name with a record, or null when the ledger file exists but does not parse. */
    fun knownOrNull(): Set<String>? = all().takeUnless { it === UNREADABLE }?.keys

    /** M2: the native value now equals the FSS copy with ciphertext digest [legacyDigest]. Never overwrites a record. */
    fun recordMigrated(name: String, legacyDigest: String) {
        val all = all()
        if (all === UNREADABLE || name in all) return
        save(all + (name to Record(rev = 0, state = State.Live, legacy = legacyDigest, staleQueued = false)))
    }

    /**
     * Rule 1, the FIRST write of a native change or delete: `rev+1`, state, `legacy = null`, `staleQueued = [queued]`.
     * The caller then adds the name to `oc.native.staleLegacySecrets` (when FSS still holds it) and only then changes the
     * secret store. A crash in between leaves a stale marker for an unchanged value: one "Enter password" in R1, never a
     * resurrected credential.
     */
    fun invalidate(name: String, deleted: Boolean, queued: Boolean) {
        val all = all().takeUnless { it === UNREADABLE } ?: emptyMap()
        val before = all[name]
        save(
            all + (
                name to Record(
                    rev = (before?.rev ?: 0L) + 1,
                    state = if (deleted) State.Deleted else State.Live,
                    legacy = null,
                    staleQueued = queued || before?.staleQueued == true,
                    guardConsumed = before?.guardConsumed.orEmpty(),
                )
            ),
        )
    }

    private fun save(records: Map<String, Record>) {
        val names = records.toSortedMap().mapValues { (_, r) ->
            linkedMapOf(
                "rev" to r.rev, "state" to r.state.wire, "legacy" to r.legacy, "staleQueued" to r.staleQueued,
                "guardConsumed" to r.guardConsumed,
            )
        }
        Fs.writeAtomically(file, MiniJson.encode(linkedMapOf("v" to 1L, "names" to names)).toByteArray(Charsets.UTF_8), faults, "ledger")
    }

    companion object {
        /** Sentinel for a ledger file that exists but does not parse. */
        private val UNREADABLE: Map<String, Record> = java.util.Collections.unmodifiableMap(LinkedHashMap())

        /** Digest of the FSS ciphertext as stored (never of a decrypted value: a hash of a short password can be guessed). */
        fun ciphertextDigest(storedCiphertext: String): String = Fs.sha256Hex(storedCiphertext.toByteArray(Charsets.UTF_8))
    }
}
