package io.github.eslamasabry.oc.engine.plans

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal const val SEC = 1_000L
internal const val MIN = 60_000L
internal const val HOUR = 3_600_000L
internal const val DAY = 86_400_000L

/** Epoch milliseconds of a UTC wall time, like Dart's DateTime.utc. */
internal fun utc(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.UTC).toEpochMilliseconds()
