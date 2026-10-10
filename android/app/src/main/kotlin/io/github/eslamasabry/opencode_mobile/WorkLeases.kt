package io.github.eslamasabry.opencode_mobile

import java.util.Collections

/**
 * Process-local, bounded CPU-work holds. Idle servers never own a lease.
 *
 * All deadlines use the supplied monotonic clock; no Android or stored owner
 * identity belongs here. Overlapping acquisitions cannot extend the continuous
 * aggregate deadline. A backward, negative or unavailable clock closes existing
 * holds, and admission resumes only when the last observed time is reached.
 *
 * Native callers must retain a closed/capped logical work owner until explicit
 * idle/completion. They must never acquire a replacement token for the same run
 * after renewal fails: this registry cannot distinguish that from new work.
 */
internal class WorkLeases(
    private val nowMs: () -> Long,
    private val maxHoldMs: Long = 900_000L,
    private val maxLifetimeMs: Long = 21_600_000L,
    private val maxLeases: Int = MAX_LEASES,
) {
    enum class Kind { CHAT, SETUP, SIGN_IN, TERMINAL }

    data class Snapshot(
        val activeCount: Int,
        val holdForMs: Long,
        val nextExpiryInMs: Long?,
        val counts: Map<Kind, Int> = emptyMap(),
    )

    private data class Lease(
        val kind: Kind,
        val hardDeadlineMs: Long,
        var expiresAtMs: Long,
    )

    init {
        require(maxHoldMs > 0L && maxLifetimeMs > 0L && maxLeases in 1..MAX_LEASES)
    }

    private val leases = linkedMapOf<Long, Lease>()
    private var lastToken = 0L
    private var lastNowMs: Long? = null
    private var aggregateDeadlineMs: Long? = null

    @Synchronized
    fun acquire(kind: Kind, holdMs: Long): Long? {
        val now = sweep()
        val aggregate = if (now != null) aggregateDeadlineMs ?: checkedAdd(now, maxLifetimeMs) else null
        val open = holdMs > 0L && leases.size < maxLeases && lastToken != Long.MAX_VALUE
        if (now == null || aggregate == null || !open) return null
        val lifetime = minOf(maxLifetimeMs, aggregate - now)
        val hardDeadline = checkedAdd(now, lifetime)
        val expiresAt = checkedAdd(now, minOf(holdMs, maxHoldMs, lifetime))
        return if (hardDeadline == null || expiresAt == null) {
            null
        } else {
            (++lastToken).also {
                leases[it] = Lease(kind, hardDeadline, expiresAt)
                aggregateDeadlineMs = aggregate
            }
        }
    }

    @Synchronized
    fun renew(token: Long, holdMs: Long): Boolean {
        val now = sweep()
        val lease = leases[token]
        val aggregate = aggregateDeadlineMs
        if (now == null || lease == null || aggregate == null) return false
        val duration = minOf(holdMs, maxHoldMs, lease.hardDeadlineMs - now, aggregate - now)
        val expiresAt = if (holdMs > 0L) checkedAdd(now, duration) else null
        if (expiresAt != null) lease.expiresAtMs = expiresAt
        return expiresAt != null
    }

    @Synchronized
    fun release(token: Long): Boolean {
        sweep()
        val removed = leases.remove(token) != null
        resetAggregateWhenIdle()
        return removed
    }

    @Synchronized
    fun releaseKind(kind: Kind) {
        sweep()
        leases.entries.removeAll { it.value.kind == kind }
        resetAggregateWhenIdle()
    }

    @Synchronized
    fun clear() {
        leases.clear()
        aggregateDeadlineMs = null
    }

    @Synchronized
    fun contains(token: Long): Boolean {
        if (sweep() == null) return false
        return token in leases
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val now = sweep()
        val counts = Collections.unmodifiableMap(Kind.entries.associateWith { kind ->
            leases.values.count { it.kind == kind }
        })
        if (now == null || leases.isEmpty()) return Snapshot(0, 0L, null, counts)
        val remaining = leases.values.map { it.expiresAtMs - now }
        return Snapshot(leases.size, remaining.maxOrNull()!!, remaining.minOrNull(), counts)
    }

    /** Invalid time never produces a longer hold or exposes callback errors. */
    private fun sweep(): Long? {
        val now = try { nowMs() } catch (_: Throwable) { null }
        val previous = lastNowMs
        val reversed = previous != null && now != null && now < previous
        if (now == null || now < 0L || reversed) {
            clear()
            return null
        }
        lastNowMs = now
        if (aggregateDeadlineMs?.let { now >= it } == true) {
            clear()
        } else {
            leases.entries.removeAll { now >= it.value.expiresAtMs || now >= it.value.hardDeadlineMs }
            resetAggregateWhenIdle()
        }
        return now
    }

    private fun checkedAdd(start: Long, duration: Long): Long? =
        if (start < 0L || duration <= 0L || start > Long.MAX_VALUE - duration) null else start + duration

    private fun resetAggregateWhenIdle() {
        if (leases.isEmpty()) aggregateDeadlineMs = null
    }

    private companion object {
        const val MAX_LEASES = 128
    }
}
