package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.time.Duration
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Flutter KitSince.durationWords: the largest units that read at a glance; never polls a clock. */
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
            val remainder = minutes % 60 // F: lib/ui/kit/kit_since.dart:154
            if (remainder == 0L) stringResource(Res.string.kitDurationHours, hours)
            else stringResource(Res.string.kitDurationHoursMinutes, hours, remainder)
        }
        else -> {
            val remainder = hours % 24 // F: lib/ui/kit/kit_since.dart:159
            if (remainder == 0L) stringResource(Res.string.kitDurationDays, days)
            else stringResource(Res.string.kitDurationDaysHours, days, remainder)
        }
    }
}
