package io.github.eslamasabry.oc.feature.chat.presentation

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit

/**
 * signin-followups: when a failed reply's own limit sentence says the limit resets, as epoch milliseconds, so the
 * error row's headline can say it in the person's time zone and clock format. Only the reset is read from the words;
 * they are never shown. Claude Code's shapes:
 * - "You've hit your weekly limit · resets Nov 20, 1am (UTC)" (a date with no year, an hour, a time zone);
 * - "You've hit your session limit · resets 3:30pm (Europe/Berlin)" (no date: the next such time);
 * - "Claude AI usage limit reached|1763600400" (epoch seconds after the bar).
 * Null when the words name no reset this can read (an unknown zone, no time).
 */
internal object ReplyLimitReset {
    private val RESETS =
        Regex(
            """\bresets?\s+(?:(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?\s+(\d{1,2}),?\s+(?:at\s+)?)?""" +
                """(\d{1,2})(?::(\d{2}))?\s*(am|pm)\s*\(([A-Za-z0-9_+\-/]{1,64})\)""",
            RegexOption.IGNORE_CASE,
        )
    private val EPOCH = Regex("""usage limit reached\|(\d{10})\b""", RegexOption.IGNORE_CASE)
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** The reset [words] name, read against [saidAtMs] (when the reply came): the first such moment after it. */
    fun resetAt(words: String, saidAtMs: Long): Long? {
        EPOCH.find(words)?.let { return it.groupValues[1].toLong() * 1000 }
        val m = RESETS.find(words) ?: return null
        return try {
            val zone = TimeZone.of(m.groupValues[6])
            val hour12 = m.groupValues[3].toInt()
            val minute = m.groupValues[4].ifEmpty { "0" }.toInt()
            if (hour12 !in 1..12 || minute !in 0..59) return null
            val hour = hour12 % 12 + if (m.groupValues[5].equals("pm", ignoreCase = true)) 12 else 0
            val said = Instant.fromEpochMilliseconds(saidAtMs).toLocalDateTime(zone)
            val month = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { MONTHS.indexOf(it.lowercase().take(3)) + 1 }
            val days: List<LocalDate> =
                if (month != null) {
                    val day = m.groupValues[2].toInt()
                    listOf(said.year, said.year + 1).mapNotNull { year ->
                        try {
                            LocalDate(year, month, day)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    }
                } else listOf(said.date, said.date.plus(1, DateTimeUnit.DAY))
            // The words were said before the reset: the first candidate after them (a minute of clock skew allowed).
            days
                .map { it.atTime(hour, minute).toInstant(zone).toEpochMilliseconds() }
                .firstOrNull { it > saidAtMs - 60_000 }
        } catch (_: IllegalArgumentException) {
            null // an unknown time zone (IllegalTimeZoneException is one)
        }
    }
}
