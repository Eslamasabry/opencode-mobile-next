package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.common.text.shortCalendarDate
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitLocaleTag
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitRelDaysAgo
import io.github.eslamasabry.oc.ui.words.res.kitRelHoursAgo
import io.github.eslamasabry.oc.ui.words.res.kitRelJustNow
import io.github.eslamasabry.oc.ui.words.res.kitRelMinutesAgo
import io.github.eslamasabry.oc.ui.words.res.kitRelShortDays
import io.github.eslamasabry.oc.ui.words.res.kitRelShortHours
import io.github.eslamasabry.oc.ui.words.res.kitRelShortMinutes
import io.github.eslamasabry.oc.ui.words.res.kitRelYesterday
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Shared relative-time formatter across the app: future dates clamp to now; a week or more uses a local short date. */
@Composable
public fun kitRelativeAge(atMs: Long, nowMs: Long): String =
    relativeAge(atMs, nowMs, { kitRelativeMinutesAgo(it) }, { kitRelativeHoursAgo(it) }, { kitRelativeDaysAgo(it) })

/**
 * [kitRelativeAge] in its short form, for the time beside a row's title (B-558: "5m", "2h", "3d", in the phone's
 * digits), so a long title keeps its room at large text. A screen reader hears the full words of [kitRelativeAge];
 * just now, yesterday and a date read as they do in full.
 */
@Composable
public fun kitRelativeAgeShort(atMs: Long, nowMs: Long): String =
    relativeAge(
        atMs,
        nowMs,
        { kitRelativeMinutesShort(it) },
        { kitRelativeHoursShort(it) },
        { kitRelativeDaysShort(it) },
    )

/** The two forms of a relative age: which words say its minutes, its hours and its days. */
@Composable
private fun relativeAge(
    atMs: Long,
    nowMs: Long,
    minutes: @Composable (Long) -> String,
    hours: @Composable (Long) -> String,
    days: @Composable (Long) -> String,
): String {
    val age = if (atMs >= nowMs) 0L else (nowMs - atMs).takeIf { it >= 0L } ?: Long.MAX_VALUE
    val mins = age / 60_000L
    val hrs = age / 3_600_000L
    val ds = age / 86_400_000L
    return when {
        mins < 1L -> stringResource(Res.string.kitRelJustNow)
        hrs < 1L -> minutes(mins)
        ds < 1L -> hours(hrs)
        ds < 2L -> stringResource(Res.string.kitRelYesterday)
        ds < 7L -> days(ds)
        else -> shortCalendarDate(atMs, nowMs, LocalKitLocaleTag.current)
    }
}

/** Formats whole [ageMinutes] as just-now / minutes / hours / days ago using the shared plural resources. */
@Composable
public fun kitRelativeAgeFromMinutes(ageMinutes: Long): String =
    when {
        ageMinutes < 1L -> stringResource(Res.string.kitRelJustNow)
        ageMinutes < 60L -> kitRelativeMinutesAgo(ageMinutes)
        ageMinutes < 1440L -> kitRelativeHoursAgo(ageMinutes / 60L)
        else -> kitRelativeDaysAgo(ageMinutes / 1440L)
    }

/** [kitRelativeAgeFromMinutes] in the short form of [kitRelativeAgeShort]. */
@Composable
public fun kitRelativeAgeFromMinutesShort(ageMinutes: Long): String =
    when {
        ageMinutes < 1L -> stringResource(Res.string.kitRelJustNow)
        ageMinutes < 60L -> kitRelativeMinutesShort(ageMinutes)
        ageMinutes < 1440L -> kitRelativeHoursShort(ageMinutes / 60L)
        else -> kitRelativeDaysShort(ageMinutes / 1440L)
    }

/** Localized `"N min ago"` with CLDR plurals and phone digits. */
@Composable
public fun kitRelativeMinutesAgo(minutes: Long): String {
    val m = minutes.coerceAtLeast(1L)
    return pluralStringResource(Res.plurals.kitRelMinutesAgo, m.toInt(), Numbers.format(m))
}

/** Localized `"Nh ago"` with CLDR plurals and phone digits. */
@Composable
public fun kitRelativeHoursAgo(hours: Long): String {
    val h = hours.coerceAtLeast(1L)
    return pluralStringResource(Res.plurals.kitRelHoursAgo, h.toInt(), Numbers.format(h))
}

/** Localized `"Nd ago"` with CLDR plurals and phone digits. */
@Composable
public fun kitRelativeDaysAgo(days: Long): String {
    val d = days.coerceAtLeast(1L)
    return pluralStringResource(Res.plurals.kitRelDaysAgo, d.toInt(), Numbers.format(d))
}

/** Localized short `"Nm"`, with phone digits. */
@Composable
public fun kitRelativeMinutesShort(minutes: Long): String = shortCount(Res.plurals.kitRelShortMinutes, minutes)

/** Localized short `"Nh"`, with phone digits. */
@Composable
public fun kitRelativeHoursShort(hours: Long): String = shortCount(Res.plurals.kitRelShortHours, hours)

/** Localized short `"Nd"`, with phone digits. */
@Composable
public fun kitRelativeDaysShort(days: Long): String = shortCount(Res.plurals.kitRelShortDays, days)

/** A count of at least one in one of the short unit forms ("5m"). */
@Composable
private fun shortCount(form: PluralStringResource, count: Long): String {
    val n = count.coerceAtLeast(1L)
    return pluralStringResource(form, n.toInt(), Numbers.format(n))
}

/** KitTime's locale-neutral selection; the platform supplies local calendar/clock formatting.
 * All comparisons and formatters receive the same local value (kit_time.dart:42-57).
 */
public object KitTime {
    public fun <T> clockWith(at: T, use24h: Boolean, toLocal: (T) -> T, formatTime: (T, Boolean) -> String): String =
        formatTime(toLocal(at), use24h)

    public fun <T> momentWith(
        at: T,
        now: T,
        use24h: Boolean,
        toLocal: (T) -> T,
        sameDay: (T, T) -> Boolean,
        sameYear: (T, T) -> Boolean,
        formatTime: (T, Boolean) -> String,
        formatMonthDay: (T) -> String,
        formatDate: (T) -> String,
    ): String {
        val local = toLocal(at)
        val today = toLocal(now)
        val time = formatTime(local, use24h)
        if (sameDay(local, today)) return time
        val day = if (sameYear(local, today)) formatMonthDay(local) else formatDate(local)
        return "$day, $time"
    }
}
