package io.github.eslamasabry.oc.engine.plans

// The small ports this engine needs from the platform. Android adapters are written by the integrator; tests use fakes.

/**
 * The preferences the plans engine keeps (Flutter SharedPreferences keys, same names and JSON shapes, so a migrated
 * install keeps its records). Reads are synchronous over the in-memory cache; writes report whether the platform
 * accepted them (false = refused, nothing changed).
 *
 * Keys written: `oc.usageAlerts.v1`, `oc.usageAlertSeen.v1`, `oc.usageWidget.v1`, `oc.widgetUsageSubs` (shared) and the
 * per-profile `oc.usageLast.<profileId>`, `oc.quotaMonitor.<profileId>`, `oc.widgetUsage.<profileId>` (register these
 * with ProfileScope so profile deletion sweeps them).
 */
public interface PlansPrefs {
    public fun getString(key: String): String?

    public suspend fun setString(key: String, value: String): Boolean

    public suspend fun remove(key: String): Boolean
}

/** Wall time. [minuteOfDay] is the person's local wall-clock minute after midnight (0..1439) at [epochMs]. */
public interface PlansClock {
    public fun nowMs(): Long

    public fun minuteOfDay(epochMs: Long): Int
}

/** The device network: true on Wi-Fi, false off it, null when unknown (unknown fails closed). */
public fun interface WifiState {
    public suspend fun onWifi(): Boolean?
}

/**
 * The notification rules that are the same for every server: one quiet-hours pair, one Wi-Fi-only choice, one
 * check-in rule and one quota-alert choice (port of SharedNotifyRules).
 */
public data class SharedNotifyRules(
    /** Local wall-clock minutes after midnight; quiet hours are on only when both are set. */
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
    val wifiOnly: Boolean = false,
    val checkInAfterMinutes: Int? = null,
    val quotaAlerts: Boolean = false,
) {
    val quietEnabled: Boolean get() = quietStart != null && quietEnd != null

    /** Whether [minuteOfDay] (local) falls in quiet hours. Equal start and end means all day. */
    public fun quietAt(minuteOfDay: Int): Boolean = quietAt(quietStart, quietEnd, minuteOfDay)

    public companion object {
        public const val DEFAULT_QUIET_START: Int = 22 * 60
        public const val DEFAULT_QUIET_END: Int = 8 * 60
    }
}

internal fun quietAt(start: Int?, end: Int?, minute: Int): Boolean {
    if (start == null || end == null) return false
    return start == end || if (start < end) minute in start until end else minute >= start || minute < end
}
