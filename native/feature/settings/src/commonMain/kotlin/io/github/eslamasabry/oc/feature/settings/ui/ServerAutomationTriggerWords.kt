package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.composerLimitFriday
import io.github.eslamasabry.oc.ui.words.res.composerLimitMonday
import io.github.eslamasabry.oc.ui.words.res.composerLimitSaturday
import io.github.eslamasabry.oc.ui.words.res.composerLimitSunday
import io.github.eslamasabry.oc.ui.words.res.composerLimitThursday
import io.github.eslamasabry.oc.ui.words.res.composerLimitTuesday
import io.github.eslamasabry.oc.ui.words.res.composerLimitWednesday
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsCronCustom
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsCronDaily
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsCronHourlyAt
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsCronMonthly
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsCronWeekdays
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsCronWeekends
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsCronWeekly
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsDayTime
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsEveryDays
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsEveryHours
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsEveryMinutes
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsEverySeconds
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsEveryWeeks
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsInDays
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsInHours
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsInMinutes
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsInSeconds
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsOnEvent
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsTriggerEvent
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsTriggerEventAny
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsTriggerEvery
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsTriggerOnce
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsWithZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun automationTriggerDescription(
    trigger: AutomationTrigger,
    nowMs: Long,
    phoneZoneId: String? = null,
): String? =
    when (trigger) {
        is AutomationTrigger.Every -> formatEveryTrigger(trigger.duration)
        is AutomationTrigger.Cron -> formatCronTrigger(trigger)
        is AutomationTrigger.At -> formatAtTrigger(trigger, nowMs, phoneZoneId)
        is AutomationTrigger.Event -> formatEventTrigger(trigger)
        AutomationTrigger.Unknown -> null
    }

@Composable
private fun formatEveryTrigger(raw: String): String? {
    val trimmed = raw.trim()
    val parsed = if (trimmed.isEmpty()) null else AutomationScheduleParser.parseDuration(trimmed)
    return when {
        trimmed.isEmpty() -> null
        parsed != null -> formatEveryDuration(parsed)
        else -> stringResource(Res.string.serverAutomationsTriggerEvery, trimmed)
    }
}

@Composable
private fun formatEveryDuration(parsed: ParsedDuration): String {
    val res =
        when (parsed.unit) {
            AutomationDurationUnit.Seconds -> Res.plurals.serverAutomationsEverySeconds
            AutomationDurationUnit.Minutes -> Res.plurals.serverAutomationsEveryMinutes
            AutomationDurationUnit.Hours -> Res.plurals.serverAutomationsEveryHours
            AutomationDurationUnit.Days -> Res.plurals.serverAutomationsEveryDays
            AutomationDurationUnit.Weeks -> Res.plurals.serverAutomationsEveryWeeks
        }
    return pluralStringResource(res, parsed.amount, Numbers.format(parsed.amount))
}

@Composable
private fun formatCronTrigger(trigger: AutomationTrigger.Cron): String? {
    if (trigger.expression.isBlank()) return null
    val base = formatParsedCron(AutomationScheduleParser.parseCron(trigger.expression))
    return withOptionalZone(base, trigger.timeZone)
}

@Composable
private fun formatParsedCron(parsed: ParsedCron): String =
    when (parsed) {
        ParsedCron.EveryMinute ->
            pluralStringResource(Res.plurals.serverAutomationsEveryMinutes, 1, Numbers.format(1))
        is ParsedCron.EveryNMinutes ->
            pluralStringResource(
                Res.plurals.serverAutomationsEveryMinutes,
                parsed.minutes,
                Numbers.format(parsed.minutes),
            )
        ParsedCron.EveryHour ->
            pluralStringResource(Res.plurals.serverAutomationsEveryHours, 1, Numbers.format(1))
        is ParsedCron.HourlyAt ->
            stringResource(
                Res.string.serverAutomationsCronHourlyAt,
                Numbers.digits(parsed.minute.toString().padStart(TIME_DIGITS, '0')),
            )
        is ParsedCron.EveryNHours ->
            pluralStringResource(
                Res.plurals.serverAutomationsEveryHours,
                parsed.hours,
                Numbers.format(parsed.hours),
            )
        is ParsedCron.Daily ->
            stringResource(
                Res.string.serverAutomationsCronDaily,
                formatAutomationClock(parsed.hour, parsed.minute),
            )
        is ParsedCron.Weekdays ->
            stringResource(
                Res.string.serverAutomationsCronWeekdays,
                formatAutomationClock(parsed.hour, parsed.minute),
            )
        is ParsedCron.Weekends ->
            stringResource(
                Res.string.serverAutomationsCronWeekends,
                formatAutomationClock(parsed.hour, parsed.minute),
            )
        is ParsedCron.Weekly ->
            stringResource(
                Res.string.serverAutomationsCronWeekly,
                stringResource(
                    WEEKDAY_FULL.getOrElse(parsed.weekday) { Res.string.composerLimitSunday }
                ),
                formatAutomationClock(parsed.hour, parsed.minute),
            )
        is ParsedCron.Monthly ->
            stringResource(
                Res.string.serverAutomationsCronMonthly,
                Numbers.digits(parsed.dayOfMonth.toString()),
                formatAutomationClock(parsed.hour, parsed.minute),
            )
        ParsedCron.Custom -> stringResource(Res.string.serverAutomationsCronCustom)
    }

@Composable
private fun formatAtTrigger(
    trigger: AutomationTrigger.At,
    nowMs: Long,
    phoneZoneId: String?,
): String? {
    val atMs = trigger.atMs
    val raw = trigger.raw?.trim()?.takeIf { it.isNotEmpty() }
    val relative = raw?.let(AutomationScheduleParser::parseRelativeAt)
    val wall = raw?.let(AutomationScheduleParser::parseWallClock)
    return when {
        atMs != null ->
            stringResource(
                Res.string.serverAutomationsTriggerOnce,
                formatAutomationMoment(atMs, nowMs, phoneZoneId),
            )
        relative != null ->
            stringResource(Res.string.serverAutomationsTriggerOnce, formatInDuration(relative))
        wall != null ->
            withOptionalZone(
                stringResource(
                    Res.string.serverAutomationsTriggerOnce,
                    stringResource(
                        Res.string.serverAutomationsDayTime,
                        Numbers.digits(wall.date),
                        formatAutomationClock(wall.hour, wall.minute),
                    ),
                ),
                trigger.timeZone,
            )
        raw != null ->
            withOptionalZone(
                stringResource(Res.string.serverAutomationsTriggerOnce, raw),
                trigger.timeZone,
            )
        else -> null
    }
}

@Composable
private fun formatInDuration(parsed: ParsedDuration): String {
    val res =
        when (parsed.unit) {
            AutomationDurationUnit.Seconds -> Res.plurals.serverAutomationsInSeconds
            AutomationDurationUnit.Minutes -> Res.plurals.serverAutomationsInMinutes
            AutomationDurationUnit.Hours -> Res.plurals.serverAutomationsInHours
            AutomationDurationUnit.Days,
            AutomationDurationUnit.Weeks -> Res.plurals.serverAutomationsInDays
        }
    val count =
        if (parsed.unit == AutomationDurationUnit.Weeks) parsed.amount * DAYS_PER_WEEK
        else parsed.amount
    return pluralStringResource(res, count, Numbers.format(count))
}

@Composable
private fun formatEventTrigger(trigger: AutomationTrigger.Event): String {
    val server = trigger.server?.takeIf { it.isNotBlank() }
    val event = trigger.event?.takeIf { it.isNotBlank() }
    return when {
        server != null && event != null ->
            stringResource(Res.string.serverAutomationsTriggerEvent, server, event)
        server != null -> stringResource(Res.string.serverAutomationsTriggerEventAny, server)
        else -> stringResource(Res.string.serverAutomationsOnEvent)
    }
}

@Composable
private fun withOptionalZone(text: String, timeZone: String?): String {
    val cleanZone = timeZone?.trim()?.takeIf { it.isNotEmpty() }
    return if (cleanZone == null) text
    else stringResource(Res.string.serverAutomationsWithZone, text, cleanZone)
}

private val WEEKDAY_FULL =
    listOf(
        Res.string.composerLimitMonday,
        Res.string.composerLimitTuesday,
        Res.string.composerLimitWednesday,
        Res.string.composerLimitThursday,
        Res.string.composerLimitFriday,
        Res.string.composerLimitSaturday,
        Res.string.composerLimitSunday,
    )

private const val TIME_DIGITS = 2
private const val DAYS_PER_WEEK = 7
