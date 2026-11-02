package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.data.secret.SecretStore

/** B-STATE-098: restart recovery for the metadata/Keystore boundary. Caller holds ProfileStore's mutex.
 * Journals contain metadata and secret NAMES only. The old value, when needed, stays in SecretStore.
 */
internal class ProfileTransactions(private val kv: KeyValueStore, private val secrets: SecretStore, private val lanes: WriterLanes) {
    private val log = AppLog.tagged("profiles.recovery")
    private fun key(id: ProfileId) = ProfileKeys.TRANSACTION + id.raw
    private fun backup(id: ProfileId) = ProfileKeys.TRANSACTION_SECRET + id.raw
    private fun broken() = Outcome.Failed(Failure.StorageRefused())

    suspend fun beginSave(id: ProfileId, name: String): Outcome<Unit> {
        val previous = when (val read = secrets.read(name)) {
            is Outcome.Failed -> return read
            is Outcome.Ok -> read.value
        }
        val journal = snapshot(id).apply {
            put("kind", "save")
            put("phase", "preparing")
            put("name", name)
            put("hadSecret", previous != null)
        }
        val started = write(id, journal)
        if (started is Outcome.Failed) return started
        if (previous != null) {
            val saved = secrets.write(backup(id), previous)
            if (saved is Outcome.Failed) {
                // No metadata or original secret has been touched. Recovery only discards the staging alias.
                return saved
            }
        }
        journal["phase"] = "ready"
        return write(id, journal)
    }

    suspend fun beginDelete(id: ProfileId, names: List<String>): Outcome<Unit> = write(id, snapshot(id).apply {
        put("kind", "delete")
        put("phase", "ready")
        put("names", names)
    })

    suspend fun finish(id: ProfileId, rollback: Boolean = false): Outcome<Unit> {
        val journal = read(id) ?: return broken()
        if (rollback) {
            val restored = rollbackSave(id, journal)
            if (restored is Outcome.Failed) return restored
        }
        journal["phase"] = "done"
        val marked = write(id, journal)
        if (marked is Outcome.Failed) return marked
        val cleaned = discard(id, journal["kind"] == "save")
        if (journal["kind"] == "save" && cleaned is Outcome.Failed) {
            // The durable done marker commits the save. Recovery only discards staging data from here on.
            log.w { "completed profile save has pending staging cleanup" }
            return Outcome.Ok(Unit)
        }
        return cleaned
    }

    /** Used after deletion restored its row, or a row commit itself was refused. */
    suspend fun abandon(id: ProfileId): Outcome<Unit> = finish(id)

    suspend fun recover(): Outcome<Unit> {
        for (key in kv.keys(ProfileKeys.TRANSACTION)) {
            val id = ProfileId(key.removePrefix(ProfileKeys.TRANSACTION))
            val journal = read(id) ?: return broken()
            val phase = journal["phase"] as? String ?: return broken()
            when {
                phase == "done" || phase == "preparing" -> Unit
                journal["kind"] == "save" -> {
                    val restored = rollbackSave(id, journal)
                    if (restored is Outcome.Failed) return restored
                }
                journal["kind"] == "delete" -> {
                    val rows = StoredProfileJson.rows(kv.string(ProfileKeys.PROFILES)) ?: return broken()
                    // A row still present means removal never committed, or compensation kept the server.
                    if (rows.any { (it as? Map<*, *>)?.get("id") == id.raw }) {
                        journal["phase"] = "done"
                        val marked = write(id, journal)
                        if (marked is Outcome.Failed) return marked
                        val discarded = discard(id, hasBackup = false)
                        if (discarded is Outcome.Failed) return discarded
                        continue
                    }
                    val names = (journal["names"] as? List<*>)?.filterIsInstance<String>() ?: return broken()
                    for (name in names) {
                        val deleted = secrets.delete(name)
                        if (deleted is Outcome.Failed) return deleted
                        when (val left = secrets.names(name)) {
                            is Outcome.Failed -> return left
                            is Outcome.Ok -> if (name in left.value) return Outcome.Failed(Failure.SecretUnavailable())
                        }
                    }
                    val removed = kv.commit(lanes.app) {
                        putString(ProfileKeys.PROFILES, StoredProfileJson.encodeRows(rows.filterNot { (it as? Map<*, *>)?.get("id") == id.raw }))
                        if (kv.string(ProfileKeys.ACTIVE) == id.raw) remove(ProfileKeys.ACTIVE)
                    }
                    if (removed is Outcome.Failed) return removed
                }
                else -> return broken()
            }
            journal["phase"] = "done"
            val marked = write(id, journal)
            if (marked is Outcome.Failed) return marked
            val discarded = discard(id, journal["kind"] == "save")
            if (discarded is Outcome.Failed) return discarded
        }
        return Outcome.Ok(Unit)
    }

    private suspend fun rollbackSave(id: ProfileId, journal: Map<String, Any?>): Outcome<Unit> {
        val name = journal["name"] as? String ?: return broken()
        val current = StoredProfileJson.rows(kv.string(ProfileKeys.PROFILES)).orEmpty()
        val rows = current.filterNot { (it as? Map<*, *>)?.get("id") == id.raw }.toMutableList()
        (journal["row"] as? Map<*, *>)?.let { rows.add(((journal["index"] as? Long)?.toInt() ?: rows.size).coerceIn(0, rows.size), it) }
        val metadata = kv.commit(lanes.app) {
            if (journal["profilesPresent"] == false && rows.isEmpty()) remove(ProfileKeys.PROFILES)
            else putString(ProfileKeys.PROFILES, StoredProfileJson.encodeRows(rows))
            putOrRemove(ProfileKeys.cleartextOk(id), journal["cleartext"] as? String)
            putOrRemove(ProfileKeys.certificatePin(id), journal["pin"] as? String)
        }
        if (metadata is Outcome.Failed) return metadata
        return if (journal["hadSecret"] == true) {
            val previous = when (val read = secrets.read(backup(id))) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value ?: return Outcome.Failed(Failure.SecretUnavailable())
            }
            secrets.write(name, previous)
        } else {
            secrets.delete(name)
        }
    }

    private fun snapshot(id: ProfileId): MutableMap<String, Any?> = linkedMapOf(
        "version" to 1L,
        "profilesPresent" to (kv.get(ProfileKeys.PROFILES) != null),
        "index" to StoredProfileJson.rows(kv.string(ProfileKeys.PROFILES))?.indexOfFirst { (it as? Map<*, *>)?.get("id") == id.raw }?.toLong(),
        "row" to StoredProfileJson.rows(kv.string(ProfileKeys.PROFILES))?.firstOrNull { (it as? Map<*, *>)?.get("id") == id.raw },
        "cleartext" to kv.string(ProfileKeys.cleartextOk(id)),
        "pin" to kv.string(ProfileKeys.certificatePin(id)),
    )

    private fun read(id: ProfileId): MutableMap<String, Any?>? {
        val parsed = kv.string(key(id))?.let(DartJson::decodeOrNull) as? Map<*, *> ?: return null
        if (parsed["version"] != 1L || parsed.keys.any { it !is String }) return null
        val kind = parsed["kind"]
        if (kind !in setOf("save", "delete") || parsed["phase"] !in setOf("preparing", "ready", "done")) return null
        if (kind == "save" && parsed["name"] !in setOf(ProfileKeys.password(id), ProfileKeys.codexToken(id))) return null
        if (kind == "delete") {
            val names = parsed["names"] as? List<*> ?: return null
            if (names.any { name -> name !is String || ProfileKeys.SECRET_PREFIXES.keys.none { name == it + id.raw || name.startsWith(it + id.raw + ".") } }) return null
        }
        return parsed.entries.associateTo(LinkedHashMap()) { (key, value) -> key as String to value }
    }

    private suspend fun write(id: ProfileId, journal: Map<String, Any?>): Outcome<Unit> =
        kv.commit(lanes.app) { putString(key(id), DartJson.encode(journal)) }

    private suspend fun discard(id: ProfileId, hasBackup: Boolean): Outcome<Unit> {
        if (hasBackup) {
            val deleted = secrets.delete(backup(id))
            if (deleted is Outcome.Failed) return deleted
        }
        return kv.commit(lanes.app) { remove(key(id)) }
    }
}
