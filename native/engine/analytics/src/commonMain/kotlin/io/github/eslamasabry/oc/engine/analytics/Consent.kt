package io.github.eslamasabry.oc.engine.analytics

import kotlinx.serialization.Serializable

/**
 * The person's answer about DETAILED. BASIC has no consent state: it is always on, anonymous and disclosed (owner,
 * 2026-11-01). Anything other than [Granted] means DETAILED events are dropped before they reach the queue.
 */
public enum class DetailedConsent { NotAsked, Granted, Declined }

@Serializable
public data class ConsentState(val detailed: DetailedConsent = DetailedConsent.NotAsked) {
    val detailedOn: Boolean get() = detailed == DetailedConsent.Granted

    public fun permits(tier: AnalyticsTier): Boolean =
        when (tier) {
            AnalyticsTier.Basic -> true
            AnalyticsTier.Detailed -> detailedOn
        }
}

/** Counted actions for BASIC `daily_use`. Each is a count only, rolled into buckets once per UTC day. */
public enum class UsageCounter { AppOpen, ChatStarted, MessageSent }

/**
 * Today's counts (UTC day number). When the day rolls, the previous day becomes one `daily_use` event plus
 * `server_kinds` and `agent_kinds` from the last inventory, and the ledger starts again.
 */
@Serializable
internal data class DailyLedger(
    val day: Long,
    val counts: Map<UsageCounter, Int> = emptyMap(),
    val servers: Map<ServerKind, Int>? = null,
    val agents: Map<AgentKind, Int>? = null,
    val connectedKinds: Set<ServerKind> = emptySet(),
) {
    fun count(counter: UsageCounter): DailyLedger =
        copy(counts = counts + (counter to ((counts[counter] ?: 0) + 1).coerceAtMost(CAP)))

    /** The events summarising this ledger's day, or none when nothing happened and no inventory was reported. */
    fun summary(): List<AnalyticsEvent> {
        val out = mutableListOf<AnalyticsEvent>()
        if (counts.values.any { it > 0 }) {
            out +=
                DailyUse(
                    opens = CountBucket.of(counts[UsageCounter.AppOpen] ?: 0),
                    chatsStarted = CountBucket.of(counts[UsageCounter.ChatStarted] ?: 0),
                    messagesSent = CountBucket.of(counts[UsageCounter.MessageSent] ?: 0),
                )
        }
        servers?.let { out += ServerKinds.of(it) }
        agents?.let { out += AgentKinds.of(it) }
        return out
    }

    /** A new day keeps the last inventory (it is still true) and clears counts and the connect dedupe. */
    fun next(day: Long): DailyLedger = DailyLedger(day = day, servers = servers, agents = agents)

    companion object {
        private const val CAP = 1_000_000

        fun dayOf(epochMs: Long): Long = epochMs.floorDiv(DAY_MS)
    }
}
