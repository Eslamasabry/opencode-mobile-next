package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.dart.DartJson

/** One armed occurrence as the OS scheduler carries it (Flutter `ScheduledPromptStore.Entry`). */
public data class ArmedEntry(val profileId: String, val scheduleId: String, val revision: Int, val dueAtMs: Long) {
    public val occurrence: String get() = ScheduleIds.occurrence(scheduleId, revision, dueAtMs)

    /** Unique OS work name. Native names differ from the Flutter-era `oc.schedule.*` so a stale Flutter job never wins. */
    public val workName: String get() = "oc.nschedule.$profileId.$occurrence"

    public val digest: Boolean get() = scheduleId == ScheduleIds.DIGEST

    public companion object {
        private val idPattern = Regex("^[A-Za-z0-9_.-]{1,128}$")

        /** Routing data from a work request; null when any part is unsafe. */
        public fun orNull(profileId: String, scheduleId: String, revision: Int, dueAtMs: Long): ArmedEntry? =
            if (idPattern.matches(profileId) && idPattern.matches(scheduleId) && revision >= 0 && dueAtMs > 0) {
                ArmedEntry(profileId, scheduleId, revision, dueAtMs)
            } else {
                null
            }
    }
}

public enum class Claim { New, Recover, Duplicate, Stale }

/**
 * Permanent occurrence tombstones (Flutter ScheduledPromptClaims.kt, same keys and JSON): an occurrence is sent at
 * most once. A claim found unfinished after a crash is a [Claim.Recover], which can only record "interrupted",
 * never send. The armed index (`oc.scheduleIndex.<profile>`) is what may be claimed at all.
 */
public class ScheduleClaims(private val backend: ScheduleBackend, private val nowMs: () -> Long) {
    public companion object {
        public const val MAX_CLAIMS: Int = 256
        private const val ATTEMPTS = 20
    }

    public suspend fun index(profileId: String): List<ArmedEntry> {
        val raw = backend.read(ScheduleKeys.index(profileId)) ?: return emptyList()
        val rows = DartJson.decodeOrNull(raw) as? List<*> ?: return emptyList()
        return rows.mapNotNull { row ->
            val map = row as? Map<*, *> ?: return@mapNotNull null
            val id = map["id"] as? String ?: return@mapNotNull null
            val revision = (map["revision"] as? Long)?.toInt() ?: return@mapNotNull null
            val due = map["dueAtMs"] as? Long ?: return@mapNotNull null
            ArmedEntry.orNull(profileId, id, revision, due)
        }
    }

    /** Replaces the armed index with exactly [entries]. */
    public suspend fun replaceIndex(profileId: String, entries: List<ArmedEntry>) {
        val key = ScheduleKeys.index(profileId)
        val encoded =
            DartJson.encode(
                entries.map { linkedMapOf("id" to it.scheduleId, "revision" to it.revision.toLong(), "dueAtMs" to it.dueAtMs) },
            )
        repeat(ATTEMPTS) {
            val raw = backend.read(key)
            if (raw == encoded || backend.compareAndSet(key, raw, encoded)) return
        }
        throw ScheduleStoreException(ScheduleCode.StorageBusy)
    }

    public suspend fun claim(entry: ArmedEntry): Claim {
        val current = index(entry.profileId).firstOrNull { it.scheduleId == entry.scheduleId }
        if (current == null || current.revision != entry.revision || current.dueAtMs != entry.dueAtMs) return Claim.Stale
        return update(entry.profileId) { claims ->
            val existing = claims[entry.occurrence] as? Map<*, *>
            when {
                existing == null -> {
                    claims[entry.occurrence] = linkedMapOf("at" to nowMs(), "finished" to false)
                    trim(claims)
                    Claim.New
                }
                existing["finished"] == true -> Claim.Duplicate
                else -> {
                    // A fresh recovery reservation can only write interrupted history, never send.
                    claims[entry.occurrence] = linkedMapOf("at" to nowMs(), "finished" to false)
                    Claim.Recover
                }
            }
        } ?: Claim.Stale
    }

    /** Marks a claimed occurrence finished (sent, failed, skipped or interrupted): it can never run again. */
    public suspend fun settle(entry: ArmedEntry) {
        update(entry.profileId) { claims ->
            val existing = claims[entry.occurrence] as? Map<*, *> ?: return@update Unit
            claims[entry.occurrence] = linkedMapOf("at" to (existing["at"] ?: nowMs()), "finished" to true)
        }
    }

    /** Releases an unsent claim (the clock moved back before the run was due): the occurrence may run later. */
    public suspend fun release(entry: ArmedEntry) {
        update(entry.profileId) { claims ->
            val existing = claims[entry.occurrence] as? Map<*, *> ?: return@update Unit
            if (existing["finished"] != true) claims.remove(entry.occurrence)
        }
    }

    public suspend fun finished(entry: ArmedEntry): Boolean {
        val raw = backend.read(ScheduleKeys.claims(entry.profileId)) ?: return false
        val claims = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return false
        return (claims[entry.occurrence] as? Map<*, *>)?.get("finished") == true
    }

    private fun trim(claims: LinkedHashMap<String, Any?>) {
        if (claims.size <= MAX_CLAIMS) return
        claims.entries
            .sortedBy { ((it.value as? Map<*, *>)?.get("at") as? Long) ?: 0L }
            .take(claims.size - MAX_CLAIMS)
            .map { it.key }
            .forEach(claims::remove)
    }

    private suspend fun <T> update(profileId: String, change: (LinkedHashMap<String, Any?>) -> T): T? {
        val key = ScheduleKeys.claims(profileId)
        repeat(ATTEMPTS) {
            val raw = backend.read(key)
            val decoded = raw?.let { DartJson.decodeOrNull(it) as? Map<*, *> }
            if (raw != null && decoded == null) throw ScheduleStoreException(ScheduleCode.StorageCorrupt)
            val claims = LinkedHashMap<String, Any?>()
            decoded?.forEach { (k, v) -> if (k is String) claims[k] = v }
            val result = change(claims)
            val encoded = DartJson.encode(claims)
            if (encoded == raw || backend.compareAndSet(key, raw, encoded)) return result
        }
        throw ScheduleStoreException(ScheduleCode.StorageBusy)
    }
}
