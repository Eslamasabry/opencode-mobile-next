package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.common.text.shortCalendarDate
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitLocaleTag
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitRelJustNow
import io.github.eslamasabry.oc.ui.words.res.kitRelShortDays
import io.github.eslamasabry.oc.ui.words.res.kitRelShortHours
import io.github.eslamasabry.oc.ui.words.res.kitRelShortMinutes
import io.github.eslamasabry.oc.ui.words.res.kitRelYesterday
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A minute, an hour and a day, in milliseconds. */
private const val MINUTE_MS: Long = 60_000L
private const val HOUR_MS: Long = 60L * MINUTE_MS
private const val DAY_MS: Long = 24L * HOUR_MS

/** From this many days on, an age is written as a calendar date. */
private const val DATE_AFTER_DAYS: Long = 7L

/**
 * [kitRelativeAge] in its short form, for the time beside a row's title (B-558: "5m", "2h", "3d", in the phone's
 * digits), so a long title keeps its room at large text. A screen reader hears the full words of [kitRelativeAge];
 * just now, yesterday and a date read as they do in full. Future dates clamp to now.
 */
@Composable
public fun kitRelativeAgeShort(atMs: Long, nowMs: Long): String {
    val age = if (atMs >= nowMs) 0L else (nowMs - atMs).takeIf { it >= 0L } ?: Long.MAX_VALUE
    val minutes = age / MINUTE_MS
    val hours = age / HOUR_MS
    val days = age / DAY_MS
    return when {
        minutes < 1L -> stringResource(Res.string.kitRelJustNow)
        hours < 1L -> kitRelativeMinutesShort(minutes)
        days < 1L -> kitRelativeHoursShort(hours)
        days < 2L -> stringResource(Res.string.kitRelYesterday)
        days < DATE_AFTER_DAYS -> kitRelativeDaysShort(days)
        else -> shortCalendarDate(atMs, nowMs, LocalKitLocaleTag.current)
    }
}

/** Localized short minutes ("5m"), with phone digits. */
@Composable
public fun kitRelativeMinutesShort(minutes: Long): String = shortCount(Res.plurals.kitRelShortMinutes, minutes)

/** Localized short hours ("2h"), with phone digits. */
@Composable
public fun kitRelativeHoursShort(hours: Long): String = shortCount(Res.plurals.kitRelShortHours, hours)

/** Localized short days ("3d"), with phone digits. */
@Composable
public fun kitRelativeDaysShort(days: Long): String = shortCount(Res.plurals.kitRelShortDays, days)

/** A count of at least one in one of the short unit forms. */
@Composable
private fun shortCount(form: PluralStringResource, count: Long): String {
    val n = count.coerceAtLeast(1L)
    return pluralStringResource(form, n.toInt(), Numbers.format(n))
}
