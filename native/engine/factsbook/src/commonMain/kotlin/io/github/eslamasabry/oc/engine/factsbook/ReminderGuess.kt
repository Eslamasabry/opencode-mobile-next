package io.github.eslamasabry.oc.engine.factsbook

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * A first guess at a reminder from what the person wrote (F: lib/domain/factsbook/reminder_guess.dart): only to fill
 * the check page's fields. The person confirms every field, and the node's own preview is what is saved. A reminder is
 * a one-off unless the words say it repeats ("every Monday", "monthly", "every day").
 */
public data class ReminderGuess(
    /** The title: the first sentence. */
    val what: String,
    val at: LocalDateTime,
    /** `date` and/or `time` when the text did not say. */
    val ambiguities: List<String>,
    val repeat: ReminderRepeat = ReminderRepeat.Never,
) {
    /** The check page's starting timing: weekly on the guessed day, monthly on its date. */
    public val timing: ReminderTiming
        get() =
            ReminderTiming(
                repeat,
                at,
                weekdays = if (repeat == ReminderRepeat.Weekly) setOf(at.date.dayOfWeek.isoDayNumber) else emptySet(),
                dayOfMonth = at.date.day,
            )

    public companion object {
        private val weekdays =
            listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        private val clock = Regex("""\bat (\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")

        public fun from(text: String, now: LocalDateTime): ReminderGuess {
            val lower = text.lowercase()
            val today = now.date
            var date: LocalDate = today.plus(DatePeriod(days = 1))
            val ambiguities = mutableListOf<String>()
            var found = false
            for ((i, name) in weekdays.withIndex()) {
                if (name in lower) {
                    var ahead = (i + 1 - today.dayOfWeek.isoDayNumber).mod(7)
                    if (ahead == 0 || "next $name" in lower) ahead += 7
                    date = today.plus(DatePeriod(days = ahead))
                    found = true
                    break
                }
            }
            if (!found) {
                if ("today" in lower) {
                    date = today
                    found = true
                } else if ("tomorrow" in lower) found = true
            }
            if (!found) ambiguities += "date"
            var hour = 9
            var minute = 0
            val at = clock.find(lower)
            if (at != null) {
                hour = at.groupValues[1].toInt()
                minute = at.groupValues[2].toIntOrNull() ?: 0
                val half = at.groupValues[3]
                if (half == "pm" && hour < 12) hour += 12
                if (half == "am" && hour == 12) hour = 0
            } else ambiguities += "time"
            val what = text.split(Regex("[.\n]")).first().trim()
            return ReminderGuess(
                what = what.ifEmpty { text.trim() },
                at = LocalDateTime(date.year, date.month, date.day, hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
                ambiguities = ambiguities,
                repeat = repeatOf(lower),
            )
        }

        private fun repeatOf(lower: String): ReminderRepeat =
            when {
                Regex("""\b(every year|yearly|annually|every anniversary)\b""").containsMatchIn(lower) -> ReminderRepeat.Yearly
                Regex("""\b(every month|monthly)\b""").containsMatchIn(lower) -> ReminderRepeat.Monthly
                Regex("""\b(every week|weekly|every (monday|tuesday|wednesday|thursday|friday|saturday|sunday))\b""")
                    .containsMatchIn(lower) -> ReminderRepeat.Weekly
                Regex("""\b(every day|daily|every (morning|evening|night))\b""").containsMatchIn(lower) -> ReminderRepeat.Daily
                else -> ReminderRepeat.Never
            }
    }
}
