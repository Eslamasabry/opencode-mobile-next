package io.github.eslamasabry.oc.feature.chats.inventory

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// Port of lib/state/session_inventory_cache.dart.

/**
 * The preference file under the cache (the app binds it to its `KeyValueStore` and the profile's lane). [read] sees
 * acknowledged values only; [write] answers true only when the platform acknowledged the commit.
 */
public interface PreviewStorage {
    public fun read(key: String): String?

    public suspend fun write(key: String, value: String): Boolean
}

public data class SessionPreview(val id: SessionId, val title: String, val updatedMs: Long)

/** Last-known labels for a read-only opening shell, never live session truth. */
public class SessionInventoryPreview(public val fetchedAtMs: Long, public val sessions: ImmutableList<SessionPreview>)

/**
 * One bounded location per profile under `oc.sessionInventory.<profileId>` (swept with the profile). No prompts, auth,
 * status, permission or capability evidence is stored. A preview answers only for its exact profile and scope; a
 * corrupt, oversized or foreign one is treated as empty; a write whose [save] `isCurrent` turned false (a revoked
 * connection, a deleted profile) is skipped. [redact] masks known secrets in titles before they are stored.
 */
public class SessionInventoryCache(
    private val storage: PreviewStorage,
    private val clock: () -> Long,
    private val redact: (String) -> String = { it },
) {
    private class Decoded(val raw: String, val scope: String, val preview: SessionInventoryPreview)

    private val decoded = HashMap<ProfileId, Decoded>()
    private val lanes = HashMap<ProfileId, TurnLane>()

    public fun read(profile: ProfileId, scope: String): SessionInventoryPreview? {
        val raw = storage.read(keyFor(profile)) ?: return null
        if (raw.length > MAX_BYTES) return null
        val previous = decoded[profile]
        // The scope stays part of the owner: a changed location never reuses a snapshot of the same profile.
        if (previous != null && previous.raw == raw) return previous.preview.takeIf { previous.scope == scope }
        val json = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
        if (json["v"] != 1L || json["scope"] != scope) return null
        val at = json["at"] as? Long ?: return null
        val rows = json["rows"] as? List<*> ?: return null
        if (rows.size > MAX_SESSIONS) return null
        val sessions = rows.map { row ->
            val map = row as? Map<*, *> ?: return null
            SessionPreview(
                SessionId(map["id"] as? String ?: return null),
                map["title"] as? String ?: return null,
                map["updated"] as? Long ?: return null,
            )
        }
        val preview = SessionInventoryPreview(at, sessions.toImmutableList())
        decoded[profile] = Decoded(raw, scope, preview)
        return preview
    }

    /**
     * Snapshots plain values now (a deferred write never reads another connection's sessions), then writes after
     * the profile's earlier writes, only while [isCurrent]. Display copy is truncated, server ids never.
     */
    public suspend fun save(
        profile: ProfileId,
        scope: String,
        sessions: List<Session>,
        isCurrent: () -> Boolean,
        fetchedAtMs: Long? = null,
    ) {
        val rows = ArrayList<Map<String, Any>>()
        var rowBytes = 0
        for (session in sessions.take(MAX_SESSIONS)) {
            if (session.id.raw.isEmpty() || session.id.raw.length > 512) continue
            val row = linkedMapOf<String, Any>(
                "id" to session.id.raw,
                "title" to firstCodePoints(redact(session.title ?: ""), 256),
                "updated" to (session.time?.updatedMs ?: session.time?.createdMs ?: 0L),
            )
            val bytes = DartJson.utf8(DartJson.encode(row)).size
            if (rowBytes + bytes > MAX_BYTES - 512) break
            rowBytes += bytes + 1
            rows += row
        }
        val raw = DartJson.encode(linkedMapOf("v" to 1L, "scope" to scope, "at" to (fetchedAtMs ?: clock()), "rows" to rows))
        lanes.getOrPut(profile) { TurnLane() }.run {
            if (!isCurrent()) return@run
            storage.write(keyFor(profile), raw)
            forget(profile)
        }
    }

    public fun forget(profile: ProfileId) {
        decoded.remove(profile)
    }

    public companion object {
        public const val MAX_SESSIONS: Int = 100
        public const val MAX_BYTES: Int = 65536

        public fun keyFor(profile: ProfileId): String = "oc.sessionInventory.${profile.raw}"

        /**
         * Dart `SessionInventoryCache.scopeFor`: SHA-256 of `jsonEncode([backend, flavor, baseUrl, username, directory,
         * workspace])`, with the Dart enum names (`opencode`, `v2`, …) so a preview written by the Flutter app still
         * matches.
         */
        public fun scopeFor(
            backend: String,
            flavor: String,
            baseUrl: String,
            username: String?,
            directory: String?,
            workspace: String?,
        ): String = DartJson.sha256Hex(DartJson.encode(listOf(backend, flavor, baseUrl, username, directory, workspace)))
    }
}

/** Dart `String.fromCharCodes(s.runes.take(n))`: never splits a surrogate pair. */
internal fun firstCodePoints(text: String, count: Int): String {
    var i = 0
    var taken = 0
    while (i < text.length && taken < count) {
        i += if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
        taken++
    }
    return text.substring(0, i)
}
