package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import kotlin.time.Clock

/**
 * Flutter-compatible opening labels only. Never status, permissions, capabilities or
 * resumable-session proof.
 */
@Inject
@SingleIn(AppScope::class)
class NativeSessionInventory(
    private val stores: PreviewStores,
    private val redactor: Redactor,
    private val clock: Clock,
) {
    private fun location(profile: StoredProfile): Pair<String?, String?> {
        if (profile.usesAgentSocket) return profile.codexDirectory to null
        val text = (stores.prefs.get("oc.location.${profile.id.raw}") as? PrefValue.Str)?.value
        val value = text?.let(DartJson::decodeOrNull) as? Map<*, *>
        return (value?.get("directory") as? String) to (value?.get("workspace") as? String)
    }

    private fun scope(profile: StoredProfile): String {
        val (directory, workspace) = location(profile)
        val backend =
            when (profile.backend) {
                BackendKind.OpenCode2 -> "openCode"
                BackendKind.Paseo -> "paseo"
                BackendKind.Codex -> "codex"
            }
        return DartJson.sha256Hex(
            DartJson.encode(
                listOf(backend, "v2", profile.baseUrl, profile.username, directory, workspace)
            )
        )
    }

    fun read(profile: StoredProfile): List<Session> {
        val raw =
            (stores.prefs.get(PREFIX + profile.id.raw) as? PrefValue.Str)?.value
                ?: return emptyList()
        if (raw.length > MAX_BYTES || raw.encodeToByteArray().size > MAX_BYTES) return emptyList()
        val value = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return emptyList()
        if ((value["v"] as? Number)?.toInt() != 1 || value["scope"] != scope(profile))
            return emptyList()
        val rows = value["rows"] as? List<*> ?: return emptyList()
        if (rows.size > MAX_SESSIONS) return emptyList()
        val directory = location(profile).first
        val result = ArrayList<Session>()
        for (entry in rows) {
            val row = entry as? Map<*, *> ?: return emptyList()
            val id = row["id"] as? String ?: return emptyList()
            val title = row["title"] as? String ?: return emptyList()
            val updated = (row["updated"] as? Number)?.toLong() ?: return emptyList()
            if (id.isEmpty() || id.length > 512) return emptyList()
            result +=
                Session(
                    id = SessionId(id),
                    title = redactor.diagnostics(title, 256),
                    directory = directory,
                    time = SessionTime(updatedMs = updated),
                )
        }
        return result
    }

    /** User-visible list time only, used by Paseo's no-restamp idle guard. */
    fun savedListTime(profile: StoredProfile, daemonSessionId: String): Long? =
        read(profile)
            .firstOrNull {
                it.id.raw == daemonSessionId || it.id.raw.substringAfterLast(':') == daemonSessionId
            }
            ?.time
            ?.updatedMs

    suspend fun save(profile: StoredProfile, sessions: List<Session>, current: () -> Boolean) {
        val rows = ArrayList<Map<String, Any>>()
        var used = 0
        for (session in sessions.take(MAX_SESSIONS)) {
            if (session.id.raw.isEmpty() || session.id.raw.length > 512) continue
            val row =
                mapOf(
                    "id" to session.id.raw,
                    "title" to redactor.diagnostics(session.title.orEmpty(), 256),
                    "updated" to (session.time?.updatedMs ?: session.time?.createdMs ?: 0L),
                )
            val bytes = DartJson.encode(row).encodeToByteArray().size + 1
            if (used + bytes > MAX_BYTES - 512) break
            rows += row
            used += bytes
        }
        val encoded =
            DartJson.encode(
                mapOf(
                    "v" to 1,
                    "scope" to scope(profile),
                    "at" to clock.now().toEpochMilliseconds(),
                    "rows" to rows,
                )
            )
        if (encoded.encodeToByteArray().size > MAX_BYTES) return
        if (!current()) return
        stores.prefs.commit(stores.lanes.forProfile(profile.id)) {
            if (current() && stores.profiles.profile(profile.id)?.profile == profile)
                putString(PREFIX + profile.id.raw, encoded)
        }
    }

    companion object {
        const val PREFIX = "oc.sessionInventory."
        private const val MAX_SESSIONS = 100
        private const val MAX_BYTES = 65_536
    }
}
