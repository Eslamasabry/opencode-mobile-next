package io.github.eslamasabry.opencode_mobile.graph

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * signin-truth: when an agent's own usage-limit line says the limit resets, as wall-clock millis. Only the reset is read
 * from the words; they are never kept or shown. Claude Code's shapes:
 * - "You've hit your weekly limit · resets Nov 20, 1am (UTC)" (2.x, a date with no year, an hour, a time zone);
 * - "You've hit your session limit · resets 3:30pm (Europe/Berlin)" (no date: the next such time);
 * - "Claude AI usage limit reached|1763600400" (1.x, epoch seconds after the bar).
 * Null when the words name no reset this can read (an unknown zone, no time).
 */
internal object AgentLimitReset {
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
        val zone =
            try {
                ZoneId.of(m.groupValues[6])
            } catch (_: Exception) {
                return null
            }
        val hour12 = m.groupValues[3].toInt()
        val minute = m.groupValues[4].ifEmpty { "0" }.toInt()
        if (hour12 !in 1..12 || minute !in 0..59) return null
        val pm = m.groupValues[5].equals("pm", ignoreCase = true)
        val time = LocalTime.of(hour12 % 12 + if (pm) 12 else 0, minute)
        val said = Instant.ofEpochMilli(saidAtMs).atZone(zone)
        val month = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { MONTHS.indexOf(it.lowercase().take(3)) + 1 }
        val candidates: List<ZonedDateTime> =
            if (month != null) {
                val day = m.groupValues[2].toInt()
                listOf(said.year, said.year + 1).mapNotNull { year ->
                    try {
                        ZonedDateTime.of(LocalDate.of(year, month, day), time, zone)
                    } catch (_: Exception) {
                        null
                    }
                }
            } else listOf(0L, 1L).map { ZonedDateTime.of(said.toLocalDate().plusDays(it), time, zone) }
        // The words were said before the reset: the first candidate after them (a minute of clock skew allowed).
        return candidates.firstOrNull { it.toInstant().toEpochMilli() > saidAtMs - 60_000 }?.toInstant()?.toEpochMilli()
    }
}
