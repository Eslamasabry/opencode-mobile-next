package io.github.eslamasabry.oc.engine.analytics

import kotlinx.serialization.Serializable

/** One queued event: a [WireEvent] without its session id (sessions are never stored). */
@Serializable
internal data class QueuedEvent(
    val seq: Long,
    val tier: AnalyticsTier,
    val atMs: Long,
    val name: String,
    val hourUtc: Long,
    val install: String,
    val system: SystemProps,
    val props: Map<String, String>,
) {
    fun toWire(session: String): WireEvent = WireEvent(name, hourUtc, install, session, system, props)

    /** Size as it would be sent; the queue's byte bound counts this. */
    val sizeBytes: Int get() = WireCodec.encode(toWire(SIZE_PLACEHOLDER)).encodeToByteArray().size

    private companion object {
        const val SIZE_PLACEHOLDER = "00000000000000000000000000000000"
    }
}

/**
 * The local queue (plan 16 §5.1): at most [maxEvents] events and [maxBytes] bytes, none older than [maxAgeMs]. When
 * full, the oldest events go first. Not thread-safe: only the engine's lane touches it.
 */
internal class EventQueue(
    private val maxEvents: Int = MAX_EVENTS,
    private val maxBytes: Int = MAX_BYTES,
    private val maxAgeMs: Long = MAX_AGE_MS,
) {
    private val items = ArrayDeque<Pair<QueuedEvent, Int>>()
    var bytes: Int = 0
        private set

    val size: Int get() = items.size

    fun snapshot(): List<QueuedEvent> = items.map { it.first }

    fun restore(events: List<QueuedEvent>) {
        clear()
        events.sortedBy { it.seq }.forEach(::add)
    }

    fun add(event: QueuedEvent) {
        val size = event.sizeBytes
        if (size > maxBytes) return
        items.addLast(event to size)
        bytes += size
        while (items.size > maxEvents || bytes > maxBytes) removeFirst()
    }

    fun expire(nowMs: Long): Int {
        val before = items.size
        while (items.isNotEmpty() && nowMs - items.first().first.atMs > maxAgeMs) removeFirst()
        // Events stamped in the future (a clock that jumped back) are kept; they age out once the clock catches up.
        return before - items.size
    }

    fun dropTier(tier: AnalyticsTier): Int {
        val keep = items.filter { it.first.tier != tier }
        val dropped = items.size - keep.size
        items.clear()
        items.addAll(keep)
        bytes = keep.sumOf { it.second }
        return dropped
    }

    fun batch(max: Int = MAX_BATCH): List<QueuedEvent> = items.take(max).map { it.first }

    fun remove(seqs: Set<Long>) {
        val keep = items.filter { it.first.seq !in seqs }
        items.clear()
        items.addAll(keep)
        bytes = keep.sumOf { it.second }
    }

    fun clear() {
        items.clear()
        bytes = 0
    }

    private fun removeFirst() {
        val (_, size) = items.removeFirst()
        bytes -= size
    }

    companion object {
        const val MAX_EVENTS: Int = 500
        const val MAX_BYTES: Int = 256 * 1024
        const val MAX_AGE_MS: Long = 7 * DAY_MS
        const val MAX_BATCH: Int = 25
    }
}
