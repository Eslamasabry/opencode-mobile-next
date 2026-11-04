package io.github.eslamasabry.oc.engine.runtime.recovery.budget

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

/**
 * One snapshot of the phone server's automatic restart budget (port of `_Budget` in
 * lib/builtin/builtin_server_recovery.dart): at most [MAX_ATTEMPTS] attempts, the [pending] attempt's [eventId] and
 * native [recoveryGeneration], and its health-confirmed time once it answered. Native owns the counter once migrated:
 * a consumer may only write receipts back with the same [attempts] and [revision].
 */
public data class RecoveryBudget(
    val attempts: Int = 0,
    val nextAt: Instant? = null,
    val pending: Boolean = false,
    val eventId: String? = null,
    val recoveryGeneration: Long? = null,
    val confirmedAt: Instant? = null,
    val revision: Long = 0,
) {
    public fun confirmed(at: Instant): RecoveryBudget = copy(confirmedAt = at)

    /** The attempt is over: the count stays, the pending receipt goes. */
    public fun finished(): RecoveryBudget = RecoveryBudget(attempts = attempts, nextAt = nextAt, revision = revision)

    /** The wire shape (version 1) native and the legacy store both hold. */
    public fun toMap(): Map<String, Any?> =
        mapOf(
            "version" to 1,
            "revision" to revision,
            "attempts" to attempts,
            "nextAt" to nextAt?.toEpochMilliseconds(),
            "pending" to pending,
            "eventId" to eventId,
            "recoveryGeneration" to recoveryGeneration,
            "confirmedAt" to confirmedAt?.toEpochMilliseconds(),
        )

    public fun toJson(): String = json(toMap())

    public companion object {
        public const val MAX_ATTEMPTS: Int = 3

        /** The legacy store's text: absent is a fresh budget; unreadable or malformed is null (fail closed). */
        public fun readJson(raw: String?): RecoveryBudget? {
            if (raw == null) return RecoveryBudget()
            return readMap(parse(raw) ?: return null)
        }

        /**
         * A snapshot only when every field has its type: version 1, attempts 0…3, a boolean pending, integer times,
         * a confirmed time only on a pending attempt, and a pending attempt's event id and generation. Else null.
         */
        public fun readMap(value: Any?): RecoveryBudget? {
            val map = value as? Map<*, *> ?: return null
            if (integer(map["version"]) != 1L) return null
            val attempts = integer(map["attempts"]) ?: return null
            if (attempts !in 0L..MAX_ATTEMPTS.toLong()) return null
            val pending = map["pending"] as? Boolean ?: return null
            val nextAt = map["nextAt"]
            if (nextAt != null && integer(nextAt) == null) return null
            val confirmedAt = map["confirmedAt"]
            if (confirmedAt != null && (integer(confirmedAt) == null || !pending)) return null
            val eventId = map["eventId"]
            val generation = map["recoveryGeneration"]
            if (pending && (eventId !is String || integer(generation) == null)) return null
            return RecoveryBudget(
                attempts = attempts.toInt(),
                revision = integer(map["revision"]) ?: 0,
                nextAt = integer(nextAt)?.let(Instant::fromEpochMilliseconds),
                pending = pending,
                eventId = eventId as? String,
                recoveryGeneration = integer(generation),
                confirmedAt = integer(confirmedAt)?.let(Instant::fromEpochMilliseconds),
            )
        }

        /** Dart `is int`: integer types only, never 2.0 or "2". */
        internal fun integer(value: Any?): Long? =
            when (value) {
                is Int -> value.toLong()
                is Long -> value
                else -> null
            }

        internal fun parse(raw: String): Any? =
            try {
                plain(Json.parseToJsonElement(raw))
            } catch (e: IllegalArgumentException) {
                null
            }

        private fun plain(element: JsonElement): Any? =
            when (element) {
                JsonNull -> null
                is JsonObject -> element.mapValues { plain(it.value) }
                is JsonArray -> element.map(::plain)
                is JsonPrimitive ->
                    when {
                        element.isString -> element.content
                        element.booleanOrNull != null -> element.booleanOrNull
                        element.longOrNull != null -> element.longOrNull
                        else -> element.content.toDoubleOrNull()
                    }
            }

        internal fun json(map: Map<String, Any?>): String =
            JsonObject(
                map.mapValues { (_, value) ->
                    when (value) {
                        null -> JsonNull
                        is Boolean -> JsonPrimitive(value)
                        is Number -> JsonPrimitive(value)
                        else -> JsonPrimitive(value.toString())
                    }
                },
            ).toString()
    }
}
