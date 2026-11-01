package io.github.eslamasabry.oc.engine.delivery.queue

/**
 * What [QueueLimits.enforce] dropped, so the caller can say so instead of letting a prompt vanish (B-STATE-046).
 * Words live in `:ui:words`; this carries counts only.
 */
public data class QueueEviction(
    /** The entries that stay, oldest first. */
    val kept: List<QueuedPrompt>,
    /** Older than [QueueLimits.MAX_AGE_MS]. */
    val expired: Int = 0,
    /** Past [QueueLimits.MAX_ENTRIES]. */
    val overflowed: Int = 0,
    /** Past [QueueLimits.MAX_TOTAL_BYTES]. */
    val oversized: Int = 0,
) {
    val removed: Int get() = expired + overflowed + oversized

    override fun toString(): String = "QueueEviction(kept=${kept.size}, expired=$expired, overflowed=$overflowed, oversized=$oversized)"
}

/** Flutter's `OfflineQueueStore` limits, unchanged (plan 04 §13.3). */
public object QueueLimits {
    /** The composer's aggregate attachment cap: a queue entry can never legitimately be bigger. */
    public const val MAX_ENTRY_BYTES: Long = 20L * 1024 * 1024

    public const val MAX_TOTAL_BYTES: Long = 3 * MAX_ENTRY_BYTES

    public const val MAX_ENTRIES: Int = 50

    public const val MAX_AGE_MS: Long = 14L * 24 * 60 * 60 * 1000

    /** Timestamps below 2000-01-01 are not wall-clock times: an unknown age never deletes a prompt. */
    public const val PLAUSIBLE_EPOCH_FLOOR_MS: Long = 946_684_800_000

    /**
     * Age, count and byte limits, oldest first (`OfflineQueueStore.enforceLimits`). Pure and total: the caller persists
     * [QueueEviction.kept] and reports the counts. A single entry is never evicted into nothing by the byte quota.
     */
    public fun enforce(entries: List<QueuedPrompt>, nowMs: Long): QueueEviction {
        val cutoff = nowMs - MAX_AGE_MS
        val ordered = entries.sortedBy { it.createdAt } // stable, like Dart's List.sort on equal keys in practice
        val fresh = ordered.filter { it.createdAt < PLAUSIBLE_EPOCH_FLOOR_MS || it.createdAt >= cutoff }.toMutableList()
        val expired = ordered.size - fresh.size
        var overflowed = 0
        if (fresh.size > MAX_ENTRIES) {
            overflowed = fresh.size - MAX_ENTRIES
            repeat(overflowed) { fresh.removeAt(0) }
        }
        var oversized = 0
        var total = fresh.sumOf { it.payloadBytes }
        while (fresh.size > 1 && total > MAX_TOTAL_BYTES) {
            total -= fresh.removeAt(0).payloadBytes
            oversized += 1
        }
        return QueueEviction(kept = fresh.toList(), expired = expired, overflowed = overflowed, oversized = oversized)
    }
}
