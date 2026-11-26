package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Identifies one subscription's rolling window in [UsageHistory]. */
public data class WindowKey(
    val subscriptionKey: String,
    val windowId: String,
)

/** One recorded sample in a window's reading history. */
public data class UsageSample(
    val fetchedAt: Instant,
    val usedPercent: Double,
    val resetsAt: Instant?,
)

/**
 * Persistent per-window usage reading history for recent-rate pace charts and previous-window
 * details (`BUILD-SPEC.md §4.2`, `DESIGN.md §10`). Stored per connection under
 * `oc.usageHistory.<connectionId>` next to `oc.usageLast.<connectionId>` so profile deletion
 * removes it cleanly.
 */
public class UsageHistory(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    private val clock: () -> Instant,
    initialConnections: Collection<String> = emptyList(),
) {
    private val lock = Mutex()
    private val byConnection = LinkedHashMap<String, MutableMap<WindowKey, List<UsageSample>>>()
    private val loadedConnections = HashSet<String>()
    private val _samples = MutableStateFlow<Map<WindowKey, List<UsageSample>>>(emptyMap())

    /** Reactive view of every loaded window's ordered samples. */
    public val samples: StateFlow<Map<WindowKey, List<UsageSample>>> = _samples.asStateFlow()

    init {
        for (connectionId in initialConnections) ensureLoadedLocked(connectionId)
    }

    /** Loads persisted history for [connectionId] once under [lock]; corrupt/missing data is empty. */
    public suspend fun loadConnection(connectionId: String): Unit = lock.withLock {
        ensureLoadedLocked(connectionId)
    }

    private fun ensureLoadedLocked(connectionId: String) {
        if (connectionId.isEmpty() || !loadedConnections.add(connectionId)) return
        val decoded = decodeUsageHistoryConnection(store.string(key(connectionId)), MAX_SAMPLES_PER_WINDOW)
        pruneConnectionSlot(decoded, clock())
        if (decoded.isNotEmpty()) {
            byConnection[connectionId] = decoded
            _samples.value = mergeUsageHistory(byConnection, MAX_SAMPLES_PER_WINDOW, DEDUPE_REFRESH_INTERVAL)
        }
    }

    public suspend fun recordSample(
        connectionId: String,
        key: WindowKey,
        sample: UsageSample,
    ): Boolean = recordBatch(connectionId, listOf(key to sample))

    public suspend fun recordSample(
        connectionId: String,
        key: WindowKey,
        usedPercent: Double,
        resetsAt: Instant?,
        fetchedAt: Instant = clock(),
    ): Boolean = recordSample(connectionId, key, UsageSample(fetchedAt, usedPercent, resetsAt))

    public suspend fun recordReadings(readings: List<UsageReading>) {
        for ((connectionId, updates) in collectReadingSamples(readings)) {
            recordBatch(connectionId, updates)
        }
    }

    public suspend fun recordProviderReadings(
        connectionId: String,
        readings: List<ProviderUsageReading>,
    ) {
        val updates = collectProviderSamples(connectionId, readings)
        if (updates.isNotEmpty()) recordBatch(connectionId, updates)
    }

    public suspend fun recordSnapshot(snapshot: UsageSnapshot) {
        for ((connectionId, updates) in collectSnapshotSamples(snapshot)) {
            recordBatch(connectionId, updates)
        }
    }

    /** Deletes persisted and in-memory history for [connectionId]. */
    public suspend fun removeProfile(connectionId: String): Boolean = lock.withLock {
        byConnection.remove(connectionId)
        loadedConnections -= connectionId
        val ok = store.commit(lane) { remove(key(connectionId)) } is Outcome.Ok
        _samples.value = mergeUsageHistory(byConnection, MAX_SAMPLES_PER_WINDOW, DEDUPE_REFRESH_INTERVAL)
        ok
    }

    /** Drops history for [providerId] on [connectionId] (e.g. consent withdrawn). */
    public suspend fun removeProvider(connectionId: String, providerId: String): Boolean = lock.withLock {
        ensureLoadedLocked(connectionId)
        val slot = byConnection[connectionId]
        val previous = slot?.toMap()
        val prefix = "$providerId|"
        val removed = slot != null && slot.keys.removeAll { it.subscriptionKey.startsWith(prefix) }
        if (!removed) {
            true
        } else {
            val ok = persistConnection(store, lane, byConnection, connectionId)
            if (!ok && previous != null) {
                byConnection[connectionId] = LinkedHashMap(previous)
            }
            _samples.value = mergeUsageHistory(byConnection, MAX_SAMPLES_PER_WINDOW, DEDUPE_REFRESH_INTERVAL)
            ok
        }
    }

    private suspend fun recordBatch(
        connectionId: String,
        updates: List<Pair<WindowKey, UsageSample>>,
    ): Boolean = lock.withLock {
        var result = true
        if (connectionId.isNotEmpty() && updates.isNotEmpty()) {
            ensureLoadedLocked(connectionId)
            val slot = byConnection.getOrPut(connectionId) { LinkedHashMap() }
            if (applyWindowUpdates(slot, updates, clock())) {
                result = persistConnection(store, lane, byConnection, connectionId)
                _samples.value =
                    mergeUsageHistory(byConnection, MAX_SAMPLES_PER_WINDOW, DEDUPE_REFRESH_INTERVAL)
            }
        }
        result
    }

    public companion object {
        public const val KEY_PREFIX: String = "oc.usageHistory."
        public const val MAX_SAMPLES_PER_WINDOW: Int = 300
        public val MAX_SAMPLE_AGE: Duration = 90.days
        public val DEDUPE_REFRESH_INTERVAL: Duration = 15.minutes
        private const val MIN_PERCENT: Double = 0.0
        private const val MAX_PERCENT: Double = 100.0
        private const val VALUE_EPSILON: Double = 1e-6

        public fun key(connectionId: String): String = "$KEY_PREFIX$connectionId"

        /** Drops samples strictly older than [maxAge] relative to [now]. */
        public fun pruneExpiredSamples(
            samples: List<UsageSample>,
            now: Instant,
            maxAge: Duration = MAX_SAMPLE_AGE,
        ): List<UsageSample> {
            val cutoff = now - maxAge
            return samples.filter { it.fetchedAt >= cutoff }
        }

        internal fun pruneConnectionSlot(
            slot: MutableMap<WindowKey, List<UsageSample>>,
            now: Instant,
        ): Boolean {
            var changed = false
            val iterator = slot.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val pruned = pruneExpiredSamples(entry.value, now)
                if (pruned.isEmpty()) {
                    iterator.remove()
                    changed = true
                } else if (pruned.size != entry.value.size) {
                    entry.setValue(pruned)
                    changed = true
                }
            }
            return changed
        }

        public val SHAPE: KeyShape =
            KeyShape(
                KEY_PREFIX,
                KeyScope.Profile,
                PrefType.Str,
                ":engine:integrations-usage",
                "per-window usage reading history",
            )

        /**
         * Pure transition for one window's history:
         * - ignores non-finite or out-of-order (older) samples,
         * - on a later [UsageSample.resetsAt], drops prior window samples except the old window's
         *   last sample (kept for Details "Last week: ..."),
         * - dedupes matching (usedPercent, resetsAt), updating only the last sample's fetchedAt
         *   when strictly more than [dedupeInterval] later,
         * - caps each window at [maxSamples] newest samples.
         */
        public fun appendSample(
            existing: List<UsageSample>,
            sample: UsageSample,
            maxSamples: Int = MAX_SAMPLES_PER_WINDOW,
            dedupeInterval: Duration = DEDUPE_REFRESH_INTERVAL,
        ): List<UsageSample> {
            if (!sample.usedPercent.isFinite() || maxSamples <= 0) return existing
            val normalized =
                sample.copy(usedPercent = sample.usedPercent.coerceIn(MIN_PERCENT, MAX_PERCENT))
            val last = existing.lastOrNull()
            val next =
                when {
                    last == null -> listOf(normalized)
                    normalized.fetchedAt < last.fetchedAt -> existing
                    isLaterReset(last.resetsAt, normalized.resetsAt) -> listOf(last, normalized)
                    isSameReading(last, normalized) ->
                        if (normalized.fetchedAt - last.fetchedAt > dedupeInterval) {
                            existing.dropLast(1) + last.copy(fetchedAt = normalized.fetchedAt)
                        } else {
                            existing
                        }
                    else -> existing + normalized
                }
            return if (next.size > maxSamples) next.takeLast(maxSamples) else next
        }

        private fun isLaterReset(previous: Instant?, current: Instant?): Boolean =
            previous != null && current != null && current > previous

        private fun isSameReading(previous: UsageSample, current: UsageSample): Boolean =
            previous.resetsAt == current.resetsAt &&
                abs(previous.usedPercent - current.usedPercent) <= VALUE_EPSILON
    }
}

/** Returns the current ordered samples for [key], or an empty list when none are recorded. */
public fun UsageHistory.forWindow(key: WindowKey): List<UsageSample> = samples.value[key].orEmpty()

private suspend fun persistConnection(
    store: KeyValueStore,
    lane: WriterLane,
    byConnection: MutableMap<String, MutableMap<WindowKey, List<UsageSample>>>,
    connectionId: String,
): Boolean {
    val slot = byConnection[connectionId]
    val storageKey = UsageHistory.key(connectionId)
    return if (slot.isNullOrEmpty()) {
        byConnection.remove(connectionId)
        store.string(storageKey) == null ||
            store.commit(lane) { remove(storageKey) } is Outcome.Ok
    } else {
        val payload = encodeUsageHistoryConnection(slot)
        store.commit(lane) { putString(storageKey, payload) } is Outcome.Ok
    }
}

private fun applyWindowUpdates(
    slot: MutableMap<WindowKey, List<UsageSample>>,
    updates: List<Pair<WindowKey, UsageSample>>,
    now: Instant,
): Boolean {
    var changed = UsageHistory.pruneConnectionSlot(slot, now)
    for ((windowKey, sample) in updates) {
        val prior = slot[windowKey].orEmpty()
        val appended =
            UsageHistory.appendSample(
                prior,
                sample,
                UsageHistory.MAX_SAMPLES_PER_WINDOW,
                UsageHistory.DEDUPE_REFRESH_INTERVAL,
            )
        val next = UsageHistory.pruneExpiredSamples(appended, now)
        if (next != prior) {
            if (next.isEmpty()) slot.remove(windowKey) else slot[windowKey] = next
            changed = true
        }
    }
    return changed
}
