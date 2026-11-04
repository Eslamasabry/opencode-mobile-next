package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
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
 * Subscription threshold/reset delivery over acknowledged readings; never owns a service or a
 * timer.
 */
public class UsageAlertDelivery(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    private val show: suspend (UsageAlertNote) -> Boolean,
    private val dismiss: (String) -> Unit,
    private val wifi: () -> Boolean?,
    private val currentProfile: (String) -> Boolean,
    private val clock: () -> Instant,
) {
    private val writes = Mutex()

    public companion object {
        public const val STORE_KEY: String = "oc.usageAlertSeen.v1"

        public fun tokenFor(subscriptionKey: String): String =
            hash(listOf("usage-alert", subscriptionKey))

        private fun hash(value: Any?): String = DartJson.sha256Hex(DartJson.encode(value))

        private fun alertKey(key: String, reset: Boolean): String =
            "${if (reset) "quota-reset" else "quota-sub"}|${hash(key).take(16)}"
    }

    private fun load(): MutableMap<String, MutableMap<String, Map<String, Any?>>> {
        val raw = store.string(STORE_KEY)?.takeIf { it.length <= 262_144 } ?: return LinkedHashMap()
        val subs =
            (DartJson.decodeOrNull(raw) as? Map<*, *>)?.get("subs") as? Map<*, *>
                ?: return LinkedHashMap()
        val out = LinkedHashMap<String, MutableMap<String, Map<String, Any?>>>()
        for ((key, value) in subs.entries.take(256)) {
            if (key !is String || value !is Map<*, *>) continue
            val windows = LinkedHashMap<String, Map<String, Any?>>()
            for ((id, record) in value.entries.take(16)) {
                if (id !is String || record !is Map<*, *>) continue
                windows[id] =
                    record.entries
                        .filter { it.key is String }
                        .associate { it.key as String to it.value }
            }
            out[key] = windows
        }
        return out
    }

    /**
     * Stale subscriptions are pruned even while delivery is suppressed. Foreground/quiet/Wi-Fi
     * skips acknowledge nothing.
     */
    public suspend fun evaluate(
        snapshot: UsageSnapshot,
        enabled: Boolean,
        foreground: Boolean,
        quiet: Boolean,
        wifiOnly: Boolean,
    ): Unit = writes.withLock {
        val now = clock().toEpochMilliseconds()
        val seen = load()
        val alive = snapshot.subscriptions.mapTo(HashSet()) { it.key }
        var changed = seen.keys.removeAll { it !in alive }
        if (enabled && !foreground && !quiet) {
            for (s in snapshot.subscriptions) {
                val from = s.readFrom ?: continue
                val fetched = s.fetchedAt?.toEpochMilliseconds() ?: continue
                if (
                    !currentProfile(from.id) ||
                        s.windows.isEmpty() ||
                        now - fetched !in 0..2_700_000 ||
                        s.state == SubscriptionState.OfflineStale ||
                        s.state == SubscriptionState.ReaderBroken
                )
                    continue
                val record = seen[s.key].orEmpty()
                val next = LinkedHashMap<String, Map<String, Any?>>()
                var threshold = false
                var reset = false
                for (w in s.windows) {
                    val previous = record[w.id]
                    val previousUsed = (previous?.get("u") as? Number)?.toDouble()
                    val previousReset = (previous?.get("r") as? Number)?.toLong()
                    val previousAt = (previous?.get("t") as? Number)?.toLong()
                    val resetAt = w.resetsAt?.toEpochMilliseconds()
                    if (
                        s.notifyOnReset &&
                            previousUsed != null &&
                            previousUsed >= s.alertThreshold &&
                            w.usedPercent < s.alertThreshold &&
                            previousReset != resetAt &&
                            previousAt != null &&
                            now - previousAt in 0..43_200_000
                    )
                        reset = true
                    var marker = previous?.get("m") as? String
                    if (w.usedPercent >= s.alertThreshold) {
                        val fresh =
                            hash(listOf(s.key, w.id, w.durationSeconds, resetAt, s.alertThreshold))
                        if (marker != fresh) threshold = true
                        marker = fresh
                    } else marker = null
                    next[w.id] = buildMap {
                        put("u", w.usedPercent)
                        put("r", resetAt)
                        put("t", now)
                        if (marker != null) put("m", marker)
                    }
                }
                if ((threshold || reset) && wifiOnly && wifi() != true) continue
                if (!s.windows.any { it.usedPercent >= s.alertThreshold })
                    dismiss(alertKey(s.key, false))
                val name =
                    s.displayName.takeIf { Regex("^[A-Za-z0-9 .-]{1,32}$").matches(it) }.orEmpty()
                if (!currentProfile(from.id)) continue
                var delivered = true
                if (threshold)
                    delivered =
                        show(
                            UsageAlertNote(
                                from.id,
                                alertKey(s.key, false),
                                tokenFor(s.key),
                                false,
                                name,
                            )
                        )
                if (
                    reset &&
                        !show(
                            UsageAlertNote(
                                from.id,
                                alertKey(s.key, true),
                                tokenFor(s.key),
                                true,
                                name,
                            )
                        )
                ) {
                    // Keep the old high observation so a blocked reset delivery can retry.
                    continue
                }
                if (!delivered) {
                    for ((id, value) in next.toMap()) next[id] = value - "m"
                }
                if (!currentProfile(from.id)) continue
                seen[s.key] = next
                changed = true
            }
        }
        if (changed)
            store.commit(lane) {
                putString(STORE_KEY, DartJson.encode(mapOf("v" to 1, "subs" to seen)))
            }
    }
}
