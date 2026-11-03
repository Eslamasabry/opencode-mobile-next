package io.github.eslamasabry.oc.engine.plans

import io.github.eslamasabry.oc.core.common.dart.DartJson

/** One notification to show. The UI maps [reset] to "plan is back" or "past the alert level" copy. */
public data class UsageAlertNote(
    /** The connection the reading came from ('phone' for this phone's agents). */
    val profileId: String,
    /** Notification key: one per subscription and kind. */
    val key: String,
    /** 64 hex characters the notification carries, so a tap can find the subscription again without a name. */
    val token: String,
    /** "back again" (D5) rather than "past the alert level" (D4). */
    val reset: Boolean,
    /** A plain provider name (letters, digits, space, dot, dash; up to 32) or empty. */
    val providerName: String,
)

/** The OS notification sink for plan alerts. [show] returns false when the system refused it (it is tried again). */
public interface PlanAlertNotifier {
    public suspend fun show(note: UsageAlertNote): Boolean

    public suspend fun dismiss(key: String)
}

/**
 * The second alert delivery path (port of SubscriptionAlertDelivery): keyed on the subscription, so plans read by a
 * computer, by Paseo or from the Codex account alert like In-app Ubuntu's, and any plan can send the reset message. It
 * reuses the quota monitor's rules: the shared switch, quiet hours, Wi-Fi only, no delivery while the app is open, and
 * a marker per window and reset time so one crossing alerts once.
 */
public class SubscriptionAlertDelivery(
    private val prefs: PlansPrefs,
    private val sharedRules: () -> SharedNotifyRules,
    private val notifier: PlanAlertNotifier,
    private val wifi: WifiState,
    private val clock: PlansClock,
    /** Numbers older than this never alert. */
    private val maxAgeMs: Long = 45 * 60_000L,
    /** A reset is announced only when the high reading it follows is this fresh. */
    private val resetWithinMs: Long = 12 * 3_600_000L,
) {
    public companion object {
        /** Shared blob. Holds opaque subscription keys and numbers, never a name. */
        public const val STORE_KEY: String = "oc.usageAlertSeen.v1"

        private fun hash(value: Any?): String = DartJson.sha256Hex(DartJson.encode(value))

        /** The token a notification for [subscriptionKey] carries. */
        public fun tokenFor(subscriptionKey: String): String = hash(listOf("usage-alert", subscriptionKey))

        internal fun alertKey(subscriptionKey: String, reset: Boolean): String =
            "${if (reset) "quota-reset" else "quota-sub"}|${hash(subscriptionKey).substring(0, 16)}"

        private val safeName = Regex("^[A-Za-z0-9 .-]{1,32}$")

        internal fun safeName(name: String): String = if (safeName.matches(name)) name else ""
    }

    /** Whether a tapped notification names a subscription read from [profileId]. */
    public fun owns(snapshot: UsageSnapshot, profileId: String, token: String): Boolean =
        snapshot.subscriptions.any { it.readFrom?.id == profileId && tokenFor(it.key) == token }

    private fun load(): MutableMap<String, MutableMap<String, MutableMap<String, Any?>>> {
        val out = LinkedHashMap<String, MutableMap<String, MutableMap<String, Any?>>>()
        val raw = prefs.getString(STORE_KEY) ?: return out
        if (raw.length > 262144) return out
        val subs = (DartJson.decodeOrNull(raw) as? Map<*, *>)?.get("subs") as? Map<*, *> ?: return out
        for ((key, windows) in subs.entries.take(256)) {
            if (key !is String || windows !is Map<*, *>) continue
            val record = LinkedHashMap<String, MutableMap<String, Any?>>()
            for ((id, value) in windows.entries.take(16)) {
                if (id is String && value is Map<*, *>) {
                    record[id] = LinkedHashMap<String, Any?>().apply {
                        for ((k, v) in value) if (k is String) put(k, v)
                    }
                }
            }
            out[key] = record
        }
        return out
    }

    private suspend fun save(seen: Map<String, Map<String, Map<String, Any?>>>) {
        // A refused write is retried by the next evaluation.
        prefs.setString(STORE_KEY, DartJson.encode(mapOf("v" to 1, "subs" to seen)))
    }

    /** Drops records of subscriptions that no longer exist anywhere. */
    public suspend fun prune(alive: Set<String>) {
        val seen = load()
        val before = seen.size
        seen.keys.retainAll(alive)
        if (seen.size != before) save(seen)
    }

    /**
     * Looks at the current subscriptions and shows what is due. [covered] says a subscription's threshold alert already
     * comes from the quota monitor, so only its reset message is sent here.
     */
    public suspend fun evaluate(
        snapshot: UsageSnapshot,
        alertsOn: Boolean,
        foreground: Boolean,
        covered: (UsageSubscription) -> Boolean,
    ) {
        val now = clock.nowMs()
        val rules = sharedRules()
        // The app being open, the switch being off and quiet hours leave records as they were: a crossing missed
        // meanwhile is announced afterwards.
        if (!alertsOn || foreground || rules.quietAt(clock.minuteOfDay(now))) return
        val seen = load()
        var changed = false
        var onWifi: Boolean? = null
        var askedWifi = false
        suspend fun allowed(): Boolean {
            if (!rules.wifiOnly) return true
            if (!askedWifi) {
                onWifi = wifi.onWifi()
                askedWifi = true
            }
            return onWifi == true
        }

        for (s in snapshot.subscriptions) {
            val from = s.readFrom
            val fetched = s.fetchedAt
            if (from == null || fetched == null || s.windows.isEmpty() ||
                s.state == SubscriptionState.OfflineStale || s.state == SubscriptionState.ReaderBroken ||
                now - fetched > maxAgeMs
            ) {
                continue
            }
            val threshold = s.alertThreshold
            val record = seen.getOrPut(s.key) { LinkedHashMap() }
            var wantsReset = false
            var wantsAlert = false
            val next = LinkedHashMap<String, MutableMap<String, Any?>>()
            for (w in s.windows) {
                val prev = record[w.id]
                val prevUsed = (prev?.get("u") as? Number)?.toDouble()
                val prevReset = (prev?.get("r") as? Number)?.toLong()
                val prevAt = prev?.get("t") as? Long
                val resetMs = w.resetsAt
                if (s.notifyOnReset && prevUsed != null && prevUsed >= threshold && w.usedPercent < threshold &&
                    prevReset != resetMs && prevAt != null && now - prevAt <= resetWithinMs
                ) {
                    wantsReset = true
                }
                var marker = prev?.get("m") as? String
                if (w.usedPercent >= threshold) {
                    val fresh = hash(listOf(s.key, w.id, w.durationSeconds, resetMs, threshold))
                    if (marker != fresh) {
                        if (!covered(s)) wantsAlert = true
                        marker = fresh
                    }
                } else {
                    marker = null
                }
                next[w.id] =
                    LinkedHashMap<String, Any?>().apply {
                        put("u", w.usedPercent)
                        put("r", resetMs)
                        put("t", now)
                        if (marker != null) put("m", marker)
                    }
            }
            val high = s.windows.any { it.usedPercent >= threshold }
            if (!high) notifier.dismiss(alertKey(s.key, reset = false))
            if ((wantsAlert || wantsReset) && !allowed()) {
                // Off Wi-Fi: leave this subscription as it was and look again later.
                continue
            }
            var keepMarkers = true
            val name = safeName(s.displayName)
            if (wantsAlert) {
                val note = UsageAlertNote(from.id, alertKey(s.key, reset = false), tokenFor(s.key), false, name)
                // An unavailable notification sink is not a delivery: forget the marker so the next evaluation tries
                // the same crossing again.
                if (!notifier.show(note)) keepMarkers = false
            }
            if (wantsReset) {
                notifier.show(UsageAlertNote(from.id, alertKey(s.key, reset = true), tokenFor(s.key), true, name))
            }
            if (!keepMarkers) next.values.forEach { it.remove("m") }
            record.clear()
            record.putAll(next)
            changed = true
        }
        if (changed) save(seen)
    }
}
