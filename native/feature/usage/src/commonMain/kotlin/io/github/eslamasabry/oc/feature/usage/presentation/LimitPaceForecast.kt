package io.github.eslamasabry.oc.feature.usage.presentation

import io.github.eslamasabry.oc.engine.usage.UsageCalendar
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Rate unit for the comparison line: per day for multi-day windows, else per hour. */
public enum class PaceRateUnit {
    PerDay,
    PerHour,
}

/** Average rate since the reset paired with the rate needed from now to last until reset. */
public data class PaceRateComparison(
    val unit: PaceRateUnit,
    val sinceResetPercent: Int,
    val neededToLastPercent: Int,
)

/** Window geometry shared by every pace forecast state. */
public data class PaceWindowFrame(
    val elapsed: Double,
    val left: Double,
    val evenLeft: Double,
    val shortWindow: Boolean,
    val axis: ImmutableList<PaceAxisTick>,
)

/** When a limit is projected to run out (or reset), worded on the device's clock when a time zone is known. */
public sealed interface PaceMoment {
    /** Same local calendar day as now; [clock] is `HH:MM`. */
    public data class Today(val clock: String) : PaceMoment

    /** Tomorrow on the local clock; [clock] is `HH:MM`. */
    public data class Tomorrow(val clock: String) : PaceMoment

    /** Another local weekday ([isoDay] is 1 Monday .. 7 Sunday); [clock] is `HH:MM`. */
    public data class Weekday(val isoDay: Int, val clock: String) : PaceMoment

    /** Fallback when no readable device time zone is known: relative time from now. */
    public data class Relative(val days: Long, val hours: Long, val minutes: Long) : PaceMoment
}

/** Localizable label for an axis tick on the pace bend chart. */
public sealed interface PaceTickKind {
    /** Local midnight dividing days (drawn as a hairline tick only, no label). */
    public data object Divider : PaceTickKind

    /** The current day on a multi-day axis. */
    public data object Today : PaceTickKind

    /** Another weekday on a multi-day axis ([isoDay] is 1 Monday .. 7 Sunday). */
    public data class Weekday(val isoDay: Int) : PaceTickKind

    /** Clock time (`HH:MM`) on a short window axis. */
    public data class Clock(val text: String) : PaceTickKind

    /** Current moment on a short window axis. */
    public data object Now : PaceTickKind

    /** Window start fallback when no time zone is available. */
    public data object Start : PaceTickKind

    /** Window reset fallback when no time zone is available. */
    public data object Reset : PaceTickKind
}

/** One tick mark along the bend chart's time axis. */
public data class PaceAxisTick(
    val fraction: Double,
    val kind: PaceTickKind,
)

/** Another subscription account that has spare room to absorb work. */
public data class PaceSwitchHint(
    val accountName: String,
    val leftPercent: Int,
)

/**
 * Local-only pace forecast for a single window (B-508, no-history option A).
 * Computed strictly from the window's current snapshot `(left, resetsAtMs, lengthMs)` at `nowMs`.
 */
public sealed interface PaceForecast {
    /** Shared window coordinates and axis ticks for the bend chart. */
    public val frame: PaceWindowFrame

    /** Too little elapsed time (< 5%) or too little use (<= 1%) to project reliably. */
    public data class TooLittle(
        override val frame: PaceWindowFrame,
    ) : PaceForecast

    /** On course to last until the reset at the average rate since the reset. */
    public data class Lasts(
        override val frame: PaceWindowFrame,
        val atReset: Double,
        val atResetPercent: Int,
    ) : PaceForecast

    /** On course to run out before the reset at the average rate since the reset. */
    public data class RunsOut(
        override val frame: PaceWindowFrame,
        val runsOutElapsed: Double,
        val runsOutAtMs: Long,
        val runsOutMoment: PaceMoment,
        val gapBeforeReset: ResetIn.Left,
        val comparison: PaceRateComparison,
    ) : PaceForecast

    /** Already exhausted (<= 0.5% left) while the reset is still ahead. */
    public data class Out(
        override val frame: PaceWindowFrame,
        val resetsMoment: PaceMoment,
    ) : PaceForecast
}

/** Pure local-only pace forecast arithmetic (B-508, no-history option A). */
public object LimitPaceMath {
    private const val MINUTE_MS: Long = 60_000L
    private const val HALF_MINUTE_MS: Long = 30_000L
    private const val MINUTES_PER_HOUR: Long = 60L
    private const val MINUTES_PER_DAY: Long = 1_440L
    private const val HOUR_MS: Long = 3_600_000L
    private const val DAY_MS: Long = 86_400_000L
    private const val PER_DAY_MIN_MS: Long = 48L * HOUR_MS
    private const val PERCENT_SCALE: Double = 100.0
    private const val FLOAT_EPSILON: Double = 1e-9
    private const val MAX_DAY_SCAN: Int = 10

    /** Minimum share of the window that must have elapsed before projecting a run-out time. */
    public const val MIN_ELAPSED: Double = 0.05

    /** Minimum share of the window that must have been used before projecting a run-out time. */
    public const val MIN_USED: Double = 0.01

    /** Remaining share at or below which the window is treated as exhausted. */
    public const val OUT_EPSILON: Double = 0.005

    /** Minimum projected share left at reset for another account to be suggested as having room. */
    public const val ROOM_THRESHOLD: Double = 0.20

    private data class SnapshotSpan(
        val nowMs: Long,
        val startMs: Long,
        val resetsAtMs: Long,
        val lengthMs: Long,
        val frame: PaceWindowFrame,
        val zone: String?,
    ) {
        val elapsedMs: Long get() = (nowMs - startMs).coerceIn(0L, lengthMs)
        val remainingMs: Long get() = (resetsAtMs - nowMs).coerceAtLeast(0L)
        val used: Double get() = (1.0 - frame.left).coerceIn(0.0, 1.0)
    }

    /**
     * Forecasts pace from the single current reading. Returns null when the window has no reset time,
     * no known length, or the reset time has already passed.
     */
    public fun forecast(
        left: Double,
        nowMs: Long,
        resetsAtMs: Long?,
        lengthMs: Long?,
        zone: String? = null,
    ): PaceForecast? {
        if (resetsAtMs == null || lengthMs == null) return null
        val remainingMs = resetsAtMs - nowMs
        return if (lengthMs <= 0L || remainingMs <= 0L || !left.isFinite()) {
            null
        } else {
            val span = buildSpan(left, nowMs, resetsAtMs, lengthMs, zone)
            when {
                span.frame.left <= OUT_EPSILON -> PaceForecast.Out(span.frame, moment(resetsAtMs, nowMs, zone))
                span.frame.elapsed < MIN_ELAPSED - FLOAT_EPSILON || span.used <= MIN_USED + FLOAT_EPSILON ->
                    PaceForecast.TooLittle(span.frame)
                else -> project(span)
            }
        }
    }

    /** Picks the other account with the most projected room at reset (preferring weekly windows). */
    public fun bestSwitchHint(accounts: List<LimitAccountUi>, exceptKey: String): PaceSwitchHint? =
        accounts
            .filter { it.key != exceptKey }
            .mapNotNull { account ->
                bestRoomWindow(account)?.let { (atReset, leftPercent) ->
                    atReset to PaceSwitchHint(account.name, leftPercent)
                }
            }
            .maxByOrNull { it.first }
            ?.second

    private fun buildSpan(
        left: Double,
        nowMs: Long,
        resetsAtMs: Long,
        lengthMs: Long,
        zone: String?,
    ): SnapshotSpan {
        val clampedLeft = left.coerceIn(0.0, 1.0)
        val startMs = resetsAtMs - lengthMs
        val elapsedMs = (nowMs - startMs).coerceIn(0L, lengthMs)
        val elapsed = elapsedMs.toDouble() / lengthMs.toDouble()
        val evenLeft = (1.0 - elapsed).coerceIn(0.0, 1.0)
        val shortWindow = lengthMs < PER_DAY_MIN_MS
        val axis =
            if (shortWindow) {
                shortAxisTicks(startMs, resetsAtMs, elapsed, zone)
            } else {
                multiDayAxisTicks(startMs, nowMs, resetsAtMs, elapsed, zone)
            }
        val frame = PaceWindowFrame(elapsed, clampedLeft, evenLeft, shortWindow, axis)
        return SnapshotSpan(nowMs, startMs, resetsAtMs, lengthMs, frame, zone)
    }

    private fun bestRoomWindow(account: LimitAccountUi): Pair<Double, Int>? {
        val windows = account.groups.flatMap { it.windows }
        if (windows.any { it.isUsedUp() }) {
            return null
        }
        val lasting =
            windows.mapNotNull { w ->
                val lasts = w.forecast as? PaceForecast.Lasts
                if (lasts != null && lasts.atReset >= ROOM_THRESHOLD) {
                    Triple(!lasts.frame.shortWindow, lasts.atReset, w.leftPercent)
                } else {
                    null
                }
            }
        val preferred = lasting.filter { it.first }.ifEmpty { lasting }
        val top = preferred.maxByOrNull { it.second }
        return top?.let { it.second to it.third }
    }

    private fun project(span: SnapshotSpan): PaceForecast {
        val ratePerMs = span.used / span.elapsedMs.toDouble()
        val atReset = span.frame.left - ratePerMs * span.remainingMs.toDouble()
        val ahead = span.frame.left < span.frame.evenLeft - LimitMath.PACE_SLACK
        val untilOutMs = if (ahead && atReset < 0.0) {
            (span.frame.left / ratePerMs).roundToLong().coerceIn(1L, span.remainingMs)
        } else {
            span.remainingMs
        }
        val gapMs = span.remainingMs - untilOutMs
        return if (!ahead || atReset >= 0.0 || gapMs < MINUTE_MS) {
            val clamped = atReset.coerceIn(0.0, 1.0)
            val pct = (clamped * PERCENT_SCALE).roundToInt().coerceIn(0, PERCENT_SCALE.toInt())
            PaceForecast.Lasts(span.frame, clamped, pct)
        } else {
            val runsOutAtMs = span.nowMs + untilOutMs
            val runsOutElapsed =
                ((runsOutAtMs - span.startMs).toDouble() / span.lengthMs.toDouble()).coerceIn(span.frame.elapsed, 1.0)
            val unit = if (span.frame.shortWindow) PaceRateUnit.PerHour else PaceRateUnit.PerDay
            val unitMs = if (span.frame.shortWindow) HOUR_MS else DAY_MS
            val sinceResetPct = (ratePerMs * unitMs * PERCENT_SCALE).roundToInt().coerceAtLeast(1)
            val neededPerMs = span.frame.left / span.remainingMs.toDouble()
            val neededPct = (neededPerMs * unitMs * PERCENT_SCALE).roundToInt().coerceAtLeast(1)
            PaceForecast.RunsOut(
                frame = span.frame,
                runsOutElapsed = runsOutElapsed,
                runsOutAtMs = runsOutAtMs,
                runsOutMoment = moment(runsOutAtMs, span.nowMs, span.zone),
                gapBeforeReset = roundedDuration(gapMs),
                comparison = PaceRateComparison(unit, sinceResetPct, neededPct),
            )
        }
    }

    private fun roundedDuration(ms: Long): ResetIn.Left {
        val totalMinutes = ((ms + HALF_MINUTE_MS) / MINUTE_MS).coerceAtLeast(1L)
        return ResetIn.Left(
            days = totalMinutes / MINUTES_PER_DAY,
            hours = totalMinutes % MINUTES_PER_DAY / MINUTES_PER_HOUR,
            minutes = totalMinutes % MINUTES_PER_HOUR,
        )
    }

    private fun moment(targetMs: Long, nowMs: Long, zone: String?): PaceMoment {
        val zoned =
            zone?.let { z ->
                val targetStamp = UsageCalendar.stamp(targetMs, z)
                val todayDay = UsageCalendar.dayLabel(nowMs, z)
                val tomorrowStart = UsageCalendar.startOfDayMs(Instant.fromEpochMilliseconds(nowMs), z, -1)
                val tomorrowDay = tomorrowStart?.let { UsageCalendar.dayLabel(it, z) }
                if (targetStamp != null && todayDay != null) {
                    when (targetStamp.day) {
                        todayDay -> PaceMoment.Today(targetStamp.clock)
                        tomorrowDay -> PaceMoment.Tomorrow(targetStamp.clock)
                        else -> PaceMoment.Weekday(targetStamp.weekday, targetStamp.clock)
                    }
                } else {
                    null
                }
            }
        return zoned ?: roundedDuration((targetMs - nowMs).coerceAtLeast(MINUTE_MS)).let {
            PaceMoment.Relative(it.days, it.hours, it.minutes)
        }
    }

    private fun shortAxisTicks(
        startMs: Long,
        resetsAtMs: Long,
        elapsed: Double,
        zone: String?,
    ): ImmutableList<PaceAxisTick> {
        val startStamp = zone?.let { UsageCalendar.stamp(startMs, it) }
        val resetStamp = zone?.let { UsageCalendar.stamp(resetsAtMs, it) }
        return listOf(
            PaceAxisTick(0.0, startStamp?.let { PaceTickKind.Clock(it.clock) } ?: PaceTickKind.Start),
            PaceAxisTick(elapsed, PaceTickKind.Now),
            PaceAxisTick(1.0, resetStamp?.let { PaceTickKind.Clock(it.clock) } ?: PaceTickKind.Reset),
        ).toImmutableList()
    }

    private fun multiDayAxisTicks(
        startMs: Long,
        nowMs: Long,
        resetsAtMs: Long,
        elapsed: Double,
        zone: String?,
    ): ImmutableList<PaceAxisTick> {
        if (zone == null) {
            return listOf(
                PaceAxisTick(0.0, PaceTickKind.Start),
                PaceAxisTick(elapsed, PaceTickKind.Now),
                PaceAxisTick(1.0, PaceTickKind.Reset),
            ).toImmutableList()
        }
        val nowInstant = Instant.fromEpochMilliseconds(nowMs)
        return (MAX_DAY_SCAN downTo -MAX_DAY_SCAN)
            .flatMap { daysBack -> dayAxisTicks(nowInstant, zone, daysBack, startMs, resetsAtMs) }
            .toImmutableList()
    }

    private fun dayAxisTicks(
        nowInstant: Instant,
        zone: String,
        daysBack: Int,
        startMs: Long,
        resetsAtMs: Long,
    ): List<PaceAxisTick> {
        val dayStart = UsageCalendar.startOfDayMs(nowInstant, zone, daysBack)
        val dayEnd = UsageCalendar.startOfDayMs(nowInstant, zone, daysBack - 1)
        if (dayStart == null || dayEnd == null) return emptyList()
        val lengthMs = (resetsAtMs - startMs).coerceAtLeast(1L)
        val divider =
            if (dayStart > startMs && dayStart < resetsAtMs) {
                val frac = ((dayStart - startMs).toDouble() / lengthMs.toDouble()).coerceIn(0.0, 1.0)
                PaceAxisTick(frac, PaceTickKind.Divider)
            } else {
                null
            }
        val a = maxOf(dayStart, startMs)
        val b = minOf(dayEnd, resetsAtMs)
        val label =
            if (b > a) {
                val mid = (((a + b) / 2.0 - startMs) / lengthMs.toDouble()).coerceIn(0.0, 1.0)
                val kind =
                    if (daysBack == 0) {
                        PaceTickKind.Today
                    } else {
                        UsageCalendar.stamp(a, zone)?.let { PaceTickKind.Weekday(it.weekday) }
                    }
                kind?.let { PaceAxisTick(mid, it) }
            } else {
                null
            }
        return listOfNotNull(divider, label)
    }
}

/** Out, running out before its reset, or nothing left with no forecast to say more: not a window to send work to. */
private fun LimitWindowUi.isUsedUp(): Boolean =
    when (forecast) {
        is PaceForecast.Out, is PaceForecast.RunsOut -> true
        null -> leftPercent <= 0
        else -> false
    }
