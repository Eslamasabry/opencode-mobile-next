package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.time.Duration
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

public enum class KitDurationStyle {
    Standard,
    Compact,
}

/** Flutter KitSince.durationWords on base fe32cd0628: delegates to legacy non-Numbers resources. */
@Composable
public fun kitDurationWords(elapsed: Duration): String {
    val span = elapsed.coerceAtLeast(Duration.ZERO)
    val hours = span.inWholeHours
    val days = span.inWholeDays
    val minutes = span.inWholeMinutes
    return when {
        hours < 1 ->
            when (minutes) {
                0L -> stringResource(Res.string.kitSinceAge__eq0)
                1L -> stringResource(Res.string.kitSinceAge__eq1)
                else -> pluralStringResource(Res.plurals.kitSinceAge, minutes.toInt(), minutes)
            }
        days < 1 -> {
            val remainder = minutes % 60
            if (remainder == 0L) stringResource(Res.string.kitDurationHours, hours)
            else stringResource(Res.string.kitDurationHoursMinutes, hours, remainder)
        }
        else -> {
            val remainder = hours % 24
            if (remainder == 0L) stringResource(Res.string.kitDurationDays, days)
            else stringResource(Res.string.kitDurationDaysHours, days, remainder)
        }
    }
}

@Composable
public fun kitElapsedSecondsMinutes(
    totalSeconds: Long,
    style: KitDurationStyle = KitDurationStyle.Standard,
    nonBreaking: Boolean = false,
    padSeconds: Boolean = false,
): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val mins = (s / 60L).toInt()
    val rem = (s % 60L).toInt()
    return if (style == KitDurationStyle.Compact) {
        val secStr = if (padSeconds) rem.toString().padStart(2, '0') else rem.toString()
        if (s < 60L) stringResource(Res.string.chatUiDurationSeconds, rem)
        else stringResource(Res.string.chatUiDurationMinutesSeconds, mins, secStr)
    } else {
        if (s < 60L) stringResource(Res.string.kitTurnLiveSeconds, rem)
        else stringResource(Res.string.kitTurnLiveMinutes, mins, rem)
    }
}

@Composable
public fun kitTenthsSecondsWords(tenths: String): String =
    stringResource(Res.string.chatUiDurationSeconds, tenths)

@Composable
public fun kitResetInWords(remaining: Duration, sentenceCase: Boolean = true): String {
    val totalMinutes = remaining.inWholeMinutes.coerceAtLeast(1L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> stringResource(Res.string.composerLimitResetsInMinutes, minutes)
        minutes == 0 -> stringResource(Res.string.composerLimitResetsInHours, hours)
        else -> stringResource(Res.string.composerLimitResetsInHoursMinutes, hours, minutes)
    }
}
