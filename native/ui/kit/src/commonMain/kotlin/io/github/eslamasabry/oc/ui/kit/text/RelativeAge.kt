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
import io.github.eslamasabry.oc.ui.words.res.kitRelYesterday
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Shared relative-time formatter across the app: future dates clamp to now; a week or more uses a local short date. */
@Composable
public fun kitRelativeAge(atMs: Long, nowMs: Long): String {
    val age = if (atMs >= nowMs) 0L else (nowMs - atMs).takeIf { it >= 0L } ?: Long.MAX_VALUE
    val minutes = age / 60_000L
    val hours = age / 3_600_000L
    val days = age / 86_400_000L
    return when {
        minutes < 1L -> stringResource(Res.string.kitRelJustNow)
        hours < 1L -> kitRelativeMinutesAgo(minutes)
        days < 1L -> kitRelativeHoursAgo(hours)
        days < 2L -> stringResource(Res.string.kitRelYesterday)
        days < 7L -> kitRelativeDaysAgo(days)
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
