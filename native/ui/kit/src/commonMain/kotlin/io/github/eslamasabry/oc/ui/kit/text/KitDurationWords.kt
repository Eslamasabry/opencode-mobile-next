package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitApproxMinutes
import io.github.eslamasabry.oc.ui.words.res.kitApproxSeconds
import io.github.eslamasabry.oc.ui.words.res.kitDurCompactDays
import io.github.eslamasabry.oc.ui.words.res.kitDurCompactHours
import io.github.eslamasabry.oc.ui.words.res.kitDurCompactMinutes
import io.github.eslamasabry.oc.ui.words.res.kitDurCompactSeconds
import io.github.eslamasabry.oc.ui.words.res.kitDurDays
import io.github.eslamasabry.oc.ui.words.res.kitDurHours
import io.github.eslamasabry.oc.ui.words.res.kitDurLessThanMinute
import io.github.eslamasabry.oc.ui.words.res.kitDurMinutes
import io.github.eslamasabry.oc.ui.words.res.kitDurPair
import io.github.eslamasabry.oc.ui.words.res.kitDurSeconds
import io.github.eslamasabry.oc.ui.words.res.kitDurTenthsSeconds
import io.github.eslamasabry.oc.ui.words.res.kitResetCompound
import io.github.eslamasabry.oc.ui.words.res.kitResetDays
import io.github.eslamasabry.oc.ui.words.res.kitResetHours
import io.github.eslamasabry.oc.ui.words.res.kitResetLowerCompound
import io.github.eslamasabry.oc.ui.words.res.kitResetLowerDays
import io.github.eslamasabry.oc.ui.words.res.kitResetLowerHours
import io.github.eslamasabry.oc.ui.words.res.kitResetLowerMinutes
import io.github.eslamasabry.oc.ui.words.res.kitResetMinutes
import io.github.eslamasabry.oc.ui.words.res.kitResetNow
import io.github.eslamasabry.oc.ui.words.res.kitResetNowSentence
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Unit compactness for shared duration formatting: spaced units (`3 h 20 min`) or tight units (`13m 36s`, `1h 38m`). */
public enum class KitDurationStyle {
    Standard,
    Compact,
}

/** Formats ASCII digits and decimal separator in [raw] using the phone's [Numbers.symbols]. */
public fun kitFormatNumberText(raw: String): String {
    val symbols = Numbers.symbols()
    if (symbols.zeroDigit == '0' && symbols.decimal == '.') return raw
    return buildString(raw.length) {
        for (ch in raw) {
            when (ch) {
                in '0'..'9' -> append(symbols.zeroDigit + (ch - '0'))
                '.' -> append(symbols.decimal)
                else -> append(ch)
            }
        }
    }
}

/** Localized single-unit seconds text (e.g. `"5 s"`, `"5 ثوانٍ"`, `"5秒"`), with phone digits. */
@Composable
public fun kitSecondsUnitWords(
    seconds: Long,
    style: KitDurationStyle = KitDurationStyle.Standard,
    padTwoDigits: Boolean = false,
): String {
    val s = seconds.coerceAtLeast(0L)
    val count = s.toInt()
    val shown =
        if (padTwoDigits && s < 10L) Numbers.format(0) + Numbers.format(s)
        else Numbers.format(s)
    val res = if (style == KitDurationStyle.Compact) Res.plurals.kitDurCompactSeconds else Res.plurals.kitDurSeconds
    return pluralStringResource(res, count, shown)
}

/** Localized single-unit minutes text (e.g. `"5 min"`, `"5 دقائق"`, `"5分"`), with phone digits. */
@Composable
public fun kitMinutesUnitWords(minutes: Long, style: KitDurationStyle = KitDurationStyle.Standard): String {
    val m = minutes.coerceAtLeast(0L)
    val res = if (style == KitDurationStyle.Compact) Res.plurals.kitDurCompactMinutes else Res.plurals.kitDurMinutes
    return pluralStringResource(res, m.toInt(), Numbers.format(m))
}

/** Localized single-unit hours text (e.g. `"3 h"`, `"3 ساعات"`, `"3時間"`), with phone digits. */
@Composable
public fun kitHoursUnitWords(hours: Long, style: KitDurationStyle = KitDurationStyle.Standard): String {
    val h = hours.coerceAtLeast(0L)
    val res = if (style == KitDurationStyle.Compact) Res.plurals.kitDurCompactHours else Res.plurals.kitDurHours
    return pluralStringResource(res, h.toInt(), Numbers.format(h))
}

/** Localized single-unit days text (e.g. `"2 d"`, `"يومان"`, `"2日"`), with phone digits. */
@Composable
public fun kitDaysUnitWords(days: Long, style: KitDurationStyle = KitDurationStyle.Standard): String {
    val d = days.coerceAtLeast(0L)
    val res = if (style == KitDurationStyle.Compact) Res.plurals.kitDurCompactDays else Res.plurals.kitDurDays
    return pluralStringResource(res, d.toInt(), Numbers.format(d))
}

/** Flutter KitSince.durationWords: largest units at a glance (`<1m` -> `"less than a minute"`, `"45 min"`, `"3 h 20 min"`, `"1 d 21 h"`, `"2 d"`). */
@Composable
public fun kitDurationWords(
    elapsed: Duration,
    style: KitDurationStyle = KitDurationStyle.Standard,
): String {
    val span = if (elapsed.isNegative()) Duration.ZERO else elapsed
    val minutes = span.inWholeMinutes
    if (minutes < 1L) return stringResource(Res.string.kitDurLessThanMinute)
    return kitDurationParts(
        days = span.inWholeDays,
        hours = span.inWholeHours % 24L,
        minutes = minutes % 60L,
        style = style,
    )
}

/** Formats split [days], [hours], [minutes] as at most two largest units in the phone's words and digits. */
@Composable
public fun kitDurationParts(
    days: Long = 0L,
    hours: Long = 0L,
    minutes: Long = 0L,
    style: KitDurationStyle = KitDurationStyle.Standard,
): String =
    when {
        days > 0L && hours > 0L ->
            stringResource(Res.string.kitDurPair, kitDaysUnitWords(days, style), kitHoursUnitWords(hours, style))
        days > 0L -> kitDaysUnitWords(days, style)
        hours > 0L && minutes > 0L ->
            stringResource(Res.string.kitDurPair, kitHoursUnitWords(hours, style), kitMinutesUnitWords(minutes, style))
        hours > 0L -> kitHoursUnitWords(hours, style)
        else -> kitMinutesUnitWords(minutes.coerceAtLeast(1L), style)
    }

/** Shared formatter for minutes+seconds or seconds (`"12 s"`, `"1 min 12 s"`, `"13m 36s"`, `"1m 05s"`). */
@Composable
public fun kitElapsedSecondsMinutes(
    totalSeconds: Long,
    style: KitDurationStyle = KitDurationStyle.Standard,
    nonBreaking: Boolean = false,
    padSeconds: Boolean = false,
): String {
    val clamped = totalSeconds.coerceAtLeast(0L)
    val mins = clamped / 60L
    val secs = clamped % 60L
    val text =
        if (mins < 1L) kitSecondsUnitWords(secs, style, padTwoDigits = false)
        else stringResource(
            Res.string.kitDurPair,
            kitMinutesUnitWords(mins, style),
            kitSecondsUnitWords(secs, style, padTwoDigits = padSeconds),
        )
    return if (nonBreaking) text.replace(' ', '\u00A0') else text
}

/** Localized tenths-of-a-second duration (`"0.8s"`, `"12.4s"`, `"١٢٫٤ ثانية"`, `"12.4秒"`). */
@Composable
public fun kitTenthsSecondsWords(tenths: String): String =
    stringResource(Res.string.kitDurTenthsSeconds, kitFormatNumberText(tenths))

/** Localized spaced decimal-second duration (`"1.2 s"`, `"1.2 ثانية"`, `"1.2秒"`). */
@Composable
public fun kitDecimalSecondsWords(seconds: Double): String {
    val count = kotlin.math.round(seconds).toLong().coerceAtLeast(1L).toInt()
    return pluralStringResource(Res.plurals.kitDurSeconds, count, Numbers.decimal(seconds, 1))
}

/** Approximate wait in seconds (`"about 5 s"`). */
@Composable
public fun kitApproxSecondsWords(seconds: Long): String {
    val safe = seconds.coerceAtLeast(1L)
    return pluralStringResource(Res.plurals.kitApproxSeconds, safe.toInt(), Numbers.format(safe))
}

/** Approximate wait in minutes (`"about 2 min"`). */
@Composable
public fun kitApproxMinutesWords(minutes: Long): String {
    val safe = minutes.coerceAtLeast(1L)
    return pluralStringResource(Res.plurals.kitApproxMinutes, safe.toInt(), Numbers.format(safe))
}

/** Shared countdown words (`"Resets now"`, `"Resets in 5 min"`, `"Resets in 3 h"`, `"Resets in 2 h 10 min"`, `"Resets in 2 days"`). */
@Composable
public fun kitResetInWords(remaining: Duration, sentenceCase: Boolean = true): String {
    val span = if (remaining.isNegative()) Duration.ZERO else remaining
    val totalMinutes = span.inWholeMinutes
    if (totalMinutes <= 0L) {
        return stringResource(if (sentenceCase) Res.string.kitResetNowSentence else Res.string.kitResetNow)
    }
    val days = totalMinutes / (24L * 60L)
    val hours = totalMinutes / 60L
    val remMinutes = totalMinutes % 60L
    return when {
        totalMinutes < 60L -> {
            val res = if (sentenceCase) Res.plurals.kitResetMinutes else Res.plurals.kitResetLowerMinutes
            pluralStringResource(res, totalMinutes.toInt(), Numbers.format(totalMinutes))
        }
        hours >= 48L -> {
            val res = if (sentenceCase) Res.plurals.kitResetDays else Res.plurals.kitResetLowerDays
            pluralStringResource(res, days.toInt(), Numbers.format(days))
        }
        remMinutes == 0L -> {
            val res = if (sentenceCase) Res.plurals.kitResetHours else Res.plurals.kitResetLowerHours
            pluralStringResource(res, hours.toInt(), Numbers.format(hours))
        }
        else -> {
            val inner = kitDurationParts(hours = hours, minutes = remMinutes, style = KitDurationStyle.Standard)
            stringResource(if (sentenceCase) Res.string.kitResetCompound else Res.string.kitResetLowerCompound, inner)
        }
    }
}

/** Formats millisecond duration as at least 1 minute via [kitDurationWords]. */
@Composable
public fun kitMinutesDurationWords(ms: Long, style: KitDurationStyle = KitDurationStyle.Standard): String {
    val minutes = (ms / 60_000L).coerceAtLeast(1L)
    return kitDurationWords((minutes * 60_000L).milliseconds, style)
}
