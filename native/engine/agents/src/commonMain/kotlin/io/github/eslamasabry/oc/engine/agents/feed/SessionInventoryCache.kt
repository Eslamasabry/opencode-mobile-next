package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One last-known label (Dart `SessionPreview`): never prompts, status, permissions or capability evidence. */
public data class SessionPreview(val id: SessionId, val title: String, val updatedMs: Long) {
    /** The label as a listing row, for the read-only opening list before the server answers. */
    public fun toSession(): Session = Session(id = id, title = title.ifEmpty { null }, time = SessionTime(updatedMs = updatedMs))
}

/** Last-known labels for a read-only opening list, never live session truth (B-STATE-103). [sessions] cannot be written. */
public class SessionInventoryPreview(public val fetchedAtMs: Long, public val sessions: ImmutableList<SessionPreview>)

/**
 * Where the cache lives: one string per profile under [SessionInventoryCache.keyFor]. On Android the adopted Flutter
 * preference file, so a migrated `oc.sessionInventory.<id>` reads as is and the profile sweep (`oc.<what>.<id>`)
 * removes it with the profile (B-STATE-104).
 */
public interface SessionInventoryStorage {
    public fun read(key: String): String?

    public suspend fun write(key: String, raw: String): Outcome<Unit>
}

/**
 * Port of Dart `SessionInventoryCache` (lib/state/session_inventory_cache.dart): one bounded location per profile.
 * A read is exact to the profile and its location scope; a corrupt, foreign, oversized or other-version value reads as
 * nothing. A save whose owner is no longer current (deleted profile, switched location) writes nothing.
 */
public class SessionInventoryCache(
    private val storage: SessionInventoryStorage,
    /** Display copy is masked before it is stored (the app passes the redactor's `redact`). */
    private val mask: (String) -> String = { it },
) {
    private val writes = Mutex()
    private val decoded = mutableMapOf<String, Triple<String, String, SessionInventoryPreview>>()

    public fun read(profileId: String, scope: String): SessionInventoryPreview? {
        val raw = storage.read(keyFor(profileId)) ?: return null
        if (raw.length > MAX_BYTES) return null
        decoded[profileId]?.let { (seenRaw, seenScope, preview) ->
            // Same stored text: the same object (one decode); a changed location never reuses it.
            if (seenRaw == raw) return preview.takeIf { seenScope == scope }
        }
        val json = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
        if ((json["v"] as? Long) != 1L || json["scope"] != scope) return null
        val at = json["at"] as? Long ?: return null
        val rows = json["rows"] as? List<*> ?: return null
        if (rows.size > MAX_SESSIONS) return null
        val sessions = rows.map { row ->
            val map = row as? Map<*, *> ?: return null
            val id = map["id"] as? String ?: return null
            if (id.isEmpty()) return null
            SessionPreview(SessionId(id), map["title"] as? String ?: return null, map["updated"] as? Long ?: return null)
        }
        val preview = SessionInventoryPreview(at, sessions.toImmutableList())
        decoded[profileId] = Triple(raw, scope, preview)
        return preview
    }

    /**
     * Persists at most [MAX_SESSIONS] labels within [MAX_BYTES]. Values are captured now; [isCurrent] is asked again
     * just before the write, so a profile deleted or switched meanwhile is not written back (no resurrection).
     */
    public suspend fun save(
        profileId: String,
        scope: String,
        sessions: List<Session>,
        fetchedAtMs: Long,
        isCurrent: () -> Boolean,
    ): Outcome<Unit> {
        val rows = mutableListOf<Map<String, Any>>()
        var bytes = 0
        for (session in sessions.take(MAX_SESSIONS)) {
            val id = session.id.raw
            if (id.isEmpty() || id.length > 512) continue
            val title = mask(session.title.orEmpty()).let { t -> t.codePointStrings().joinFirst(MAX_TITLE) }
            val row = linkedMapOf<String, Any>(
                "id" to id,
                "title" to title,
                "updated" to (session.time?.updatedMs ?: session.time?.createdMs ?: 0L),
            )
            val size = DartJson.utf8(DartJson.encode(row)).size
            if (bytes + size > MAX_BYTES - 512) break
            bytes += size + 1
            rows += row
        }
        val raw = DartJson.encode(linkedMapOf("v" to 1L, "scope" to scope, "at" to fetchedAtMs, "rows" to rows))
        return writes.withLock {
            if (!isCurrent()) return@withLock Outcome.Ok(Unit)
            storage.write(keyFor(profileId), raw).also { decoded.remove(profileId) }
        }
    }

    /** Drops the decoded copy (profile deleted or reloaded). */
    public fun forget(profileId: String) {
        decoded.remove(profileId)
    }

    public companion object {
        public const val MAX_SESSIONS: Int = 100
        public const val MAX_BYTES: Int = 65536
        private const val MAX_TITLE = 256

        public fun keyFor(profileId: String): String = "oc.sessionInventory.$profileId"

        /** Dart `scopeFor`: sha256 of `jsonEncode([backend, flavor, baseUrl, username, directory, workspace])`. */
        public fun scopeFor(
            backend: String,
            flavor: String,
            baseUrl: String,
            username: String?,
            directory: String?,
            workspace: String?,
        ): String = DartJson.sha256Hex(DartJson.encode(listOf(backend, flavor, baseUrl, username, directory, workspace)))

        private fun String.codePointStrings(): List<String> {
            val out = mutableListOf<String>()
            var i = 0
            while (i < length) {
                val step = if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
                out += substring(i, i + step)
                i += step
            }
            return out
        }

        private fun List<String>.joinFirst(n: Int): String = asSequence().take(n).joinToString("")
    }
}
