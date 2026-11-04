package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public data class UsageAlertNote(
    val profileId: String,
    val key: String,
    val token: String,
    val reset: Boolean,
    val providerName: String,
)

/**
 * One durable owner for subscription crossings and reset messages. Only opaque keys and numbers
 * persist.
 */
public class SubscriptionAlertDelivery(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    private val clock: () -> Instant,
    private val allowed: suspend () -> Boolean,
    private val show: suspend (UsageAlertNote) -> Boolean,
    private val dismiss: suspend (String) -> Unit,
) {
    private val lock = Mutex()

    public companion object {
        public const val KEY: String = "oc.usageAlertSeen.v1"

        public fun tokenFor(key: String): String = hash(listOf("usage-alert", key))

        private fun hash(value: Any?): String = DartJson.sha256Hex(DartJson.encode(value))

        private fun alertKey(key: String, reset: Boolean): String =
            "${if (reset) "quota-reset" else "quota-sub"}|${hash(key).take(16)}"
    }

    private fun load(): MutableMap<String, MutableMap<String, Map<String, Any?>>> {
        val raw = store.string(KEY) ?: return linkedMapOf()
        if (raw.length > 262_144) return linkedMapOf()
        val subs =
            (DartJson.decodeOrNull(raw) as? Map<*, *>)?.get("subs") as? Map<*, *>
                ?: return linkedMapOf()
        return subs.entries
            .take(256)
            .mapNotNull { (key, value) ->
                if (key !is String || value !is Map<*, *>) null
                else
                    key to
                        value.entries
                            .take(16)
                            .mapNotNull { (id, record) ->
                                if (id !is String || record !is Map<*, *>) null
                                else
                                    id to
                                        record.entries
                                            .filter { it.key is String }
                                            .associate { it.key as String to it.value }
                            }
                            .toMap()
                            .toMutableMap()
            }
            .toMap()
            .toMutableMap()
    }

    public suspend fun prune(alive: Set<String>): Boolean = lock.withLock {
        val seen = load()
        if (seen.keys.all { it in alive }) return@withLock true
        val removed = seen.keys - alive
        seen.keys.retainAll(alive)
        if (!save(seen)) return@withLock false
        for (key in removed) {
            dismiss(alertKey(key, false))
            dismiss(alertKey(key, true))
        }
        true
    }

    private suspend fun save(seen: Map<String, Map<String, Map<String, Any?>>>): Boolean =
        store.commit(lane) { putString(KEY, DartJson.encode(mapOf("v" to 1, "subs" to seen))) } is
            Outcome.Ok

    private suspend fun post(note: UsageAlertNote, isCurrent: () -> Boolean): Boolean {
        if (!isCurrent() || !allowed()) return false
        val delivered =
            when (val result = attempt({ Failure.Device() }) { show(note) }) {
                is Outcome.Ok -> result.value
                is Outcome.Failed -> false
            }
        // Android's sink can finish after a deletion, new account or foreground return.
        // Retract that exact late notification and leave its claim retryable.
        if (!isCurrent() || !allowed()) {
            dismiss(note.key)
            return false
        }
        return delivered
    }

    public suspend fun evaluate(
        snapshot: UsageSnapshot,
        alertsOn: Boolean,
        foreground: Boolean,
        covered: (UsageSubscription) -> Boolean,
        isCurrent: (UsageSubscription) -> Boolean = { true },
    ) {
        lock.withLock {
            if (!alertsOn || foreground || !allowed()) return
            val now = clock()
            val seen = load()
            var changed = false
            for (s in snapshot.subscriptions) {
                if (!isCurrent(s)) continue
                val from = s.readFrom ?: continue
                val fetched = s.fetchedAt ?: continue
                if (
                    s.windows.isEmpty() ||
                        s.state in
                            setOf(SubscriptionState.OfflineStale, SubscriptionState.ReaderBroken) ||
                        fetched > now ||
                        now - fetched > 45.minutes
                )
                    continue
                val record = seen[s.key].orEmpty()
                var wantsAlert = false
                var wantsReset = false
                val next = linkedMapOf<String, Map<String, Any?>>()
                for (w in s.windows) {
                    val previous = record[w.id]
                    val reset = w.resetsAt?.toEpochMilliseconds()
                    val priorUsed = (previous?.get("u") as? Number)?.toDouble()
                    val priorTime = (previous?.get("t") as? Number)?.toLong()
                    if (
                        s.notifyOnReset &&
                            priorUsed != null &&
                            priorUsed >= s.alertThreshold &&
                            w.usedPercent < s.alertThreshold &&
                            previous?.get("r") != reset &&
                            priorTime != null &&
                            now.toEpochMilliseconds() - priorTime in 0..12.hours.inWholeMilliseconds
                    )
                        wantsReset = true
                    val marker =
                        if (w.usedPercent >= s.alertThreshold)
                            hash(listOf(s.key, w.id, w.durationSeconds, reset, s.alertThreshold))
                        else null
                    if (marker != null && marker != previous?.get("m") && !covered(s))
                        wantsAlert = true
                    next[w.id] =
                        mapOf(
                            "u" to w.usedPercent,
                            "r" to reset,
                            "t" to now.toEpochMilliseconds(),
                            "m" to marker,
                        )
                }
                if (s.windows.none { it.usedPercent >= s.alertThreshold })
                    dismiss(alertKey(s.key, false))
                if (!allowed() || !isCurrent(s)) continue
                val name =
                    s.displayName.takeIf { Regex("^[A-Za-z0-9 .-]{1,32}$").matches(it) }.orEmpty()
                val note =
                    UsageAlertNote(from.id, alertKey(s.key, false), tokenFor(s.key), false, name)
                // Persist the claim before showing; a refused sink rolls it back for a later
                // evaluation.
                val previous = seen[s.key]
                seen[s.key] = next
                if (!save(seen)) {
                    if (previous == null) seen.remove(s.key) else seen[s.key] = previous
                    continue
                }
                if (!isCurrent(s) || !allowed()) {
                    if (previous == null) seen.remove(s.key) else seen[s.key] = previous
                    save(seen)
                    continue
                }
                val delivered =
                    (!wantsAlert || post(note) { isCurrent(s) }) &&
                        (!wantsReset ||
                            post(note.copy(key = alertKey(s.key, true), reset = true)) {
                                isCurrent(s)
                            })
                if (!delivered) {
                    if (previous == null) seen.remove(s.key) else seen[s.key] = previous
                    save(seen)
                }
                changed = true
            }
            if (changed) save(seen)
        }
    }
}
