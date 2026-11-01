package io.github.eslamasabry.oc.engine.runtime.budget

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Why an app-owned Linux service last ended, from the native service records (no command or log text). */
public enum class ServiceExitReason(public val wire: String) {
    /** Exit 137: a memory or Android phantom-process kill. The code alone cannot tell those from another SIGKILL. */
    MemoryOrPhantomKill("memory_or_phantom_kill"),
    Exited("exited"),
    Unknown("unknown"),
    ;

    public companion object {
        public fun parse(value: String?): ServiceExitReason =
            entries.firstOrNull { it.wire == value && it != Unknown } ?: Unknown
    }
}

/** One service's last exit, as `RuntimePerformance` reports it. [lastStopRequested] is null when unknown. */
public data class ServiceExitRecord(
    val lastExitAt: Instant?,
    val lastStopRequested: Boolean?,
    val exitReason: ServiceExitReason,
)

/**
 * When Android last stopped one of the app's Linux programs by itself (a SIGKILL nobody asked for: its process limit or
 * memory) within [within]; null when it did not (port of process_budget.dart `lastAndroidKill`). A stop the app asked
 * for, an unknown request flag and a normal exit do not count. The kill line's words are :ui:words' (plan 11 §7.2).
 */
public fun lastAndroidKill(
    services: Map<String, ServiceExitRecord>,
    now: Instant,
    within: Duration = 24.hours,
): Instant? {
    val since = now - within
    return services.values
        .filter { it.lastStopRequested == false && it.exitReason == ServiceExitReason.MemoryOrPhantomKill }
        .mapNotNull { it.lastExitAt }
        .filter { it >= since }
        .maxOrNull()
}
