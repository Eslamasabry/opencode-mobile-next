package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationPreviews
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NoConversationPreviews
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Flutter's bounded read-only labels, with the exact origin + location fingerprint and the profile
 * writer fence.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoConversationPreviews::class])
@Inject
class StoredConversationPreviews(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
    private val clock: Clock,
) : ConversationPreviews {
    private val writes = Mutex()

    private class Decoded(val raw: String, val scope: String, val rows: List<Session>)

    private val decoded = mutableMapOf<String, Decoded>()

    override fun scope(server: ConversationServer): String? {
        val graph = registry.live.value[server.profile] ?: return null
        if (graph.session.server !== server.gateway) return null
        val saved = stores.profiles.profile(server.profile) ?: return null
        if (saved.toServerProfile(graph.profile.managedOnPhone) != graph.profile) return null
        val p = saved.profile
        val backend =
            when (p.backend) {
                BackendKind.OpenCode2 -> "openCode"
                BackendKind.Paseo -> "paseo"
                BackendKind.Codex -> "codex"
            }
        val location = server.gateway.location.value
        return DartJson.sha256Hex(
            DartJson.encode(
                listOf(backend, "v2", p.baseUrl, p.username, location.directory, location.workspace)
            )
        )
    }

    override fun read(server: ConversationServer): List<Session> {
        val scope = scope(server) ?: return emptyList()
        if (!stores.lanes.forProfile(server.profile).isAdmitting) return emptyList()
        val raw = (stores.prefs.get(key(server)) as? PrefValue.Str)?.value ?: return emptyList()
        if (raw.encodeToByteArray().size > MAX_BYTES) return emptyList()
        decoded[server.profile.raw]?.let { cached ->
            if (cached.raw == raw) return cached.rows.takeIf { cached.scope == scope }.orEmpty()
        }
        val doc = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return emptyList()
        if (doc["v"] != 1L || doc["scope"] != scope || doc["at"] !is Long) return emptyList()
        val rows = doc["rows"] as? List<*> ?: return emptyList()
        if (rows.size > MAX_ROWS) return emptyList()
        val result = ArrayList<Session>()
        for (item in rows) {
            val row = item as? Map<*, *> ?: return emptyList()
            val id = row["id"] as? String ?: return emptyList()
            val title = row["title"] as? String ?: return emptyList()
            val updated = row["updated"] as? Long ?: return emptyList()
            if (id.isEmpty() || id.length > 512) return emptyList()
            result += Session(SessionId(id), title = title, time = SessionTime(updatedMs = updated))
        }
        val snapshot = result.toImmutableList()
        decoded[server.profile.raw] = Decoded(raw, scope, snapshot)
        return snapshot
    }

    override suspend fun save(
        server: ConversationServer,
        scope: String,
        sessions: List<Session>,
    ): Outcome<Unit> {
        // Capture primitive labels before suspending; no deferred read of another connection's
        // inventory.
        val rows = mutableListOf<Map<String, Any>>()
        var size = 0
        for (session in sessions.take(MAX_ROWS)) {
            if (session.id.raw.isEmpty() || session.id.raw.length > 512) continue
            val row =
                linkedMapOf<String, Any>(
                    "id" to session.id.raw,
                    "title" to redactor.redact(session.title.orEmpty()).take(256),
                    "updated" to (session.time?.updatedMs ?: session.time?.createdMs ?: 0L),
                )
            val bytes = DartJson.encode(row).encodeToByteArray().size
            if (size + bytes > MAX_BYTES - 512) break
            size += bytes + 1
            rows += row
        }
        val raw =
            DartJson.encode(
                linkedMapOf(
                    "v" to 1,
                    "scope" to scope,
                    "at" to clock.now().toEpochMilliseconds(),
                    "rows" to rows,
                )
            )
        return writes.withLock {
            if (scope(server) != scope || !stores.lanes.forProfile(server.profile).isAdmitting)
                return@withLock Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            val done =
                stores.prefs.commit(stores.lanes.forProfile(server.profile)) {
                    putString(key(server), raw)
                }
            decoded.remove(server.profile.raw)
            done
        }
    }

    private fun key(server: ConversationServer): String =
        "oc.sessionInventory." + server.profile.raw

    private companion object {
        const val MAX_ROWS = 100
        const val MAX_BYTES = 65_536
    }
}
