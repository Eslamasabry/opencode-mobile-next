package io.github.eslamasabry.oc.engine.plans

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// Port of lib/domain/quota_answers.dart and the parts of lib/domain/usage_statistics.dart the answers use.
// Times are epoch milliseconds (UTC).

/** The authority that measured remaining provider quota. */
public enum class QuotaAnswerSource { CodexAccount, Collector }

/** Availability of a fresh quota answer, independent of a retained snapshot. */
public enum class QuotaAnswerStatus {
    Ready, Unavailable, NeedsSignIn, NeedsCollector, CollectorAuth, Unsupported, InvalidResponse,
}

/** Numeric facts for one provider quota window; presentation localizes them. */
public data class QuotaAnswerWindow(
    val id: String,
    val usedPercent: Double,
    val durationMinutes: Int? = null,
    val resetsAt: Long? = null,
) {
    val remainingPercent: Double get() = (100 - usedPercent).coerceIn(0.0, 100.0)
    val isWeekly: Boolean get() = durationMinutes == 10080
}

/**
 * A measurement whose observation time must survive offline reads unchanged. [observedAt] is when the source measured
 * the value, not when the UI read it. An expired snapshot is historical evidence, never a fresh quota promise.
 */
public class QuotaAnswerSnapshot(
    public val source: QuotaAnswerSource,
    public val observedAt: Long,
    windows: List<QuotaAnswerWindow>,
) {
    /** A read-only copy: later changes to the caller's list do not reach it. */
    public val windows: List<QuotaAnswerWindow> = ReadOnlyList(windows.toList())

    /** Clamps clock skew to zero without changing the stored observation time. */
    public fun age(now: Long): Duration = (now - observedAt).coerceAtLeast(0).milliseconds

    /** True as soon as any known window reset has been reached. A missing reset time does not imply freshness. */
    public fun isExpired(now: Long): Boolean = windows.any { it.resetsAt != null && now >= it.resetsAt }
}

public enum class UsageRange { Today, ThirtyDays, Year, AllTime }

/** A usage-statistics query: [from] inclusive (null for all time), [to] exclusive. */
public data class UsageQuery(val from: Long? = null, val to: Long, val timezone: String, val projectId: String? = null)

/** The interval a usage-statistics response says it covers. */
public data class UsageStatisticsRange(val from: Long, val to: Long)

/**
 * The actual interval covered by a Spent answer, independent of its selector. [from] inclusive and [to] exclusive. When
 * [matchesRequestedRange] is false, label the returned interval rather than presenting [requestedRange].
 */
public data class SpentPeriod(
    val from: Long,
    val to: Long,
    val requestedRange: UsageRange,
    val matchesRequestedRange: Boolean,
) {
    public companion object {
        /**
         * Uses the response's interval. An all-time query has no requested lower bound: its returned start may be the
         * first available record. The upper bound must still match. Other ranges require both bounds to match.
         */
        public fun fromUsage(
            query: UsageQuery,
            statistics: UsageStatisticsRange,
            requestedRange: UsageRange,
        ): SpentPeriod =
            SpentPeriod(
                from = statistics.from,
                to = statistics.to,
                requestedRange = requestedRange,
                matchesRequestedRange =
                    statistics.to == query.to &&
                        (statistics.from == query.from || (requestedRange == UsageRange.AllTime && query.from == null)),
            )
    }
}

/** A list view that cannot be cast back to a mutable list. */
internal class ReadOnlyList<T>(private val items: List<T>) : AbstractList<T>() {
    override val size: Int get() = items.size

    override fun get(index: Int): T = items[index]
}
