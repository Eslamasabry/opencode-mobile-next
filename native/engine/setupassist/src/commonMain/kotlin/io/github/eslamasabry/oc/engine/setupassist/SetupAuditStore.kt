package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

/** One metadata-only audit line: never configuration, credentials, server location or an Undo handle. */
public data class SetupAuditRecord(val id: String, val action: String, val at: String, val operation: String? = null)

/**
 * The app's audit owners: one writer per profile, shared by every setup controller (port of the Dart Expando).
 * Create once per app (or per preference store) and hand it to every [SetupController].
 */
public class SetupAuditRegistry(
    private val prefs: SetupPrefs,
    private val redactor: Redactor,
    private val clock: Clock,
    private val presence: ProfilePresence = ProfilePresence.fromProfilesKey(prefs),
) {
    private val owners = HashMap<String, SetupAuditStore>()

    /** The shared owner, or a [SetupFailureCode.Storage] failure for an empty or secret-looking id. */
    public fun forProfile(profileId: String): SetupOutcome<SetupAuditStore> {
        if (profileId.isEmpty() || redactor.containsSecret(profileId)) {
            return SetupOutcome.Failed(SetupFailure(SetupFailureCode.Storage, "Setup history is unavailable."))
        }
        return SetupOutcome.Ok(owners.getOrPut(profileId) { SetupAuditStore(prefs, profileId, redactor, clock, presence) })
    }

    /**
     * Stops admission before its first suspension, then drains writes already running. The closed owner stays
     * registered until restart so a new controller cannot reopen admission between the key sweep and profile removal.
     */
    public suspend fun closeProfile(profileId: String) {
        val owner = (forProfile(profileId) as? SetupOutcome.Ok)?.value ?: return
        owner.close()
        owner.drain()
    }
}

/** Port of lib/state/setup_audit_store.dart. Invalid stored text is never promoted and never overwritten. */
public class SetupAuditStore internal constructor(
    private val prefs: SetupPrefs,
    private val profileId: String,
    private val redactor: Redactor,
    private val clock: Clock,
    private val presence: ProfilePresence,
) {
    private val writes = Mutex()
    private var closed = false

    public val key: String get() = SetupProfileKeys.audit(profileId)

    internal fun close() {
        closed = true
    }

    internal suspend fun drain() {
        writes.withLock { }
    }

    private val present: Boolean get() = presence.isPresent(profileId)

    public val isAvailable: Boolean get() = !closed && present && readRecords() != null

    /** Read-only records, oldest first, at most [MAX_RECORDS]. Empty when closed, absent or unreadable. */
    public val records: List<SetupAuditRecord>
        get() = if (closed || !present) emptyList() else readRecords() ?: emptyList()

    private fun readRecords(): List<SetupAuditRecord>? {
        val raw = prefs.getString(key) ?: return emptyList()
        val decoded = try {
            Json.parseToJsonElement(raw)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (decoded !is JsonArray) return null
        val result = ArrayList<SetupAuditRecord>()
        for (record in decoded) {
            if (record !is JsonObject || record.keys.any { it !in RECORD_KEYS }) return null
            val id = record["id"].string()
            val action = record["action"].string()
            val at = record["at"].string()
            val operationElement = record["operation"]
            val operation = operationElement.string()
            if (id == null || !validId(id) || action !in ACTIONS || at == null || at.length > 40 ||
                (operationElement != null && operation !in OPERATIONS)
            ) {
                return null
            }
            val timestamp = parseUtc(at) ?: return null
            result.add(SetupAuditRecord(id, action!!, timestamp.toString(), operation))
        }
        return if (result.size > MAX_RECORDS) result.subList(result.size - MAX_RECORDS, result.size).toList() else result.toList()
    }

    private fun validId(id: String): Boolean = ID_PATTERN.matches(id) && !redactor.containsSecret(id)

    private fun admission(): SetupFailure? = when {
        closed || !present -> CLOSED
        readRecords() == null -> STORAGE
        else -> null
    }

    /** Appends one metadata line after every earlier append. Failures are values; nothing is fabricated on refusal. */
    public suspend fun append(id: String, action: String, operation: String? = null): SetupOutcome<Unit> {
        admission()?.let { return SetupOutcome.Failed(it) }
        if (!validId(id) || action !in ACTIONS || (operation != null && operation !in OPERATIONS)) {
            return SetupOutcome.Failed(STORAGE)
        }
        return writes.withLock {
            admission()?.let { return@withLock SetupOutcome.Failed(it) }
            val previous = readRecords()!!
            val next = previous + SetupAuditRecord(id, action, clock.now().toString(), operation)
            val bounded = if (next.size > MAX_RECORDS) next.subList(next.size - MAX_RECORDS, next.size) else next
            val encoded = buildJsonArray {
                bounded.forEach { r ->
                    add(
                        buildJsonObject {
                            put("id", r.id)
                            put("action", r.action)
                            put("at", r.at)
                            r.operation?.let { put("operation", it) }
                        },
                    )
                }
            }.toString()
            if (prefs.setString(key, encoded)) SetupOutcome.Ok(Unit) else SetupOutcome.Failed(STORAGE)
        }
    }

    public companion object {
        public const val MAX_RECORDS: Int = 100
        private val RECORD_KEYS = setOf("id", "action", "at", "operation")
        private val ACTIONS = setOf("proposed", "pending", "accepted", "verified", "uncertain", "rejected")
        private val OPERATIONS = setOf("apply", "undo", "review")
        private val ID_PATTERN =
            Regex(
                "^(?:(?:proposal|undo)-[0-9]+|[0-9a-f]{48}|(?:proposal-)?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                    "[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$",
            )
        private val STORAGE = SetupFailure(SetupFailureCode.Storage, "Setup history could not be saved. Refresh before trying again.")
        private val CLOSED = SetupFailure(SetupFailureCode.Offline, "Setup is unavailable for this server.")

        private fun kotlinx.serialization.json.JsonElement?.string(): String? =
            (this as? JsonPrimitive)?.takeIf { it.isString }?.content

        /** Dart `DateTime.tryParse(at)` with `isUtc`: only timestamps with a zone (Z or an offset) are accepted. */
        private fun parseUtc(at: String): Instant? = try {
            Instant.parse(at)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
