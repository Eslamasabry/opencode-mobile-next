package io.github.eslamasabry.oc.engine.runtime.setupassistant

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/**
 * Only acknowledged metadata is durable; restore handles, config and locations never enter this
 * record.
 */
public data class SetupAuditRecord(
    val id: String,
    val action: String,
    val at: Instant,
    val operation: String? = null,
)

/** One process owner per profile. Close stops admission before draining a pending disk write. */
public class SetupAuditOwners(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val redactor: Redactor,
    private val clock: Clock,
    private val present: (ProfileId) -> Boolean,
) {
    private val owners = AtomicReference<Map<ProfileId, SetupAudit>>(emptyMap())

    public fun forProfile(id: ProfileId): SetupAudit {
        while (true) {
            val previous = owners.load()
            previous[id]?.let {
                return it
            }
            val next = SetupAudit(store, lanes, id, redactor, clock) { present(id) }
            if (owners.compareAndSet(previous, previous + (id to next))) return next
        }
    }

    public fun stop(id: ProfileId) {
        forProfile(id).stopAdmission()
    }

    public fun reopen(id: ProfileId) {
        while (true) {
            val previous = owners.load()
            if (owners.compareAndSet(previous, previous - id)) return
        }
    }

    public suspend fun close(id: ProfileId) {
        forProfile(id).close()
    }
}

public class SetupAudit
internal constructor(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val profile: ProfileId,
    private val redactor: Redactor,
    private val clock: Clock,
    private val present: () -> Boolean,
) {
    private val lock = Mutex()
    @Volatile private var closed = false
    public val key: String = "oc.setupAudit.${profile.raw}"

    public fun records(): ImmutableList<SetupAuditRecord>? {
        if (closed || !present() || redactor.containsSecret(profile.raw)) return null
        val raw = store.string(key) ?: return emptyList<SetupAuditRecord>().toImmutableList()
        if (raw.length > 65_536) return null
        val array =
            try {
                Json.parseToJsonElement(raw) as? JsonArray
            } catch (_: IllegalArgumentException) {
                null
            } ?: return null
        val result = array.map { item ->
            val row = item as? JsonObject ?: return null
            if (row.keys.any { it !in setOf("id", "action", "at", "operation") }) return null
            val id = row["id"].string() ?: return null
            val action = row["action"].string() ?: return null
            val operation = row["operation"].string()
            if (row["operation"] != null && row["operation"] != JsonNull && operation == null)
                return null
            val timestamp =
                row["at"].string()?.takeIf { it.length <= 40 && it.endsWith('Z') } ?: return null
            val at =
                try {
                    Instant.parse(timestamp)
                } catch (_: IllegalArgumentException) {
                    return null
                }
            if (!valid(id, action, operation)) return null
            SetupAuditRecord(id, action, at, operation)
        }
        return result.takeLast(100).toImmutableList()
    }

    public suspend fun append(
        id: String,
        action: String,
        operation: String? = null,
    ): Outcome<Unit> {
        if (closed || !present()) return unavailable()
        return lock.withLock {
            if (closed || !present()) return@withLock unavailable()
            if (!valid(id, action, operation))
                return@withLock Outcome.Failed(Failure.StorageRefused())
            val previous = records() ?: return@withLock Outcome.Failed(Failure.StorageRefused())
            val next =
                (previous + SetupAuditRecord(id, action, clock.now(), operation)).takeLast(100)
            val encoded =
                JsonArray(
                        next.map { record ->
                            buildJsonObject {
                                put("id", record.id)
                                put("action", record.action)
                                put("at", record.at.toString())
                                record.operation?.let { put("operation", it) }
                            }
                        }
                    )
                    .toString()
            store.commit(lanes.forProfile(profile)) { putString(key, encoded) }
        }
    }

    public fun stopAdmission() {
        closed = true
    }

    public suspend fun close() {
        closed = true
        lock.withLock {}
    }

    private fun valid(id: String, action: String, operation: String?): Boolean =
        ID.matches(id) &&
            !redactor.containsSecret(id) &&
            action in ACTIONS &&
            (operation == null || operation in OPERATIONS)

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing))

    private companion object {
        val ID =
            Regex(
                "^(?:(?:proposal|undo)-[0-9]+|[0-9a-f]{48}|(?:proposal-)?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$"
            )
        val ACTIONS = setOf("proposed", "pending", "accepted", "verified", "uncertain", "rejected")
        val OPERATIONS = setOf("apply", "undo", "review")
    }
}

internal fun JsonElement?.string(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content
