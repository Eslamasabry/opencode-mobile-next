package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The Flutter store's JSON, byte for byte (lib/scheduling/models.dart `toJson` key order, dart:convert encoding,
 * `DateTime.toUtc().toIso8601String()`). Decoding is strict like the Dart `fromJson`: any wrong type, unknown enum,
 * or invalid value throws [ScheduleFormatException], which the store reports as `storage_corrupt`.
 */
public class ScheduleFormatException internal constructor() : Exception("invalid scheduled prompt data")

public object ScheduleCodec {
    public const val SCHEMA_VERSION: Long = 1

    // ---- Dart DateTime ISO-8601

    /** `DateTime.fromMillisecondsSinceEpoch(ms, isUtc: true).toIso8601String()`. */
    public fun isoUtc(ms: Long): String {
        val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC)
        val millis = (ms % 1000 + 1000) % 1000
        return buildString {
            append(pad(t.year, 4)).append('-').append(pad(t.month.ordinal + 1, 2)).append('-').append(pad(t.day, 2))
            append('T').append(pad(t.hour, 2)).append(':').append(pad(t.minute, 2)).append(':').append(pad(t.second, 2))
            append('.').append(pad(millis.toInt(), 3)).append('Z')
        }
    }

    private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')

    private val isoPattern =
        Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:[.,](\d{1,9}))?)?(Z|[+-]\d{2}:?\d{2})?$""")

    /** Dart `DateTime.parse` for the forms the Flutter store writes (UTC `Z`, explicit offsets); millisecond precision. */
    public fun parseIso(text: String): Long {
        val m = isoPattern.matchEntire(text) ?: throw ScheduleFormatException()
        val g = m.groupValues
        val fraction = g[7].padEnd(9, '0').take(9).toInt()
        val local =
            try {
                LocalDateTime(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt(), fraction)
            } catch (e: IllegalArgumentException) {
                throw ScheduleFormatException()
            }
        val zone = g[8]
        val instant =
            when {
                zone.isEmpty() -> local.toInstant(TimeZone.currentSystemDefault())
                zone == "Z" -> local.toInstant(TimeZone.UTC)
                else -> {
                    val digits = zone.substring(1).replace(":", "")
                    val sign = if (zone[0] == '-') -1 else 1
                    val offset = UtcOffset(sign * digits.take(2).toInt(), sign * digits.drop(2).toInt())
                    local.toInstant(offset)
                }
            }
        return instant.toEpochMilliseconds()
    }

    // ---- Encoding (key order = Dart toJson)

    public fun recurrence(value: Recurrence): Map<String, Any?> =
        linkedMapOf(
            "kind" to value.kind.wire,
            "at" to value.atMs?.let(::isoUtc),
            "hour" to value.hour.toLong(),
            "minute" to value.minute.toLong(),
            "hours" to value.hours.toLong(),
        )

    public fun schedule(value: ScheduledPrompt): Map<String, Any?> =
        linkedMapOf(
            "id" to value.id,
            "profileId" to value.profileId,
            "profileFingerprint" to value.profileFingerprint,
            "prompt" to value.prompt,
            "projectPath" to value.projectPath,
            "workspace" to value.workspace,
            "sessionId" to value.sessionId,
            "agent" to value.agent,
            "modelProviderId" to value.modelProviderId,
            "modelId" to value.modelId,
            "recurrence" to recurrence(value.recurrence),
            "missedPolicy" to value.missedPolicy.wire,
            "enabled" to value.enabled,
            "nextRunAt" to value.nextRunAtMs?.let(::isoUtc),
            "createdAt" to isoUtc(value.createdAtMs),
            "revision" to value.revision.toLong(),
        )

    public fun run(value: ScheduledRun): Map<String, Any?> =
        linkedMapOf(
            "id" to value.id,
            "scheduleId" to value.scheduleId,
            "scheduledAt" to isoUtc(value.scheduledAtMs),
            "startedAt" to isoUtc(value.startedAtMs),
            "status" to value.status.wire,
            "late" to value.late,
            "finishedAt" to value.finishedAtMs?.let(::isoUtc),
            "sessionId" to value.sessionId,
            "errorCode" to value.errorCode,
        )

    public fun digest(value: ScheduleDigest): Map<String, Any?> =
        linkedMapOf(
            "enabled" to value.enabled,
            "hour" to value.hour.toLong(),
            "minute" to value.minute.toLong(),
            "lastDeliveredAt" to value.lastDeliveredAtMs?.let(::isoUtc),
        )

    // ---- Strict decoding (Dart `as` casts)

    private fun Map<*, *>.str(key: String): String = this[key] as? String ?: throw ScheduleFormatException()

    private fun Map<*, *>.strOrNull(key: String): String? =
        when (val v = this[key]) {
            null -> null
            is String -> v
            else -> throw ScheduleFormatException()
        }

    private fun Map<*, *>.int(key: String): Int {
        val v = this[key] as? Long ?: throw ScheduleFormatException()
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) throw ScheduleFormatException()
        return v.toInt()
    }

    private fun Map<*, *>.bool(key: String): Boolean = this[key] as? Boolean ?: throw ScheduleFormatException()

    private fun Map<*, *>.timeOrNull(key: String): Long? = strOrNull(key)?.let(::parseIso)

    private fun Any?.obj(): Map<*, *> = this as? Map<*, *> ?: throw ScheduleFormatException()

    public fun decodeRecurrence(json: Map<*, *>): Recurrence {
        val kind = RecurrenceKind.of(json.str("kind")) ?: throw ScheduleFormatException()
        val at = json.timeOrNull("at")
        val value =
            when (kind) {
                RecurrenceKind.Once -> Recurrence.once(at ?: throw ScheduleFormatException())
                RecurrenceKind.Daily -> Recurrence.daily(json.int("hour"), json.int("minute"))
                RecurrenceKind.Weekdays -> Recurrence.weekdays(json.int("hour"), json.int("minute"))
                RecurrenceKind.Interval -> Recurrence.interval(json.int("hours"), at ?: throw ScheduleFormatException())
            }
        if (!value.valid) throw ScheduleFormatException()
        return value
    }

    public fun decodeSchedule(json: Map<*, *>): ScheduledPrompt {
        val value =
            ScheduledPrompt(
                id = json.str("id"),
                profileId = json.str("profileId"),
                profileFingerprint = json.str("profileFingerprint"),
                prompt = json.str("prompt"),
                projectPath = json.str("projectPath"),
                workspace = json.strOrNull("workspace"),
                sessionId = json.strOrNull("sessionId"),
                agent = json.strOrNull("agent"),
                modelProviderId = json.strOrNull("modelProviderId"),
                modelId = json.strOrNull("modelId"),
                recurrence = decodeRecurrence(json["recurrence"].obj()),
                missedPolicy = MissedRunPolicy.of(json.str("missedPolicy")) ?: throw ScheduleFormatException(),
                enabled = json.bool("enabled"),
                nextRunAtMs = json.timeOrNull("nextRunAt"),
                createdAtMs = parseIso(json.str("createdAt")),
                revision = json.int("revision"),
            )
        if (!value.valid) throw ScheduleFormatException()
        return value
    }

    public fun decodeRun(json: Map<*, *>): ScheduledRun {
        val value =
            ScheduledRun(
                id = json.str("id"),
                scheduleId = json.str("scheduleId"),
                scheduledAtMs = parseIso(json.str("scheduledAt")),
                startedAtMs = parseIso(json.str("startedAt")),
                status = RunStatus.of(json.str("status")) ?: throw ScheduleFormatException(),
                late = json.bool("late"),
                finishedAtMs = json.timeOrNull("finishedAt"),
                sessionId = json.strOrNull("sessionId"),
                errorCode = json.strOrNull("errorCode"),
            )
        if (!value.valid) throw ScheduleFormatException()
        return value
    }

    public fun decodeDigest(json: Map<*, *>): ScheduleDigest {
        val value =
            ScheduleDigest(
                enabled = json.bool("enabled"),
                hour = json.int("hour"),
                minute = json.int("minute"),
                lastDeliveredAtMs = json.timeOrNull("lastDeliveredAt"),
            )
        if (!value.valid) throw ScheduleFormatException()
        return value
    }

    /** The whole per-profile envelope. */
    public data class Envelope(
        val schedules: List<ScheduledPrompt> = emptyList(),
        val runs: List<ScheduledRun> = emptyList(),
        val digest: ScheduleDigest = ScheduleDigest(),
    )

    public fun encodeEnvelope(value: Envelope): String =
        DartJson.encode(
            linkedMapOf(
                "schemaVersion" to SCHEMA_VERSION,
                "schedules" to value.schedules.map(::schedule),
                "runs" to value.runs.map { run(it.stored()) },
                "digest" to digest(value.digest),
            ),
        )

    /** Null raw = an empty envelope. Throws [ScheduleFormatException] on anything else that is not exactly valid. */
    public fun decodeEnvelope(raw: String?, profileId: String, maxSchedules: Int, maxHistory: Int, maxBytes: Int): Envelope {
        if (raw == null) return Envelope()
        if (raw.encodeToByteArray().size > maxBytes) throw ScheduleFormatException()
        val json = (DartJson.decodeOrNull(raw) ?: throw ScheduleFormatException()).obj()
        if (json["schemaVersion"] != SCHEMA_VERSION) throw ScheduleFormatException()
        val schedules = (json["schedules"] as? List<*> ?: throw ScheduleFormatException()).map { decodeSchedule(it.obj()) }
        val runs = (json["runs"] as? List<*> ?: throw ScheduleFormatException()).map { decodeRun(it.obj()) }
        if (schedules.size > maxSchedules || runs.size > maxHistory ||
            schedules.any { it.profileId != profileId } ||
            schedules.map { it.id }.toSet().size != schedules.size ||
            runs.map { it.id }.toSet().size != runs.size
        ) {
            throw ScheduleFormatException()
        }
        return Envelope(schedules, runs, decodeDigest(json["digest"].obj()))
    }
}
