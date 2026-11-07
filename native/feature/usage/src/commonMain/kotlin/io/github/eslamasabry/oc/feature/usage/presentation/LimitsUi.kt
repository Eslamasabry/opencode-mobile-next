package io.github.eslamasabry.oc.feature.usage.presentation

import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindow
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
import io.github.eslamasabry.oc.engine.usage.UsageCalendar
import io.github.eslamasabry.oc.engine.usage.UsageStamp
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/*
 * Usage › Limits (B-159): every plan account's rolling windows as "N% left", a reset countdown and a pace marker.
 * The numbers are the ones the Plans page and the home-screen usage widget show: the same UsageSnapshot the engine
 * (:engine:integrations-usage) merges from OpenCode sign-ins, the phone's agents, Paseo, computer readers and the
 * quota collector. Nothing here invents a number: a window with no reset time has no countdown and no pace; an
 * account that reports no windows says so.
 */

/** The time left until a window resets, split for "resets in 1h 38m". */
public sealed interface ResetIn {
    /** The reset time has passed: the numbers are from the previous window until the next read. */
    public data object Passed : ResetIn

    /** Under one minute. */
    public data object UnderMinute : ResetIn

    public data class Left(val days: Long, val hours: Long, val minutes: Long) : ResetIn
}

public enum class LimitPace {
    /** Less is left than an even pace would leave. */
    Ahead,

    /** As much or more is left than an even pace would leave. */
    WithinPace,

    /** No pace can be stated (no reset time, no window length, or the reset has passed). */
    Unknown,
}

/** Pure arithmetic for the Limits view. */
public object LimitMath {
    public const val FIVE_HOURS_MS: Long = 5L * 3_600_000
    public const val WEEK_MS: Long = 7L * 86_400_000

    /** Readings older than this show their age even when nothing else is wrong. */
    public const val STALE_AFTER_MS: Long = 15L * 60_000

    /** Half a percent: a reading exactly on pace never flickers between Ahead and WithinPace. */
    public const val PACE_SLACK: Double = 0.005

    /**
     * The window's length: what the source reported, else the fixed length its kind implies (five hours, one
     * week). A month has no fixed length and an unknown window has none, so they get no pace.
     */
    public fun lengthMs(window: UsageWindow): Long? =
        window.durationSeconds?.takeIf { it > 0 }?.let { it * 1_000 }
            ?: when (window.kind) {
                UsageWindowKind.FiveHour -> FIVE_HOURS_MS
                UsageWindowKind.Weekly -> WEEK_MS
                UsageWindowKind.Monthly,
                UsageWindowKind.Other -> null
            }

    /**
     * The share of the window that has gone by, 0..1: (length − time to reset) / length. Null when there is no reset
     * time or length, or the reset has passed (the next window has started and its start is not known yet).
     */
    public fun elapsed(nowMs: Long, resetsAtMs: Long?, lengthMs: Long?): Double? {
        if (resetsAtMs == null || lengthMs == null || lengthMs <= 0) return null
        val remaining = resetsAtMs - nowMs
        if (remaining <= 0) return null
        return ((lengthMs - remaining).toDouble() / lengthMs).coerceIn(0.0, 1.0)
    }

    /** Where the left-fill would end if the window had been spent evenly so far: 1 − elapsed. */
    public fun evenLeft(nowMs: Long, resetsAtMs: Long?, lengthMs: Long?): Double? =
        elapsed(nowMs, resetsAtMs, lengthMs)?.let { 1.0 - it }

    public fun pace(leftShare: Double, evenLeft: Double?): LimitPace =
        when {
            evenLeft == null || !leftShare.isFinite() -> LimitPace.Unknown
            leftShare < evenLeft - PACE_SLACK -> LimitPace.Ahead
            else -> LimitPace.WithinPace
        }

    /** The countdown: whole minutes, rounded down, split into days, hours and minutes. */
    public fun resetIn(nowMs: Long, resetsAtMs: Long): ResetIn {
        val remaining = resetsAtMs - nowMs
        if (remaining <= 0) return ResetIn.Passed
        val minutes = remaining / 60_000
        if (minutes == 0L) return ResetIn.UnderMinute
        return ResetIn.Left(days = minutes / 1_440, hours = minutes % 1_440 / 60, minutes = minutes % 60)
    }
}

/** One window as the Limits view shows it. */
public data class LimitWindowUi(
    val id: String,
    val kind: UsageWindowKind,
    /** The source's own name for this limit (a model or tier), blank when it gave none. */
    val label: String,
    /** Whole percent left, 0..100. */
    val leftPercent: Int,
    /** The exact share left, 0..1, for the bar. */
    val left: Double,
    /** Where an even pace would leave the fill, 0..1; null when no pace can be stated. */
    val evenLeft: Double?,
    val pace: LimitPace,
    val resetsAtMs: Long?,
    val resetIn: ResetIn?,
    /** The reset on the device's clock, for Details; null without a reset time or a readable time zone. */
    val resetsAt: UsageStamp? = null,
)

/** The limits that share one window length (the five-hour window, the week, …). */
public data class LimitGroupUi(
    val kind: UsageWindowKind,
    val lengthMs: Long?,
    val windows: ImmutableList<LimitWindowUi>,
) {
    /** Several limits in one window (models, tiers) are listed compactly in two columns. */
    public val compact: Boolean
        get() = windows.size > 1
}

/** One plan account (a provider signed in somewhere). */
public data class LimitAccountUi(
    val key: String,
    val providerId: String,
    val name: String,
    /** Named only when the same provider is signed in on more than one connection. */
    val connection: String?,
    val connectionIsPhone: Boolean,
    val plan: String?,
    val state: SubscriptionState,
    val groups: ImmutableList<LimitGroupUi>,
    val fetchedAtMs: Long?,
    /** When the numbers were read, on the device's clock (Details). */
    val fetchedAt: UsageStamp? = null,
    /** True when the numbers are old enough (or offline, or past a reset) that their age must show. */
    val stale: Boolean,
) {
    /** The account answered but reports no windows at all: the view says so and shows no numbers. */
    public val reportsNothing: Boolean
        get() = groups.isEmpty()
}

/** A pay-as-you-go key: it has no plan limits to show. */
public data class LimitKeyUi(val name: String, val connection: String, val connectionIsPhone: Boolean)

public enum class LimitsPhase {
    Loading,
    Empty,
    List,
}

public data class LimitsUi(
    val phase: LimitsPhase = LimitsPhase.Loading,
    val accounts: ImmutableList<LimitAccountUi> = persistentListOf(),
    val keys: ImmutableList<LimitKeyUi> = persistentListOf(),
    val refreshing: Boolean = false,
    val nowMs: Long = 0,
)

public interface LimitsActions {
    /** The visible Refresh and pull-to-refresh: reads every source now. */
    public fun refresh()
}

private fun UsageConnection.isPhone(): Boolean =
    kind == UsageConnectionKind.PhoneAgents || id == UsageConnection.PHONE_ID

private val kindOrder =
    listOf(UsageWindowKind.FiveHour, UsageWindowKind.Weekly, UsageWindowKind.Monthly, UsageWindowKind.Other)

/**
 * Pure: the engine's snapshot as the Limits view's state at [nowMs]. [timezone] (the device's IANA zone) dates the
 * absolute times under Details; without it they are left out, never written in UTC.
 */
public fun limitsUi(snapshot: UsageSnapshot, refreshing: Boolean, nowMs: Long, timezone: String? = null): LimitsUi {
    val zone = timezone?.takeIf { it.isNotBlank() && UsageCalendar.isReadable(it) }
    val accounts =
        snapshot.subscriptions
            .sortedBy { s -> s.leastLeftPercent ?: 101.0 }
            .map { s -> account(s, nowMs, zone) }
    val keys =
        snapshot.apiKeyAccounts.map {
            LimitKeyUi(it.displayName, it.connection.name, it.connection.isPhone())
        }
    val phase =
        when {
            !snapshot.loaded && accounts.isEmpty() -> LimitsPhase.Loading
            accounts.isEmpty() && keys.isEmpty() -> LimitsPhase.Empty
            else -> LimitsPhase.List
        }
    return LimitsUi(phase, accounts.toImmutableList(), keys.toImmutableList(), refreshing, nowMs)
}

private fun account(s: UsageSubscription, nowMs: Long, zone: String?): LimitAccountUi {
    val where = if (s.showConnectionName) s.readFrom ?: s.signedInOn.firstOrNull()?.connection else null
    val windows = s.windows.map { window(it, nowMs, zone) to LimitMath.lengthMs(it) }
    val groups =
        windows
            .groupBy { (w, length) -> Triple(w.kind, length, if (length == null && w.kind == UsageWindowKind.Other) w.id else "") }
            .entries
            // Kind first (5 hours, week, month, other), then the shorter window; sortedBy is stable.
            .sortedBy { it.key.second ?: Long.MAX_VALUE }
            .sortedBy { kindOrder.indexOf(it.key.first) }
            .map { (key, members) -> LimitGroupUi(key.first, key.second, members.map { it.first }.toImmutableList()) }
    val fetched = s.fetchedAt?.toEpochMilliseconds()
    val stale =
        s.state == SubscriptionState.OfflineStale ||
            s.resetSinceLastReading ||
            (fetched != null && nowMs - fetched > LimitMath.STALE_AFTER_MS) ||
            s.windows.any { w -> w.resetsAt?.let { it.toEpochMilliseconds() <= nowMs } == true }
    return LimitAccountUi(
        key = s.key,
        providerId = s.providerId,
        name = s.displayName,
        connection = where?.name,
        connectionIsPhone = where?.isPhone() == true,
        plan = s.planLabel?.takeIf { it.isNotBlank() },
        state = s.state,
        groups = groups.toImmutableList(),
        fetchedAtMs = fetched,
        fetchedAt = fetched?.let { at -> zone?.let { UsageCalendar.stamp(at, it) } },
        stale = stale,
    )
}

private fun window(w: UsageWindow, nowMs: Long, zone: String?): LimitWindowUi {
    val left = (w.leftPercent / 100.0).coerceIn(0.0, 1.0)
    val resets = w.resetsAt?.toEpochMilliseconds()
    val even = LimitMath.evenLeft(nowMs, resets, LimitMath.lengthMs(w))
    return LimitWindowUi(
        id = w.id,
        kind = w.kind,
        label = w.label,
        leftPercent = w.leftPercent.roundToInt().coerceIn(0, 100),
        left = left,
        evenLeft = even,
        pace = LimitMath.pace(left, even),
        resetsAtMs = resets,
        resetIn = resets?.let { LimitMath.resetIn(nowMs, it) },
        resetsAt = resets?.let { at -> zone?.let { UsageCalendar.stamp(at, it) } },
    )
}
