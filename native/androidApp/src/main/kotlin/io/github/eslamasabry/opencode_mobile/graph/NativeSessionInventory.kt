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
                    // The conversation's own folder when the row kept it (an agent's project), else the list's.
                    directory = (row["dir"] as? String)?.takeIf { it.isNotBlank() }?.take(1024) ?: directory,
                    time = SessionTime(updatedMs = updated),
                )
        }
        return result
    }

    /**
     * The agent each saved row of an agents server was run by (id, label), when the row kept it: a saved agent
     * conversation shows its own agent's mark and name, never OpenCode's, before its server answers.
     */
    fun agents(profile: StoredProfile): Map<String, Pair<String, String?>> {
        val raw = (stores.prefs.get(PREFIX + profile.id.raw) as? PrefValue.Str)?.value ?: return emptyMap()
        return richRows(raw, scope(profile)).mapNotNull { (id, row) ->
            (row["agent"] as? String)
                ?.takeIf { it.isNotBlank() && it != OPENCODE }
                ?.let { id to (it to (row["agentLabel"] as? String)?.takeIf { label -> label.isNotBlank() }) }
        }.toMap()
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
        val scope = scope(profile)
        val rich = richRows((stores.prefs.get(PREFIX + profile.id.raw) as? PrefValue.Str)?.value, scope)
        var used = 0
        // The saved list counts what home counts: top-level, not archived (sub-conversations and archived ones made the
        // first paint say 45 where the live read said 42).
        for (session in sessions.filter { it.parentId == null && it.time?.archivedMs == null }.take(MAX_SESSIONS)) {
            if (session.id.raw.isEmpty() || session.id.raw.length > 512) continue
            val row =
                withRich(
                    rich,
                    mapOf(
                        "id" to session.id.raw,
                        "title" to redactor.diagnostics(session.title.orEmpty(), 256),
                        "updated" to (session.time?.updatedMs ?: session.time?.createdMs ?: 0L),
                    ) + described(profile.backend, session),
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
                    "scope" to scope,
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

        /** The fields a plain list save writes; everything else on a row is the home's (project, agent, preview, …). */
        private val PLAIN = setOf("id", "title", "updated")

        /**
         * Cache-first: the saved rows of [raw] by id when it was saved for [scope]. Three writers share this key (the
         * home's feed with its marks and last-message line, the runtime's list refresh and All conversations); a plain
         * save keeps the home's richer fields of each row it rewrites, so the home's first frame keeps them.
         */
        fun richRows(raw: String?, scope: String): Map<String, Map<*, *>> {
            if (raw == null || raw.length > MAX_BYTES) return emptyMap()
            val doc = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return emptyMap()
            if (doc["scope"] != scope) return emptyMap()
            return (doc["rows"] as? List<*>).orEmpty().mapNotNull { row ->
                (row as? Map<*, *>)?.let { r -> (r["id"] as? String)?.let { it to r } }
            }.toMap()
        }

        fun withRich(rich: Map<String, Map<*, *>>, row: Map<String, Any>): Map<String, Any> {
            val saved = rich[row["id"]] ?: return row
            val extra = LinkedHashMap<String, Any>()
            // What this save knows itself (its folder, its agent) wins over what an earlier save kept.
            for ((k, v) in saved) if (k is String && k !in PLAIN && k !in row && v != null) extra[k] = v
            return if (extra.isEmpty()) row else LinkedHashMap(row).apply { putAll(extra) }
        }

        /**
         * What a plain list save can say of [session] besides its id, title and time: its own folder, and on an
         * agents server (Paseo) the agent that runs it. Without them a saved agent conversation came back as an
         * OpenCode one in the list's root folder ("projects") until its server answered.
         */
        fun described(backend: BackendKind, session: Session): Map<String, Any> =
            buildMap {
                session.directory?.takeIf { it.isNotBlank() }?.let { put("dir", it.take(1024)) }
                if (backend == BackendKind.Paseo)
                    PhoneConversationRules.agentOf(session)?.let { (id, label) ->
                        put("agent", id.take(128))
                        label?.let { put("agentLabel", it.take(128)) }
                    }
            }

        private const val OPENCODE = "opencode"
        private const val MAX_SESSIONS = 100
        private const val MAX_BYTES = 65_536
    }
}
