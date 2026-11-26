package io.github.eslamasabry.oc.feature.settings.ui

/** Duration unit parsed from an OpenCode `every` or relative `at` trigger (B-605). */
internal enum class AutomationDurationUnit {
    Seconds,
    Minutes,
    Hours,
    Days,
    Weeks,
}

internal data class ParsedDuration(val amount: Int, val unit: AutomationDurationUnit)

internal data class ParsedWallClock(val date: String, val hour: Int, val minute: Int)

/** Plain-language schedule shapes recognized from a 5-field cron expression (B-605). */
internal sealed interface ParsedCron {
    data object EveryMinute : ParsedCron

    data class EveryNMinutes(val minutes: Int) : ParsedCron

    data object EveryHour : ParsedCron

    data class HourlyAt(val minute: Int) : ParsedCron

    data class EveryNHours(val hours: Int) : ParsedCron

    data class Daily(val hour: Int, val minute: Int) : ParsedCron

    data class Weekdays(val hour: Int, val minute: Int) : ParsedCron

    data class Weekends(val hour: Int, val minute: Int) : ParsedCron

    /** [weekday] is 0 = Monday .. 6 = Sunday. */
    data class Weekly(val weekday: Int, val hour: Int, val minute: Int) : ParsedCron

    data class Monthly(val dayOfMonth: Int, val hour: Int, val minute: Int) : ParsedCron

    data object Custom : ParsedCron
}

internal object AutomationScheduleParser {
    private val durationRegex = Regex("""^(\d+)\s*([a-zA-Z]+)$""")
    private val relativeAtRegex = Regex("""^in\s+(\d+)\s*([a-zA-Z]+)$""", RegexOption.IGNORE_CASE)
    private val wallClockRegex =
        Regex("""^(\d{4}-\d{2}-\d{2})[T ](\d{1,2}):(\d{2})(?::\d{2})?$""")
    private val whitespaceRegex = Regex("""\s+""")

    private const val MAX_MINUTE = 59
    private const val MAX_HOUR = 23
    private const val MAX_DAY_OF_MONTH = 31
    private const val CRON_FIELD_COUNT = 5
    private const val FIELD_MONTH = 3
    private const val FIELD_DOW = 4
    private const val GROUP_MINUTE = 3
    private const val MONDAY = 0
    private const val TUESDAY = 1
    private const val WEDNESDAY = 2
    private const val THURSDAY = 3
    private const val FRIDAY = 4
    private const val SATURDAY = 5
    private const val SUNDAY = 6

    fun parseDuration(raw: String): ParsedDuration? =
        durationRegex.matchEntire(raw.trim())?.let { match ->
            val amount = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
            val unit = parseUnit(match.groupValues[2])
            if (amount != null && unit != null) ParsedDuration(amount, unit) else null
        }

    fun parseRelativeAt(raw: String): ParsedDuration? =
        relativeAtRegex.matchEntire(raw.trim())?.let { match ->
            val amount = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
            val unit = parseUnit(match.groupValues[2])
            if (amount != null && unit != null) ParsedDuration(amount, unit) else null
        }

    fun parseWallClock(raw: String): ParsedWallClock? =
        wallClockRegex.matchEntire(raw.trim())?.let { match ->
            val hour = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..MAX_HOUR }
            val minute = match.groupValues[GROUP_MINUTE].toIntOrNull()?.takeIf { it in 0..MAX_MINUTE }
            if (hour != null && minute != null) {
                ParsedWallClock(match.groupValues[1], hour, minute)
            } else {
                null
            }
        }

    fun parseCron(expression: String): ParsedCron {
        val trimmed = expression.trim()
        val macro =
            when (trimmed.lowercase()) {
                "@hourly" -> ParsedCron.EveryHour
                "@daily",
                "@midnight" -> ParsedCron.Daily(hour = 0, minute = 0)
                "@weekly" -> ParsedCron.Weekly(weekday = SUNDAY, hour = 0, minute = 0)
                "@monthly" -> ParsedCron.Monthly(dayOfMonth = 1, hour = 0, minute = 0)
                else -> null
            }
        val parts = trimmed.split(whitespaceRegex)
        return when {
            trimmed.isEmpty() -> ParsedCron.Custom
            macro != null -> macro
            parts.size != CRON_FIELD_COUNT -> ParsedCron.Custom
            else ->
                parseFiveFieldCron(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[FIELD_MONTH],
                    parts[FIELD_DOW],
                )
        }
    }

    private fun parseFiveFieldCron(
        minField: String,
        hourField: String,
        domField: String,
        monthField: String,
        dowField: String,
    ): ParsedCron {
        val minute = minField.toIntOrNull()?.takeIf { it in 0..MAX_MINUTE }
        val hour = hourField.toIntOrNull()?.takeIf { it in 0..MAX_HOUR }
        return when {
            monthField != "*" -> ParsedCron.Custom
            domField == "*" && dowField == "*" -> parseWildcardDayCron(minField, hourField)
            minute == null || hour == null -> ParsedCron.Custom
            domField == "*" -> parseDayOfWeekCron(dowField, hour, minute)
            dowField == "*" ->
                domField
                    .toIntOrNull()
                    ?.takeIf { it in 1..MAX_DAY_OF_MONTH }
                    ?.let { ParsedCron.Monthly(it, hour, minute) } ?: ParsedCron.Custom
            else -> ParsedCron.Custom
        }
    }

    private fun parseWildcardDayCron(minField: String, hourField: String): ParsedCron {
        val minute = minField.toIntOrNull()?.takeIf { it in 0..MAX_MINUTE }
        val hour = hourField.toIntOrNull()?.takeIf { it in 0..MAX_HOUR }
        val minStep = parseStep(minField, 2..MAX_MINUTE)
        val hourStep = parseStep(hourField, 2..MAX_HOUR)
        return when {
            minField == "*" && hourField == "*" -> ParsedCron.EveryMinute
            hourField == "*" && minStep != null -> ParsedCron.EveryNMinutes(minStep)
            hourField == "*" && minute == 0 -> ParsedCron.EveryHour
            hourField == "*" && minute != null -> ParsedCron.HourlyAt(minute)
            minute == 0 && hourStep != null -> ParsedCron.EveryNHours(hourStep)
            minute != null && hour != null -> ParsedCron.Daily(hour, minute)
            else -> ParsedCron.Custom
        }
    }

    private fun parseDayOfWeekCron(dowField: String, hour: Int, minute: Int): ParsedCron {
        val norm = dowField.uppercase()
        val singleDay = SINGLE_WEEKDAY[norm]
        return when {
            norm in WEEKDAY_TOKENS -> ParsedCron.Weekdays(hour, minute)
            norm in WEEKEND_TOKENS -> ParsedCron.Weekends(hour, minute)
            singleDay != null -> ParsedCron.Weekly(singleDay, hour, minute)
            else -> ParsedCron.Custom
        }
    }

    private fun parseStep(field: String, range: IntRange): Int? =
        field.removePrefix("*/").takeIf { field.startsWith("*/") }?.toIntOrNull()?.takeIf {
            it in range
        }

    private fun parseUnit(raw: String): AutomationDurationUnit? =
        when (raw.lowercase()) {
            "s",
            "sec",
            "secs",
            "second",
            "seconds" -> AutomationDurationUnit.Seconds
            "m",
            "min",
            "mins",
            "minute",
            "minutes" -> AutomationDurationUnit.Minutes
            "h",
            "hr",
            "hrs",
            "hour",
            "hours" -> AutomationDurationUnit.Hours
            "d",
            "day",
            "days" -> AutomationDurationUnit.Days
            "w",
            "wk",
            "wks",
            "week",
            "weeks" -> AutomationDurationUnit.Weeks
            else -> null
        }

    private val WEEKDAY_TOKENS = setOf("1-5", "MON-FRI", "1,2,3,4,5", "MON,TUE,WED,THU,FRI")
    private val WEEKEND_TOKENS =
        setOf("0,6", "6,0", "6,7", "7,6", "SAT,SUN", "SUN,SAT", "6-7", "SAT-SUN")
    private val SINGLE_WEEKDAY =
        mapOf(
            "0" to SUNDAY,
            "7" to SUNDAY,
            "SUN" to SUNDAY,
            "1" to MONDAY,
            "MON" to MONDAY,
            "2" to TUESDAY,
            "TUE" to TUESDAY,
            "3" to WEDNESDAY,
            "WED" to WEDNESDAY,
            "4" to THURSDAY,
            "THU" to THURSDAY,
            "5" to FRIDAY,
            "FRI" to FRIDAY,
            "6" to SATURDAY,
            "SAT" to SATURDAY,
        )
}
