package io.github.eslamasabry.oc.engine.factsbook

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/*
 * Reminders (F: factsbook_types.dart ReminderSchedule/ReminderPreview/Reminder/ReminderTiming,
 * http_factsbook_gateway.dart previewReminder..updateReminder). None of these calls is owner-signed.
 */

/** `Schedule.when`, kept raw so a form this app does not build round-trips untouched. */
public data class ScheduleWhen(val raw: JsonObject) {
    /** `{"once": "2026-10-15T09:00"}`: a local date-time. */
    val once: LocalDateTime? get() = raw.str("once")?.let(::localOrNull)

    val every: String? get() = raw.str("every")

    val at: String? get() = raw.str("at")
}

/** `Schedule`. [zone] is `vault`, `floating` or an IANA name. */
public data class ReminderSchedule(val id: String, val `when`: ScheduleWhen, val zone: String, val raw: JsonObject) {
    internal companion object {
        fun read(json: JsonObject): ReminderSchedule =
            ReminderSchedule(json.need("id"), ScheduleWhen(json.obj("when") ?: throw FactsBookShapeError("when")), json.str("zone") ?: "vault", json)
    }
}

/**
 * The node's own reading of a reminder before anything is saved (`Preview`): date, time, zone and locale are shown
 * before saving. The digest binds exactly this object, so it is sent back unchanged to confirm.
 */
public data class ReminderPreview(
    val what: String,
    val zone: String,
    val locale: String,
    /** The node's words for what it could not settle. It cannot be confirmed while any remain. */
    val ambiguities: List<String>,
    val adjustments: List<String>,
    val digest: String,
    /** The object as received (with `preview_digest`), sent back to confirm. */
    val raw: JsonObject,
    val effectiveZone: String = "",
    val area: String = "general",
    val sensitive: Boolean = false,
    val schedule: ReminderSchedule? = null,
) {
    val confirmable: Boolean get() = schedule != null && ambiguities.isEmpty()

    /** The local date-time of a one-time reminder. */
    val at: LocalDateTime? get() = schedule?.`when`?.once

    internal companion object {
        fun read(json: JsonObject): ReminderPreview =
            ReminderPreview(
                what = json.str("what").orEmpty(),
                zone = json.str("zone") ?: "vault",
                locale = json.str("locale") ?: "en",
                ambiguities = json.strings("ambiguities"),
                adjustments = json.strings("adjustments"),
                digest = json.need("preview_digest"),
                raw = json,
                effectiveZone = json.str("effective_zone").orEmpty(),
                area = json.str("area") ?: "general",
                sensitive = json.str("sens") == "sensitive",
                schedule = json.obj("schedule")?.let(ReminderSchedule::read),
            )
    }
}

/** A saved reminder (`Reminder`), kept independently of chats. */
public data class Reminder(
    val id: String,
    val what: String,
    val schedule: ReminderSchedule,
    val locale: String,
    val urgent: Boolean = false,
    val paused: Boolean = false,
    val revision: Long = 0,
    val area: String = "general",
    val done: Boolean = false,
    val snoozedUntil: Instant? = null,
) {
    internal companion object {
        fun read(json: JsonObject): Reminder =
            Reminder(
                id = json.need("id"),
                what = json.str("what").orEmpty(),
                schedule = ReminderSchedule.read(json.obj("schedule") ?: throw FactsBookShapeError("schedule")),
                locale = json.str("locale") ?: "en",
                urgent = json.isTrue("urgent"),
                paused = json.isTrue("paused"),
                revision = json.long("rev") ?: 0,
                area = json.str("area") ?: "general",
                done = json.isTrue("done"),
                snoozedUntil = json.timeOrNull("snoozed_until"),
            )
    }
}

/** How often a reminder repeats, as far as this app builds it (intervals, events and cron show and pause only). */
public enum class ReminderRepeat { Never, Daily, Weekly, Monthly, Yearly }

/**
 * When a reminder fires, in the node's schedule forms. [at] carries the clock time (and the date for [ReminderRepeat.Never],
 * the month and day for yearly). [weekdays] are 1 (Monday) to 7 (Sunday).
 */
public data class ReminderTiming(
    val repeat: ReminderRepeat,
    val at: LocalDateTime,
    val weekdays: Set<Int> = emptySet(),
    val dayOfMonth: Int = 1,
    val lastDay: Boolean = false,
) {
    private fun two(n: Int) = n.toString().padStart(2, '0')

    private val clock: String get() = "${two(at.hour)}:${two(at.minute)}"

    /** The `when` object the node reads (F: `toWhen`). */
    public fun toWhen(): JsonObject =
        when (repeat) {
            ReminderRepeat.Never ->
                obj("once" to JsonPrimitive("${at.year.toString().padStart(4, '0')}-${two(at.month.ordinal + 1)}-${two(at.day)}T$clock"))
            ReminderRepeat.Daily -> obj("every" to JsonPrimitive("day"), "at" to JsonPrimitive(clock))
            ReminderRepeat.Weekly ->
                obj(
                    "every" to JsonPrimitive("week"),
                    "on" to JsonArray(weekdays.sorted().map { JsonPrimitive(NAMES[it - 1]) }),
                    "at" to JsonPrimitive(clock),
                )
            ReminderRepeat.Monthly ->
                obj("every" to JsonPrimitive("month"), "day" to if (lastDay) JsonPrimitive("last") else JsonPrimitive(dayOfMonth), "at" to JsonPrimitive(clock))
            ReminderRepeat.Yearly ->
                obj("every" to JsonPrimitive("year"), "month" to JsonPrimitive(at.month.ordinal + 1), "day" to JsonPrimitive(at.day), "at" to JsonPrimitive(clock))
        }

    public companion object {
        private val NAMES = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

        private fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))

        /** Reads a `when` back, or null for a form this app does not build (F: `fromWhen`). */
        public fun fromWhen(`when`: ScheduleWhen): ReminderTiming? {
            `when`.once?.let { return ReminderTiming(ReminderRepeat.Never, it) }
            val raw = `when`.raw
            val time = raw.str("at")?.split(':') ?: return null
            val hour = time.firstOrNull()?.toIntOrNull() ?: return null
            val minute = time.getOrNull(1)?.toIntOrNull() ?: return null
            fun clock(month: Int, day: Int) = LocalDateTime(2000, month, day, hour, minute)
            return when (raw.str("every")) {
                "day" -> ReminderTiming(ReminderRepeat.Daily, clock(1, 1))
                "week" -> {
                    val days = raw.strings("on").mapNotNull { NAMES.indexOf(it).takeIf { i -> i >= 0 }?.plus(1) }.toSet()
                    if (days.isEmpty()) null else ReminderTiming(ReminderRepeat.Weekly, clock(1, 1), weekdays = days)
                }
                "month" -> {
                    val day = raw["day"] as? JsonPrimitive
                    ReminderTiming(
                        ReminderRepeat.Monthly,
                        clock(1, 1),
                        lastDay = day?.contentOrNull == "last",
                        dayOfMonth = day?.takeIf { !it.isString }?.intOrNull ?: 1,
                    )
                }
                "year" -> {
                    val month = (raw["month"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null
                    val day = (raw["day"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null
                    ReminderTiming(ReminderRepeat.Yearly, clock(month, day))
                }
                else -> null
            }
        }
    }
}

private fun localOrNull(raw: String): LocalDateTime? =
    try {
        LocalDateTime.parse(raw)
    } catch (e: IllegalArgumentException) {
        null
    }

/** The reminders part of the App API (F: http_factsbook_gateway.dart). */
public interface FactsBookReminders {
    /** `POST /v1/reminders/parse`: the node's reading, nothing saved. [scheduleId] keeps identity on an edit. */
    public suspend fun previewReminder(
        what: String,
        timing: ReminderTiming,
        zone: String,
        locale: String,
        scheduleId: String? = null,
        area: String = "general",
    ): FbResult<ReminderPreview>

    public suspend fun createReminder(preview: ReminderPreview, urgent: Boolean = false, floatingConfirmed: Boolean = false): FbResult<MutationResult>

    public suspend fun reminders(): FbResult<List<Reminder>>

    public suspend fun setReminderPaused(reminder: Reminder, paused: Boolean): FbResult<MutationResult>

    public suspend fun updateReminder(reminder: Reminder, preview: ReminderPreview, floatingConfirmed: Boolean = false): FbResult<MutationResult>
}

/** A schedule id the node accepts: `sch_` and 26 Crockford characters, the first 0 to 7. */
internal fun scheduleId(random: kotlin.random.Random): String {
    val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    return "sch_" + alphabet[random.nextInt(8)] + (1..25).map { alphabet[random.nextInt(32)] }.joinToString("")
}

/** The structured form the node reads: the schedule as JSON text, the title in its own field. */
internal fun scheduleText(id: String, timing: ReminderTiming, zone: String): String =
    JsonObject(
        linkedMapOf(
            "v" to JsonPrimitive(1),
            "id" to JsonPrimitive(id),
            "when" to timing.toWhen(),
            "zone" to JsonPrimitive(zone),
            "window" to JsonNull,
            "catchup" to JsonPrimitive("coalesce"),
        )
    ).toString()
